# AI 执行日志记录 — 方案执行完整流程

> 整合四份子方案文档，给出从基础设施到运行时的完整执行流程。

---

## 一、执行阶段总览

| 阶段 | 内容 | 对应文档 |
|:----:|------|---------|
| 0 | 基础设施准备（DDL、常量类、Redis 封装类） | — |
| 1 | 主流程 AOP（FlowExecutionAspect） | ai-flow-execution-aop-plan.md |
| 2 | Diagnosis 节点 AOP（NodeExecutionAspect.aroundDiagnosisNode） | ai-diagnosis-node-aop-plan.md |
| 3 | Conversation 节点 AOP（NodeExecutionAspect.aroundConversationNode） | ai-conversation-node-aop-plan.md |
| 4 | 模型调用记录（RecordingModelInterceptor + BaseModel 改造） | ai-model-call-recording-final-plan.md |
| 5 | 聚合统计（异步延迟聚合） | ai-flow-execution-aop-plan.md §六 |

---

## 二、阶段 0：基础设施准备

### 0-1 执行 DDL 建表

```sql
-- ai_flow_execution：主流程记录表
CREATE TABLE ai_flow_execution (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    trace_id                BIGINT          NOT NULL,
    conversation_id         BIGINT          NOT NULL,
    round_num               INT             NULL,
    user_id                 BIGINT          NULL,
    flow_type               TINYINT         NOT NULL COMMENT '1=会话消息处理 2=仅诊断分析 3=批量分析 4=报告生成',
    status                  TINYINT         NOT NULL COMMENT '1=运行中 2=完成 3=失败',
    error_code              VARCHAR(64)     NULL,
    error_message           TEXT            NULL,
    started_at              DATETIME(3)     NOT NULL,
    finished_at             DATETIME(3)     NULL,
    duration_ms             BIGINT          NULL,
    node_count              INT             NULL,
    model_call_count        INT             NULL,
    total_input_tokens      BIGINT          NULL,
    total_output_tokens     BIGINT          NULL,
    total_token_count       BIGINT          NULL,
    total_cost_cny          DECIMAL(10,6)   NULL,
    updated_at              DATETIME(3)     NULL,
    UNIQUE INDEX uk_trace_id (trace_id),
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_conversation_round (conversation_id, round_num),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI流程执行记录主表';

-- ai_node_execution：节点执行记录表
CREATE TABLE ai_node_execution (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    trace_id                BIGINT          NOT NULL,
    node_name               VARCHAR(64)     NOT NULL,
    node_sequence           INT             NOT NULL,
    status                  TINYINT         NOT NULL COMMENT '1=运行中 2=完成 3=失败',
    error_code              VARCHAR(64)     NULL,
    error_message           TEXT            NULL,
    started_at              DATETIME(3)     NOT NULL,
    finished_at             DATETIME(3)     NULL,
    duration_ms             BIGINT          NULL,
    input_summary           JSON            NULL,
    output_summary          JSON            NULL,
    updated_at              DATETIME(3)     NULL,
    INDEX idx_trace_id (trace_id),
    INDEX idx_trace_sequence (trace_id, node_sequence),
    INDEX idx_node_name (node_name),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI节点执行记录表';

-- ai_model_call：模型调用记录表
CREATE TABLE ai_model_call (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    trace_id                BIGINT          NOT NULL,
    node_id                 BIGINT          NULL,
    call_type               TINYINT         NOT NULL COMMENT '1=同步调用 2=流式调用',
    model_provider          VARCHAR(32)     NOT NULL,
    model_name              VARCHAR(128)    NOT NULL,
    system_prompt           TEXT            NULL,
    user_prompt             TEXT            NULL,
    model_params            JSON            NULL,
    status                  TINYINT         NOT NULL COMMENT '1=完成 2=失败',
    error_code              VARCHAR(64)     NULL,
    error_message           TEXT            NULL,
    model_output            TEXT            NULL,
    input_tokens            BIGINT          NULL,
    output_tokens           BIGINT          NULL,
    total_tokens            BIGINT          NULL,
    cost_cny                DECIMAL(10,6)   NULL,
    started_at              DATETIME(3)     NOT NULL,
    finished_at             DATETIME(3)     NULL,
    duration_ms             BIGINT          NULL,
    tool_call_count         INT             NULL,
    finish_reason           VARCHAR(32)     NULL,
    updated_at              DATETIME(3)     NULL,
    INDEX idx_trace_id (trace_id),
    INDEX idx_node_id (node_id),
    INDEX idx_model_provider (model_provider),
    INDEX idx_model_name (model_name),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at),
    INDEX idx_model_started (model_name, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型调用记录表';
```

