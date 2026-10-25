package org.lixiyun.server.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.utils.DateUtils;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.server.infrastructure.conversation.ConversationRepository;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.infrastructure.interaction.adapter.InputAdapterSessionManager;
import org.lixiyun.server.service.user.ConversationLifecycleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 会话空闲超时兜底回收任务
 *
 * <p>前端语音页内置空闲定时器为主（页面存活时优先由前端发起释放），本任务作为<b>兜底</b>
 * 覆盖「页面挂起 / 前台定时器节流 / 前端崩溃」导致定时器不触发的场景：</p>
 * <ul>
 *   <li>每 {@code conversation.idle-timeout.scan-interval} 扫描一次<b>已注册生命周期</b>的会话
 *       （{@link InputAdapterSessionManager#getActiveConversationIds()}，即持有 ASR 长连接 / 输出管道资源的会话）；</li>
 *   <li>会话 {@code lastActiveTime}（无则回退 {@code createdTime}）距今超过
 *       {@code conversation.idle-timeout.seconds} 即判定空闲：先推送 {@code SESSION_TIMEOUT}
 *       WebSocket 事件（前端收到后主动提示并释放本地资源），再释放后端生命周期资源；</li>
 *   <li>文本会话同样被兜底回收，但前端 {@code sendTextWithLifecycle} 已有 2802 自愈，
 *       下次发送会自动重新 init，无需改动文本页。</li>
 * </ul>
 *
 * <p>时区约定与 {@code ConversationCleanupTask} 一致：一切 {@code LocalDateTime} 均为 UTC，
 * 阈值换算沿用 {@link DateUtils#toUtcZoned}，禁止改用本地时区。</p>
 *
 * @author lixiyun
 * @since 2026-10-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationIdleTimeoutTask {

    private final DateUtils dateUtils;
    private final InputAdapterSessionManager inputAdapterSessionManager;
    private final ConversationRepository conversationRepository;
    private final ConversationLifecycleService conversationLifecycleService;
    private final ConversationWebSocketManager conversationWebSocketManager;

    /** 空闲超时阈值（秒） */
    @Value("${conversation.idle-timeout.seconds:300}")
    private long idleTimeoutSeconds;

    @Scheduled(fixedRateString = "${conversation.idle-timeout.scan-interval:30000}")
    public void scanAndReleaseIdleSessions() {
        try {
            var conversationIds = inputAdapterSessionManager.getActiveConversationIds();
            if (conversationIds.isEmpty()) {
                return;
            }

            long nowUtcMilli = dateUtils.toUtcZoned(LocalDateTime.now()).toInstant().toEpochMilli();
            long idleThresholdMilli = idleTimeoutSeconds * 1000L;

            for (Long conversationId : conversationIds) {
                Conversation conversation = conversationRepository.getConversationById(conversationId);
                if (conversation == null) {
                    // 会话已被删除但适配器残留（异常路径）：直接释放资源，不推送事件
                    conversationLifecycleService.releaseIdleSession(conversationId);
                    continue;
                }

                LocalDateTime lastActive = conversation.getLastActiveTime() != null
                        ? conversation.getLastActiveTime()
                        : conversation.getCreatedTime();
                if (lastActive == null) {
                    continue;
                }

                long lastActiveUtcMilli = dateUtils.toUtcZoned(lastActive).toInstant().toEpochMilli();
                if (nowUtcMilli - lastActiveUtcMilli >= idleThresholdMilli) {
                    log.warn("[会话空闲回收] 会话{}空闲超时（lastActiveTime={}，阈值={}秒），通知前端并回收资源",
                            conversationId, lastActive, idleTimeoutSeconds);
                    // 先推送事件让前端主动降级/跳转，再释放资源
                    conversationWebSocketManager.sendSessionTimeout(conversation.getUserId(), conversationId);
                    conversationLifecycleService.releaseIdleSession(conversationId);
                }
            }
        } catch (Exception e) {
            log.error("[会话空闲回收] 扫描任务执行异常", e);
        }
    }
}
