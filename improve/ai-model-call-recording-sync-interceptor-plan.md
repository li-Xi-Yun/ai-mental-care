# AI 模型调用记录 — 同步调用拦截器落地方案

> 覆盖所有通过 `BaseModel.doCall()` / `BaseModel.doCallForResult()` 发起的同步模型调用，
> 基于 `RecordingModelInterceptor`（继承 `BaseModelInterceptor`）在 Interceptor 层统一记录输入与输出。
>
> **核心原则：记录逻辑收敛在 Interceptor 中，业务代码零改动。**

---

## 一、适用范围

### 1.1 覆盖的调用路径

所有同步模型调用最终收敛到 `BaseModel` 的两个模板方法：

| 模板方法 | 返回类型 | 典型调用方 |
|---------|---------|-----------|
| `doCall(ChatModel, String, AiNodeConfig)` | `AssistantMessage` | `HistoryMessageCompressionModel`、`HistoryAnalysisCompressionModel`、`ConversationNameGenerationModel` |
| `doCallForResult(ChatModel, String, AiNodeConfig)` | 泛型 T | `EmotionRecognitionModel`、诊断图各 ProcessModel |

### 1.2 不覆盖的调用路径

| 路径 | 原因 | 对应方案 |
|------|------|---------|
| `BaseModel.doStream()` | 流式调用无法在 Interceptor 中获取完整输出 | 见 `ai-model-call-recording-stream-interceptor-plan.md` |

### 1.3 调用链路

```
ConversationMessageProcessor.processConversationMessage(conversationId)
│
├── 【主线程】executeMainThread()
│     └── TextMessageProcessor.processMessage()
│           └── TextMessageProcessorModel.stream()     ← 流式，不在此方案覆盖
│
├── 【异步线程】analysisAndDiagnosis()
│     ├── EmotionRecognitionNode.apply()
│     │     └── EmotionRecognitionModel.callForResult()
│     │           └── BaseModel.doCallForResult()
│     │                 └── ReactAgent.call()
│     │                       └── RecordingModelInterceptor.interceptBaseModel()  ← 本方案
│     │
│     └── DiagnosisGraph.executeGraph()
│           └── 各 Diagnosis 节点.apply()
│                 └── 各 Model 子类.callForResult()
│                       └── BaseModel.doCallForResult()
│                             └── ReactAgent.call()
│                                   └── RecordingModelInterceptor.interceptBaseModel()  ← 本方案
│
├── 【异步线程】checkAndTriggerSemanticCompression()
│     ├── HistoryMessageCompressionNode.apply()
│     │     └── HistoryMessageCompressionModel.call()
│     │           └── BaseModel.doCall()
│     │                 └── ReactAgent.call()
│     │                       └── RecordingModelInterceptor.interceptBaseModel()  ← 本方案
│     │
│     └── HistoryAnalysisCompressionNode.apply()
│           └── HistoryAnalysisCompressionModel.call()
│                 └── BaseModel.doCall()
│                       └── ReactAgent.call()
│                             └── RecordingModelInterceptor.interceptBaseModel()  ← 本方案
│
└── 【异步线程】generateConversationNameIfFirstRound()
      └── ConversationNameGenerationNode.apply()
            └── ConversationNameGenerationModel.call()
                  └── BaseModel.doCall()
                        └── ReactAgent.call()
                              └── RecordingModelInterceptor.interceptBaseModel()  ← 本方案
```

---

## 二、前置条件与约束

| 条件 | 说明 |
|------|------|
| spring-ai-alibaba 版本 | 1.1.2.0 |
| `ModelInterceptor` | ✅ 可用，项目已有 `BaseModelInterceptor` / `MessageModelInterceptor` |
| `BaseModelInterceptor` | ✅ 抽象基类，定义 `interceptBaseModel()` 模板方法 |
| `BaseModel.reactAgentBuilder()` | 当前未注册任何 Interceptor，需改造注入 |
| `AiModelCall` 实体 | ✅ 已创建，对应 `ai_model_call` 表 |
| `AiModelCallMapper` | ✅ 已创建 |
| `FlowExecutionContextManager` | ✅ 已创建，基于 Redis Hash 管理流程上下文 |

---

## 三、详细设计

### 3.1 RecordingModelInterceptor

继承项目已有的 `BaseModelInterceptor`，在 `interceptBaseModel` 中完成同步调用的完整记录。