### 0-2 创建 FlowExecutionConstant

```java
public interface FlowExecutionConstant {
    String CTX_KEY_PREFIX = "flow:ctx:";
    String FIELD_TRACE_ID = "trace_id";
    String FIELD_CONVERSATION_ID = "conversation_id";
    String FIELD_ROUND_NUM = "round_num";
    String FIELD_USER_ID = "user_id";
    String FIELD_NODE_SEQUENCE = "node_sequence";
    String FIELD_ASYNC_TASK_COUNT = "async_task_count";
    String FIELD_ASYNC_TASK_DONE = "async_task_done";
    int CTX_TTL_MINUTES = 30;
}
```

### 0-3 创建 FlowExecutionContextManager

Spring Bean，封装所有 Redis Hash 操作，方法清单：

| 方法 | 说明 |
|------|------|
| `initContext(conversationId, traceId)` | 流程入口初始化全部字段，设 TTL |
| `getTraceId(conversationId)` | 获取 trace_id |
| `incrementNodeSequence(conversationId)` | 原子递增 node_sequence 并返回递增后的值 |
| `updateRoundNumAndUserId(conversationId, roundNum, userId)` | 补填 round_num 和 user_id |
| `getRoundNum(conversationId)` | 获取 round_num |
| `getUserId(conversationId)` | 获取 user_id |
| `existsContext(conversationId)` | 判断上下文是否存在（降级判断用） |
| `removeContext(conversationId)` | 删除上下文（流程结束清理） |
| `incrementAsyncTaskCount(conversationId)` | 原子递增异步任务总数 |
| `incrementAsyncTaskDone(conversationId)` | 原子递增已完成异步任务数 |

### 0-4 确认/补充 conversationId 字段

确认 `KnowledgeMatchRequest`、`DiagnosisDataRequest` 是否包含 `conversationId` 字段。若无，需补充。

---

## 三、阶段 1：主流程 AOP

### 切面类

```
FlowExecutionAspect
├── 依赖注入
│   ├── FlowExecutionContextManager
│   ├── AiFlowExecutionMapper
│   └── ConversationRepository
├── 切点
│   └── execution(* ConversationMessageProcessor.processConversationMessage(Long))
└── 通知: @Around
```

### 执行流程

```
@Around 拦截 processConversationMessage(conversationId)
│
├── 【入口 — INSERT】
│   ├── trace_id = IdUtil.getSnowflake(23, 17).nextId()
│   ├── started_at = Instant.now()
│   ├── FlowExecutionContextManager.initContext(conversationId, traceId)
│   └── INSERT ai_flow_execution (trace_id, conversation_id, flow_type=1, status=1, started_at)
│
├── 【执行原方法】pjp.proceed()
│
└── 【finally — UPDATE】
    ├── finished_at = Instant.now()
    ├── duration_ms = 计算
    ├── 反查 Conversation → round_num, user_id
    ├── FlowExecutionContextManager.updateRoundNumAndUserId(conversationId, roundNum, userId)
    ├── status = 有异常 ? 3(失败) : 2(完成)
    ├── UPDATE ai_flow_execution SET status, finished_at, duration_ms, round_num, user_id, ...
    └── FlowExecutionContextManager.removeContext(conversationId)
```

---

## 四、阶段 2：Diagnosis 节点 AOP

### 业务代码修改（3 行）

| 文件 | 修改内容 |
|------|---------|
| `InputGraph.executeGraph()` | `RunnableConfig` 加 `.addContextMapEntry("conversation_id", contextBO.getConversation().getId())` |
| `KnowledgeGraph.executeGraph()` | `.addContextMapEntry("conversation_id", knowledgeMatchRequest.getConversationId())` |
| `ProcessGraph.executeGraph()` | `.addContextMapEntry("conversation_id", diagnosisDataRequest.getConversationId())` |

### 切点表达式

```java
@Around(
    "execution(* org.lixiyun.server.ai.node.diagnosis..*.apply(OverAllState, RunnableConfig)) " +
    "&& target(org.lixiyun.server.ai.node.NodeExecutionSummary)"
)
```

### 执行流程

