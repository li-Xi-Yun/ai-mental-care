package org.lixiyun.common.agent.tts.vendor.volcengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tts.api.ITtsProvider;
import org.lixiyun.common.agent.tts.api.TtsProviderType;
import org.lixiyun.common.agent.tts.api.TtsResultCallback;
import org.lixiyun.common.agent.tts.vendor.volcengine.protocol.EventType;
import org.lixiyun.common.agent.tts.vendor.volcengine.protocol.Message;
import org.lixiyun.common.agent.tts.vendor.volcengine.protocol.MsgType;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 火山引擎双向流式TTS Provider
 * <p>基于火山引擎V3 WebSocket双向流式API，使用自定义二进制协议封装，
 * 通过{@link VolcWebSocketClient}维护单一长连接，支持多抽象会话并发TTS合成。</p>
 * <p>通过{@code @ConditionalOnProperty(name = "tts.provider", havingValue = "volcengine")}
 * 控制按需加载，仅在配置指定火山引擎时生效。</p>
 * <p><b>注意：</b>本Provider不感知用户概念，仅通过抽象sessionId管理TTS会话。
 * userId ↔ sessionId映射由防腐层（TtsConnectionManager）负责。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *     <li><b>长连接复用</b>：全局维护一条WebSocket连接，按火山引擎协议层sessionId区分会话</li>
 *     <li><b>双向流式</b>：逐字发送文本即可获得实时语音流</li>
 *     <li><b>中断</b>：发送CancelSession帧取消当前合成，保留会话可再次合成</li>
 * </ul>
 *
 * <h3>交互时序（单次合成）</h3>
 * <pre>
 * sendTextSegment(首次)
 *   └─ StartSession → [等待SessionStarted] → TaskRequest(文本)
 * sendTextSegment(后续)
 *   └─ TaskRequest(文本)
 * finishSynthesis()
 *   └─ FinishSession → [接收音频帧...] → SessionFinished
 * </pre>
 *
 * <h3>调度模型</h3>
 * <pre>
 * 主线程(API调用)                Dispatcher线程
 * sendStartSession ──→ queue ──→ 收到SessionStarted → countDown(startedLatch)
 *    await(startedLatch) ←──┘
 * sendTaskRequest ────→ queue
 *    ...
 * sendFinishSession ──→ queue ──→ 收到TTS_RESPONSE → callback.onAudioData()
 *    await(finishedLatch)  ←── 收到SessionFinished → countDown(finishedLatch)
 * </pre>
 *
 * @author lixiyun
 * @since 2026-09-25
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tts.provider", havingValue = "volcengine")
public class VolcengineTtsProvider implements ITtsProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 最大并发TTS会话数 */
    private static final int MAX_SESSIONS = 100;
    /** 空闲会话超时时间（秒） */
    private static final int IDLE_TIMEOUT_SECONDS = 60;
    /** 空闲会话检查间隔（秒） */
    private static final int IDLE_CHECK_INTERVAL_SECONDS = 60;
    /** 启动/停止会话等待超时（秒） */
    private static final int SESSION_WAIT_TIMEOUT_SECONDS = 30;
    /** 合成完成等待超时（秒） */
    private static final long SYNTHESIS_TIMEOUT_SECONDS = 60L;

    /** 抽象TTS会话注册表（key=抽象sessionId） */
    private final ConcurrentHashMap<String, VolcSession> sessions = new ConcurrentHashMap<>();
    /** 火山引擎协议层sessionId → 抽象sessionId反向映射，用于Dispatcher路由 */
    private final ConcurrentHashMap<String, String> protocolSessionIdToSessionId = new ConcurrentHashMap<>();

    /** WebSocket客户端 */
    private volatile VolcWebSocketClient wsClient;
    /** 定时任务调度器 */
    private ScheduledExecutorService scheduler;
    /** Provider运行状态 */
    private volatile boolean running = false;
    /** 消息分发线程 */
    private Thread dispatcherThread;

    /** 火山引擎TTS配置 */
    private final VolcengineTtsConfig config;

    /**
     * 构造函数，注入火山引擎TTS配置
     *
     * @param config 火山引擎TTS配置
     */
    public VolcengineTtsProvider(VolcengineTtsConfig config) {
        this.config = config;
    }

    @Override
    @PostConstruct
    public void init() {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("[火山引擎 TTS] api-key 未配置，跳过初始化。请在 application.yaml 中配置 tts.volcengine.api-key");
            return;
        }
        if (config.getResourceId() == null || config.getResourceId().isBlank()) {
            log.warn("[火山引擎 TTS] resource-id 未配置，跳过初始化。请在 application.yaml 中配置 tts.volcengine.resource-id");
            return;
        }

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Api-Key", config.getApiKey());
            headers.put("X-Api-Resource-Id", config.getResourceId());
            headers.put("X-Api-Connect-Id", UUID.randomUUID().toString());
            headers.put("X-Control-Require-Usage-Tokens-Return", "*");

            wsClient = new VolcWebSocketClient(new URI(config.getEndpoint()), headers);
            wsClient.connectBlocking();

            log.info("[火山引擎 TTS] WebSocket连接已建立, endpoint={}", config.getEndpoint());

            wsClient.sendStartConnection();
            wsClient.waitForMessage(MsgType.FULL_SERVER_RESPONSE, EventType.CONNECTION_STARTED);
            if (!wsClient.isConnected()) {
                log.error("[火山引擎 TTS] WebSocket连接建立后立即断开，请检查 api-key、resource-id 和网络连通性");
                throw new RuntimeException("[火山引擎 TTS] WebSocket连接建立失败: 请检查 tts.volcengine.api-key 和 tts.volcengine.resource-id 配置是否正确");
            }
            log.info("[火山引擎 TTS] StartConnection成功");
        } catch (Exception e) {
            log.error("[火山引擎 TTS] WebSocket连接或StartConnection失败", e);
            throw new RuntimeException("[火山引擎 TTS] 初始化失败: " + e.getMessage(), e);
        }

        running = true;
        dispatcherThread = new Thread(this::dispatchMessages, "tts-volcengine-dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tts-volcengine-idle-check");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                this::cleanIdleSessions,
                IDLE_CHECK_INTERVAL_SECONDS,
                IDLE_CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("[火山引擎 TTS] Provider初始化完成，最大会话数：{}", MAX_SESSIONS);
    }

    @Override
    @PreDestroy
    public void shutdown() {
        log.info("[火山引擎 TTS] Provider关闭，清理所有资源");
        running = false;

        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        sessions.forEach((sessionId, session) -> {
            session.closed.set(true);
            if (session.startedLatch != null) {
                session.startedLatch.countDown();
            }
            if (session.finishedLatch != null) {
                session.finishedLatch.countDown();
            }
        });
        sessions.clear();
        protocolSessionIdToSessionId.clear();

        if (wsClient != null && wsClient.isConnected()) {
            try {
                wsClient.sendFinishConnection();
            } catch (Exception e) {
                log.error("[火山引擎 TTS] 发送FinishConnection失败", e);
            }
            wsClient.close();
            log.info("[火山引擎 TTS] WebSocket连接已关闭");
        }
    }

    @Override
    public String createSession(TtsResultCallback callback) {
        if (!running) {
            throw new IllegalStateException("[火山引擎 TTS] Provider未初始化，请检查 tts.volcengine.api-key 和 tts.volcengine.resource-id 配置");
        }
        String sessionId = UUID.randomUUID().toString();
        sessions.compute(sessionId, (k, existing) -> {
            if (existing != null && !existing.closed.get()) {
                log.warn("[火山引擎 TTS] 会话{}已存在，先关闭旧会话", sessionId);
                closeSession(existing, sessionId);
            }

            if (sessions.size() >= MAX_SESSIONS) {
                log.error("[火山引擎 TTS] 并发会话数已达上限{}", MAX_SESSIONS);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }

            VolcSession newSession = new VolcSession(callback);
            log.info("[火山引擎 TTS] 会话{}注册成功", sessionId);
            return newSession;
        });
        return sessionId;
    }

    @Override
    public void sendTextSegment(String sessionId, String text) {
        if (!running) {
            throw new IllegalStateException("[火山引擎 TTS] Provider未初始化，请检查 tts.volcengine.api-key 和 tts.volcengine.resource-id 配置");
        }
        VolcSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.error("[火山引擎 TTS] 会话{}未注册或已关闭", sessionId);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
        }

        if (text == null || text.isBlank()) {
            return;
        }

        if (!session.sessionActive.get()) {
            if (!session.tryAcquire()) {
                log.error("[火山引擎 TTS] 会话{}合成冲突", sessionId);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }

            try {
                String protocolSessionId = UUID.randomUUID().toString();
                session.protocolSessionId = protocolSessionId;
                session.startedLatch = new CountDownLatch(1);
                session.finishedLatch = new CountDownLatch(1);
                session.finalProtocolSessionId = protocolSessionId;
                protocolSessionIdToSessionId.put(protocolSessionId, sessionId);

                Map<String, Object> startReq = buildStartSessionRequest(sessionId);
                wsClient.sendStartSession(objectMapper.writeValueAsBytes(startReq), protocolSessionId);

                boolean started = session.startedLatch.await(SESSION_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!started) {
                    log.error("[火山引擎 TTS] 会话{}等待SessionStarted超时", sessionId);
                    protocolSessionIdToSessionId.remove(protocolSessionId);
                    session.release();
                    throw new RuntimeException("[火山引擎 TTS] Session启动超时");
                }
                session.sessionActive.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                session.release();
                throw new RuntimeException("[火山引擎 TTS] Session启动被中断", e);
            } catch (Exception e) {
                log.error("[火山引擎 TTS] 会话{}启动Session失败", sessionId, e);
                protocolSessionIdToSessionId.remove(session.protocolSessionId);
                session.release();
                throw new RuntimeException("[火山引擎 TTS] Session启动失败: " + e.getMessage(), e);
            }
        }

        try {
            Map<String, Object> taskReq = buildTaskRequest(text);
            wsClient.sendTaskRequest(objectMapper.writeValueAsBytes(taskReq), session.protocolSessionId);
            session.lastActiveTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("[火山引擎 TTS] 会话{}发送TaskRequest失败", sessionId, e);
            throw new RuntimeException("[火山引擎 TTS] 文本发送失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void finishSynthesis(String sessionId) {
        if (!running) {
            throw new IllegalStateException("[火山引擎 TTS] Provider未初始化，请检查 tts.volcengine.api-key 和 tts.volcengine.resource-id 配置");
        }
        VolcSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.error("[火山引擎 TTS] 会话{}未注册或已关闭", sessionId);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
        }

        if (!session.sessionActive.get()) {
            log.warn("[火山引擎 TTS] 会话{}无活跃TTS会话，无需完成合成", sessionId);
            session.release();
            return;
        }

        try {
            wsClient.sendFinishSession(session.protocolSessionId);

            boolean completed = session.finishedLatch.await(SYNTHESIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("[火山引擎 TTS] 会话{}合成完成超时（{}s）", sessionId, SYNTHESIS_TIMEOUT_SECONDS);
            }
        } catch (Exception e) {
            log.error("[火山引擎 TTS] 会话{}完成合成异常", sessionId, e);
        } finally {
            protocolSessionIdToSessionId.remove(session.protocolSessionId);
            session.sessionActive.set(false);
            session.release();
            session.lastActiveTime = System.currentTimeMillis();
        }
    }

    @Override
    public void interrupt(String sessionId) {
        if (!running) {
            throw new IllegalStateException("[火山引擎 TTS] Provider未初始化，请检查 tts.volcengine.api-key 和 tts.volcengine.resource-id 配置");
        }
        VolcSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.warn("[火山引擎 TTS] 会话{}无TTS会话或已关闭，忽略中断", sessionId);
            return;
        }

        if (!session.sessionActive.get()) {
            return;
        }

        try {
            session.canceledLatch = new CountDownLatch(1);
            wsClient.sendCancelSession(session.protocolSessionId);

            boolean canceled = session.canceledLatch.await(SESSION_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!canceled) {
                log.warn("[火山引擎 TTS] 会话{}等待SessionCanceled超时", sessionId);
            }
            log.info("[火山引擎 TTS] 会话{}合成已中断", sessionId);
        } catch (Exception e) {
            log.error("[火山引擎 TTS] 会话{}中断合成异常", sessionId, e);
        } finally {
            protocolSessionIdToSessionId.remove(session.protocolSessionId);
            session.sessionActive.set(false);
            session.canceledLatch = null;
            session.release();
        }
    }

    @Override
    public void cancel(String sessionId) {
        if (!running) {
            throw new IllegalStateException("[火山引擎 TTS] Provider未初始化，请检查 tts.volcengine.api-key 和 tts.volcengine.resource-id 配置");
        }
        sessions.computeIfPresent(sessionId, (k, session) -> {
            if (!session.closed.compareAndSet(false, true)) {
                log.debug("[火山引擎 TTS] 会话{}已关闭，忽略取消请求", sessionId);
                return null;
            }

            if (session.sessionActive.get() && session.protocolSessionId != null) {
                try {
                    wsClient.sendCancelSession(session.protocolSessionId);
                } catch (Exception e) {
                    log.error("[火山引擎 TTS] 会话{}发送CancelSession失败", sessionId, e);
                }
            }

            protocolSessionIdToSessionId.remove(session.protocolSessionId);
            if (session.finishedLatch != null) {
                session.finishedLatch.countDown();
            }
            if (session.startedLatch != null) {
                session.startedLatch.countDown();
            }
            session.release();
            log.info("[火山引擎 TTS] 会话{}已取消并清理", sessionId);
            return null;
        });
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        VolcSession session = sessions.get(sessionId);
        return session != null && !session.closed.get();
    }

    @Override
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(s -> !s.closed.get()).count();
    }

    @Override
    public TtsProviderType getProviderType() {
        return TtsProviderType.VOLCENGINE;
    }

    // ======================== 消息分发 ========================

    /**
     * Dispatcher线程主循环：从消息队列中读取消息，按协议层sessionId路由到对应抽象会话
     */
    private void dispatchMessages() {
        log.info("[火山引擎 TTS] 消息分发线程启动");
        while (running) {
            try {
                Message msg = wsClient.receiveMessage(1, TimeUnit.SECONDS);
                if (msg == null) {
                    continue;
                }

                MsgType type = msg.getType();
                if (type == MsgType.ERROR) {
                    handleErrorMessage(msg);
                    continue;
                }

                String protocolSessionId = msg.getSessionId();
                if (protocolSessionId == null || protocolSessionId.isEmpty()) {
                    log.debug("[火山引擎 TTS] 收到无sessionId的消息: {}", msg);
                    continue;
                }

                String sessionId = protocolSessionIdToSessionId.get(protocolSessionId);
                if (sessionId == null) {
                    log.debug("[火山引擎 TTS] 收到未知协议层sessionId的消息: protocolSessionId={}", protocolSessionId);
                    continue;
                }

                VolcSession session = sessions.get(sessionId);
                if (session == null || session.closed.get()) {
                    continue;
                }

                dispatchToSession(session, sessionId, msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[火山引擎 TTS] 消息分发异常", e);
            }
        }
        log.info("[火山引擎 TTS] 消息分发线程退出");
    }

    /** 将消息路由到对应会话的回调或信号量 */
    private void dispatchToSession(VolcSession session, String sessionId, Message msg) {
        EventType event = msg.getEvent();
        if (event == null) {
            log.debug("[火山引擎 TTS] 会话{}收到无event的消息", sessionId);
            return;
        }

        switch (event) {
            case SESSION_STARTED -> {
                log.debug("[火山引擎 TTS] 会话{}收到SessionStarted", sessionId);
                if (session.startedLatch != null) {
                    session.startedLatch.countDown();
                }
                session.callback.onSynthesisStart();
            }
            case TTS_SENTENCE_START -> {
                log.debug("[火山引擎 TTS] 会话{}收到TTSSentenceStart", sessionId);
                session.callback.onSentenceBegin();
            }
            case TTS_SENTENCE_END -> {
                log.debug("[火山引擎 TTS] 会话{}收到TTSSentenceEnd", sessionId);
                session.callback.onSentenceEnd();
            }
            case TTS_RESPONSE -> {
                byte[] payload = msg.getPayload();
                if (payload != null && payload.length > 0) {
                    if (!session.firstAudioReceived.getAndSet(true)) {
                        log.info("[火山引擎 TTS] 会话{}收到首包音频数据", sessionId);
                    }
                    session.callback.onAudioData(payload);
                }
            }
            case SESSION_FINISHED -> {
                log.info("[火山引擎 TTS] 会话{}收到SessionFinished", sessionId);
                if (session.finishedLatch != null) {
                    session.finishedLatch.countDown();
                }
                session.callback.onSynthesisComplete();
            }
            case SESSION_CANCELED -> {
                log.info("[火山引擎 TTS] 会话{}收到SessionCanceled", sessionId);
                if (session.canceledLatch != null) {
                    session.canceledLatch.countDown();
                }
            }
            case SESSION_FAILED -> {
                log.error("[火山引擎 TTS] 会话{}收到SessionFailed", sessionId);
                if (session.finishedLatch != null) {
                    session.finishedLatch.countDown();
                }
                session.callback.onFail("", "SessionFailed");
            }
            default -> log.debug("[火山引擎 TTS] 会话{}收到未处理的事件: {}", sessionId, event);
        }
    }

    /** 处理错误消息帧 */
    private void handleErrorMessage(Message msg) {
        String payload = msg.getPayload() != null ? new String(msg.getPayload()) : "unknown";
        log.error("[火山引擎 TTS] 收到错误消息: errorCode={}, payload={}", msg.getErrorCode(), payload);

        String protocolSessionId = msg.getSessionId();
        if (protocolSessionId != null) {
            String sessionId = protocolSessionIdToSessionId.get(protocolSessionId);
            if (sessionId != null) {
                VolcSession session = sessions.get(sessionId);
                if (session != null && !session.closed.get()) {
                    session.callback.onFail(String.valueOf(msg.getErrorCode()), payload);
                    if (session.finishedLatch != null) {
                        session.finishedLatch.countDown();
                    }
                }
            }
        }
    }

    // ======================== 请求构建 ========================

    /** 构建StartSession请求JSON，uid字段使用抽象sessionId */
    private Map<String, Object> buildStartSessionRequest(String sessionId) {
        Map<String, Object> reqParams = new HashMap<>();
        reqParams.put("speaker", config.getSpeaker());
        reqParams.put("audio_params", Map.of(
                "format", config.getFormat(),
                "sample_rate", config.getSampleRate(),
                "speech_rate", config.getSpeechRate(),
                "loudness_rate", config.getLoudnessRate()
        ));
        try {
            reqParams.put("additions", objectMapper.writeValueAsString(
                    Map.of("disable_markdown_filter", false)));
        } catch (Exception ignored) {
        }

        Map<String, Object> request = new HashMap<>();
        request.put("user", Map.of("uid", sessionId));
        request.put("event", EventType.START_SESSION.getValue());
        request.put("namespace", "BidirectionalTTS");
        request.put("req_params", reqParams);
        return request;
    }

    /** 构建TaskRequest请求JSON */
    private Map<String, Object> buildTaskRequest(String text) {
        Map<String, Object> request = new HashMap<>();
        request.put("event", EventType.TASK_REQUEST.getValue());
        request.put("namespace", "BidirectionalTTS");
        request.put("req_params", Map.of("text", text));
        return request;
    }

    // ======================== 会话清理 ========================

    /** 清除空闲超时的会话 */
    private void cleanIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.forEach((sessionId, session) -> {
            long idleSeconds = (now - session.lastActiveTime) / 1000;
            if (idleSeconds > IDLE_TIMEOUT_SECONDS) {
                log.info("[火山引擎 TTS] 会话{}空闲超时（{}s > {}s），自动淘汰",
                        sessionId, idleSeconds, IDLE_TIMEOUT_SECONDS);
                cancel(sessionId);
            }
        });
    }

    /** 关闭指定会话并清理资源 */
    private void closeSession(VolcSession session, String sessionId) {
        session.closed.set(true);
        protocolSessionIdToSessionId.remove(session.protocolSessionId);
        if (session.finishedLatch != null) {
            session.finishedLatch.countDown();
        }
        if (session.startedLatch != null) {
            session.startedLatch.countDown();
        }
        session.release();
        log.info("[火山引擎 TTS] 会话{}已关闭", sessionId);
    }

    // ======================== 内部类 ========================

    /**
     * 抽象会话维度的火山引擎TTS会话上下文
     */
    private static class VolcSession {
        /** 合成结果回调 */
        final TtsResultCallback callback;
        /** 火山引擎协议层SessionId（每轮合成重新生成） */
        volatile String protocolSessionId;
        /** 已确认的最终协议层sessionId（用于清理映射） */
        volatile String finalProtocolSessionId;
        /** 会话活跃标记 */
        final AtomicBoolean sessionActive = new AtomicBoolean(false);
        /** 会话关闭标记 */
        final AtomicBoolean closed = new AtomicBoolean(false);
        /** 合成锁，0=空闲/1=合成中 */
        final AtomicInteger busyFlag = new AtomicInteger(0);
        /** 是否已收到首包音频 */
        final AtomicBoolean firstAudioReceived = new AtomicBoolean(false);
        /** 最后活跃时间（毫秒） */
        volatile long lastActiveTime;
        /** SessionStarted信号量 */
        volatile CountDownLatch startedLatch;
        /** SessionFinished信号量 */
        volatile CountDownLatch finishedLatch;
        /** SessionCanceled信号量 */
        volatile CountDownLatch canceledLatch;

        /**
         * 构造火山引擎TTS会话
         *
         * @param callback 合成结果回调
         */
        VolcSession(TtsResultCallback callback) {
            this.callback = callback;
            this.lastActiveTime = System.currentTimeMillis();
        }

        /** CAS获取合成锁 */
        boolean tryAcquire() {
            return busyFlag.compareAndSet(0, 1);
        }

        /** 释放合成锁 */
        void release() {
            busyFlag.set(0);
        }
    }
}