```java
@Slf4j
@Component(RecordingModelInterceptor.NAME)
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class RecordingModelInterceptor extends BaseModelInterceptor {

    public static final String NAME = "RecordingModelInterceptor";

    private final AiModelCallRecordStorage recordStorage;

    public RecordingModelInterceptor(AiModelCallRecordStorage recordStorage) {
        this.recordStorage = recordStorage;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ModelResponse interceptBaseModel(ModelRequest request, ModelCallHandler handler) {
        // ========== 1. 构建记录 ==========
        AiModelCall record = new AiModelCall();
        record.setStartedAt(LocalDateTime.now());
        record.setCallType(AiModelCall.CALL_TYPE_SYNC);

        // 从 messages 中提取 systemPrompt 和 userPrompt
        extractPrompts(request, record);

        // 从 context 中提取 ConversationMetadata（通过 RunnableConfig.metadata 传入）
        ConversationMetadata conversationMetadata = (ConversationMetadata) request.getContext().get(ConversationMetadata.NAME);
        if (conversationMetadata != null) {
            record.setConversationId(conversationMetadata.getConversationId());
            record.setUserId(conversationMetadata.getUserId());
            record.setRoundNum(conversationMetadata.getCurrentRound());
        }

        // 从 options 中提取模型名称
        record.setModelName(
            request.getOptions() != null ? request.getOptions().getModel() : "unknown"
        );

        // 从 FlowExecutionContextManager 获取 traceId
        if (conversationMetadata != null && FlowExecutionContextManager.existsContext(conversationMetadata.getConversationId())) {
            record.setTraceId(FlowExecutionContextManager.getTraceId(conversationMetadata.getConversationId()));
        }

        // ========== 2. 执行调用 ==========
        long start = System.currentTimeMillis();
        ModelResponse response;
        Throwable caughtException = null;
        try {
            response = handler.call(request);
        } catch (Throwable t) {
            caughtException = t;
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            record.setDurationMs(durationMs);
            record.setFinishedAt(LocalDateTime.now());

            // ========== 3. 记录输出 ==========
            if (caughtException == null && response != null && response.getResult() != null) {
                ChatResponse chatResponse = response.getResult();
                if (chatResponse.getResult() != null
                        && chatResponse.getResult().getOutput() != null) {
                    record.setModelOutput(chatResponse.getResult().getOutput().getText());
                }
                record.setStatus(AiModelCall.STATUS_COMPLETED);

                // 提取 token 使用量
                extractUsage(chatResponse, record);
            } else {
                record.setStatus(AiModelCall.STATUS_FAILED);
                if (caughtException != null) {
                    record.setErrorCode(caughtException.getClass().getSimpleName());
                    String msg = caughtException.getMessage();
                    record.setErrorMessage(msg != null && msg.length() > 2000
                            ? msg.substring(0, 2000) : msg);
                }
            }

            // ========== 4. 异步持久化 ==========
            try {
                recordStorage.saveAsync(record);
            } catch (Exception e) {
                log.error("同步调用记录持久化失败", e);
            }
        }

        return response;
    }

    private void extractPrompts(ModelRequest request, AiModelCall record) {
        List<Message> messages = request.getMessages();
        String systemPrompt = null;
        String userPrompt = null;
        for (Message msg : messages) {
            if (msg.getMessageType() == MessageType.SYSTEM) {
                systemPrompt = msg.getText();
            } else if (msg.getMessageType() == MessageType.USER) {
                userPrompt = msg.getText();
            }
        }
        record.setSystemPrompt(systemPrompt);
        record.setUserPrompt(userPrompt);
    }

    private void extractUsage(ChatResponse chatResponse, AiModelCall record) {
        if (chatResponse.getMetadata() != null
                && chatResponse.getMetadata().getUsage() != null) {
            var usage = chatResponse.getMetadata().getUsage();
            record.setInputTokens(usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null);
            record.setOutputTokens(usage.getGenerationTokens() != null ? usage.getGenerationTokens().longValue() : null);
            record.setTotalTokens(usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null);
        }
    }
}
```

### 3.2 BaseModel 改造：reactAgentBuilder() 注入 Interceptor

