package org.lixiyun.server.ai.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.message.ThinkMessage;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话历史消息存储服务
 * <p>
 * 负责将AI对话过程中的消息持久化到数据库，支持多种消息类型的转换和存储。
 * 主要用于保存用户与AI助手之间的完整对话历史记录。
 * </p>
 *
 * <h3>支持的消息类型：</h3>
 * <ul>
 *     <li>{@link MessageType#USER} - 用户消息</li>
 *     <li>{@link MessageType#ASSISTANT} - AI助手回复</li>
 *     <li>{@link MessageType#SYSTEM} - 系统提示消息</li>
 *     <li>{@link MessageType#TOOL} - 工具调用结果</li>
 *     <li>{@link MessageType#ASSISTANT_TOOL} - AI助手的工具调用请求</li>
 *     <li>{@link MessageType#THINKING} - AI思考过程</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *     <li>文本对话场景的消息存储</li>
 *     <li>语音对话场景的消息存储</li>
 *     <li>多轮对话的上下文管理</li>
 *     <li>对话历史的查询和回溯</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-03-15 15:53
 * @see ConversationMemory
 * @see MessageType
 * @see ConversationMemoryMapper
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ConversationHistoryMessagesStorage {

    private final ConversationMemoryMapper conversationMemoryMapper;

    /**
     * 批量保存会话消息列表到数据库
     * <p>
     * 将Spring AI框架的Message对象列表转换为 {@link ConversationMemory} 实体对象，
     * 并批量插入数据库。该方法支持空值安全处理，当传入的消息列表为空或null时直接返回。
     * </p>
     *
     * <h3>处理流程：</h3>
     * <ol>
     *     <li>参数校验：检查消息列表是否为空或null</li>
     *     <li>类型转换：将每个Message对象转换为ConversationMemory实体</li>
     *     <li>批量插入：使用MyBatis-Plus的批量插入功能保存到数据库</li>
     * </ol>
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * List<Message> messages = Arrays.asList(
     *     new UserMessage("你好"),
     *     new AssistantMessage("你好！有什么可以帮助你的？")
     * );
     * storage.save(userId, conversationId, currentRound, messages);
     * }</pre>
     *
     * @param userId         用户ID，关联用户表的主键
     * @param conversationId 会话ID，关联会话表的主键
     * @param roundCount     当前对话轮次，用于标识消息在对话中的顺序位置
     * @param messageList    Spring AI框架的消息列表，包含UserMessage、AssistantMessage等多种类型
     *                       支持的类型包括：UserMessage、AssistantMessage、SystemMessage、ToolResponseMessage、ThinkMessage
     * @see #getConversationMemory(Long, Long, int, Message)
     */
    public void save(Long userId, Long conversationId, int roundCount, List<Message> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            log.debug("消息列表为空，跳过保存操作，会话ID：{}，轮次：{}", conversationId, roundCount);
            return;
        }

        log.info("开始批量保存会话消息，用户ID：{}，会话ID：{}，轮次：{}，消息数量：{}",
                userId, conversationId, roundCount, messageList.size());

        List<ConversationMemory> result = new ArrayList<>(messageList.size());
        for (var message : messageList) {
            ConversationMemory conversationMemory = getConversationMemory(userId, conversationId, roundCount, message);
            result.add(conversationMemory);
        }

        conversationMemoryMapper.insert(result);
        log.info("会话消息批量保存成功，用户ID：{}，会话ID：{}，保存数量：{}",
                userId, conversationId, result.size());
    }

    /**
     * 将Spring AI的Message对象转换为ConversationMemory实体（基于Message对象）
     * <p>
     * 根据消息的具体类型进行智能转换，自动识别消息内容并设置正确的消息类型标识。
     * 该方法是核心转换逻辑，支持Spring AI框架的所有标准消息类型以及自定义的ThinkMessage。
     * </p>
     *
     * <h3>消息类型映射规则：</h3>
     * <table border="1">
     *     <tr><th>输入类型</th><th>输出MessageType</th><th>内容处理规则</th></tr>
     *     <tr><td>UserMessage</td><td>USER</td><td>直接使用getText()内容</td></tr>
     *     <tr><td>AssistantMessage（有文本）</td><td>ASSISTANT</td><td>使用getText()内容</td></tr>
     *     <tr><td>AssistantMessage（无文本有工具调用）</td><td>ASSISTANT_TOOL</td><td>拼接工具名称和参数</td></tr>
     *     <tr><td>SystemMessage</td><td>SYSTEM</td><td>直接使用getText()内容</td></tr>
     *     <tr><td>ToolResponseMessage</td><td>TOOL</td><td>拼接工具名称和返回结果</td></tr>
     *     <tr><td>ThinkMessage</td><td>THINKING</td><td>直接使用getText()内容</td></tr>
     * </table>
     *
     * <h3>特殊处理：</h3>
     * <ul>
     *     <li>AssistantMessage无文本内容时，自动识别为工具调用请求类型（ASSISTANT_TOOL）</li>
     *     <li>多个工具调用时使用分号分隔</li>
     *     <li>null消息输入时返回基础构建器（仅包含userId、conversationId、roundNum）</li>
     * </ul>
     *
     * @param userId         用户ID，消息的归属用户
     * @param conversationId 会话ID，消息所属的会话
     * @param roundCount     消息所在轮次，用于多轮对话排序
     * @param message        Spring AI框架的消息对象，支持以下类型：
     *                       <ul>
     *                           <li>{@link UserMessage} - 用户发送的消息</li>
     *                           <li>{@link AssistantMessage} - AI助手的回复消息</li>
     *                           <li>{@link SystemMessage} - 系统提示消息</li>
     *                           <li>{@link ToolResponseMessage} - 工具执行结果消息</li>
     *                           <li>{@link ThinkMessage} - AI思考过程消息（自定义类型）</li>
     *                       </ul>
     * @return 转换后的ConversationMemory实体对象，包含完整的消息元数据和内容信息
     * @see MessageType
     * @see ConversationMemory
     */
    public ConversationMemory getConversationMemory(Long userId, Long conversationId, int roundCount, Message message) {
        ConversationMemory.ConversationMemoryBuilder builder = ConversationMemory.builder()
                .userId(userId)
                .conversationId(conversationId)
                .roundNum(roundCount);

        if (message != null) {
            if (message instanceof UserMessage) {
                builder.type(MessageType.USER.getName())
                        .content(message.getText());
            } else if (message instanceof AssistantMessage assistantMessage) {

                if (assistantMessage.getText() == null || assistantMessage.getText().isEmpty()) {
                    String toolCallInfo = assistantMessage.getToolCalls().stream()
                            .map(toolCall -> "工具：" + toolCall.name() + ", 参数：" + toolCall.arguments())
                            .collect(Collectors.joining(";"));
                    builder.type(MessageType.ASSISTANT_TOOL.getName())
                            .content("准备调用工具--" + toolCallInfo);
                } else {
                    builder.type(MessageType.ASSISTANT.getName())
                            .content(assistantMessage.getText());
                }
            } else if (message instanceof SystemMessage) {
                builder.type(MessageType.SYSTEM.getName())
                        .content(message.getText());
            } else if (message instanceof ToolResponseMessage toolResponseMessage) {
                String responses = toolResponseMessage.getResponses().stream()
                        .map(response -> "工具名称：" + response.name() + "，返回结果：" + response.responseData())
                        .collect(Collectors.joining(";"));
                builder.type(MessageType.TOOL.getName())
                        .content("工具调用成功--" + responses);
            } else if (message instanceof ThinkMessage) {
                builder.type(MessageType.THINKING.getName())
                        .content(message.getText());
            }
        }
        return builder.build();
    }

}