```
@Around 拦截 diagnosis 节点 apply(OverAllState, RunnableConfig)
│
├── 【入口 — INSERT】
│   ├── conversationId = config.contextMap["conversation_id"]
│   ├── existsContext(conversationId) → false 则跳过日志，直接 proceed
│   ├── trace_id = FlowExecutionContextManager.getTraceId(conversationId)
│   ├── node_name = 反射读取 NODE_NAME
│   ├── node_sequence = FlowExecutionContextManager.incrementNodeSequence(conversationId)
│   ├── input_summary = ((NodeExecutionSummary) target).inputSummary(state)
│   └── INSERT ai_node_execution (trace_id, node_name, node_sequence, status=1, started_at, input_summary)
│
├── 【执行原方法】result = pjp.proceed()
│
└── 【finally — UPDATE】
    ├── output_summary = ((NodeExecutionSummary) target).outputSummary(state)
    ├── status = 有异常 ? 3(失败) : 2(完成)
    └── UPDATE ai_node_execution SET status, finished_at, duration_ms, output_summary, ...
```

### 节点清单（21 个）

**InputGraph**：PreCleanNode、MessageStructuredProcessNode、EmotionStatisticsNode、HistoryDiagnosisSummaryNode

**KnowledgeGraph**：QueryTransformLayerNode、SymptomKnowledgeRepositoryNode、DiagnosisStandardRepositoryNode、InterventionPlanRepositoryNode、RerankLayerNode

**ProcessGraph**：ComprehensiveDiagnosisNode、DiseaseCourseAttributionNode、PsychologicalStateNode、SocialFunctionImpactNode、ProtectiveFactorNode、RiskAssessmentNode、InterventionSuggestionNode、DiagnosisSummaryNode

**子图入口**：InputNode、KnowledgeNode、ProcessNode

---

## 五、阶段 3：Conversation 节点 AOP

### 切点表达式

```java
@Around(
    "execution(* org.lixiyun.server.ai.node.conversation..*.apply(..)) " +
    "&& !execution(* org.lixiyun.server.ai.node.conversation..*.apply(OverAllState, ..))"
)
```

### 执行流程

```
@Around 拦截 conversation 节点 apply(业务对象)
│
├── 【入口 — INSERT】
│   ├── conversationId = 从方法参数 instanceof 判断提取
│   │     ConversationProcessContextBO → ctx.getConversation().getId()
│   │     HistoryCompressionBO → bo.getConversation().getId()
│   ├── existsContext(conversationId) → false 则跳过日志，直接 proceed
│   ├── trace_id = FlowExecutionContextManager.getTraceId(conversationId)
│   ├── node_name = 反射读取 NODE_NAME
│   ├── node_sequence = FlowExecutionContextManager.incrementNodeSequence(conversationId)
│   ├── input_summary = 按参数类型提取
│   └── INSERT ai_node_execution (...)
│
├── 【执行原方法】result = pjp.proceed()
│
└── 【finally — UPDATE】
    ├── output_summary = 按返回类型提取
    │     String → 直接作为 output
    │     EmotionRecognitionResult → 整个对象
    └── UPDATE ai_node_execution SET ...
```

### 节点清单（4 个）

| 节点 | NODE_NAME | 返回类型 |
|------|-----------|---------|
| ConversationNameGenerationNode | conversationNameGenerationNode | String |
| EmotionRecognitionNode | emotionRecognitionNode | EmotionRecognitionResult |
| HistoryMessageCompressionNode | historyMessageCompressionNode | String |
| HistoryAnalysisCompressionNode | historyAnalysisCompressionNode | String |

---

## 六、阶段 4：模型调用记录

### 新增文件

| # | 类 | 说明 |
|---|---|------|
| 1 | `AiCallContextHolder` | ThreadLocal 上下文持有器（conversationId、roundNum、isStream、streamRecordId） |
| 2 | `RecordingModelInterceptor` | 继承 BaseModelInterceptor，记录输入侧 + 同步输出侧 |
| 3 | `AiModelCallRecordStorage` | 存储接口 |
| 4 | `DatabaseAiModelCallRecordStorage` | 数据库存储实现（异步写入） |
| 5 | `AiModelCallRecordMapper` | Mapper 接口 |

### BaseModel 改造（~40 行）

1. `reactAgentBuilder()` 中注册 `RecordingModelInterceptor`
2. `doStream()` 返回的 Flux 叠加 `wrapStreamWithRecording()`：
   - `doOnNext`：逐 chunk 累积到 StringBuilder（纯内存操作）
   - `AGENT_MODEL_FINISHED`：一次性 UPDATE 完整输出到数据库

### 调用路径

**同步调用**：
```
doCall() → ReactAgent.call() → RecordingModelInterceptor.interceptBaseModel()
  → INSERT 一条完整记录（输入+输出全部写入） → 完成
```

