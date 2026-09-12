package org.lixiyun.pojo.entity.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI流程执行记录主表(ai_flow_execution)实体类
 *
 * @author lixiyun
 * @since 2026-09-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_flow_execution")
public class AiFlowExecution implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程类型-会话消息处理 */
    public static final int FLOW_TYPE_CHAT = 1;
    /** 流程类型-仅诊断分析 */
    public static final int FLOW_TYPE_DIAGNOSIS = 2;
    /** 流程类型-批量分析 */
    public static final int FLOW_TYPE_BATCH = 3;
    /** 流程类型-报告生成 */
    public static final int FLOW_TYPE_REPORT = 4;

    /** 流程状态-运行中 */
    public static final int STATUS_RUNNING = 1;
    /** 流程状态-完成 */
    public static final int STATUS_COMPLETED = 2;
    /** 流程状态-失败 */
    public static final int STATUS_FAILED = 3;
    /** 流程状态-超时 */
    public static final int STATUS_TIMEOUT = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程唯一标识（雪花算法） */
    private Long traceId;

    /** 会话ID */
    private Long conversationId;

    /** 轮次编号 */
    private Integer roundNum;

    /** 用户ID */
    private Long userId;

    /** 流程类型（1=会话消息处理 2=仅诊断分析 3=批量分析 4=报告生成） */
    private Integer flowType;

    /** 流程状态（1=运行中 2=完成 3=失败 4=超时） */
    private Integer status;

    /** 错误码 */
    private String errorCode;

    /** 错误详细信息 */
    private String errorMessage;

    /** 流程开始时间 */
    private LocalDateTime startedAt;

    /** 流程结束时间 */
    private LocalDateTime finishedAt;

    /** 流程总耗时（ms） */
    private Long durationMs;

    /** 执行的节点总数 */
    private Integer nodeCount;

    /** 模型调用次数 */
    private Integer modelCallCount;

    /** 所有模型调用的输入token总和 */
    private Long totalInputTokens;

    /** 所有模型调用的输出token总和 */
    private Long totalOutputTokens;

    /** 所有模型调用的总token数 */
    private Long totalTokenCount;

    /** 所有模型调用的总成本（人民币） */
    private BigDecimal totalCostCny;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;
}