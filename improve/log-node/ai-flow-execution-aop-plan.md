# AI 主流程执行记录 — AOP 切面落地方案

## 一、整体架构

```
processConversationMessage 入口
  │
  ▼ AOP: INSERT ai_flow_execution (trace_id, conversation_id, started_at, status=1)
  │     FlowExecutionContextManager.initContext(conversationId, traceId)
  │
  ├─ 主线程: executeMainThread → MessageProcessor.processMessage
  │     │
  │     ▼ 节点AOP: FlowExecutionContextManager.getTraceId / incrementNodeSequence
  │     │     INSERT ai_node_execution (trace_id, node_name, ...)
  │     │     节点内模型调用 → 拦截器: FlowExecutionContextManager.getTraceId
  │     │     INSERT ai_model_call (trace_id, node_id, ...)
  │     ▼ 节点AOP: UPDATE ai_node_execution (output_summary, duration_ms, ...)
  │
  ├─ 异步线程: analysisAndDiagnosis
  │     │ (同上，通过 FlowExecutionContextManager 获取上下文)
  │     ▼
  │
  ▼ AOP: finally
        UPDATE ai_flow_execution (finished_at, duration_ms, status, round_num, user_id)
        FlowExecutionContextManager.removeContext(conversationId)

异步任务全部完成后 → 触发聚合 UPDATE ai_flow_execution (node_count, model_call_count, total_tokens, ...)
```

## 二、Redis 上下文存储设计

### 2.1 存储结构

使用 Redis Hash 存储流程上下文，key 为 `flow:ctx:{conversation_id}`，字段如下：

| Hash Field | 类型 | 说明 |
|-----------|------|------|
| `trace_id` | Long | 流程唯一标识（雪花算法） |
| `conversation_id` | Long | 会话 ID |
| `round_num` | Integer | 当前轮次（初始为 0，UPDATE 阶段补填） |
| `user_id` | Long | 用户 ID（初始为 0，UPDATE 阶段补填） |
| `node_sequence` | Integer | 节点执行顺序计数器（从 1 递增） |

### 2.2 为什么用 Redis 而非 ThreadLocal

| | ThreadLocal | Redis Hash |
|---|---|---|
| 跨线程传递 | ❌ 需手动在每个异步任务中 set/clear | ✅ 天然跨线程，按 conversation_id 索引 |
| 侵入程度 | 每个异步 lambda 加 2 行（set + clear） | 零侵入，异步任务无需任何修改 |
| node_sequence 递增 | ❌ 需 AtomicInteger + 跨线程同步 | ✅ Redis HINCRBY 原子递增 |
| 生命周期 | 需手动 clear，遗漏则内存泄漏 | 可设 TTL 兜底，流程结束主动删除 |
| 性能 | 内存读写，纳秒级 | 网络往返，微秒级（本机 Redis < 0.1ms） |

**核心优势**：异步线程无需任何修改，彻底消除 ThreadLocal 手动传递的侵入性。

### 2.3 FlowExecutionContextManager — Redis 操作封装类

