package org.lixiyun.server.ai.model.factory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-07-14 23:42
 */
@Slf4j
@Getter
@Component
public class ChatModelFactory {
    
    @Autowired
    @Qualifier("deepSeekChatModel")
    private ChatModel deepSeekChatModel;

    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel dashScopeChatModel;

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel ollamaChatModel;

    public ChatModel getChatModel(ChatModelType type) {
        return switch (type) {
            case OLLAMA -> ollamaChatModel;
            case DEEP_SEEK -> deepSeekChatModel;
            case DASH_SCOPE -> dashScopeChatModel;
        };
    }

}