package org.lixiyun.server.infrastructure.audio;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizerResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.properity.TtsProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TTS会话管理器
 * <p>基于阿里云NLS SDK的{@link FlowingSpeechSynthesizer}实现按用户维度的TTS流式语音合成管理。
 * 每轮合成会独立建立WebSocket连接（NLS TTS协议要求），连接在合成完成后自动关闭。</p>
 * <p>本管理器维护的是用户维度的会话上下文（输出流、回调、合成状态），
 * 而非WebSocket长连接复用。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *     <li><b>注册</b>：为用户建立TTS会话上下文，绑定音频输出流与完成回调</li>
 *     <li><b>执行合成</b>：新建WebSocket连接将文本转为语音，实时写入输出流（异步非阻塞）</li>
 *     <li><b>中断</b>：取消当前正在进行的语音合成，保留会话上下文，允许再次合成</li>
 *     <li><b>取消</b>：关闭会话并清理所有资源</li>
 * </ul>
 *
 * <h3>安全保障</h3>
 * <ul>
 *     <li>最大并发会话数限制（{@value #MAX_SESSIONS}），容量检查在compute内原子执行</li>
 *     <li>空闲会话超时自动淘汰（{@value #IDLE_TIMEOUT_SECONDS}s）</li>
 *     <li>回调关闭保护：session关闭后丢弃所有SDK回调事件</li>
 *     <li>原子会话生命周期管理：使用{@code compute}消除竞态</li>
 *     <li>同一用户同一时刻仅允许一路TTS合成（CAS自旋保证）</li>
 *     <li>异步回调通过synthesisId标识校验，防止过期回调污染全局状态</li>
 *     <li>主动中断后递增synthesisId，使SDK延迟回调自动失效</li>
 *     <li>合成器连接在所有退出路径（完成/异常/中断/取消）均显式关闭</li>
 * </ul>
 *
 * <h3>生命周期时序</h3>
 * <pre>
 * Spring容器启动
 *   └─ init()
 *       ├─ initNlsClient()              ← 获取Token、创建NlsClient
 *       ├─ schedule(cleanIdleSessions)  ← 每60s扫描淘汰空闲会话
 *       └─ schedule(refreshToken)       ← 每23h自动刷新Token
 *
 * 业务调用
 *   ├─ register(userId, callback)     ← 建立会话上下文，返回音频输出流
 *   ├─ sendTextSegment(userId, text)  ← 反复调用，流式发送文本分片
 *   ├─ finishSynthesis(userId)        ← LLM输出结束，等待收尾音频
 *   ├─ interrupt(userId)              ← 中断当前合成（会话保留）
 *   └─ cancel(userId)                 ← 关闭会话 + 清理资源
 *
 * Spring容器关闭
 *   └─ shutdown()
 *       ├─ scheduler.shutdownNow()
 *       ├─ 遍历关闭所有TtsSession
 *       └─ nlsClient.shutdown()
 * </pre>
 *
 * <h3>配置项</h3>
 * <pre>
 * nls:
 *   app-key:              # 阿里云NLS应用AppKey（与ASR共享）
 *   access-key-id:        # 阿里云AccessKey ID（与ASR共享）
 *   access-key-secret:    # 阿里云AccessKey Secret（与ASR共享）
 *   gateway-url:          # NLS网关地址（与ASR共享）
 *   token-refresh-hours:  # Token自动刷新间隔（小时），默认23
 *
 * tts.nls:
 *   voice: siyue          # 发音人标识
 *   volume: 50            # 音量 0~100
 *   pitch-rate: 0         # 语调 -500~500
 *   speech-rate: 0        # 语速 -500~500
 * </pre>
 *
 * @author lixiyun
 * @since 2026-08-16
 */
@Slf4j
@Component
public class TtsConnectionManager {

    /** 最大并发TTS会话数 */
    public static final int MAX_SESSIONS = 100;
    /** 空闲会话超时时间（秒） */
    public static final int IDLE_TIMEOUT_SECONDS = 60;
    /** 空闲会话检查间隔（秒） */
    private static final int IDLE_CHECK_INTERVAL_SECONDS = 60;
    /** 合成超时时间（秒） */
    private static final long SYNTHESIS_TIMEOUT_SECONDS = 60L;
    /** 连续两次发送文本的最小时间间隔（毫秒） */
    private static final int MIN_SEND_INTERVAL_MS = 100;
    /** 保活ping空闲阈值（毫秒） */
    private static final long PING_IDLE_THRESHOLD_MS = 2000L;
    /** 保活ping检查间隔（毫秒） */
    private static final long PING_CHECK_INTERVAL_MS = 1000L;
    /** 用户TTS会话注册表 */
    private final ConcurrentHashMap<Long, TtsSession> sessions = new ConcurrentHashMap<>();
    /** 全局NLS客户端 */
    private volatile NlsClient nlsClient;
    /** 定时任务调度器 */
    private ScheduledExecutorService scheduler;

    private final TtsProperties ttsProperties;

    public TtsConnectionManager(TtsProperties ttsProperties) {
        this.ttsProperties = ttsProperties;
    }

    /**
     * Spring容器初始化回调：获取Token、创建NlsClient、启动定时任务
     *
     * @throws Exception Token获取失败或客户端初始化异常
     */
    @PostConstruct
    public void init() throws Exception {
        initNlsClient();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tts-idle-check");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                this::cleanIdleSessions,
                IDLE_CHECK_INTERVAL_SECONDS,
                IDLE_CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        scheduler.scheduleAtFixedRate(
                this::refreshToken,
                ttsProperties.getTokenRefreshHours(),
                ttsProperties.getTokenRefreshHours(),
                TimeUnit.HOURS
        );

        log.info("TTS连接管理器初始化完成，最大会话数：{}，空闲超时：{}s", MAX_SESSIONS, IDLE_TIMEOUT_SECONDS);
    }

    /** 初始化NLS客户端，获取Token并创建NlsClient，若存在旧实例则先shutdown */
    private void initNlsClient() {
        AccessToken accessToken = new AccessToken(ttsProperties.getAccessKeyId(), ttsProperties.getAccessKeySecret());
        try {
            accessToken.apply();
            log.info("TTS NLS Token获取成功，过期时间：{}", accessToken.getExpireTime());
        } catch (IOException e) {
            log.error("TTS NLS Token获取失败", e);
            throw new RuntimeException("TTS NLS Token获取失败", e);
        }

        NlsClient client;
        String gatewayUrl = ttsProperties.getGatewayUrl();
        if (gatewayUrl == null || gatewayUrl.isEmpty()) {
            client = new NlsClient(accessToken.getToken());
        } else {
            client = new NlsClient(gatewayUrl, accessToken.getToken());
        }

        NlsClient old = this.nlsClient;
        this.nlsClient = client;

        if (old != null) {
            old.shutdown();
        }

        log.info("TTS NlsClient初始化完成，gatewayUrl：{}", (gatewayUrl == null || gatewayUrl.isEmpty()) ? "默认" : gatewayUrl);
    }

    /** 刷新NLS Token，委托initNlsClient()完成，刷新后中断所有活跃合成任务 */
    private void refreshToken() {
        try {
            log.info("开始刷新TTS NLS Token");
            initNlsClient();
            interruptActiveSyntheses();
            log.info("TTS NLS Token刷新成功，已中断所有活跃合成任务");
        } catch (Exception e) {
            log.error("TTS NLS Token刷新失败，将在下个周期重试", e);
        }
    }

    /** 中断所有活跃的合成任务（Token刷新后调用） */
    private void interruptActiveSyntheses() {
        sessions.forEach((userId, session) -> {
            if (!session.closed.get() && session.busyFlag.get() == 1) {
                interrupt(userId);
                log.debug("[TTS] 用户{}活跃合成已中断（Token已刷新）", userId);
            }
        });
    }

    /** 清理空闲超时的TTS会话 */
    private void cleanIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.forEach((userId, session) -> {
            long idleSeconds = (now - session.lastActiveTime) / 1000;
            if (idleSeconds > IDLE_TIMEOUT_SECONDS) {
                log.info("用户{}TTS会话空闲超时（{}s > {}s），自动淘汰", userId, idleSeconds, IDLE_TIMEOUT_SECONDS);
                cancel(userId);
            }
        });
    }

    /** TTS合成结果回调接口，所有回调在SDK WebSocket线程中执行 */
    public interface TtsResultCallback {

        /** 流式合成开始回调 */
        default void onSynthesisStart() {
        }

        /** 服务端检测到一句话的开始 */
        default void onSentenceBegin() {
        }

        /** 服务端检测到一句话的结束 */
        default void onSentenceEnd() {
        }

        /**
         * 接收到音频数据回调
         *
         * @param audioData 音频数据字节数组
         */
        default void onAudioData(byte[] audioData) {
        }

        /** 增量时间戳回调 */
        default void onSentenceSynthesis() {
        }

        /** 合成完成回调 */
        default void onSynthesisComplete() {
        }

        /**
         * 合成失败回调
         *
         * @param taskId     任务ID
         * @param statusText 错误状态描述
         */
        default void onFail(String taskId, String statusText) {
        }
    }

    /**
     * 为用户建立TTS会话上下文，绑定合成结果回调
     *
     * @param userId   用户ID
     * @param callback 合成结果回调
     * @return 音频输出缓冲流
     * @throws BusinessException 若并发会话数达到上限时
     */
    public ByteArrayOutputStream register(Long userId, TtsResultCallback callback) {
        AtomicReference<ByteArrayOutputStream> outputStreamRef = new AtomicReference<>();
        sessions.compute(userId, (k, existing) -> {
            if (existing != null && !existing.closed.get()) {
                log.warn("用户{}TTS会话已存在，先关闭旧会话", userId);
                closeSession(existing, userId);
            }

            if (sessions.size() >= MAX_SESSIONS) {
                log.error("TTS并发会话数已达上限{}", MAX_SESSIONS);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStreamRef.set(outputStream);
            TtsSession newSession = new TtsSession(callback);
            log.info("用户{}TTS会话注册成功", userId);
            return newSession;
        });
        return outputStreamRef.get();
    }

    /**
     * 发送文本分片，首次调用时自动创建合成器并建立连接
     *
     * @param userId  用户ID
     * @param segment 文本分片
     * @throws BusinessException 若用户未注册或存在合成冲突
     * @throws RuntimeException   若首次调用时创建合成器或建立连接失败
     */
    public void sendTextSegment(Long userId, String segment) {
        TtsSession session = sessions.get(userId);
        if (session == null || session.closed.get()) {
            log.error("用户{}未注册TTS会话或会话已关闭，无法发送文本分片", userId);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
        }

        if (segment == null || segment.isBlank()) {
            return;
        }

        FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.get();

        if (synthesizer == null) {
            if (!session.tryAcquire()) {
                log.error("用户{}TTS合成冲突，当前有正在进行的合成任务", userId);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }

            session.processFlag.set(false);
            int currentSynthesisId = session.synthesisId.incrementAndGet();
            try {
                session.resetLatch();
                FlowingSpeechSynthesizerListener listener = createListener(session, userId, currentSynthesisId);
                synthesizer = createSynthesizer(listener);
                session.synthesizerRef.set(synthesizer);
                session.synthesizerCreationId = currentSynthesisId;

                long start = System.currentTimeMillis();
                synthesizer.start();
                synthesizer.setMinSendIntervalMS(MIN_SEND_INTERVAL_MS);
                session.lastSendTime = System.currentTimeMillis();
                startPingTask(userId, session);
                log.info("[TTS] 用户{}合成器连接建立，耗时{}ms", userId, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("[TTS] 用户{}创建合成器或建立连接失败", userId, e);
                session.synthesizerRef.set(null);
                session.synthesizerCreationId = -1;
                session.release();
                throw new RuntimeException("TTS合成器初始化失败: " + e.getMessage(), e);
            }
        }

        if (session.synthesisId.get() != session.synthesizerCreationId) {
            log.debug("[TTS] 用户{}合成已被中断，忽略文本分片发送", userId);
            return;
        }

        try {
            if (session.processFlag.get()) {
                log.debug("[TTS] 用户{}当前正在处理文本分片，忽略本次发送", userId);
                return;
            }
            synthesizer.send(segment);
            session.lastSendTime = System.currentTimeMillis();
            session.lastActiveTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("[TTS] 用户{}发送文本分片失败", userId, e);
            throw new RuntimeException("TTS文本分片发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 完成合成：通知NLS服务文本发完，等待收尾音频
     *
     * @param userId 用户ID
     * @throws BusinessException 若用户未注册或合成器未初始化
     * @throws RuntimeException   若stop失败或合成超时
     */
    public void finishSynthesis(Long userId) {
        TtsSession session = sessions.get(userId);
        if (session == null || session.closed.get()) {
            log.error("用户{}未注册TTS会话或会话已关闭，无法完成合成", userId);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
        }

        FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.get();
        if (synthesizer == null) {
            log.warn("用户{}无活跃合成器，无需完成合成", userId);
            session.release();
            return;
        }

        session.processFlag.set(true);
        try {
            synthesizer.stop();

            boolean completed = session.latch.await(SYNTHESIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("[TTS] 用户{}合成超时（{}s），强制中断", userId, SYNTHESIS_TIMEOUT_SECONDS);
            }
        } catch (Exception e) {
            log.error("[TTS] 用户{}完成合成异常", userId, e);
            session.synthesisId.incrementAndGet();
            throw new RuntimeException("TTS完成合成失败: " + e.getMessage(), e);
        } finally {
            stopPing(session);
            closeSynthesizer(synthesizer, userId, "finish");
            session.synthesizerRef.set(null);
            session.release();
            session.lastActiveTime = System.currentTimeMillis();
        }
    }

    /**
    * 中断TTS合成
    *
    * @param userId 用户ID
    */
    public void interrupt(Long userId) {
        TtsSession session = sessions.get(userId);
        if (session == null || session.closed.get()) {
            log.warn("用户{}无TTS会话或会话已关闭，忽略中断", userId);
            return;
        }

        session.synthesisId.incrementAndGet();
        stopPing(session);
        FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.getAndSet(null);
        if (synthesizer != null) {
            try {
                synthesizer.close();
                log.info("用户{}TTS合成器已关闭（中断）", userId);
            } catch (Exception e) {
                log.error("用户{}中断TTS合成器失败", userId, e);
            }
        }

        if (session.latch != null) {
            session.latch.countDown();
        }

        session.release();
        log.info("用户{}TTS合成已中断", userId);
    }

    /**
     * 取消TTS会话
     *
     * @param userId 用户ID
     */
     public void cancel(Long userId) {
          sessions.computeIfPresent(userId, (k, session) -> {
              if (!session.closed.compareAndSet(false, true)) {
                  log.debug("用户{}TTS会话已关闭，忽略取消请求", userId);
                  return null;
              }

              session.synthesisId.incrementAndGet();
              stopPing(session);
              FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.getAndSet(null);
              if (synthesizer != null) {
                  try {
                      synthesizer.close();
                  } catch (Exception e) {
                      log.error("用户{}关闭TTS合成器失败", userId, e);
                  }
              }

              if (session.latch != null) {
                  session.latch.countDown();
              }

              session.release();
              log.info("用户{}TTS会话已取消并清理", userId);
              return null;
          });
    }

    /**
     * 检查用户TTS会话是否活跃
     *
     * @param userId 用户ID
     * @return {@code true}表示会话活跃
     */
    public boolean isSessionActive(Long userId) {
        TtsSession session = sessions.get(userId);
        return session != null && !session.closed.get();
    }

    /**
     * 检查用户TTS会话是否已关闭
     *
     * @param userId 用户ID
     * @return {@code true}表示会话已关闭或不存在
     */
    private boolean isSessionClosed(Long userId) {
        TtsSession session = sessions.get(userId);
        return session == null || session.closed.get();
    }

    /**
     * 获取当前活跃会话数
     *
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(s -> !s.closed.get()).count();
    }

    /**
     * 创建TTS合成器回调监听器
     *
     * @param session TTS会话
     * @param userId 用户ID
     * @return TTS合成器回调监听器
     */
    private FlowingSpeechSynthesizerListener createListener(TtsSession session, Long userId, int currentSynthesisId){
        return new FlowingSpeechSynthesizerListener() {
            private boolean firstRecvBinary = true;

            @Override
            public void onSynthesisStart(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(userId) || session.synthesisId.get() != currentSynthesisId) {
                    log.debug("用户{}收到过期onSynthesisStart回调，忽略", userId);
                    return;
                }
                log.debug("[TTS] 用户{}合成开始, status={}", userId, response.getStatus());
                session.callback.onSynthesisStart();
            }

            @Override
            public void onSentenceBegin(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(userId) || session.synthesisId.get() != currentSynthesisId) {
                    return;
                }
                log.debug("[TTS] 用户{}句子开始, status={}", userId, response.getStatus());
                session.callback.onSentenceBegin();
            }

            @Override
            public void onSentenceEnd(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(userId) || session.synthesisId.get() != currentSynthesisId) {
                    return;
                }
                log.debug("[TTS] 用户{}句子结束, subtitles={}", userId, response.getObject("subtitles"));
                session.callback.onSentenceEnd();
            }

            @Override
            public void onAudioData(ByteBuffer message) {
                if (isSessionClosed(userId) || session.synthesisId.get() != currentSynthesisId) {
                    log.debug("用户{}收到过期音频数据，忽略", userId);
                    return;
                }
                try {
                    if (firstRecvBinary) {
                        firstRecvBinary = false;
                        log.info("[TTS] 用户{}收到首包音频数据", userId);
                    }
                    byte[] bytesArray = new byte[message.remaining()];
                    message.get(bytesArray, 0, bytesArray.length);
                    session.callback.onAudioData(bytesArray);
                } catch (Exception e) {
                    log.error("[TTS] 用户{}音频数据写入输出流失败", userId, e);
                }
            }

            @Override
            public void onSentenceSynthesis(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(userId) || session.synthesisId.get() != currentSynthesisId) {
                    return;
                }
                log.debug("[TTS] 用户{}增量时间戳, subtitles={}", userId, response.getObject("subtitles"));
                session.callback.onSentenceSynthesis();
            }

            @Override
            public void onSynthesisComplete(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(userId) || session.synthesisId.get() != currentSynthesisId) {
                    log.debug("用户{}收到过期onSynthesisComplete回调，忽略", userId);
                    return;
                }
                log.info("[TTS] 用户{}TTS合成完成, status={}", userId, response.getStatus());
                session.latch.countDown();
                session.processFlag.set(false);
                session.callback.onSynthesisComplete();
            }

            @Override
            public void onFail(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(userId) || session.synthesisId.get() != currentSynthesisId) {
                    log.debug("用户{}收到过期onFail回调，忽略", userId);
                    return;
                }
                log.error("[TTS] 用户{}TTS合成失败, taskId={}, status={}, statusText={}",
                        userId, response.getTaskId(), response.getStatus(), response.getStatusText());
                session.latch.countDown();
                session.processFlag.set(false);
                session.callback.onFail(response.getTaskId(), response.getStatusText());
            }
        };
    }

    /**
     * 创建TTS合成器实例
     *
     * @param listener TTS合成器回调监听器
     * @return TTS合成器实例
     * @throws Exception 如果创建合成器失败
     */
    private FlowingSpeechSynthesizer createSynthesizer(FlowingSpeechSynthesizerListener listener) throws Exception {
        FlowingSpeechSynthesizer synthesizer = new FlowingSpeechSynthesizer(nlsClient, listener);
        synthesizer.setAppKey(ttsProperties.getAppKey());
        if (ttsProperties.getFormat() != null){
            if(ttsProperties.getFormat().equalsIgnoreCase(OutputFormatEnum.WAV.name())){
                synthesizer.setFormat(OutputFormatEnum.WAV);
            } else if (ttsProperties.getFormat().equalsIgnoreCase(OutputFormatEnum.MP3.name())){
                synthesizer.setFormat(OutputFormatEnum.MP3);
            }
        }
        synthesizer.setSampleRate(ttsProperties.getSampleRate());
        synthesizer.setVoice(ttsProperties.getVoice());
        synthesizer.setVolume(ttsProperties.getVolume());
        synthesizer.setPitchRate(ttsProperties.getPitchRate());
        synthesizer.setSpeechRate(ttsProperties.getSpeechRate());

        return synthesizer;
    }

    /**
     * 启动保活ping定时任务，空闲超时后自动发送WebSocket ping帧
     *
     * @param userId  用户ID
     * @param session TTS会话实例
     */
    private void startPingTask(Long userId, TtsSession session) {
        session.pingFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (session.closed.get() || session.synthesizerRef.get() == null) {
                    return;
                }
                long elapsed = System.currentTimeMillis() - session.lastSendTime;
                if (elapsed >= PING_IDLE_THRESHOLD_MS) {
                    FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.get();
                    if (synthesizer != null && !session.closed.get()) {
                        synthesizer.getConnection().sendPing();
                        session.lastSendTime = System.currentTimeMillis();
                        log.debug("[TTS] 用户{}发送保活ping（空闲{}ms）", userId, elapsed);
                    }
                }
            } catch (Exception e) {
                log.debug("[TTS] 用户{}保活ping发送失败（合成器可能已关闭）", userId);
            }
        }, PING_CHECK_INTERVAL_MS, PING_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止保活ping定时任务
     *
     * @param session TTS会话实例
     */
    private void stopPing(TtsSession session) {
        ScheduledFuture<?> pingFuture = session.pingFuture;
        if (pingFuture != null) {
            pingFuture.cancel(false);
            session.pingFuture = null;
        }
    }

    /**
     * 安全关闭合成器实例，捕获异常并记录日志
     *
     * @param synthesizer 待关闭的合成器实例
     * @param userId      用户ID
     * @param reason      关闭原因
     */
    private void closeSynthesizer(FlowingSpeechSynthesizer synthesizer, Long userId, String reason) {
        if (synthesizer == null) {
            return;
        }
        try {
            synthesizer.close();
            log.debug("用户{}TTS合成器已关闭, reason={}", userId, reason);
        } catch (Exception e) {
            log.error("用户{}关闭TTS合成器失败, reason={}", userId, reason, e);
        }
    }

    /**
     * 关闭TTS会话
     *
     * @param session 待关闭的会话实例
     * @param userId  用户ID
     */
    private void closeSession(TtsSession session, Long userId) {
        session.closed.set(true);
        session.synthesisId.incrementAndGet();
        stopPing(session);
        FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.getAndSet(null);
        if (synthesizer != null) {
            try {
                synthesizer.close();
            } catch (Exception e) {
                log.error("用户{}关闭TTS合成器失败", userId, e);
            }
        }
        if (session.latch != null) {
            session.latch.countDown();
        }
        session.release();
        log.info("用户{}TTS会话已关闭", userId);
    }

    /** Spring容器销毁回调，关闭调度器、所有会话和NlsClient */
    @PreDestroy
    public void shutdown() {
        log.info("TTS连接管理器关闭，清理所有会话");

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        sessions.forEach((userId, session) -> {
            session.closed.set(true);
            session.synthesisId.incrementAndGet();
            stopPing(session);
            FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.getAndSet(null);
            if (synthesizer != null) {
                try {
                    synthesizer.close();
                } catch (Exception e) {
                    log.error("用户{}关闭TTS合成器失败", userId, e);
                }
            }
            if (session.latch != null) {
                session.latch.countDown();
            }
            session.release();
        });
        sessions.clear();

        if (nlsClient != null) {
            nlsClient.shutdown();
            log.info("TTS NlsClient已关闭");
        }
    }

    /** 用户维度的TTS会话上下文 */
    private static class TtsSession {
        /** 合成结果回调 */
        final TtsResultCallback callback;
        /** 合成标识，每次中断/异常时递增 */
        final AtomicInteger synthesisId;
        /** 当前合成器引用 */
        final AtomicReference<FlowingSpeechSynthesizer> synthesizerRef;
        /** 完成信号量 */
        volatile CountDownLatch latch;
        /** 合成锁，0=空闲/1=合成中 */
        final AtomicInteger busyFlag = new AtomicInteger(0);
        /** 会话关闭标记 */
        final AtomicBoolean closed = new AtomicBoolean(false);
        /** 最后活跃时间（毫秒） */
        volatile long lastActiveTime;
        /** 当前合成器创建时的synthesisId快照 */
        volatile int synthesizerCreationId;
        /** 最后一次send/ping的时间戳（毫秒） */
        volatile long lastSendTime;
        /** 保活ping定时任务句柄 */
        volatile ScheduledFuture<?> pingFuture;
        /** 处理中标记 */
        final AtomicBoolean processFlag = new AtomicBoolean(false);

        TtsSession(TtsResultCallback callback) {
            this.callback = callback;
            this.synthesisId = new AtomicInteger(0);
            this.synthesizerRef = new AtomicReference<>();
            this.latch = new CountDownLatch(1);
            this.lastActiveTime = System.currentTimeMillis();
            this.synthesizerCreationId = -1;
            this.lastSendTime = 0L;
        }

        /**
         * CAS获取合成锁，0→1表示从空闲转为合成中
         *
         * @return true=获取成功，false=已有合成任务正在进行
         */
        boolean tryAcquire() {
            return busyFlag.compareAndSet(0, 1);
        }

        /** 释放合成锁，将状态从1重置为0 */
        void release() {
            busyFlag.set(0);
        }

        /** 重置完成信号量 */
        void resetLatch() {
            this.latch = new CountDownLatch(1);
        }
    }
}