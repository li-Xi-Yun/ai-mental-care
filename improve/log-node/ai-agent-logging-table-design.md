# AI Agent 执行日志记录 — 分层表结构设计

> 以流程为顶层实体，节点、模型调用、工具调用逐层关联，
> 支持完整还原每一次流程的执行过程，并面向未来扩展。

---

## 一、设计原则

| 原则 | 说明 |
|------|------|
| **分层记录** | 流程 → 节点 → 模型调用 → 工具调用，四层结构逐层钻取 |
| **无外键约束** | 表间关联由程序控制，不使用数据库外键，保证写入性能和灵活性 |
| **节点类型开放** | 节点表通过 `node_type` 区分类型，新增节点类型无需建新表（除非需要专属详情表） |
| **按需扩展** | 当前只建必须的 4 张表，未来按场景新增扩展表，主表结构稳定 |
| **关联标识为 `trace_id`** | 所有扩展表通过 `trace_id`（ULID）关联主表，主表主键仍为自增 `id`，详见第六章 |

---

## 二、表间关系

```
ai_flow_execution (id PK, trace_id UNIQUE)     ← 第1层：流程
  │
  ├── ai_node_execution (trace_id)              ← 第2层：节点（通过 trace_id 关联）
  │     │
  │     ├── ai_model_call (trace_id, node_id)   ← 第3层：模型调用（通过 trace_id 关联流程，node_id 关联节点）
  │     │     │
  │     │     └── ai_tool_call (trace_id, model_call_id)  ← 第4层：工具调用（通过 trace_id 关联流程，model_call_id 关联模型调用）
  │     │
  │     ├── ai_retrieval_record (trace_id, node_id)      ← 第5层：检索记录（通过 trace_id 关联）[未来]
  │     │
  │     ├── ai_guardrail_record (trace_id, node_id)      ← 第6层：安全过滤（通过 trace_id 关联）[未来]
  │     │
  │     └── ai_cache_record (trace_id, node_id)          ← 第7层：缓存记录（通过 trace_id 关联）[未来]
  │
  └── ai_evaluation (trace_id, target_type, target_id)   ← 第8层：评测（通过 trace_id 关联）[未来]
```

---

## 三、各表职责

### 第1层：`ai_flow_execution` — 流程执行记录表

| 维度 | 说明 |
|------|------|
| 粒度 | 1 次流程 = 1 条记录 |
| 什么时候写入 | `ConversationMessageProcessor.processConversationMessage()` 入口处 INSERT，出口处 UPDATE |
| 记录什么 | 流程的元数据标识：哪个会话、哪一轮、哪个用户、什么时候开始、什么时候结束、整体是否成功 |
| 谁产生 | `ConversationMessageProcessor` |

### 第2层：`ai_node_execution` — 节点执行记录表

| 维度 | 说明 |
|------|------|
| 粒度 | 1 个节点 = 1 条记录 |
| 什么时候写入 | 节点执行前 INSERT，执行后 UPDATE |
| 记录什么 | 节点名称、节点类型、执行耗时、输出摘要、是否成功 |
| 谁产生 | 各 Node 执行前后（可通过 AOP / Interceptor / 显式调用） |

**节点类型（`node_type`）枚举：**

| node_type | 说明 | 当前项目中的代表 |
|-----------|------|-----------------|
| `model_call` | 调用模型的节点 | EmotionRecognitionNode、HistoryMessageCompressionNode |
| `model_stream` | 流式调用模型的节点 | TextMessageProcessor |
| `data_process` | 纯数据处理节点 | 诊断图中的数据组装节点 |
| `graph` | 子图/子流程节点 | DiagnosisGraph |
| `retrieval` | 检索节点 | [未来] RAG 独立检索步骤 |
| `guardrail` | 安全过滤节点 | [未来] 输入/输出安全审核 |
| `cache_lookup` | 缓存查找节点 | [未来] 语义缓存 |
| `human_review` | 人工审核节点 | [未来] human-in-the-loop |

**嵌套支持：** 通过 `parent_node_id` 字段，DiagnosisGraph（graph 节点）内部的子节点可以指向它。

### 第3层：`ai_model_call` — 模型调用记录表