```java
// BaseModel.java 新增字段
@Autowired(required = false)
private RecordingModelInterceptor recordingModelInterceptor;

// reactAgentBuilder() 改造
public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder(ChatModel chatModel, AiNodeConfig config) {
    if (chatModel == null) {
        throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
    }
    com.alibaba.cloud.ai.graph.agent.Builder builder = ReactAgent.builder()
            .model(chatModel)
            .name(getAgentName())
            .description(getAgentDescription())
            .chatOptions(chatOptions(chatModel, config))
            .enableLogging(false);

    // 注入记录拦截器（通过配置开关控制是否生效）
    if (recordingModelInterceptor != null) {
        builder.interceptors(recordingModelInterceptor);
    }

    return builder;
}
```

### 3.3 上下文传播

业务上下文（conversationId、userId、currentRound）通过 `RunnableConfig.metadata()` 传入框架，
由 `ConversationMetadata` 承载，在 Interceptor 中通过 `request.getContext()` 获取。

详见 **第十三章：ConversationMetadata 上下文传播方案**。

---

## 四、存储层

### 4.1 接口

```java
public interface AiModelCallRecordStorage {
    /**
     * 异步保存记录，返回记录ID
     */
    Long saveAsync(AiModelCall record);

    /**
     * 异步更新流式调用的输出内容
     */
    void updateOutputAsync(Long recordId, String modelOutput, String thinkOutput);
}
```

