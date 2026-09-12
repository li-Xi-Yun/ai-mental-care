# AI Diagnosis 节点执行记录 — AOP 切面落地方案

## 一、节点清单与并行情况

### 1.1 InputGraph（3 并行 + 1 串行）

| 节点 | NODE_NAME | 并行/串行 |
|------|-----------|:---------:|
| `PreCleanNode` | `preCleanNode` | 串行（第一个） |
| `MessageStructuredProcessNode` | `messageStructuredProcessNode` | 并行 |
| `EmotionStatisticsNode` | `emotionStatisticsNode` | 并行 |
| `HistoryDiagnosisSummaryNode` | `historyDiagnosisSummaryNode` | 并行 |

### 1.2 KnowledgeGraph（2 串行 + 3 并行）

| 节点 | NODE_NAME | 并行/串行 |
|------|-----------|:---------:|
| `QueryTransformLayerNode` | `queryTransformLayerNode` | 串行 |
| `SymptomKnowledgeRepositoryNode` | `symptomKnowledgeRepositoryNode` | 并行 |
| `DiagnosisStandardRepositoryNode` | `diagnosisStandardRepositoryNode` | 并行 |
| `InterventionPlanRepositoryNode` | `interventionPlanRepositoryNode` | 并行 |
| `RerankLayerNode` | `rerankLayerNode` | 串行 |

### 1.3 ProcessGraph（6 并行 + 2 串行）

| 节点 | NODE_NAME | 并行/串行 |
|------|-----------|:---------:|
| `ComprehensiveDiagnosisNode` | `comprehensiveDiagnosisNode` | 并行 |
| `DiseaseCourseAttributionNode` | `diseaseCourseAttributionNode` | 并行 |
| `PsychologicalStateNode` | `psychologicalStateNode` | 并行 |
| `SocialFunctionImpactNode` | `socialFunctionImpactNode` | 并行 |
| `ProtectiveFactorNode` | `protectiveFactorNode` | 并行 |
| `RiskAssessmentNode` | `riskAssessmentNode` | 并行 |
| `InterventionSuggestionNode` | `interventionSuggestionNode` | 串行 |
| `DiagnosisSummaryNode` | `diagnosisSummaryNode` | 串行 |

### 1.4 子图入口节点

| 节点 | NODE_NAME | 所属 |
|------|-----------|------|
| `InputNode` | `inputNode` | DiagnosisGraph → InputGraph |
| `KnowledgeNode` | `knowledgeNode` | DiagnosisGraph → KnowledgeGraph |
| `ProcessNode` | `processNode` | DiagnosisGraph → ProcessGraph |

**总计：21 个节点**（18 个子图内部 + 3 个子图入口）

## 二、切点表达式

所有 21 个节点统一实现 `NodeActionWithConfig` + `NodeExecutionSummary`，方法签名完全一致：

```java
@Around(
    "execution(* org.lixiyun.server.ai.node.diagnosis..*.apply(com.alibaba.cloud.ai.graph.OverAllState, com.alibaba.cloud.ai.graph.RunnableConfig)) " +
    "&& target(org.lixiyun.server.ai.node.NodeExecutionSummary)"
)
public Object aroundDiagnosisNode(ProceedingJoinPoint pjp) throws Throwable {
    // ...
}
```

**含义**：匹配 diagnosis 包下所有类的 `apply(OverAllState, RunnableConfig)` 方法，且目标类实现了 `NodeExecutionSummary` 接口。

## 三、conversationId 获取方案

### 3.1 问题

diagnosis 节点的入参是 `OverAllState`，需要从中提取 `conversationId`。但三个子图的初始 stateMap 内容不同：

| 子图 | stateMap 内容 | 能否直接获取 conversationId |
|------|-------------|:-------------------------:|
| InputGraph | `ConversationProcessContextBO` + `InputResult` | ✅ 可直接获取 |
| KnowledgeGraph | `KnowledgeMatchRequest` + `KnowledgeRetrieveResult` | ❌ 无法获取 |
| ProcessGraph | `DiagnosisDataRequest` + `DiagnosisData` | ❌ 无法获取 |

### 3.2 方案：RunnableConfig 传递 conversationId

