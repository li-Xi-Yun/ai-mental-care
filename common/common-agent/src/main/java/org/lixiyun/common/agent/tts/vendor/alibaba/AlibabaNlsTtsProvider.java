package org.lixiyun.common.agent.tts.vendor.alibaba;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizerResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tts.api.ITtsProvider;
import org.lixiyun.common.agent.tts.api.TtsProviderType;
import org.lixiyun.common.agent.tts.api.TtsResultCallback;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阿里云NLS TTS Provider
 * <p>基于阿里云NLS SDK的{@link FlowingSpeechSynthesizer}实现按<strong>抽象会话ID</strong>维度的TTS流式语音合成。
 * 每轮合成会独立建立WebSocket连接（NLS TTS协议要求），连接在合成完成后自动关闭。</p>
 * <p>通过{@code @ConditionalOnProperty(name = "tts.provider", havingValue = "alibaba_nls")}
 * 控制按需加载，仅在配置指定阿里云时生效。</p>
 * <p><b>注意：</b>本Provider不感知用户概念，仅通过抽象sessionId管理TTS会话。
 * userId ↔ sessionId映射由防腐层（TtsConnectionManager）负责。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *     <li><b>创建会话</b>：生成抽象sessionId，绑定音频输出流与完成回调</li>
 *     <li><b>执行合成</b>：新建WebSocket连接将文本转为语音（异步非阻塞）</li>
 *     <li><b>中断</b>：取消当前正在进行的语音合成，保留会话上下文，允许再次合成</li>
 *     <li><b>取消</b>：关闭会话并清理所有资源</li>
 * </ul>
 *
 * <h3>安全保障</h3>
 * <ul>
 *     <li>最大并发会话数限制，容量检查在compute内原子执行</li>
 *     <li>空闲会话超时自动淘汰</li>
 *     <li>回调关闭保护：session关闭后丢弃所有SDK回调事件</li>
 *     <li>原子会话生命周期管理：使用{@code compute}消除竞态</li>
 *     <li>同一会话同一时刻仅允许一路TTS合成（CAS自旋保证）</li>
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
 *       └─ schedule(refreshToken)       ← 每N小时自动刷新Token
 *
 * 业务调用
 *   ├─ createSession(callback)        ← 创建抽象会话，返回sessionId
 *   ├─ sendTextSegment(sessionId, ...) ← 反复调用，流式发送文本分片
 *   ├─ finishSynthesis(sessionId)     ← 文本输出结束，等待收尾音频
 *   ├─ interrupt(sessionId)           ← 中断当前合成（会话保留）
 *   └─ cancel(sessionId)              ← 关闭会话 + 清理资源
 *
 * Spring容器关闭
 *   └─ shutdown()
 *       ├─ scheduler.shutdownNow()
 *       ├─ 遍历关闭所有TtsSession
 *       └─ nlsClient.shutdown()
 * </pre>
 *
 * @author lixiyun
 * @since 2026-09-25
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tts.provider", havingValue = "alibaba_nls", matchIfMissing = true)
public class AlibabaNlsTtsProvider implements ITtsProvider {

    /** 最大并发TTS会话数 */
    private static final int MAX_SESSIONS = 100;
    /** 空闲会话超时时间（秒） */
    private static final int IDLE_TIMEOUT_SECONDS = 60;
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

    /** 抽象TTS会话注册表（key=sessionId） */
    private final ConcurrentHashMap<String, TtsSession> sessions = new ConcurrentHashMap<>();
    /** 全局NLS客户端 */
    private volatile NlsClient nlsClient;
    /** 定时任务调度器 */
    private ScheduledExecutorService scheduler;

    /** 阿里云TTS配置 */
    private final AlibabaNlsTtsConfig config;

    /**
     * 构造函数，注入阿里云TTS配置
     *
     * @param config 阿里云TTS配置
     */
    public AlibabaNlsTtsProvider(AlibabaNlsTtsConfig config) {
        this.config = config;
    }