### 4.2 数据库实现

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class DatabaseAiModelCallRecordStorage implements AiModelCallRecordStorage {

    private final AiModelCallMapper mapper;

    @Async("recordingTaskExecutor")
    @Override
    public Long saveAsync(AiModelCall record) {
        mapper.insert(record);
        return record.getId();
    }

    @Async("recordingTaskExecutor")
    @Override
    public void updateOutputAsync(Long recordId, String modelOutput, String thinkOutput) {
        LambdaUpdateWrapper<AiModelCall> wrapper = new LambdaUpdateWrapper<AiModelCall>()
                .eq(AiModelCall::getId, recordId)
                .set(AiModelCall::getModelOutput, modelOutput)
                .set(AiModelCall::getStatus, AiModelCall.STATUS_COMPLETED);
        mapper.update(null, wrapper);
    }
}
```

### 4.3 异步线程池配置

```java
@Bean("recordingTaskExecutor")
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public ThreadPoolTaskExecutor recordingTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("recording-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

---

## 五、数据模型

### 5.1 已有实体：AiModelCall

实体类和 Mapper 已在阶段 0 创建，字段与 `ai_model_call` 表一一对应：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT AUTO | 主键 |
| `traceId` | BIGINT | 流程唯一标识，关联 `ai_flow_execution.trace_id` |
| `nodeId` | BIGINT | 关联的节点执行记录 ID |
| `callType` | INT | 1=同步 2=流式 |
| `modelProvider` | VARCHAR(32) | 模型供应商 |
| `modelName` | VARCHAR(128) | 模型名称 |
| `systemPrompt` | TEXT | 系统提示词 |
| `userPrompt` | TEXT | 用户提示词 |
| `modelParams` | JSON | 推理参数 |
| `status` | INT | 1=完成 2=失败 |
| `errorCode` | VARCHAR(64) | 错误码 |
| `errorMessage` | TEXT | 错误信息 |
| `modelOutput` | TEXT | 模型输出 |
| `inputTokens` | BIGINT | 输入 token |
| `outputTokens` | BIGINT | 输出 token |
| `totalTokens` | BIGINT | 总 token |
| `costCny` | DECIMAL(10,6) | 调用成本 |
| `startedAt` | DATETIME(3) | 开始时间 |
| `finishedAt` | DATETIME(3) | 结束时间 |
| `durationMs` | BIGINT | 耗时(ms) |
| `toolCallCount` | INT | 工具调用次数 |
| `finishReason` | VARCHAR(32) | 结束原因 |

---

## 六、开关控制

```yaml
ai:
  evaluation:
    recording:
      enabled: false    # 默认关闭，按需开启
```

所有新增 Bean 均通过 `@ConditionalOnProperty` 控制：
- `RecordingModelInterceptor`
- `DatabaseAiModelCallRecordStorage`
- `recordingTaskExecutor`

`BaseModel` 中通过 `@Autowired(required = false)` 注入，
未启用时为 null，`reactAgentBuilder()` 不注册 Interceptor，原有逻辑不受影响。

---

## 七、执行流程

```
同步模型调用（以情绪识别为例）
│
├── 【Interceptor 入口】RecordingModelInterceptor.interceptBaseModel()
│   ├── 构建 AiModelCall 记录
│   │     ├── callType = 1 (CALL_TYPE_SYNC)
│   │     ├── startedAt = LocalDateTime.now()
│   │     ├── systemPrompt = 从 request.getMessages() 提取
│   │     ├── userPrompt = 从 request.getMessages() 提取
│   │     ├── conversationId / userId / roundNum = 从 request.getContext() 获取 ConversationMetadata
│   │     ├── modelName = 从 request.getOptions().getModel() 获取
│   │     └── traceId = FlowExecutionContextManager.getTraceId(conversationId)
│   │
│   ├── handler.call(request)  → 模型执行
│   │
│   └── 【finally — 记录输出】
│       ├── durationMs = 计算
│       ├── finishedAt = LocalDateTime.now()
│       ├── modelOutput = response.getResult().getOutput().getText()
│       ├── inputTokens / outputTokens / totalTokens = 从 usage 提取
│       ├── status = 有异常 ? 2(FAILED) : 1(COMPLETED)
│       └── recordStorage.saveAsync(record)
│             → INSERT ai_model_call（一条完整记录，输入+输出全部写入）
│
└── 数据库结果：1 条记录，所有字段完整
```

---

## 八、新增文件清单（2 个）

| # | 类名 | 包路径 | 说明 |
|---|------|--------|------|
| 1 | `RecordingModelInterceptor` | `org.lixiyun.server.ai.interceptor.ModelInterceptor` | 同步调用记录拦截器 |
| 2 | `AiModelCallRecordStorage` + `DatabaseAiModelCallRecordStorage` | `org.lixiyun.server.ai.model.recording` | 存储接口 + 数据库实现 |

## 九、需修改文件清单（1 个）

| # | 文件 | 修改内容 | 改动量 |
|---|------|---------|:------:|
| 1 | `BaseModel.java` | 注入 `RecordingModelInterceptor`；`reactAgentBuilder()` 注册 Interceptor | ~8 行 |

## 十、需新增配置

| # | 内容 | 位置 |
|---|------|------|
| 1 | `ai.evaluation.recording.enabled` | `application.yml` |
| 2 | `recordingTaskExecutor` Bean | 配置类 |

---

## 十一、与现有 Interceptor 的共存

### 11.1 现有 Interceptor 体系

| Interceptor | 职责 | 注册位置 |
|-------------|------|---------|
| `MessageModelInterceptor` | 透传，仅调用 `handler.call(request)` | `OllamaModelConfig` 中的货运客服 Agent |

### 11.2 共存策略

- `RecordingModelInterceptor` 独立注册到会话处理流程的 Agent 中
- 不影响 `MessageModelInterceptor` 的现有功能
- 两者操作不同 Agent 实例，互不干扰
- `BaseModelInterceptor.interceptModel()` 中的耗时日志逻辑依然生效（`RecordingModelInterceptor` 继承后自动拥有）

---

## 十二、风险与应对

| 风险 | 应对 |
|------|------|
| 高频写入影响数据库性能 | 异步写入 + 独立线程池 + `CallerRunsPolicy` 兜底 |
| Interceptor 异常影响业务 | finally 中持久化操作自身 try-catch 保护，异常不抛出 |
| 生产环境误开启导致数据膨胀 | 默认 `enabled=false`，可加定期清理策略 |
| 敏感信息记录 | 评估脱敏需求，可配置保留周期 |
| `request.getMessages()` 中 System Prompt 可能被框架合并 | `extractPrompts()` 同时遍历 messages 和尝试 `request.getSystemMessage()`，双保险 |
| Interceptor 与现有 MessageModelInterceptor 共存 | 独立注册，操作不同 Agent 实例，互不干扰 |

---

## 十三、ConversationMetadata 上下文传播方案

> 解决 `ModelRequest.getContext()` 中无法获取 `ConversationMetadata`（conversationId、userId、currentRound）的问题，
> 使 Interceptor 层能完整记录每次模型调用的业务上下文。

### 13.1 问题根因

当前 `BaseModel.doCall()` 调用 `ReactAgent.call(userPrompt)` 时，使用的是**不带 `RunnableConfig` 的重载**：

```java
// BaseModel.java 当前代码
protected AssistantMessage doCall(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
    return buildAgent(chatModel, config).call(userPrompt);  // ← 无 RunnableConfig
}
```

框架内部 `AgentLlmNode` 构建 `ModelRequest` 时，`context` 来源于 `config.metadata()`：

```java
// AgentLlmNode.java（框架源码）
ModelRequest.Builder requestBuilder = ModelRequest.builder()
    .messages(messages)
    .options(this.chatOptions != null ? this.chatOptions.copy() : null)
    .context(config.metadata().orElse(new HashMap<>()));  // ← context = config.metadata()
```

而 `buildNonStreamConfig(RunnableConfig config)` 的逻辑为：

```java
// Agent.java（框架源码）
RunnableConfig.Builder builder = config == null
    ? RunnableConfig.builder()                          // ← 无传入config，metadata为空
    : RunnableConfig.builder(config);                   // ← 有传入config，保留其metadata
builder.addMetadata("_stream_", false).addMetadata("_AGENT_", name);
```

**结论：不传 `RunnableConfig` → metadata 为空 → `ModelRequest.context` 中只有框架自动注入的 `_AGENT_` 和 `_stream_`，`ConversationMetadata` 完全缺失。**

### 13.2 ModelRequest.context 数据来源全景

`ModelRequest.getContext()` 返回 `Map<String, Object>`，其内容 = `OverAllState.data()` + `RunnableConfig.metadata()` 的合并结果：

| Key | 类型 | 来源 | 说明 |
|-----|------|------|------|
| `_AGENT_` | `String` | 框架自动注入 | 当前 Agent 名称（如 `"historyMessageCompression"`） |
| `_stream_` | `Boolean` | 框架自动注入 | 是否为流式调用 |
| `conversationMetadata` | `ConversationMetadata` | 需通过 `RunnableConfig.metadata()` 传入 | 会话元数据（conversationId、userId、currentRound） |

### 13.3 改造方案

#### 13.3.1 第一步：父图构建 RunnableConfig 时注入 ConversationMetadata

**ProcessGraph.java** — 当前 `executeGraph()` 构建 `RunnableConfig` 时未放入 metadata：

```java
// 改造前
RunnableConfig runnableConfig = RunnableConfig.builder()
        .addParallelNodeExecutor(ComprehensiveDiagnosisNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(DiseaseCourseAttributionNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(PsychologicalStateNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(SocialFunctionImpactNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(ProtectiveFactorNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(RiskAssessmentNode.NODE_NAME, ForkJoinPool.commonPool())
        .build();

// 改造后
RunnableConfig runnableConfig = RunnableConfig.builder()
        .addParallelNodeExecutor(ComprehensiveDiagnosisNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(DiseaseCourseAttributionNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(PsychologicalStateNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(SocialFunctionImpactNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(ProtectiveFactorNode.NODE_NAME, ForkJoinPool.commonPool())
        .addParallelNodeExecutor(RiskAssessmentNode.NODE_NAME, ForkJoinPool.commonPool())
        .addMetadata(ConversationMetadata.NAME, metadata)   // ← 注入 ConversationMetadata
        .build();
```

**DiagnosisGraph.java** — 当前 `executeGraph()` 未传 `RunnableConfig`，需补充构建：

```java
// 改造前
OverAllState result = diagnosisGraph.invoke(stateMap).orElse(null);

// 改造后
RunnableConfig runnableConfig = RunnableConfig.builder()
        .addMetadata(ConversationMetadata.NAME, metadata)   // ← 注入 ConversationMetadata
        .build();
OverAllState result = diagnosisGraph.invoke(stateMap, runnableConfig).orElse(null);
```

**其他图** — 同理改造 `InputGraph`、`KnowledgeGraph` 等的 `executeGraph()` 方法。

#### 13.3.2 第二步：BaseModel 支持传入 RunnableConfig

改造 `BaseModel.java` 的 `doCall()`、`doStream()`、`doCallForResult()`，增加 `RunnableConfig` 参数：

```java
// ==================== 改造前 ====================

protected AssistantMessage doCall(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
    return buildAgent(chatModel, config).call(userPrompt);
}

protected Flux<NodeOutput> doStream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
    return buildAgent(chatModel, config).stream(userPrompt);
}

protected <T> T doCallForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
    AssistantMessage message = doCall(chatModel, userPrompt, config);
    return (T) deserializeResult(message);
}

// ==================== 改造后 ====================

protected AssistantMessage doCall(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
    ReactAgent agent = buildAgent(chatModel, config);
    if (runnableConfig != null) {
        return agent.call(userPrompt, runnableConfig);   // ← 带config调用，metadata流入context
    }
    return agent.call(userPrompt);
}

protected Flux<NodeOutput> doStream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
    ReactAgent agent = buildAgent(chatModel, config);
    if (runnableConfig != null) {
        return agent.stream(userPrompt, runnableConfig);  // ← 同理
    }
    return agent.stream(userPrompt);
}

protected <T> T doCallForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
    AssistantMessage message = doCall(chatModel, userPrompt, config, runnableConfig);
    return (T) deserializeResult(message);
}
```

> **向后兼容**：保留旧签名方法，默认传 null，避免一次性改动所有调用方：
> ```java
> protected AssistantMessage doCall(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
>     return doCall(chatModel, userPrompt, config, null);
> }
> protected Flux<NodeOutput> doStream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
>     return doStream(chatModel, userPrompt, config, null);
> }
> protected <T> T doCallForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
>     return doCallForResult(chatModel, userPrompt, config, null);
> }
> ```

#### 13.3.3 第三步：各 Node 传递 RunnableConfig 给模型调用

每个 Node 的 `apply(OverAllState state, RunnableConfig config)` 方法已持有 `RunnableConfig`，只需将其传递给模型调用：

**ComprehensiveDiagnosisNode.java** — 示例：

```java
// 改造前
ComprehensiveDiagnosisProcessModel.EmotionComprehensiveResult result =
    comprehensiveDiagnosisProcessModel.callForResult(chatModel, userPrompt, aiNodeConfig);

// 改造后
ComprehensiveDiagnosisProcessModel.EmotionComprehensiveResult result =
    comprehensiveDiagnosisProcessModel.callForResult(chatModel, userPrompt, aiNodeConfig, config);
```

**需改造的 Node 清单**：

| Node | 调用的模型方法 | 改动 |
|------|---------------|------|
| `ComprehensiveDiagnosisNode` | `callForResult(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `DiseaseCourseAttributionNode` | `callForResult(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `PsychologicalStateNode` | `callForResult(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `SocialFunctionImpactNode` | `callForResult(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `ProtectiveFactorNode` | `callForResult(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `RiskAssessmentNode` | `callForResult(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `InterventionSuggestionNode` | `callForResult(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `DiagnosisSummaryNode` | `callForResult(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `HistoryMessageCompressionNode` | `call(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `HistoryAnalysisCompressionNode` | `call(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |
| `ConversationNameGenerationNode` | `call(chatModel, prompt, aiNodeConfig)` | 追加 `config` 参数 |

> **注意**：`HistoryMessageCompressionNode` 的 `apply(HistoryCompressionBO)` 重载不持有 `RunnableConfig`，
> 需要在其调用处（`HistoryMessageCompressionGraph`）构建并传入。

#### 13.3.4 第四步：各 Model 子类适配新签名

每个 Model 子类的 `call()` / `callForResult()` / `stream()` 方法需追加 `RunnableConfig` 参数，委托给 `BaseModel` 的新签名方法：

```java
// HistoryMessageCompressionModel.java 改造示例

// 改造前
@Retryable(...)
public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
    return doCall(chatModel, userPrompt, config);
}

// 改造后
@Retryable(...)
public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
    return doCall(chatModel, userPrompt, config, runnableConfig);
}

// 保留旧签名向后兼容
public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
    return call(chatModel, userPrompt, config, null);
}
```

#### 13.3.5 第五步：BaseModelInterceptor 中正确读取 ConversationMetadata

```java
// BaseModelInterceptor.java 改造后
if (duration > 5000) {
    ConversationMetadata metadata = (ConversationMetadata) request.getContext().get(ConversationMetadata.NAME);
    String modelName = request.getOptions() != null ? request.getOptions().getModel() : "unknown";
    if (metadata != null) {
        log.warn("会话[{}] 用户[{}] 轮次[{}] 模型[{}] 响应时间过长({}ms)，请检查代码",
                metadata.getConversationId(), metadata.getUserId(),
                metadata.getCurrentRound(), modelName, duration);
    } else {
        log.warn("模型[{}] 响应时间过长({}ms)，请检查代码", modelName, duration);
    }
}
```

### 13.4 完整数据流向

```
ProcessGraph.executeGraph(diagnosisDataRequest, metadata)
  │
  ├─ ① RunnableConfig.builder()
  │      .addMetadata(ConversationMetadata.NAME, metadata)    // 放入metadata
  │      .build()
  │
  └─ processGraph.invoke(stateMap, runnableConfig)
       │
       └─ ComprehensiveDiagnosisNode.apply(state, config)     // config 携带 metadata
            │
            └─ ② model.callForResult(chatModel, prompt, aiNodeConfig, config)   // 传递config
                 │
                 └─ BaseModel.doCall(chatModel, prompt, config, runnableConfig)
                      │
                      └─ ③ agent.call(userPrompt, runnableConfig)   // 带config调用
                           │
                           └─ ④ 框架内部: buildNonStreamConfig(runnableConfig)
                                // 保留传入的metadata + 追加 _AGENT_, _stream_
                                │
                                └─ AgentLlmNode.apply(state, config)
                                     │
                                     └─ ⑤ ModelRequest.builder()
                                          .context(config.metadata())   // metadata → context
                                          .build()
                                          │
                                          └─ ⑥ BaseModelInterceptor.interceptModel(request, handler)
                                               │
                                               └─ request.getContext().get(ConversationMetadata.NAME)
                                                  // ✅ 成功获取 ConversationMetadata！
```

### 13.5 改造文件清单

| # | 文件 | 改造内容 | 改动量 |
|---|------|---------|:------:|
| 1 | `BaseModel.java` | `doCall/doStream/doCallForResult` 增加 `RunnableConfig` 参数 + 保留旧签名兼容 | ~20 行 |
| 2 | `BaseModelInterceptor.java` | 从 `request.getContext()` 读取 `ConversationMetadata`，替换 `get("model")` | ~8 行 |
| 3 | `ProcessGraph.java` | `RunnableConfig.builder()` 注入 `ConversationMetadata` | ~2 行 |
| 4 | `DiagnosisGraph.java` | 构建 `RunnableConfig` 并注入 `ConversationMetadata`，传给 `invoke()` | ~5 行 |
| 5 | `InputGraph.java` | 同上 | ~5 行 |
| 6 | `KnowledgeGraph.java` | 同上 | ~5 行 |
| 7 | 各 ProcessModel 子类（~8个） | `call/callForResult/stream` 追加 `RunnableConfig` 参数 | 各 ~6 行 |
| 8 | 各 Diagnosis Node（~11个） | 调用模型时传递 `config` | 各 ~2 行 |
| 9 | `HistoryMessageCompressionModel.java` | 同 ProcessModel 改造 | ~6 行 |
| 10 | `HistoryAnalysisCompressionModel.java` | 同 ProcessModel 改造 | ~6 行 |
| 11 | `ConversationNameGenerationModel.java` | 同 ProcessModel 改造 | ~6 行 |

### 13.6 风险与应对

| 风险 | 应对 |
|------|------|
| 旧签名全部调用方需逐步迁移 | 保留旧签名（默认传 null），可渐进式改造，不强制一次性全部迁移 |
| `RunnableConfig` 在子图嵌套中丢失 | 框架 `CompiledGraph.invoke(stateMap, config)` 会自动将 config 传播到子节点 |
| `ConversationMetadata` 为 null 时 Interceptor 降级 | `BaseModelInterceptor` 中 null 判断，降级为仅记录模型名称 |
| `@Retryable` 与新签名方法的 AOP 代理兼容 | 新签名方法标注 `@Retryable`，旧签名方法不标注（仅委托调用） |

### 13.7 对 RecordingModelInterceptor 的影响

改造完成后，[3.1 节](#31-recordingmodelinterceptor) 中 `RecordingModelInterceptor` 的业务上下文获取方式为：

```java
// 纯 config 方式：从 request.getContext() 获取 ConversationMetadata
ConversationMetadata metadata = (ConversationMetadata) request.getContext().get(ConversationMetadata.NAME);
if (metadata != null) {
    record.setConversationId(metadata.getConversationId());
    record.setUserId(metadata.getUserId());
    record.setRoundNum(metadata.getCurrentRound());
}
```

**优势**：
- `ConversationMetadata` 通过框架 `RunnableConfig.metadata()` 机制传播，天然支持子图嵌套和异步线程池
- 无需 ThreadLocal，无泄漏风险，无需 `TaskDecorator` 跨线程传播
- `userId` 字段无需额外传递，直接从 `ConversationMetadata` 获取
- 上下文生命周期由框架管理，无需手动 set/clear