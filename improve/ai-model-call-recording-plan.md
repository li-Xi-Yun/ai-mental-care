# AI 模型调用记录改进方案

> 目标：将会话处理流程中每一步发送到模型的提示词（System Prompt + User Prompt）和模型输出内容进行完整记录，用于后续测评。

---

## 一、现状分析

### 1.1 模型调用分布

当前会话处理流程中，AI 模型调用分布在以下几层：

| 层级 | 调用方式 | 代表类 | 入口方法 |
|------|---------|--------|---------|
| 主线程对话 | 流式调用 `BaseModel.doStream()` | `TextMessageProcessor` → `TextMessageProcessorModel.stream()` | 流式 |
| 情绪识别 | 同步调用 `BaseModel.doCallForResult()` | `EmotionRecognitionNode` → `EmotionRecognitionModel.callForResult()` | 同步 |
| 语义压缩（消息） | 同步调用 `BaseModel.doCall()` | `HistoryMessageCompressionNode` → `HistoryMessageCompressionModel.call()` | 同步 |
| 语义压缩（分析） | 同步调用 `BaseModel.doCall()` | `HistoryAnalysisCompressionNode` → `HistoryAnalysisCompressionModel.call()` | 同步 |
| 会话名称生成 | 同步调用 `BaseModel.doCall()` | `ConversationNameGenerationNode` → `ConversationNameGenerationModel.call()` | 同步 |
| 诊断图各节点 | 同步调用 `BaseModel.doCallForResult()` | `ComprehensiveDiagnosisNode` 等处理侧节点 → 各 Model 子类 | 同步 |

### 1.2 关键收敛点

所有模型调用最终都收敛到 `BaseModel` 的三个模板方法：

- `doCall(ChatModel, String userPrompt, AiNodeConfig)` → 同步返回 `AssistantMessage`
- `doCallForResult(ChatModel, String userPrompt, AiNodeConfig)` → 同步返回反序列化结果
- `doStream(ChatModel, String userPrompt, AiNodeConfig)` → 流式返回 `Flux<NodeOutput>`

### 1.3 流式处理装饰器链

当前 `AgentStreamProcessorBuilder` 组装的装饰器链：

```
RootAgentStreamProcessor
  → ThinkAccumulateDecorator
    → LoggingDecorator
      → MessagePersistDecorator
        → ListenerDispatchDecorator
```

---

## 二、改进方案：AOP 切面 + 装饰器 组合

### 2.1 方案一：AOP 切面拦截 BaseModel（覆盖同步调用）

#### 思路

在 `BaseModel` 的 `doCall()` 和 `doCallForResult()` 方法上挂 AOP 切面，在方法执行前后记录提示词和输出。

#### 拦截范围

| 方法 | 调用类型 | 输出获取方式 |
|------|---------|-------------|
| `doCall()` | 同步 | 返回值 `AssistantMessage.getText()` |
| `doCallForResult()` | 同步 | 返回值为反序列化对象，需通过 `JsonUtils.toJsonStr()` 序列化记录 |
| `doStream()` | 流式 | **不在此切面记录输出**（Flux 无法在切面中直接消费），仅记录输入侧 |

#### 可获取的上下文信息

从 AOP 切面可获取：

- `BaseModel` 实例 → `getAgentName()`、`getAgentDescription()`、`getSystemPrompt(config)`
- 方法参数 → `chatModel`（可知模型类型：Ollama/DeepSeek/DashScope）、`userPrompt`、`config`（节点配置）
- 返回值 → 模型输出内容
- ThreadLocal → `conversationId`、`roundNum`

#### 伪代码

