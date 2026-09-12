# AI Conversation 节点执行记录 — AOP 切面落地方案

## 一、背景

`conversation` 包下的 4 个节点与 `diagnosis` 节点有本质区别：

| | diagnosis 节点 | conversation 节点 |
|---|---|---|
| 调用方式 | StateGraph 框架调用 `apply(OverAllState, RunnableConfig)` | 直接调用重载的 `apply(业务对象)` |
| 接口方法 | `apply(OverAllState, RunnableConfig)` 是实际入口 | `apply(OverAllState, RunnableConfig)` 返回空/null，从未被实际调用 |
| 入参类型 | 统一 `OverAllState` | 各不相同：`ConversationProcessContextBO` 或 `HistoryCompressionBO` |
| 返回值 | `Map<String, Object>` 写入 state | `String` 或 `EmotionRecognitionResult`，直接返回给调用方 |

## 二、节点清单与实际调用入口

| 节点 | NODE_NAME | 实际被调用的方法 | 返回类型 | 调用位置 |
|------|-----------|-----------------|---------|---------|
| `ConversationNameGenerationNode` | `conversationNameGenerationNode` | `apply(ConversationProcessContextBO)` | `String` | `ConversationMessageProcessor.generateConversationNameIfFirstRound()` |
| `EmotionRecognitionNode` | `emotionRecognitionNode` | `apply(ConversationProcessContextBO)` | `EmotionRecognitionResult` | `ConversationAiService.analysisAndDiagnosis()` |
| `HistoryMessageCompressionNode` | `historyMessageCompressionNode` | `apply(HistoryCompressionBO)` | `String` | `ConversationAiService.semanticCompression()` |
| `HistoryAnalysisCompressionNode` | `historyAnalysisCompressionNode` | `apply(HistoryCompressionBO)` | `String` | `ConversationAiService.semanticCompression()` |

## 三、切点表达式

4 个节点的业务方法都叫 `apply`，用包级通配符统一匹配，同时排除 `NodeActionWithConfig` 接口方法：

```java
@Around(
    "execution(* org.lixiyun.server.ai.node.conversation..*.apply(..)) " +
    "&& !execution(* org.lixiyun.server.ai.node.conversation..*.apply(com.alibaba.cloud.ai.graph.OverAllState, ..))"
)
public Object aroundConversationNode(ProceedingJoinPoint pjp) throws Throwable {
    // ...
}
```

匹配结果：

| 方法 | 是否匹配 |
|------|:--------:|
| `ConversationNameGenerationNode.apply(ConversationProcessContextBO)` | ✅ |
| `EmotionRecognitionNode.apply(ConversationProcessContextBO)` | ✅ |
| `HistoryMessageCompressionNode.apply(HistoryCompressionBO)` | ✅ |
| `HistoryAnalysisCompressionNode.apply(HistoryCompressionBO)` | ✅ |
| `*.apply(OverAllState, RunnableConfig)` | ❌ 排除 |

## 四、节点名称获取

每个节点类都有 `NODE_NAME` 静态字段，通过反射读取，保证与业务代码中全链路一致：

```java
Class<?> nodeClass = pjp.getSignature().getDeclaringType();
String nodeName = (String) nodeClass.getDeclaredField("NODE_NAME").get(null);
```

对比：

| | `getSimpleName()` | `NODE_NAME` |
|---|---|---|
| 结果 | `HistoryMessageCompressionNode` | `historyMessageCompressionNode` |
| 风格 | PascalCase（类名风格） | camelCase（业务标识风格） |
| 一致性 | 与代码中其他标识不一致 | 与 `AiNodeConfigManager.getConfig(NODE_NAME)` 等全链路一致 |

## 五、上下文获取方式（FlowExecutionContextManager）

所有流程上下文字段（`trace_id`、`conversation_id`、`round_num`、`user_id`、`node_sequence`）统一通过 `FlowExecutionContextManager` 获取，底层存储为 Redis Hash，key 为 `flow:ctx:{conversation_id}`，常量定义在 `FlowExecutionConstant` 中。

### 5.1 conversation_id 的获取

从方法参数中提取 `conversationId`，作为 `FlowExecutionContextManager` 各方法的入参：

```java
Object arg = pjp.getArgs()[0];
Long conversationId = null;
if (arg instanceof ConversationProcessContextBO ctx) {
    conversationId = ctx.getConversation().getId();
} else if (arg instanceof HistoryCompressionBO bo) {
    conversationId = bo.getConversation().getId();
}
```

### 5.2 trace_id 与 node_sequence 的获取

```java
// 获取 trace_id
Long traceId = flowExecutionContextManager.getTraceId(conversationId);

// 获取并原子递增 node_sequence
Long nodeSequence = flowExecutionContextManager.incrementNodeSequence(conversationId);
```

### 5.3 跨线程无需传递

所有 4 个节点都在 `diagnosisExecutor` 异步线程中执行，`FlowExecutionContextManager` 底层基于 Redis Hash 按 `conversation_id` 索引，天然跨线程，无需任何 ThreadLocal 手动传递。

## 六、执行流程

