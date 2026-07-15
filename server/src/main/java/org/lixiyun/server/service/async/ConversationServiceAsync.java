package org.lixiyun.server.service.async;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.pojo.entity.conversation.GraphCheckpoint;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.mapper.EmotionAnalysisMapper;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.lixiyun.server.mapper.GraphCheckpointMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author lixiyun
 * @since 2026-03-20 21:26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceAsync {

    private final ConversationMemoryMapper conversationMemoryMapper;
    private final EmotionAnalysisMapper emotionAnalysisMapper;
    private final EmotionDiagnosisMapper emotionDiagnosisMapper;
    private final GraphCheckpointMapper graphCheckpointMapper;

    @Async
    @Transactional
    public void deleteConversation(Long conversationId) {
        try {
            conversationMemoryMapper.delete(new LambdaUpdateWrapper<ConversationMemory>()
                    .eq(ConversationMemory::getConversationId, conversationId));
            emotionAnalysisMapper.delete(new LambdaUpdateWrapper<EmotionAnalysis>()
                    .eq(EmotionAnalysis::getConversationId, conversationId));
            emotionDiagnosisMapper.delete(new LambdaUpdateWrapper<EmotionDiagnosis>()
                    .eq(EmotionDiagnosis::getConversationId, conversationId));
            graphCheckpointMapper.delete(new LambdaUpdateWrapper<GraphCheckpoint>()
                    .eq(GraphCheckpoint::getConversationId, conversationId));
        } catch (Exception e) {
            log.error("删除会话失败，会话ID：{}", conversationId, e);
        }
    }

}
