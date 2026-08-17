package org.lixiyun.server.infrastructure.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话流式订阅持有器
 * <p>
 * 管理每个会话（conversationId）对应的 LLM 流式订阅 {@link Disposable}，
 * 支持在外部中断时通过 {@link #cancelStream} 直接 dispose 订阅，
 * 从而真正终止模型输出、关闭上游 HTTP 连接。
 * </p>
 *
 * <h3>生命周期：</h3>
 * <ol>
 *     <li>{@link #addStream} — 流式订阅创建后注册</li>
 *     <li>{@link #cancelStream} — 用户中断时调用，dispose 并移除</li>
 *     <li>{@link #removeStream} — 流正常结束或异常时清理</li>
 * </ol>
 *
 * @author lixiyun
 * @since 2026-08-17 11:18
 */
@Slf4j
@Component
public class ConversationStreamHolder {

    /** 活跃的会话流式订阅记录 key=会话ID, value=流式订阅的 Disposable 引用 */
    private final ConcurrentHashMap<Long, Disposable> activeStreams = new ConcurrentHashMap<>();

    /**
     * 注册会话的流式订阅
     *
     * @param conversationId 会话ID
     * @param disposable     流式订阅的 Disposable 引用
     */
    public void addStream(Long conversationId, Disposable disposable) {
        Disposable existing = activeStreams.put(conversationId, disposable);
        if (existing != null && !existing.isDisposed()) {
            existing.dispose();
            log.warn("会话流持有器-覆盖未完成的订阅，会话ID：{}，旧订阅已dispose", conversationId);
        }
        log.debug("会话流持有器-注册流式订阅，会话ID：{}", conversationId);
    }

    /**
     * 取消会话的流式订阅（用于用户中断场景）
     * <p>
     * 调用 {@link Disposable#dispose()} 后，Reactor 会沿上游传播取消信号，
     * 最终关闭与 LLM API 的 HTTP 连接，真正停止模型生成。
     * </p>
     *
     * @param conversationId 会话ID
     * @return 是否成功取消（true=存在订阅且已dispose，false=无活跃订阅）
     */
    public boolean cancelStream(Long conversationId) {
        Disposable disposable = activeStreams.remove(conversationId);
        if (disposable == null) {
            log.debug("会话流持有器-无活跃订阅可取消，会话ID：{}", conversationId);
            return false;
        }
        if (!disposable.isDisposed()) {
            disposable.dispose();
            log.info("会话流持有器-已取消LLM流式订阅，会话ID：{}", conversationId);
            return true;
        }
        log.debug("会话流持有器-订阅已处于disposed状态，会话ID：{}", conversationId);
        return false;
    }

    /**
     * 移除会话的流式订阅记录（用于流正常结束或异常时的清理）
     *
     * @param conversationId 会话ID
     */
    public void removeStream(Long conversationId) {
        activeStreams.remove(conversationId);
        log.debug("会话流持有器-移除订阅记录，会话ID：{}", conversationId);
    }

    /**
     * 判断会话是否存在活跃的流式订阅
     *
     * @param conversationId 会话ID
     * @return 是否存在且未 disposed
     */
    public boolean hasActiveStream(Long conversationId) {
        Disposable disposable = activeStreams.get(conversationId);
        return disposable != null && !disposable.isDisposed();
    }
}