| 维度 | 说明 |
|------|------|
| 粒度 | 1 次模型调用 = 1 条记录 |
| 什么时候写入 | Interceptor 中 INSERT（输入侧），流式完成时 UPDATE（输出侧） |
| 记录什么 | 提示词、输出、Token 用量、成本、耗时、模型配置参数 |
| 谁产生 | `RecordingModelInterceptor` + `BaseModel.doStream()` |
| 前提 | 仅 `model_call` / `model_stream` 类型的节点有此记录 |

### 第4层：`ai_tool_call` — 工具调用记录表

| 维度 | 说明 |
|------|------|
| 粒度 | 1 次工具调用 = 1 条记录 |
| 什么时候写入 | ToolInterceptor 中记录 |
| 记录什么 | 工具名称、调用参数 JSON、返回结果 JSON、执行耗时 |
| 谁产生 | `RecordingToolInterceptor` |
| 前提 | 仅模型触发了工具调用时才有记录 |

### 第5层：`ai_retrieval_record` — 检索记录表 [未来]

| 维度 | 说明 |
|------|------|
| 粒度 | 1 次检索 = 1 条记录 |
| 记录什么 | 检索 query、检索源（知识库/向量库）、检索结果（文档片段+相似度）、耗时 |
| 触发条件 | 项目引入独立 RAG 检索步骤时 |

### 第6层：`ai_guardrail_record` — 安全过滤记录表 [未来]

| 维度 | 说明 |
|------|------|
| 粒度 | 1 次安全过滤 = 1 条记录 |
| 记录什么 | 过滤对象（输入/输出）、过滤规则（PII/内容安全/幻觉）、过滤结果（通过/拦截/修改）、拦截原因 |
| 触发条件 | 合规需求出现时（EU AI Act、国内生成式 AI 管理办法） |

### 第7层：`ai_cache_record` — 缓存记录表 [未来]

| 维度 | 说明 |
|------|------|
| 粒度 | 1 次缓存查找 = 1 条记录 |
| 记录什么 | 命中/未命中、缓存 key、缓存响应、相似度分数 |
| 触发条件 | 引入语义缓存进行成本优化时 |

### 第8层：`ai_evaluation` — 评测记录表 [未来]

| 维度 | 说明 |
|------|------|
| 粒度 | 1 次评测 = 1 条记录 |
| 记录什么 | 评测目标（flow/node/model_call）、评测类型（人工/自动）、评分、反馈、幻觉标记 |
| 触发条件 | 建立评测闭环时 |

---

## 四、当前必须建 vs 未来按需建

| 表 | 当前必须 | 理由 |
|---|:-------:|------|
| `ai_flow_execution` | ✅ | 流程是顶层锚点，没有它其他表没有归属 |
| `ai_node_execution` | ✅ | 节点是流程的核心执行单元，必须记录 |
| `ai_model_call` | ✅ | 模型调用是评测的核心数据，本次改进的直接目标 |
| `ai_tool_call` | ✅ | 项目已有 ToolInterceptor，工具调用记录是模型调用的自然子级 |
| `ai_retrieval_record` | ⏳ | 项目已有 RAG，但当前检索逻辑嵌入在模型调用中，可后续拆分 |
| `ai_guardrail_record` | ❌ | 当前无安全过滤，合规需求出现时再建 |
| `ai_cache_record` | ❌ | 当前无缓存，成本优化需求出现时再建 |
| `ai_evaluation` | ❌ | 当前无评测体系，评测闭环建立时再建 |

---

## 五、数据关联示例

以一次完整的会话消息处理为例：

