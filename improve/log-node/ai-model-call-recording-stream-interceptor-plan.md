# AI 模型调用记录 — 流式调用拦截器落地方案

> 覆盖所有通过 `BaseModel.doStream()` 发起的流式模型调用，
> 基于 `RecordingModelInterceptor`（记录输入侧）+ `BaseModel.doStream()` 内部 Flux 副作用（记录输出侧），
> 实现流式场景的完整调用记录。
>
> **核心原则：Interceptor 记录输入，BaseModel.doStream() 叠加 Flux 副作用记录输出，业务代码零改动。**

---

## 一、适用范围

### 1.1 覆盖的调用路径

| 模板方法 | 返回类型 | 典型调用方 |
|---------|---------|-----------|
| `doStream(ChatModel, String, AiNodeConfig)` | `Flux<NodeOutput>` | `TextMessageProcessorModel`（主线程对话） |

### 1.2 不覆盖的调用路径

| 路径 | 原因 | 对应方案 |
|------|------|---------|
| `BaseModel.doCall()` / `doCallForResult()` | 同步调用可在 Interceptor 中一次完成记录 | 见 `ai-model-call-recording-sync-interceptor-plan.md` |

### 1.3 流式调用的核心挑战

流式调用返回 `Flux<NodeOutput>`，数据逐 chunk 到达，**Interceptor 拦截时拿不到完整输出**：

```
时间线 ──────────────────────────────────────────────────►

Interceptor.interceptBaseModel() 执行时刻
  │  ✅ 可获取：systemPrompt, userPrompt, modelProvider, traceId, roundNum
  │  ❌ 无法获取：modelOutput（此时模型尚未开始输出 chunk）
  │
  ▼ 模型开始流式输出 chunk1, chunk2, chunk3 ... chunkN
  │
  │  chunk1 → chunk2 → ... → chunkN → AGENT_MODEL_FINISHED
  │  ↑ 此时才能拼接出完整 modelOutput
```

**因此流式记录必须分两步：**
1. **Interceptor**：INSERT 记录（输入侧），`modelOutput = null`
2. **BaseModel.doStream() Flux 副作用**：逐 chunk 累积，流完成时 UPDATE 记录（输出侧）

---

## 二、前置条件与约束

| 条件 | 说明 |
|------|------|
| spring-ai-alibaba 版本 | 1.1.2.0 |
| `StreamingModelInterceptor` | ❌ 不可用（PR #4605，1.1.2.0 未包含），无法在框架层拦截流式 chunk |
| `ModelInterceptor` | ✅ 可用，但只能记录输入侧，无法获取流式完整输出 |
| `BaseModel.doStream()` | ✅ 唯一能拿到完整 Flux 的位置，在此叠加副作用 |
| `NodeOutput` / `StreamingOutput` | ✅ 框架流式输出类型，`OutputType.AGENT_MODEL_FINISHED` 标识流结束 |
| `AiCallContextHolder.streamRecordId` | ✅ 纽带：Interceptor INSERT 后存入 recordId，Flux 完成时取出 UPDATE |

---

## 三、两步记录机制

### 3.1 数据库中始终只有 1 条记录

```
步骤1: RecordingModelInterceptor（拦截器）
  │  INSERT 一条新记录
  │  写入字段: traceId, callType=2(STREAM), modelProvider, systemPrompt, userPrompt,
  │           roundNum, startedAt, status
  │  modelOutput = null（流式场景此时拿不到完整输出）
  │  → 获得 recordId = 456
  │  → 存入 AiCallContextHolder.setStreamRecordId(456)
  │
  ▼ 模型开始流式输出 chunk1, chunk2, chunk3 ... chunkN
  │
  │  BaseModel.doStream() 内部的 Flux 副作用：
  │  doOnNext: chunk1 → contentBuilder.append("我")
  │  doOnNext: chunk2 → contentBuilder.append("理解")
  │  doOnNext: chunk3 → contentBuilder.append("你的")
  │  doOnNext: chunk4 → contentBuilder.append("感受")
  │  ...
  │
步骤2: AGENT_MODEL_FINISHED 信号到达
  │  UPDATE ai_model_call SET modelOutput = '我理解你的感受', status = 1 WHERE id = 456
  │  AiCallContextHolder.clearStreamRecordId()
  ▼
```

### 3.2 关键纽带：AiCallContextHolder.streamRecordId

