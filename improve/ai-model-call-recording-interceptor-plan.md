# AI 模型调用记录改进方案 — Interceptor 体系方案

> 基于 Spring AI Alibaba 框架原生 Interceptor/Hook 机制，实现模型调用全链路提示词与输出记录，用于后续测评。

---

## 一、方案选型背景

前序方案（AOP + 装饰器）已记录于 `ai-model-call-recording-plan.md`。经进一步调研，发现项目已完整接入 Spring AI Alibaba 的 Interceptor/Hook 体系，且框架提供了 `ModelInterceptor` 扩展点，天然适合在模型调用前后进行拦截记录。

### 1.1 项目已有 Interceptor/Hook 体系

| 类型 | 基类 | 框架接口 | 核心方法 | 现有实现 |
|------|------|---------|---------|---------|
| ModelInterceptor | `BaseModelInterceptor` | `ModelInterceptor` | `interceptModel(ModelRequest, ModelCallHandler)` | `MessageModelInterceptor` |
| ToolInterceptor | `BaseToolInterceptor` | `ToolInterceptor` | `interceptToolCall(ToolCallRequest, ToolCallHandler)` | `MessageToolInterceptor` |
| AgentHook | `BaseAgentHook` | `AgentHook` | `beforeAgent()` / `afterAgent()` | `MessageProcessedAgentHook` |
| ModelHook | `BaseModelHook` | `ModelHook` | `beforeModel()` / `afterModel()` | `MessageProcessedModelHook` |

### 1.2 当前问题：BaseModel 未注册 Interceptor

`BaseModel.reactAgentBuilder()` 构建 ReactAgent 时未注册任何 interceptor：

```java
// BaseModel.java L255-266
public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder(ChatModel chatModel, AiNodeConfig config) {
    return ReactAgent.builder()
            .model(chatModel)
            .name(getAgentName())
            .description(getAgentDescription())
            .chatOptions(chatOptions(chatModel, config))
            .enableLogging(false);
    // ← 没有 .interceptors() 和 .hooks()
}
```

而 `OllamaModelConfig` 中的货运客服 Agent 是注册了的，但会话处理流程的 Agent 未注册。

---

## 二、ModelInterceptor 能力分析

### 2.1 ModelRequest 提供的信息

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getMessages()` | `List<Message>` | 完整消息列表（System + User + History） |
| `getContext()` | `Map<String, Object>` | 上下文信息（含 `_AGENT_` 等） |

### 2.2 ModelResponse 提供的信息

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getResult()` | `ChatResponse` | 模型完整输出 |

### 2.3 ModelCallHandler 提供的能力

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `call(request)` | `ModelResponse` | 执行实际模型调用 |

**核心优势：在 `handler.call(request)` 前后可分别拿到请求和响应，天然适合记录。**

### 2.4 StreamingModelInterceptor（框架 PR #4605）

Spring AI Alibaba 在 PR #4605 中新增了 `StreamingModelInterceptor`，提供流式场景的逐 chunk 拦截：

| 方法 | 触发时机 | 用途 |
|------|---------|------|
| `beforeStreamCall(ModelRequest)` | 每次订阅前调用一次 | 记录输入 |
| `onStreamChunk(ChatResponse, ModelRequest)` | 每个 chunk 到达时 | 累积输出 |
| `afterStreamComplete(ModelRequest)` | 流完成时 | 刷出完整记录 |

> ⚠️ 需确认当前项目使用的 spring-ai-alibaba 版本是否已包含此 PR。如版本不支持，流式场景仍需配合装饰器方案。

---

## 三、方案设计

### 3.1 整体架构