```
用户发送消息 "我今天很难过"
│
▼ 触发一次完整流程
│
trace_id = "01H5A3F8B2C1D4E5F6A7B8C9D0"    ← 流程启动时生成
│
ai_flow_execution: id=1, trace_id="01H5A3F8B2...", conversation_id=123, round_num=1
│
├─ 步骤5（异步）：语义压缩
│   │
│   ├─ ai_node_execution: id=101, trace_id="01H5A3F8B2...", node_name="HistoryMessageCompressionNode",
│   │                       node_type="model_call", duration_ms=2100
│   │   └─ ai_model_call: id=201, trace_id="01H5A3F8B2...", node_id=101, model_name="deepseek-v3",
│   │                       system_prompt="压缩以下对话历史...", user_prompt="...",
│   │                       model_output="用户此前表达了工作压力...",
│   │                       input_tokens=800, output_tokens=50, cost=0.001
│   │
│   └─ ai_node_execution: id=102, trace_id="01H5A3F8B2...", node_name="HistoryAnalysisCompressionNode",
│                           node_type="model_call", duration_ms=1800
│       └─ ai_model_call: id=202, trace_id="01H5A3F8B2...", node_id=102, ...
│
├─ 步骤6（异步）：分析与诊断
│   │
│   ├─ ai_node_execution: id=103, trace_id="01H5A3F8B2...", node_name="EmotionRecognitionNode",
│   │                       node_type="model_call", duration_ms=1200
│   │   └─ ai_model_call: id=203, trace_id="01H5A3F8B2...", node_id=103, model_name="deepseek-v3",
│   │                       model_output='{"emotion":"sad","confidence":0.9}'
│   │
│   └─ ai_node_execution: id=104, trace_id="01H5A3F8B2...", node_name="DiagnosisGraph",
│                           node_type="graph", duration_ms=8500
│       │
│       ├─ ai_node_execution: id=105, trace_id="01H5A3F8B2...", parent_node_id=104,
│       │                       node_name="ComprehensiveDiagnosisNode", node_type="model_call", duration_ms=3200
│       │   └─ ai_model_call: id=204, trace_id="01H5A3F8B2...", node_id=105, model_name="deepseek-r1", ...
│       │       └─ ai_tool_call: id=301, trace_id="01H5A3F8B2...", model_call_id=204,
│       │                       tool_name="searchKnowledgeBase",
│       │                       arguments={"query":"抑郁症早期症状"}, result={...}, duration_ms=230
│       │
│       ├─ ai_node_execution: id=106, trace_id="01H5A3F8B2...", parent_node_id=104,
│       │                       node_name="RiskAssessmentNode", node_type="model_call", duration_ms=1800
│       │   └─ ai_model_call: id=205, trace_id="01H5A3F8B2...", node_id=106, ...
│       │
│       └─ ai_node_execution: id=107, trace_id="01H5A3F8B2...", parent_node_id=104,
│                               node_name="DataAggregationNode", node_type="data_process", duration_ms=50
│           └─ （无模型调用记录，纯数据处理）
│
├─ 步骤7（主线程）：消息处理
│   │
│   └─ ai_node_execution: id=108, trace_id="01H5A3F8B2...", node_name="TextMessageProcessor",
│                           node_type="model_stream", duration_ms=4500
│       └─ ai_model_call: id=206, trace_id="01H5A3F8B2...", node_id=108, call_type="doStream",
│                           model_output="我理解你的感受...", think_output="让我想想..."
│
└─ 步骤（异步）：会话名称生成
    │
    └─ ai_node_execution: id=109, trace_id="01H5A3F8B2...", node_name="ConversationNameGenerationNode",
                            node_type="model_call", duration_ms=800
        └─ ai_model_call: id=207, trace_id="01H5A3F8B2...", node_id=109, ...
```

---

## 六、关联标识决策

### 决策结论

**使用 `trace_id` 作为流程的唯一标识，在主表与所有扩展表中都添加 `trace_id` 字段用于链路关联。**

**主表主键仍使用自增 `id`，`trace_id` 不作为主键。**

### 具体规则

| 规则 | 说明 |
|------|------|
| 主表主键 | `id` BIGINT AUTO_INCREMENT — 数据库自增，保证全局唯一且紧凑 |
| 流程唯一标识 | `trace_id` BIGINT — 雪花算法（SnowflakeId），应用层在流程启动时生成 |
| 扩展表关联方式 | 所有扩展表通过 `trace_id` 关联主表，**不使用 `flow_id`** |
| `trace_id` 生成时机 | 流程第一行代码生成，INSERT 主表前即可使用 |
| `trace_id` 生成策略 | SnowflakeId — 时间有序、严格递增、8 字节 BIGINT、需分配 workerId/datacenterId |
| 枚举字段策略 | 所有枚举类型字段统一使用 TINYINT 存储，Java 侧用枚举类映射 |

### 为什么不用 `flow_id`（主键）做关联