| 步骤 | 操作 | 代码 |
|------|------|------|
| Interceptor INSERT 后 | 将 recordId 存入 ThreadLocal | `AiCallContextHolder.setStreamRecordId(recordId)` |
| BaseModel Flux 完成时 | 从 ThreadLocal 取出 recordId | `AiCallContextHolder.getStreamRecordId()` → 456 |
| BaseModel UPDATE 后 | 清除 ThreadLocal | `AiCallContextHolder.clearStreamRecordId()` |

---

## 四、详细设计

### 4.1 RecordingModelInterceptor — 流式路径的输入侧记录

在 `interceptBaseModel` 中，通过 `AiCallContextHolder.isStreamCall()` 判断当前是否为流式调用。
流式调用仅记录输入侧，将 recordId 存入上下文供后续关联。

```java
@Override
public ModelResponse interceptBaseModel(ModelRequest request, ModelCallHandler handler) {
    // ========== 构建记录 ==========
    AiModelCall record = new AiModelCall();
    record.setStartedAt(LocalDateTime.now());

    boolean isStream = AiCallContextHolder.isStreamCall();
    record.setCallType(isStream ? AiModelCall.CALL_TYPE_STREAM : AiModelCall.CALL_TYPE_SYNC);

    // 从 messages 中提取 systemPrompt 和 userPrompt
    extractPrompts(request, record);

    // 从 context 中提取 Agent 信息
    record.setModelProvider(
        (String) request.getContext().getOrDefault("model", "unknown")
    );

    // 从 FlowExecutionContextManager 获取 traceId
    Long conversationId = AiCallContextHolder.getConversationId();
    if (conversationId != null && FlowExecutionContextManager.existsContext(conversationId)) {
        record.setTraceId(FlowExecutionContextManager.getTraceId(conversationId));
    }

    record.setRoundNum(AiCallContextHolder.getRoundNum());

    // ========== 执行调用 ==========
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

        if (caughtException != null) {
            record.setStatus(AiModelCall.STATUS_FAILED);
            record.setErrorCode(caughtException.getClass().getSimpleName());
            String msg = caughtException.getMessage();
            record.setErrorMessage(msg != null && msg.length() > 2000
                    ? msg.substring(0, 2000) : msg);
            try {
                recordStorage.saveAsync(record);
            } catch (Exception e) {
                log.error("流式调用记录持久化失败", e);
            }
            return;  // 异常时直接返回
        }

        // ========== 同步调用：直接记录完整输出 ==========
        if (!isStream && response != null && response.getResult() != null) {
            ChatResponse chatResponse = response.getResult();
            if (chatResponse.getResult() != null
                    && chatResponse.getResult().getOutput() != null) {
                record.setModelOutput(chatResponse.getResult().getOutput().getText());
            }
            record.setStatus(AiModelCall.STATUS_COMPLETED);
            extractUsage(chatResponse, record);
        }

        // ========== 持久化 ==========
        Long recordId = recordStorage.saveAsync(record);

        // 流式调用：将 recordId 存入上下文，供 BaseModel.doStream() 关联
        if (isStream) {
            AiCallContextHolder.setStreamRecordId(recordId);
        }
    }

    return response;
}
```

### 4.2 BaseModel 改造：doStream() 内部叠加记录副作用

**关键：在 `doStream()` 返回的 `Flux<NodeOutput>` 上用 `doOnNext` 叠加副作用，
逐 chunk 累积到 StringBuilder，在 `AGENT_MODEL_FINISHED` 时一次性 UPDATE 完整输出。**

