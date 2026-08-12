package org.lixiyun.pojo.bo.conversation.diagnosis.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 知识侧检索入参对象
 *
 * @author lixiyun
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeMatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NAME = "KnowledgeMatchRequest";

    /** 模块4归一化后的标准症状列表 */
    private List<String> standardSymptoms;

    /** 模块3输出的主导情绪 */
    private String coreEmotion;

    /** 模块2输出的用户核心诉求 */
    private String coreAppeal;

    /** 症状类Prompt，用于症状知识库中进行向量检索 */
    private String symptomPrompt;

    /** 诊断标准类Prompt，用于诊断标准知识库中进行向量检索 */
    private String diagnosisPrompt;

    /** 干预方案类Prompt，用于干预方案知识库中进行向量检索 */
    private String interventionPrompt;

    /** 症状类分片ID与相似度的键值对 */
    private Map<Long, Double> symptomSliceIds;

    /** 诊断标准类分片ID与相似度的键值对 */
    private Map<Long, Double> diagnosisSliceIds;

    /** 干预方案类分片ID与相似度的键值对 */
    private Map<Long, Double> interventionSliceIds;
}