```
@Around 拦截 conversation 节点的 apply(业务对象)
│
├── 【方法入口 — INSERT 阶段】
│   ├── 从方法参数提取 conversationId
│   ├── FlowExecutionContextManager.existsContext(conversationId) → false 则跳过日志，直接 proceed
│   ├── trace_id = FlowExecutionContextManager.getTraceId(conversationId)
│   ├── node_name = 反射读取 NODE_NAME
│   ├── node_sequence = FlowExecutionContextManager.incrementNodeSequence(conversationId)
│   ├── started_at = Instant.now()
│   ├── input_summary = 从方法参数提取（按参数类型 instanceof 判断）
│   └── INSERT ai_node_execution
│         (trace_id, node_name, node_sequence, status=1, started_at, input_summary)
│
├── 【执行原方法】result = pjp.proceed()
│
└── 【finally — UPDATE 阶段】
    ├── finished_at = Instant.now()
    ├── duration_ms = 计算耗时
    ├── status = 有异常 ? 3(失败) : 2(完成)
    ├── output_summary = 从返回值提取（按返回类型判断）
    ├── 有异常时记录 error_code、error_message
    └── UPDATE ai_node_execution SET
          status, finished_at, duration_ms, output_summary, error_code, error_message
          WHERE id = ?
```

## 七、input_summary 提取策略

这些节点没有 `NodeExecutionSummary` 接口，入参也不是 `OverAllState`，在切面中按参数类型直接提取：

```java
Object arg = pjp.getArgs()[0];
if (arg instanceof ConversationProcessContextBO ctx) {
    // 提取 conversationId、currentRound、temporaryMessages 摘要
} else if (arg instanceof HistoryCompressionBO bo) {
    // 提取 conversation.id、historyMessages.size()、emotionAnalyses.size()
}
```

| 参数类型 | 提取内容 |
|---------|---------|
| `ConversationProcessContextBO` | `conversation.id`、`conversation.currentRound`、`temporaryMessages` 内容摘要 |
| `HistoryCompressionBO` | `conversation.id`、`historyMessages.size()`、`emotionAnalyses.size()` |

## 八、output_summary 提取策略

返回值直接从 `pjp.proceed()` 获取，按类型处理：

| 节点 | 返回类型 | output_summary |
|------|---------|---------------|
| `ConversationNameGenerationNode` | `String` | 生成的会话名称文本 |
| `EmotionRecognitionNode` | `EmotionRecognitionResult` | 整个结果对象（含 emotionLabel、confidence、intensity 等） |
| `HistoryMessageCompressionNode` | `String` | 压缩后的摘要文本 |
| `HistoryAnalysisCompressionNode` | `String` | 压缩后的摘要文本 |

## 九、与 diagnosis 节点 AOP 的合并

建议合并为一个 `NodeExecutionAspect` 类，统一管理所有节点的拦截逻辑：

```
NodeExecutionAspect
├── 依赖注入
│   ├── FlowExecutionContextManager  — Redis 上下文操作封装
│   └── AiNodeExecutionMapper        — 持久化操作
│
├── aroundDiagnosisNode()      — 切点: NodeActionWithConfig.apply(OverAllState, RunnableConfig)
│                                 且目标类实现 NodeExecutionSummary
│                                 从 OverAllState 中获取 conversationId
│                                 通过 FlowExecutionContextManager 获取 trace_id、node_sequence
│
└── aroundConversationNode()   — 切点: conversation 包下 *.apply(..) 排除 OverAllState 参数
                                  从方法参数中获取 conversationId
                                  通过 FlowExecutionContextManager 获取 trace_id、node_sequence
```

两个切面方法均通过 `FlowExecutionContextManager` 获取上下文字段，不直接操作 `StringRedisTemplate`。

## 十、调用线程与上下文传递

| 节点 | 调用线程 | 上下文获取方式 |
|------|---------|---------------|
| `ConversationNameGenerationNode` | `diagnosisExecutor` 异步线程 | `FlowExecutionContextManager`，按 conversation_id 索引 |
| `EmotionRecognitionNode` | `diagnosisExecutor` 异步线程 | 同上 |
| `HistoryMessageCompressionNode` | `diagnosisExecutor` 异步线程 | 同上 |
| `HistoryAnalysisCompressionNode` | `diagnosisExecutor` 异步线程 | 同上 |

所有 4 个节点都在 `diagnosisExecutor` 异步线程中执行，**无需任何 ThreadLocal 手动传递**，`FlowExecutionContextManager` 底层 Redis 天然跨线程。

## 十一、注意事项

1. **`EmotionRecognitionNode` 有副作用**：除了返回结果，还会写 DB（`emotionAnalysisMapper.insert`）和更新 Redis 缓存。这些副作用不属于 output_summary 范畴，output_summary 只记录返回给调用方的数据。

2. **`HistoryMessageCompressionNode` 和 `HistoryAnalysisCompressionNode` 顺序调用**：在 `ConversationAiService.semanticCompression()` 中依次调用，各自产生独立的 `ai_node_execution` 记录，`node_sequence` 通过 `FlowExecutionContextManager.incrementNodeSequence()` 原子递增保证顺序。

3. **异常处理**：与主流程 AOP 相同原则——日志记录异常不影响业务流程，catch 后原样 throw，finally 中 UPDATE 操作自身 try-catch 保护。

4. **Redis Hash 不存在时的降级**：通过 `FlowExecutionContextManager.existsContext(conversationId)` 判断，若返回 false（如流程入口 AOP 未执行或 Redis Key 已过期），切面跳过日志记录，仅执行原方法，避免因日志机制缺失影响业务流程。