```java
@Aspect
@Component
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class AiModelCallRecordingAspect {

    private final AiModelCallRecordStorage recordStorage;

    @Around("execution(* org.lixiyun.server.ai.model.BaseModel.doCall(..)) || " +
            "execution(* org.lixiyun.server.ai.model.BaseModel.doCallForResult(..))")
    public Object recordSyncCall(ProceedingJoinPoint pjp) throws Throwable {
        BaseModel model = (BaseModel) pjp.getTarget();
        Object[] args = pjp.getArgs();
        ChatModel chatModel = (ChatModel) args[0];
        String userPrompt = (String) args[1];
        AiNodeConfig config = (AiNodeConfig) args[2];

        AiModelCallRecord record = new AiModelCallRecord();
        record.setAgentName(model.getAgentName());
        record.setModelType(resolveModelType(chatModel));
        record.setSystemPrompt(model.getSystemPrompt(config));
        record.setUserPrompt(userPrompt);
        record.setCallType(pjp.getSignature().getName());
        record.setConversationId(AiCallContextHolder.getConversationId());
        record.setRoundNum(AiCallContextHolder.getRoundNum());
        record.setCalledAt(LocalDateTime.now());

        long start = System.currentTimeMillis();
        Object result = pjp.proceed();
        record.setCallDurationMs(System.currentTimeMillis() - start);

        if (result instanceof AssistantMessage msg) {
            record.setModelOutput(msg.getText());
        } else {
            record.setModelOutput(JsonUtils.toJsonStr(result));
        }

        recordStorage.saveAsync(record);
        return result;
    }

    @Around("execution(* org.lixiyun.server.ai.model.BaseModel.doStream(..))")
    public Flux<NodeOutput> recordStreamCall(ProceedingJoinPoint pjp) throws Throwable {
        BaseModel model = (BaseModel) pjp.getTarget();
        Object[] args = pjp.getArgs();
        ChatModel chatModel = (ChatModel) args[0];
        String userPrompt = (String) args[1];
        AiNodeConfig config = (AiNodeConfig) args[2];

        // 流式调用仅记录输入侧，输出由 PromptRecordingDecorator 记录
        AiModelCallRecord record = new AiModelCallRecord();
        record.setAgentName(model.getAgentName());
        record.setModelType(resolveModelType(chatModel));
        record.setSystemPrompt(model.getSystemPrompt(config));
        record.setUserPrompt(userPrompt);
        record.setCallType("doStream");
        record.setConversationId(AiCallContextHolder.getConversationId());
        record.setRoundNum(AiCallContextHolder.getRoundNum());
        record.setCalledAt(LocalDateTime.now());

        // 将 record ID 传入上下文，供装饰器关联
        Long recordId = recordStorage.saveAsync(record);
        AiCallContextHolder.setStreamRecordId(recordId);

        return (Flux<NodeOutput>) pjp.proceed();
    }

    private String resolveModelType(ChatModel chatModel) {
        if (chatModel instanceof OllamaChatModel) return "ollama";
        if (chatModel instanceof DashScopeChatModel) return "dashscope";
        if (chatModel instanceof DeepSeekChatModel) return "deepseek";
        return "unknown";
    }
}
```

---

### 2.2 方案二：PromptRecordingDecorator 装饰器（覆盖流式调用）

#### 思路

在 `AgentStreamProcessorBuilder` 装饰器链中新增 `PromptRecordingDecorator`，在流式处理完成时记录完整的模型输出。

#### 装饰器链变更

```
RootAgentStreamProcessor
  → ThinkAccumulateDecorator
    → LoggingDecorator
      → PromptRecordingDecorator    ← 新增
        → MessagePersistDecorator
          → ListenerDispatchDecorator
```

#### 伪代码

```java
public class PromptRecordingDecorator extends AgentStreamDecorator {

    private final Long conversationId;
    private final AiModelCallRecordStorage recordStorage;
    private final StringBuilder contentAccumulator = new StringBuilder();
    private final StringBuilder thinkAccumulator = new StringBuilder();

    public PromptRecordingDecorator(AgentStreamProcessor delegate,
                                    Long conversationId,
                                    AiModelCallRecordStorage recordStorage) {
        super(delegate);
        this.conversationId = conversationId;
        this.recordStorage = recordStorage;
    }

    @Override
    public Flux<AgentStreamEvent> process(Flux<NodeOutput> rawOutputFlux) {
        return delegate.process(rawOutputFlux)
                .doOnNext(this::accumulate)
                .doOnComplete(this::flushRecord);
    }

    private void accumulate(AgentStreamEvent event) {
        if (event instanceof AgentStreamEvent.ModelContentChunk chunk) {
            contentAccumulator.append(chunk.text());
        } else if (event instanceof AgentStreamEvent.ModelThinkChunk chunk) {
            thinkAccumulator.append(chunk.reasoning());
        }
    }

    private void flushRecord() {
        Long recordId = AiCallContextHolder.getStreamRecordId();
        if (recordId != null) {
            String fullOutput = contentAccumulator.toString();
            String fullThink = thinkAccumulator.toString();
            recordStorage.updateOutput(recordId, fullOutput, fullThink);
        }
        AiCallContextHolder.clearStreamRecordId();
    }
}
```

