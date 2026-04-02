package org.lixiyun.server.socket.handle;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.socket.WebSocketMessageHandle;
import org.lixiyun.pojo.dto.conversation.AudioModelDTO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-03-23 20:38
 */
@Slf4j
@Component("audioModel")
public class AudioModelHandle implements WebSocketMessageHandle {

    @Autowired
    @Qualifier("deepSeekChatModel")
    private ChatModel deepSeekChatModel;

    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel dashScopeChatModel;

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel ollamaChatModel;

    @Override
    public void handle(Long userId, Object data) {
        log.info("音频模型调用，userId：{}", userId);
//        byte[] audioData = AudioCache.getAndClearAudioData(userId);
        byte[] audioData = null;
        if(audioData.length == 0){
            log.warn("用户{}音频数据为空", userId);
            return;
        }

        if(!(data instanceof AudioModelDTO audioModelDTO)){
            log.warn("用户{}音频模型数据格式错误", userId);
            return;
        }

        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        Integer currentRound = audioModelDTO.getCurrentRound();
        String threadId = audioModelDTO.getConversationId() != null ? String.valueOf(audioModelDTO.getConversationId()) : BaseCheckpointSaver.THREAD_ID_DEFAULT;

    }

}
