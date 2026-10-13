package org.lixiyun.server.infrastructure.audio;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.asr.api.AsrResultCallback;
import org.lixiyun.common.agent.asr.api.IAsrProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ASR连接管理器（防腐层 + 用户会话映射层）
 * <p>对内提供以userId为粒度的稳定ASR服务API，对外委托给{@link IAsrProvider}策略实现。
 * 通过{@code asr.provider}配置项在阿里云NLS和火山引擎之间灵活切换，
 * 无需修改本层代码。</p>
 *
 * <h3>职责划分</h3>
 * <ul>
 *     <li><b>用户映射</b>：维护 userId → 抽象sessionId 的映射关系</li>
 *     <li><b>业务规则</b>：一用户一会话（注册新会话自动关闭旧会话）</li>
 *     <li><b>委托转发</b>：所有ASR操作委托给底层Provider</li>
 * </ul>
 *
 * <h3>目前支持的Provider</h3>
 * <ul>
 *     <li><b>alibaba_nls</b> — {@code AlibabaNlsAsrProvider}（默认）</li>
 *     <li><b>volcengine</b> — 待实现</li>
 * </ul>
 *
 * <h3>切换方式</h3>
 * <pre>{@code
 * # application.yml
 * asr:
 *   provider: volcengine     # 切换到火山引擎（待实现）
 *   alibaba:
 *     app-key: xxx
 *     access-key-id: xxx
 *     access-key-secret: xxx
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-08-16
 */
@Slf4j
@Component
public class AsrConnectionManager {

    /** 短连接模式标识 */
    private static final String SHORT_MODE = "short";

    /** ASR Provider策略接口，由Spring根据{@code asr.provider}配置自动注入实现 */
    private final IAsrProvider asrProvider;

    /** 连接模式：{@code "long"}（默认）或 {@code "short"} */
    @Value("${asr.connection-mode:long}")
    private String connectionMode;

    /** 用户ID → 抽象会话（会话ID + 回调），回调用于会话失效时自愈重建（长连接模式） */
    private final ConcurrentHashMap<Long, UserAsrSession> userSessions = new ConcurrentHashMap<>();

    /** 长连接模式下用户与其 ASR 会话的绑定（保留回调以便自动重建） */
    private record UserAsrSession(String sessionId, AsrResultCallback callback) {
    }

    /** 短连接模式：用户ID → 回调 映射表（仅存储，不建长连接） */
    private final ConcurrentHashMap<Long, AsrResultCallback> shortCallbacks = new ConcurrentHashMap<>();

    public AsrConnectionManager(IAsrProvider asrProvider) {
        this.asrProvider = asrProvider;
    }

    /**
     * 为用户建立ASR WebSocket长连接，注册识别结果回调
     * <p>一用户一会话规则：若用户已有活跃会话，先关闭旧会话再创建新会话。</p>
     *
     * @param userId   用户ID
     * @param callback 识别结果回调
     * @throws org.lixiyun.common.core.error.exception.BusinessException 当并发连接数达到上限时
     * @throws RuntimeException                                           当ASR连接启动失败时
     */
    public void register(Long userId, AsrResultCallback callback) {
        if (SHORT_MODE.equals(connectionMode)) {
            shortCallbacks.put(userId, callback);
            log.info("[ASR防腐层-短连接] 用户{}已注册回调", userId);
            return;
        }
        userSessions.compute(userId, (k, old) -> {
            if (old != null) {
                log.info("[ASR防腐层] 用户{}已有会话{}，先关闭旧会话", userId, old.sessionId());
                asrProvider.cancel(old.sessionId());
            }
            String newSessionId = asrProvider.createSession(callback);
            log.info("[ASR防腐层] 用户{}映射到会话{}", userId, newSessionId);
            return new UserAsrSession(newSessionId, callback);
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
        if (SHORT_MODE.equals(connectionMode)) {
            AsrResultCallback callback = shortCallbacks.get(userId);
            if (callback == null) {
                log.warn("[ASR防腐层-短连接] 用户{}未注册回调", userId);
                return;
            }
            log.info("[ASR防腐层-短连接] 用户{}发起一次性识别，数据长度{}", userId, length);
            asrProvider.recognizeShortAudio(data, callback);
            return;
        }
        UserAsrSession session = userSessions.get(userId);
        if (session == null) {
            log.warn("[ASR防腐层] 用户{}没有活跃的ASR会话", userId);
            return;
        }

        // 自愈：底层会话可能因网络/协议异常被服务端关闭（Provider 侧已从注册表移除），
        // 此时用保存的回调重建会话后再发送，避免后续音频帧被永久静默丢弃
        if (!asrProvider.isSessionActive(session.sessionId())) {
            UserAsrSession rebuilt = rebuildSession(userId, session);
            if (rebuilt == null) {
                return;
            }
            session = rebuilt;
        }

        asrProvider.sendAudio(session.sessionId(), data, length);
    }

    /**
     * 重建用户的 ASR 长连接会话（尽力而为）。
     * <p>不使用 {@code compute}：createSession 会阻塞至多一个连接超时，
     * 持有 CHM 桶锁会卡住同桶的音频线程；极端并发下可能短暂产生重复会话，由 Provider 空闲清理回收。</p>
     *
     * @param userId  用户ID
     * @param stale   已失效的会话绑定
     * @return 重建后的绑定；重建失败返回 null
     */
    private UserAsrSession rebuildSession(Long userId, UserAsrSession stale) {
        try {
            log.warn("[ASR防腐层] 用户{}会话{}已失效，自动重建", userId, stale.sessionId());
            asrProvider.cancel(stale.sessionId());
            String newSessionId = asrProvider.createSession(stale.callback());
            UserAsrSession rebuilt = new UserAsrSession(newSessionId, stale.callback());
            userSessions.put(userId, rebuilt);
            log.info("[ASR防腐层] 用户{}ASR会话重建成功，新会话{}", userId, newSessionId);
            return rebuilt;
        } catch (Exception e) {
            log.error("[ASR防腐层] 用户{}ASR会话重建失败，本帧丢弃", userId, e);
            return null;
        }
    }

    /**
     * 关闭连接并清理所有资源，幂等操作
     *
     * @param userId 用户ID
     */
    public void cancel(Long userId) {
        if (SHORT_MODE.equals(connectionMode)) {
            shortCallbacks.remove(userId);
            log.info("[ASR防腐层-短连接] 用户{}回调已移除", userId);
            return;
        }
        UserAsrSession session = userSessions.remove(userId);
        if (session != null) {
            log.info("[ASR防腐层] 用户{}会话{}已取消并解除映射", userId, session.sessionId());
            asrProvider.cancel(session.sessionId());
        }
    }

    /**
     * 检查用户ASR会话是否活跃
     *
     * @param userId 用户ID
     * @return {@code true}表示会话活跃
     */
    public boolean isSessionActive(Long userId) {
        if (SHORT_MODE.equals(connectionMode)) {
            return false;
        }
        UserAsrSession session = userSessions.get(userId);
        return session != null && asrProvider.isSessionActive(session.sessionId());
    }

    /**
     * 发送音频流结束信号，通知ASR Provider音频已全部发送
     * <p>会话不存在或已关闭时静默忽略。</p>
     *
     * @param userId 用户ID
     */
    public void sendEndOfStream(Long userId) {
        if (SHORT_MODE.equals(connectionMode)) {
            log.debug("[ASR防腐层-短连接] endOfStream已在sendAudio中自动发送，跳过");
            return;
        }
        UserAsrSession session = userSessions.get(userId);
        if (session == null) {
            log.warn("[ASR防腐层] 用户{}没有活跃的ASR会话，忽略结束信号", userId);
            return;
        }
        log.info("[ASR防腐层] 用户{}会话{}发送音频流结束信号", userId, session.sessionId());
        asrProvider.sendEndOfStream(session.sessionId());
    }

    /**
     * 获取当前活跃的ASR会话总数
     *
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        return asrProvider.getActiveSessionCount();
    }
}