```java
@Slf4j
public abstract class BaseModel implements Model {

    @Autowired(required = false)
    private RecordingModelInterceptor recordingModelInterceptor;

    @Autowired(required = false)
    private AiModelCallRecordStorage recordStorage;

    // ==================== doStream 改造：叠加流式记录 ====================

    /**
     * 流式调用模型并返回Flux流
     * <p>
     * 构建Agent后执行流式调用，返回{@link Flux}流供调用方逐帧消费。
     * 若启用模型调用记录，在返回的 Flux 上叠加记录副作用：
     * 逐 chunk 累积正文和思考内容，在 AGENT_MODEL_FINISHED 时一次性 UPDATE 到数据库。
     * </p>
     *
     * @param chatModel  具体的ChatModel实例
     * @param userPrompt 用户提示词
     * @param config     节点配置，可为null（使用默认值）
     * @return 模型响应的NodeOutput流（可能叠加了记录副作用）
     * @throws GraphRunnerException Agent执行异常
     */
    protected Flux<NodeOutput> doStream(ChatModel chatModel, String userPrompt, AiNodeConfig config)
            throws GraphRunnerException {
        Flux<NodeOutput> rawFlux = buildAgent(chatModel, config).stream(userPrompt);

        // 未启用记录，直接返回原始 Flux
        if (recordStorage == null) {
            return rawFlux;
        }

        // 启用记录：在 Flux 上叠加累积副作用
        return wrapStreamWithRecording(rawFlux);
    }

    /**
     * 在流式 Flux 上叠加记录副作用
     * <p>
     * 逐 chunk 累积正文和思考内容到 StringBuilder，
     * 在 AGENT_MODEL_FINISHED 信号到达时，将累积的完整输出一次性 UPDATE 到数据库。
     * </p>
     * <p>
     * <b>重要：记录的是完整输出，不是单条 chunk。</b>
     * 每个 chunk 仅追加到 StringBuilder（纯内存操作，无 IO），
     * 只有 AGENT_MODEL_FINISHED 到达时才触发一次数据库写入。
     * </p>
     *
     * @param rawFlux 原始的 NodeOutput 流
     * @return 叠加了记录副作用的 NodeOutput 流
     */
    private Flux<NodeOutput> wrapStreamWithRecording(Flux<NodeOutput> rawFlux) {
        return Flux.defer(() -> {
            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder thinkBuilder = new StringBuilder();

            return rawFlux
                    .doOnNext(output -> {
                        if (!(output instanceof StreamingOutput streamingOutput)) {
                            return;
                        }

                        OutputType type = streamingOutput.getOutputType();
                        Message message = streamingOutput.message();

                        // 累积正文 chunk
                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            if (message instanceof AssistantMessage assistantMessage) {
                                Object reasoning = assistantMessage.getMetadata()
                                        .get("reasoningContent");
                                if (reasoning != null && !reasoning.toString().isBlank()) {
                                    thinkBuilder.append(reasoning);
                                } else {
                                    contentBuilder.append(assistantMessage.getText());
                                }
                            }
                        }

                        // 流完成信号：一次性 UPDATE 完整输出
                        if (type == OutputType.AGENT_MODEL_FINISHED) {
                            flushStreamRecord(contentBuilder, thinkBuilder);
                        }
                    })
                    .doOnError(err -> {
                        log.error("流式记录副作用异常，recordId：{}",
                                AiCallContextHolder.getStreamRecordId(), err);
                        // 异常时也尝试标记记录为失败
                        Long recordId = AiCallContextHolder.getStreamRecordId();
                        if (recordId != null) {
                            try {
                                recordStorage.updateOutputAsync(recordId, null, null);
                            } catch (Exception e) {
                                log.error("流式异常记录更新失败", e);
                            }
                        }
                        AiCallContextHolder.clearStreamRecordId();
                    });
        });
    }

    /**
     * 将累积的完整流式输出一次性更新到数据库
     * <p>
     * contentBuilder 中包含所有正文 chunk 的累积结果，
     * thinkBuilder 中包含所有思考 chunk 的累积结果。
     * 此时写入的是完整输出，不是单条 chunk。
     * </p>
     *
     * @param contentBuilder 正文累积器
     * @param thinkBuilder   思考内容累积器
     */
    private void flushStreamRecord(StringBuilder contentBuilder, StringBuilder thinkBuilder) {
        Long recordId = AiCallContextHolder.getStreamRecordId();
        if (recordId == null) {
            log.warn("流式记录刷新时 recordId 为空，跳过更新");
            return;
        }
        try {
            String modelOutput = contentBuilder.toString();
            // thinkOutput 暂存到 modelOutput 的元信息中，或后续扩展 thinkOutput 字段
            recordStorage.updateOutputAsync(recordId, modelOutput, null);
            log.debug("流式记录已更新，recordId：{}，输出长度：{}", recordId, modelOutput.length());
        } catch (Exception e) {
            log.error("流式记录更新失败，recordId：{}", recordId, e);
        } finally {
            AiCallContextHolder.clearStreamRecordId();
        }
    }
}
```

### 4.3 AiCallContextHolder — 流式上下文扩展

在同步方案基础上，流式场景需要额外使用 `STREAM_CALL` 和 `STREAM_RECORD_ID` 两个 ThreadLocal：

