package org.lixiyun.pojo.bo.conversation.diagnosis.input.clean;

import lombok.Builder;
import lombok.Data;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;

import java.io.Serializable;
import java.util.List;

/**
 * 轮次级有效级别
 * 一个对话轮次对应一个实例，包含该轮下所有消息
 */
@Data
@Builder
public class RoundEffectiveLevel implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 轮次号
     */
    private Integer roundNum;

    /**
     * 轮次级整体有效级别
     * 0=valid有效，1=weak弱语义，2=invalid无效
     * 计算规则：取轮内所有消息的最高有效等级（valid > weak > invalid）
     */
    private Integer level;

    /**
     * 轮次级情绪分析结果
     */
    private EmotionAnalysis emotionAnalysis;

    /**
     * 该轮次下的所有消息级清洗结果
     */
    private List<MessageEffectiveLevel> messageEffectiveLevelList;

    public static String getPrompt() {
        return "roundNum：轮次号，" +
                "level：轮次级整体有效级别（0=valid有效，1=weak弱语义，2=invalid无效），" +
                "emotionAnalysis：轮次级情绪分析结果，包含emotionLabel情感标签、emotionSubLabel情感细分标签、emotionConfidence情感识别置信度、emotionIntensity情绪强烈程度、pScore PAD愉悦度、aScore PAD唤醒度、dScore PAD支配度，" +
                "messageEffectiveLevelList：" + MessageEffectiveLevel.getPrompt();
    }
}