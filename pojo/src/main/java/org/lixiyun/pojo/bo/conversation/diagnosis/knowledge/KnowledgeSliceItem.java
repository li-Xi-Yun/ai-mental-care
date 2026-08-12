package org.lixiyun.pojo.bo.conversation.diagnosis.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单条知识切片（向量检索的最小单元，带完整元数据）
 *
 * @author lixiyun
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSliceItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 切片唯一ID（对应向量库的主键ID）
     */
    private String sliceId;

    /**
     * 所属文档ID
     * 多个切片来自同一文档时，该字段相同，可用于分组聚合
     */
    private String documentId;

    /**
     * 所属文档名称/标题
     * 例：《心理咨询师三级教材-症状学》《心理危机干预规范》
     */
    private String documentTitle;

    /**
     * 一级分块索引
     */
    private Integer chunkLevel1Idx;

    /**
     * 二级分块索引
     */
    private Integer chunkLevel2Idx;

    /**
     * 切片核心内容（知识库原文）
     */
    private String content;

    /**
     * 检索相似度得分（0~1，越高越相关）
     */
    private BigDecimal similarityScore;

    /**
     * 知识类型
     * symptom/diagnosis/intervention/risk
     */
    private String knowledgeType;

    /**
     * 知识来源（可选，合规溯源用）
     * 例：ICD-10、CBT干预指南、危机干预规范
     */
    private String source;
}