# AI 模型调用记录 — 最终落地方案

> 基于当前条件（spring-ai-alibaba 1.1.2.0，无 StreamingModelInterceptor），
> 结合项目已有 Interceptor 体系和 BaseModel 模板方法，给出可直接编码落地的方案。
> 
> **核心原则：记录逻辑收敛在 BaseModel + Interceptor 中，业务代码零改动。**

---

## 一、前置条件与约束

| 条件 | 说明 |
|------|------|
| spring-ai-alibaba 版本 | 1.1.2.0 |
| StreamingModelInterceptor | ❌ 不可用（PR #4605，1.1.2.0 未包含） |
| ModelInterceptor | ✅ 可用，项目已有 `BaseModelInterceptor` / `MessageModelInterceptor` |
| BaseModel 模板方法 | ✅ `doCall()` / `doCallForResult()` / `doStream()` 是所有模型调用的统一入口 |
| BaseModel.reactAgentBuilder() | 当前未注册任何 Interceptor |

---

## 二、为什么 Interceptor + BaseModel 不会产生重复记录

### 2.1 核心原因：两者操作的是同一条数据库记录的不同字段

整个记录过程分为两步，对应一条记录的**输入侧**和**输出侧**：

```
时间线 ──────────────────────────────────────────────────►

步骤1: RecordingModelInterceptor（拦截器）
  │  INSERT 一条新记录
  │  写入字段: agentName, modelType, callType, systemPrompt, userPrompt,
  │           conversationId, roundNum, callDurationMs, calledAt
  │  modelOutput = null（流式场景此时拿不到完整输出）
  │  → 获得 recordId = 456
  │  → 存入 AiCallContextHolder.setStreamRecordId(456)
  │
  ▼ 模型开始流式输出 chunk1, chunk2, chunk3 ... chunkN
  │
  │  BaseModel.doStream() 内部的 Flux 副作用：
  │  doOnNext: chunk1 → contentBuilder.append("你")
  │  doOnNext: chunk2 → contentBuilder.append("好")
  │  doOnNext: chunk3 → contentBuilder.append("吗")
  │  ...
  │
步骤2: AGENT_MODEL_FINISHED 信号到达
  │  UPDATE ai_model_call_record SET modelOutput = '你好吗' WHERE id = 456
  │  AiCallContextHolder.clearStreamRecordId()
  ▼
```

**数据库中始终只有 1 条记录**，经历 `1 次 INSERT + 1 次 UPDATE`。

### 2.2 同步调用更简单：一步到位

同步调用中，Interceptor 既能拿到输入也能拿到输出，**一次 INSERT 就完成整条记录**，不需要第二步 UPDATE：

```
RecordingModelInterceptor:
  → INSERT 记录（systemPrompt + userPrompt + modelOutput 全部写入）
  → 完成，无需后续步骤
```

### 2.3 对比：如果两者各 INSERT 一次才是重复

**重复记录**的情况是这样的（本方案**不会**发生）：

```
❌ 错误做法：
  Interceptor:  INSERT 记录A（输入侧）
  BaseModel:    INSERT 记录B（输出侧）
  → 数据库中 2 条记录，内容重复
```

**本方案的做法**：

```
✅ 正确做法：
  Interceptor:  INSERT 记录A（输入侧），返回 recordId
  BaseModel:    UPDATE 记录A（输出侧），WHERE id = recordId
  → 数据库中 1 条记录，字段完整
```

### 2.4 关键纽带：AiCallContextHolder.streamRecordId

两个步骤之所以能关联到同一条记录，靠的是 `AiCallContextHolder.streamRecordId`：

| 步骤 | 操作 | 代码 |
|------|------|------|
| Interceptor INSERT 后 | 将 recordId 存入 ThreadLocal | `AiCallContextHolder.setStreamRecordId(recordId)` |
| BaseModel Flux 完成时 | 从 ThreadLocal 取出 recordId | `AiCallContextHolder.getStreamRecordId()` → 456 |
| BaseModel UPDATE 后 | 清除 ThreadLocal | `AiCallContextHolder.clearStreamRecordId()` |

---

## 三、方案总览

