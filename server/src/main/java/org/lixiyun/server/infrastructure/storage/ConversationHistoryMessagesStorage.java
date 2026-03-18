package org.lixiyun.server.infrastructure.storage;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.ConversationMemory;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.enums.MessageType;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
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

    public void save(Long conversationId, int roundCount, List<Message> messageList) {
        if(messageList == null || messageList.isEmpty()){
            return;
        }
        List<ConversationMemory> result = new ArrayList<>(messageList.size());
        for(var message : messageList) {
            ConversationMemory.ConversationMemoryBuilder builder = ConversationMemory.builder()
                    .conversationId(conversationId)
                    .roundNum(roundCount);
            if (message != null) {
                if (message instanceof UserMessage) {
                    builder.type(MessageType.USER.getName())
                            .content(message.getText());
                } else if (message instanceof AssistantMessage assistantMessage) {

                    if(assistantMessage.getText() == null || assistantMessage.getText().isEmpty()){
                        String var = assistantMessage.getToolCalls().stream()
                                .map(item1 -> "工具：" + item1.name() + ", 参数：" + item1.arguments())
                                .collect(Collectors.joining(";"));
                        builder.type(MessageType.ASSISTANT_TOOL.getName())
                                .content("准备调用工具--" + var);
                    }else {
                        builder.type(MessageType.ASSISTANT.getName())
                                .content(assistantMessage.getText());
                    }
                } else if (message instanceof SystemMessage) {
                    builder.type(MessageType.SYSTEM.getName())
                            .content(message.getText());
                } else if (message instanceof ToolResponseMessage toolResponseMessage) {
                    String responses = toolResponseMessage.getResponses().stream()
                            .map(item1 -> "工具名称：" + item1.name() + "，返回结果：" + item1.responseData())
                            .collect(Collectors.joining(";"));
                    builder.type(MessageType.TOOL.getName())
                            .content("工具调用成功--" + responses);
                }
            }
            result.add(builder.build());
        }
        conversationMemoryMapper.insert(result);
    }

    public static void storeDataToConfig(RunnableConfig config, String data, MessageType messageType){
        ArrayList<Message> conversationMessageList = (ArrayList<Message>) config.context().get(GraphConstant.CONVERSATION_MESSAGES);
        switch (messageType) {
            case USER -> {
                conversationMessageList.add(new UserMessage(data));
            }
            case ASSISTANT, THINKING, ASSISTANT_TOOL -> {
                conversationMessageList.add(new AssistantMessage(data));
            }
            case SYSTEM -> {
                conversationMessageList.add(new SystemMessage(data));
            }
//            case TOOL -> {
//                conversationMessageList.add(new ToolResponseMessage(data));
//            }
        }
    }

    public static void storeDataToConfig(RunnableConfig config, Message data){
        ArrayList<Message> conversationMessageList = (ArrayList<Message>) config.context().get(GraphConstant.CONVERSATION_MESSAGES);
        conversationMessageList.add(data);
    }
}

