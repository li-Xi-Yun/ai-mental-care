package org.lixiyun.common.agent.asr.vendor.alibaba;

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
import org.lixiyun.common.agent.asr.api.AsrProviderType;
import org.lixiyun.common.agent.asr.api.AsrResultCallback;
import org.lixiyun.common.agent.asr.api.IAsrProvider;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 阿里云NLS ASR Provider
 * <p>基于阿里云NLS SDK的{@link SpeechTranscriber}实现按<strong>抽象会话ID</strong>维度的ASR实时语音识别。
 * 为用户建立WebSocket长连接，支持多轮语音识别而无需反复建连。</p>
 * <p>通过{@code @ConditionalOnProperty(name = "asr.provider", havingValue = "alibaba_nls", matchIfMissing = true)}
 * 控制按需加载，仅在配置指定阿里云时生效（默认激活）。</p>
 * <p><b>注意：</b>本Provider不感知用户概念，仅通过抽象sessionId管理ASR会话。
 * userId ↔ sessionId映射由防腐层（AsrConnectionManager）负责。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *     <li><b>创建会话</b>：生成抽象sessionId，建立WebSocket长连接并启动识别</li>
 *     <li><b>发送音频帧</b>：复用已有连接持续发送音频数据</li>
 *     <li><b>取消</b>：关闭连接并清理所有资源</li>
 * </ul>
 *
 * <h3>安全保障</h3>
 * <ul>
 *     <li>最大并发会话数限制，容量检查在compute内原子执行</li>
 *     <li>空闲会话超时自动淘汰</li>
 *     <li>回调关闭保护：session关闭后丢弃所有SDK回调事件</li>
 *     <li>原子会话生命周期管理：使用{@code compute}消除竞态</li>
 *     <li>Token刷新后自动标记会话待重建连接</li>
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
 *   ├─ sendAudio(sessionId, data)     ← 持续发送音频帧
 *   └─ cancel(sessionId)              ← 关闭会话 + 清理资源
 *
 * Spring容器关闭
 *   └─ shutdown()
 *       ├─ scheduler.shutdownNow()
 *       ├─ 遍历关闭所有AsrSession
 *       └─ nlsClient.shutdown()
 * </pre>
 *
 * @author lixiyun
 * @since 2026-09-26
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "asr.provider", havingValue = "alibaba_nls", matchIfMissing = true)
public class AlibabaNlsAsrProvider implements IAsrProvider {

    /** 最大并发ASR会话数 */
    private static final int MAX_SESSIONS = 100;
    /** 空闲会话超时时间（秒） */
    private static final int IDLE_TIMEOUT_SECONDS = 60;
    /** 空闲会话检查间隔（秒） */
    private static final int IDLE_CHECK_INTERVAL_SECONDS = 60;
    /** 网关空闲超时状态码，非致命错误，连接可自动重建 */
    private static final int STATUS_IDLE_TIMEOUT = 40000004;

    /** 抽象ASR会话注册表（key=sessionId） */
    private final ConcurrentHashMap<String, AsrSession> sessions = new ConcurrentHashMap<>();
    /** 全局NLS客户端 */
    private volatile NlsClient nlsClient;
    /** 定时任务调度器 */
    private ScheduledExecutorService scheduler;

    /** 阿里云ASR配置 */
    private final AlibabaNlsAsrConfig config;

    /**
     * 构造函数，注入阿里云ASR配置
     *
     * @param config 阿里云ASR配置
     */
    public AlibabaNlsAsrProvider(AlibabaNlsAsrConfig config) {
        this.config = config;
    }

    @Override
    @PostConstruct
    public void init() {
        initNlsClient();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "asr-alibaba-idle-check");
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

