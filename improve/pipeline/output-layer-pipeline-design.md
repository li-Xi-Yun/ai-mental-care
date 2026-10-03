# AI 对话输出层设计

> **文档状态**：设计讨论稿  
> **创建日期**：2026-09-29  
> **讨论范围**：输出层管道/过滤器架构，解耦 Model、TTS 等处理节点的组合与多通道输出  
> **注意**：本文档仅涉及输出层，输入层设计见 `input-layer-adapter-design.md`

---

## 目录

1. [背景与动机](#1-背景与动机)
2. [现状分析与耦合问题](#2-现状分析与耦合问题)
3. [架构总览：三层分离模型](#3-架构总览三层分离模型)
4. [核心设计原则](#4-核心设计原则)
5. [OutputDataType 枚举](#5-outputdatatype-枚举)
6. [OutputNode 接口设计](#6-outputnode-接口设计)
7. [标准消息契约](#7-标准消息契约)
8. [具体节点实现](#8-具体节点实现)
9. [OutputPipeline 管道设计](#9-outputpipeline-管道设计)
10. [OutputPipelineFactory 管道组装工厂](#10-outputpipelinefactory-管道组装工厂)
11. [OutputPipelineSessionManager 会话管道管理器](#11-outputpipelinesessionmanager-会话管道管理器)
12. [OutputContext 上下文数据包](#12-outputcontext-上下文数据包)
13. [ProcessingResult：处理结果](#13-processingresult处理结果)
14. [IOMode 输入输出模式](#14-iomode-输入输出模式)
15. [多输出模式设计](#15-多输出模式设计)
16. [与现有架构的融合](#16-与现有架构的融合)
17. [生命周期管理](#17-生命周期管理)
18. [中断信号传播机制](#18-中断信号传播机制)
19. [两种实现路径对比](#19-两种实现路径对比)
20. [扩展性与未来规划](#20-扩展性与未来规划)
21. [与输入层的关系](#21-与输入层的关系)
22. [待讨论的开放问题](#22-待讨论的开放问题)
23. [总结](#23-总结)

---

## 1. 背景与动机

### 1.1 目标

实现一个 **可选输入侧数据类型 × 多选输出侧数据类型** 的对话系统。用户在前端选择输入和输出模式后，后端自动组装对应的处理管道（Pipeline），数据在管道中依次流过各个处理节点，实现解耦的、可组合的数据处理。

### 1.2 当前支持的组合

| 输入 | 输出 | 实现路径 | 对应类 |
|------|------|---------|--------|
| 文本 | 文本 | `input → Model → output` | `TextMessageProcessor` |
| 语音 | 语音 | `input → ASR → Model → TTS → output` | `ChatMessageProcessor` + `AudioServiceImpl` |

### 1.3 期望支持的全部组合

| 输入 | 输出 | 实现路径 | 中间节点 |
|------|------|---------|---------|
| 文本 | 文本 | `Adapter → Model → output` | Model |
| 文本 | 语音 | `Adapter → Model → TTS → output` | Model, TTS |
| 文本 | 文本 + 语音 | `Adapter → Model → TTS → output`（双输出） | Model, TTS |
| 语音 | 文本 | `Adapter(ASR) → Model → output` | ASR, Model |
| 语音 | 语音 | `Adapter(ASR) → Model → TTS → output` | ASR, Model, TTS |
| 语音 | 文本 + 语音 | `Adapter(ASR) → Model → TTS → output`（双输出） | ASR, Model, TTS |

> **说明**："文本 + 语音"组合表示一个会话同时输出文本流和音频流。节点链与单一音频输出相同（`[ModelNode, TtsNode]`），区别在于节点内部根据 `outputTypes` 判断是否附加推送副作用——详见 [§15 多输出模式设计](#15-多输出模式设计)。

### 1.4 三个基础处理节点

| 节点 | 缩写 | 输入类型 | 输出类型 | 职责 |
|------|------|---------|---------|------|
| 语音识别 | **ASR** | Audio（`byte[]`） | Text（`String`） | 将用户语音转为文本 |
| 大语言模型 | **Model** | Text（`String`） | Text（`String`） | 对文本进行理解与生成回复 |
| 文本转语音 | **TTS** | Text（`String`） | Audio（`byte[]`） | 将文本回复转为语音 |

> **注意**：ASR 放在输入层（`AudioInputAdapter`），不属于输出层。输出层从时间轮触发时的 `OutputContext` 获取已识别的文本消息。

---

## 2. 现状分析与耦合问题

### 2.1 资源创建的耦合

**位置**：[`AudioServiceImpl.initializeAudioResources()`](file:///D:/JavaProject/ai_mental_care/server/src/main/java/org/lixiyun/server/service/impl/user/AudioServiceImpl.java#L206-L222)

```java
private void initializeAudioResources(Long conversationId, Long userId) {
    // 问题：ASR 和 TTS 总是同时创建，无法按需选择
    asrConnectionManager.register(userId, createAsrCallback(conversationId, userId));
    ttsConnectionManager.register(userId, createTtsCallback(conversationId, userId));
}
```

### 2.2 数据流转的耦合

**位置**：[`VoiceMessageProcessor.buildListener()`](file:///D:/JavaProject/ai_mental_care/server/src/main/java/org/lixiyun/server/infrastructure/conversation/processor/VoiceMessageProcessor.java#L152-L192)

```java
public void onContentChunk(String text) {
    contentBuilder.append(text);
    ttsConnectionManager.sendTextSegment(userId, text);  // 问题：LLM→TTS 级联写死
    conversationWebSocketManager.sendAudioStream(userId, conversationId, text);
}

public void onModelComplete(AssistantMessage message) {
    ttsConnectionManager.finishSynthesis(userId);  // 问题：TTS 完成信号写死
}
```

### 2.3 资源销毁的耦合

所有资源集中销毁，不区分哪些实际在使用。

### 2.4 输入侧与输出侧被时间轮分隔

输出侧的执行是通过时间轮异步触发的，与输入侧不在同一个时间片段中。

---

## 3. 架构总览：三层分离模型

```
┌──────────────────────────────────────────────────────────────┐
│                  输入层 (Input Layer)                         │
│  详见：input-layer-adapter-design.md                           │
└──────────────────────┬───────────────────────────────────────┘
                       │ 写入
                       ▼
┌──────────────────────────────────────────────────────────────┐
│              中间存储层 (Storage Layer)                        │
│  ConversationHistoryMessagesStorage  (MySQL 消息表)            │
│  ConversationCacheManager            (Redis 缓存)              │
└──────────────────────┬───────────────────────────────────────┘
                       │ 时间轮到期触发
                       ▼
┌──────────────────────────────────────────────────────────────┐
│                  输出层 (Output Layer)       ← 本文档的范围     │
│  职责：加载上下文 → 管道执行（Model → TTS → ...）→ 流式推送     │
│  模式：管道/过滤器（Pipeline + OutputNode）                     │
│  时机：异步执行（时间轮驱动）                                    │
│                                                               │
│  [ModelNode] → [TtsNode] → ...                                │
│  每个节点：接收 OutputContext（共享状态池）→ 从 fluxStore/dataStore 读写数据
└──────────────────────────────────────────────────────────────┘
```

### 3.1 为什么输出层需要管道？

输出层有明显的**级联处理链**：

```
ModelNode 产出文本 → TtsNode 消费文本并转语音 → （未来）AvatarNode 消费文本驱动虚拟形象 → ...
```

每个下游节点消费上游节点的输出，这正是管道/过滤器模式的标准应用场景。

---

## 4. 核心设计原则

1. **节点通过共享 Context 通信**：节点间不直接传递数据，而是通过 `OutputContext` 的 `fluxStore` / `dataStore` 作为共享状态池解耦
2. **副作用内聚于节点**：WebSocket 推送、数据库存储等副作用由各节点在 `process()` 内部自行处理，Pipeline 不参与副作用编排
3. **输出方式由 outputTypes 驱动**：节点根据 `context.hasOutputType()` 判断是否附加推送副作用
4. **节点接口极简化**：`process(OutputContext)` 返回 `void`，不区分 I/O 模式，不包装 ProcessingResult

---

## 5. OutputDataType 枚举

```java
/** 管道中流动的数据类型 */
public enum OutputDataType {
    TEXT,
    AUDIO
}
```

---

## 6. OutputNode 接口设计

### 6.1 接口定义

```java
/**
 * 输出节点接口——管道中的一个"过滤器"
 *
 * <p>节点接收 OutputContext（共享状态池），从中读取上游节点产出的数据，
 * 处理后写入产出的 Flux 或数据到 context 中，供下游节点消费。
 * 节点之间不直接传递数据，而是通过 OutputContext 的 fluxStore / dataStore 解耦。</p>
 */
interface OutputNode {

    // ==================== 元信息 ====================

    /** 节点名称 */
    String getNodeName();

    // ==================== 生命周期 ====================

    /** 初始化节点：创建连接、注册回调、分配资源 */
    void init(Long conversationId, Long userId);

    /** 销毁节点：释放所有资源、关闭连接 */
    void destroy();

    /** 中断当前处理：保留实例和连接，中止正在进行的流 */
    void interrupt();

    // ==================== 核心处理 ====================

    /**
     * 核心处理入口
     * @param context   输出上下文（包含所有可用数据，节点按需提取）
     */
    void process(OutputContext context);
    
    // ================ Socket 路径与说明 ===============
    
    /**
     * 获取该节点中的Socket路径和对应的说明信息
     * @return NodeEndpoint 包含Socket路径 + 说明
     */
    List<NodeEndpoint> getSocketInfo();
}
```

### 6.2 类型关系总览图

```
                    《interface》
                      OutputNode
        ├── getNodeName()            : String
        ├── init() / destroy() / interrupt()
        ├── process(OutputContext)   : void
        └── getSocketInfo()          : List<NodeEndpoint>
                              △
          ┌───────────────────┼───────────────────┐
          │                   │                   │
    ┌───────────┐      ┌───────────┐      ┌───────────┐
    │ModelNode  │      │ TtsNode   │      │(未来节点)  │
    └───────────┘      └───────────┘      └───────────┘


    OutputContext                      ← 共享状态池，贯穿整个管道执行过程
    ├── userId, conversationId         ← 会话标识
    ├── outputTypes                    ← 前端请求的输出模式集合
    ├── fluxStore                      ← ConcurrentHashMap<String, Flux<?>>
    │   └── 存储各节点产出的 Flux 流（如 "model_output"、"tts_output"）
    └── dataStore                      ← ConcurrentHashMap<String, Object>
        └── 存储非流式数据（如诊断结果、意图标签等）
```

> **设计要点**：与旧版设计不同，节点间不再通过 `upstream Flux` 参数显式传递数据流，而是通过 `OutputContext` 的 `fluxStore` / `dataStore` 作为共享状态池隐式通信。每个节点从 context 中按 key 读取上游产出，将自己的产出写入 context 供下游消费。

---

## 7. 标准消息契约

> **注意**：当前简化版设计中，节点间通过 `OutputContext` 的 `fluxStore` / `dataStore` 以 key-value 方式隐式通信，不再需要显式的 `TextChunk` / `AudioChunk` 消息契约。以下内容保留作为未来扩展参考——当需要传递富元数据（情绪标签、语速建议等）时，可重新引入契约结构。

### 7.1 为什么需要标准契约？（可选扩展）

当节点间需要传递富元数据时（如 ModelNode 告诉 TtsNode 当前文本的情绪标签以调整音色），可以使用标准契约替代隐式类型约定。

### 7.2 TextChunk（文本块契约，可选扩展）

```java
public class TextChunk {
    private final String text;
    private final int sequenceIndex;
    private final boolean isLast;
    private final Map<String, Object> metadata;
}
```

### 7.3 AudioChunk（音频块契约，可选扩展）

```java
public class AudioChunk {
    private final byte[] audioData;
    private final int sequenceIndex;
    private final boolean isLast;
    private final AudioFormat format;
    private final Map<String, Object> metadata;
}
```

---

## 8. 具体节点实现

### 8.1 节点概览

| 节点 | OutputContext 所需数据 | 写入 fluxStore 的 key | 写入 dataStore 的 key | 内部依赖 |
|------|----------------------|----------------------|----------------------|---------|
| `ModelChatOutputNode` | temporaryMessages, historyMessages, emotionAnalysis, diagnosisAnalysis, aiNodeConfig, compressedSummary | `"model_output"` (Flux\<String\>) | — | ChatModelFactory, TextMessageProcessorModel |
| `TtsOutputNode` | 从 fluxStore 读取 `"model_output"` | `"tts_output"` (Flux\<byte[]\>) | — | TtsConnectionManager |

> **设计要点**：各节点通过 `context.getFlux(key, type)` / `context.putFlux(key, flux)` / `context.getData(key, type)` / `context.putData(key, value)` 读写共享状态池，不再通过方法参数传递。

### 8.2 ModelNode

```java
/**
 * 大语言模型节点
 * <p>职责：从 OutputContext 提取多类型上下文 → 构建 Prompt → LLM 流式推理 → 将产出写入 context.fluxStore</p>
 */
class ModelNode implements OutputNode {

    private final ChatModelFactory chatModelFactory;
    private final TextMessageProcessorModel processorModel;
    private final ConversationHistoryMessagesStorage historyMessagesStorage;
    private final ConversationStreamHolder streamHolder;
    private final ConversationWebSocketManager webSocketManager;

    private Long conversationId;
    private Long userId;

    @Override
    public String getNodeName() { return "ModelNode"; }

    @Override
    public void init(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    @Override
    public void process(OutputContext context) {
        // 1. 从 OutputContext.dataStore 中提取需要的数据
        List<StandardMessage> tempMessages = context.getData("temporaryMessages", List.class);
        List<ConversationMemory> history = context.getData("historyMessages", List.class);
        EmotionAnalysisResult emotion = context.getData("emotionAnalysis", EmotionAnalysisResult.class);
        DiagnosisResult diagnosis = context.getData("diagnosisAnalysis", DiagnosisResult.class);
        AiNode aiNodeConfig = context.getData("aiNodeConfig", AiNode.class);
        String summary = context.getData("compressedSummary", String.class);

        // 2. 通知前端 AI 开始思考
        webSocketManager.sendStatus(userId, conversationId, "thinking");

        // 3. 构建 Prompt
        String prompt = buildPrompt(tempMessages, history, emotion, diagnosis, summary, aiNodeConfig);

        // 4. 创建 Sinks 桥接 LLM 回调 → Flux
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 5. 调用 LLM 流式推理
        ChatModel chatModel = chatModelFactory.getOrCreateChatModel(aiNodeConfig);
        processorModel.stream(chatModel, prompt, new StreamEventListener() {
            @Override
            public void onContentChunk(String textChunk) {
                sink.tryEmitNext(textChunk);
            }

            @Override
            public void onModelComplete(AssistantMessage message) {
                sink.tryEmitComplete();
            }

            @Override
            public void onError(Throwable error) {
                sink.tryEmitError(error);
            }
        });

        // 6. 根据 outputTypes 决定是否附加推送副作用
        Flux<String> textFlux = sink.asFlux();

        if (context.hasOutputType(OutputDataType.TEXT)) {
            textFlux = textFlux
                    .doOnNext(chunk -> webSocketManager.sendTextStream(userId, conversationId, chunk))
                    .doOnComplete(() -> historyMessagesStorage.saveAssistantMessage(conversationId, /* 完整文本 */));
        }

        // 7. 将产出写入 context.fluxStore（下游 TtsNode 按 key 获取）
        context.putFlux("model_output", textFlux);
    }

    @Override
    public void destroy() {
        streamHolder.cancelStream(conversationId);
    }

    @Override
    public void interrupt() {
        streamHolder.cancelStream(conversationId);
    }
    
    @Override
    public List<NodeEndpoint> getSocketInfo() {
        return List.of(new NodeEndpoint("text-stream", "/ws/conversation/" + conversationId + "/text"));
    }
}
```

### 8.3 TtsNode

```java
/**
 * 文本转语音节点
 * <p>职责：从 context.fluxStore 获取上游文本流 → TTS 合成 → 将音频流写入 context.fluxStore</p>
 */
class TtsNode implements OutputNode {

    private final TtsConnectionManager ttsConnectionManager;
    private final ConversationWebSocketManager webSocketManager;
    private Long userId;
    private Long conversationId;
    private Sinks.Many<byte[]> audioSink;

    @Override
    public String getNodeName() { return "TtsNode"; }

    @Override
    public void init(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.audioSink = Sinks.many().unicast().onBackpressureBuffer();
        // 注册 TTS 连接
        ttsConnectionManager.register(userId, new TtsResultCallback() {
            @Override
            public void onAudioData(byte[] audioData) {
                audioSink.tryEmitNext(audioData);
            }

            @Override
            public void onSynthesisComplete() {
                audioSink.tryEmitComplete();
            }

            @Override
            public void onFail(String taskId, String statusText) {
                audioSink.tryEmitError(new TtsException(taskId, statusText));
            }
        });
    }

    @Override
    public void process(OutputContext context) {
        // 1. 从 context.fluxStore 获取上游节点产出的文本流
        Flux<String> textFlux = context.getFlux("model_output", String.class);

        // 2. 订阅文本流 → 发送到 TTS 引擎
        textFlux.subscribe(
            text -> ttsConnectionManager.sendTextSegment(userId, text),
            error -> audioSink.tryEmitError(error),
            () -> ttsConnectionManager.finishSynthesis(userId)
        );

        // 3. 根据 outputTypes 决定是否附加音频推送副作用
        Flux<byte[]> audioFlux = audioSink.asFlux();

        if (context.hasOutputType(OutputDataType.AUDIO)) {
            audioFlux = audioFlux
                    .doOnNext(chunk -> webSocketManager.sendAudioBinary(userId, conversationId, chunk));
        }

        // 4. 将产出写入 context.fluxStore
        context.putFlux("tts_output", audioFlux);
    }

    @Override
    public void destroy() {
        ttsConnectionManager.cancel(userId);
    }

    @Override
    public void interrupt() {
        ttsConnectionManager.interrupt(userId);
    }
    
    @Override
    public List<NodeEndpoint> getSocketInfo() {
        return List.of(new NodeEndpoint("audio-stream", "/ws/conversation/" + conversationId + "/audio"));
    }
}
```

---

## 9. OutputPipeline 管道设计

### 9.1 OutputPipeline 类

```java
/**
 * 输出层管道——管理多个 OutputNode 的有序组合
 *
 * <p>每个节点通过 OutputContext 的 fluxStore / dataStore 共享状态池进行隐式通信。
 * Pipeline 按序调用各节点的 process()，节点从 context 读取上游产出、将自身产出写入 context。
 * 副作用（WebSocket 推送、数据库存储等）由各节点在 process() 内部自行处理。</p>
 */
public class OutputPipeline {

    /** 有序的处理节点列表 */
    private final List<OutputNode> nodes;

    /** 所属会话ID */
    private final Long conversationId;

    /** 所属用户ID */
    private final Long userId;
    
    public OutputPipeline(List<OutputNode> nodes, Long conversationId, Long userId) {
        this.nodes = new ArrayList<>(nodes);
        this.conversationId = conversationId;
        this.userId = userId;
    }

    // ==================== 生命周期 ====================

    public void init() {
        for (OutputNode node : nodes) {
            node.init(conversationId, userId);
        }
    }

    /** 逆序销毁：先关下游再关上游 */
    public void destroy() {
        List<OutputNode> reversed = new ArrayList<>(nodes);
        Collections.reverse(reversed);
        for (OutputNode node : reversed) {
            try {
                node.destroy();
            } catch (Exception e) {
                log.error("[管道] 节点销毁失败: {}", node.getNodeName(), e);
            }
        }
    }

    public void interrupt() {
        for (OutputNode node : nodes) {
            try {
                node.interrupt();
            } catch (Exception e) {
                log.error("[管道] 节点中断失败: {}", node.getNodeName(), e);
            }
        }
    }

    // ==================== 核心执行 ====================

    /**
     * 执行输出管道
     * <pre>
     * 执行流程：
     *   1. 按节点列表顺序依次调用 node.process(context)
     *   2. 每个节点从 OutputContext 中读取上游产出的数据
     *   3. 每个节点将自身产出的 Flux/数据写入 OutputContext
     *   4. 下游节点通过 OutputContext 获取上游产出，形成隐式数据流
     * </pre>
     *
     * @param context  时间轮触发时编排器装配的上下文数据包
     */
    public void execute(OutputContext context) {
        if (nodes.isEmpty()) {
            return;
        }

        for (OutputNode node : nodes) {
            log.debug("[管道] 节点开始执行: {}", node.getNodeName());
            node.process(context);
        }
    }
    
    // ================ Socket 路径与说明 ===============

    /**
     * 获取管道中所有节点的 Socket 路径和说明信息
     * @return NodeEndpoint 包含Socket路径 + 说明
     */
    public List<NodeEndpoint> getSocketInfo() {
        List<NodeEndpoint> list = new ArrayList<>();
        for (OutputNode node : nodes) {
            list.addAll(node.getSocketInfo());
        }
        return list;
    }
}
```

### 9.2 数据流完整示例（TEXT → AUDIO）

```
OutputContext (包含: dataStore={temporaryMessages, historyMessages, emotionAnalysis, ...},
               outputTypes={TEXT, AUDIO})
    │
    ▼
═══════════════════════════════════════════════════════════════════
  [ModelNode].process(context)
     │
     │ 从 context.dataStore 提取: temporaryMessages + history + emotion + ...
     │ 构建 Prompt → ChatModel.stream()
     │
     │ 副作用由节点自行处理（根据 outputTypes 判断）：
     │   if (context.hasOutputType(TEXT)):
     │     .doOnNext(chunk → wsManager.sendTextStream())
     │     .doOnComplete(() → messageStorage.saveReply())
     │
     │ 写入 context.fluxStore:
     │   context.putFlux("model_output", textFlux)
     │
═══════════════════════════════════════════════════════════════════
  [TtsNode].process(context)
     │
     │ 从 context.fluxStore 读取:
     │   context.getFlux("model_output", String.class)
     │
     │ 订阅文本流 → 发送到 TTS 引擎
     │   textFlux.subscribe(
     │       text → ttsManager.sendTextSegment(userId, text),
     │       error → audioSink.tryEmitError(error),
     │       () → ttsManager.finishSynthesis(userId)
     │   )
     │
     │ 副作用由节点自行处理（根据 outputTypes 判断）：
     │   if (context.hasOutputType(AUDIO)):
     │     .doOnNext(chunk → wsManager.sendAudioBinary())
     │
     │ 写入 context.fluxStore:
     │   context.putFlux("tts_output", audioFlux)
     │
═══════════════════════════════════════════════════════════════════
```

> **与旧版的关键区别**：
> - 旧版：Pipeline 通过 `upstream = result.getMainOutput()` 显式传递数据流，使用 `ProcessingResult` 包装
> - 新版：节点通过 `context.putFlux(key, flux)` / `context.getFlux(key, type)` 读写共享状态池，Pipeline 只负责按序调度，`execute()` 返回 `void`

---

## 10. OutputPipelineFactory 管道组装工厂

### 10.1 定位

`OutputPipelineFactory` 根据 `outputTypes` 组装节点链，产出 `OutputPipeline` 实例。

### 10.2 完整实现

```java
/**
 * 输出管道组装工厂
 *
 * <p>职责：
 * <ul>
 *   <li>根据 outputTypes 组装节点链（ModelNode、TtsNode 等）</li>
 *   <li>产出可执行的 OutputPipeline 实例</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OutputPipelineFactory {

    private final ModelNode modelNode;
    private final TtsNode ttsNode;

    // ==================== 组装规则 ====================

    /**
     * 根据输出模式集合组装管道
     *
     * @param outputTypes    前端请求的输出模式集合（如 {TEXT, AUDIO}）
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 组装好的 OutputPipeline
     */
    public OutputPipeline createPipeline(Set<OutputDataType> outputTypes,
                                         Long conversationId, Long userId) {
        List<OutputNode> nodeChain = buildNodeChain(outputTypes);
        return new OutputPipeline(nodeChain, conversationId, userId);
    }

    /**
     * 根据 outputTypes 组装节点链
     *
     * <p>组装规则：
     * <pre>
     * {TEXT}        → [ModelNode]
     * {AUDIO}       → [ModelNode, TtsNode]
     * {TEXT, AUDIO} → [ModelNode, TtsNode]    ← 与纯音频相同的节点链，区别在节点内判断
     * </pre>
     */
    private List<OutputNode> buildNodeChain(Set<OutputDataType> outputTypes) {
        boolean needAudio = outputTypes.contains(OutputDataType.AUDIO);

        if (needAudio) {
            return List.of(modelNode, ttsNode);
        } else {
            return List.of(modelNode);
        }
    }
}
```

> **注意**：`createPipeline` 不再在内部调用 `pipeline.init()`。节点初始化由 `OutputPipelineSessionManager.initSession()` 统一调用 `pipeline.init()`，职责更清晰。

---

## 11. OutputPipelineSessionManager 会话管道管理器

### 11.1 定位

`OutputPipelineSessionManager` 是输出层的**集中统一管理器**，以 `conversationId` 为 key 持有所有会话的 `OutputPipeline` 实例。

对应你的流程：
- **资源初始化**："将管道与会话ID进行绑定，放入一个Map中" → `initSession()`
- **对话处理**："根据会话ID从Map中获取管道信息" → `getPipeline()`
- **中断/销毁**："根据会话ID获取管道数据，然后中断/销毁" → `interrupt()` / `endSession()`

### 11.2 完整实现

```java
/**
 * 输出层会话管道管理器
 *
 * <p>纯容器——以 conversationId 为 key 持有 OutputPipeline 实例，
 * 提供注册、获取、中断、销毁的统一入口。</p>
 */
@Component
@RequiredArgsConstructor
public class OutputPipelineSessionManager {

    /**
     * conversationId → 该会话绑定的 OutputPipeline
     *
     * <p>一个会话只关联一个管道，管道内部包含有序的节点列表</p>
     */
    private final ConcurrentHashMap<Long, OutputPipeline> sessionPipelines = new ConcurrentHashMap<>();

    private final OutputPipelineFactory pipelineFactory;

    /**
     * 初始化会话——组装管道、校验类型、初始化节点资源、绑定到会话
     *
     * @param conversationId 会话ID（Map 的 key）
     * @param userId         用户ID
     * @param outputTypes    前端请求的输出模式集合
     */
    public void initSession(Long conversationId, Long userId, Set<OutputDataType> outputTypes) {
        // 先销毁旧管道（如果存在）
        OutputPipeline old = sessionPipelines.remove(conversationId);
        if (old != null) {
            old.destroy();
        }
        
        // 1. Factory 组装节点链 → 产出 Pipeline
        OutputPipeline pipeline = pipelineFactory.createPipeline(outputTypes, conversationId, userId);
        
        // 2. 初始化各节点资源
        pipeline.init(); 

        // 3. 绑定到会话
        sessionPipelines.put(conversationId, pipeline);
    }

    /**
     * 获取会话管道——供时间轮触发后执行
     */
    public OutputPipeline getPipeline(Long conversationId) {
        OutputPipeline pipeline = sessionPipelines.get(conversationId);
        if (pipeline == null) {
            throw new IllegalStateException("会话管道不存在: " + conversationId);
        }
        return pipeline;
    }

    /**
     * 中断会话管道——中断所有节点的当前操作（不销毁）
     */
    public void interrupt(Long conversationId) {
        OutputPipeline pipeline = sessionPipelines.get(conversationId);
        if (pipeline != null) {
            pipeline.interrupt();
        }
    }

    /**
     * 销毁会话管道——销毁所有节点资源，从 Map 中移除
     */
    public void endSession(Long conversationId) {
        OutputPipeline pipeline = sessionPipelines.remove(conversationId);
        if (pipeline != null) {
            pipeline.destroy();
        }
    }
    
    /**
     * 构建客户端所需的 Socket 端点路径
     * <p>客户端根据返回的路径，直接连接对应的 WebSocket 端点收发数据</p>
     */
    public List<NodeEndpoint> buildEndpointInfo(Long conversationId) {
        OutputPipeline pipeline = sessionPipelines.get(conversationId);
        if (pipeline == null) {
            throw new IllegalStateException("会话管道不存在: " + conversationId);
        }
        return pipeline.getSocketInfo();
    }
}
```

### 11.3 NodeEndpoint 类

```java
/**
 * 节点端点信息——返回给客户端的路径描述
 */
public class NodeEndpoint {
    /** 端点用途标识（如 "text-stream", "audio-stream"） */
    private final String name;
    /** WebSocket 路径 */
    private final String path;
}
```

> **设计要点**：`buildEndpointInfo` 不再根据 `outputTypes` 硬编码构建路径，而是委托给 `pipeline.getSocketInfo()`，由各节点自行声明其 Socket 路径，更符合"节点自治"原则。

### 11.4 在整体流程中的位置

```
initSession(inputType, outputTypes)
    │
    ├── 输入层：inputSessionManager.register(conversationId, adapter)
    │
    └── 输出层：outputPipelineSessionManager.initSession(conversationId, userId, outputTypes)
        ├── 先移除旧管道（如果存在）并销毁
        ├── pipelineFactory.createPipeline(outputTypes, cid, uid)
        │   ├── buildNodeChain(outputTypes)     → [ModelNode] 或 [ModelNode, TtsNode]
        │   └── new OutputPipeline(nodes, cid, uid)
        ├── pipeline.init()                      → 各节点 init()
        └── sessionPipelines.put(conversationId, pipeline)

... 返回 InitSessionResponse（含各端点路径，由 pipeline.getSocketInfo() 生成） ...

============ 时间轮触发 ============

ConversationMessageProcessor.processConversationMessage(convId)
    │
    ├── 装配 OutputContext（从 DB/缓存 聚合数据到 dataStore）
    │
    └── outputPipelineSessionManager.getPipeline(convId)
        └── pipeline.execute(context)        ← 执行管道
            ├── [ModelNode].process(context)
            │   ├── .doOnNext → wsManager.sendTextStream()  ← 文本推前端
            │   ├── .doOnComplete → messageStorage.saveReply() ← 存 DB
            │   └── context.putFlux("model_output", textFlux)
            └── [TtsNode].process(context)
                ├── context.getFlux("model_output", String.class)
                └── context.putFlux("tts_output", audioFlux)

============ 中断 ============

interrupt(conversationId)
    ├── inputSessionManager.interrupt(conversationId)
    └── outputPipelineSessionManager.interrupt(conversationId)
        └── pipeline.interrupt() → 各节点 interrupt()

============ 销毁 ============

endSession(conversationId)
    ├── inputSessionManager.endSession(conversationId)
    └── outputPipelineSessionManager.endSession(conversationId)
        └── pipeline.destroy() → 逆序调用各节点 destroy()
```

---

## 12. OutputContext 上下文数据包

### 12.1 定义

```java
/**
 * 输出上下文——贯穿整个管道执行过程的状态池
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>fluxStore</b>：存储各节点产出的 Flux 流，key 为节点产出标识（如 "model_output"）</li>
 *   <li><b>dataStore</b>：存储非流式数据（诊断结果、意图标签、历史消息等），key 为数据标识</li>
 *   <li><b>outputTypes</b>：前端请求的输出模式集合，各节点据此决定是否附加推送副作用</li>
 * </ul>
 *
 * <p>数据来源（在时间轮触发时由编排器装配到 dataStore）：
 * <ul>
 *   <li>"temporaryMessages" — 消息表（ConversationHistoryMessagesStorage）</li>
 *   <li>"historyMessages" — Redis 缓存（ConversationCacheManager）</li>
 *   <li>"emotionAnalysis" — Redis 缓存</li>
 *   <li>"diagnosisAnalysis" — Redis 缓存</li>
 *   <li>"compressedSummary" — Redis 缓存</li>
 *   <li>"aiNodeConfig" — AiNodeConfigManager</li>
 * </ul>
 */
public class OutputContext {

    private final Long userId;
    private final Long conversationId;

    /**
     * 前端请求的输出模式集合
     *
     * <p>各节点根据此字段判断是否附加对应的推送副作用：
     */
    private final Set<OutputDataType> outputTypes;

    // ==================== 流式数据池 ====================

    /**
     * Flux 数据池——存储各节点产出的 Flux 流
     *
     * <p>所有存入的 Flux 必须先调用 {@code .cache()}，原因：
     * 多个下游节点可能订阅同一个 Flux，不 cache 会导致数据源被重复触发</p>
     */
    private final ConcurrentHashMap<String, Flux<?>> fluxStore = new ConcurrentHashMap<>();

    // ==================== 非流式数据池 ====================

    /**
     * 非流式数据池——存储其他类型、格式的数据
     */
    private final ConcurrentHashMap<String, Object> dataStore = new ConcurrentHashMap<>();

    // ==================== 构造 & 便捷方法 ====================

    public OutputContext(Long userId, Long conversationId, Set<OutputDataType> outputTypes) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.outputTypes = outputTypes;
    }

    public Long getUserId() { return userId; }
    public Long getConversationId() { return conversationId; }

    /**
     * 便捷方法：判断是否包含指定输出模式
     */
    public boolean hasOutputType(OutputDataType type) {
        return outputTypes != null && outputTypes.contains(type);
    }

    // ==================== Flux 存取 ====================

    /**
     * 存入 Flux 流
     *
     * <p>存入时对 flux 调用 .cache()，防止多订阅者重复触发数据源</p>
     */
    public void putFlux(String key, Flux<?> flux) {
        Flux<?> cachedFlux = flux.cache();
        fluxStore.put(key, cachedFlux);
    }

    /**
     * 获取 Flux 流并按元素类型安全转换
     * @param key         产出标识
     * @param elementType 期望的流元素类型
     * @return 强转后的 Flux（key 不存在时返回空 Flux）
     */
    public <T> Flux<T> getFlux(String key, Class<T> elementType) {
        Flux<?> flux = fluxStore.get(key);
        if (flux == null) {
            return Flux.empty();
        }
        return flux.cast(elementType);
    }

    // ==================== 非流式数据存取 ====================

    /** 存入任意类型数据 */
    public void putData(String key, Object value) {
        dataStore.put(key, value);
    }

    /** 获取数据并按类型强转 */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = dataStore.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    // ==================== 供 Pipeline 使用 ====================

    /**
     * 获取 fluxStore 中所有的 Flux——供 Pipeline.execute() 统一订阅触发
     */
    public Collection<Flux<?>> getAllFluxes() {
        return new ArrayList<>(fluxStore.values());
    }
}
```

### 12.2 装配时机

由 `ConversationMessageProcessor`（编排器）在时间轮触发时装配：

```java
// 在 ConversationMessageProcessor.processConversationMessage() 中
OutputContext context = new OutputContext(userId, conversationId, outputTypes);

// 将聚合数据放入 dataStore
context.putData("temporaryMessages", loadTemporaryMessages(conversationId));
context.putData("historyMessages", cacheManager.getHistoryMessages(conversationId));
context.putData("compressedSummary", cacheManager.getCompressedSummary(conversationId));
context.putData("emotionAnalysis", cacheManager.getEmotionAnalysis(conversationId));
context.putData("diagnosisAnalysis", cacheManager.getDiagnosisAnalysis(conversationId));
context.putData("aiNodeConfig", aiNodeConfigManager.getConfigWithLoad(conversationId));

// 传给输出层管道执行
OutputPipeline pipeline = pipelineSessionManager.getPipeline(conversationId);
pipeline.execute(context);
```

> **设计要点**：与旧版不同，OutputContext 不再为每种数据类型定义独立的强类型字段（如 `getTemporaryMessages()`、`getEmotionAnalysis()` 等），而是使用通用的 `ConcurrentHashMap<String, Object>` dataStore 和 `ConcurrentHashMap<String, Flux<?>>` fluxStore。这提供了更好的扩展性——新增数据类型只需约定新的 key，无需修改 OutputContext 类本身。

---

## 13. ProcessingResult：处理结果（已移除）

> **注意**：当前简化版设计中，`OutputNode.process()` 返回 `void`，节点间通过 `OutputContext` 的 `fluxStore` / `dataStore` 隐式通信，不再需要 `ProcessingResult` 包装类。以下内容仅作为旧版参考。

<details>
<summary>旧版 ProcessingResult 定义（点击展开）</summary>

```java
/**
 * 节点的处理结果——包装主数据流
 *
 * <p>各节点在 process() 内部通过 Flux 操作符自行附加副作用
 * （WebSocket 推送、数据库存储等），ProcessingResult 仅承载主数据流供下游消费。</p>
 */
public class ProcessingResult {

    /** 主数据流——给下一个节点（或外部订阅者）消费 */
    private final Flux<?> mainOutput;

    private ProcessingResult(Flux<?> mainOutput) {
        this.mainOutput = mainOutput;
    }

    public static ProcessingResult of(Flux<?> mainOutput) {
        return new ProcessingResult(mainOutput);
    }

    public Flux<?> getMainOutput() {
        return mainOutput;
    }
}
```

</details>

### 新版替代方式

在简化后的设计中，节点产出直接写入 context：

```java
// 旧版
ProcessingResult result = node.process(context, upstream);
Flux<?> mainOutput = result.getMainOutput();

// 新版
node.process(context);                    // void，产出已写入 context
Flux<String> output = context.getFlux("model_output", String.class);  // 按 key 获取
```

---

## 14. IOMode 输入输出模式（已移除）

> **注意**：当前简化版设计中，不再区分 `IOMode`（BATCH_IN_STREAM_OUT / STREAM_IN_STREAM_OUT 等）。所有节点的输入输出统一通过 `OutputContext` 的 `fluxStore` / `dataStore` 进行，`process()` 签名为 `void process(OutputContext context)`。节点内部自行决定从 dataStore（批量）还是 fluxStore（流式）读取数据。

<details>
<summary>旧版 IOMode 枚举（点击展开）</summary>

```java
public enum IOMode {
    /** 流式输入 → 流式输出：如 TtsNode */
    STREAM_IN_STREAM_OUT,

    /** 批量输入 → 流式输出：如 ModelNode（接收上下文文本，产出流式回复） */
    BATCH_IN_STREAM_OUT,

    /** 批量输入 → 批量输出：如 SensitiveWordFilterNode */
    BATCH_IN_BATCH_OUT,

    /** 流式输入 → 批量输出：如 TextAggregatorNode */
    STREAM_IN_BATCH_OUT
}
```

| 节点 | 输入来源 | 输出方式 | IOMode |
|------|---------|---------|--------|
| `ModelChatOutputNode` | OutputContext（批量文本数据） | Flux<TextChunk> | BATCH_IN_STREAM_OUT |
| `TtsOutputNode` | upstream Flux<TextChunk> | Flux<AudioChunk> | STREAM_IN_STREAM_OUT |

</details>

---

## 15. 多输出模式设计

### 15.1 问题背景

在前端场景中，用户可能同时请求多种输出：
- 请求 `{TEXT, AUDIO}`：期望同时收到流式文本和流式语音
- Model 调用产生的文本既要推送给前端（文本弹幕），又要喂给 TTS 引擎合成语音

### 15.2 设计思路：节点自判断，管道不变

**不做**的事情：
- ❌ 把管道从线性链改成 DAG——过度设计
- ❌ 为每个输出类型创建独立的管道——浪费资源（Model 会调用多次）

**做**的事情：
- ✅ 将 `outputTypes`（前端请求的输出模式集合）传入 `OutputContext`
- ✅ 每个节点在 `process()` 中自行检查 `context.hasOutputType()`，决定是否附加副作用
- ✅ 管道保持线性链不变，结构不变

### 15.3 核心机制

```
前端请求: outputTypes = {TEXT, AUDIO}
              │
              ▼
    OutputPipeline (outputTypes 通过 OutputContext 透传)
              │
    ┌─────────┴──────────┐
    ▼                    ▼
ModelNode              TtsNode
  │                      │
  ├── 检查: TEXT ∈ outputTypes?
  │   YES → 附加 .doOnNext(wsManager.sendTextStream)
  │   NO  → 跳过文本推送
  │                      │
  │   context.putFlux    │   context.getFlux
  │   ("model_output") ──→ ("model_output", String.class)
  │                      │
  │                      ├── 检查: AUDIO ∈ outputTypes?
  │                      │   YES → 附加 .doOnNext(wsManager.sendAudioBinary)
  │                      │   NO  → 跳过音频推送
  │                      │
  ▼                      ▼
写入 fluxStore          写入 fluxStore
```

**关键点**：
- ModelNode 始终生成文本流写入 `context.putFlux("model_output", ...)`，文本推送是"可选的附加操作"
- TtsNode 始终从 context 读取并消费文本流，音频推送是"可选的附加操作"
- 当 `outputTypes = {AUDIO}` 时：ModelNode 不推文本，TtsNode 推音频
- 当 `outputTypes = {TEXT, AUDIO}` 时：两个节点都附加推送副作用
- 当 `outputTypes = {TEXT}` 时：管道链为 `[ModelNode]`，只推文本

### 15.4 条件判断代码示例

**ModelNode 中：**
```java
Flux<String> textFlux = sink.asFlux();

if (context.hasOutputType(OutputDataType.TEXT)) {
    textFlux = textFlux
            .doOnNext(chunk -> webSocketManager.sendTextStream(userId, conversationId, chunk))
            .doOnComplete(() -> historyMessagesStorage.saveAssistantMessage(conversationId, fullText));
}

context.putFlux("model_output", textFlux);
```

**TtsNode 中：**
```java
Flux<byte[]> audioFlux = audioSink.asFlux();

if (context.hasOutputType(OutputDataType.AUDIO)) {
    audioFlux = audioFlux
            .doOnNext(chunk -> webSocketManager.sendAudioBinary(userId, conversationId, chunk));
}

context.putFlux("tts_output", audioFlux);
```

### 15.5 组装规则

| outputTypes | 节点链 | 文本推送（ModelNode） | 音频推送（TtsNode） |
|-------------|--------|----------------------|---------------------|
| `{TEXT}` | `[ModelNode]` | ✅ 推 | — |
| `{AUDIO}` | `[ModelNode, TtsNode]` | ❌ 不推 | ✅ 推 |
| `{TEXT, AUDIO}` | `[ModelNode, TtsNode]` | ✅ 推 | ✅ 推 |

---

## 16. 与现有架构的融合

### 16.1 现有架构

```
ConversationAggregateScheduler (时间轮)
    │
    └── 触发 → ConversationMessageProcessor.processConversationMessage()
                    │
                    ├── 获取令牌
                    ├── 加载缓存
                    ├── 查询未处理消息
                    ├── 构建上下文
                    ├── 路由到 MessageProcessor
                    │   ├── TextMessageProcessor   ("text" 模式)
                    │   └── VoiceMessageProcessor  ("audio" 模式)
                    ├── 异步语义压缩
                    ├── 异步分析与诊断
                    ├── 更新数据库状态
                    └── 刷新缓存
```

### 16.2 目标架构（输出层部分）

```
ConversationAggregateScheduler (不变)
    │
    └── 触发 → ConversationMessageProcessor.processConversationMessage()
                    │
                    ├── ... 前置步骤不变 ...
                    │
                    ├── 装配 OutputContext（聚合数据到 dataStore）
                    │   ├── context.putData("temporaryMessages", ...)
                    │   ├── context.putData("historyMessages", ...)
                    │   └── context.putData("emotionAnalysis", ...)
                    │
                    ├── 路由到 OutputPipeline
                    │   └── outputPipelineSessionManager.getPipeline(convId)
                    │       └── OutputPipeline.execute(context)
                    │           ├── [ModelNode].process(context)
                    │           │   ├── 从 context.dataStore 取数据
                    │           │   ├── if (context.hasOutputType(TEXT)):
                    │           │   │     .doOnNext → wsManager.sendTextStream() ← 文本弹幕
                    │           │   │     .doOnComplete → messageStorage.saveReply()
                    │           │   └── context.putFlux("model_output", textFlux)
                    │           └── [TtsNode].process(context)          ← 仅当 AUDIO
                    │               ├── context.getFlux("model_output", String.class)
                    │               ├── if (context.hasOutputType(AUDIO)):
                    │               │     .doOnNext → wsManager.sendAudioBinary() ← 音频播放
                    │               └── context.putFlux("tts_output", audioFlux)
                    │
                    ├── ... 后置步骤不变 ...
```

### 16.3 输出层改动点清单

| 模块 | 改动内容 | 改动程度 |
|------|---------|---------|
| **新增** `OutputDataType` 枚举 | 输出数据类型定义（TEXT / AUDIO） | 新增 |
| **新增** `OutputContext` 类 | 共享状态池（fluxStore + dataStore + outputTypes） | 新增 |
| **新增** `OutputNode` 接口 | 输出节点抽象（process 返回 void，含 getSocketInfo） | 新增 |
| **新增** `NodeEndpoint` 类 | WebSocket 端点信息 | 新增 |
| **新增** `OutputPipeline` 类 | 输出管道容器（按序调度，execute 返回 void） | 新增 |
| **新增** `OutputPipelineFactory` | 输出管道组装工厂（@Component，按 outputTypes 组装） | 新增 |
| **新增** `OutputPipelineSessionManager` | 会话级输出管道管理（含 buildEndpointInfo） | 新增 |
| **改** `ModelChatOutputNode` | 从 `TextMessageProcessor` 提取核心逻辑，增加 `hasOutputType(TEXT)` 条件判断，通过 `context.putFlux/getFlux` 通信 | 改造 |
| **改** `TtsOutputNode` | 从 `ChatMessageProcessor` 提取 TTS 逻辑，增加 `hasOutputType(AUDIO)` 条件判断，通过 `context.getFlux/putFlux` 通信 | 改造 |
| **废弃** `TextMessageProcessor` | 被 OutputPipeline + ModelNode 替代 | 最终废弃 |
| **废弃** `ChatMessageProcessor` | 被 OutputPipeline + ModelNode + TtsNode 替代 | 最终废弃 |
| **废弃** `ProcessingResult` | 不再需要，process() 返回 void | 移除 |
| **废弃** `IOMode` | 不再需要，统一通过 context 的 fluxStore/dataStore 读写 | 移除 |
| **不变** `TtsConnectionManager` | 调用方从 VoiceMessageProcessor → TtsNode | 不变 |
| **不变** `ConversationCacheManager` | 仅 OutputContext 装配时读取 | 不变 |
| **不变** `ConversationWebSocketManager` | 在节点 `doOnNext`/`doOnComplete` 中调用 | 不变 |
| **不变** `ConversationAggregateScheduler` | 时间轮调度逻辑不变 | 不变 |

---

## 17. 生命周期管理

### 17.1 当前模式

```
initSession() {
    createNewAudioConversation()
    └── initializeAudioResources()
        ├── asrConnectionManager.register()     ←─── 总是创建
        └── ttsConnectionManager.register()     ←─── 总是创建
}

endSession() {
    interruptAudio()
    asrConnectionManager.cancel()    ←─── 总是销毁
    ttsConnectionManager.cancel()    ←─── 总是销毁
    aggregateScheduler.cancelTask()
}
```

### 17.2 三层模式（输出层部分）

```
initSession(inputType, outputType) {
    // [输入层部分见 input-layer-adapter-design.md]

    // 输出层：按需构建 OutputPipeline
    OutputPipeline pipeline = outputPipelineFactory.createPipeline(outputType, convId, userId)
    pipeline.init()
        ├── always → ModelNode.init()    (不需要创建连接)
        └── if outputType=AUDIO → TtsNode.init() → ttsConnectionManager.register()
}

endSession(conversationId) {
    // [输入层清理见 input-layer-adapter-design.md]

    // 输出层清理
    pipelineSessionManager.destroyPipeline(conversationId)
        └── OutputPipeline.destroy()
            ├── if TtsNode → ttsConnectionManager.cancel(userId)
            └── always → ModelNode.destroy()
}
```

### 17.3 输出模式切换

```java
// 从 TEXT→TEXT 切换到 TEXT→AUDIO
// 只需切换输出层管道
pipelineSessionManager.destroyPipeline(conversationId);  // 销毁旧管道 [ModelNode]
pipelineSessionManager.initPipeline(OutputDataType.AUDIO, conversationId, userId);
// 新管道 [ModelNode, TtsNode]
```

---

## 18. 中断信号传播机制

### 18.1 管道中断传播

```java
// OutputPipeline.interrupt()
public void interrupt() {
    for (OutputNode node : nodes) {
        try {
            node.interrupt();
        } catch (Exception e) {
            log.error("[管道] 节点中断失败: {}", node.getNodeName(), e);
        }
    }
}
```

### 18.2 各节点中断实现

```java
// ModelNode.interrupt()
public void interrupt() {
    streamHolder.cancelStream(conversationId);
}

// TtsNode.interrupt()
public void interrupt() {
    ttsConnectionManager.interrupt(userId);
}
```

### 18.3 中断信号流路径

```
用户触发中断（前端按钮 / 新语音检测）
    │
    ▼
AudioServiceImpl.interruptAudio(conversationId)
    │
    └── OutputPipeline.interrupt()
        ├── ModelNode.interrupt()   ← 取消 LLM 流，触发 onInterrupted → 保存部分输出
        └── TtsNode.interrupt()     ← 中断 TTS 合成，保留会话可再次合成
```

---

## 19. 两种实现路径对比

### 19.1 路径 A：渐进式演进（推荐）

**阶段 1：引入 OutputNode 接口和 OutputContext**
- 将 Model/TTS 逻辑抽取为独立 Node，但保持旧 Processor 不变
- 新旧并跑验证

**阶段 2：引入 OutputPipeline 和 PipelineFactory**
- 让 VoiceMessageProcessor 内部通过 OutputPipeline 组合节点
- 替换内部级联逻辑

**阶段 3：引入 InputAdapter**（见 input-layer-adapter-design.md）

**阶段 4：统一到三层架构**
- 用 OutputPipelineSessionManager 替代分散逻辑
- 废弃旧 Processor

### 19.2 路径 B：一次性重构

直接按目标架构实现全部组件，一次性替换。

**风险**：改动量大，回归测试范围广，线上稳定性风险高。

### 19.3 建议

推荐**路径 A**：

1. 线上系统需要保证稳定性
2. `ModelChatOutputNode` / `TtsOutputNode` 的 LLM 流式处理逻辑可先独立抽取验证
3. TTS 连接管理已通过防腐层解耦，Node 化改动范围可控
4. 每步都可独立测试和灰度发布

---

## 20. 扩展性与未来规划

### 20.1 新增输出节点

只需实现新的 `OutputNode`，在 `OutputPipelineFactory` 中注册组装规则：

| 新输出节点 | 输入 | 输出 | 组合示例 |
|-----------|------|------|---------|
| `AvatarAnimationNode` | Flux<TextChunk> | Flux<AnimationFrame> | ModelNode → AvatarAnimationNode |
| `SensitiveWordFilterNode` | String (批量) | FilterResult | 插入到 ModelNode 之前做输入过滤 |
| `TextPolisherNode` | Flux<TextChunk> | Flux<TextChunk> | ModelNode → TextPolisherNode → TtsNode |

### 20.2 模型供应商切换

替换 `ModelChatOutputNode` 内部的 `ChatModelFactory` 实现或配置，不影响任何其他节点。

### 20.3 多模型链式调用

支持在 OutputPipeline 中串联多个 ModelNode：

```
[ModelNode(reasoning)] → [ModelNode(polish)] → [TtsNode]
```

只需在 `OutputPipelineFactory` 中支持更灵活的组装规则即可。

### 20.4 条件分支管道

未来可扩展到条件分支：

```
[ModelNode] → [RouterNode]
                 ├── 普通对话 → [TtsNode]
                 └── 工具调用 → [ToolCallNode] → [ModelNode] → [TtsNode]
```

---

## 21. 与输入层的关系

### 21.1 接口抽象：完全独立

```
输入侧                           输出侧
══════════                      ══════════
InputAdapter 接口                OutputNode 接口
  ├── init()                    ├── getNodeName()
  ├── destroy()                 ├── init() / destroy() / interrupt()
  └── interrupt()               ├── process(OutputContext)
                                └── getSocketInfo()
```

### 21.2 生命周期：完全独立（时间解耦）

```
时间线 ─────────────────────────────────────────────────────────▶

[输入侧生命周期]
  initSession  →  handleInput  →  handleInput  →  ...  →  endSession
                                                              │
[输出侧生命周期]                                                │
                    [时间轮触发]                                │
                    →  createPipeline  →  execute  →  destroy  │
                        可多次触发（每次时间轮到期）
```

### 21.3 实例持有：互不持有

`InputAdapterSessionManager` 和 `OutputPipelineSessionManager` 是两个独立的 Spring Bean，互相不知道对方的存在。

### 21.4 共享的只是"契约"，不是"实现"

| 共享元素 | 本质 | 是否是耦合？ |
|---------|------|------------|
| `OutputDataType` 枚举 | 纯数据定义，无行为 | 健康的共享常量 |
| `StandardMessage` 类 | 纯数据载体 | 存储层契约，输入层生产、输出层消费，通过存储层间接通信 |

### 21.5 可独立验证

| 维度 | 说明 |
|------|------|
| 独立编译 | 输出层代码不 `import` 输入层包（除了共享的 `OutputDataType` / `StandardMessage`） |
| 独立测试 | `ModelChatOutputNode` 的测试 mock `OutputContext`（填充 dataStore + fluxStore）+ `ChatModelFactory`，不需要输入层类 |
| 独立演进 | 新增 `AvatarAnimationNode` 不影响任何输入层适配器 |

---

## 22. 待讨论的开放问题

### 22.1 OutputContext 的装配时机

是由 `ConversationMessageProcessor`（编排器）装配好后传给 OutputPipeline，还是 Pipeline 内部懒加载？

- 方案 A（推荐）：编排器装配。更接近现有架构（编排器已有加载逻辑），Pipeline 更纯粹
- 方案 B：Pipeline 内部懒加载。Pipeline 更自包含，但依赖范围变大

### 22.2 异步操作的失败处理

如果 TtsNode 执行中失败（如 TTS 网络超时），ModelNode 的 `doOnComplete` 中保存回复到数据库的逻辑是否应保证已执行？

- 方案 A（推荐）：`doOnComplete` 在 `doOnNext` 检查后附加，TTS 失败时主流可能未正常 complete，建议关键持久化逻辑放在 ModelNode 自身的 `doOnComplete` 中（不依赖下游）
- 方案 B：Pipeline 在最后一个节点执行前后增加 try-finally，在 finally 中执行管道级别的结束回调

### 22.3 流式背压对前端推送的影响

ModelNode 的 `.doOnNext(chunk → wsManager.sendTextStream())` 随主数据流消费触发。如果 TtsNode 消费 mainOutput 比 ModelNode 生产慢（背压），前端文本弹幕是否也会延迟？

- 默认：是（背压传导）。这保证了"用户看到的文本和听到的语音同步"
- 如果需要解耦：在 ModelNode 中使用 `Flux.publish().refCount(2)` 让 WS 推送和 TTS 消费拥有独立的订阅路径，互不影响

### 22.4 节点实例化策略

节点是每次创建新实例（原型模式），还是复用单例？

- 推荐：**每次时间轮触发时创建新的 Pipeline + Node 实例**。因为每个会话的状态（conversationId、userId、streamHolder）是对话级别的，不适合复用
- 轻量级配置（如 ChatModel 实例）可在节点内部通过 Factory/Manager 获取，不私有化
- 重量级连接（TTS WebSocket）通过 ConnectionManager 管理，Node 只是引用，可以随 Pipeline 一起销毁而不断开底层连接

### 22.5 错误处理策略

管道中某个节点失败时，是否应该中止整个管道？重试机制如何处理？

- `.doOnNext` 中的异常：日志记录，单个 chunk 推送失败不阻断主流
- 主流级错误：Pipeline 中止，通知前端，已执行的 `.doOnComplete` 不回滚
- 外部服务（TTS/LLM）的瞬态故障：由节点内部的 ConnectionManager/Client 负责重试，Pipeline 只感知最终成功/失败

### 22.6 性能监控

每个节点的处理耗时、吞吐量如何监控？可结合现有的 AOP 日志体系，为 `OutputNode.process()` 添加 `NodeExecutionAspect` 进行拦截记录。

---

## 23. 总结

### 核心思路

1. **输出层使用管道模式**：`OutputNode` 接收 `OutputContext`（共享状态池），产出通过 `context.putFlux(key, flux)` / `context.putData(key, value)` 写入上下文。副作用（WebSocket 推送、数据库存储）由各节点在 `process()` 内部自行处理。

2. **共享状态池通信**：节点间不再通过 `upstream Flux` 参数显式传递数据流，而是通过 `OutputContext` 的 `fluxStore` / `dataStore` 作为共享状态池隐式通信。Pipeline 只负责按序调度，`execute()` 返回 `void`。

3. **多输出模式支持**：前端可同时请求多种输出类型（如 `{TEXT, AUDIO}`），各节点根据 `context.hasOutputType()` 自行判断是否附加推送副作用，管道结构保持不变

4. **副作用内聚于节点**：各节点直接通过 `.doOnNext()` / `.doOnComplete()` 附加副作用，Pipeline 不参与副作用编排

5. **接口极简化**：移除了 `ProcessingResult`、`IOMode`、`TextChunk`/`AudioChunk` 契约等中间层，节点接口只保留核心方法

6. **与输入层完全解耦**：通过中间存储层 + 时间轮异步连接

### 输出层核心接口一览

| 接口 | 职责 |
|------|------|
| `OutputNode` | 输出节点抽象，接收 OutputContext，产出写入 context 的 fluxStore/dataStore |
| `OutputPipeline` | 管道容器，管理节点有序执行和生命周期 |
| `OutputPipelineFactory` | 管道组装工厂，按 `Set<OutputDataType>` 组装节点链 |
| `OutputPipelineSessionManager` | 会话级管道管理（创建、获取、销毁、端点信息） |
| `OutputContext` | 共享状态池（fluxStore + dataStore + outputTypes），贯穿管道执行全过程 |
| `NodeEndpoint` | WebSocket 端点路径信息 |

### 与旧版的对比

| 维度 | 旧版设计 | 新版设计 |
|------|---------|---------|
| 节点通信方式 | 显式 upstream Flux 参数 + ProcessingResult 返回值 | 隐式 context.fluxStore/dataStore 共享池 |
| process() 返回值 | `ProcessingResult` | `void` |
| I/O 模式 | `IOMode` 枚举（4 种模式） | 统一，无需区分 |
| 数据契约 | `TextChunk` / `AudioChunk` | 类型安全的 `getFlux(key, Class)` + `cast()` |
| 类型校验 | 构造时 `validateNodeChain()` | 节点按 key 约定自行消费，更灵活 |
| 端点信息 | 由调用方根据 outputTypes 硬编码 | 由节点 `getSocketInfo()` 自治声明 |
| 扩展性 | 新增数据类型需改 OutputContext 类 | 只需约定新 key，不改框架代码 |