```
┌───────────────────────────────────────────────────────────────┐
│                     同步调用路径                                │
│  doCall() / doCallForResult()                                  │
│    → ReactAgent.call()                                         │
│      → RecordingModelInterceptor.interceptBaseModel()          │
│        → INSERT 记录（systemPrompt + userPrompt + modelOutput） │
│        → 一次完成，无需第二步                                   │
├───────────────────────────────────────────────────────────────┤
│                     流式调用路径                                │
│  doStream()                                                    │
│    → ReactAgent.stream()                                       │
│      → RecordingModelInterceptor.interceptBaseModel()          │
│        → INSERT 记录（systemPrompt + userPrompt），modelOutput=null │
│        → recordId 存入 AiCallContextHolder                     │
│    → wrapStreamWithRecording()  ← BaseModel 内部自动叠加       │
│      → doOnNext: 逐 chunk 累积到 StringBuilder                 │
│      → AGENT_MODEL_FINISHED: UPDATE 记录的 modelOutput 字段    │
│        （写入的是累积后的完整输出，不是单条 chunk）              │
└───────────────────────────────────────────────────────────────┘
```

**核心设计：**
- **RecordingModelInterceptor**：统一记录所有调用的**输入侧**，同步调用同时记录输出侧
- **BaseModel.doStream() 内部 Flux 副作用**：仅在流式场景补充记录**输出侧**
- **不需要装饰器**：不需要 `PromptRecordingDecorator`，不需要改 `AgentStreamProcessorBuilder`，不需要改 `TextMessageProcessor`
- **记录的是完整输出**：chunk 逐条累积到 StringBuilder，仅在 `AGENT_MODEL_FINISHED` 时一次性写入数据库

---

## 四、详细设计

### 4.1 RecordingModelInterceptor

继承项目已有的 `BaseModelInterceptor`，在 `interceptBaseModel` 中记录输入和同步输出。

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
        // ========== 构建记录 ==========
        AiModelCallRecord record = new AiModelCallRecord();
        record.setCalledAt(LocalDateTime.now());

        // 从 messages 中提取 systemPrompt 和 userPrompt
        extractPrompts(request, record);

        // 从 context 中提取 Agent 信息
        record.setAgentName(
            (String) request.getContext().getOrDefault("_AGENT_", "unknown")
        );
        record.setModelType(
            (String) request.getContext().getOrDefault("model", "unknown")
        );

        // 从 ThreadLocal 获取业务上下文
        record.setConversationId(AiCallContextHolder.getConversationId());
        record.setRoundNum(AiCallContextHolder.getRoundNum());

        // 判断调用类型
        boolean isStream = AiCallContextHolder.isStreamCall();
        record.setCallType(isStream ? "doStream" : "doCall");

        // ========== 执行调用 ==========
        long start = System.currentTimeMillis();
        ModelResponse response = handler.call(request);
        record.setCallDurationMs(System.currentTimeMillis() - start);

        // ========== 同步调用：直接记录完整输出 ==========
        if (!isStream && response != null && response.getResult() != null) {
            ChatResponse chatResponse = response.getResult();
            if (chatResponse.getResult() != null
                    && chatResponse.getResult().getOutput() != null) {
                record.setModelOutput(chatResponse.getResult().getOutput().getText());
            }
        }

        // ========== 持久化 ==========
        Long recordId = recordStorage.saveAsync(record);

        // 流式调用：将 recordId 存入上下文，供 BaseModel.doStream() 关联
        if (isStream) {
            AiCallContextHolder.setStreamRecordId(recordId);
        }

        return response;
    }

    private void extractPrompts(ModelRequest request, AiModelCallRecord record) {
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
}
```

### 4.2 BaseModel 改造：doStream() 内部叠加记录

**关键：在 `doStream()` 返回的 `Flux<NodeOutput>` 上用 `doOnNext` 叠加副作用，
逐 chunk 累积到 StringBuilder，在 `AGENT_MODEL_FINISHED` 时一次性 UPDATE 完整输出。**

```java
@Slf4j
public abstract class BaseModel implements Model {

    @Autowired(required = false)
    private RecordingModelInterceptor recordingModelInterceptor;