**流式调用**：
```
doStream() → ReactAgent.stream() → RecordingModelInterceptor.interceptBaseModel()
  → INSERT 记录（输入侧，modelOutput=null）→ recordId 存入 AiCallContextHolder
  → wrapStreamWithRecording() 叠加副作用
    → doOnNext: 逐 chunk 累积到 StringBuilder
    → AGENT_MODEL_FINISHED: UPDATE modelOutput=完整文本 WHERE id=recordId
```

### 异步线程上下文传播

`diagnosisThreadPoolTaskExecutor` 加 `TaskDecorator`，传播 `AiCallContextHolder.Snapshot`。

### 开关控制

```yaml
ai:
  evaluation:
    recording:
      enabled: false    # 默认关闭
```

---

## 七、阶段 5：聚合统计

### 机制

基于 Redis 计数（方案 B-1）：

```
initContext() 时：async_task_count=0, async_task_done=0

异步任务提交前：incrementAsyncTaskCount()

异步任务完成时：incrementAsyncTaskDone()
  → if (done == count) → triggerAggregation(traceId)
```

### 聚合 SQL

```sql
UPDATE ai_flow_execution fe
SET
    fe.node_count         = (SELECT COUNT(*) FROM ai_node_execution ne WHERE ne.trace_id = fe.trace_id),
    fe.model_call_count   = (SELECT COUNT(*) FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.total_input_tokens = (SELECT COALESCE(SUM(mc.input_tokens), 0) FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.total_output_tokens= (SELECT COALESCE(SUM(mc.output_tokens), 0) FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.total_token_count  = (SELECT COALESCE(SUM(mc.total_tokens), 0) FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.total_cost_cny     = (SELECT COALESCE(SUM(mc.cost_cny), 0) FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.updated_at         = NOW(3)
WHERE fe.trace_id = ?
```

---

## 八、运行时完整数据流

```
用户消息到达
│
▼ ConversationMessageProcessor.processConversationMessage(conversationId=123)
│
├── 【FlowExecutionAspect — 入口】
│   ├── trace_id = IdUtil.getSnowflake(23, 17).nextId() → 1893847362847362
│   ├── FlowExecutionContextManager.initContext(123, 1893847362847362)
│   │     Redis Hash: flow:ctx:123
│   │     ├── trace_id = 1893847362847362
│   │     ├── conversation_id = 123
│   │     ├── round_num = 0
│   │     ├── user_id = 0
│   │     ├── node_sequence = 0
│   │     ├── async_task_count = 0
│   │     └── async_task_done = 0
│   └── INSERT ai_flow_execution (trace_id, conversation_id=123, status=1, started_at)
│
├── 【主线程】令牌获取 ✅ → 缓存加载 ✅ → 有未处理消息 ✅
│   │
│   ├── DiagnosisGraph.executeGraph()
│   │   │
│   │   ├── InputNode.apply(OverAllState, RunnableConfig)
│   │   │   └── 【NodeExecutionAspect — aroundDiagnosisNode】
│   │   │       ├── conversationId = config.contextMap["conversation_id"] → 123
│   │   │       ├── trace_id = getTraceId(123) → 1893847362847362
│   │   │       ├── node_sequence = incrementNodeSequence(123) → 1
│   │   │       ├── input_summary = inputNode.inputSummary(state)
│   │   │       ├── INSERT ai_node_execution (trace_id, "inputNode", seq=1, status=1, ...)
│   │   │       ├── proceed → InputGraph.executeGraph()
│   │   │       │   ├── PreCleanNode → seq=2
│   │   │       │   │   └── 内部模型调用 → RecordingModelInterceptor
│   │   │       │   │       → INSERT ai_model_call (trace_id, node_id, system_prompt, user_prompt, ...)
│   │   │       │   ├── MessageStructuredProcessNode → seq=3 (并行)
│   │   │       │   ├── EmotionStatisticsNode → seq=4 (并行)
│   │   │       │   └── HistoryDiagnosisSummaryNode → seq=5 (并行)
│   │   │       ├── output_summary = inputNode.outputSummary(state)
│   │   │       └── UPDATE ai_node_execution (output_summary, duration_ms, status=2)
│   │   │
│   │   ├── KnowledgeNode → seq=6
│   │   │   └── KnowledgeGraph 内部:
│   │   │       QueryTransformLayerNode→seq=7
│   │   │       3个并行Repository→seq=8,9,10
│   │   │       RerankLayerNode→seq=11
│   │   │
│   │   └── ProcessNode → seq=12
│   │       └── ProcessGraph 内部:
│   │           6个并行诊断→seq=13~18
│   │           InterventionSuggestionNode→seq=19
│   │           DiagnosisSummaryNode→seq=20
│   │
│   └── DiagnosisPersistNode → seq=21
│
├── 【异步线程】diagnosisExecutor.execute(...)
│   ├── ConversationNameGenerationNode.apply(ConversationProcessContextBO)
│   │   └── 【NodeExecutionAspect — aroundConversationNode】
│   │       ├── conversationId = ctx.getConversation().getId() → 123
│   │       ├── trace_id = getTraceId(123) → 1893847362847362
│   │       ├── node_sequence = incrementNodeSequence(123) → 22
│   │       ├── INSERT → proceed → UPDATE
│   │
│   ├── EmotionRecognitionNode → seq=23
│   ├── HistoryMessageCompressionNode → seq=24
│   └── HistoryAnalysisCompressionNode → seq=25
│
├── 【FlowExecutionAspect — finally】
│   ├── 反查 Conversation → round_num=3, user_id=456
│   ├── FlowExecutionContextManager.updateRoundNumAndUserId(123, 3, 456)
│   ├── UPDATE ai_flow_execution SET status=2, finished_at, duration_ms, round_num=3, user_id=456
│   └── FlowExecutionContextManager.removeContext(123)
│
└── 【异步任务全部完成 → 聚合触发】
    ├── node_count = 25
    ├── model_call_count = 12
    ├── total_input_tokens = 45000
    ├── total_output_tokens = 8000
    ├── total_token_count = 53000
    ├── total_cost_cny = 0.035000
    └── UPDATE ai_flow_execution SET 聚合字段 WHERE trace_id = 1893847362847362
```