| 考量 | 分析 |
|------|------|
| **写入时序** | 流程启动时异步任务立即开始执行，扩展表需要立即写入。如果用 `flow_id`，必须先 INSERT 主表拿到自增 id，增加了流程启动的同步依赖 |
| **跨线程传播** | `trace_id` 是 BIGINT，通过 ThreadLocal / TaskDecorator 传播更自然，不依赖数据库返回值 |
| **未来微服务** | 微服务拆分后各服务独立数据库，自增 id 会冲突，SnowflakeId 全局唯一 |
| **分布式追踪** | 未来接入 OpenTelemetry 时，`trace_id` 可映射到 OTel 的 traceId，无需额外生成 |

### 为什么 `trace_id` 不做主键

| 考量 | 分析 |
|------|------|
| **聚簇索引效率** | `trace_id` 是 BIGINT，与主键同类型，做 UNIQUE INDEX 时索引大小与主键索引相当 |
| **二级索引开销** | InnoDB 二级索引叶子节点存储主键值，BIGINT 主键开销最小 |
| **主键自增的优势** | 自增 id 是递增的，INSERT 永远追加到 B+Tree 尾部，不会触发页面分裂；SnowflakeId 虽然时间有序但不是严格递增，做主键仍有小概率分裂 |
| **`trace_id` 有独立索引** | `trace_id` 上建 UNIQUE INDEX，关联查询走此索引，BIGINT 比较效率极高 |

### 更新后的表间关系

```
ai_flow_execution (id PK, trace_id UNIQUE)
  │
  ├── ai_node_execution (trace_id)              ← 通过 trace_id 关联流程
  │     │
  │     ├── ai_model_call (trace_id, node_id)   ← 通过 trace_id 关联流程，node_id 关联节点
  │     │     │
  │     │     └── ai_tool_call (trace_id, model_call_id)  ← 通过 trace_id 关联流程，model_call_id 关联模型调用
  │     │
  │     ├── ai_retrieval_record (trace_id, node_id)        [未来]
  │     │
  │     ├── ai_guardrail_record (trace_id, node_id)        [未来]
  │     │
  │     └── ai_cache_record (trace_id, node_id)            [未来]
  │
  └── ai_evaluation (trace_id, target_type, target_id)     [未来]
```

**所有表都有 `trace_id` 字段，可以直接通过 `trace_id` 查到一次流程的所有层级数据，无需逐层 JOIN。**

### 更新后的数据关联示例

```
用户发送消息 "我今天很难过"
│
▼ 触发一次完整流程
│
trace_id = "01H5A3F8B2C1D4E5F6A7B8C9D0"    ← 流程启动时生成
│
ai_flow_execution: id=1, trace_id="01H5A3F8B2...", conversation_id=123, round_num=1
│
├─ 步骤5（异步）：语义压缩
│   │
│   ├─ ai_node_execution: id=101, trace_id="01H5A3F8B2...", node_name="HistoryMessageCompressionNode",
│   │                       node_type="model_call", duration_ms=2100
│   │   └─ ai_model_call: id=201, trace_id="01H5A3F8B2...", node_id=101, model_name="deepseek-v3",
│   │                       system_prompt="压缩以下对话历史...", user_prompt="...",
│   │                       model_output="用户此前表达了工作压力...",
│   │                       input_tokens=800, output_tokens=50, cost=0.001
│   │
│   └─ ai_node_execution: id=102, trace_id="01H5A3F8B2...", node_name="HistoryAnalysisCompressionNode",
│                           node_type="model_call", duration_ms=1800
│       └─ ai_model_call: id=202, trace_id="01H5A3F8B2...", node_id=102, ...
│
├─ 步骤6（异步）：分析与诊断
│   │
│   ├─ ai_node_execution: id=103, trace_id="01H5A3F8B2...", node_name="EmotionRecognitionNode",
│   │                       node_type="model_call", duration_ms=1200
│   │   └─ ai_model_call: id=203, trace_id="01H5A3F8B2...", node_id=103, model_name="deepseek-v3",
│   │                       model_output='{"emotion":"sad","confidence":0.9}'
│   │
│   └─ ai_node_execution: id=104, trace_id="01H5A3F8B2...", node_name="DiagnosisGraph",
│                           node_type="graph", duration_ms=8500
│       │
│       ├─ ai_node_execution: id=105, trace_id="01H5A3F8B2...", parent_node_id=104,
│       │                       node_name="ComprehensiveDiagnosisNode", node_type="model_call", duration_ms=3200
│       │   └─ ai_model_call: id=204, trace_id="01H5A3F8B2...", node_id=105, model_name="deepseek-r1", ...
│       │       └─ ai_tool_call: id=301, trace_id="01H5A3F8B2...", model_call_id=204,
│       │                       tool_name="searchKnowledgeBase",
│       │                       arguments={"query":"抑郁症早期症状"}, result={...}, duration_ms=230
│       │
│       ├─ ai_node_execution: id=106, trace_id="01H5A3F8B2...", parent_node_id=104,
│       │                       node_name="RiskAssessmentNode", node_type="model_call", duration_ms=1800
│       │   └─ ai_model_call: id=205, trace_id="01H5A3F8B2...", node_id=106, ...
│       │
│       └─ ai_node_execution: id=107, trace_id="01H5A3F8B2...", parent_node_id=104,
│                               node_name="DataAggregationNode", node_type="data_process", duration_ms=50
│           └─ （无模型调用记录，纯数据处理）
│
├─ 步骤7（主线程）：消息处理
│   │
│   └─ ai_node_execution: id=108, trace_id="01H5A3F8B2...", node_name="TextMessageProcessor",
│                           node_type="model_stream", duration_ms=4500
│       └─ ai_model_call: id=206, trace_id="01H5A3F8B2...", node_id=108, call_type="doStream",
│                           model_output="我理解你的感受...", think_output="让我想想..."
│
└─ 步骤（异步）：会话名称生成
    │
    └─ ai_node_execution: id=109, trace_id="01H5A3F8B2...", node_name="ConversationNameGenerationNode",
                            node_type="model_call", duration_ms=800
        └─ ai_model_call: id=207, trace_id="01H5A3F8B2...", node_id=109, ...
```