    @Autowired(required = false)
    private AiModelCallRecordStorage recordStorage;

    // ==================== 原有 doCall / doCallForResult 不变 ====================

    protected AssistantMessage doCall(ChatModel chatModel, String userPrompt, AiNodeConfig config)
            throws GraphRunnerException {
        return buildAgent(chatModel, config).call(userPrompt);
    }

    @SuppressWarnings("unchecked")
    protected <T> T doCallForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config)
            throws GraphRunnerException {
        AssistantMessage message = doCall(chatModel, userPrompt, config);
        return (T) deserializeResult(message);
    }

    // ==================== doStream 改造：叠加流式记录 ====================

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
     * 每个 chunk 到达时仅追加到 StringBuilder（内存操作，无 IO），
     * 只有模型输出完成时才触发一次数据库写入。
     * </p>
     */
    private Flux<NodeOutput> wrapStreamWithRecording(Flux<NodeOutput> rawFlux) {
        return Flux.defer(() -> {
            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder thinkBuilder = new StringBuilder();

            return rawFlux
                    .doOnNext(output -> {
                        if (!(output instanceof StreamingOutput streaming)) {
                            return;
                        }

                        // 累积流式正文/思考片段（纯内存操作，无 IO）
                        if (streaming.getOutputType() == OutputType.AGENT_MODEL_STREAMING
                                && streaming.message() instanceof AssistantMessage msg) {
                            Object reasoning = msg.getMetadata().get("reasoningContent");
                            if (reasoning != null && !reasoning.toString().isBlank()) {
                                thinkBuilder.append(reasoning.toString());
                            } else if (msg.getText() != null) {
                                contentBuilder.append(msg.getText());
                            }
                        }

                        // AGENT_MODEL_FINISHED：模型输出完成，刷出完整记录
                        if (streaming.getOutputType() == OutputType.AGENT_MODEL_FINISHED) {
                            flushStreamRecord(contentBuilder, thinkBuilder);
                        }
                    })
                    .doOnError(err -> {
                        log.error("流式记录异常，recordId：{}",
                                AiCallContextHolder.getStreamRecordId(), err);
                    });
        });
    }

    /**
     * 将累积的完整流式输出更新到数据库
     * <p>
     * 此时 contentBuilder 中包含所有 chunk 拼接后的完整正文，
     * thinkBuilder 中包含所有思考 chunk 拼接后的完整思考过程。
     * 一次 UPDATE 写入，不是逐 chunk 写入。
     * </p>
     */
    private void flushStreamRecord(StringBuilder contentBuilder, StringBuilder thinkBuilder) {
        Long recordId = AiCallContextHolder.getStreamRecordId();
        if (recordId == null) {
            log.warn("流式记录刷出时 recordId 为空，跳过更新");
            return;
        }
        String modelOutput = contentBuilder.toString();
        String thinkOutput = thinkBuilder.isEmpty() ? null : thinkBuilder.toString();
        recordStorage.updateOutputAsync(recordId, modelOutput, thinkOutput);
        AiCallContextHolder.clearStreamRecordId();
    }

    // ==================== reactAgentBuilder 改造：注入 Interceptor ====================

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder(
            ChatModel chatModel, AiNodeConfig config) {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        com.alibaba.cloud.ai.graph.agent.Builder builder = ReactAgent.builder()
                .model(chatModel)
                .name(getAgentName())
                .description(getAgentDescription())
                .chatOptions(chatOptions(chatModel, config))
                .enableLogging(false);

        if (recordingModelInterceptor != null) {
            builder.interceptors(recordingModelInterceptor);
        }

        return builder;
    }

    // ... 其余方法不变 ...
}
```

### 4.3 AiCallContextHolder

```java
public class AiCallContextHolder {

    private static final ThreadLocal<Long> CONVERSATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROUND_NUM = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STREAM_CALL = new ThreadLocal<>();
    private static final ThreadLocal<Long> STREAM_RECORD_ID = new ThreadLocal<>();

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

