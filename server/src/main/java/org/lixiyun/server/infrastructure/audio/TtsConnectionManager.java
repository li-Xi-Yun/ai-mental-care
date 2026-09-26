package org.lixiyun.server.infrastructure.audio;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tts.api.ITtsProvider;
import org.lixiyun.common.agent.tts.api.TtsResultCallback;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * TTS连接管理器（防腐层 + 用户会话映射层）
 * <p>对内提供以userId为粒度的稳定TTS服务API，对外委托给{@link ITtsProvider}策略实现。
 * 通过{@code tts.provider}配置项在阿里云NLS和火山引擎之间灵活切换，
 * 无需修改本层代码。</p>
 *
 * <h3>职责划分</h3>
 * <ul>
 *     <li><b>用户映射</b>：维护 userId → 抽象sessionId 的映射关系</li>
 *     <li><b>业务规则</b>：一用户一会话（注册新会话自动关闭旧会话）</li>
 *     <li><b>委托转发</b>：所有TTS操作委托给底层Provider</li>
 * </ul>
 *
 * <h3>目前支持的Provider</h3>
 * <ul>
 *     <li><b>alibaba_nls</b> — {@code AlibabaNlsTtsProvider}（默认）</li>
 *     <li><b>volcengine</b> — {@code VolcengineTtsProvider}（按需激活）</li>
 * </ul>
 *
 * <h3>切换方式</h3>
 * <pre>{@code
 * # application.yml
 * tts:
 *   provider: volcengine     # 切换到火山引擎
 *   volcengine:
 *     api-key: xxx
 *     resource-id: seed-tts-2.0
 *     speaker: BV001_streaming
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-08-16
 */
@Slf4j
@Component
public class TtsConnectionManager {

    /** TTS Provider策略接口，由Spring根据{@code tts.provider}配置自动注入实现 */
    private final ITtsProvider ttsProvider;

    /** 用户ID → 抽象会话ID 映射表 */
    private final ConcurrentHashMap<Long, String> userSessions = new ConcurrentHashMap<>();

    public TtsConnectionManager(ITtsProvider ttsProvider) {
        this.ttsProvider = ttsProvider;
    }

    /**
     * 为用户建立TTS会话上下文，绑定合成结果回调
     * <p>一用户一会话规则：若用户已有活跃会话，先关闭旧会话再创建新会话。</p>
     *
     * @param userId   用户ID
     * @param callback 合成结果回调
     */
    public void register(Long userId, TtsResultCallback callback) {
        userSessions.compute(userId, (k, oldSessionId) -> {
            if (oldSessionId != null) {
                log.info("[TTS防腐层] 用户{}已有会话{}，先关闭旧会话", userId, oldSessionId);
                ttsProvider.cancel(oldSessionId);
            }
            String newSessionId = ttsProvider.createSession(callback);
            log.info("[TTS防腐层] 用户{}映射到会话{}", userId, newSessionId);
            return newSessionId;
        });
    }

    /**
     * 发送文本分片，首次调用时自动建立合成器并建立WebSocket连接
     *
     * @param userId  用户ID
     * @param segment 文本分片
     */
    public void sendTextSegment(Long userId, String segment) {
        String sessionId = userSessions.get(userId);
        if (sessionId == null) {
            log.warn("[TTS防腐层] 用户{}没有活跃的TTS会话", userId);
            return;
        }
        ttsProvider.sendTextSegment(sessionId, segment);
    }

    /**
     * 完成合成：通知TTS服务文本已全部发送，等待收尾音频
     *
     * @param userId 用户ID
     */
    public void finishSynthesis(Long userId) {
        String sessionId = userSessions.get(userId);
        if (sessionId == null) {
            log.warn("[TTS防腐层] 用户{}没有活跃的TTS会话", userId);
            return;
        }
        ttsProvider.finishSynthesis(sessionId);
    }

    /**
     * 中断当前TTS合成，保留会话上下文，允许再次合成
     *
     * @param userId 用户ID
     */
    public void interrupt(Long userId) {
        String sessionId = userSessions.get(userId);
        if (sessionId == null) {
            log.warn("[TTS防腐层] 用户{}没有活跃的TTS会话", userId);
            return;
        }
        ttsProvider.interrupt(sessionId);
    }

    /**
     * 取消TTS会话：关闭会话并清理所有资源，解除userId映射
     *
     * @param userId 用户ID
     */
    public void cancel(Long userId) {
        String sessionId = userSessions.remove(userId);
        if (sessionId != null) {
            log.info("[TTS防腐层] 用户{}会话{}已取消并解除映射", userId, sessionId);
            ttsProvider.cancel(sessionId);
        }
    }

    /**
     * 检查用户TTS会话是否活跃
     *
     * @param userId 用户ID
     * @return {@code true}表示会话活跃
     */
    public boolean isSessionActive(Long userId) {
        String sessionId = userSessions.get(userId);
        return sessionId != null && ttsProvider.isSessionActive(sessionId);
    }

    /**
     * 获取当前活跃的TTS会话总数
     *
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        return ttsProvider.getActiveSessionCount();
    }
}