#### AgentStreamProcessorBuilder 变更

新增 `.withRecording(conversationId, recordStorage)` 方法：

```java
public AgentStreamProcessorBuilder withRecording(Long conversationId,
                                                  AiModelCallRecordStorage recordStorage) {
    this.processor = new PromptRecordingDecorator(processor, conversationId, recordStorage);
    return this;
}
```

---

### 2.3 上下文传播：AiCallContextHolder

#### 思路

通过 `ThreadLocal` 在流程入口设置 `conversationId` 和 `roundNum`，在切面和装饰器中读取。

#### 伪代码

```java
public class AiCallContextHolder {

    private static final ThreadLocal<Long> CONVERSATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROUND_NUM = new ThreadLocal<>();
    private static final ThreadLocal<Long> STREAM_RECORD_ID = new ThreadLocal<>();

    public static void set(Long conversationId, Integer roundNum) {
        CONVERSATION_ID.set(conversationId);
        ROUND_NUM.set(roundNum);
    }

    public static Long getConversationId() {
        return CONVERSATION_ID.get();
    }

    public static Integer getRoundNum() {
        return ROUND_NUM.get();
    }

    public static void setStreamRecordId(Long recordId) {
        STREAM_RECORD_ID.set(recordId);
    }

    public static Long getStreamRecordId() {
        return STREAM_RECORD_ID.get();
    }

    public static void clearStreamRecordId() {
        STREAM_RECORD_ID.remove();
    }

    public static void clear() {
        CONVERSATION_ID.remove();
        ROUND_NUM.remove();
        STREAM_RECORD_ID.remove();
    }
}
```

#### 设置时机

在 `ConversationMessageProcessor.processConversationMessage()` 入口处设置：

```java
AiCallContextHolder.set(conversationId, processContext.getConversation().getCurrentRound());
```

在流程结束时清理：

```java
AiCallContextHolder.clear();
```

#### 异步线程传播

`diagnosisThreadPoolTaskExecutor` 需配置 `TaskDecorator`，确保 ThreadLocal 在线程池提交时正确传播：

```java
executor.setTaskDecorator(runnable -> {
    Long conversationId = AiCallContextHolder.getConversationId();
    Integer roundNum = AiCallContextHolder.getRoundNum();
    return () -> {
        try {
            AiCallContextHolder.set(conversationId, roundNum);
            runnable.run();
        } finally {
            AiCallContextHolder.clear();
        }
    };
});
```

---

## 三、数据模型

### 3.1 数据库表设计

```sql
CREATE TABLE ai_model_call_record (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    agent_name          VARCHAR(64)     NOT NULL COMMENT 'Agent名称（emotionRecognition/emotionalCompanion/...）',
    model_type          VARCHAR(32)     NOT NULL COMMENT '模型类型（ollama/deepseek/dashscope）',
    call_type           VARCHAR(32)     NOT NULL COMMENT '调用方式（doCall/doCallForResult/doStream）',
    system_prompt       TEXT            NULL COMMENT '系统提示词',
    user_prompt         TEXT            NULL COMMENT '用户提示词',
    model_output        MEDIUMTEXT      NULL COMMENT '模型输出内容',
    think_output        MEDIUMTEXT      NULL COMMENT '思考过程（仅流式调用有值）',
    conversation_id     BIGINT          NULL COMMENT '会话ID',
    round_num           INT             NULL COMMENT '轮次',
    call_duration_ms    BIGINT          NULL COMMENT '调用耗时（毫秒）',
    called_at           DATETIME        NOT NULL COMMENT '调用时间',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_agent_name (agent_name),
    INDEX idx_called_at (called_at),
    INDEX idx_conversation_round (conversation_id, round_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型调用记录表';
```

### 3.2 Java 实体

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

## 四、存储层

### 4.1 异步写入策略

为避免记录逻辑影响主流程性能，采用异步写入：

- 同步调用：在 AOP 切面中通过 `@Async` 注解的存储方法异步写入
- 流式调用：先异步写入输入侧记录（获取 recordId），流结束时异步更新输出侧

### 4.2 存储接口

