# AI 对话管道/过滤器架构设计

> **文档状态**：设计讨论稿（修订版 v2）  
> **创建日期**：2026-09-29  
> **修订日期**：2026-09-29  
> **讨论范围**：解耦输入/输出处理链路，实现可组合的管道架构  
> **v2 主要变更**：明确三层分离架构，输出层节点支持异构输入与多通道副作用输出

---

## 目录

1. [背景与动机](#1-背景与动机)
2. [现状分析与耦合问题](#2-现状分析与耦合问题)
3. [架构总览：三层分离模型](#3-架构总览三层分离模型)
4. [输入层设计（Input Layer）](#4-输入层设计input-layer)
5. [中间存储层设计（Storage Layer）](#5-中间存储层设计storage-layer)
6. [输出层核心抽象设计（Output Layer）](#6-输出层核心抽象设计output-layer)
7. [标准消息契约](#7-标准消息契约)
8. [输出层具体节点实现](#8-输出层具体节点实现)
9. [OutputPipeline 管道设计](#9-outputpipeline-管道设计)
10. [OutputContext 上下文数据包](#10-outputcontext-上下文数据包)
11. [ProcessingResult：主数据流 + 多通道副作用](#11-processingresult主数据流--多通道副作用)
12. [SideEffect 机制详解](#12-sideeffect-机制详解)
13. [与现有架构的融合](#13-与现有架构的融合)
14. [生命周期管理对比](#14-生命周期管理对比)
15. [中断信号传播机制](#15-中断信号传播机制)
16. [输入输出模式分类（IOMode）](#16-输入输出模式分类iomode)
17. [两种实现路径对比](#17-两种实现路径对比)
18. [扩展性与未来规划](#18-扩展性与未来规划)
19. [待讨论的开放问题](#19-待讨论的开放问题)
20. [总结](#20-总结)

---

## 1. 背景与动机

### 1.1 目标

实现一个 **可选输入侧数据类型 × 多选输出侧数据类型** 的对话系统。用户在前端选择输入和输出模式后，后端自动组装对应的处理管道（Pipeline），数据在管道中依次流过各个处理节点，实现解耦的、可组合的数据处理。

### 1.2 当前支持的组合（现状）

| 输入 | 输出 | 实现路径 | 对应类 |
|------|------|---------|--------|
| 文本 | 文本 | `input → Model → output` | `TextMessageProcessor` |
| 语音 | 语音 | `input → ASR → Model → TTS → output` | `ChatMessageProcessor` + `AudioServiceImpl` |

### 1.3 期望支持的全部组合

| 输入 | 输出 | 实现路径 | 中间节点 |
|------|------|---------|---------|
| 文本 | 文本 | `input → Model → output` | Model |
| 文本 | 语音 | `input → Model → TTS → output` | Model, TTS |
| 语音 | 文本 | `input → ASR → Model → output` | ASR, Model |
| 语音 | 语音 | `input → ASR → Model → TTS → output` | ASR, Model, TTS |

### 1.4 三个基础处理节点

| 节点 | 缩写 | 输入类型 | 输出类型 | 职责 |
|------|------|---------|---------|------|
| 语音识别 | **ASR** | Audio（`byte[]`） | Text（`String`） | 将用户语音转为文本 |
| 大语言模型 | **Model** | Text（`String`） | Text（`String`） | 对文本进行理解与生成回复 |
| 文本转语音 | **TTS** | Text（`String`） | Audio（`byte[]`） | 将文本回复转为语音 |

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

**问题**：无论实际的输入/输出组合是什么，`initSession` 一次性同时初始化 ASR 和 TTS 连接。

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

**问题**：LLM 输出文本到 TTS 的级联逻辑写死在 `ChatMessageProcessor` 内部，`TextMessageProcessor` 和 `ChatMessageProcessor` 是独立类，没有任何复用。

### 2.3 资源销毁的耦合

**位置**：[`AudioServiceImpl.endSession()`](file:///D:/JavaProject/ai_mental_care/server/src/main/java/org/lixiyun/server/service/impl/user/AudioServiceImpl.java#L410-L464)

```java
public void endSession(Long conversationId) {
    interruptAudio(...);
    asrConnectionManager.cancel(userId);   // 总是关闭 ASR
    ttsConnectionManager.cancel(userId);   // 总是关闭 TTS
    aggregateScheduler.cancelTask(...);
    conversationCacheManager.removeCacheZSetValue(...);
}
```

**问题**：所有资源集中销毁，不区分哪些实际在使用。

### 2.4 输入侧与输出侧被时间轮分隔

当前系统中，输入侧（前端数据到达 → ASR 识别 → 存储到消息表）和输出侧（时间轮调度 → 加载上下文 → LLM 推理 → TTS 合成）是通过 `ConversationAggregateScheduler`（时间轮）隔开的两个独立时间片段。这意味着它们不能放在同一个同步 Pipeline 中。

---

## 3. 架构总览：三层分离模型

### 3.1 核心洞察

输入侧和输出侧是**两个独立的时间片段**，通过时间轮异步连接：

```
前端发送数据 → 输入层处理/存储 → 等待时间轮 [异步断点] → 输出层处理 → 前端接收结果
```

因此需要三层分离架构：

```
┌──────────────────────────────────────────────────────────────┐
│                  输入层 (Input Layer)                         │
│  职责：接收前端数据 → 转换 → 存储 → 可选实时回显                │
│  模式：简单 Adapter，不作管道串联                               │
│  时机：实时响应（同步执行）                                      │
│                                                               │
│  TextInputAdapter      AudioInputAdapter                       │
│  文字消息 → 存储到DB    ASR 识别 → 文本 → 存储到DB              │
│                        (可选：中间识别结果实时推前端)             │
└──────────────────────┬───────────────────────────────────────┘
                       │ 写入
                       ▼
┌──────────────────────────────────────────────────────────────┐
│              中间存储层 (Storage Layer)                        │
│  职责：持久化消息 → 缓存会话上下文 → 等待时间轮调度              │
│  模式：现有基础设施（不变）                                     │
│                                                               │
│  ConversationHistoryMessagesStorage  (MySQL 消息表)            │
│  ConversationCacheManager            (Redis 缓存)              │
└──────────────────────┬───────────────────────────────────────┘
                       │ 时间轮到期触发
                       ▼
┌──────────────────────────────────────────────────────────────┐
│                  输出层 (Output Layer)                         │
│  职责：加载上下文 → 管道执行（Model → TTS → ...）→ 流式推送     │
│  模式：管道/过滤器（Pipeline + OutputNode）                     │
│  时机：异步执行（时间轮驱动）                                    │
│                                                               │
│  [ModelNode] → [TtsNode] → ...                                │
│  每个节点：接收 OutputContext + upstream Flux → 输出 ProcessingResult │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 为什么输入层不需要管道？

输入层的各 Adapter 之间**不存在上下游级联关系**：

- 用户选择了"语音输入"，只需要 `AudioInputAdapter` 工作
- 不会出现 "音频 → 文本 → 再转发给另一个输入适配器" 的场景
- 每个 InputAdapter 是独立的单向处理器：接收 → 转换 → 存储 → 可选回显

因此输入层使用简单的 Adapter 模式，不需要 Pipeline 和 PipelineFactory 的复杂度。

### 3.3 为什么输出层需要管道？

输出层有明显的**级联处理链**：

```
ModelNode 产出文本 → TtsNode 消费文本并转语音 → （未来）AvatarNode 消费文本驱动虚拟形象 → ...
```

每个下游节点消费上游节点的输出，这正是管道/过滤器模式的标准应用场景。

---

## 4. 输入层设计（Input Layer）

### 4.1 InputAdapter 接口

```java
/** 输入适配器——不作管道串联，仅做"接收→转换→存储→可选回显" */
interface InputAdapter {

    /** 此适配器处理的数据类型 */
    DataType getInputType();

    /**
     * 处理前端发来的原始数据
     * @return 转换后的标准消息，用于存储到中间层
     */
    Mono<StandardMessage> handleInput(Long userId, Long conversationId, Object rawData);

    /**
     * 注册实时回显监听器（如 ASR 中间识别结果实时推前端）
     */
    void setEchoListener(EchoListener listener);

    /**
     * 取消回显监听器
     */
    void removeEchoListener();
}
```

### 4.2 EchoListener 接口

```java
/** 输入层实时回显接口（直接将中间结果推送给前端，不经过存储层） */
interface EchoListener {
    /** 推送文本回显（ASR 中间结果、输入确认等） */
    void onTextEcho(String text);

    /** 推送状态变更（识别开始、识别结束等） */
    void onStatusChange(String status, Map<String, Object> metadata);
}
```

### 4.3 TextInputAdapter

```java
/**
 * 文本输入适配器
 * <p>职责极简：接收 WebSocket 文字消息 → 存储到消息表 → 前端回显确认</p>
 */
class TextInputAdapter implements InputAdapter {

    private final ConversationHistoryMessagesStorage messagesStorage;
    private EchoListener echoListener;

    @Override
    public DataType getInputType() {
        return DataType.TEXT;
    }

    @Override
    public Mono<StandardMessage> handleInput(Long userId, Long conversationId, Object rawData) {
        String text = (String) rawData;

        // 1. 构造标准消息
        StandardMessage message = StandardMessage.builder()
                .userId(userId)
                .conversationId(conversationId)
                .content(text)
                .dataType(DataType.TEXT)
                .role(MessageRole.USER)
                .build();

        // 2. 存储到消息表（供时间轮调度后处理）
        messagesStorage.storeTemporaryMessage(conversationId, message);

        // 3. 回显确认
        if (echoListener != null) {
            echoListener.onTextEcho(text);
        }

        return Mono.just(message);
    }

    @Override
    public void setEchoListener(EchoListener listener) {
        this.echoListener = listener;
    }

    @Override
    public void removeEchoListener() {
        this.echoListener = null;
    }
}
```

### 4.4 AudioInputAdapter

```java
/**
 * 音频输入适配器
 * <p>职责：接收 WebSocket 音频流 → ASR 识别 → 中间结果推前端 → 最终文本存储到消息表</p>
 */
class AudioInputAdapter implements InputAdapter {

    private final AsrConnectionManager asrConnectionManager;
    private final ConversationHistoryMessagesStorage messagesStorage;
    private EchoListener echoListener;

    /**
     * 识别过程中的文本缓冲区
     * key: conversationId, value: StringBuilder
     */
    private final ConcurrentHashMap<Long, StringBuilder> textBuffers = new ConcurrentHashMap<>();

    @Override
    public DataType getInputType() {
        return DataType.AUDIO;
    }

    @Override
    public Mono<StandardMessage> handleInput(Long userId, Long conversationId, Object rawData) {
        byte[] audioData = (byte[]) rawData;

        // 1. 发送音频到 ASR 引擎
        asrConnectionManager.sendAudio(userId, audioData);

        // 2. ASR 中间结果 → 实时推前端
        if (echoListener != null) {
            echoListener.onTextEcho("（识别中...）");
        }

        // 注意：最终识别结果由 ASR 回调异步触发存储，不在此处
        // 这里只负责发送音频帧
        return Mono.empty();
    }

    /**
     * 当一句话识别完成时由 ASR 回调触发，将完整文本存储到消息表
     */
    public void onSentenceRecognized(Long conversationId, String text) {
        StandardMessage message = StandardMessage.builder()
                .conversationId(conversationId)
                .content(text)
                .dataType(DataType.TEXT)
                .role(MessageRole.USER)
                .build();

        messagesStorage.storeTemporaryMessage(conversationId, message);
    }

    /**
     * 发送音频流结束信号，通知 ASR Provider 音频已全部发送
     */
    public void sendEndOfStream(Long userId) {
        asrConnectionManager.sendEndOfStream(userId);
    }
}
```

### 4.5 InputAdapterRegistry

```java
/** 输入适配器注册中心——根据输入类型选择对应的 Adapter */
@Slf4j
@Component
class InputAdapterRegistry {

    private final Map<DataType, InputAdapter> adapters;

    public InputAdapterRegistry(List<InputAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(InputAdapter::getInputType, a -> a));
    }

    public InputAdapter getAdapter(DataType inputType) {
        InputAdapter adapter = adapters.get(inputType);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的输入类型: " + inputType);
        }
        return adapter;
    }

    public Set<DataType> getSupportedInputTypes() {
        return adapters.keySet();
    }
}
```

---

## 5. 中间存储层设计（Storage Layer）

### 5.1 保持不变

中间存储层沿用现有基础设施，无需改动：

| 组件 | 职责 |
|------|------|
| `ConversationHistoryMessagesStorage` | MySQL 消息存储（temporary_messages、历史消息） |
| `ConversationCacheManager` | Redis 缓存（会话上下文、情绪分析、诊断结果、压缩摘要） |
| `ConversationAggregateScheduler` | 时间轮调度器（到期触发输出层执行） |

### 5.2 新增：OutputContext 打包

时间轮触发时，编排器（`ConversationMessageProcessor`）将当前会话的所有可用数据打包为 `OutputContext`，传给输出层管道。

详见第 10 章 [OutputContext 上下文数据包](#10-outputcontext-上下文数据包)。

---

## 6. 输出层核心抽象设计（Output Layer）

### 6.1 设计原则

1. **节点输入不限于单一类型**：ModelNode 需要用户消息 + 历史消息 + 情绪分析 + 配置等多种数据，不是简单的 `Flux<String>`
2. **节点输出有多个去向**：主数据流（给下游节点）+ WebSocket 推送 + 数据库存储，多条路径并行
3. **输出方式应编码在产出中**：上游节点将副作用声明嵌入 ProcessingResult，下游消费时自动执行
4. **不假设所有节点都是流式**：支持批量→流式、批量→批量、流式→流式等多种 I/O 模式
5. **节点间通过显式契约通信**：使用标准消息契约（TextChunk、AudioChunk）而非隐式类型约定

### 6.2 DataType 枚举

```java
/** 管道中流动的数据类型 */
public enum DataType {
    TEXT,
    AUDIO
}
```

### 6.3 OutputNode 接口

```java
/**
 * 输出节点接口——管道中的一个"过滤器"
 * <p>
 * 与 v1 设计的关键区别：
 * <ol>
 *   <li>不接收单一类型的 Flux，而是接收 OutputContext（多类型数据包） + upstream Flux（上游流）</li>
 *   <li>返回 ProcessingResult 而非 Flux，支持主数据流 + 嵌入的副作用声明</li>
 *   <li>声明 IOMode 而非隐式假设为流式</li>
 * </ol>
 */
interface OutputNode {

    // ==================== 元信息 ====================

    /** 节点名称 */
    String getNodeName();

    /** 此节点需要从 OutputContext 中获取的数据类型 */
    Set<DataType> getRequiredContextTypes();

    /** 此节点的输出类型 */
    DataType getOutputType();

    /** I/O 模式 */
    IOMode getIOMode();

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
     * @param upstream  上一个节点的输出流（无上游时为 null）
     * @return ProcessingResult 包含主数据流 + 多通道副作用声明
     */
    ProcessingResult process(OutputContext context, @Nullable Flux<?> upstream);
}
```

### 6.4 类型关系总览图

```
                    《interface》
                      OutputNode
        ├── getNodeName()            : String
        ├── getRequiredContextTypes(): Set<DataType>
        ├── getOutputType()          : DataType
        ├── getIOMode()              : IOMode
        ├── init() / destroy() / interrupt()
        └── process(OutputContext, Flux<?>) : ProcessingResult
                              △
          ┌───────────────────┼───────────────────┐
          │                   │                   │
    ┌───────────┐      ┌───────────┐      ┌───────────┐
    │ AsrNode   │      │ModelNode  │      │ TtsNode   │
    │(输出层用)  │      │           │      │           │
    └───────────┘      └───────────┘      └───────────┘


    ProcessingResult
    ├── mainOutput          : Flux<?>          ← 给下一个节点的主数据流
    ├── embeddedSideEffects : List<SideEffect> ← 嵌入流中的副作用（多通道输出）
    └── onCompleteActions   : List<Runnable>   ← 流结束后的回调


    OutputContext                      ← 从缓存/DB 聚合的所有上下文数据
    ├── userId, conversationId         ← 会话标识
    ├── temporaryMessages              ← 待处理消息
    ├── historyMessages                ← 历史对话
    ├── compressedSummary              ← 压缩摘要
    ├── emotionAnalysis                ← 情绪分析
    ├── diagnosisAnalysis              ← 诊断分析
    └── aiNodeConfig                   ← AI节点配置
```

---

## 7. 标准消息契约

### 7.1 为什么需要标准契约？

**问题**：管道中节点间通过隐式数据类型约定（如"大家都用 `Flux<String>`"）耦合。当下游节点（TtsNode）需要上游（ModelNode）的声学元数据（情绪标签、语速建议等）时，隐式约定无法承载。

**解决**：为每种数据类型定义统一的契约结构，节点只与契约交互，不直接感知对方的存在。

### 7.2 TextChunk（文本块契约）

```java
/**
 * 文本块——所有文本节点交互的标准契约
 * <p>
 * 生产者职责（如 ModelNode）：
 *   1. 将内部数据转换为 TextChunk 序列
 *   2. 在 metadata 中填充下游可能需要的标注信息
 *
 * 消费者职责（如 TtsNode）：
 *   1. 从 TextChunk 提取 text 进行核心处理
 *   2. 从 metadata 提取辅助参数（音色、语速等）
 * </p>
 */
public class TextChunk {
    /** 文本内容 */
    private final String text;

    /** 序号（流式传输中的位置） */
    private final int sequenceIndex;

    /** 是否为流中的最后一块 */
    private final boolean isLast;

    /**
     * 可扩展的元数据
     * <p>示例字段（由生产者按需填充）：
     * <ul>
     *   <li>"emotion": "sad" — 情绪标签（供 TTS 选音色）</li>
     *   <li>"speed": "slow" — 语速建议（供 TTS 调整语速）</li>
     *   <li>"volume": 0.8 — 音量建议</li>
     *   <li>"sentenceBoundary": true — 句边界标记</li>
     * </ul>
     */
    private final Map<String, Object> metadata;

    // builder / getter 省略
}
```

### 7.3 AudioChunk（音频块契约）

```java
/**
 * 音频块——所有音频节点交互的标准契约
 */
public class AudioChunk {
    /** 音频数据 */
    private final byte[] audioData;

    /** 序号 */
    private final int sequenceIndex;

    /** 是否为流中的最后一块 */
    private final boolean isLast;

    /** 音频格式 */
    private final AudioFormat format;  // PCM, MP3, WAV...

    /**
     * 可扩展的元数据
     * <p>示例字段：
     * <ul>
     *   <li>"sampleRate": 16000</li>
     * </ul>
     */
    private final Map<String, Object> metadata;
}
```

### 7.4 契约如何解耦"识别与转换"

以 `[ModelNode] → [TtsNode]` 为例：

**v1 中**（隐式约定）：
- TtsNode 硬编码接收 `Flux<String>`，隐式知道 `String` 是 ModelNode 产出的文本
- 如果 ModelNode 需要传递情绪标注给 TtsNode，要么改 TtsNode 的输入语义，要么在 ModelNode 中写 TTS 相关逻辑

**v2 中**（显式契约）：
- ModelNode 的职责：将 LLM 产出转换为 `TextChunk` 序列，在 `metadata` 中填充 `emotion`、`speed` 等标注（这是 ModelNode 对"自身语义理解"的产出）
- TtsNode 的职责：消费 `TextChunk` 序列，从 `text` 中取文本做合成，从 `metadata` 中取声学参数做音色/语速选择（这是 TtsNode 对自身领域知识的运用）
- Pipeline 在组装时校验：ModelNode 输出 `TEXT` → TtsNode 输入 `TEXT`，类型兼容
- **两者只与 `TextChunk` 契约交互，不直接知道对方存在**

---

## 8. 输出层具体节点实现

### 8.1 节点概览

| 节点 | IOMode | OutputContext 所需数据 | Upstream | 输出 | key 内部依赖 |
|------|--------|----------------------|----------|------|-------------|
| `ModelChatOutputNode` | BATCH_IN_STREAM_OUT | temporaryMessages, historyMessages, emotionAnalysis, diagnosisAnalysis, aiNodeConfig, compressedSummary | null | `Flux<TextChunk>` | ChatModelFactory, TextMessageProcessorModel |
| `TtsOutputNode` | STREAM_IN_STREAM_OUT | ∅（不需要额外上下文） | `Flux<TextChunk>` | `Flux<AudioChunk>` | TtsConnectionManager |

> **注意**：AsrNode 被移动到输入层（`AudioInputAdapter`），输出层不包含 ASR。对于"音频输入"场景，ASR 识别和文本存储已经在输入层完成，时间轮触发时输出层拿到的已经是文本。

### 8.2 ModelNode

```java
/**
 * 大语言模型节点
 * <p>IOMode: BATCH_IN_STREAM_OUT</p>
 * <p>职责：从 OutputContext 提取多类型上下文 → 构建 Prompt → LLM 流式推理 → 产出 TextChunk 流</p>
 */
class ModelNode implements OutputNode {

    private final ChatModelFactory chatModelFactory;
    private final TextMessageProcessorModel processorModel;
    private final ConversationHistoryMessagesStorage historyMessagesStorage;
    private final ConversationStreamHolder streamHolder;

    private Long conversationId;
    private Long userId;

    @Override
    public String getNodeName() { return "ModelNode"; }

    @Override
    public Set<DataType> getRequiredContextTypes() {
        return Set.of(DataType.TEXT);  // 声明需要 TEXT 类型数据（temporaryMessages）
    }

    @Override
    public DataType getOutputType() { return DataType.TEXT; }

    @Override
    public IOMode getIOMode() { return IOMode.BATCH_IN_STREAM_OUT; }

    @Override
    public void init(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
        // 不需要创建连接，只需加载配置
    }

    @Override
    public ProcessingResult process(OutputContext context, @Nullable Flux<?> upstream) {
        // 1. 从 OutputContext 中提取需要的数据
        List<StandardMessage> tempMessages = context.getTemporaryMessages();
        List<ConversationMemory> history = context.getHistoryMessages();
        EmotionAnalysisResult emotion = context.getEmotionAnalysis();
        DiagnosisResult diagnosis = context.getDiagnosisAnalysis();
        AiNode aiNodeConfig = context.getAiNodeConfig();
        String summary = context.getCompressedSummary();

        // 2. 构建 Prompt
        String prompt = buildPrompt(tempMessages, history, emotion, diagnosis, summary, aiNodeConfig);

        // 3. 获取 ChatModel 实例
        ChatModel chatModel = chatModelFactory.getOrCreateChatModel(aiNodeConfig);

        // 4. 创建 Sinks 用于桥接 LLM 回调 → Flux
        Sinks.Many<TextChunk> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 5. 调用 LLM 流式推理
        processorModel.stream(chatModel, prompt, new StreamEventListener() {
            @Override
            public void onContentChunk(String textChunk) {
                TextChunk chunk = TextChunk.builder()
                        .text(textChunk)
                        .sequenceIndex(/* 递增序号 */)
                        .isLast(false)
                        .metadata(Map.of("emotion", emotion != null ? emotion.getPrimaryEmotion() : "neutral"))
                        .build();
                sink.tryEmitNext(chunk);
            }

            @Override
            public void onModelComplete(AssistantMessage message) {
                TextChunk lastChunk = TextChunk.builder()
                        .text("")
                        .sequenceIndex(/* 末位序号 */)
                        .isLast(true)
                        .build();
                sink.tryEmitNext(lastChunk);
                sink.tryEmitComplete();
            }

            @Override
            public void onError(Throwable error) {
                sink.tryEmitError(error);
            }
        });

        // 6. 构建 ProcessingResult
        //    - mainOutput: TextChunk 流，给下游 TtsNode 消费
        //    - sideEffects: WebSocket 实时推送 + 数据库最终存储
        return ProcessingResult.builder()
                .mainOutput(sink.asFlux())
                .sideEffect(new SideEffect(
                        SideEffect.Channel.WEBSOCKET,       // 目标渠道：前端
                        SideEffect.Timing.PER_CHUNK,        // 时机：每个 chunk
                        chunk -> {                          // 动作：推文本弹幕
                            TextChunk tc = (TextChunk) chunk;
                            webSocketManager.sendTextStream(userId, conversationId, tc.getText());
                        }
                ))
                .sideEffect(new SideEffect(
                        SideEffect.Channel.DATABASE,        // 目标渠道：数据库
                        SideEffect.Timing.ON_COMPLETE,      // 时机：流结束后
                        ignore -> {                         // 动作：保存完整回复
                            historyMessagesStorage.saveAssistantMessage(conversationId, /* 完整文本 */);
                        }
                ))
                .build();
    }

    @Override
    public void destroy() {
        streamHolder.cancelStream(conversationId);
    }

    @Override
    public void interrupt() {
        streamHolder.cancelStream(conversationId);
    }
}
```

### 8.3 TtsNode

```java
/**
 * 文本转语音节点
 * <p>IOMode: STREAM_IN_STREAM_OUT</p>
 * <p>职责：消费上游 TextChunk 流 → TTS 合成 → 产出 AudioChunk 流</p>
 */
class TtsNode implements OutputNode {

    private final TtsConnectionManager ttsConnectionManager;
    private Long userId;

    @Override
    public String getNodeName() { return "TtsNode"; }

    @Override
    public Set<DataType> getRequiredContextTypes() {
        return Collections.emptySet();  // 不需要额外上下文
    }

    @Override
    public DataType getOutputType() { return DataType.AUDIO; }

    @Override
    public IOMode getIOMode() { return IOMode.STREAM_IN_STREAM_OUT; }

    @Override
    public void init(Long conversationId, Long userId) {
        this.userId = userId;
        // 创建 Sinks，用于桥接 TTS 异步回调 → Flux<AudioChunk>
        this.audioSink = Sinks.many().unicast().onBackpressureBuffer();
        // 注册 TTS 连接
        ttsConnectionManager.register(userId, new TtsResultCallback() {
            @Override
            public void onAudioData(byte[] audioData) {
                AudioChunk chunk = AudioChunk.builder()
                        .audioData(audioData)
                        .isLast(false)
                        .build();
                audioSink.tryEmitNext(chunk);
            }

            @Override
            public void onSynthesisComplete() {
                AudioChunk lastChunk = AudioChunk.builder()
                        .audioData(new byte[0])
                        .isLast(true)
                        .build();
                audioSink.tryEmitNext(lastChunk);
                audioSink.tryEmitComplete();
            }

            @Override
            public void onFail(String taskId, String statusText) {
                audioSink.tryEmitError(new TtsException(taskId, statusText));
            }
        });
    }

    @Override
    public ProcessingResult process(OutputContext context, @Nullable Flux<?> upstream) {
        // 1. 消费上游 TextChunk 流
        upstream
                .cast(TextChunk.class)               // 通过契约类型接入（非隐式 String）
                .subscribe(
                    chunk -> {
                        // 2. 发送文本到 TTS 引擎
                        ttsConnectionManager.sendTextSegment(userId, chunk.getText());

                        // 3. 可选：从 metadata 提取声学参数
                        String emotion = (String) chunk.getMetadata().get("emotion");
                        if (emotion != null) {
                            // 调整音色/语速
                        }

                        // 4. 最后一个 chunk → 通知 TTS 完成
                        if (chunk.isLast()) {
                            ttsConnectionManager.finishSynthesis(userId);
                        }
                    },
                    error -> audioSink.tryEmitError(error)
                );

        // 5. 返回 ProcessingResult
        //    mainOutput: AudioChunk 流（如果没有更多下游节点，Pipeline 将执行其 sideEffects）
        //    sideEffects: WebSocket 推送音频 + 清理
        return ProcessingResult.builder()
                .mainOutput(audioSink.asFlux())
                .sideEffect(new SideEffect(
                        SideEffect.Channel.WEBSOCKET,
                        SideEffect.Timing.PER_CHUNK,
                        chunk -> {
                            AudioChunk ac = (AudioChunk) chunk;
                            webSocketManager.sendAudioBinary(userId, conversationId, ac.getAudioData());
                        }
                ))
                .build();
    }

    @Override
    public void destroy() {
        ttsConnectionManager.cancel(userId);
    }

    @Override
    public void interrupt() {
        ttsConnectionManager.interrupt(userId);
    }
}
```

---

## 9. OutputPipeline 管道设计

### 9.1 OutputPipeline 类

```java
/**
 * 输出层管道——管理多个 OutputNode 的有序组合
 * <p>与 v1 Pipeline 的关键区别：
 * <ol>
 *   <li>不是简单的 Flux 链式调用，而是 OutputContext + upstream 的复合传递</li>
 *   <li>每个节点返回 ProcessingResult，Pipeline 负责执行其中嵌入的 SideEffect</li>
 *   <li>SideEffect 随主数据流传播到下游，由 Pipeline 统一编排</li>
 * </ol>
 */
public class OutputPipeline {

    /** 有序的处理节点列表 */
    private final List<OutputNode> nodes;

    /** 会话上下文数据包 */
    private final OutputContext context;

    /** 所属会话ID */
    private final Long conversationId;

    /** 所属用户ID */
    private final Long userId;

    /** 管道级别的 sideEffect 监听器（额外订阅） */
    private final List<SideEffect> externalSideEffects = new ArrayList<>();

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
     *   1. upstream = null
     *   2. for each node:
     *      result = node.process(context, upstream)
     *      executeSideEffects(result.embeddedSideEffects)  ← 执行嵌入的副作用
     *      upstream = result.mainOutput                    ← 传给下一个节点
     *   3. 最后一个节点的 mainOutput 上的 sideEffects 由最终订阅消费
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public Flux<?> execute() {
        if (nodes.isEmpty()) {
            return Flux.empty();
        }

        Flux<?> upstream = null;
        ProcessingResult lastResult = null;

        for (int i = 0; i < nodes.size(); i++) {
            OutputNode node = nodes.get(i);
            ProcessingResult result = node.process(context, upstream);

            // 执行当前节点的嵌入副作用
            executeSideEffects(result.getEmbeddedSideEffects());

            // 将主数据流传给下一个节点
            upstream = result.getMainOutput();
            lastResult = result;
        }

        // 最后一个节点的 mainOutput 作为管道最终输出
        // 外部可以继续订阅它来执行管道级别的回调
        Flux<?> finalOutput = lastResult != null
                ? lastResult.getMainOutput()
                : Flux.empty();

        // 将管道级别的 sideEffects 附加到最终输出上
        for (SideEffect se : externalSideEffects) {
            finalOutput = finalOutput.doOnNext(data -> se.getAction().accept(data));
        }

        return finalOutput;
    }

    /** 注册管道级别的输出监听器 */
    public void addExternalSideEffect(SideEffect sideEffect) {
        this.externalSideEffects.add(sideEffect);
    }

    // ==================== 私有方法 ====================

    private void executeSideEffects(List<SideEffect> sideEffects) {
        for (SideEffect se : sideEffects) {
            if (se.getTiming() == SideEffect.Timing.IMMEDIATE) {
                se.getAction().accept(null);
            }
            // PER_CHUNK / ON_COMPLETE 的副作用已嵌入到 mainOutput 的 Flux 中，
            // 由 Pipeline.process() 中通过 doOnNext / doOnComplete 执行
        }
    }
}
```

### 9.2 数据类型兼容性校验

```java
public OutputPipeline(List<OutputNode> nodes, OutputContext context,
                      Long conversationId, Long userId) {
    // 节点间类型兼容性校验
    for (int i = 1; i < nodes.size(); i++) {
        DataType prevOutput = nodes.get(i - 1).getOutputType();
        // 校验下游节点是否声明需要上游的输出类型
        // （通过 requiredContextTypes 或隐式 upstream 消费判断）
        // 这里做基本的 DataType 兼容性检查
    }
    this.nodes = new ArrayList<>(nodes);
    this.context = context;
    this.conversationId = conversationId;
    this.userId = userId;
}
```

### 9.3 数据流完整示例（TEXT → AUDIO）

```
OutputContext (包含: temporaryMessages, historyMessages, emotionAnalysis, ...)
    │
    ▼
═══════════════════════════════════════════════════════════════════
  [ModelNode].process(context, upstream=null)
     │
     │ 从 context 提取: temporaryMessages + history + emotion + ...
     │ 构建 Prompt → ChatModel.stream()
     │
     ├──→ mainOutput: Flux<TextChunk>
     │       │
     │       │  ┌─ SideEffect{Channel=WEBSOCKET, Timing=PER_CHUNK}
     │       │  │    → webSocketManager.sendTextStream(text)
     │       │  │    (Pipeline 在流经时执行 ─→ 前端实时展示文本弹幕)
     │       │  │
     │       │  └─ SideEffect{Channel=DATABASE, Timing=ON_COMPLETE}
     │       │       → historyMessagesStorage.saveAssistantMessage(...)
     │       │       (流结束后 Pipeline 执行 ─→ 持久化到 MySQL)
     │       │
     │       ▼
     │  Pipeline.executeSideEffects(result.sideEffects)
     │       → 执行 IMMEDIATE 类型的副作用
     │       → PER_CHUNK/ON_COMPLETE 副作用已嵌入 Flux，等待消费时触发
     │
     │  upstream = Flux<TextChunk>  ──────────────────────────────────┐
     │                                                                 │
═══════════════════════════════════════════════════════════════════   │
  [TtsNode].process(context, upstream=Flux<TextChunk>)  ◄─────────────┘
     │
     │ 订阅 upstream:
     │   onNext(chunk):
     │     ├── ttsConnectionManager.sendTextSegment(chunk.text)
     │     ├── 从 chunk.metadata 提取 emotion/speed 调整音色
     │     └── [Pipeline 自动执行 TextChunk 中嵌入的 WEBSOCKET SideEffect]
     │             ↑ 注意：这是关键设计——
     │             ↑ 上游 ModelNode 嵌入的 PER_CHUNK→WEBSOCKET 副作用
     │             ↑ 在 TtsNode（或 Pipeline）消费 TextChunk 时被触发
     │   onComplete:
     │     └── ttsConnectionManager.finishSynthesis()
     │         [Pipeline 自动执行 ON_COMPLETE→DATABASE SideEffect]
     │
     ├──→ mainOutput: Flux<AudioChunk>
     │       │
     │       │  └─ SideEffect{Channel=WEBSOCKET, Timing=PER_CHUNK}
     │       │       → webSocketManager.sendAudioBinary(audioData)
     │       │       (Pipeline 在流经时执行 ─→ 前端播放音频)
     │       │
     │       ▼
     │  最后一个节点，管道结束
     │  mainOutput 作为最终输出，外部可进一步订阅
═══════════════════════════════════════════════════════════════════
```

---

## 10. OutputContext 上下文数据包

### 10.1 定义

```java
/**
 * 输出上下文——时间轮触发时，编排器将会话所有可用数据聚合到此对象中
 * <p>
 * 数据来源：
 * <ul>
 *   <li>temporaryMessages — 消息表（ConversationHistoryMessagesStorage）</li>
 *   <li>historyMessages — Redis 缓存（ConversationCacheManager）</li>
 *   <li>emotionAnalysis — Redis 缓存</li>
 *   <li>diagnosisAnalysis — Redis 缓存</li>
 *   <li>compressedSummary — Redis 缓存</li>
 *   <li>aiNodeConfig — AiNodeConfigManager</li>
 * </ul>
 * <p>
 * 节点通过 getRequiredContextTypes() 声明需要哪些数据，
 * OutputPipeline 在装配时检查 context 是否包含所需数据。
 */
public class OutputContext {

    private final Long userId;
    private final Long conversationId;

    /** 待处理消息（本次时间轮触发的消息批次） */
    private final List<StandardMessage> temporaryMessages;

    /** 历史对话消息 */
    private final List<ConversationMemory> historyMessages;

    /** 压缩摘要（可选） */
    @Nullable
    private final String compressedSummary;

    /** 情绪分析结果（可选） */
    @Nullable
    private final EmotionAnalysisResult emotionAnalysis;

    /** 诊断分析结果（可选） */
    @Nullable
    private final DiagnosisResult diagnosisAnalysis;

    /** AI 节点配置 */
    private final AiNode aiNodeConfig;

    /**
     * 类型安全的数据获取
     * @param type 数据类型 class
     * @return 对应数据，不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T get(Class<T> type) {
        if (type == List.class) return (T) temporaryMessages;
        if (type == EmotionAnalysisResult.class) return (T) emotionAnalysis;
        if (type == DiagnosisResult.class) return (T) diagnosisAnalysis;
        // ... 其他类型映射
        return null;
    }
}
```

### 10.2 装配时机

由 `ConversationMessageProcessor`（编排器）在时间轮触发时装配：

```java
// 在 ConversationMessageProcessor.processConversationMessage() 中
OutputContext context = OutputContext.builder()
        .userId(userId)
        .conversationId(conversationId)
        .temporaryMessages(loadTemporaryMessages(conversationId))
        .historyMessages(cacheManager.getHistoryMessages(conversationId))
        .compressedSummary(cacheManager.getCompressedSummary(conversationId))
        .emotionAnalysis(cacheManager.getEmotionAnalysis(conversationId))
        .diagnosisAnalysis(cacheManager.getDiagnosisAnalysis(conversationId))
        .aiNodeConfig(aiNodeConfigManager.getConfigWithLoad(conversationId))
        .build();

// 传给输出层管道执行
OutputPipeline pipeline = pipelineSessionManager.getPipeline(conversationId);
pipeline.execute(context).subscribe();
```

---

## 11. ProcessingResult：主数据流 + 多通道副作用

### 11.1 定义

```java
/**
 * 节点的处理结果——不仅包含给下游节点消费的主数据流，
 * 还包含嵌入其中的副作用声明（多通道输出）
 * <p>
 * 上游节点将副作用嵌入 ProcessingResult，
 * Pipeline 在消费 mainOutput 时自动执行这些副作用。
 * 下游节点不需要知道 WebSocket、数据库等基础设施的存在。
 */
public class ProcessingResult {

    /** 主数据流——给下一个节点（或外部订阅者）消费 */
    private final Flux<?> mainOutput;

    /**
     * 嵌入主输出流的副作用声明
     * <p>这些副作用已通过 doOnNext / doOnComplete 绑定到 mainOutput 上。
     * 当下游节点（或外部）订阅消费 mainOutput 时，副作用会自动触发。
     * <p>IMMEDIATE 类型的副作用在 process() 返回时立即由 Pipeline 执行</p>
     */
    private final List<SideEffect> embeddedSideEffects;

    /** 流完成后的回调（不同于 ON_COMPLETE SideEffect，这里用于节点内部清理） */
    private final List<Runnable> onCompleteActions;

    // builder 省略
}
```

### 11.2 为什么将副作用嵌入主数据流？

**设计理念**：副作用随数据流传播，下游消费时自动执行。

```
ModelNode.process()
  |
  ├→ mainOutput: Flux<TextChunk>
  │   + doOnNext(chunk → websocket.sendTextStream(chunk.text))      ← 嵌入副作用
  │   + doOnComplete(() → database.saveMessage(完整回复))             ← 嵌入副作用
  │
  └→ 返回 ProcessingResult
      |
      ▼
Pipeline
  ├→ 将 mainOutput 传给 TtsNode
  └→ TtsNode 消费 TextChunk 时，
      Pipeline 下的 Flux 操作符链自动触发 WEBSOCKET SideEffect
      ─→ 文本弹幕送到前端
      ─→ 最后 ON_COMPLETE 触发 database.saveMessage()

TtsNode.process(upstream=Flux<TextChunk>)
  |
  ├→ 订阅 upstream（即 ModelNode 的 mainOutput）
  │   └→ 每次收到 TextChunk，SideEffect 自动执行
  │       （TtsNode 不需要知道/SideEffect 的存在）
  │
  └→ 返回自己的 ProcessingResult
      └→ mainOutput: Flux<AudioChunk> + WEBSOCKET SideEffect
```

**优势**：

- **节点不感知基础设施**：TtsNode 只处理 `TextChunk → AudioChunk`，不知道 WebSocket 或数据库
- **副作用随流传播**：不需要显式传递、不需要全局注册，随 mainOutput 自然流动
- **可组合**：添加/移除副作用只需修改 PipelineFactory 中的节点配置，不影响节点本身
- **时序正确**：背压自然传导——如果下游消费慢，上游的 WEBSOCKET SideEffect 也会跟随延迟（或通过 `Flux.share()` 分离）

---

## 12. SideEffect 机制详解

### 12.1 SideEffect 定义

```java
/**
 * 副作用声明——描述节点产出数据后的非主链输出行为
 * <p>示例：
 * <ul>
 *   <li>ModelNode 产出文本 → 推送到前端 WebSocket（PER_CHUNK）</li>
 *   <li>ModelNode 产出完成 → 保存完整回复到 MySQL（ON_COMPLETE）</li>
 *   <li>TtsNode 初始化完成 → 发送"AI开始说话"状态给前端（IMMEDIATE）</li>
 * </ul>
 */
public class SideEffect {

    /** 目标渠道 */
    private final Channel channel;

    /** 执行时机 */
    private final Timing timing;

    /** 副作用动作 */
    private final Consumer<Object> action;

    public enum Channel {
        /** WebSocket（推送给前端） */
        WEBSOCKET,
        /** 数据库（MySQL 持久化） */
        DATABASE,
        /** MCP 外部服务 */
        MCP_SERVICE,
        /** 日志/监控 */
        METRICS
    }

    public enum Timing {
        /** 立即执行（不等待数据流） */
        IMMEDIATE,
        /** 每个数据块后执行 */
        PER_CHUNK,
        /** 流结束后执行一次 */
        ON_COMPLETE
    }
}
```

### 12.2 SideEffect 的执行时机

| Timing | 执行者 | 执行时机 | 示例 |
|--------|--------|---------|------|
| `IMMEDIATE` | Pipeline | `node.process()` 返回后立即执行 | 发送"AI开始思考"的状态通知 |
| `PER_CHUNK` | Pipeline / 下游消费者 | 当 mainOutput 中的每个元素被消费时 | WebSocket 推送文本弹幕/音频二进制 |
| `ON_COMPLETE` | Pipeline / 下游消费者 | 当 mainOutput 流完成时 | 保存完整回复到数据库 |

### 12.3 SideEffect 与背压的关系

SideEffect 通过 `Flux.doOnNext()` / `Flux.doOnComplete()` 嵌入主数据流。这意味着：

- **默认行为**：SideEffect 的执行速率受主数据流消费速率约束（背压传导）
- **如果需要解耦**：在 PipelineFactory 组装时，使用 `Flux.share()` 让 SideEffect 拥有独立的数据流副本：

```java
// 方案：让 WEBSOCKET SideEffect 不受下游背压影响
Flux<TextChunk> shared = mainOutput.publish().refCount(2);  // 两个订阅者
// 订阅者1: TtsNode 主数据流
// 订阅者2: WEBSOCKET SideEffect（独立消费，不受 TtsNode 背压影响）
```

这个选择应在 PipelineFactory 级别做，节点本身不关心。

---

## 13. 与现有架构的融合

### 13.1 现有架构回顾

```
Controller / AudioServiceImpl
    │
    ├── initSession()  → 创建 ASR + TTS 连接
    ├── sendAudioMessage() → 发送音频数据
    ├── stopSpeaking() → 发送结束信号
    ├── interruptAudio() → 中断处理
    └── endSession() → 销毁所有资源

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

### 13.2 目标架构

```
Controller / AudioServiceImpl / TextChatServiceImpl
    │
    ├── initSession(inputType, outputType)
    │   │
    │   ├── 注册 InputAdapter（输入层）
    │   │   └── inputAdapterRegistry.getAdapter(inputType)
    │   │
    │   └── 预先构建 OutputPipeline（输出层）
    │       └── outputPipelineFactory.createPipeline(outputType, convId, userId)
    │           └── OutputPipeline.init()  (创建需要的节点+连接)
    │
    ├── sendMessage(data) → inputAdapter.handleInput(userId, convId, data)
    ├── interrupt() → outputPipeline.interrupt()
    └── endSession() → inputAdapter.cleanup() + outputPipeline.destroy()

ConversationAggregateScheduler (不变)
    │
    └── 触发 → ConversationMessageProcessor.processConversationMessage()
                    │
                    ├── ... 前置步骤不变 ...
                    │
                    ├── 装配 OutputContext（从缓存/DB 聚合所有数据）
                    │
                    ├── 路由到 OutputPipeline
                    │   └── outputPipelineSessionManager.getPipeline(convId)
                    │       └── OutputPipeline.execute(context)
                    │           ├── [ModelNode].process(context, null)
                    │           │   └── SideEffect{WEBSOCKET}      → 前端文本弹幕
                    │           │   └── SideEffect{DATABASE}        → 保存到 MySQL
                    │           └── [TtsNode].process(context, textChunkFlux)
                    │               └── SideEffect{WEBSOCKET}      → 前端音频播放
                    │
                    ├── ... 后置步骤不变 ...
```

### 13.3 改动点清单

| 模块 | 改动内容 | 改动程度 |
|------|---------|---------|
| **新增** `InputAdapter` 接口 | 输入适配器抽象 | 新增 |
| **新增** `EchoListener` 接口 | 输入层实时回显 | 新增 |
| **新增** `TextInputAdapter` | 文本输入适配器 | 新增 |
| **新增** `AudioInputAdapter` | 音频输入适配器（内含 ASR） | 新增 |
| **新增** `InputAdapterRegistry` | 输入适配器注册中心 | 新增 |
| **新增** `DataType` 枚举 | 数据类型定义 | 新增 |
| **新增** `IOMode` 枚举 | 输入输出模式枚举 | 新增 |
| **新增** `TextChunk` 类 | 文本消息契约 | 新增 |
| **新增** `AudioChunk` 类 | 音频消息契约 | 新增 |
| **新增** `StandardMessage` 类 | 标准消息格式 | 新增 |
| **新增** `OutputContext` 类 | 输出上下文数据包 | 新增 |
| **新增** `OutputNode` 接口 | 输出节点抽象 | 新增 |
| **新增** `ProcessingResult` 类 | 处理结果（主流+副作用） | 新增 |
| **新增** `SideEffect` 类 | 副作用声明 | 新增 |
| **新增** `OutputPipeline` 类 | 输出管道容器 | 新增 |
| **新增** `OutputPipelineFactory` | 输出管道组装工厂 | 新增 |
| **新增** `OutputPipelineSessionManager` | 会话级输出管道管理 | 新增 |
| **改** `ModelChatOutputNode` | 从 `TextMessageProcessor` 提取核心逻辑 | 改造 |
| **改** `TtsOutputNode` | 从 `ChatMessageProcessor` 提取 TTS 逻辑 | 改造 |
| **改** `AudioServiceImpl` | initSession/endSession 委托新组件 | 改造 |
| **改** `ConversationMessageProcessor` | 装配 OutputContext → 调用 OutputPipeline | 改造 |
| **废弃** `TextMessageProcessor` | 被 OutputPipeline + ModelNode 替代 | 最终废弃 |
| **废弃** `ChatMessageProcessor` | 被 OutputPipeline + ModelNode + TtsNode 替代 | 最终废弃 |
| **不变** `AsrConnectionManager` | 调用方从 AudioServiceImpl → AudioInputAdapter | 不变 |
| **不变** `TtsConnectionManager` | 调用方从 VoiceMessageProcessor → TtsNode | 不变 |
| **不变** `ConversationCacheManager` | 仅 OutputContext 装配时读取 | 不变 |
| **不变** `ConversationWebSocketManager` | 仅 SideEffect 动作中调用 | 不变 |
| **不变** `ConversationAggregateScheduler` | 时间轮调度逻辑不变 | 不变 |

---

## 14. 生命周期管理对比

### 14.1 当前模式

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

### 14.2 三层模式

```
initSession(inputType, outputType) {
    // 1. 输入层：按需注册 InputAdapter
    if (inputType == AUDIO) {
        AudioInputAdapter → asrConnectionManager.register(userId, callback)
    }
    // (文本输入不需要注册 Adapter，直接 WebSocket 接收即可)

    // 2. 输出层：按需构建 OutputPipeline
    OutputPipeline pipeline = outputPipelineFactory.createPipeline(outputType, convId, userId)
    pipeline.init()
        ├── always → ModelNode.init()    (不需要创建连接)
        └── if outputType=AUDIO → TtsNode.init() → ttsConnectionManager.register()
}

endSession(conversationId) {
    // 1. 输入层清理
    inputAdapterRegistry.getAdapter(inputType).cleanup()
        └── if AudioInputAdapter → asrConnectionManager.cancel(userId)

    // 2. 输出层清理
    pipelineSessionManager.destroyPipeline(conversationId)
        └── OutputPipeline.destroy()
            ├── if TtsNode → ttsConnectionManager.cancel(userId)
            └── always → ModelNode.destroy()
}
```

### 14.3 模式切换

```java
// 从 TEXT→TEXT 切换到 AUDIO→AUDIO
// 输入层切换
inputAdapterRegistry.getAdapter(DataType.AUDIO).init(convId, userId);
// 输出层切换
pipelineSessionManager.destroyPipeline(conversationId);  // 销毁旧管道 [ModelNode]
pipelineSessionManager.initPipeline(DataType.AUDIO, conversationId, userId);
// 新管道 [ModelNode, TtsNode]
```

---

## 15. 中断信号传播机制

### 15.1 管道中断传播

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

### 15.2 各节点中断实现

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

### 15.3 中断信号流路径

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

## 16. 输入输出模式分类（IOMode）

### 16.1 为什么需要 IOMode？

上一版设计中所有节点都假设返回 `Flux`，但并非所有节点都是流式的，例如：
- 敏感词过滤节点：输入一段 `String`，输出一个 `FilterResult`（布尔+清洗后文本）——这是批量→批量
- 文本聚合节点：输入 `Flux<TextChunk>`，输出完整 `String`——这是流式→批量

强制所有节点使用 `Flux` 会引入不必要的复杂度。

### 16.2 IOMode 枚举

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

### 16.3 各节点对应的 IOMode

| 节点 | 输入来源 | 输出方式 | IOMode |
|------|---------|---------|--------|
| `ModelChatOutputNode` | OutputContext（批量文本数据） | Flux<TextChunk> | BATCH_IN_STREAM_OUT |
| `TtsOutputNode` | upstream Flux<TextChunk> | Flux<AudioChunk> | STREAM_IN_STREAM_OUT |
| `SensitiveWordFilterNode`（未来） | String | FilterResult | BATCH_IN_BATCH_OUT |
| `TextAggregatorNode`（未来） | Flux<TextChunk> | String（完整文本） | STREAM_IN_BATCH_OUT |

### 16.4 OutputNode 接口如何适配不同 IOMode

由于 `process()` 签名为 `process(OutputContext context, @Nullable Flux<?> upstream) → ProcessingResult`：

- **upstream = null 时**：节点从 `context` 中获取批量数据（BATCH_IN_*）
- **upstream ≠ null 时**：节点订阅上游流（STREAM_IN_*）
- **mainOutput**：通过 `ProcessingResult.mainOutput` 返回，既可以是 `Flux`（*_STREAM_OUT）也可以是 `Mono`（*_BATCH_OUT）

节点内部根据 `getIOMode()` 声明决定实际行为，Pipeline 只关心 `ProcessingResult` 的抽象接口。

---

## 17. 两种实现路径对比

### 17.1 路径 A：渐进式演进（推荐）

**阶段 1：引入 OutputNode 接口和 OutputContext**
- 将 Model/TTS 逻辑抽取为独立 Node，但保持旧 Processor 不变
- 新旧并跑验证

**阶段 2：引入 OutputPipeline 和 PipelineFactory**
- 让 VoiceMessageProcessor 内部通过 OutputPipeline 组合节点
- 替换内部级联逻辑

**阶段 3：引入 InputAdapter**
- 将 ASR 初始化逻辑从 AudioServiceImpl 提取到 AudioInputAdapter

**阶段 4：统一到三层架构**
- 用 InputAdapterRegistry + OutputPipelineSessionManager 替代 AudioServiceImpl 中的分散逻辑
- 废弃旧 Processor

### 17.2 路径 B：一次性重构

直接按目标架构实现全部组件，一次性替换。

**风险**：改动量大，回归测试范围广，线上稳定性风险高。

### 17.3 建议

推荐**路径 A**，理由与 v1 相同：

1. 线上系统需要保证稳定性
2. `ModelChatOutputNode` / `TtsOutputNode` 的 LLM 流式处理逻辑可先独立抽取验证
3. ASR/TTS 连接管理已通过防腐层解耦，Adapter 化改动范围可控
4. 每步都可独立测试和灰度发布

---

## 18. 扩展性与未来规划

### 18.1 新增输入类型

只需实现新的 `InputAdapter`，在 `InputAdapterRegistry` 中注册：

| 新输入类型 | 新 Adapter | 核心动作 |
|-----------|-----------|---------|
| 视频 | `VideoInputAdapter` | 视频 → 关键帧提取 → 文本描述 → 存储到消息表 |
| 图像 | `ImageInputAdapter` | 图像 → 视觉理解API → 文本 → 存储到消息表 |
| 生理数据 | `BioSignalInputAdapter` | 心率/脑电 → 特征提取 → 结构化文本 → 存储到消息表 |

### 18.2 新增输出节点

只需实现新的 `OutputNode`，在 `OutputPipelineFactory` 中注册组装规则：

| 新输出节点 | 输入 | 输出 | 组合示例 |
|-----------|------|------|---------|
| `AvatarAnimationNode` | Flux<TextChunk> | Flux<AnimationFrame> | ModelNode → AvatarAnimationNode |
| `SensitiveWordFilterNode` | String (批量) | FilterResult | 插入到 ModelNode 之前做输入过滤 |
| `TextPolisherNode` | Flux<TextChunk> | Flux<TextChunk> | ModelNode → TextPolisherNode → TtsNode |

### 18.3 模型供应商切换

替换 `ModelChatOutputNode` 内部的 `ChatModelFactory` 实现或配置，不影响任何其他节点。

### 18.4 多模型链式调用

支持在 OutputPipeline 中串联多个 ModelNode：

```
[ModelNode(reasoning)] → [ModelNode(polish)] → [TtsNode]
```

只需在 `OutputPipelineFactory` 中支持更灵活的组装规则即可。

### 18.5 条件分支管道

未来可扩展到条件分支：

```
[ModelNode] → [RouterNode]
                 ├── 普通对话 → [TtsNode]
                 └── 工具调用 → [ToolCallNode] → [ModelNode] → [TtsNode]
```

---

## 19. 待讨论的开放问题

### 19.1 OutputContext 的装配时机

是由 `ConversationMessageProcessor`（编排器）装配好后传给 OutputPipeline，还是 Pipeline 内部懒加载？

- 方案 A（推荐）：编排器装配。更接近现有架构（编排器已有加载逻辑），Pipeline 更纯粹
- 方案 B：Pipeline 内部懒加载。Pipeline 更自包含，但依赖范围变大

### 19.2 SideEffect 的执行保证

如果 TtsNode 执行中失败（如 TTS 网络超时），ModelNode 嵌入的 `DATABASE → ON_COMPLETE` SideEffect 是否应保证已执行？

- 方案 A：SideEffect 在产生时立即执行（而非等下游消费）。简单但不支持背压
- 方案 B：SideEffect 随流传播，但 Pipeline 捕获错误时执行已声明的 ON_COMPLETE 副作用。更复杂但语义正确
- 方案 C：DATABASE 存储不通过 SideEffect 处理，而是由 Pipeline 最终统一处理。与当前架构最接近

### 19.3 流式数据的 SideEffect 时序

ModelNode 声明了 `PER_CHUNK → WEBSOCKET → 推前端文本弹幕`。如果 TtsNode 消费 mainOutput 比 ModelNode 生产慢（背压），前端文本弹幕是否也会延迟？

- 默认：是（背压传导）。这保证了"用户看到的文本和听到的语音同步"
- 如果需要解耦：PipelineFactory 使用 `Flux.share()` 让 WEBSOCKET SideEffect 拥有独立的消费路径

### 19.4 节点实例化策略

节点是每次创建新实例（原型模式），还是复用单例？

- 推荐：**每次时间轮触发时创建新的 Pipeline + Node 实例**。因为每个会话的状态（conversationId、userId、streamHolder）是对话级别的，不适合复用
- 轻量级配置（如 ChatModel 实例）可在节点内部通过 Factory/Manager 获取，不私有化
- 重量级连接（ASR/TTS WebSocket）通过 ConnectionManager 管理，Node 只是引用，可以随 Pipeline 一起销毁而不断开底层连接

### 19.5 错误处理策略

管道中某个节点失败时，是否应该中止整个管道？重试机制如何处理？

- `PER_CHUNK` 级别的错误：日志记录，SideEffect 失败不阻断主流
- `主流级`的错误：Pipeline 中止，通知前端，已执行的 SideEffect 不回滚
- 外部服务（ASR/TTS/LLM）的瞬态故障：由节点内部的 ConnectionManager/Client 负责重试，Pipeline 只感知最终成功/失败

### 19.6 性能监控

每个节点的处理耗时、吞吐量如何监控？可结合现有的 AOP 日志体系，为 OutputNode.process() 添加 `NodeExecutionAspect` 进行拦截记录。

---

## 20. 总结

### 核心思路

1. **三层分离**：输入层（Adapter）→ 中间存储层（不变）→ 输出层（Pipeline），时间轮作为异步断点

2. **输入层不作管道串联**：使用简单 Adapter 模式，各 Adapter 独立处理不同的输入类型

3. **输出层使用管道模式**：`OutputNode` 接收 `OutputContext`（多类型数据）+ `upstream Flux`（上游流），产出 `ProcessingResult`（主流 + 嵌入副作用）

4. **标准消息契约**：`TextChunk`、`AudioChunk` 作为节点间显式契约，解耦"识别"与"转换"

5. **多通道副作用**：通过 `SideEffect` 声明将 WebSocket 推送、数据库存储等输出行为嵌入 `ProcessingResult`，随主数据流传播

### v1 vs v2 关键改进

| 维度 | v1 设计 | v2 修订设计 |
|------|---------|------------|
| 架构分层 | 单层 Pipeline 覆盖全流程 | 三层：输入层 + 存储层 + 输出层 |
| 输入层 | 也套用 Pipeline | 简单 Adapter，不串联 |
| 时间轮 | 未体现 | 明确为输入→输出的异步断点 |
| 节点输入 | 单一类型 `Flux<I>` | `OutputContext` + `upstream Flux` |
| 节点输出 | 单一 `Flux<O>` | `ProcessingResult`（主流 + SideEffect） |
| 多通道输出 | 外部 Listener 注入 | 嵌入在 ProcessingResult 中随流传播 |
| I/O 模式 | 仅流式（Flux→Flux） | 支持四种模式（BATCH/STREAM × IN/OUT） |
| 节点间契约 | 隐式类型约定（Flux<String>） | 显式标准契约（TextChunk, AudioChunk） |
| 识别/转换 | 下游隐式知道上游格式 | 通过标准契约解耦，转换职责在生产者侧 |