package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识侧检索过程记录表(RagRetrieveLog)实体类
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "rag_retrieve_log", autoResultMap = true)
public class RagRetrieveLog implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 关联的诊断记录ID
     */
    private Long diagnosisId;
    
    /**
     * 检索入参（结构化特征JSON）
     */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestParams;
    
    /**
     * 检索重试次数
     */
    private Integer retryCount;
    
    /**
     * 症状知识库召回文档数量
     */
    private Integer symptomDocCount;
    
    /**
     * 诊断标准库召回文档数量
     */
    private Integer diagnosisDocCount;
    
    /**
     * 干预方案库召回文档数量
     */
    private Integer interventionDocCount;
    
    /**
     * 检索总耗时（毫秒）
     */
    private Integer retrieveCostTime;
    
    /**
     * 重排结果：分片ID+得分列表JSON
     */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> reruleResult;
    
    /**
     * 是否触发兜底：0=否 1=是
     */
    private Integer fallbackStatus;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}