```
ConversationMessageProcessor（流程入口，设置上下文）
    │
    ├── 主线程对话（流式）
    │     └── BaseModel.doStream()
    │           └── ReactAgent.stream()
    │                 └── RecordingModelInterceptor（记录输入）
    │                 └── StreamingModelInterceptor（累积输出，流完成时记录）
    │
    ├── 情绪识别（同步）
    │     └── BaseModel.doCallForResult()
    │           └── ReactAgent.call()
    │                 └── RecordingModelInterceptor（记录输入 + 输出）
    │
    ├── 语义压缩（同步）
    │     └── BaseModel.doCall()
    │           └── ReactAgent.call()
    │                 └── RecordingModelInterceptor（记录输入 + 输出）
    │
    └── 诊断图各节点（同步）
          └── BaseModel.doCallForResult()
                └── ReactAgent.call()
                      └── RecordingModelInterceptor（记录输入 + 输出）
```

### 3.2 新增 RecordingModelInterceptor

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
        // ========== 请求前：记录输入 ==========
        AiModelCallRecord record = new AiModelCallRecord();
        record.setCallType("doCall");

        // 从 messages 中分离 system prompt 和 user prompt
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

        // 从 context 中获取 Agent 信息
        record.setAgentName((String) request.getContext().getOrDefault("_AGENT_", "unknown"));
        record.setModelType((String) request.getContext().getOrDefault("model", "unknown"));
        record.setConversationId(AiCallContextHolder.getConversationId());
        record.setRoundNum(AiCallContextHolder.getRoundNum());
        record.setCalledAt(LocalDateTime.now());

        // ========== 执行实际调用 ==========
        long start = System.currentTimeMillis();
        ModelResponse response = handler.call(request);
        record.setCallDurationMs(System.currentTimeMillis() - start);

        // ========== 响应后：记录输出 ==========
        if (response != null && response.getResult() != null) {
            ChatResponse chatResponse = response.getResult();
            if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                record.setModelOutput(chatResponse.getResult().getOutput().getText());
            }
        }

        // 异步持久化
        recordStorage.saveAsync(record);
        return response;
    }
}
```

### 3.3 新增 RecordingStreamingModelInterceptor（流式场景）

> 前提：spring-ai-alibaba 版本已包含 `StreamingModelInterceptor`（PR #4605）。

```java
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class RecordingStreamingModelInterceptor implements StreamingModelInterceptor {

    private final AiModelCallRecordStorage recordStorage;

    public RecordingStreamingModelInterceptor(AiModelCallRecordStorage recordStorage) {
        this.recordStorage = recordStorage;
    }

    @Override
    public ModelRequest beforeStreamCall(ModelRequest request) {
        // 流式调用开始前，记录输入侧
        AiModelCallRecord record = new AiModelCallRecord();
        record.setCallType("doStream");

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
        record.setAgentName((String) request.getContext().getOrDefault("_AGENT_", "unknown"));
        record.setModelType((String) request.getContext().getOrDefault("model", "unknown"));
        record.setConversationId(AiCallContextHolder.getConversationId());
        record.setRoundNum(AiCallContextHolder.getRoundNum());
        record.setCalledAt(LocalDateTime.now());

        // 保存输入侧记录，获取 recordId
        Long recordId = recordStorage.saveAsync(record);
        // 将 recordId 存入 context，供后续回调使用
        request.getContext().put("_RECORD_ID_", recordId);

        return request;
    }

    @Override
    public ChatResponse onStreamChunk(ChatResponse chunk, ModelRequest request) {
        // 逐 chunk 累积输出（可选实现，也可在 afterStreamComplete 中一次性获取）
        return chunk;
    }

    @Override
    public void afterStreamComplete(ModelRequest request) {
        // 流完成时，可在此处补全输出侧记录
        // 如果框架在 afterStreamComplete 中提供聚合后的完整输出，则在此更新
        Long recordId = (Long) request.getContext().get("_RECORD_ID_");
        // recordStorage.updateOutput(recordId, fullOutput, thinkOutput);
    }

    @Override
    public void onStreamError(Throwable error, ModelRequest request) {
        log.error("流式调用异常，recordId：{}", request.getContext().get("_RECORD_ID_"), error);
    }
}
```

### 3.4 修改 BaseModel.reactAgentBuilder() 注入 Interceptor

```java
// BaseModel.java
@Autowired(required = false)
private RecordingModelInterceptor recordingModelInterceptor;