    @Override
    @PostConstruct
    public void init() {
        try {
            initNlsClient();
        } catch (IOException e) {
            log.error("[阿里云 TTS] NLS Token获取失败", e);
            throw new RuntimeException("[阿里云 TTS] NLS Token获取失败", e);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tts-alibaba-idle-check");
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
                config.getTokenRefreshHours(),
                config.getTokenRefreshHours(),
                TimeUnit.HOURS
        );

        log.info("[阿里云 TTS] Provider初始化完成，最大会话数：{}，空闲超时：{}s", MAX_SESSIONS, IDLE_TIMEOUT_SECONDS);
    }

    @Override
    @PreDestroy
    public void shutdown() {
        log.info("[阿里云 TTS] Provider关闭，清理所有会话");

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        sessions.forEach((sessionId, session) -> {
            session.closed.set(true);
            session.synthesisId.incrementAndGet();
            stopPing(session);
            FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.getAndSet(null);
            if (synthesizer != null) {
                try {
                    synthesizer.close();
                } catch (Exception e) {
                    log.error("[阿里云 TTS] 会话{}关闭TTS合成器失败", sessionId, e);
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
            log.info("[阿里云 TTS] NlsClient已关闭");
        }
    }

    @Override
    public String createSession(TtsResultCallback callback) {
        String sessionId = UUID.randomUUID().toString();
        sessions.compute(sessionId, (k, existing) -> {
            if (existing != null && !existing.closed.get()) {
                log.warn("[阿里云 TTS] 会话{}已存在，先关闭旧会话", sessionId);
                closeSession(existing, sessionId);
            }

            if (sessions.size() >= MAX_SESSIONS) {
                log.error("[阿里云 TTS] 并发会话数已达上限{}", MAX_SESSIONS);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }

            TtsSession newSession = new TtsSession(callback);
            log.info("[阿里云 TTS] 会话{}注册成功", sessionId);
            return newSession;
        });
        return sessionId;
    }

    @Override
    public void sendTextSegment(String sessionId, String text) {
        TtsSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.error("[阿里云 TTS] 会话{}未注册或已关闭，无法发送文本分片", sessionId);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
        }

        if (text == null || text.isBlank()) {
            return;
        }

        FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.get();

        if (synthesizer == null) {
            if (!session.tryAcquire()) {
                log.error("[阿里云 TTS] 会话{}合成冲突，当前有正在进行的合成任务", sessionId);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }

            session.processFlag.set(false);
            int currentSynthesisId = session.synthesisId.incrementAndGet();
            try {
                session.resetLatch();
                FlowingSpeechSynthesizerListener listener = createListener(session, sessionId, currentSynthesisId);
                synthesizer = createSynthesizer(listener);
                session.synthesizerRef.set(synthesizer);
                session.synthesizerCreationId = currentSynthesisId;

                long start = System.currentTimeMillis();
                synthesizer.start();
                synthesizer.setMinSendIntervalMS(MIN_SEND_INTERVAL_MS);
                session.lastSendTime = System.currentTimeMillis();
                startPingTask(sessionId, session);
                log.info("[阿里云 TTS] 会话{}合成器连接建立，耗时{}ms", sessionId, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("[阿里云 TTS] 会话{}创建合成器或建立连接失败", sessionId, e);
                session.synthesizerRef.set(null);
                session.synthesizerCreationId = -1;
                session.release();
                throw new RuntimeException("[阿里云 TTS] 合成器初始化失败: " + e.getMessage(), e);
            }
        }

        if (session.synthesisId.get() != session.synthesizerCreationId) {
            log.debug("[阿里云 TTS] 会话{}合成已被中断，忽略文本分片发送", sessionId);
            return;
        }

        try {
            if (session.processFlag.get()) {
                log.debug("[阿里云 TTS] 会话{}当前正在处理文本分片，忽略本次发送", sessionId);
                return;
            }
            synthesizer.send(text);
            session.lastSendTime = System.currentTimeMillis();
            session.lastActiveTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("[阿里云 TTS] 会话{}发送文本分片失败", sessionId, e);
            throw new RuntimeException("[阿里云 TTS] 文本分片发送失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void finishSynthesis(String sessionId) {
        TtsSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.error("[阿里云 TTS] 会话{}未注册或已关闭，无法完成合成", sessionId);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
        }

        FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.get();
        if (synthesizer == null) {
            log.warn("[阿里云 TTS] 会话{}无活跃合成器，无需完成合成", sessionId);
            session.release();
            return;
        }

        session.processFlag.set(true);
        try {
            synthesizer.stop();

            boolean completed = session.latch.await(SYNTHESIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("[阿里云 TTS] 会话{}合成超时（{}s），强制中断", sessionId, SYNTHESIS_TIMEOUT_SECONDS);
            }
        } catch (Exception e) {
            log.error("[阿里云 TTS] 会话{}完成合成异常", sessionId, e);
            session.synthesisId.incrementAndGet();
            throw new RuntimeException("[阿里云 TTS] 完成合成失败: " + e.getMessage(), e);
        } finally {
            stopPing(session);
            closeSynthesizer(synthesizer, sessionId, "finish");
            session.synthesizerRef.set(null);
            session.release();
            session.lastActiveTime = System.currentTimeMillis();
        }
    }

    @Override
    public void interrupt(String sessionId) {
        TtsSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.warn("[阿里云 TTS] 会话{}无TTS会话或已关闭，忽略中断", sessionId);
            return;
        }

        session.synthesisId.incrementAndGet();
        stopPing(session);
        FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.getAndSet(null);
        if (synthesizer != null) {
            try {
                synthesizer.close();
                log.info("[阿里云 TTS] 会话{}合成器已关闭（中断）", sessionId);
            } catch (Exception e) {
                log.error("[阿里云 TTS] 会话{}中断合成器失败", sessionId, e);
            }
        }

        if (session.latch != null) {
            session.latch.countDown();
        }

        session.release();
        log.info("[阿里云 TTS] 会话{}合成已中断", sessionId);
    }

    @Override
    public void cancel(String sessionId) {
        sessions.computeIfPresent(sessionId, (k, session) -> {
            if (!session.closed.compareAndSet(false, true)) {
                log.debug("[阿里云 TTS] 会话{}已关闭，忽略取消请求", sessionId);
                return null;
            }

            session.synthesisId.incrementAndGet();
            stopPing(session);
            FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.getAndSet(null);
            if (synthesizer != null) {
                try {
                    synthesizer.close();
                } catch (Exception e) {
                    log.error("[阿里云 TTS] 会话{}关闭合成器失败", sessionId, e);
                }
            }

            if (session.latch != null) {
                session.latch.countDown();
            }

            session.release();
            log.info("[阿里云 TTS] 会话{}已取消并清理", sessionId);
            return null;
        });
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        TtsSession session = sessions.get(sessionId);
        return session != null && !session.closed.get();
    }

    @Override
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(s -> !s.closed.get()).count();
    }

    @Override
    public TtsProviderType getProviderType() {
        return TtsProviderType.ALIBABA_NLS;
    }

    /**
     * 初始化NLS客户端，获取AccessToken并创建NlsClient实例，若存在旧实例则先关闭
     *
     * @throws IOException 若Token获取失败
     */
    private void initNlsClient() throws IOException {
        AccessToken accessToken = new AccessToken(config.getAccessKeyId(), config.getAccessKeySecret());
        accessToken.apply();
        log.info("[阿里云 TTS] NLS Token获取成功，过期时间：{}", accessToken.getExpireTime());

        NlsClient client;
        String gatewayUrl = config.getGatewayUrl();
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

        log.info("[阿里云 TTS] NlsClient初始化完成，gatewayUrl：{}",
                (gatewayUrl == null || gatewayUrl.isEmpty()) ? "默认" : gatewayUrl);
    }

    /**
     * 刷新NLS AccessToken，刷新成功后中断所有活跃合成任务
     */
    private void refreshToken() {
        try {
            log.info("[阿里云 TTS] 开始刷新NLS Token");
            initNlsClient();
            interruptActiveSyntheses();
            log.info("[阿里云 TTS] NLS Token刷新成功，已中断所有活跃合成任务");
        } catch (Exception e) {
            log.error("[阿里云 TTS] NLS Token刷新失败，将在下个周期重试", e);
        }
    }

    /**
     * 中断所有活跃的合成任务（Token刷新后调用）
     */
    private void interruptActiveSyntheses() {
        sessions.forEach((sessionId, session) -> {
            if (!session.closed.get() && session.busyFlag.get() == 1) {
                interrupt(sessionId);
                log.debug("[阿里云 TTS] 会话{}活跃合成已中断（Token已刷新）", sessionId);
            }
        });
    }

    /**
     * 清理空闲超时的TTS会话
     */
    private void cleanIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.forEach((sessionId, session) -> {
            long idleSeconds = (now - session.lastActiveTime) / 1000;
            if (idleSeconds > IDLE_TIMEOUT_SECONDS) {
                log.info("[阿里云 TTS] 会话{}空闲超时（{}s > {}s），自动淘汰",
                        sessionId, idleSeconds, IDLE_TIMEOUT_SECONDS);
                cancel(sessionId);
            }
        });
    }

    /**
     * 检查TTS会话是否已关闭
     *
     * @param sessionId 会话ID
     * @return {@code true}表示会话已关闭或不存在
     */
    private boolean isSessionClosed(String sessionId) {
        TtsSession session = sessions.get(sessionId);
        return session == null || session.closed.get();
    }

    /**
     * 创建TTS合成器回调监听器
     *
     * @param session           TTS会话
     * @param sessionId            会话ID
     * @param currentSynthesisId 当前合成标识，用于过期回调校验
     * @return TTS合成器回调监听器
     */
    private FlowingSpeechSynthesizerListener createListener(TtsSession session, String sessionId, int currentSynthesisId) {
        return new FlowingSpeechSynthesizerListener() {
            /** 是否首次接收到二进制音频数据 */
            private boolean firstRecvBinary = true;

            @Override
            public void onSynthesisStart(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(sessionId) || session.synthesisId.get() != currentSynthesisId) {
                    log.debug("[阿里云 TTS] 会话{}收到过期onSynthesisStart回调，忽略", sessionId);
                    return;
                }
                log.debug("[阿里云 TTS] 会话{}合成开始, status={}", sessionId, response.getStatus());
                session.callback.onSynthesisStart();
            }

            @Override
            public void onSentenceBegin(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(sessionId) || session.synthesisId.get() != currentSynthesisId) {
                    return;
                }
                log.debug("[阿里云 TTS] 会话{}句子开始, status={}", sessionId, response.getStatus());
                session.callback.onSentenceBegin();
            }

            @Override
            public void onSentenceEnd(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(sessionId) || session.synthesisId.get() != currentSynthesisId) {
                    return;
                }
                log.debug("[阿里云 TTS] 会话{}句子结束, subtitles={}", sessionId, response.getObject("subtitles"));
                session.callback.onSentenceEnd();
            }