    public static void setStreamRecordId(Long recordId) { STREAM_RECORD_ID.set(recordId); }
    public static Long getStreamRecordId() { return STREAM_RECORD_ID.get(); }
    public static void clearStreamRecordId() { STREAM_RECORD_ID.remove(); }

    public static void clear() {
        CONVERSATION_ID.remove();
        ROUND_NUM.remove();
        STREAM_CALL.remove();
        STREAM_RECORD_ID.remove();
    }

    /**
     * 捕获当前上下文快照，用于跨线程传播
     */
    public static Snapshot capture() {
        return new Snapshot(getConversationId(), getRoundNum(), isStreamCall());
    }

    /**
     * 从快照恢复上下文
     */
    public static void restore(Snapshot snapshot) {
        set(snapshot.conversationId, snapshot.roundNum, snapshot.isStream);
    }

    public record Snapshot(Long conversationId, Integer roundNum, boolean isStream) {}
}
```

### 4.4 上下文设置时机

#### ConversationMessageProcessor（流程入口）

```java
// 同步节点调用前
AiCallContextHolder.set(conversationId, currentRound, false);
try {
    // ... 执行同步节点 ...
} finally {
    AiCallContextHolder.clear();
}

// 流式调用前
AiCallContextHolder.set(conversationId, currentRound, true);
try {
    // ... 执行流式调用 ...
} finally {
    AiCallContextHolder.clear();
}
```

#### 异步线程池传播

```java
@Bean("diagnosisThreadPoolTaskExecutor")
public ThreadPoolTaskExecutor diagnosisThreadPoolTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // ... 原有配置 ...
    executor.setTaskDecorator(runnable -> {
        AiCallContextHolder.Snapshot snapshot = AiCallContextHolder.capture();
        return () -> {
            try {
                AiCallContextHolder.restore(snapshot);
                runnable.run();
            } finally {
                AiCallContextHolder.clear();
            }
        };
    });
    return executor;
}
```

---

## 五、装饰器链 — 无变更

**本方案不需要修改装饰器链。** 流式输出记录逻辑完全在 `BaseModel.doStream()` 内部完成。

```
RootAgentStreamProcessor
  → ThinkAccumulateDecorator
    → LoggingDecorator
      → MessagePersistDecorator
        → ListenerDispatchDecorator
