package org.lixiyun.pojo.vo.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI节点配置VO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Schema(description = "AI节点配置VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiNodeConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "自增主键ID", example = "1")
    private Long id;

    @Schema(description = "节点唯一标识", example = "psychologicalState")
    private String nodeKey;

    @Schema(description = "节点中文名称", example = "心理状态与症状评估")
    private String nodeName;

    @Schema(description = "节点分组", example = "process")
    private String nodeGroup;

    @Schema(description = "系统提示词", example = "你是一个心理健康领域的评估助手……")
    private String systemPrompt;

    @Schema(description = "默认使用的模型类型：0-OLLAMA 1-DEEP_SEEK 2-DASH_SCOPE", example = "1")
    private Integer modelType;

    @Schema(description = "DeepSeek模型名称", example = "deepseek-chat")
    private String deepseekModelName;

    @Schema(description = "Ollama模型名称", example = "qwen3:7b-chat-thinking")
    private String ollamaModelName;

    @Schema(description = "DashScope模型名称", example = "qwen-max")
    private String dashscopeModelName;

    @Schema(description = "最大输出token数", example = "2048")
    private Integer maxToken;

    @Schema(description = "温度参数", example = "0.2")
    private BigDecimal temperature;

    @Schema(description = "Top-P核采样参数", example = "0.85")
    private BigDecimal topP;

    @Schema(description = "Top-K采样参数", example = "50")
    private Integer topK;

    @Schema(description = "频率惩罚", example = "0.7")
    private BigDecimal frequencyPenalty;

    @Schema(description = "存在惩罚", example = "0.3")
    private BigDecimal presencePenalty;

    @Schema(description = "重复惩罚（Ollama专用）", example = "1.1")
    private BigDecimal repeatPenalty;

    @Schema(description = "随机种子", example = "42")
    private Integer seed;

    @Schema(description = "重试最大次数", example = "3")
    private Integer retryMaxAttempts;

    @Schema(description = "重试初始间隔（毫秒）", example = "1000")
    private Integer retryDelay;

    @Schema(description = "重试间隔乘数（指数退避）", example = "2")
    private Integer retryMultiplier;

    @Schema(description = "是否启用：0-禁用 1-启用", example = "1")
    private Integer enabled;

    @Schema(description = "节点显示排序序号", example = "1")
    private Integer sort;

    @Schema(description = "备注信息", example = "心理状态评估节点")
    private String remark;

    @Schema(description = "配置版本号", example = "1")
    private Integer version;

    @Schema(description = "创建时间", example = "2026-09-07 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "创建人用户ID", example = "1")
    private Long createdBy;

    @Schema(description = "最后更新时间", example = "2026-09-07 12:00:00")
    private LocalDateTime updatedTime;

    @Schema(description = "最后更新人用户ID", example = "1")
    private Long updatedBy;
}