---

## 九、新增文件清单（10 个）

| # | 类名 | 包路径 | 阶段 |
|---|------|--------|:----:|
| 1 | `FlowExecutionConstant` | `org.lixiyun.server.constant` | 0 |
| 2 | `FlowExecutionContextManager` | `org.lixiyun.server.aop.context` | 0 |
| 3 | `FlowExecutionAspect` | `org.lixiyun.server.aop.aspect` | 1 |
| 4 | `NodeExecutionAspect` | `org.lixiyun.server.aop.aspect` | 2+3 |
| 5 | `RecordingModelInterceptor` | `org.lixiyun.server.ai.interceptor` | 4 |
| 6 | `AiCallContextHolder` | `org.lixiyun.server.ai.model.recording` | 4 |
| 7 | `AiModelCallRecordStorage` | `org.lixiyun.server.ai.model.recording` | 4 |
| 8 | `DatabaseAiModelCallRecordStorage` | `org.lixiyun.server.ai.model.recording` | 4 |
| 9 | `AiModelCallRecordMapper` | `org.lixiyun.server.ai.model.recording` | 4 |
| 10 | `recordingTaskExecutor` 配置 | 配置类 | 4 |

## 十、修改文件清单（5 个）

| # | 文件 | 修改内容 | 行数 |
|---|------|---------|:----:|
| 1 | `InputGraph.executeGraph()` | `.addContextMapEntry("conversation_id", ...)` | 1 |
| 2 | `KnowledgeGraph.executeGraph()` | 同上 | 1 |
| 3 | `ProcessGraph.executeGraph()` | 同上 | 1 |
| 4 | `BaseModel.java` | 注入 Interceptor + recordStorage；`reactAgentBuilder()` 注册；`doStream()` 叠加记录 | ~40 |
| 5 | `diagnosisThreadPoolTaskExecutor` 配置 | 加 `TaskDecorator` 传播 AiCallContextHolder | ~10 |

**业务代码总侵入：~53 行**

---

## 十一、异常处理统一原则

| 原则 | 说明 |
|------|------|
| 不吞异常 | catch 后原样 throw，保持原有异常传播链 |
| 不影响业务 | 日志记录的任何异常不能影响业务流程 |
| finally 保护 | finally 中的 UPDATE 操作自身 try-catch 保护 |
| 降级跳过 | `existsContext()` 返回 false 时跳过日志，仅执行原方法 |

---

## 十二、开关控制

```yaml
ai:
  evaluation:
    recording:
      enabled: false    # 默认关闭，按需开启
```

所有新增 Bean 均通过 `@ConditionalOnProperty` 控制，未启用时 `BaseModel` 中 `@Autowired(required = false)` 为 null，原有逻辑不受影响。