```

**业务代码（TextMessageProcessor、AgentStreamProcessorBuilder）无需任何改动。**

---

## 六、数据模型

### 6.1 数据库表

```sql
CREATE TABLE ai_model_call_record (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    agent_name          VARCHAR(64)     NOT NULL,
    model_type          VARCHAR(32)     NOT NULL,
    call_type           VARCHAR(32)     NOT NULL COMMENT 'doCall / doStream',
    system_prompt       TEXT            NULL,
    user_prompt         TEXT            NULL,
    model_output        MEDIUMTEXT      NULL,
    think_output        MEDIUMTEXT      NULL COMMENT '思考过程（仅流式有值）',
    conversation_id     BIGINT          NULL,
    round_num           INT             NULL,
    call_duration_ms    BIGINT          NULL,
    called_at           DATETIME        NOT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_conversation_id (conversation_id),
    INDEX idx_agent_name (agent_name),
    INDEX idx_called_at (called_at),
    INDEX idx_conversation_round (conversation_id, round_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI模型调用记录表';
```

### 6.2 Java 实体

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_model_call_record")
public class AiModelCallRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentName;
    private String modelType;
    private String callType;
    private String systemPrompt;
    private String userPrompt;
    private String modelOutput;
    private String thinkOutput;
    private Long conversationId;
    private Integer roundNum;
    private Long callDurationMs;
    private LocalDateTime calledAt;
    private LocalDateTime createdAt;
}
```

---

## 七、存储层

### 7.1 接口

```java
public interface AiModelCallRecordStorage {
    Long saveAsync(AiModelCallRecord record);
    void updateOutputAsync(Long recordId, String modelOutput, String thinkOutput);
}
```

### 7.2 数据库实现

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class DatabaseAiModelCallRecordStorage implements AiModelCallRecordStorage {

    private final AiModelCallRecordMapper mapper;

    @Async("recordingTaskExecutor")
    @Override
    public Long saveAsync(AiModelCallRecord record) {
        mapper.insert(record);
        return record.getId();
    }

    @Async("recordingTaskExecutor")
    @Override
    public void updateOutputAsync(Long recordId, String modelOutput, String thinkOutput) {
        AiModelCallRecord update = new AiModelCallRecord();
        update.setId(recordId);
        update.setModelOutput(modelOutput);
        update.setThinkOutput(thinkOutput);
        mapper.updateById(update);
    }
}
```

### 7.3 异步线程池配置

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

## 八、开关控制

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
未启用时为 null，`doStream()` 直接返回原始 Flux，原有逻辑不受影响。

---

## 九、完整文件清单

### 新增文件（6 个）

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `server/.../ai/interceptor/ModelInterceptor/RecordingModelInterceptor.java` | 模型调用记录拦截器 |
| 2 | `server/.../ai/model/recording/AiModelCallRecord.java` | 记录实体类 |
| 3 | `server/.../ai/model/recording/AiModelCallRecordMapper.java` | Mapper 接口 |
| 4 | `server/.../ai/model/recording/AiModelCallRecordStorage.java` | 存储接口 |
| 5 | `server/.../ai/model/recording/DatabaseAiModelCallRecordStorage.java` | 数据库存储实现 |
| 6 | `server/.../ai/model/recording/AiCallContextHolder.java` | ThreadLocal 上下文持有器 |

### 需修改文件（2 个）

| # | 文件路径 | 修改内容 | 改动量 |
|---|---------|---------|--------|
| 1 | `BaseModel.java` | 注入 Interceptor + recordStorage；`reactAgentBuilder()` 注册 Interceptor；`doStream()` 叠加 `wrapStreamWithRecording()` | ~40 行 |
| 2 | `ConversationMessageProcessor.java` | 流程入口设置/清理 `AiCallContextHolder` | ~12 行 |

### 需新增配置

| # | 内容 | 位置 |
|---|------|------|
| 1 | `ai.evaluation.recording.enabled` | `application.yml` |
| 2 | `recordingTaskExecutor` Bean | 配置类 |
| 3 | `diagnosisThreadPoolTaskExecutor` 的 `TaskDecorator` | 配置类 |
| 4 | 建表 DDL | 迁移脚本 |

---

## 十、数据流示例

### 10.1 同步调用（情绪识别）

```
1. ConversationMessageProcessor
   → AiCallContextHolder.set(123, 1, false)

2. EmotionRecognitionNode.callForResult()
   → BaseModel.doCallForResult()
     → BaseModel.buildAgent()
       → reactAgentBuilder() 注册 RecordingModelInterceptor
       → ReactAgent.build()
     → ReactAgent.call(userPrompt)
       → RecordingModelInterceptor.interceptBaseModel()
         → 构建 AiModelCallRecord:
             agentName = "emotionRecognition"
             callType = "doCall"
             systemPrompt = "你是一个情绪识别专家..."
             userPrompt = "用户消息：我今天很难过"
             conversationId = 123, roundNum = 1
         → handler.call(request)  → 模型执行，耗时 1200ms
         → 记录 response:
             modelOutput = "{\"emotion\":\"sad\",\"confidence\":0.9}"
             callDurationMs = 1200
         → recordStorage.saveAsync(record)
             → INSERT 一条完整记录（输入+输出全部写入）
         → 返回 response

3. AiCallContextHolder.clear()

数据库结果：1 条记录，所有字段完整
```

### 10.2 流式调用（主线程对话）

```
1. ConversationMessageProcessor
   → AiCallContextHolder.set(123, 1, true)

2. TextMessageProcessor.processMessage()
   → AgentStreamProcessorBuilder.create()
       .withThinkAccumulate()
       .withLogging(conversationId)
       .withPersistence(...)
       .withListener(...)
       .build()
       （无需加 withRecording，业务代码不变）

3. TextMessageProcessorModel.stream()
   → BaseModel.doStream()
     → buildAgent().stream(userPrompt)
       → RecordingModelInterceptor.interceptBaseModel()
         → 构建 AiModelCallRecord:
             agentName = "emotionalCompanion"
             callType = "doStream"
             systemPrompt = "你是一个心理咨询师..."
             userPrompt = "【会话历史上下文】..."
             modelOutput = null（流式，此时拿不到完整输出）
         → handler.call(request)  → 返回 Flux<NodeOutput>
         → recordStorage.saveAsync(record)
             → INSERT 记录，recordId = 456，modelOutput = null
         → AiCallContextHolder.setStreamRecordId(456)
     → wrapStreamWithRecording(rawFlux)
       → 返回叠加了记录副作用的 Flux

4. 装饰器链处理 Flux（与记录无关，原有逻辑不变）:
   RootAgentStreamProcessor  → 转换为 AgentStreamEvent
   ThinkAccumulateDecorator  → 累积思考片段
   LoggingDecorator          → 日志埋点
   MessagePersistDecorator   → 持久化对话消息
   ListenerDispatchDecorator → WebSocket 推送

5. 同时，wrapStreamWithRecording 的 doOnNext 在静默工作:
   chunk "我"   → contentBuilder.append("我")
   chunk "理解" → contentBuilder.append("理解")
   chunk "你的" → contentBuilder.append("你的")
   chunk "感受" → contentBuilder.append("感受")
   ...
   AGENT_MODEL_FINISHED 到达
     → flushStreamRecord():
         modelOutput = contentBuilder.toString() = "我理解你的感受..."
         thinkOutput = thinkBuilder.toString() = "让我想想..."
         recordStorage.updateOutputAsync(456, modelOutput, thinkOutput)
             → UPDATE ai_model_call_record SET modelOutput='我理解你的感受...', thinkOutput='让我想想...' WHERE id=456
         AiCallContextHolder.clearStreamRecordId()

6. AiCallContextHolder.clear()

数据库结果：1 条记录，先 INSERT 输入侧，后 UPDATE 输出侧，最终字段完整
```

---

## 十一、测评数据查询

### 按会话查询完整调用链

```sql
SELECT agent_name, call_type, system_prompt, user_prompt,
       model_output, think_output, call_duration_ms, called_at
FROM ai_model_call_record
WHERE conversation_id = 123
ORDER BY called_at ASC;
```

### 按节点类型统计耗时

```sql
SELECT agent_name,
       COUNT(*) AS call_count,
       AVG(call_duration_ms) AS avg_duration_ms,
       MAX(call_duration_ms) AS max_duration_ms
FROM ai_model_call_record
WHERE called_at BETWEEN '2026-09-01' AND '2026-09-09'
GROUP BY agent_name;
```

### 导出为 JSONL 评测数据集

```sql
SELECT JSON_OBJECT(
    'agent_name', agent_name,
    'system_prompt', system_prompt,
    'user_prompt', user_prompt,
    'model_output', model_output,
    'conversation_id', conversation_id,
    'round_num', round_num
) AS jsonl
FROM ai_model_call_record
WHERE conversation_id = 123
ORDER BY called_at ASC;
```

---

## 十二、风险与应对

| 风险 | 应对 |
|------|------|
| 高频写入影响数据库性能 | 异步写入 + 独立线程池 + `CallerRunsPolicy` 兜底 |
| ThreadLocal 在异步线程中丢失 | `TaskDecorator` 传播 `AiCallContextHolder.Snapshot` |
| 流式异常时输出记录不完整 | `doOnError` 中记录异常日志，DB 中 modelOutput 保持 null 标识异常 |
| 生产环境误开启导致数据膨胀 | 默认 `enabled=false`，可加定期清理策略 |
| 敏感信息记录 | 评估脱敏需求，可配置保留周期 |
| Interceptor 与现有 MessageModelInterceptor 共存 | `RecordingModelInterceptor` 独立注册，不影响现有拦截器链 |
| 1.1.2.0 的 ModelRequest.getMessages() 可能丢失 systemMessage | `extractPrompts()` 中同时遍历 messages 和尝试 `request.getSystemMessage()`，双保险 |
| StringBuilder 累积大文本占用内存 | 流式场景单次调用文本量有限（通常 < 10KB），且随 Flux 订阅结束自动 GC |