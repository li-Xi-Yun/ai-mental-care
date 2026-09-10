package org.lixiyun.pojo.bo.conversation.diagnosis.input.clean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;

import java.io.Serializable;

/**
 * 消息级有效级别
 * 单条消息对应一个实例
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEffectiveLevel implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 消息级有效级别
     * 0=valid有效（可参与情绪分析）
     * 1=weak弱语义（低权重纳入统计）
     * 2=invalid无效（直接过滤）
     */
    private Integer level;

    /**
     * 消息内容
     */
    private ConversationMemory conversationMemory;

    public static String getPrompt() {
        return "level：消息级有效级别（0=valid有效，1=weak弱语义，2=invalid无效），" +
                "conversationMemory：消息内容记录，包含content消息文本内容、audioEmotionLabel语音情绪标签、type消息类型、roundNum轮次号";
    }
}