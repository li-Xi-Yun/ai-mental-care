-- ============================================================
-- AI Agent 执行日志记录 — 建表语句
-- ============================================================
-- 设计原则：
--   1. 分层记录：流程 → 节点 → 模型调用 → 工具调用
--   2. 无外键约束，表间关联由程序控制
--   3. trace_id 使用雪花算法（BIGINT），作为流程唯一标识
--   4. 枚举字段统一使用 TINYINT
--   5. Token 相关字段使用 BIGINT
-- ============================================================


-- ============================================================
-- 第1层：流程执行记录主表
-- ============================================================
-- 粒度：1 次流程 = 1 条记录
-- 写入时机：流程入口 INSERT，流程出口 UPDATE
-- 产生者：ConversationMessageProcessor

CREATE TABLE ai_flow_execution (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    trace_id                BIGINT          NOT NULL COMMENT '流程唯一标识（雪花算法）',
    conversation_id         BIGINT          NOT NULL COMMENT '会话ID',
    round_num               INT             NOT NULL COMMENT '轮次编号',
    user_id                 BIGINT          NULL     COMMENT '用户ID',
    flow_type               TINYINT         NOT NULL COMMENT '流程类型（1=会话消息处理 2=仅诊断分析 3=批量分析 4=报告生成）',
    status                  TINYINT         NOT NULL COMMENT '流程状态（1=运行中 2=完成 3=失败 4=超时）',
    error_code              VARCHAR(64)     NULL     COMMENT '错误码',
    started_at              DATETIME(3)     NOT NULL COMMENT '流程开始时间',
    finished_at             DATETIME(3)     NULL     COMMENT '流程结束时间',
    duration_ms             BIGINT          NULL     COMMENT '流程总耗时（ms）',
    node_count              INT             NULL     COMMENT '执行的节点总数',
    model_call_count        INT             NULL     COMMENT '模型调用次数',
    total_input_tokens      BIGINT          NULL     COMMENT '所有模型调用的输入token总和',
    total_output_tokens     BIGINT          NULL     COMMENT '所有模型调用的输出token总和',
    total_token_count       BIGINT          NULL     COMMENT '所有模型调用的总token数',
    total_cost_cny          DECIMAL(10,6)   NULL     COMMENT '所有模型调用的总成本（人民币）',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    updated_at              DATETIME(3)     NULL     COMMENT '记录更新时间',

    UNIQUE INDEX uk_trace_id            (trace_id),
    INDEX idx_conversation_id           (conversation_id),
    INDEX idx_conversation_round        (conversation_id, round_num),
    INDEX idx_user_id                   (user_id),
    INDEX idx_flow_type                 (flow_type),
    INDEX idx_status                    (status),
    INDEX idx_started_at                (started_at),
    INDEX idx_flow_type_started         (flow_type, started_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI流程执行记录主表';


-- ============================================================
-- 第2层：节点执行记录表
-- ============================================================
-- 粒度：1 个节点 = 1 条记录
-- 写入时机：节点执行前 INSERT，执行后 UPDATE
-- 产生者：各 Node 执行前后

-- CREATE TABLE ai_node_execution (
--     待设计
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
--   COMMENT='AI节点执行记录表';

CREATE TABLE ai_node_execution (
                                   id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
                                   trace_id                BIGINT          NOT NULL COMMENT '流程唯一标识（雪花算法），关联ai_flow_execution.trace_id',
                                   node_name               VARCHAR(64)     NOT NULL COMMENT '节点名称',
                                   node_sequence           INT             NOT NULL COMMENT '全局执行顺序，从1递增',
                                   status                  TINYINT         NOT NULL COMMENT '节点状态（1=运行中 2=完成 3=失败）',
                                   error_code              VARCHAR(64)     NULL     COMMENT '错误码',
                                   error_message           TEXT            NULL     COMMENT '错误详细信息',
                                   started_at              DATETIME(3)     NOT NULL COMMENT '节点开始时间',
                                   finished_at             DATETIME(3)     NULL     COMMENT '节点结束时间',
                                   duration_ms             BIGINT          NULL     COMMENT '节点执行耗时（ms）',
                                   input_summary           JSON            NULL     COMMENT '输入参数',
                                   output_summary          JSON            NULL     COMMENT '输出结果',
                                   created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',

                                   INDEX idx_trace_id              (trace_id),
                                   INDEX idx_trace_sequence        (trace_id, node_sequence),
                                   INDEX idx_node_name             (node_name),
                                   INDEX idx_status                (status),
                                   INDEX idx_started_at            (started_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI节点执行记录表';

-- ============================================================
-- 第3层：模型调用记录表
-- ============================================================
-- 粒度：1 次模型调用 = 1 条记录
-- 写入时机：Interceptor 中 INSERT（输入侧），流式完成时 UPDATE（输出侧）
-- 产生者：RecordingModelInterceptor + BaseModel.doStream()

-- CREATE TABLE ai_model_call (
--     待设计
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
--   COMMENT='AI模型调用记录表';


-- ============================================================
-- 第4层：工具调用记录表
-- ============================================================
-- 粒度：1 次工具调用 = 1 条记录
-- 写入时机：ToolInterceptor 中记录
-- 产生者：RecordingToolInterceptor

-- CREATE TABLE ai_tool_call (
--     待设计
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
--   COMMENT='AI工具调用记录表';