在 3 个 Graph 的 `executeGraph` 方法中，构建 `RunnableConfig` 时加入 `conversation_id`：

**InputGraph**：
```java
RunnableConfig runnableConfig = RunnableConfig.builder()
    .addParallelNodeExecutor(MessageStructuredProcessNode.NODE_NAME, ForkJoinPool.commonPool())
    .addParallelNodeExecutor(EmotionStatisticsNode.NODE_NAME, ForkJoinPool.commonPool())
    .addParallelNodeExecutor(HistoryDiagnosisSummaryNode.NODE_NAME, ForkJoinPool.commonPool())
    .addContextMapEntry("conversation_id", contextBO.getConversation().getId())  // ← 新增
    .build();
```

**KnowledgeGraph**：
```java
RunnableConfig runnableConfig = RunnableConfig.builder()
    .addParallelNodeExecutor(SymptomKnowledgeRepositoryNode.NODE_NAME, ForkJoinPool.commonPool())
    .addParallelNodeExecutor(DiagnosisStandardRepositoryNode.NODE_NAME, ForkJoinPool.commonPool())
    .addParallelNodeExecutor(InterventionPlanRepositoryNode.NODE_NAME, ForkJoinPool.commonPool())
    .addContextMapEntry("conversation_id", knowledgeMatchRequest.getConversationId())  // ← 新增
    .build();
```

**ProcessGraph**：
```java
RunnableConfig runnableConfig = RunnableConfig.builder()
    .addParallelNodeExecutor(ComprehensiveDiagnosisNode.NODE_NAME, ForkJoinPool.commonPool())
    // ... 其他并行节点
    .addContextMapEntry("conversation_id", diagnosisDataRequest.getConversationId())  // ← 新增
    .build();
```

**AOP 切面中获取**：
```java
RunnableConfig config = (RunnableConfig) pjp.getArgs()[1];
Long conversationId = (Long) config.getContextMap().get("conversation_id");
```

### 3.3 前提：KnowledgeMatchRequest 和 DiagnosisDataRequest 需要有 conversationId 字段

需确认这两个类是否已包含 `conversationId` 字段。如果没有，需要补充。

## 四、节点名称获取

通过反射读取 `NODE_NAME` 静态字段，与 conversation 节点方案一致：

```java
Class<?> nodeClass = pjp.getSignature().getDeclaringType();
String nodeName = (String) nodeClass.getDeclaredField("NODE_NAME").get(null);
```

## 五、input/output_summary 提取

所有 21 个节点已实现 `NodeExecutionSummary` 接口，直接调用：

```java
NodeExecutionSummary summaryNode = (NodeExecutionSummary) pjp.getTarget();

// 方法入口 — 提取输入摘要
Object inputSummary = summaryNode.inputSummary(state);

// ... proceed ...

// finally — 提取输出摘要
Object outputSummary = summaryNode.outputSummary(state);
```

**关键**：`outputSummary` 在 `finally` 中调用时，`state` 已包含节点执行后的最新数据（节点通过返回值 `Map<String, Object>` 更新了 state），所以 `outputSummary(state)` 能正确获取输出。

## 六、执行流程

```
@Around 拦截 diagnosis 节点的 apply(OverAllState, RunnableConfig)
│
├── 【方法入口 — INSERT 阶段】
│   ├── 从 RunnableConfig.contextMap 获取 conversationId
│   ├── FlowExecutionContextManager.existsContext(conversationId) → false 则跳过日志，直接 proceed
│   ├── trace_id = FlowExecutionContextManager.getTraceId(conversationId)
│   ├── node_name = 反射读取 NODE_NAME
│   ├── node_sequence = FlowExecutionContextManager.incrementNodeSequence(conversationId)
│   ├── started_at = Instant.now()
│   ├── input_summary = ((NodeExecutionSummary) target).inputSummary(state)
│   └── INSERT ai_node_execution
│         (trace_id, node_name, node_sequence, status=1, started_at, input_summary)
│
├── 【执行原方法】result = pjp.proceed()
│
└── 【finally — UPDATE 阶段】
    ├── finished_at = Instant.now()
    ├── duration_ms = 计算耗时
    ├── status = 有异常 ? 3(失败) : 2(完成)
    ├── output_summary = ((NodeExecutionSummary) target).outputSummary(state)
    ├── 有异常时记录 error_code、error_message
    └── UPDATE ai_node_execution SET
          status, finished_at, duration_ms, output_summary, error_code, error_message
          WHERE id = ?
```

