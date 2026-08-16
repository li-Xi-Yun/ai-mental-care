package org.lixiyun.server.infrastructure.audio;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.properity.AsrProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ASR长连接管理器
 * <p>基于阿里云NLS SDK实现WebSocket长连接复用，
 * 按用户ID维护独立的ASR连接实例，支持多轮语音识别而无需反复建连。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *     <li><b>注册</b>：为用户异步发起ASR WebSocket长连接，注册识别结果回调</li>
 *     <li><b>发送音频帧</b>：复用已有连接持续发送音频数据</li>
 *     <li><b>中断</b>：结束当前识别轮次（finish-task）并自动开启下一轮（run-task），连接全程复用</li>
 *     <li><b>取消</b>：关闭连接并清理所有资源</li>
 * </ul>
 *
 * <h3>安全保障</h3>
 * <ul>
 *     <li>最大并发连接数限制（{@value #MAX_SESSIONS}），容量检查在compute内原子执行</li>
 *     <li>空闲会话超时自动淘汰（{@value #IDLE_TIMEOUT_SECONDS}s）</li>
 *     <li>回调关闭保护：session关闭后丢弃所有SDK回调事件</li>
 *     <li>原子会话生命周期管理：使用{@code compute}消除竞态</li>
 *     <li>中断防重入：{@code interrupting}原子标记防止并发中断导致SDK状态混乱</li>
 * </ul>
 *
 * <h3>生命周期时序</h3>
 * <pre>
 * Spring容器启动
 *   └─ init()
 *       ├─ initNlsClient()          ← 获取Token、创建NlsClient
 *       ├─ schedule(cleanIdleSessions) ← 每60s扫描淘汰空闲会话
 *       └─ schedule(refreshToken)   ← 每23h自动刷新Token
 *
 * 业务调用
 *   ├─ register(userId, callback)   ← 建连 + 启动识别
 *   ├─ sendAudio(userId, data)      ← 持续发送音频帧
 *   ├─ interrupt(userId)            ← stop + start（轮次切换，连接复用）
 *   └─ cancel(userId)               ← 关闭连接 + 清理资源
 *
 * Spring容器关闭
 *   └─ shutdown()
 *       ├─ scheduler.shutdownNow()
 *       ├─ 遍历关闭所有AsrSession
 *       └─ nlsClient.shutdown()
 * </pre>
 *
 * <h3>配置项</h3>
 * <pre>
 * nls:
 *   app-key:              # 阿里云NLS应用AppKey（在智能语音交互控制台创建）
 *   access-key-id:        # 阿里云AccessKey ID
 *   access-key-secret:    # 阿里云AccessKey Secret
 *   gateway-url:          # NLS网关地址，默认wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1
 *   token-refresh-hours:  # Token自动刷新间隔（小时），默认23
 * </pre>
 *
 * @author lixiyun
 * @since 2026-08-16
 */
@Slf4j
@Component
public class AsrConnectionManager {

    /** 最大并发ASR会话数 */
    public static final int MAX_SESSIONS = 100;
    /** 空闲会话超时时间（秒） */
    public static final int IDLE_TIMEOUT_SECONDS = 60;
    /** 空闲会话检查间隔（秒） */
    private static final int IDLE_CHECK_INTERVAL_SECONDS = 60;
    /** 用户ASR会话注册表 */
    private final ConcurrentHashMap<Long, AsrSession> sessions = new ConcurrentHashMap<>();
    /** 全局NLS客户端 */
    private volatile NlsClient nlsClient;
    /** 定时任务调度器 */
    private ScheduledExecutorService scheduler;

    private final AsrProperties asrProperties;

    public AsrConnectionManager(AsrProperties asrProperties) {
        this.asrProperties = asrProperties;
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
            Thread t = new Thread(r, "asr-idle-check");
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
                asrProperties.getTokenRefreshHours(),
                asrProperties.getTokenRefreshHours(),
                TimeUnit.HOURS
        );

        log.info("ASR连接管理器初始化完成，最大会话数：{}，空闲超时：{}s", MAX_SESSIONS, IDLE_TIMEOUT_SECONDS);
    }

    /** 初始化NLS客户端，获取Token并创建NlsClient，若存在旧实例则先shutdown */
    private void initNlsClient() {
        AccessToken accessToken = new AccessToken(asrProperties.getAccessKeyId(), asrProperties.getAccessKeySecret());
        try {
            accessToken.apply();
            log.info("NLS Token获取成功，过期时间：{}", accessToken.getExpireTime());
        } catch (IOException e) {
            log.error("NLS Token获取失败", e);
            throw new RuntimeException("NLS Token获取失败", e);
        }

        NlsClient client;
        String gatewayUrl = asrProperties.getGatewayUrl();
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

        log.info("NlsClient初始化完成，gatewayUrl：{}", gatewayUrl.isEmpty() ? "默认" : gatewayUrl);
    }

    /** 刷新NLS Token，委托initNlsClient()完成，失败时下个周期自动重试 */
    private void refreshToken() {
        try {
            log.info("开始刷新NLS Token");
            initNlsClient();
            markSessionsForReconnect();
            log.info("NLS Token刷新成功，已标记所有活跃会话待重建");
        } catch (Exception e) {
            log.error("NLS Token刷新失败，将在下个周期重试", e);
        }
    }

    /** 标记所有活跃会话待重建连接（Token刷新后调用） */
    private void markSessionsForReconnect() {
        sessions.forEach((userId, session) -> {
            if (!session.closed.get()) {
                session.needsReconnect.set(true);
                log.debug("[ASR] 用户{}会话已标记待重建（Token已刷新）", userId);
            }
        });
    }

    /** 清理空闲超时的ASR会话 */
    private void cleanIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.forEach((userId, session) -> {
            long idleSeconds = (now - session.lastActiveTime) / 1000;
            if (idleSeconds > IDLE_TIMEOUT_SECONDS) {
                log.info("用户{}ASR会话空闲超时（{}s > {}s），自动淘汰", userId, idleSeconds, IDLE_TIMEOUT_SECONDS);
                cancel(userId);
            }
        });
    }

    /** ASR识别结果回调接口，所有回调在SDK WebSocket线程中执行 */
    public interface AsrResultCallback {

        /**
         * 中间识别结果回调
         *
         * @param text         当前中间识别文本
         * @param sentenceIndex 句子编号，从1开始递增
         */
        default void onIntermediateResult(String text, int sentenceIndex) {
        }

        /**
         * 识别开始回调
         *
         * @param taskId 服务端分配的识别任务ID
         */
        default void onTranscriberStart(String taskId) {
        }

        /**
         * 一句话开始回调（服务端智能断句）
         *
         * @param text 该句初始识别文本
         * @param sentenceIndex 句子编号，从1开始递增
         */
        default void onSentenceBegin(String text, int sentenceIndex) {
        }

        /**
         * 一句话结束回调
         *
         * @param text          该句最终识别文本
         * @param sentenceIndex 句子编号，从1开始递增
         * @param beginTime     该句在音频流中的开始时间（毫秒）
         * @param time          当前已处理的音频时长（毫秒）
         * @param confidence    该句识别置信度，0.0~1.0
         */
        default void onSentenceEnd(String text, int sentenceIndex, long beginTime, long time, double confidence) {
        }

        /** 整轮识别完毕回调 */
        default void onComplete() {
        }

        /**
         * 识别失败回调
         *
         * @param taskId     识别任务ID
         * @param statusText 错误状态描述
         */
        default void onError(String taskId, String statusText) {
        }
    }

    /** ASR会话内部实体，封装单个用户的ASR长连接上下文 */
    private static class AsrSession {
        /** SDK实时语音识别器 */
        final SpeechTranscriber transcriber;
        /** 业务结果回调 */
        final AsrResultCallback callback;
        /** 会话关闭标记 */
        final AtomicBoolean closed = new AtomicBoolean(false);
        /** 重建连接标记 */
        final AtomicBoolean needsReconnect = new AtomicBoolean(false);
        /** 最后活跃时间（毫秒） */
        volatile long lastActiveTime;

        /**
         * 构造ASR会话
         *
         * @param transcriber SDK识别器实例
         * @param callback    业务结果回调
         */
        AsrSession(SpeechTranscriber transcriber, AsrResultCallback callback) {
            this.transcriber = transcriber;
            this.callback = callback;
            this.lastActiveTime = System.currentTimeMillis();
        }
    }

    /**
     * 创建SDK识别监听器，桥接到业务回调，每个入口先检查会话关闭状态
     *
     * @param userId   用户ID
     * @param callback 业务结果回调
     * @return SDK监听器实例
     */
    private SpeechTranscriberListener createListener(Long userId, AsrResultCallback callback) {
        return new SpeechTranscriberListener() {
            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                if (isSessionClosed(userId)) {
                    return;
                }
                log.debug("[ASR] 用户{}中间结果，index：{}，text：{}", userId, response.getTransSentenceIndex(), response.getTransSentenceText());
                callback.onIntermediateResult(response.getTransSentenceText(), response.getTransSentenceIndex());
            }

            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                if (isSessionClosed(userId)) {
                    return;
                }
                log.debug("[ASR] 用户{}识别开始，taskId：{}，status：{}", userId, response.getTaskId(), response.getStatus());
                callback.onTranscriberStart(response.getTaskId());
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                if (isSessionClosed(userId)) {
                    return;
                }
                log.debug("[ASR] 用户{}句子开始，index：{}", userId, response.getTransSentenceIndex());
                callback.onSentenceBegin(response.getTransSentenceText(), response.getTransSentenceIndex());
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                if (isSessionClosed(userId)) {
                    return;
                }
                log.debug("[ASR] 用户{}句子结束，index：{}，text：{}，confidence：{}", userId, response.getTransSentenceIndex(), response.getTransSentenceText(), response.getConfidence());
                callback.onSentenceEnd(
                        response.getTransSentenceText(),
                        response.getTransSentenceIndex(),
                        response.getSentenceBeginTime(),
                        response.getTransSentenceTime(),
                        response.getConfidence()
                );
            }

            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                if (isSessionClosed(userId)) {
                    return;
                }
                log.debug("[ASR] 用户{}识别完毕，taskId：{}，status：{}", userId, response.getTaskId(), response.getStatus());
                callback.onComplete();
                checkAndScheduleReconnect(userId);
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                if (isSessionClosed(userId)) {
                    return;
                }
                log.error("[ASR] 用户{}识别失败，taskId：{}，status：{}，statusText：{}", userId, response.getTaskId(), response.getStatus(), response.getStatusText());
                callback.onError(response.getTaskId(), response.getStatusText());
                checkAndScheduleReconnect(userId);
            }
        };
    }

    /**
     * 检查用户ASR会话是否已关闭
     *
     * @param userId 用户ID
     * @return {@code true}表示会话已关闭或不存在
     */
    private boolean isSessionClosed(Long userId) {
        AsrSession session = sessions.get(userId);
        return session == null || session.closed.get();
    }

    /**
     * 配置SpeechTranscriber识别参数（PCM/16K/中间结果/标点/关闭ITN）
     *
     * @param transcriber 待配置的SDK识别器实例
     */
    private void configureTranscriber(SpeechTranscriber transcriber) {
        transcriber.setAppKey(asrProperties.getAppKey());
        transcriber.setFormat(InputFormatEnum.PCM);
        transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
        transcriber.setEnableIntermediateResult(true);
        transcriber.setEnablePunctuation(true);
        transcriber.setEnableITN(false);
        // transcriber.addCustomedParam("max_sentence_silence", 600);
        // transcriber.addCustomedParam("enable_semantic_sentence_detection", false);
        // transcriber.addCustomedParam("disfluency", true);
        // transcriber.addCustomedParam("enable_words", true);
        // transcriber.addCustomedParam("speech_noise_threshold", 0.3);
    }

    /**
     * 为用户建立ASR WebSocket长连接，注册识别结果回调
     *
     * @param userId   用户ID
     * @param callback 识别结果回调
     * @throws BusinessException 当并发连接数达到上限时
     * @throws RuntimeException  当ASR连接启动失败时
     */
    public void register(Long userId, AsrResultCallback callback) {
        sessions.compute(userId, (k, existing) -> {
            if (existing != null && !existing.closed.get()) {
                log.warn("用户{}ASR会话已存在，先关闭旧会话", userId);
                closeSession(existing, userId);
            }

            if (sessions.size() >= MAX_SESSIONS) {
                log.error("ASR并发连接数已达上限{}", MAX_SESSIONS);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }

            SpeechTranscriberListener listener = createListener(userId, callback);
            SpeechTranscriber transcriber = null;
            boolean createSuccess = false;

            try {
                transcriber = new SpeechTranscriber(nlsClient, listener);
                configureTranscriber(transcriber);
                transcriber.start();
                createSuccess = true;
                log.info("用户{}ASR长连接注册成功", userId);
            } catch (Exception e) {
                log.error("用户{}ASR长连接启动失败", userId, e);
                throw new RuntimeException("ASR连接启动失败", e);
            } finally {
                // 只有创建流程失败，才执行关闭
                if (!createSuccess && transcriber != null) {
                    try {
                        transcriber.close();
                    } catch (Exception ex) {
                        log.error("用户{}ASR启动失败，关闭transcriber异常", userId, ex);
                    }
                }
            }

            return new AsrSession(transcriber, callback);
        });
    }

    /**
     * 发送音频帧（全量发送）
     *
     * @param userId 用户ID
     * @param data   音频数据字节数组
     */
    public void sendAudio(Long userId, byte[] data) {
        sendAudio(userId, data, data.length);
    }

    /**
     * 发送音频帧（部分发送），会话不存在或已关闭时静默忽略
     *
     * @param userId 用户ID
     * @param data   音频数据字节数组
     * @param length 实际发送长度
     */
    public void sendAudio(Long userId, byte[] data, int length) {
        AsrSession session = sessions.get(userId);
        if (session == null || session.closed.get()) {
            log.warn("用户{}ASR会话不存在或已关闭，忽略音频帧（长度：{}）", userId, length);
            return;
        }
        try {
            session.transcriber.send(data, length);
            session.lastActiveTime = System.currentTimeMillis();
            log.debug("用户{}ASR音频帧发送成功，长度：{}", userId, length);
        } catch (Exception e) {
            log.error("用户{}ASR音频帧发送失败", userId, e);
        }
    }

    /**
     * 关闭连接并清理所有资源，幂等操作
     *
     * @param userId 用户ID
     */
    public void cancel(Long userId) {
        sessions.computeIfPresent(userId, (k, session) -> {
            if (!session.closed.compareAndSet(false, true)) {
                log.debug("用户{}ASR会话已关闭，忽略取消请求", userId);
                return null;
            }
            closeSession(session, userId);
            return null;
        });
    }

    /**
     * 检查用户ASR会话是否活跃
     *
     * @param userId 用户ID
     * @return {@code true}表示会话活跃
     */
    public boolean isSessionActive(Long userId) {
        AsrSession session = sessions.get(userId);
        return session != null && !session.closed.get();
    }

    /**
     * 检查会话是否需要重建连接，若需要则提交到调度器异步执行
     *
     * @param userId 用户ID
     */
    private void checkAndScheduleReconnect(Long userId) {
        AsrSession session = sessions.get(userId);
        if (session == null || session.closed.get()) {
            return;
        }
        if (session.needsReconnect.compareAndSet(true, false)) {
            log.info("[ASR] 用户{}会话标记待重建，提交重建任务到调度器", userId);
            scheduler.execute(() -> attemptReconnect(userId));
        }
    }

    /**
     * 原子替换重建ASR连接（Token刷新后调用）
     *
     * @param userId 用户ID
     */
    private void attemptReconnect(Long userId) {
        AsrSession currentSession = sessions.get(userId);
        if (currentSession == null || currentSession.closed.get()) {
            log.debug("[ASR] 用户{}会话不存在或已关闭，跳过重建", userId);
            return;
        }

        sessions.compute(userId, (k, existing) -> {
            if (existing != currentSession || existing.closed.get()) {
                return existing;
            }

            existing.closed.set(true);
            try {
                existing.transcriber.close();
                log.info("[ASR] 用户{}旧ASR连接已关闭（Token刷新重建）", userId);
            } catch (Exception e) {
                log.error("[ASR] 用户{}旧ASR连接关闭异常（Token刷新重建）", userId, e);
            }

            SpeechTranscriberListener listener = createListener(userId, existing.callback);
            SpeechTranscriber newTranscriber = null;
            boolean createSuccess = false;

            try {
                newTranscriber = new SpeechTranscriber(nlsClient, listener);
                configureTranscriber(newTranscriber);
                newTranscriber.start();
                createSuccess = true;
                log.info("[ASR] 用户{}新ASR连接已建立（Token刷新重建）", userId);
            } catch (Exception e) {
                log.error("[ASR] 用户{}新ASR连接创建失败（Token刷新重建）", userId, e);
                throw new RuntimeException("Token刷新重建连接失败", e);
            } finally {
                if (!createSuccess && newTranscriber != null) {
                    try {
                        newTranscriber.close();
                    } catch (Exception ex) {
                        log.error("[ASR] 用户{}新ASR连接创建失败，关闭transcriber异常", userId, ex);
                    }
                }
            }

            AsrSession newSession = new AsrSession(newTranscriber, existing.callback);
            newSession.lastActiveTime = existing.lastActiveTime;
            return newSession;
        });
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
     * 关闭ASR会话
     *
     * @param session 待关闭的会话实例
     * @param userId  用户ID
     */
    private void closeSession(AsrSession session, Long userId) {
        session.closed.set(true);
        try {
            session.transcriber.close();
            log.info("用户{}ASR会话已关闭", userId);
        } catch (Exception e) {
            log.error("用户{}ASR会话关闭失败", userId, e);
        }
    }

    /**
     * 关闭并从注册表移除ASR会话
     *
     * @param userId  用户ID
     * @param session 待关闭并移除的会话实例
     */
    private void closeAndRemoveSession(Long userId, AsrSession session) {
        session.closed.set(true);
        try {
            session.transcriber.close();
        } catch (Exception e) {
            log.error("用户{}ASR会话关闭失败", userId, e);
        }
        sessions.remove(userId);
        log.info("用户{}ASR会话已关闭并移除", userId);
    }

    /** Spring容器销毁回调，关闭调度器、所有会话和NlsClient */
    @PreDestroy
    public void shutdown() {
        log.info("ASR连接管理器关闭，清理所有会话");

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        sessions.forEach((userId, session) -> {
            session.closed.set(true);
            try {
                session.transcriber.close();
            } catch (Exception e) {
                log.error("用户{}ASR会话关闭失败", userId, e);
            }
        });
        sessions.clear();

        if (nlsClient != null) {
            nlsClient.shutdown();
            log.info("NlsClient已关闭");
        }
    }
}