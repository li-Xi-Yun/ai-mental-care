package org.lixiyun.common.agent.asr.vendor.volcengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.lixiyun.common.agent.asr.api.AsrProviderType;
import org.lixiyun.common.agent.asr.api.AsrResultCallback;
import org.lixiyun.common.agent.asr.api.IAsrProvider;
import org.lixiyun.common.agent.asr.model.AsrResult;
import org.lixiyun.common.agent.asr.vendor.volcengine.converter.VolcengineAsrConverter;
import org.lixiyun.common.agent.asr.vendor.volcengine.model.VolcengineAsrResult;
import org.lixiyun.common.agent.asr.vendor.volcengine.protocol.MessageFlag;
import org.lixiyun.common.agent.asr.vendor.volcengine.protocol.MessageType;
import org.lixiyun.common.agent.asr.vendor.volcengine.protocol.ProtocolCodec;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.lixiyun.common.agent.asr.common.SilencePcmGenerator.generate;

/**
 * 火山引擎ASR Provider
 * <p>基于火山引擎SAUC大模型实时语音识别API，使用java-websocket实现
 * 按<strong>抽象会话ID</strong>维度的ASR实时语音识别。
 * 每个会话独立建立一条WebSocket长连接，通过自定义二进制协议传输音频数据。</p>
 * <p>通过{@code @ConditionalOnProperty(name = "asr.provider", havingValue = "volcengine")}
 * 控制按需加载，仅在配置指定火山引擎时生效。</p>
 * <p><b>注意：</b>本Provider不感知用户概念，仅通过抽象sessionId管理ASR会话。
 * userId ↔ sessionId映射由防腐层（AsrConnectionManager）负责。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *     <li><b>创建会话</b>：生成抽象sessionId，建立WebSocket长连接、发送FullClientRequest启动识别</li>
 *     <li><b>发送音频帧</b>：将原始PCM音频GZIP压缩后封装为二进制帧发送</li>
 *     <li><b>响应解析</b>：接收并解析二进制帧，通过转换器转换为通用业务实体类后回调</li>
 *     <li><b>保活</b>：长时间无人说话时自动发送静音PCM音频数据，维持ASR识别会话</li>
 *     <li><b>取消</b>：发送最后一帧并关闭连接、清理资源</li>
 * </ul>
 *
 * <h3>二进制协议说明</h3>
 * <pre>
 * 客户端 → 服务端（FullClientRequest）：
 *   [4字节头][4字节载荷长度][GZIP压缩的JSON载荷]
 *   载荷内容：{"audio":{...},"request":{...}}
 *
 * 客户端 → 服务端（音频帧）：
 *   [4字节头][4字节序号][4字节载荷长度][GZIP压缩的PCM音频]
 *
 * 服务端 → 客户端：
 *   [4字节头][4字节序号][4字节事件][4字节载荷长度][GZIP压缩的JSON载荷]
 * </pre>
 *
 * <h3>安全保障</h3>
 * <ul>
 *     <li>最大并发会话数限制</li>
 *     <li>空闲会话超时自动淘汰</li>
 *     <li>回调关闭保护：session关闭后丢弃所有WS回调事件</li>
 *     <li>原子会话生命周期管理：使用{@code compute}消除竞态</li>
 * </ul>
 *
 * <h3>生命周期时序</h3>
 * <pre>
 * Spring容器启动
 *   └─ init()
 *       ├─ schedule(cleanIdleSessions)  ← 每60s扫描淘汰空闲会话
 *       └─ schedule(checkAndKeepAlive)  ← 每1s检查需要保活的会话
 *
 * 业务调用
 *   ├─ createSession(callback)          ← 创建WebSocket连接 + FullClientRequest
 *   ├─ sendAudio(sessionId, data)       ← 持续发送音频二进制帧
 *   └─ cancel(sessionId)                ← 发送最后一帧 + 关闭WebSocket连接
 *
 * Spring容器关闭
 *   └─ shutdown()
 *       ├─ scheduler.shutdownNow()
 *       └─ 遍历关闭所有WsSession
 * </pre>
 *
 * @author lixiyun
 * @since 2026-09-28
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "asr.provider", havingValue = "volcengine")
public class VolcengineAsrProvider implements IAsrProvider {

    /** 最大并发ASR会话数 */
    private static final int MAX_SESSIONS = 100;
    /** 默认空闲会话超时时间（毫秒），若session未指定idleTimeoutMs则使用此值 */
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 120_000;
    /** 空闲会话检查间隔（秒） */
    private static final int IDLE_CHECK_INTERVAL_SECONDS = 60;
    /** 保活：长时间无人说话时发送静音PCM音频数据，防止网关空闲断开 */
    private static final byte[] SILENT_PCM_CHUNK = generate(16000, 16, 1, 20);
    /** 保活：没有消息交互N毫秒后启动静音PCM保活 */
    private static final int PING_THRESHOLD_MS = 3000;
    /** 保活：每N毫秒发送一次静音PCM音频分片 */
    private static final int PING_INTERVAL_MS = 3000;
    /** 保活状态检查间隔（毫秒） */
    private static final int PING_CHECK_INTERVAL_MS = 1000;
    /** 音频帧序号起始值 */
    private static final int AUDIO_SEQUENCE_START = 1;
    /** 短连接：PCM音频分片大小（20ms @ 16kHz 16bit 单声道 = 640字节） */
    private static final int SHORT_AUDIO_CHUNK_SIZE = 640;
    /** 短连接：会话超时时间（ms），超时后强制关闭 */
    private static final int SHORT_SESSION_TIMEOUT_MS = 30_000;
    /** 短连接：音频发送前等待时间（ms），确保FullClientRequest已发送 */
    private static final int SHORT_AUDIO_SEND_DELAY_MS = 500;

    /** JSON序列化工具 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 抽象ASR会话注册表（key=sessionId） */
    private final ConcurrentHashMap<String, WsSession> sessions = new ConcurrentHashMap<>();
    /** 保活任务注册表（key=sessionId） */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> keepAliveTasks = new ConcurrentHashMap<>();
    /** 定时任务调度器 */
    private ScheduledExecutorService scheduler;
    /** 回调线程池：将耗时的业务回调从WebSocket接收线程中剥离，防止阻塞底层IO */
    private ExecutorService callbackExecutor;

    /** 火山引擎ASR密钥及音频配置 */
    private final VolcengineAsrConfig config;
    /** 火山引擎ASR请求可选参数 */
    private final AsrRequestProperties requestProperties;

    /**
     * 构造函数，注入火山引擎ASR配置
     *
     * @param config           密钥及音频配置
     * @param requestProperties 请求可选参数
     */
    public VolcengineAsrProvider(VolcengineAsrConfig config, AsrRequestProperties requestProperties) {
        this.config = config;
        this.requestProperties = requestProperties;
    }

    @Override
    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "asr-volcengine-maintenance");
            t.setDaemon(true);
            return t;
        });
        callbackExecutor = new ThreadPoolExecutor(
                4, 16,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "asr-volcengine-callback");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        scheduler.scheduleAtFixedRate(
                this::cleanIdleSessions,
                IDLE_CHECK_INTERVAL_SECONDS,
                IDLE_CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        scheduler.scheduleAtFixedRate(
                this::checkAndKeepAlive,
                PING_CHECK_INTERVAL_MS,
                PING_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        log.info("[火山引擎 ASR] Provider初始化完成，最大会话数：{}，默认空闲超时：{}ms，端点：{}",
                MAX_SESSIONS, DEFAULT_IDLE_TIMEOUT_MS, config.getEndpoint());
    }

    @Override
    @PreDestroy
    public void shutdown() {
        log.info("[火山引擎 ASR] Provider关闭，清理所有会话");

        keepAliveTasks.keySet().forEach(this::cancelKeepAlive);

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        if (callbackExecutor != null) {
            callbackExecutor.shutdown();
            try {
                if (!callbackExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    callbackExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                callbackExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        sessions.forEach((sessionId, session) -> {
            session.closed.set(true);
            try {
                session.wsClient.close();
            } catch (Exception e) {
                log.error("[火山引擎 ASR] 会话{}关闭失败", sessionId, e);
            }
        });
        sessions.clear();

        log.info("[火山引擎 ASR] Provider已关闭");
    }

    @Override
    public String createSession(AsrResultCallback callback) {
        return createSession(callback, DEFAULT_IDLE_TIMEOUT_MS);
    }

    @Override
    public String createSession(AsrResultCallback callback, long idleTimeoutMs) {
        String sessionId = UUID.randomUUID().toString();
        long effectiveTimeout = idleTimeoutMs > 0 ? idleTimeoutMs : DEFAULT_IDLE_TIMEOUT_MS;

        // 步骤1：原子检查（仅做内存判断，不在compute内执行阻塞IO）
        WsSession oldSession = sessions.compute(sessionId, (k, existing) -> {
            if (existing != null && !existing.closed.get()) {
                existing.closed.set(true);
            }
            if (sessions.size() >= MAX_SESSIONS) {
                log.error("[火山引擎 ASR] 并发会话数已达上限{}", MAX_SESSIONS);
                throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
            }
            return existing;
        });

        // 步骤2：无锁创建并建立连接（阻塞IO不持有CHM桶锁）
        WsSession session = new WsSession(callback, effectiveTimeout, sessionId);
        try {
            session.connect();
            log.info("[火山引擎 ASR] 会话{}长连接注册成功，空闲超时：{}ms", sessionId, effectiveTimeout);
        } catch (Exception e) {
            log.error("[火山引擎 ASR] 会话{}连接建立失败", sessionId, e);
            if (oldSession != null && oldSession.closed.get()) {
                closeSessionQuietly(oldSession, sessionId);
            }
            throw new RuntimeException("火山引擎ASR连接启动失败", e);
        }

        // 步骤3：原子注册
        sessions.put(sessionId, session);

        // 步骤4：在锁外清理被替换的旧会话（close涉及IO操作）
        if (oldSession != null && oldSession.closed.get()) {
            closeSessionQuietly(oldSession, sessionId);
        }

        return sessionId;
    }

    @Override
    public void sendAudio(String sessionId, byte[] data) {
        sendAudio(sessionId, data, data.length);
    }

    @Override
    public void sendAudio(String sessionId, byte[] data, int length) {
        WsSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.warn("[火山引擎 ASR] 会话{}不存在或已关闭，忽略音频帧（长度：{}）", sessionId, length);
            return;
        }

        if (length == 0) {
            return;
        }

        session.touch();

        try {
            byte[] audioData;
            if (length == data.length) {
                audioData = data;
            } else {
                audioData = new byte[length];
                System.arraycopy(data, 0, audioData, 0, length);
            }

            int seq = session.sequenceCounter.getAndIncrement();
            byte[] frame = ProtocolCodec.buildFrame(
                    MessageType.CLIENT_AUDIO_ONLY_REQUEST,
                    MessageFlag.POS_SEQUENCE,
                    seq,
                    audioData
            );

            if (!session.safeSend(frame)) {
                log.warn("[火山引擎 ASR] 会话{} WebSocket已断开，无法发送音频帧", sessionId);
            }
        } catch (Exception e) {
            log.error("[火山引擎 ASR] 会话{}发送音频帧失败", sessionId, e);
        }
    }

    @Override
    public void sendEndOfStream(String sessionId) {
        // 火山引擎中是没有客户端手动标识一句话结束的功能的
//        WsSession session = sessions.get(sessionId);
//        if (session == null || session.closed.get()) {
//            log.warn("[火山引擎 ASR] 会话{}不存在或已关闭，忽略结束信号", sessionId);
//            return;
//        }
//
//        int seq = session.sequenceCounter.getAndIncrement();
//        log.info("[火山引擎 ASR] 会话{}发送音频流结束信号，seq=-{}", sessionId, seq);
//
//        try {
//            byte[] frame = ProtocolCodec.buildFrame(
//                    MessageType.CLIENT_AUDIO_ONLY_REQUEST,
//                    MessageFlag.NEG_WITH_SEQUENCE,
//                    -seq,
//                    new byte[0]
//            );
//            session.safeSend(frame);
//        } catch (Exception e) {
//            log.error("[火山引擎 ASR] 会话{}发送结束帧失败", sessionId, e);
//        }
    }

    @Override
    public void recognizeShortAudio(byte[] data, AsrResultCallback callback) {
        String sessionId = UUID.randomUUID().toString();

        if (sessions.size() >= MAX_SESSIONS) {
            log.error("[火山引擎 ASR-短连接] 并发会话数已达上限{}", MAX_SESSIONS);
            throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
        }

        WsSession session = new WsSession(callback, SHORT_SESSION_TIMEOUT_MS, sessionId, true);
        try {
            session.connect();
            log.info("[火山引擎 ASR-短连接] 会话{}连接已建立", sessionId);
        } catch (Exception e) {
            log.error("[火山引擎 ASR-短连接] 会话{}连接建立失败", sessionId, e);
            throw new RuntimeException("火山引擎ASR短连接启动失败", e);
        }

        sessions.put(sessionId, session);

        scheduler.schedule(() -> {
            if (!session.closed.get()) {
                log.warn("[火山引擎 ASR-短连接] 会话{}超时（{}ms），强制关闭",
                        sessionId, SHORT_SESSION_TIMEOUT_MS);
                closeSession(session, sessionId);
            }
        }, SHORT_SESSION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        callbackExecutor.execute(() -> {
            try {
                Thread.sleep(SHORT_AUDIO_SEND_DELAY_MS);
                if (session.closed.get()) {
                    return;
                }
                for (int offset = 0; offset < data.length; offset += SHORT_AUDIO_CHUNK_SIZE) {
                    if (session.closed.get()) {
                        return;
                    }
                    int len = Math.min(SHORT_AUDIO_CHUNK_SIZE, data.length - offset);
                    sendAudioFrame(session, data, offset, len);
                }
                if (!session.closed.get()) {
                    sendEndOfStreamForSession(session, sessionId);
                }
            } catch (Exception e) {
                log.error("[火山引擎 ASR-短连接] 会话{}音频发送失败", sessionId, e);
                closeSession(session, sessionId);
            }
        });
    }

    @Override
    public void cancel(String sessionId) {
        WsSession session = sessions.get(sessionId);
        if (session == null || session.closed.get()) {
            log.warn("[火山引擎 ASR] 会话{}不存在或已关闭，忽略取消请求", sessionId);
            return;
        }
        log.info("[火山引擎 ASR] 会话{}取消，发送最后一帧并关闭连接", sessionId);
        sendLastFrameAndClose(session, sessionId);
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        WsSession session = sessions.get(sessionId);
        return session != null && !session.closed.get();
    }

    @Override
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(s -> !s.closed.get()).count();
    }

    @Override
    public AsrProviderType getProviderType() {
        return AsrProviderType.VOLCENGINE;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 直接向指定会话发送音频帧（跳过sessionId查找，供短连接内部使用）
     *
     * @param session 目标会话
     * @param data    音频数据
     * @param offset  数据起始偏移
     * @param length  实际发送长度
     */
    private void sendAudioFrame(WsSession session, byte[] data, int offset, int length) {
        if (length == 0) {
            return;
        }
        session.touch();

        try {
            byte[] audioData;
            if (offset == 0 && length == data.length) {
                audioData = data;
            } else {
                audioData = new byte[length];
                System.arraycopy(data, offset, audioData, 0, length);
            }

            int seq = session.sequenceCounter.getAndIncrement();
            byte[] frame = ProtocolCodec.buildFrame(
                    MessageType.CLIENT_AUDIO_ONLY_REQUEST,
                    MessageFlag.POS_SEQUENCE,
                    seq,
                    audioData
            );

            if (!session.safeSend(frame)) {
                log.warn("[火山引擎 ASR] 会话{} WebSocket已断开，无法发送音频帧", session.sessionId);
            }
        } catch (IOException e) {
            log.error("[火山引擎 ASR] 会话{}构建音频帧失败", session.sessionId, e);
        }
    }

    /**
     * 直接向指定会话发送音频流结束信号（跳过sessionId查找，供短连接内部使用）
     */
    private void sendEndOfStreamForSession(WsSession session, String sessionId) {
        int seq = session.sequenceCounter.getAndIncrement();
        log.info("[火山引擎 ASR] 会话{}发送音频流结束信号，seq=-{}", sessionId, seq);

        try {
            byte[] frame = ProtocolCodec.buildFrame(
                    MessageType.CLIENT_AUDIO_ONLY_REQUEST,
                    MessageFlag.NEG_WITH_SEQUENCE,
                    -seq,
                    new byte[0]
            );
            session.safeSend(frame);
        } catch (Exception e) {
            log.error("[火山引擎 ASR] 会话{}发送结束帧失败", sessionId, e);
        }
    }

    /**
     * 构建鉴权请求头
     */
    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            headers.put("X-Api-Key", config.getApiKey());
        }
        if (config.getResourceId() != null && !config.getResourceId().isEmpty()) {
            headers.put("X-Api-Resource-Id", config.getResourceId());
        }
        if (config.getAppKey() != null && !config.getAppKey().isEmpty()) {
            headers.put("X-Api-App-Key", config.getAppKey());
        }
        if (config.getAccessKey() != null && !config.getAccessKey().isEmpty()) {
            headers.put("X-Api-Access-Key", config.getAccessKey());
        }
        return headers;
    }

    /**
     * 构建FullClientRequest的JSON载荷，委托给Config和RequestProperties各自的实例方法序列化
     */
    private String buildFullClientRequestPayload() throws JsonProcessingException {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.set("audio", OBJECT_MAPPER.readTree(config.toAudioJson()));
        root.set("request", OBJECT_MAPPER.readTree(requestProperties.toRequestJson()));
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    /**
     * 发送最后一帧并关闭WebSocket连接
     */
    private void sendLastFrameAndClose(WsSession session, String sessionId) {
        session.closed.set(true);
        cancelKeepAlive(sessionId);

        try {
            // 发送最后一个音频分片（负序列号表示结束）
            int seq = session.sequenceCounter.getAndIncrement();
            byte[] frame = ProtocolCodec.buildFrame(
                    MessageType.CLIENT_AUDIO_ONLY_REQUEST,
                    MessageFlag.NEG_WITH_SEQUENCE,
                    -seq,
                    new byte[0]
            );
            session.safeSend(frame);
        } catch (Exception e) {
            log.error("[火山引擎 ASR] 会话{}发送最后一帧失败", sessionId, e);
        }

        // 延迟关闭，确保最后一帧被发送
        scheduler.schedule(() -> {
            try {
                session.wsClient.close();
            } catch (Exception e) {
                log.error("[火山引擎 ASR] 会话{}关闭WebSocket失败", sessionId, e);
            }
            sessions.remove(sessionId);
        }, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭会话内部资源
     */
    private void closeSession(WsSession session, String sessionId) {
        session.closed.set(true);
        cancelKeepAlive(sessionId);
        try {
            session.wsClient.close();
        } catch (Exception e) {
            log.error("[火山引擎 ASR] 关闭旧会话{}失败", sessionId, e);
        }
        sessions.remove(sessionId);
    }

    /**
     * 关闭会话内部资源（静默版本，用于异步/锁外清理）
     */
    private void closeSessionQuietly(WsSession session, String sessionId) {
        try {
            session.closed.set(true);
            cancelKeepAlive(sessionId);
            if (session.wsClient != null) {
                session.wsClient.close();
            }
            sessions.remove(sessionId);
        } catch (Exception e) {
            log.error("[火山引擎 ASR] 会话{}静默清理失败", sessionId, e);
        }
    }

    /**
     * 取消保活任务
     */
    private void cancelKeepAlive(String sessionId) {
        ScheduledFuture<?> task = keepAliveTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * 定时检查并淘汰空闲会话
     */
    private void cleanIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.forEach((sessionId, session) -> {
            if (session.shortConnection) {
                return;
            }
            if (!session.closed.get() && (now - session.lastActiveTime.get()) > session.idleTimeoutMs) {
                log.info("[火山引擎 ASR] 会话{}空闲超时（{}ms），自动关闭",
                        sessionId, now - session.lastActiveTime.get());
                sendLastFrameAndClose(session, sessionId);
            }
        });
    }

    /**
     * 定时检查并发送保活PCM静音数据
     */
    private void checkAndKeepAlive() {
        long now = System.currentTimeMillis();
        sessions.forEach((sessionId, session) -> {
            if (session.closed.get() || session.shortConnection) {
                return;
            }
            long elapsedSinceLastActivity = now - session.lastActiveTime.get();
            if (elapsedSinceLastActivity > PING_THRESHOLD_MS) {
                ScheduledFuture<?> existing = keepAliveTasks.get(sessionId);
                if (existing == null || existing.isDone()) {
                    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
                        try {
                            if (session.closed.get()) {
                                cancelKeepAlive(sessionId);
                                return;
                            }
                            // 保活帧：携带序列号，参与计数以保持与服务端同步
                            int seq = session.sequenceCounter.getAndIncrement();
                            byte[] frame = ProtocolCodec.buildFrame(
                                    MessageType.CLIENT_AUDIO_ONLY_REQUEST,
                                    MessageFlag.POS_SEQUENCE,
                                    seq,
                                    SILENT_PCM_CHUNK
                            );
                            if (!session.safeSend(frame)) {
                                session.sequenceCounter.decrementAndGet();
                                cancelKeepAlive(sessionId);
                            }
                        } catch (Exception e) {
                            log.error("[火山引擎 ASR] 会话{}保活发送失败", sessionId, e);
                            cancelKeepAlive(sessionId);
                        }
                    }, 0, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    keepAliveTasks.put(sessionId, future);
                }
            } else {
                cancelKeepAlive(sessionId);
            }
        });
    }

    /**
     * 将识别结果分发给回调接口
     */
    private void dispatchResult(WsSession session, AsrResult result, String sessionId) {
        if (session.closed.get()) {
            return;
        }

        AsrResultCallback callback = session.callback;
        try {
            if (result.getResultCode() == null) {
                return;
            }

            switch (result.getResultCode()) {
                case 0: // INTERMEDIATE
                    callback.onIntermediateResult(result);
                    break;
                case 1: // SENTENCE_END
                    callback.onSentenceEnd(result);
                    session.sentenceStarted.set(false);
                    break;
                case -1: // ERROR
                    callback.onError(sessionId, result.getText());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("[火山引擎 ASR] 会话{}回调分发异常", sessionId, e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断火山引擎原始响应中是否包含非定型utterance（用于首次出现时触发onSentenceBegin）
     */
    private static boolean hasNonDefiniteUtterance(VolcengineAsrResult vendorResult) {
        VolcengineAsrResult.Result result = vendorResult.getResult();
        if (result == null || result.getUtterances() == null) {
            return false;
        }
        return result.getUtterances().stream().anyMatch(u -> !u.isDefinite());
    }

    // ==================== 内部类：WebSocket会话 ====================

    /**
     * 火山引擎ASR WebSocket会话
     * <p>每个会话对应一个独立的WebSocket长连接。</p>
     */
    private class WsSession {

        /** 会话回调 */
        final AsrResultCallback callback;
        /** 空闲超时时间（毫秒） */
        final long idleTimeoutMs;
        /** 是否为短连接会话（短连接不参与保活，不参与空闲淘汰） */
        final boolean shortConnection;
        /** 会话是否已关闭 */
        final AtomicBoolean closed;
        /** 当前句子是否已触发onSentenceBegin（收到SENTENCE_END后重置） */
        final AtomicBoolean sentenceStarted;
        /** 音频帧序号计数器 */
        final AtomicInteger sequenceCounter;
        /** 最后一次活动时间（毫秒时间戳），用于空闲超时判断 */
        final AtomicLong lastActiveTime;
        /** WebSocket客户端 */
        volatile WebSocketClient wsClient;
        /** 会话ID */
        final String sessionId;
        /** 发送锁：保证对wsClient.send()的调用串行化，因为java-websocket的WebSocketClient非线程安全 */
        private final ReentrantLock sendLock = new ReentrantLock();

        WsSession(AsrResultCallback callback, long idleTimeoutMs, String sessionId) {
            this(callback, idleTimeoutMs, sessionId, false);
        }

        WsSession(AsrResultCallback callback, long idleTimeoutMs, String sessionId, boolean shortConnection) {
            this.callback = callback;
            this.idleTimeoutMs = idleTimeoutMs;
            this.sessionId = sessionId;
            this.shortConnection = shortConnection;
            this.closed = new AtomicBoolean(false);
            this.sentenceStarted = new AtomicBoolean(false);
            this.sequenceCounter = new AtomicInteger(AUDIO_SEQUENCE_START);
            this.lastActiveTime = new AtomicLong(System.currentTimeMillis());
        }

        /**
         * 线程安全地发送二进制帧
         *
         * @param frame 已构建好的二进制帧
         * @return true发送成功，false会话已关闭或连接不可用
         */
        boolean safeSend(byte[] frame) {
            if (closed.get()) {
                return false;
            }
            if (wsClient == null || !wsClient.isOpen()) {
                return false;
            }
            sendLock.lock();
            try {
                if (closed.get() || wsClient == null || !wsClient.isOpen()) {
                    return false;
                }
                wsClient.send(ByteBuffer.wrap(frame));
                return true;
            } finally {
                sendLock.unlock();
            }
        }

        /**
         * 建立WebSocket连接并发送FullClientRequest
         */
        void connect() throws URISyntaxException {
            Map<String, String> headers = buildAuthHeaders();
            URI uri = new URI(config.getEndpoint());

            wsClient = new WebSocketClient(uri, headers) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("[火山引擎 ASR] 会话{} WebSocket连接已建立", sessionId);
                    try {
                        String payload = buildFullClientRequestPayload();
                        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
                        byte[] frame = ProtocolCodec.buildFrame(
                                MessageType.CLIENT_FULL_REQUEST,
                                MessageFlag.POS_SEQUENCE,
                                WsSession.this.sequenceCounter.getAndIncrement(),
                                payloadBytes
                        );
                        WsSession.this.safeSend(frame);
                        touch();
                        log.info("[火山引擎 ASR] 会话{}已发送FullClientRequest", sessionId);
                        callback.onTranscriberStart(sessionId);
                    } catch (Exception e) {
                        log.error("[火山引擎 ASR] 会话{}发送FullClientRequest失败", sessionId, e);
                        closed.set(true);
                    }
                }

                @Override
                public void onMessage(String message) {
                    // ASR仅处理二进制帧，文本消息忽略
                    log.debug("[火山引擎 ASR] 会话{}收到文本消息，忽略：{}", sessionId, message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    if (closed.get()) {
                        return;
                    }
                    touch();

                    // 安全拷贝：WebSocket底层可能复用或清空ByteBuffer，
                    // 且避免Direct Buffer导致array()抛异常
                    final byte[] rawBytes;
                    if (bytes.hasArray()) {
                        rawBytes = new byte[bytes.remaining()];
                        System.arraycopy(bytes.array(), bytes.position(), rawBytes, 0, bytes.remaining());
                    } else {
                        rawBytes = new byte[bytes.remaining()];
                        bytes.get(rawBytes);
                    }

                    // 异步投递到回调线程池，防止阻塞java-websocket的接收线程
                    callbackExecutor.execute(() -> {
                        if (closed.get()) {
                            return;
                        }
                        try {
                            VolcengineAsrResult vendorResult = VolcengineAsrConverter.toVendorResult(rawBytes);
                            log.info("[火山引擎 ASR] 会话{} 收到原始响应：{}", sessionId, vendorResult);
                            if (vendorResult.getCode() != VolcengineAsrResult.SUCCESS_CODE) {
                                log.error("[火山引擎 ASR] 会话{} 识别失败，错误码：{}，响应：{}", sessionId, vendorResult.getCode(), vendorResult);
                                callback.onError(sessionId, "ASR识别失败，错误码：" + vendorResult.getCode() + "，错误信息：" + vendorResult.getResult().getText());
                                return;  // 中断执行，不再继续转换和分发
                            }
                            AsrResult commonResult = VolcengineAsrConverter.toCommonResult(rawBytes, null, AsrProviderType.VOLCENGINE);
                            log.info("[火山引擎 ASR] 会话{} 转换为通用业务封装类结果：{}", sessionId, commonResult);

                            // 首次出现非定型utterance → 句子开始
                            if (!sentenceStarted.get() && hasNonDefiniteUtterance(vendorResult)) {
                                callback.onSentenceBegin(commonResult);
                                sentenceStarted.set(true);
                            }

                            dispatchResult(WsSession.this, commonResult, sessionId);

                            // isLastPackage 是 WebSocket 协议层概念，由 Provider 消化，不透传到 AsrResult.resultCode
                            if (vendorResult.isLastPackage()) {
                                callback.onComplete();
                                sessions.remove(sessionId);
                                cancelKeepAlive(sessionId);
                            }
                        } catch (Exception e) {
                            log.error("[火山引擎 ASR] 会话{}响应解析失败", sessionId, e);
                        }
                    });
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("[火山引擎 ASR] 会话{} WebSocket关闭，code={}，reason={}，remote={}",
                            sessionId, code, reason, remote);
                    if (!closed.getAndSet(true)) {
                        cancelKeepAlive(sessionId);
                        sessions.remove(sessionId);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("[火山引擎 ASR] 会话{} WebSocket异常", sessionId, ex);
                    if (!closed.get()) {
                        callback.onError(sessionId, ex.getMessage() != null ? ex.getMessage() : "WebSocket异常");
                    }
                    if (!closed.getAndSet(true)) {
                        cancelKeepAlive(sessionId);
                        sessions.remove(sessionId);
                    }
                }
            };

            wsClient.setConnectionLostTimeout(config.getConnectTimeoutMs() / 1000);
            wsClient.connect();
        }

        /**
         * 更新最后活动时间，用于空闲超时判断和保活判断
         */
        void touch() {
            lastActiveTime.set(System.currentTimeMillis());
        }
    }
}