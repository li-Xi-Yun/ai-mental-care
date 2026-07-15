package org.lixiyun.pojo.bo.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;

import java.io.Serializable;
import java.util.List;

/**
 * @author lixiyun
 * @since 2026-07-15 15:41
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryCompressionBO implements Serializable {

    private static final long serialVersionUID = 420266596019691443L;

    public static final String NAME = "historyCompressionBO";

    /** 会话元数据信息，未压缩轮次之后的数据 */
    private Conversation conversation;

    /** 会话历史消息 */
    private List<ConversationMemory> historyMessages;

    /** 会话历史情绪分析数据，未压缩轮次之后的数据 */
    private List<EmotionAnalysis> emotionAnalyses;

}
