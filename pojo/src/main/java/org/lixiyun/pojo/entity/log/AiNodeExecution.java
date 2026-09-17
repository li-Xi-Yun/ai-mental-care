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
import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI节点执行记录表(ai_node_execution)实体类
 *
 * @author lixiyun
 * @since 2026-09-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_node_execution", autoResultMap = true)
public class AiNodeExecution implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点状态-运行中 */
    public static final int STATUS_RUNNING = 1;
    /** 节点状态-完成 */
    public static final int STATUS_COMPLETED = 2;
    /** 节点状态-失败 */
    public static final int STATUS_FAILED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程唯一标识（雪花算法），关联ai_flow_execution.trace_id */
    private Long traceId;

    /** 节点模型配置id */
    private Long nodeConfigId;

    /** 节点唯一标识，如psychologicalState/riskAssessment */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 全局执行顺序，从1递增 */
    private Integer nodeSequence;

    /** 节点状态（1=运行中 2=完成 3=失败） */
    private Integer status;

    /** 错误码 */
    private String errorCode;

    /** 错误详细信息 */
    private String errorMessage;

    /** 节点开始时间 */
    private LocalDateTime startedAt;

    /** 节点结束时间 */
    private LocalDateTime finishedAt;

    /** 节点执行耗时（ms） */
    private Long durationMs;

    /** 输入参数 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSummary;

    /** 输出结果 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputSummary;

    /** 创建时间 */
    private LocalDateTime createdTime;
}