## 七、并行节点的 node_sequence 处理

并行节点通过 `ForkJoinPool.commonPool()` 执行，多个节点可能同时调用 `FlowExecutionContextManager.incrementNodeSequence()`。

由于底层是 Redis `HINCRBY` 原子操作，**天然保证顺序号不重复、不丢失**。

并行节点的 `node_sequence` 不代表执行先后顺序，仅代表**提交顺序**，这是可接受的。

### 7.1 并行场景示例

```
ProcessGraph 执行时，6 个并行节点同时提交：

ComprehensiveDiagnosisNode  → incrementNodeSequence() → node_sequence = 5
RiskAssessmentNode          → incrementNodeSequence() → node_sequence = 6
PsychologicalStateNode      → incrementNodeSequence() → node_sequence = 7
SocialFunctionImpactNode    → incrementNodeSequence() → node_sequence = 8
ProtectiveFactorNode        → incrementNodeSequence() → node_sequence = 9
DiseaseCourseAttributionNode→ incrementNodeSequence() → node_sequence = 10

（前 4 个序号由 InputGraph/KnowledgeGraph 的节点占用）
```

具体哪个节点拿到哪个序号取决于 Redis HINCRBY 的执行时序，但每个序号唯一且连续。

## 八、与 conversation 节点 AOP 的合并

两个切面方法合并到 `NodeExecutionAspect` 中：

```
NodeExecutionAspect
├── 依赖注入
│   ├── FlowExecutionContextManager  — Redis 上下文操作封装
│   └── AiNodeExecutionMapper        — 持久化操作
│
├── aroundDiagnosisNode()
│   └── 切点: diagnosis 包下 *.apply(OverAllState, RunnableConfig) && target(NodeExecutionSummary)
│       ├── conversationId 从 RunnableConfig.contextMap 获取
│       ├── input/output_summary 通过 NodeExecutionSummary 接口获取
│       └── node_name 通过 NODE_NAME 反射获取
│
└── aroundConversationNode()
    └── 切点: conversation 包下 *.apply(..) 排除 OverAllState 参数
        ├── conversationId 从方法参数 instanceof 判断获取
        ├── input/output_summary 在切面中按类型提取
        └── node_name 通过 NODE_NAME 反射获取
```

## 九、异常处理

与主流程 AOP 相同原则：

```java
@Around(...)
public Object aroundDiagnosisNode(ProceedingJoinPoint pjp) throws Throwable {
    // ... INSERT ...
    Throwable caught = null;
    Object result = null;
    try {
        result = pjp.proceed();
        return result;
    } catch (Throwable t) {
        caught = t;
        throw t;
    } finally {
        try {
            // UPDATE ...
        } catch (Exception e) {
            log.error("节点执行记录UPDATE失败，node_name={}", nodeName, e);
        }
    }
}
```

**关键原则**：
- 日志记录异常**不影响业务流程**
- catch 后原样 throw，保持原有异常传播链
- finally 中 UPDATE 操作自身 try-catch 保护

## 十、降级策略

若 `FlowExecutionContextManager.existsContext(conversationId)` 返回 false（如流程入口 AOP 未执行或 Redis Key 已过期），切面跳过日志记录，仅执行原方法：

```java
if (!flowExecutionContextManager.existsContext(conversationId)) {
    return pjp.proceed();
}
```

## 十一、侵入性总结

| 侵入点 | 修改内容 | 行数 |
|--------|---------|------|
| `InputGraph.executeGraph()` | `RunnableConfig` 加 `.addContextMapEntry("conversation_id", ...)` | 1 行 |
| `KnowledgeGraph.executeGraph()` | 同上 | 1 行 |
| `ProcessGraph.executeGraph()` | 同上 | 1 行 |

**业务代码总侵入：3 行**（3 个 Graph 类各加 1 行），其余全部在 AOP 切面中完成。