@Autowired(required = false)
private RecordingStreamingModelInterceptor recordingStreamingModelInterceptor;

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

---

## 四、上下文传播：AiCallContextHolder

### 4.1 ThreadLocal 上下文持有器

```java
public class AiCallContextHolder {

    private static final ThreadLocal<Long> CONVERSATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROUND_NUM = new ThreadLocal<>();

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

    public static void clear() {
        CONVERSATION_ID.remove();
        ROUND_NUM.remove();
    }
}
```

### 4.2 设置时机

在 `ConversationMessageProcessor.processConversationMessage()` 入口处设置：

```java
AiCallContextHolder.set(conversationId, processContext.getConversation().getCurrentRound());
try {
    // ... 原有流程 ...
} finally {
    AiCallContextHolder.clear();
}
```

### 4.3 异步线程传播

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

## 五、数据模型

### 5.1 数据库表设计

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

### 5.2 Java 实体

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

## 六、存储层

### 6.1 存储接口

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

### 6.2 数据库实现

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

## 七、开关控制

### 7.1 配置项

```yaml
ai:
  evaluation:
    recording:
      enabled: true    # 是否启用模型调用记录
```

### 7.2 条件装配

所有新增 Bean 均通过 `@ConditionalOnProperty` 控制：

- `RecordingModelInterceptor`
- `RecordingStreamingModelInterceptor`（如版本支持）
- `DatabaseAiModelCallRecordStorage`

`BaseModel` 中通过 `@Autowired(required = false)` 注入，未启用时为 null，不影响原有逻辑。

---

## 八、涉及修改的文件清单

### 8.1 新增文件

| 文件 | 说明 |
|------|------|
| `server/.../ai/interceptor/ModelInterceptor/RecordingModelInterceptor.java` | 同步调用记录拦截器 |
| `server/.../ai/interceptor/ModelInterceptor/RecordingStreamingModelInterceptor.java` | 流式调用记录拦截器（如版本支持） |
| `server/.../ai/model/recording/AiModelCallRecord.java` | 记录实体类 |
| `server/.../ai/model/recording/AiModelCallRecordMapper.java` | Mapper 接口 |
| `server/.../ai/model/recording/AiModelCallRecordStorage.java` | 存储接口 |
| `server/.../ai/model/recording/DatabaseAiModelCallRecordStorage.java` | 数据库存储实现 |
| `server/.../ai/model/recording/AiCallContextHolder.java` | ThreadLocal 上下文持有器 |
| `resources/db/migration/V*.sql` | 建表 DDL |

### 8.2 需修改的文件

| 文件 | 修改内容 | 改动量 |
|------|---------|--------|
| `BaseModel.java` | `reactAgentBuilder()` 中注入 `RecordingModelInterceptor` | 约 5 行 |
| `ConversationMessageProcessor.java` | 流程入口设置/清理 `AiCallContextHolder` | 约 6 行 |
| `diagnosisThreadPoolTaskExecutor` 配置 | 设置 `TaskDecorator` 传播 ThreadLocal | 约 10 行 |
| `application.yml` | 新增 `ai.evaluation.recording.enabled` 配置项 | 3 行 |

---

## 九、与 AOP 方案的对比