将所有 Redis Hash 操作封装为统一的 Spring Bean，所有 AOP 切面和拦截器通过该类调用，不直接操作 `StringRedisTemplate`。

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowExecutionContextManager {

    private final StringRedisTemplate stringRedisTemplate;

    // ── 初始化流程上下文（流程入口 AOP 调用）──
    public void initContext(Long conversationId, Long traceId) {
        String key = FlowExecutionConstant.CTX_KEY_PREFIX + conversationId;
        Map<String, String> ctx = new HashMap<>();
        ctx.put(FlowExecutionConstant.FIELD_TRACE_ID, String.valueOf(traceId));
        ctx.put(FlowExecutionConstant.FIELD_CONVERSATION_ID, String.valueOf(conversationId));
        ctx.put(FlowExecutionConstant.FIELD_ROUND_NUM, "0");
        ctx.put(FlowExecutionConstant.FIELD_USER_ID, "0");
        ctx.put(FlowExecutionConstant.FIELD_NODE_SEQUENCE, "0");
        stringRedisTemplate.opsForHash().putAll(key, ctx);
        stringRedisTemplate.expire(key, FlowExecutionConstant.CTX_TTL_MINUTES, TimeUnit.MINUTES);
    }

    // ── 获取 trace_id ──
    public Long getTraceId(Long conversationId) {
        String key = FlowExecutionConstant.CTX_KEY_PREFIX + conversationId;
        Object val = stringRedisTemplate.opsForHash().get(key, FlowExecutionConstant.FIELD_TRACE_ID);
        return val != null ? Long.valueOf((String) val) : null;
    }

    // ── 获取并原子递增 node_sequence ──
    public Long incrementNodeSequence(Long conversationId) {
        String key = FlowExecutionConstant.CTX_KEY_PREFIX + conversationId;
        return stringRedisTemplate.opsForHash().increment(key, FlowExecutionConstant.FIELD_NODE_SEQUENCE, 1);
    }

    // ── 更新 round_num 与 user_id（UPDATE 阶段补填）──
    public void updateRoundNumAndUserId(Long conversationId, Integer roundNum, Long userId) {
        String key = FlowExecutionConstant.CTX_KEY_PREFIX + conversationId;
        stringRedisTemplate.opsForHash().put(key, FlowExecutionConstant.FIELD_ROUND_NUM, String.valueOf(roundNum));
        stringRedisTemplate.opsForHash().put(key, FlowExecutionConstant.FIELD_USER_ID, String.valueOf(userId));
    }

    // ── 获取 round_num ──
    public Integer getRoundNum(Long conversationId) {
        String key = FlowExecutionConstant.CTX_KEY_PREFIX + conversationId;
        Object val = stringRedisTemplate.opsForHash().get(key, FlowExecutionConstant.FIELD_ROUND_NUM);
        return val != null ? Integer.valueOf((String) val) : null;
    }

    // ── 获取 user_id ──
    public Long getUserId(Long conversationId) {
        String key = FlowExecutionConstant.CTX_KEY_PREFIX + conversationId;
        Object val = stringRedisTemplate.opsForHash().get(key, FlowExecutionConstant.FIELD_USER_ID);
        return val != null ? Long.valueOf((String) val) : null;
    }

    // ── 判断上下文是否存在 ──
    public boolean existsContext(Long conversationId) {
        String key = FlowExecutionConstant.CTX_KEY_PREFIX + conversationId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    // ── 删除流程上下文（流程结束 AOP finally 调用）──
    public void removeContext(Long conversationId) {
        String key = FlowExecutionConstant.CTX_KEY_PREFIX + conversationId;
        stringRedisTemplate.delete(key);
    }
}
```

**方法清单**：

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `initContext` | conversationId, traceId | void | 流程入口初始化全部字段，设 TTL |
| `getTraceId` | conversationId | Long / null | 获取 trace_id |
| `incrementNodeSequence` | conversationId | Long | 原子递增 node_sequence 并返回递增后的值 |
| `updateRoundNumAndUserId` | conversationId, roundNum, userId | void | 补填 round_num 和 user_id |
| `getRoundNum` | conversationId | Integer / null | 获取 round_num |
| `getUserId` | conversationId | Long / null | 获取 user_id |
| `existsContext` | conversationId | boolean | 判断上下文是否存在（降级判断用） |
| `removeContext` | conversationId | void | 删除上下文（流程结束清理） |

## 三、trace_id 生成

使用 Hutool 的 `IdUtil.getSnowflake()` 生成：

```java
long traceId = IdUtil.getSnowflake(23, 17).nextId();
```

项目中已有相同用法（`AutoFillAspect`、`GithubUserConverter`），保持一致。

## 四、切面类设计

### 4.1 类结构

```
FlowExecutionAspect
├── 依赖注入
│   ├── FlowExecutionContextManager  — Redis 上下文操作封装
│   ├── AiFlowExecutionMapper        — 持久化操作
│   └── ConversationRepository       — 反查 round_num、user_id
│
├── 切点
│   └── execution(* ConversationMessageProcessor.processConversationMessage(Long))
│
└── 通知
    └── @Around
```

### 4.2 执行流程

```
@Around 拦截 processConversationMessage(conversationId)
│
├── 【方法入口 — INSERT 阶段】
│   ├── trace_id = IdUtil.getSnowflake(23, 17).nextId()
│   ├── conversationId = pjp.getArgs()[0]
│   ├── started_at = Instant.now()
│   ├── FlowExecutionContextManager.initContext(conversationId, traceId)
│   └── INSERT ai_flow_execution
│         (trace_id, conversation_id, flow_type=1, status=1, started_at)
│
├── 【执行原方法】pjp.proceed()
│
└── 【finally — UPDATE 阶段】
    ├── finished_at = Instant.now()
    ├── duration_ms = Duration.between(started_at, finished_at).toMillis()
    ├── 根据 conversation_id 反查 Conversation 获取：
    │   ├── round_num = conversation.getCurrentRound()
    │   └── user_id = conversation.getUserId()
    ├── FlowExecutionContextManager.updateRoundNumAndUserId(conversationId, roundNum, userId)
    ├── status = 有异常 ? 3(失败) : 2(完成)
    ├── 有异常时：
    │   ├── error_code = 异常类名或业务错误码
    │   └── error_message = 异常信息（截断至 2000 字符）
    ├── UPDATE ai_flow_execution SET
    │     status, finished_at, duration_ms, round_num, user_id,
    │     error_code, error_message, updated_at
    │     WHERE trace_id = ?
    └── FlowExecutionContextManager.removeContext(conversationId)
```

## 五、round_num 与 user_id 获取方案

### 方案：UPDATE 时反查（零侵入）

不在 INSERT 时填入 `round_num` 和 `user_id`，而是在 finally 的 UPDATE 阶段根据 `conversation_id` 查询数据库获取。

**理由**：
- 这两个字段在方法内部构建的 `ConversationProcessContextBO` 中，AOP 切面无法直接访问
- 反查一次 DB 的成本可接受（流程本身已有多次 DB 操作）
- 不需要在业务代码中添加任何东西，保持零侵入

**反查逻辑**：
```java
Conversation conversation = conversationRepository.getConversationById(conversationId);
if (conversation != null) {
    roundNum = conversation.getCurrentRound();
    userId = conversation.getUserId();
}
```

反查后通过 `FlowExecutionContextManager.updateRoundNumAndUserId()` 同步写入 Redis Hash，供后续节点/拦截器使用。

## 六、聚合统计方案（方案 B：异步延迟聚合）

### 6.1 问题

`processConversationMessage` 中存在异步任务：

```java
diagnosisExecutor.execute(() -> {
    conversationAiService.analysisAndDiagnosis(conversationId, processContext);
});
```

主线程方法返回时，异步任务可能尚未完成，此时无法聚合完整的统计数据。

### 6.2 方案 B：异步延迟聚合

**核心思路**：主流程 UPDATE 只写基础字段（status、duration_ms 等），聚合字段（node_count、model_call_count、total_tokens 等）在异步任务全部完成后单独触发一次聚合 UPDATE。

### 6.3 聚合触发机制

#### 方案 B-1：基于 Redis 计数

```
Redis Hash flow:ctx:{conversation_id} 中增加字段：
├── async_task_count    — 异步任务总数
└── async_task_done     — 已完成的异步任务数

主流程 AOP 入口：
  └── FlowExecutionContextManager.initContext() 中追加 async_task_count=0, async_task_done=0

每个异步任务提交前：
  └── FlowExecutionContextManager.incrementAsyncTaskCount(conversationId)

每个异步任务完成时（节点 AOP finally 中检测）：
  └── FlowExecutionContextManager.incrementAsyncTaskDone(conversationId)
  └── if (done == count) → triggerAggregation(traceId)

聚合方法：
  └── 查询 ai_node_execution + ai_model_call 按 trace_id 汇总
  └── UPDATE ai_flow_execution SET 聚合字段 WHERE trace_id = ?
  └── FlowExecutionContextManager.removeContext(conversationId)
```

#### 方案 B-2：基于定时轮询

```
主流程 UPDATE 时：
  └── 标记 status=2(完成)，不填聚合字段

后台定时任务（每 10s）：
  └── 查询 ai_flow_execution WHERE 聚合字段 IS NULL AND status=2 AND finished_at < NOW()-10s
  └── 对每条记录执行聚合 UPDATE
```

**推荐 B-1**：实时性好，无延迟；B-2 简单但有延迟且浪费查询。

### 6.4 聚合字段

| 字段 | 聚合来源 |
|------|---------|
| `node_count` | `COUNT(ai_node_execution) WHERE trace_id = ?` |
| `model_call_count` | `COUNT(ai_model_call) WHERE trace_id = ?` |
| `total_input_tokens` | `SUM(ai_model_call.input_tokens) WHERE trace_id = ?` |
| `total_output_tokens` | `SUM(ai_model_call.output_tokens) WHERE trace_id = ?` |
| `total_token_count` | `SUM(ai_model_call.total_tokens) WHERE trace_id = ?` |
| `total_cost_cny` | `SUM(ai_model_call.cost_cny) WHERE trace_id = ?` |

### 6.5 聚合 SQL

```sql
UPDATE ai_flow_execution fe
SET
    fe.node_count         = (SELECT COUNT(*)              FROM ai_node_execution ne WHERE ne.trace_id = fe.trace_id),
    fe.model_call_count   = (SELECT COUNT(*)              FROM ai_model_call mc    WHERE mc.trace_id = fe.trace_id),
    fe.total_input_tokens = (SELECT COALESCE(SUM(mc.input_tokens), 0)  FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.total_output_tokens= (SELECT COALESCE(SUM(mc.output_tokens), 0) FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.total_token_count  = (SELECT COALESCE(SUM(mc.total_tokens), 0)  FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.total_cost_cny     = (SELECT COALESCE(SUM(mc.cost_cny), 0)     FROM ai_model_call mc WHERE mc.trace_id = fe.trace_id),
    fe.updated_at         = NOW(3)
WHERE fe.trace_id = ?
```

## 七、异常处理

### 7.1 切面异常策略

```java
@Around(...)
public Object aroundFlow(ProceedingJoinPoint pjp) throws Throwable {
    // INSERT ...
    Throwable caught = null;
    try {
        return pjp.proceed();
    } catch (Throwable t) {
        caught = t;
        throw t;    // 原样抛出，不吞异常
    } finally {
        try {
            // UPDATE ...（根据 caught 是否为 null 决定 status）
        } catch (Exception e) {
            log.error("流程执行记录UPDATE失败，trace_id={}", traceId, e);
            // 日志记录失败不影响业务流程
        }
        flowExecutionContextManager.removeContext(conversationId);
    }
}
```

**关键原则**：
- 日志记录的任何异常**不能影响业务流程**
- catch 后原样 throw，保持原有异常传播链
- finally 中的 UPDATE 操作自身也要 try-catch 保护
- finally 中通过 `FlowExecutionContextManager.removeContext()` 清理 Redis Hash，防止上下文泄漏

### 7.2 error_code 提取

| 异常类型 | error_code | error_message |
|---------|------------|---------------|
| `BusinessException` | `e.getCode()` | `e.getMessage()` |
| 其他 `RuntimeException` | `RUNTIME_ERROR` | `e.getMessage()`（截断） |
| 其他 `Exception` | `SYSTEM_ERROR` | `e.getMessage()`（截断） |

## 八、提前退出场景

`processConversationMessage` 存在三个提前退出点，AOP 无条件拦截后也会产生记录：

| 场景 | 记录特征 |
|------|---------|
| 令牌获取失败 | `status=2`, `node_count=0`, `duration_ms < 10ms` |
| 缓存加载失败 | `status=2`, `node_count=0`, `duration_ms < 50ms` |
| 无未处理消息 | `status=2`, `node_count=0`, `duration_ms < 50ms` |

查询时可通过 `WHERE node_count > 0` 过滤掉这些无效记录。

## 九、其他 AOP / 拦截器如何使用上下文

节点 AOP 切面、模型调用拦截器等，统一通过 `FlowExecutionContextManager` 获取所需字段：

```java
// 节点 AOP 中
Long traceId = flowExecutionContextManager.getTraceId(conversationId);
Long nodeSequence = flowExecutionContextManager.incrementNodeSequence(conversationId);
Integer roundNum = flowExecutionContextManager.getRoundNum(conversationId);
```

**无需关心当前线程**，只要知道 `conversation_id` 即可获取全部上下文。

## 十、侵入性总结

| 侵入点 | 修改内容 | 行数 |
|--------|---------|------|
| 新增 `FlowExecutionConstant` | Redis Key/Field 常量类 | 新文件 |
| 新增 `FlowExecutionContextManager` | Redis 操作封装类 | 新文件 |
| 新增 `FlowExecutionAspect` | AOP 切面类 | 新文件 |

**业务代码总侵入：0 行**，异步任务无需任何修改，Redis 天然跨线程传递。