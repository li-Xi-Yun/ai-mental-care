package org.lixiyun.pojo.dto.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI节点配置新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Data
@Schema(description = "AI节点配置新增/修改DTO")
public class AiNodeConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "节点唯一标识不能为空", groups = {AddGroup.class})
    @Schema(description = "节点唯一标识，如psychologicalState/riskAssessment", requiredMode = Schema.RequiredMode.REQUIRED, example = "psychologicalState")
    private String nodeKey;

    @NotBlank(message = "节点中文名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "节点中文名称，如心理状态与症状评估", requiredMode = Schema.RequiredMode.REQUIRED, example = "心理状态与症状评估")
    private String nodeName;

    @Schema(description = "节点分组：process/input/knowledge", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "process")
    private String nodeGroup;

    @NotBlank(message = "系统提示词不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "系统提示词", requiredMode = Schema.RequiredMode.REQUIRED, example = "你是一个心理健康领域的评估助手……")
    private String systemPrompt;

    @NotNull(message = "模型类型不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @NumberOfRanges(min = 0, max = 2)
    @Schema(description = "默认使用的模型类型：0-OLLAMA 1-DEEP_SEEK 2-DASH_SCOPE", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer modelType;

    @Schema(description = "DeepSeek模型名称", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "deepseek-chat")
    private String deepseekModelName;

    @Schema(description = "Ollama模型名称", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "qwen3:7b-chat-thinking")
    private String ollamaModelName;

    @Schema(description = "DashScope模型名称", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "qwen-max")
    private String dashscopeModelName;

    @NotNull(message = "最大输出token数不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "最大输出token数", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Integer maxToken;

    @NotNull(message = "温度参数不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @DecimalMin(value = "0", message = "温度参数不能小于0")
    @DecimalMax(value = "2", message = "温度参数不能大于2")
    @Schema(description = "温度参数，控制随机性，范围[0,2]", requiredMode = Schema.RequiredMode.REQUIRED, example = "0.2")
    private BigDecimal temperature;

    @NotNull(message = "Top-P参数不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @DecimalMin(value = "0", message = "Top-P参数不能小于0")
    @DecimalMax(value = "1", message = "Top-P参数不能大于1")
    @Schema(description = "Top-P核采样参数，范围[0,1]", requiredMode = Schema.RequiredMode.REQUIRED, example = "0.85")
    private BigDecimal topP;

    @Schema(description = "Top-K采样参数，限制候选词数量", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "50")
    private Integer topK;

    @DecimalMin(value = "-2", message = "频率惩罚不能小于-2")
    @DecimalMax(value = "2", message = "频率惩罚不能大于2")
    @Schema(description = "频率惩罚，降低高频词出现概率，范围[-2,2]", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "0.7")
    private BigDecimal frequencyPenalty;

    @DecimalMin(value = "-2", message = "存在惩罚不能小于-2")
    @DecimalMax(value = "2", message = "存在惩罚不能大于2")
    @Schema(description = "存在惩罚，增加新词出现概率，范围[-2,2]", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "0.3")
    private BigDecimal presencePenalty;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "输出格式：0-自由文本(TEXT) 1-结构化JSON(JSON_OBJECT)", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer responseFormat;

    @Schema(description = "停止序列，JSON数组格式，如[\"\\n\\n\\n\",\"```\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "[\"\\n\\n\\n\",\"```\"]")
    private String stopSequences;

    @NotNull(message = "重试最大次数不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "重试最大次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer retryMaxAttempts;

    @NotNull(message = "重试初始间隔不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "重试初始间隔（毫秒）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    private Integer retryDelay;

    @NotNull(message = "重试间隔乘数不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "重试间隔乘数（指数退避）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer retryMultiplier;

    @Schema(description = "是否启用：0-禁用 1-启用", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer enabled;

    @Schema(description = "节点显示排序序号", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer sort;

    @Schema(description = "备注信息", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "心理状态评估节点")
    private String remark;

    @NotNull(message = "配置版本号不能为空", groups = {UpdateGroup.class})
    @Schema(description = "配置版本号（乐观锁，修改时必传）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer version;
}