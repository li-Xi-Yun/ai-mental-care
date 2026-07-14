package org.lixiyun.pojo.bo.conversation;

import lombok.Builder;
import lombok.Data;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;

import java.util.List;

/**
 * 会话消息处理上下文业务对象（BO）
 * <p>用于封装会话消息处理所需的完整上下文数据</p>
 *
 * @author lixiyun
 * @since 2026-07-14 18:02
 */
@Data
@Builder
public class ConversationProcessContextBO {

    /**
     * 临时消息数据（待处理的用户消息列表）
     */
    private List<ConversationMemory> temporaryMessages;

    /**
     * 会话基本信息
     */
    private Conversation conversation;

    /**
     * 会话历史上下文信息
     */
    private List<ConversationMemory> conversationHistory;

    /**
     * 历史情绪分析结果列表
     */
    private List<EmotionAnalysis> emotionAnalyses;

    /**
     * 历史心理诊断结果
     */
    private EmotionDiagnosis emotionDiagnosis;
}