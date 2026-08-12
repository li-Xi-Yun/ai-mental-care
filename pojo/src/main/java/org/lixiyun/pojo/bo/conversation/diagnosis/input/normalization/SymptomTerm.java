package org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.entity.conversation.SymptomDict;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 会话级症状统计结果
 * 基于标准字典，叠加本次会话的统计信息
 *
 * @author lixiyun
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomTerm implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 匹配来源：规则词典匹配 */
    public static final int MATCH_SOURCE_RULE = 0;
    /** 匹配来源：模型语义匹配 */
    public static final int MATCH_SOURCE_MODEL = 1;
    /** 匹配来源：未匹配保留原文 */
    public static final int MATCH_SOURCE_RAW = 2;

    /**
     * 标准术语字典
     */
    private SymptomDict symptomDict;

    /**
     * 症状出现总次数
     * 全会话中该症状累计出现的轮次数
     */
    private Integer appearCount;

    /**
     * 症状出现的对应轮次号列表
     * 用于溯源定位，可快速找到对应对话上下文
     */
    private List<Integer> roundList;

    /**
     * 匹配来源
     * 枚举值：rule（规则词典匹配）/ model（模型语义匹配）/ raw（未匹配保留原文）
     * @see #MATCH_SOURCE_RULE
     * @see #MATCH_SOURCE_MODEL
     * @see #MATCH_SOURCE_RAW
     */
    private Integer matchSource;

    /**
     * 匹配置信度
     * 规则匹配默认1.0；模型匹配输出语义相似度分数，范围0-1；未匹配时为null
     */
    private BigDecimal matchConfidence;

    public static String getPrompt() {
        return "symptomDict：标准术语字典，包含symptomTerm标准症状名称、symptomCategory症状大类、severityDefault默认严重程度，" +
                "appearCount：症状出现总次数，" +
                "roundList：症状出现的对应轮次号列表，" +
                "matchSource：匹配来源（0=规则词典匹配，1=模型语义匹配，2=未匹配保留原文），" +
                "matchConfidence：匹配置信度（0-1）";
    }
}