```java
public interface AiModelCallRecordStorage {

    /**
     * 异步保存记录，返回记录ID
     */
    Long saveAsync(AiModelCallRecord record);

    /**
     * 异步更新流式调用的输出内容
     */
    void updateOutput(Long recordId, String modelOutput, String thinkOutput);
}
```

### 4.3 实现类

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
    public void updateOutput(Long recordId, String modelOutput, String thinkOutput) {
        AiModelCallRecord update = new AiModelCallRecord();
        update.setId(recordId);
        update.setModelOutput(modelOutput);
        update.setThinkOutput(thinkOutput);
        mapper.updateById(update);
    }
}
```

---

## 五、开关控制

### 5.1 配置项

```yaml
ai:
  evaluation:
    recording:
      enabled: true    # 是否启用模型调用记录
```

### 5.2 条件装配

所有新增的 Bean 均通过 `@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")` 控制装配：

- `AiModelCallRecordingAspect`
- `DatabaseAiModelCallRecordStorage`
- `PromptRecordingDecorator`（在 Builder 中通过配置项判断是否装配）

---

## 六、涉及修改的文件清单

### 6.1 新增文件

| 文件 | 说明 |
|------|------|
| `server/.../ai/model/recording/AiModelCallRecord.java` | 记录实体类 |
| `server/.../ai/model/recording/AiModelCallRecordMapper.java` | Mapper 接口 |
| `server/.../ai/model/recording/AiModelCallRecordStorage.java` | 存储接口 |
| `server/.../ai/model/recording/DatabaseAiModelCallRecordStorage.java` | 数据库存储实现 |
| `server/.../ai/model/recording/AiModelCallRecordingAspect.java` | AOP 切面 |
| `server/.../ai/model/recording/AiCallContextHolder.java` | ThreadLocal 上下文持有器 |
| `server/.../ai/model/processor/decorator/PromptRecordingDecorator.java` | 流式记录装饰器 |
| `resources/db/migration/V*.sql` | 建表 DDL |

### 6.2 需修改的文件

| 文件 | 修改内容 |
|------|---------|
| `ConversationMessageProcessor.java` | 流程入口设置/清理 `AiCallContextHolder` |
| `AgentStreamProcessorBuilder.java` | 新增 `.withRecording()` 方法 |
| `TextMessageProcessor.java` | Builder 链中调用 `.withRecording()` |
| `diagnosisThreadPoolTaskExecutor` 配置 | 设置 `TaskDecorator` 传播 ThreadLocal |
| `application.yml` | 新增 `ai.evaluation.recording.enabled` 配置项 |

---

## 七、测评数据使用方式

### 7.1 按会话查询完整调用链

```sql
SELECT agent_name, call_type, system_prompt, user_prompt, model_output, call_duration_ms, called_at
FROM ai_model_call_record
WHERE conversation_id = ?
ORDER BY called_at ASC;
```

### 7.2 按节点类型统计

```sql
SELECT agent_name, 
       COUNT(*) AS call_count, 
       AVG(call_duration_ms) AS avg_duration_ms,
       MAX(call_duration_ms) AS max_duration_ms
FROM ai_model_call_record
WHERE called_at BETWEEN ? AND ?
GROUP BY agent_name;
```

### 7.3 导出为评测数据集

可将记录导出为 JSONL 格式，供离线评测脚本使用：

```jsonl
{"agent_name": "emotionRecognition", "system_prompt": "...", "user_prompt": "...", "model_output": "...", "conversation_id": 123, "round_num": 1}
{"agent_name": "emotionalCompanion", "system_prompt": "...", "user_prompt": "...", "model_output": "...", "conversation_id": 123, "round_num": 1}
```

---

## 八、风险与注意事项

| 风险 | 应对措施 |
|------|---------|
| 高频写入影响数据库性能 | 异步写入 + 独立线程池，可考虑批量写入 |
| system_prompt / model_output 文本量大 | 使用 TEXT/MEDIUMTEXT 类型，异步写入避免阻塞 |
| ThreadLocal 在异步线程中丢失 | 配置 TaskDecorator 传播上下文 |
| 流式调用异常时记录不完整 | 在 `doOnError` 中也触发 flushRecord，标记记录状态为异常 |
| 生产环境误开启导致数据膨胀 | 配置开关默认关闭，增加定期清理策略 |
| 敏感信息（用户对话内容）记录 | 评估是否需要脱敏处理，或限制记录保留周期 |