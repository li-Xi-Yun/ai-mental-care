package org.lixiyun.server.infrastructure.conversation.processor;

import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;

/**
 * 消息处理器接口
 * <p>定义会话消息处理的核心处理逻辑</p>
 *
 * @author lixiyun
 * @since 2026-07-14 18:02
 */
public interface MessageProcessor {

    /**
     * 处理会话消息
     * <p>根据传入的处理上下文，执行完整的消息处理流程</p>
     *
     * @param context 会话消息处理上下文业务对象 {@link ConversationProcessContextBO}
     */
    void processMessage(ConversationProcessContextBO context);


}