| 维度 | AOP 切面方案 | Interceptor 方案（本方案） |
|------|-------------|-------------------------|
| **侵入性** | 零侵入（不改动任何现有代码） | 需修改 `BaseModel.reactAgentBuilder()`（约 5 行） |
| **输入获取** | 从方法参数直接获取 `userPrompt` | 从 `request.getMessages()` 解析（需遍历 Message 列表） |
| **输出获取** | 从返回值直接获取 | 从 `response.getResult()` 获取 |
| **上下文获取** | ThreadLocal 传播 | `request.getContext()` + ThreadLocal |
| **流式支持** | 不支持，需配合装饰器 | 框架提供 `StreamingModelInterceptor`（如版本支持） |
| **框架一致性** | 绕过框架，独立机制 | ✅ 框架原生扩展点，与项目已有 Interceptor 体系一致 |
| **覆盖范围** | 所有经过 BaseModel 的调用 | 仅经过 ReactAgent 的调用（与 BaseModel 调用路径一致） |
| **可维护性** | AOP 切点可能被后续重构破坏 | Interceptor 是框架契约，更稳定 |
| **语义清晰度** | 通用方法拦截语义 | ✅ "模型调用拦截"语义，更贴切 |

---

## 十、流式场景的兼容策略

### 10.1 版本支持 StreamingModelInterceptor

直接使用 `RecordingStreamingModelInterceptor`，统一用 Interceptor 体系覆盖同步 + 流式。

### 10.2 版本不支持 StreamingModelInterceptor

退回装饰器方案，仅在流式场景使用 `PromptRecordingDecorator`：

```
RootAgentStreamProcessor
  → ThinkAccumulateDecorator
    → LoggingDecorator
      → PromptRecordingDecorator    ← 流式场景补充
        → MessagePersistDecorator
          → ListenerDispatchDecorator
```

此时：
- **同步调用** → `RecordingModelInterceptor` 记录
- **流式调用** → `RecordingModelInterceptor` 记录输入 + `PromptRecordingDecorator` 记录输出

---

## 十一、测评数据使用方式

### 11.1 按会话查询完整调用链

```sql
SELECT agent_name, call_type, system_prompt, user_prompt, model_output, call_duration_ms, called_at
FROM ai_model_call_record
WHERE conversation_id = ?
ORDER BY called_at ASC;
```

### 11.2 按节点类型统计

```sql
SELECT agent_name,
       COUNT(*) AS call_count,
       AVG(call_duration_ms) AS avg_duration_ms,
       MAX(call_duration_ms) AS max_duration_ms
FROM ai_model_call_record
WHERE called_at BETWEEN ? AND ?
GROUP BY agent_name;
```

### 11.3 导出为评测数据集

JSONL 格式，供离线评测脚本使用：

```jsonl
{"agent_name": "emotionRecognition", "system_prompt": "...", "user_prompt": "...", "model_output": "...", "conversation_id": 123, "round_num": 1}
{"agent_name": "emotionalCompanion", "system_prompt": "...", "user_prompt": "...", "model_output": "...", "conversation_id": 123, "round_num": 1}
```

---

## 十二、风险与注意事项

| 风险 | 应对措施 |
|------|---------|
| 高频写入影响数据库性能 | 异步写入 + 独立线程池，可考虑批量写入 |
| system_prompt / model_output 文本量大 | 使用 TEXT/MEDIUMTEXT 类型，异步写入避免阻塞 |
| ThreadLocal 在异步线程中丢失 | 配置 TaskDecorator 传播上下文 |
| 流式调用异常时记录不完整 | 在 `onStreamError` 中标记记录状态为异常 |
| 生产环境误开启导致数据膨胀 | 配置开关默认关闭，增加定期清理策略 |
| 敏感信息（用户对话内容）记录 | 评估是否需要脱敏处理，或限制记录保留周期 |
| Interceptor 注册顺序影响 | `RecordingModelInterceptor` 应在最外层（最先拦截请求、最后处理响应），确保记录到完整耗时 |
| spring-ai-alibaba 版本不支持 StreamingModelInterceptor | 降级为装饰器方案覆盖流式场景 |
| `request.getMessages()` 中 System Prompt 可能被框架合并到 User Prompt 中 | 需实测验证消息结构，必要时从第一条 SYSTEM 类型消息中提取 |