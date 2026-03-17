package org.lixiyun.server.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.ConversationMemory;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Repository;

import java.util.stream.Collectors;

/**
 * @author lixiyun
 * @since 2026-03-15 15:53
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ConversationHistoryMessagesStorage {

    private final ConversationMemoryMapper conversationMemoryMapper;

    private static final String USER = "user";
    private static final String ASSISTANT = "assistant";
    private static final String SYSTEM = "system";
    private static final String TOOL = "tool";
    private static final String ASSISTANT_TOOL = "assistant_tool";

    public void save(Long conversationId, int roundCount, Message message) {
        ConversationMemory.ConversationMemoryBuilder builder = ConversationMemory.builder()
                .conversationId(conversationId)
                .roundNum(roundCount);
        if (message instanceof UserMessage) {
            builder.type(USER)
                    .content(message.getText());
        } else if (message instanceof AssistantMessage assistantMessage) {

            if(assistantMessage.getText() == null || assistantMessage.getText().isEmpty()){
                String var = assistantMessage.getToolCalls().stream()
                        .map(item1 -> "工具：" + item1.name() + ", 参数：" + item1.arguments())
                        .collect(Collectors.joining(";"));
                builder.type(ASSISTANT_TOOL)
                        .content("准备调用工具--" + var);
            }else {
                builder.type(ASSISTANT)
                        .content(assistantMessage.getText());
            }
        } else if (message instanceof SystemMessage) {
            builder.type(SYSTEM)
                    .content(message.getText());
        } else if (message instanceof ToolResponseMessage toolResponseMessage) {
            String responses = toolResponseMessage.getResponses().stream()
                    .map(item1 -> "工具名称：" + item1.name() + "，返回结果：" + item1.responseData())
                    .collect(Collectors.joining(";"));
            builder.type(TOOL)
                    .content("工具调用成功--" + responses);
        }
        conversationMemoryMapper.insert(builder.build());
    }

}