            @Override
            public void onAudioData(ByteBuffer message) {
                if (isSessionClosed(sessionId) || session.synthesisId.get() != currentSynthesisId) {
                    log.debug("[阿里云 TTS] 会话{}收到过期音频数据，忽略", sessionId);
                    return;
                }
                try {
                    if (firstRecvBinary) {
                        firstRecvBinary = false;
                        log.info("[阿里云 TTS] 会话{}收到首包音频数据", sessionId);
                    }
                    byte[] bytesArray = new byte[message.remaining()];
                    message.get(bytesArray, 0, bytesArray.length);
                    session.callback.onAudioData(bytesArray);
                } catch (Exception e) {
                    log.error("[阿里云 TTS] 会话{}音频数据处理失败", sessionId, e);
                }
            }

            @Override
            public void onSentenceSynthesis(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(sessionId) || session.synthesisId.get() != currentSynthesisId) {
                    return;
                }
                log.debug("[阿里云 TTS] 会话{}增量时间戳, subtitles={}", sessionId, response.getObject("subtitles"));
                session.callback.onSentenceSynthesis();
            }

            @Override
            public void onSynthesisComplete(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(sessionId) || session.synthesisId.get() != currentSynthesisId) {
                    log.debug("[阿里云 TTS] 会话{}收到过期onSynthesisComplete回调，忽略", sessionId);
                    return;
                }
                log.info("[阿里云 TTS] 会话{}TTS合成完成, status={}", sessionId, response.getStatus());
                session.latch.countDown();
                session.processFlag.set(false);
                session.callback.onSynthesisComplete();
            }

            @Override
            public void onFail(FlowingSpeechSynthesizerResponse response) {
                if (isSessionClosed(sessionId) || session.synthesisId.get() != currentSynthesisId) {
                    log.debug("[阿里云 TTS] 会话{}收到过期onFail回调，忽略", sessionId);
                    return;
                }
                log.error("[阿里云 TTS] 会话{}TTS合成失败, taskId={}, status={}, statusText={}",
                        sessionId, response.getTaskId(), response.getStatus(), response.getStatusText());
                session.latch.countDown();
                session.processFlag.set(false);
                session.callback.onFail(response.getTaskId(), response.getStatusText());
            }
        };
    }

    /**
     * 创建TTS合成器实例，根据配置设置AppKey、格式、采样率等参数
     *
     * @param listener TTS合成器回调监听器
     * @return TTS合成器实例
     * @throws Exception 若创建合成器失败
     */
    private FlowingSpeechSynthesizer createSynthesizer(FlowingSpeechSynthesizerListener listener) throws Exception {
        FlowingSpeechSynthesizer synthesizer = new FlowingSpeechSynthesizer(nlsClient, listener);
        synthesizer.setAppKey(config.getAppKey());
        if (config.getFormat() != null) {
            if (config.getFormat().equalsIgnoreCase(OutputFormatEnum.WAV.name())) {
                synthesizer.setFormat(OutputFormatEnum.WAV);
            } else if (config.getFormat().equalsIgnoreCase(OutputFormatEnum.MP3.name())) {
                synthesizer.setFormat(OutputFormatEnum.MP3);
            }
        }
        synthesizer.setSampleRate(config.getSampleRate());
        synthesizer.setVoice(config.getVoice());
        synthesizer.setVolume(config.getVolume());
        synthesizer.setPitchRate(config.getPitchRate());
        synthesizer.setSpeechRate(config.getSpeechRate());

        return synthesizer;
    }

    /**
     * 启动保活ping定时任务，空闲超时后自动发送WebSocket ping帧
     *
     * @param sessionId  会话ID
     * @param session TTS会话实例
     */
    private void startPingTask(String sessionId, TtsSession session) {
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
                        log.debug("[阿里云 TTS] 会话{}发送保活ping（空闲{}ms）", sessionId, elapsed);
                    }
                }
            } catch (Exception e) {
                log.debug("[阿里云 TTS] 会话{}保活ping发送失败（合成器可能已关闭）", sessionId);
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
     * @param sessionId      会话ID
     * @param reason      关闭原因
     */
    private void closeSynthesizer(FlowingSpeechSynthesizer synthesizer, String sessionId, String reason) {
        if (synthesizer == null) {
            return;
        }
        try {
            synthesizer.close();
            log.debug("[阿里云 TTS] 会话{}合成器已关闭, reason={}", sessionId, reason);
        } catch (Exception e) {
            log.error("[阿里云 TTS] 会话{}关闭合成器失败, reason={}", sessionId, reason, e);
        }
    }

    /**
     * 关闭TTS会话：标记关闭、停止ping、关闭合成器、释放信号量
     *
     * @param session 待关闭的会话实例
     * @param sessionId  会话ID
     */
    private void closeSession(TtsSession session, String sessionId) {
        session.closed.set(true);
        session.synthesisId.incrementAndGet();
        stopPing(session);
        FlowingSpeechSynthesizer synthesizer = session.synthesizerRef.getAndSet(null);
        if (synthesizer != null) {
            try {
                synthesizer.close();
            } catch (Exception e) {
                log.error("[阿里云 TTS] 会话{}关闭合成器失败", sessionId, e);
            }
        }
        if (session.latch != null) {
            session.latch.countDown();
        }
        session.release();
        log.info("[阿里云 TTS] 会话{}已关闭", sessionId);
    }

    /**
     * 抽象会话维度的TTS会话上下文
     * <p>封装合成器引用、状态标记和并发控制字段，保证线程安全。</p>
     */
    private static class TtsSession {
        /** 合成结果回调 */
        final TtsResultCallback callback;
        /** 合成标识，每次中断/异常时递增 */
        final AtomicInteger synthesisId;
        /** 当前合成器引用 */
        final AtomicReference<FlowingSpeechSynthesizer> synthesizerRef;
        /** 完成信号量，用于阻塞等待合成完成 */
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
        /** 处理中标记，防止并发发送 */
        final AtomicBoolean processFlag = new AtomicBoolean(false);

        /**
         * 构造TTS会话
         *
         * @param callback 合成结果回调
         */
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