        log.info("[阿里云 ASR] Provider初始化完成，最大会话数：{}，空闲超时：{}s", MAX_SESSIONS, IDLE_TIMEOUT_SECONDS);
    }

    @Override
    @PreDestroy
    public void shutdown() {
        log.info("[阿里云 ASR] Provider关闭，清理所有会话");

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        sessions.forEach((sessionId, session) -> {
            session.closed.set(true);
            try {
                session.transcriber.close();
            } catch (Exception e) {
                log.error("[阿里云 ASR] 会话{}关闭失败", sessionId, e);
            }
        });
        sessions.clear();

        if (nlsClient != null) {
            nlsClient.shutdown();
            log.info("[阿里云 ASR] NlsClient已关闭");
        }
    }

    @Override
    public String createSession(AsrResultCallback callback) {
        String sessionId = UUID.randomUUID().toString();
        sessions.compute(sessionId, (k, existing) -> {
            if (existing != null && !existing.closed.get()) {
                log.warn("[阿里云 ASR] 会话{}已存在，先关闭旧会话", sessionId);
                closeSession(existing, sessionId);
            }

            if (sessions.size() >= MAX_SESSIONS) {
                log.error("[阿里云 ASR] 并发会话数已达上限{}", MAX_SESSIONS);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }

            SpeechTranscriberListener listener = createListener(sessionId, callback);
            SpeechTranscriber transcriber = null;
            boolean createSuccess = false;

            try {
                transcriber = new SpeechTranscriber(nlsClient, listener);
                configureTranscriber(transcriber);
                transcriber.start();
                createSuccess = true;
                log.info("[阿里云 ASR] 会话{}长连接注册成功", sessionId);
            } catch (Exception e) {
                log.error("[阿里云 ASR] 会话{}长连接启动失败", sessionId, e);
                throw new RuntimeException("ASR连接启动失败", e);
            } finally {
                if (!createSuccess && transcriber != null) {
                    try {
                        transcriber.close();
                    } catch (Exception ex) {
                        log.error("[阿里云 ASR] 会话{}启动失败，关闭transcriber异常", sessionId, ex);
                    }
                }
            }

            return new AsrSession(transcriber, callback);
        });
        return sessionId;
    }

    @Override
    public void sendAudio(String sessionId, byte[] data) {
        sendAudio(sessionId, data, data.length);
    }

    @Override
    public void sendAudio(String sessionId, byte[] data, int length) {
        AsrSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.warn("[阿里云 ASR] 会话{}不存在或已关闭，忽略音频帧（长度：{}）", sessionId, length);
            return;
        }
        try {
            session.transcriber.send(data, length);
            session.lastActiveTime = System.currentTimeMillis();
            log.debug("[阿里云 ASR] 会话{}音频帧发送成功，长度：{}", sessionId, length);
        } catch (Exception e) {
            log.error("[阿里云 ASR] 会话{}音频帧发送失败", sessionId, e);
        }
    }

    @Override
    public void cancel(String sessionId) {
        sessions.computeIfPresent(sessionId, (k, session) -> {
            if (!session.closed.compareAndSet(false, true)) {
                log.debug("[阿里云 ASR] 会话{}已关闭，忽略取消请求", sessionId);
                return null;
            }
            closeSession(session, sessionId);
            return null;
        });
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        AsrSession session = sessions.get(sessionId);
        return session != null && !session.closed.get();
    }

    @Override
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(s -> !s.closed.get()).count();
    }

    @Override
    public AsrProviderType getProviderType() {
        return AsrProviderType.ALIBABA_NLS;
    }

    // ======================== 内部实现 ========================

    /** 初始化NLS客户端，获取Token并创建NlsClient，若存在旧实例则先shutdown */
    private void initNlsClient() {
        AccessToken accessToken = new AccessToken(config.getAccessKeyId(), config.getAccessKeySecret());
        try {
            accessToken.apply();
            log.info("[阿里云 ASR] NLS Token获取成功，过期时间：{}", accessToken.getExpireTime());
        } catch (IOException e) {
            log.error("[阿里云 ASR] NLS Token获取失败", e);
            throw new RuntimeException("NLS Token获取失败", e);
        }

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

        log.info("[阿里云 ASR] NlsClient初始化完成，gatewayUrl：{}",
                (gatewayUrl == null || gatewayUrl.isEmpty()) ? "默认" : gatewayUrl);
    }

    /** 刷新NLS Token，委托initNlsClient()完成，失败时下个周期自动重试 */
    private void refreshToken() {
        try {
            log.info("[阿里云 ASR] 开始刷新NLS Token");
            initNlsClient();
            markSessionsForReconnect();
            log.info("[阿里云 ASR] NLS Token刷新成功，已标记所有活跃会话待重建");
        } catch (Exception e) {
            log.error("[阿里云 ASR] NLS Token刷新失败，将在下个周期重试", e);
        }
    }

    /** 标记所有活跃会话待重建连接（Token刷新后调用） */
    private void markSessionsForReconnect() {
        sessions.forEach((sessionId, session) -> {
            if (!session.closed.get()) {
                session.needsReconnect.set(true);
                log.debug("[阿里云 ASR] 会话{}已标记待重建（Token已刷新）", sessionId);
            }
        });
    }

    /** 清理空闲超时的ASR会话 */
    private void cleanIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.forEach((sessionId, session) -> {
            long idleSeconds = (now - session.lastActiveTime) / 1000;
            if (idleSeconds > IDLE_TIMEOUT_SECONDS) {
                log.info("[阿里云 ASR] 会话{}空闲超时（{}s > {}s），自动淘汰", sessionId, idleSeconds, IDLE_TIMEOUT_SECONDS);
                cancel(sessionId);
            }
        });
    }

    /**
     * 配置SpeechTranscriber识别参数（PCM/16K/中间结果/标点/关闭ITN）
     *
     * @param transcriber 待配置的SDK识别器实例
     */
    private void configureTranscriber(SpeechTranscriber transcriber) {
        transcriber.setAppKey(config.getAppKey());
        transcriber.setFormat(InputFormatEnum.PCM);
        transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
        transcriber.setEnableIntermediateResult(true);
        transcriber.setEnablePunctuation(true);
        transcriber.setEnableITN(false);
    }

    /**
     * 创建SDK识别监听器，桥接到业务回调，每个入口先检查会话关闭状态
     *
     * @param sessionId 会话ID
     * @param callback  业务结果回调
     * @return SDK监听器实例
     */
    private SpeechTranscriberListener createListener(String sessionId, AsrResultCallback callback) {
        return new SpeechTranscriberListener() {
            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                if (isSessionClosed(sessionId)) {
                    return;
                }
                log.debug("[阿里云 ASR] 会话{}中间结果，index：{}，text：{}",
                        sessionId, response.getTransSentenceIndex(), response.getTransSentenceText());
                callback.onIntermediateResult(response.getTransSentenceText(), response.getTransSentenceIndex());
            }

            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                if (isSessionClosed(sessionId)) {
                    return;
                }
                log.debug("[阿里云 ASR] 会话{}识别开始，taskId：{}，status：{}",
                        sessionId, response.getTaskId(), response.getStatus());
                callback.onTranscriberStart(response.getTaskId());
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                if (isSessionClosed(sessionId)) {
                    return;
                }
                log.debug("[阿里云 ASR] 会话{}句子开始，index：{}",
                        sessionId, response.getTransSentenceIndex());
                callback.onSentenceBegin(response.getTransSentenceText(), response.getTransSentenceIndex());
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                if (isSessionClosed(sessionId)) {
                    return;
                }
                log.debug("[阿里云 ASR] 会话{}句子结束，index：{}，text：{}，confidence：{}",
                        sessionId, response.getTransSentenceIndex(), response.getTransSentenceText(),
                        response.getConfidence());
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
                if (isSessionClosed(sessionId)) {
                    return;
                }
                log.debug("[阿里云 ASR] 会话{}识别完毕，taskId：{}，status：{}",
                        sessionId, response.getTaskId(), response.getStatus());
                callback.onComplete();
                checkAndScheduleReconnect(sessionId);
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                if (isSessionClosed(sessionId)) {
                    return;
                }
                int status = response.getStatus();
                String statusText = response.getStatusText();
                log.error("[阿里云 ASR] 会话{}识别失败，taskId：{}，status：{}，statusText：{}",
                        sessionId, response.getTaskId(), status, statusText);
                callback.onError(response.getTaskId(), statusText);

                if (status == STATUS_IDLE_TIMEOUT) {
                    AsrSession session = sessions.get(sessionId);
                    if (session != null && !session.closed.get()) {
                        log.info("[阿里云 ASR] 会话{}网关空闲超时，自动重建连接", sessionId);
                        session.needsReconnect.set(true);
                    }
                }

                checkAndScheduleReconnect(sessionId);
            }
        };
    }

    /**
     * 检查ASR会话是否已关闭
     *
     * @param sessionId 会话ID
     * @return {@code true}表示会话已关闭或不存在
     */
    private boolean isSessionClosed(String sessionId) {
        AsrSession session = sessions.get(sessionId);
        return session == null || session.closed.get();
    }

    /**
     * 检查会话是否需要重建连接，若需要则提交到调度器异步执行
     *
     * @param sessionId 会话ID
     */
    private void checkAndScheduleReconnect(String sessionId) {
        AsrSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            return;
        }
        if (session.needsReconnect.compareAndSet(true, false)) {
            log.info("[阿里云 ASR] 会话{}标记待重建，提交重建任务到调度器", sessionId);
            scheduler.execute(() -> attemptReconnect(sessionId));
        }
    }

    /**
     * 原子替换重建ASR连接（Token刷新后调用）
     *
     * @param sessionId 会话ID
     */
    private void attemptReconnect(String sessionId) {
        AsrSession currentSession = sessions.get(sessionId);
        if (currentSession == null || currentSession.closed.get()) {
            log.debug("[阿里云 ASR] 会话{}不存在或已关闭，跳过重建", sessionId);
            return;
        }

        sessions.compute(sessionId, (k, existing) -> {
            if (existing != currentSession || existing.closed.get()) {
                return existing;
            }

            existing.closed.set(true);
            try {
                existing.transcriber.close();
                log.info("[阿里云 ASR] 会话{}旧连接已关闭（连接重建）", sessionId);
            } catch (Exception e) {
                log.error("[阿里云 ASR] 会话{}旧连接关闭异常（连接重建）", sessionId, e);
            }

            SpeechTranscriberListener listener = createListener(sessionId, existing.callback);
            SpeechTranscriber newTranscriber = null;
            boolean createSuccess = false;

            try {
                newTranscriber = new SpeechTranscriber(nlsClient, listener);
                configureTranscriber(newTranscriber);
                newTranscriber.start();
                createSuccess = true;
                log.info("[阿里云 ASR] 会话{}新连接已建立（连接重建）", sessionId);
            } catch (Exception e) {
                log.error("[阿里云 ASR] 会话{}新连接创建失败（连接重建）", sessionId, e);
                throw new RuntimeException("连接重建失败", e);
            } finally {
                if (!createSuccess && newTranscriber != null) {
                    try {
                        newTranscriber.close();
                    } catch (Exception ex) {
                        log.error("[阿里云 ASR] 会话{}新连接创建失败，关闭transcriber异常", sessionId, ex);
                    }
                }
            }

            AsrSession newSession = new AsrSession(newTranscriber, existing.callback);
            newSession.lastActiveTime = existing.lastActiveTime;
            return newSession;
        });
    }

    /**
     * 关闭ASR会话
     *
     * @param session   待关闭的会话实例
     * @param sessionId 会话ID
     */
    private void closeSession(AsrSession session, String sessionId) {
        session.closed.set(true);
        try {
            session.transcriber.close();
            log.info("[阿里云 ASR] 会话{}已关闭", sessionId);
        } catch (Exception e) {
            log.error("[阿里云 ASR] 会话{}关闭失败", sessionId, e);
        }
    }

    // ======================== 内部实体 ========================

    /** ASR会话内部实体，封装单个会话的ASR长连接上下文 */
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
}