### 典型查询模式

**查询一次流程的所有数据（最核心查询）：**

```sql
-- 查流程
SELECT * FROM ai_flow_execution WHERE trace_id = '01H5A3F8B2...';

-- 查该流程所有节点
SELECT * FROM ai_node_execution WHERE trace_id = '01H5A3F8B2...' ORDER BY called_at;

-- 查该流程所有模型调用
SELECT * FROM ai_model_call WHERE trace_id = '01H5A3F8B2...' ORDER BY called_at;

-- 查该流程所有工具调用
SELECT * FROM ai_tool_call WHERE trace_id = '01H5A3F8B2...' ORDER BY called_at;
```

**优势：每一层都可以直接通过 `trace_id` 查询，无需逐层 JOIN。**

---

## 七、未来扩展场景验证

| 扩展场景 | 需要什么 | 当前表结构能否支撑 |
|---------|---------|-------------------|
| **多 Agent 协作**（Agent A 委托 Agent B） | Agent B 的执行也是一个流程 | ✅ `ai_flow_execution` 支持 `parent_flow_id` 嵌套 |
| **动态流程构建**（Workflow as Code） | 流程定义可能每次不同 | ✅ `ai_flow_execution` 记录 `flow_type`，`ai_node_execution` 记录节点拓扑 |
| **RAG 检索独立记录** | 检索步骤与模型调用分离 | ✅ 新增 `ai_retrieval_record`，`node_type=retrieval` |
| **输入/输出安全审核** | Guardrail 节点 | ✅ 新增 `ai_guardrail_record`，`node_type=guardrail` |
| **语义缓存** | 缓存命中直接返回 | ✅ 新增 `ai_cache_record`，`node_type=cache_lookup` |
| **Human-in-the-loop** | 人工审核步骤 | ✅ `node_type=human_review`，无需新表 |
| **A/B 测试** | 不同提示词版本对比 | ✅ `ai_model_call` 中记录 `prompt_version`，`ai_flow_execution` 中记录 `experiment_id` |
| **多模型路由**（同一节点尝试多个模型） | 一次节点调用多个模型 | ✅ 一个 node_id 对应多条 `ai_model_call` 记录 |
| **批量处理**（一次处理多个会话） | 批量任务 | ✅ `ai_flow_execution` 加 `batch_id` 字段即可 |
| **成本分摊**（按租户/部门） | 成本归属 | ✅ `ai_flow_execution` 中记录 `tenant_id`，汇总时 GROUP BY |
| **分布式追踪集成** | OpenTelemetry / SkyWalking | ✅ `trace_id` 已作为流程标识，未来可直接复用 OTel 的 traceId |