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

    /**
     * 症状库查询降级级别（0=精准版，1=简化版，2=极简版）
     * <p>重试时递增，查询变换层根据级别生成对应复杂度的Query</p>
     */
    private int symptomQueryLevel;

    /**
     * 诊断标准库查询降级级别（0=精准版，1=简化版，2=极简版）
     * <p>重试时递增，查询变换层根据级别生成对应复杂度的Query</p>
     */
    private int diagnosisQueryLevel;

    /**
     * 干预方案库查询降级级别（0=精准版，1=简化版，2=极简版）
     * <p>重试时递增，查询变换层根据级别生成对应复杂度的Query</p>
     */
    private int interventionQueryLevel;

    /**
     * 症状库重试次数
     * <p>每次触发症状库重试时递增，达到最大值后使用兜底内容</p>
     */
    private int symptomRetryCount;

    /**
     * 诊断标准库重试次数
     * <p>每次触发诊断标准库重试时递增，达到最大值后使用兜底内容</p>
     */
    private int diagnosisRetryCount;

    /**
     * 干预方案库重试次数
     * <p>每次触发干预方案库重试时递增，达到最大值后使用兜底内容</p>
     */
    private int interventionRetryCount;

    /**
     * 症状库是否需要查询变换
     * <p>重排层前置/后置检查发现症状库为空时置为true，查询变换层据此只为需要重试的类型生成新Query</p>
     */
    private boolean symptomNeedTransform;

    /**
     * 诊断标准库是否需要查询变换
     * <p>重排层前置/后置检查发现诊断标准库为空时置为true，查询变换层据此只为需要重试的类型生成新Query</p>
     */
    private boolean diagnosisNeedTransform;

    /**
     * 干预方案库是否需要查询变换
     * <p>重排层前置/后置检查发现干预方案库为空时置为true，查询变换层据此只为需要重试的类型生成新Query</p>
     */
    private boolean interventionNeedTransform;
}