```java
public class AiCallContextHolder {

    private static final ThreadLocal<Long> CONVERSATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROUND_NUM = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STREAM_CALL = new ThreadLocal<>();
    private static final ThreadLocal<Long> STREAM_RECORD_ID = new ThreadLocal<>();

    // ========== 基础上下文 ==========

    public static void set(Long conversationId, Integer roundNum, boolean isStream) {
        CONVERSATION_ID.set(conversationId);
        ROUND_NUM.set(roundNum);
        STREAM_CALL.set(isStream);
    }

    public static Long getConversationId() { return CONVERSATION_ID.get(); }
    public static Integer getRoundNum() { return ROUND_NUM.get(); }
    public static boolean isStreamCall() {
        Boolean v = STREAM_CALL.get();
        return v != null && v;
    }

    // ========== 流式记录关联 ==========

    public static void setStreamRecordId(Long recordId) { STREAM_RECORD_ID.set(recordId); }
    public static Long getStreamRecordId() { return STREAM_RECORD_ID.get(); }
    public static void clearStreamRecordId() { STREAM_RECORD_ID.remove(); }

    // ========== 清理 ==========

    public static void clear() {
        CONVERSATION_ID.remove();
        ROUND_NUM.remove();
        STREAM_CALL.remove();
        STREAM_RECORD_ID.remove();
    }

    // ========== 跨线程传播 ==========

    public static Snapshot capture() {
        return new Snapshot(getConversationId(), getRoundNum(), isStreamCall());
    }

    public static void restore(Snapshot snapshot) {
        set(snapshot.conversationId, snapshot.roundNum, snapshot.isStream);
    }

    public record Snapshot(Long conversationId, Integer roundNum, boolean isStream) {}
}
```

### 4.4 上下文设置时机

#### ConversationMessageProcessor（流程入口）

```java
// 流式调用前
AiCallContextHolder.set(conversationId, currentRound, true);
try {
    // ... 执行流式调用（TextMessageProcessor.processMessage） ...
} finally {
    AiCallContextHolder.clear();
}
```

---

## 五、为什么不需要修改装饰器链

**本方案不需要修改 `AgentStreamProcessorBuilder` 和任何装饰器。**

流式输出记录逻辑完全在 `BaseModel.doStream()` 内部完成，
位于装饰器链的上游（`rawFlux` 被包装后才进入装饰器管道）：

```
BaseModel.doStream()
  │
  ├── buildAgent().stream(userPrompt)     → 原始 Flux<NodeOutput>
  │
  ├── wrapStreamWithRecording(rawFlux)     → 叠加记录副作用后的 Flux
  │     │
  │     │  doOnNext: 累积 chunk 到 StringBuilder（纯内存，无 IO）
  │     │  AGENT_MODEL_FINISHED: UPDATE 数据库（一次 IO）
  │     │
  │     ▼
  │   返回叠加后的 Flux
  │
  └── 返回给调用方（TextMessageProcessor）
        │
        ▼
  AgentStreamProcessorBuilder.create()
    .withThinkAccumulate()    → 累积思考片段（独立逻辑，与记录无关）
    .withLogging()            → 日志埋点（独立逻辑，与记录无关）
    .withPersistence()        → 持久化对话消息（独立逻辑，与记录无关）
    .withListener()           → WebSocket 推送（独立逻辑，与记录无关）
    .build()
    .process(flux)            → 装饰器链处理
```

**业务代码（TextMessageProcessor、AgentStreamProcessorBuilder）无需任何改动。**

---

## 六、执行流程

