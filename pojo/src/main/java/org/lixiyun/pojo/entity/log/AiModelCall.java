package org.lixiyun.pojo.entity.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI模型调用记录表(ai_model_call)实体类
 *
 * @author lixiyun
 * @since 2026-09-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_model_call", autoResultMap = true)
public class AiModelCall implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 调用类型-同步调用 */
    public static final int CALL_TYPE_SYNC = 1;
    /** 调用类型-流式调用 */
    public static final int CALL_TYPE_STREAM = 2;

    /** 调用状态-完成 */
    public static final int STATUS_COMPLETED = 1;
    /** 调用状态-失败 */
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程唯一标识，关联ai_flow_execution.trace_id */
    private Long traceId;

    /** 关联的节点执行记录ID，关联ai_node_execution.id */
    private Long nodeId;

    /** 节点唯一标识，如psychologicalState/riskAssessment */
    private String nodeKey;

    /** 节点中文名称，如心理状态与症状评估 */
    private String nodeName;

    /** 调用类型（1=同步调用 2=流式调用） */
    private Integer callType;

    /** 模型供应商（ollama/deepseek/dashscope/openai等） */
    private String modelProvider;

    /** 模型名称（deepseek-v3/qwen-plus/llama3等） */
    private String modelName;

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户提示词 */
    private String userPrompt;

    /** 模型推理参数（temperature/topP/topK/stopSequences等） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> modelParams;

    /** 调用状态（1=完成 2=失败） */
    private Integer status;

    /** 错误码 */
    private String errorCode;

    /** 错误详细信息 */
    private String errorMessage;

    /** 模型输出内容（同步：完整文本；流式：拼接后的完整文本） */
    private String modelOutput;

    /** 输入token数 */
    private Long inputTokens;

    /** 输出token数 */
    private Long outputTokens;

    /** 总token数（input+output） */
    private Long totalTokens;

    /** 本次调用成本（人民币） */
    private BigDecimal costCny;

    /** 调用开始时间 */
    private LocalDateTime startedAt;

    /** 调用结束时间 */
    private LocalDateTime finishedAt;

    /** 调用耗时（ms） */
    private Long durationMs;

    /** 本次调用触发的工具调用次数 */
    private Integer toolCallCount;

    /** 结束原因（stop/tool_calls/length/content_filter等） */
    private String finishReason;

    /** 创建时间 */
    private LocalDateTime createdTime;
}