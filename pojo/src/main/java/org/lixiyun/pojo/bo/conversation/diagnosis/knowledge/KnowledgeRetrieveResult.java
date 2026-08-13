package org.lixiyun.pojo.bo.conversation.diagnosis.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 知识侧-检索输出结果
 * <p>包含症状/诊断标准/干预方案三类参考文本及切片列表，以及检索元信息</p>
 *
 * @author lixiyun
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrieveResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NAME = "KnowledgeRetrieveResult";

    /** 知识类型-症状 */
    public static final String KNOWLEDGE_TYPE_SYMPTOM = "symptom";
    /** 知识类型-诊断标准 */
    public static final String KNOWLEDGE_TYPE_DIAGNOSIS = "diagnosis";
    /** 知识类型-干预方案 */
    public static final String KNOWLEDGE_TYPE_INTERVENTION = "intervention";

    /**
     * 症状类参考拼接文本
     */
    private String symptomReferencePrompt;

    /**
     * 诊断标准类参考拼接文本
     */
    private String diagnosisReferencePrompt;

    /**
     * 干预方案类参考拼接文本
     */
    private String interventionReferencePrompt;

    /**
     * 症状类检索切片列表（带完整元数据）
     */
    private List<KnowledgeSliceItem> symptomSliceList;

    /**
     * 诊断标准类检索切片列表
     */
    private List<KnowledgeSliceItem> diagnosisSliceList;

    /**
     * 干预方案类检索切片列表
     */
    private List<KnowledgeSliceItem> interventionSliceList;

}