```
流式模型调用（主线程对话）
│
├── 【步骤1】ConversationMessageProcessor
│   └── AiCallContextHolder.set(123, 1, true)
│
├── 【步骤2】TextMessageProcessor.processMessage()
│   └── TextMessageProcessorModel.stream()
│         └── BaseModel.doStream()
│               │
│               ├── buildAgent().stream(userPrompt)
│               │     └── RecordingModelInterceptor.interceptBaseModel()
│               │           ├── 构建 AiModelCall 记录:
│               │           │     callType = 2 (CALL_TYPE_STREAM)
│               │           │     systemPrompt = "你是一位温暖贴心的心理陪伴好友..."
│               │           │     userPrompt = "【会话历史上下文】..."
│               │           │     modelOutput = null（流式，此时拿不到完整输出）
│               │           │     traceId = FlowExecutionContextManager.getTraceId(123)
│               │           │     roundNum = 1
│               │           ├── handler.call(request)  → 返回 Flux<NodeOutput>
│               │           ├── recordStorage.saveAsync(record)
│               │           │     → INSERT 记录，recordId = 456，modelOutput = null
│               │           └── AiCallContextHolder.setStreamRecordId(456)
│               │
│               └── wrapStreamWithRecording(rawFlux)
│                     → 返回叠加了记录副作用的 Flux
│
├── 【步骤3】装饰器链处理 Flux（与记录无关，原有逻辑不变）:
│   RootAgentStreamProcessor  → 转换为 AgentStreamEvent
│   ThinkAccumulateDecorator  → 累积思考片段
│   LoggingDecorator          → 日志埋点
│   MessagePersistDecorator   → 持久化对话消息
│   ListenerDispatchDecorator → WebSocket 推送
│
├── 【步骤4】同时，wrapStreamWithRecording 的 doOnNext 在静默工作:
│   chunk "我"   → contentBuilder.append("我")
│   chunk "理解" → contentBuilder.append("理解")
│   chunk "你的" → contentBuilder.append("你的")
│   chunk "感受" → contentBuilder.append("感受")
│   ...
│
├── 【步骤5】AGENT_MODEL_FINISHED 到达
│   └── flushStreamRecord():
│         ├── modelOutput = contentBuilder.toString() = "我理解你的感受..."
│         ├── recordStorage.updateOutputAsync(456, modelOutput, null)
│         │     → UPDATE ai_model_call SET modelOutput='我理解你的感受...', status=1 WHERE id=456
│         └── AiCallContextHolder.clearStreamRecordId()
│
└── 【步骤6】AiCallContextHolder.clear()

数据库结果：1 条记录，先 INSERT 输入侧，后 UPDATE 输出侧，最终字段完整
```

---

## 七、与同步方案的对比

| 维度 | 同步方案 | 流式方案（本方案） |
|------|---------|------------------|
| **记录步骤** | 1 次 INSERT（一步到位） | 1 次 INSERT + 1 次 UPDATE（两步完成） |
| **输出获取** | Interceptor 中直接从 `response.getResult()` 获取 | `doStream()` 内 Flux 副作用逐 chunk 累积 |
| **recordId 关联** | 不需要 | 需要 `AiCallContextHolder.streamRecordId` |
| **异常处理** | finally 中判断异常设置 status | `doOnError` 中标记 status=FAILED |
| **token 统计** | 从 `ChatResponse.getMetadata().getUsage()` 提取 | 流式场景通常无 usage，字段为 null |
| **装饰器链影响** | 无 | 无 |
| **业务代码改动** | 无 | 无 |

---

## 八、风险与应对

| 风险 | 应对 |
|------|------|
| 高频写入影响数据库性能 | 异步写入 + 独立线程池 + `CallerRunsPolicy` 兜底 |
| ThreadLocal 在异步线程中丢失 | `TaskDecorator` 传播 `AiCallContextHolder.Snapshot` |
| 流式异常时输出记录不完整 | `doOnError` 中标记 status=FAILED，DB 中 modelOutput 保持 null 标识异常 |
| StringBuilder 累积大文本占用内存 | 流式场景单次调用文本量有限（通常 < 10KB），且随 Flux 订阅结束自动 GC |
| Interceptor INSERT 与 Flux UPDATE 之间的时间窗口 | recordId 通过 ThreadLocal 传递，同一请求内线程安全 |
| 生产环境误开启导致数据膨胀 | 默认 `enabled=false`，可加定期清理策略 |
| 敏感信息记录 | 评估脱敏需求，可配置保留周期 |
| `StreamingModelInterceptor` 未来可用时的迁移 | 当前方案可平滑迁移：Flux 副作用逻辑可迁移到 `afterStreamComplete()` 回调中 |
| Reactor 线程模型下 ThreadLocal 传播 | `Flux.defer()` 确保 StringBuilder 在订阅时创建，`AiCallContextHolder` 在订阅线程中可用 |

---

## 九、未来演进：StreamingModelInterceptor

当 spring-ai-alibaba 升级到包含 PR #4605 的版本后，可引入 `StreamingModelInterceptor`：

| 迁移项 | 当前方案 | 迁移后 |
|--------|---------|--------|
| 输入记录 | `RecordingModelInterceptor` | `RecordingStreamingModelInterceptor.beforeStreamCall()` |
| 输出累积 | `BaseModel.doStream()` Flux 副作用 | `RecordingStreamingModelInterceptor.onStreamChunk()` |
| 输出写入 | `flushStreamRecord()` | `RecordingStreamingModelInterceptor.afterStreamComplete()` |
| BaseModel 改动 | `wrapStreamWithRecording()` | 移除，回归原始 `doStream()` |

迁移后 `BaseModel.doStream()` 无需任何改造，记录逻辑完全收敛在 Interceptor 体系中。