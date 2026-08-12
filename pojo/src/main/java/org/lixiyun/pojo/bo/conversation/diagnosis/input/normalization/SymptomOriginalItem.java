package org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 症状原始表述项
 * 记录单条用户原话的来源轮次与内容
 *
 * @author lixiyun
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomOriginalItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 对应对话轮次号
     */
    private Integer roundNum;

    /**
     * 用户原始表述内容
     * 完全保留用户原话，不做修改，用于溯源与人工复核
     */
    private String originalText;

    /**
     * 关联的标准术语ID
     * 与SymptomDict的termId对应，未匹配时为null
     */
    private Long matchedTermId;

    /**
     * 匹配置信度
     * 规则匹配默认1.0；模型匹配输出语义相似度分数，范围0-1
     */
    private BigDecimal matchConfidence;

    public static String getPrompt() {
        return "roundNum：对应对话轮次号，" +
                "originalText：用户原始表述内容，" +
                "matchedTermId：关联的标准术语ID，" +
                "matchConfidence：匹配置信度（0-1）";
    }
}