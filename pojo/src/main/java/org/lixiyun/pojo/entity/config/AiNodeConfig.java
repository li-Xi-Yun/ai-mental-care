package org.lixiyun.pojo.entity.config;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI诊断节点配置表(AiNodeConfig)实体类
 * <p>
 * 模型类型在 org.lixiyun.server.ai.model.factory.ChatModelType 中定义
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_node_config")
public class AiNodeConfig implements Serializable {

    public static final String NAME = "aiNodeConfig";

    private static final long serialVersionUID = 1L;

    /** 启用 */
    public static final int ENABLED_YES = 1;
    /** 禁用 */
    public static final int ENABLED_NO = 0;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 节点唯一标识，如psychologicalState/riskAssessment */
    private String nodeKey;

    /** 节点中文名称，如心理状态与症状评估 */
    private String nodeName;

    /** 节点分组，如process/input/knowledge，用于前端分类展示 */
    private String nodeGroup;

    /** 系统提示词，定义模型角色、输出格式、字段说明、注意事项等 */
    private String systemPrompt;

    /** 默认使用的模型类型：0-OLLAMA 1-DEEP_SEEK 2-DASH_SCOPE */
    private Integer modelType;

    /** DeepSeek模型名称 */
    private String deepseekModelName;

    /** Ollama模型名称 */
    private String ollamaModelName;

    /** DashScope模型名称 */
    private String dashscopeModelName;

    /** 最大输出token数 */
    private Integer maxToken;

    /** 温度参数，控制随机性，范围[0,2] */
    private BigDecimal temperature;

    /** Top-P核采样参数，范围[0,1] */
    private BigDecimal topP;

    /** 输出格式：0-自由文本(TEXT) 1-结构化JSON(JSON_OBJECT) */
    private Integer responseFormat;

    /** 停止序列，JSON数组格式存储，如["\n\n\n","```"] */
    private String stopSequences;

    /** Top-K采样参数，限制候选词数量 */
    private Integer topK;

    /** 频率惩罚，降低高频词出现概率 */
    private BigDecimal frequencyPenalty;

    /** 存在惩罚，增加新词出现概率 */
    private BigDecimal presencePenalty;

    /** 重试最大次数，包含第一次执行 */
    private Integer retryMaxAttempts;

    /** 重试初始间隔（毫秒） */
    private Integer retryDelay;

    /** 重试间隔乘数（指数退避） */
    private Integer retryMultiplier;

    /** 是否启用：0-禁用 1-启用 */
    private Integer enabled;

    /** 节点显示排序序号 */
    private Integer sort;

    /** 备注信息 */
    private String remark;

    /** 配置版本号，乐观锁，每次更新+1 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 创建人用户ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;

    /** 最后更新人用户ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 是否删除，0-否，1-是 */
    @TableLogic
    private Integer deleted;

    public boolean isEnabled() {
        return enabled != null && enabled == ENABLED_YES;
    }

    public boolean isDeleted() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}