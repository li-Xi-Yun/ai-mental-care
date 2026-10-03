# AI 对话输入层设计

> **文档状态**：设计讨论稿  
> **创建日期**：2026-09-29  
> **最后更新**：2026-09-29  
> **讨论范围**：输入层 InputAdapter 设计，解耦前端数据接收、转换、存储与实时回显  
> **注意**：本文档仅涉及输入层，输出层设计见 `output-layer-pipeline-design.md`

---

## 目录

1. [背景与动机](#1-背景与动机)
2. [现状分析与耦合问题](#2-现状分析与耦合问题)
3. [整体流程设计](#3-整体流程设计)
4. [架构总览：三层分离模型](#4-架构总览三层分离模型)
5. [InputAdapter 接口设计](#5-inputadapter-接口设计)
6. [InputAdapterSessionManager 会话适配器管理器](#6-inputadaptersessionmanager-会话适配器管理器)
7. [TextInputAdapter 文本输入适配器](#7-textinputadapter-文本输入适配器)
8. [AudioInputAdapter 音频输入适配器](#8-audioinputadapter-音频输入适配器)
9. [Controller 层设计](#9-controller-层设计)
10. [中间存储层设计](#10-中间存储层设计)
11. [与现有架构的融合](#11-与现有架构的融合)
12. [生命周期管理](#12-生命周期管理)
13. [扩展性与未来规划](#13-扩展性与未来规划)
14. [与输出层的关系](#14-与输出层的关系)
15. [待讨论的开放问题](#15-待讨论的开放问题)
16. [总结](#16-总结)

---

## 1. 背景与动机

### 1.1 目标

实现一个 **可选输入侧数据类型 × 多选输出侧数据类型** 的对话系统。用户在前端选择输入和输出模式后，后端自动组装对应的处理链路，实现解耦的、可组合的数据处理。

### 1.2 当前支持的组合

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

LLM 输出文本到 TTS 的级联逻辑写死在 `ChatMessageProcessor` 内部，`TextMessageProcessor` 和 `ChatMessageProcessor` 是独立类，没有任何复用。

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

## 3. 整体流程设计

### 3.1 资源初始化

```
1. 客户端发送 HTTP 请求到服务器，参数：inputTypes（可多选）、outputType

2. 服务器根据 outputType 组装输出层管道：
     ├── 校验各节点输入输出类型是否适配
     ├── 对需要资源初始化的节点进行 init()
     └── 将管道与会话ID绑定，放入会话管道 Map

3. 输入层：InputAdapterSessionManager.register(inputTypes, userId, conversationId)
     ├── InputAdapterFactory.assemble(inputTypes) → 根据类型查找对应适配器
     ├── adapter.init(userId, conversationId) → 统一初始化（如 ASR 连接注册）
     └── 纳入 sessionAdapters Map 管理

4. 校验与初始化完成后，返回各节点 Socket 路径给客户端
```

### 3.2 请求发送

```
1. 客户端根据 HTTP 或 Socket 路径发送数据到指定 Controller 接口

2. 各 Adapter 接收并处理后：
     ├── 将处理好的数据写入存储层
     ├── 根据需要推送数据给客户端（可选）
     │     ├── 文本输入无需推送
     │     └── 音频输入需要实时推送 ASR 中间结果
     └── 根据需要启动时间轮任务（可选）
           └── 实时表情识别、生理特征等不需要时间轮
```

### 3.3 对话处理

```
1. 时间轮调度后，根据会话ID从管道Map中获取 OutputPipeline

2. 执行管道——各节点依次处理：
     ├── 接收上一个节点处理完的数据
     ├── 从存储层获取所需上下文（可选）
     └── 将节点产出的数据发送到客户端（可选）
```

### 3.4 中断处理

```
客户端发送 HTTP 或 Socket 到服务器
    → 根据会话ID从管道Map获取 OutputPipeline
    → pipeline.interrupt()
    → 中断管道中各节点
```

### 3.5 销毁处理

```
客户端发送 HTTP 到服务器
    → 根据会话ID从管道Map获取 OutputPipeline
    → pipeline.destroy()
    → 销毁各节点资源
    → 销毁适配器资源
```

---

## 4. 架构总览：三层分离模型

### 4.1 核心洞察

输入侧和输出侧是**两个独立的时间片段**，通过时间轮异步连接：

```
前端发送数据 → 输入层处理/存储 → 等待时间轮 [异步断点] → 输出层处理 → 前端接收结果
```

因此需要三层分离架构：

```
┌──────────────────────────────────────────────────────────────┐
│                  输入层 (Input Layer)                         │
│  职责：接收前端数据 → 转换 → 存储 → 可选实时回显                │
│  模式：独立 Adapter，每个对应一个 Controller 端点               │
│  时机：实时响应（同步执行）                                      │
│                                                               │
│  TextInputController  → TextInputAdapter                      │
│  AudioInputController → AudioInputAdapter                     │
│  文字消息 → 存储到DB     ASR 识别 → 文本 → 存储到DB              │
│                          (中间识别结果实时推前端)                 │
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
│  详见：output-layer-pipeline-design.md                         │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 为什么输入层不需要管道？

- 各 Adapter 之间**不存在上下游级联关系**，不需要 Pipeline
- 每个 Adapter 有**独立的 Controller 端点**，客户端直接向对应路径发送数据
- `InputAdapterFactory` 负责根据 `InputDataType` 查找匹配的 Adapter，实现"一个类型对应一个适配器"的映射
- 每个 InputAdapter 是独立的单向处理器：接收 → 转换 → 存储 → 可选推送

---

## 5. InputAdapter 接口设计

### 5.1 InputDataType 枚举

```java
/** 管道中流动的数据类型 */
public enum InputDataType {
    TEXT,
    AUDIO
}
```

### 5.2 接口定义

```java
/**
 * 输入适配器——仅定义生命周期，不定义统一的数据处理签名
 *
 * <p>设计要点：
 * <ul>
 * <li>不统一定义 handleInput 方法签名——不同 Adapter 接收的数据类型不同，
   *       文本接收 String、音频接收 byte[]、视频接收二进制流，统一签名反而引入强制转型</li>
   * <li>不统一定义返回值——存储是 Adapter 内部行为，不应把 StandardMessage 返回给外部</li>
   * <li>推送逻辑直接内聚在 Adapter 中，直接调用 ConversationWebSocketManager</li>
   * <li>接口的作用是提供统一的生命周期契约，具体数据处理方法由各子类自行定义</li>
 * </ul>
 */
public interface InputAdapter {

    /**
     * 返回该适配器支持的输入数据类型
     *
     * <p>用于工厂在组装时根据 InputDataType 分发适配器，一个适配器对应一种输入类型</p>
     */
    InputDataType getSupportedType();

    /** 为指定会话初始化资源（如注册 ASR 连接、分配缓冲区） */
    void init(Long userId, Long conversationId);

    /** 销毁会话持有的资源（如取消 ASR 连接、清空缓冲区） */
    void destroy();

    /** 中断正在进行的处理（保留资源和连接，仅中止当前操作） */
    void interrupt();
}
```

### 5.3 方法职责说明

| 方法 | 职责 | 调用时机 |
|------|------|---------|
| `getSupportedType()` | 返回适配器支持的输入数据类型，供工厂分发 | 工厂组装时调用 |
| `init()` | 初始化资源（ASR 连接、缓冲区等） | `initSession` 时调用 |
| `destroy()` | 销毁资源、关闭连接、清空状态 | `endSession` 时调用 |
| `interrupt()` | 中止当前操作，保留连接以备下次使用 | 用户中断时调用 |

### 5.4 设计原则

1. **每个 Adapter 有专用的数据处理方法**：`TextInputAdapter.handleTextInput()`、`AudioInputAdapter.handleAudioFrame()` 等
2. **推送逻辑内聚在 Adapter 中**：直接调用 `ConversationWebSocketManager`，不通过中间层抽象
3. **存储是 Adapter 内部行为**：不在接口中声明，外部不参与
4. **不存在 Adapter 之间的级联**：不会出现 `AdapterA` 的输出作为 `AdapterB` 的输入

### 5.5 InputAdapterFactory 适配器工厂

```java
/**
 * 输入适配器工厂
 *
 * <p>核心职责：根据调用方传入的输入方式（InputDataType）集合，完成适配器的查找与组装。
 * 是输入层"组装配置"的唯一入口，只做纯查找，不负责资源初始化（init 由 InputAdapterSessionManager 统一调用）。</p>
 *
 * <p>设计要点：
 * <ul>
 * <li>Spring 自动注入所有 InputAdapter 实现，按 getSupportedType() 建立映射表</li>
 * <li>assemble() 仅根据 inputTypes 查找对应适配器并返回，不产生副作用</li>
 * <li>新增输入类型只需新增一个 InputAdapter 实现类，工厂零改动</li>
 * </ul>
 */
@Component
public class InputAdapterFactory {

    /**
     * InputDataType → InputAdapter 映射表
     *
     * <p>由 Spring 构造器注入所有 InputAdapter Bean，按 getSupportedType() 自动建立索引</p>
     */
    private final Map<InputDataType, InputAdapter> adapterMap;

    /**
     * Spring 自动注入所有 InputAdapter 实现类
     *
     * <p>如果存在同一 InputDataType 对应多个实现，保留第一个（可通过 @Order 控制优先级）</p>
     */
    public InputAdapterFactory(List<InputAdapter> adapters) {
        this.adapterMap = adapters.stream()
                .collect(Collectors.toMap(
                        InputAdapter::getSupportedType,
                        Function.identity(),
                        (existing, replacement) -> existing));
    }

    /**
     * 根据输入方式集合，查找并返回对应的适配器列表（纯查找，不做初始化）
     *
     * <p>调用时机：InputAdapterSessionManager.register() 内部调用</p>
     *
     * @param inputTypes 客户端请求的输入方式集合（例如 {TEXT, AUDIO}）
     * @return 匹配的适配器列表（未初始化，顺序与 inputTypes 遍历顺序一致）
     * @throws IllegalArgumentException 如果 inputTypes 为空
     * @throws UnsupportedOperationException 如果某个 InputDataType 没有对应的适配器实现
     */
    public List<InputAdapter> assemble(Set<InputDataType> inputTypes) {
        if (inputTypes == null || inputTypes.isEmpty()) {
            throw new IllegalArgumentException("inputTypes must not be empty");
        }

        List<InputAdapter> assembled = new ArrayList<>(inputTypes.size());
        for (InputDataType type : inputTypes) {
            InputAdapter adapter = adapterMap.get(type);
            if (adapter == null) {
                throw new UnsupportedOperationException(
                        "No InputAdapter registered for type: " + type);
            }
            assembled.add(adapter);
        }
        return assembled;
    }
}
```

---

## 6. InputAdapterSessionManager 会话适配器管理器

### 6.1 定位

`InputAdapterSessionManager` 是输入层的**唯一编排入口**——负责调用 `InputAdapterFactory` 组装适配器、统一初始化、收集管理。对外提供注册、按类型查找、中断、销毁等生命周期操作。

### 6.2 完整实现

```java
/**
 * 输入层会话适配器管理器
 *
 * <p>输入层唯一编排入口——负责调用 InputAdapterFactory 组装适配器、统一初始化、收集管理。
 * 对外提供注册、按类型查找、中断、销毁等生命周期操作。</p>
 */
@Component
public class InputAdapterSessionManager {

    private final InputAdapterFactory factory;

    /**
     * conversationId → 该会话持有的所有 Adapter 列表
     *
     * <p>支持单会话多 Adapter 组合（例如：音频输入 + 视频输入 同时启用）</p>
     */
    private final ConcurrentHashMap<Long, List<InputAdapter>> sessionAdapters = new ConcurrentHashMap<>();

    /**
     * 根据会话ID与输入数据类型，获取会话下对应的输入适配器
     *
     * @param conversationId 会话唯一标识
     * @param type           输入数据类型
     * @return 匹配类型的输入适配器
     * @throws IllegalStateException 如果会话不存在
     */
    public InputAdapter getInputAdapter(Long conversationId, InputDataType type) {
        List<InputAdapter> adapterList = sessionAdapters.get(conversationId);
        if (adapterList == null) {
            throw new IllegalStateException("No session found: " + conversationId);
        }
        return adapterList.stream()
                .filter(item -> item.getSupportedType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * 注册适配器——工厂组装 + 统一初始化 + 纳入会话管理
     *
     * @param inputTypes     客户端请求的输入方式集合
     * @param userId         用户ID
     * @param conversationId 会话ID
     * @throws IllegalStateException 如果该会话已被注册
     */
    public void register(Set<InputDataType> inputTypes, Long userId, Long conversationId) {
        if (sessionAdapters.containsKey(conversationId)) {
            throw new IllegalStateException("Session already registered: " + conversationId);
        }
        List<InputAdapter> adapterList = factory.assemble(inputTypes);
        adapterList.forEach(item -> item.init(userId, conversationId));
        sessionAdapters.put(conversationId, adapterList);
    }

    /**
     * 销毁会话——取出所有 Adapter 并逆序销毁，同时移除会话记录
     */
    public void endSession(Long conversationId) {
        List<InputAdapter> adapters = sessionAdapters.remove(conversationId);
        if (adapters != null) {
            for (int i = adapters.size() - 1; i >= 0; i--) {
                adapters.get(i).destroy();
            }
        }
    }

    /**
     * 中断会话——中断所有 Adapter 的当前操作（不销毁）
     */
    public void interrupt(Long conversationId) {
        List<InputAdapter> adapters = sessionAdapters.get(conversationId);
        if (adapters != null) {
            adapters.forEach(InputAdapter::interrupt);
        }
    }
}
```

### 6.3 在整体流程中的位置

```
initSession(inputTypes, outputType)
    │
    ├── 输入层（SessionManager 统一组装和初始化）：
    │   │
    │   ├── sessionManager.register(inputTypes, userId, conversationId)
    │   │   ├── factory.assemble(inputTypes)     ← 工厂根据 InputDataType 查找适配器
    │   │   └── adapter.init(userId, cid)         ← 统一初始化
    │   │
    │   └── (后续可通过 getInputAdapter(cid, type) 按类型获取)
    │
    └── 输出层：outputPipelineSessionManager.initSession(...)
        └── [见 output-layer-pipeline-design.md]

... 数据收发阶段 ...
    各 Controller → sessionManager.getInputAdapter(cid, type) → 各 Adapter 专用方法

interrupt(conversationId)
    ├── sessionManager.interrupt(conversationId)
    │   └── sessionAdapters.get(conversationId) → 遍历 interrupt()
    │
    └── outputSessionManager.interrupt(conversationId)

endSession(conversationId)
    ├── sessionManager.endSession(conversationId)
    │   └── sessionAdapters.remove(conversationId) → 遍历 destroy()
    │
    └── outputSessionManager.endSession(conversationId)
```

### 6.4 使用示例：AudioServiceImpl 中的注册流程

```java
// AudioServiceImpl.initSession()
public void initSession(Long userId, Long conversationId,
                        Set<InputDataType> inputTypes, DataType outputType) {

    // 1. 输入层：SessionManager 统一组装 + 初始化 + 管理
    inputAdapterSessionManager.register(inputTypes, userId, conversationId);

    // 2. 输出层初始化 ...
}

// endSession
public void endSession(Long conversationId) {
    inputAdapterSessionManager.endSession(conversationId);
    outputPipelineSessionManager.endSession(conversationId);
}
```

---

## 7. TextInputAdapter 文本输入适配器

```java
/**
 * 文本输入适配器
 *
 * <p>职责：接收 WebSocket/HTTP 文字消息 → 存储到消息表 → 回显确认 → 启动时间轮</p>
 */
@Component
class TextInputAdapter implements InputAdapter {

    private final ConversationHistoryMessagesStorage messagesStorage;
    private final ConversationWebSocketManager wsManager;
    private final ConversationAggregateScheduler scheduler;

    // ==================== 专用方法 ====================

    /**
     * 处理文本输入
     * <p>仅由 TextInputController 调用，不通过统一接口分发</p>
     */
    public void handleTextInput(Long userId, Long conversationId, String text) {
        // 1. 构造标准消息并写入消息表
        StandardMessage message = StandardMessage.builder()
                .userId(userId)
                .conversationId(conversationId)
                .content(text)
                .dataType(DataType.TEXT)
                .role(MessageRole.USER)
                .build();
        messagesStorage.storeTemporaryMessage(conversationId, message);

        // 2. 回显确认——直接调用 WebSocketManager，不需要 EchoListener
        wsManager.sendStatus(userId, conversationId, "received");

        // 3. 启动/更新时间轮任务（等待调度后触发输出层）
        scheduler.scheduleOrUpdate(conversationId);
    }

    // ==================== 生命周期 ====================

    @Override
    public void init(Long userId, Long conversationId) {
        // 文本适配器无状态，无需初始化
    }

    @Override
    public void destroy() {
        // 无资源需释放
    }

    @Override
    public void interrupt() {
        // 同步操作，无运行中任务需中断
    }
}
```

### 6.1 调用时序

```
用户在前端输入文字并发送
    │
    ▼
WebSocket / HTTP 消息到达 TextInputController
    │
    ▼
textAdapter.handleTextInput(userId, conversationId, "你好")
    │
    ├──→ messagesStorage.storeTemporaryMessage(conversationId, message)
    │       └── 写入 MySQL temporary_messages 表
    │
    ├──→ wsManager.sendStatus(userId, conversationId, "received")
    │       └── WebSocket 推送回显确认到前端
    │
    └──→ scheduler.scheduleOrUpdate(conversationId)
            └── 启动/更新时间轮，等待到期后触发输出层 Pipeline
```

---

## 8. AudioInputAdapter 音频输入适配器

```java
/**
 * 音频输入适配器
 *
 * <p>职责：接收 WebSocket 音频帧 → ASR 识别 → 中间结果实时推前端 → 最终文本存储到消息表</p>
 */
@Component
class AudioInputAdapter implements InputAdapter {

    private final AsrConnectionManager asrManager;
    private final ConversationHistoryMessagesStorage messagesStorage;
    private final ConversationWebSocketManager wsManager;
    private final ConversationAggregateScheduler scheduler;

    // ==================== 专用方法 ====================

    /**
     * 处理音频帧——仅由 AudioInputController 调用
     */
    public void handleAudioFrame(Long userId, Long conversationId, byte[] audioData) {
        asrManager.sendAudio(userId, audioData);
    }

    /**
     * 发送音频流结束信号，通知 ASR 引擎音频已全部发送
     */
    public void handleEndOfStream(Long userId) {
        asrManager.sendEndOfStream(userId);
    }

    // ==================== ASR 异步回调（内部） ====================

    private void onAsrSentenceBegin(Long userId, Long conversationId) {
        // 通知前端：AI 检测到用户开始说话
        wsManager.sendStatus(userId, conversationId, "speaking");
    }

    private void onAsrPartialResult(Long userId, Long conversationId, String partialText) {
        // ★ 实时推送 ASR 中间结果——不经存储层，直接推前端
        //   用户看到 "你好" → "你好，我" → "你好，我今天心情不好" → ...
        wsManager.sendTextStream(userId, conversationId, partialText);
    }

    private void onAsrFinalResult(Long userId, Long conversationId, String finalText) {
        // 1. 最终识别文本存入消息表（供时间轮调度后处理）
        StandardMessage message = StandardMessage.builder()
                .userId(userId)
                .conversationId(conversationId)
                .content(finalText)
                .dataType(DataType.TEXT)
                .role(MessageRole.USER)
                .build();
        messagesStorage.storeTemporaryMessage(conversationId, message);

        // 2. 通知前端：识别完成
        wsManager.sendStatus(userId, conversationId, "recognized");

        // 3. 启动时间轮
        scheduler.scheduleOrUpdate(conversationId);
    }

    private void onAsrError(Long userId, Long conversationId, String errorMessage) {
        wsManager.sendStatus(userId, conversationId, "error");
    }

    // ==================== 生命周期 ====================

    @Override
    public void init(Long userId, Long conversationId) {
        // 注册 ASR 连接——将内部回调方法绑定到 ASR 引擎
        asrManager.register(userId, new AsrResultCallback() {
            @Override
            public void onSentenceBegin() {
                onAsrSentenceBegin(userId, conversationId);
            }
            @Override
            public void onPartialResult(String text) {
                onAsrPartialResult(userId, conversationId, text);
            }
            @Override
            public void onSentenceEnd(String finalText) {
                onAsrFinalResult(userId, conversationId, finalText);
            }
            @Override
            public void onError(String errorMessage) {
                onAsrError(userId, conversationId, errorMessage);
            }
        });
    }

    @Override
    public void destroy() {
        asrManager.cancel(userId);
    }

    @Override
    public void interrupt() {
        asrManager.interrupt(userId);
    }
}
```

### 7.1 ASR 回调 → 回显 vs 存储（时序图）

```
时间 ─────────────────────────────────────────────────────▶

前端看到的内容（实时，不经过存储层）：
  "（聆听中...）"  →  "你好" → "你好，我今天" → "你好，我今天心情" → "你好，我今天心情不太好"
  [sendStatus]      [sendTextStream]  [sendTextStream]  [sendTextStream]  [sendTextStream]
       ↑                  ↑               ↑               ↑               ↑
    ASR 回调：sentenceBegin   partialResult   partialResult   partialResult   sentenceEnd
       │                  │               │               │               │
       │                  │               │               │               └→ storeTemporaryMessage("你好，我今天心情不太好")
       │                  │               │               │                   ↑ 仅最终结果写入存储层
       │                  │               │               │
       └──────────────────┴───────────────┴───────────────┘
              这些中间结果不经存储层，直接通过 wsManager 推前端
```

该设计保证：**前端实时看到用户说了什么（即时反馈），但只有最终完整的识别文本进入消息表供 LLM 处理**。

### 7.2 为什么不把 handleInput 设计成阻塞等待 ASR 结果？

- ASR 识别是异步过程，音频发送和识别结果返回之间有延迟
- 阻塞等待会导致 WebSocket 线程被占用，无法及时接收下一帧音频
- 中间识别结果通过 ASR 回调异步推前端，不需要阻塞返回

---

## 9. Controller 层设计

### 9.1 设计原则

每个 InputAdapter 对应一个独立的 Controller 端点。客户端在 `initSession` 阶段拿到各端点路径后，直接向对应路径发送数据：

```
                     ┌──────────────────┐
                     │    initSession   │
                     │   (HTTP)          │
                     │  inputType=TEXT   │
                     │  outputType=AUDIO │
                     └────────┬─────────┘
                              │ 返回各节点Socket路径
                              ▼
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ TextInput-    │    │ AudioInput-   │    │ (未来)Video-  │
│ Controller    │    │ Controller    │    │ InputContr.   │
│               │    │               │    │               │
│ /api/conv/    │    │ /ws/audio/    │    │ /ws/video/    │
│ {id}/text     │    │ {id}          │    │ {id}          │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ TextInput-    │    │ AudioInput-   │    │ VideoInput-   │
│ Adapter       │    │ Adapter       │    │ Adapter       │
└───────────────┘    └───────────────┘    └───────────────┘
```

### 9.2 TextInputController

```java
@RestController
@RequestMapping("/api/conversation")
class TextInputController {

    private final TextInputAdapter textAdapter;

    @PostMapping("/{conversationId}/text")
    public void handleText(
            @PathVariable Long conversationId,
            @RequestBody String text) {

        Long userId = getCurrentUserId();   // 从安全上下文获取
        textAdapter.handleTextInput(userId, conversationId, text);
    }
}
```

### 9.3 AudioInputController

```java
@Controller
class AudioInputController {

    private final AudioInputAdapter audioAdapter;

    /**
     * WebSocket 音频帧端点
     * <p>客户端通过 WebSocket 持续发送音频帧到此路径</p>
     */
    @MessageMapping("/ws/audio/{conversationId}")
    public void handleAudioBinary(
            @DestinationVariable Long conversationId,
            byte[] audioData) {

        Long userId = getCurrentUserId();
        audioAdapter.handleAudioFrame(userId, conversationId, audioData);
    }

    /**
     * 客户端发送音频结束信号（如松开录音按钮）
     */
    @MessageMapping("/ws/audio/{conversationId}/end")
    public void handleAudioEnd(@DestinationVariable Long conversationId) {
        Long userId = getCurrentUserId();
        audioAdapter.handleEndOfStream(userId);
    }
}
```

---

## 10. 中间存储层设计

### 10.1 保持不变

中间存储层沿用现有基础设施，无需改动：

| 组件 | 职责 |
|------|------|
| `ConversationHistoryMessagesStorage` | MySQL 消息存储（temporary_messages、历史消息） |
| `ConversationCacheManager` | Redis 缓存（会话上下文、情绪分析、诊断结果、压缩摘要） |
| `ConversationAggregateScheduler` | 时间轮调度器（到期触发输出层执行） |

### 10.2 输入层与存储层的关系

输入层**只写入**，不读取：

```
TextInputAdapter.handleTextInput()
    └──→ messagesStorage.storeTemporaryMessage()

AudioInputAdapter.onAsrFinalResult()
    └──→ messagesStorage.storeTemporaryMessage()

时间轮到期后
    │
    └──→ 编排器从 MySQL + Redis 读取 (OutputContext)
         └──→ 传给输出层 Pipeline
```

---

## 11. 与现有架构的融合

### 11.1 现有架构

```
Controller / AudioServiceImpl
    │
    ├── initSession()  → 创建 ASR + TTS 连接
    ├── sendAudioMessage() → 发送音频数据
    ├── stopSpeaking() → 发送结束信号
    ├── interruptAudio() → 中断处理
    └── endSession() → 销毁所有资源
```

### 11.2 目标架构

```
AudioServiceImpl
    │
    ├── initSession(inputTypes, outputType)
    │   │
    │   ├── 输入层：
    │   │   └── inputAdapterSessionManager.register(inputTypes, userId, conversationId)
    │   │       ├── factory.assemble(inputTypes)     ← 工厂根据 InputDataType 查找适配器
    │   │       └── adapter.init(userId, cid)         ← 统一初始化
    │   │
    │   └── 输出层：根据 outputType 组装 OutputPipeline
    │       └── [见 output-layer-pipeline-design.md]
    │
    ├── 数据发送：各自 Controller → sessionManager.getInputAdapter(cid, type) → 专用方法
    ├── interrupt() → inputAdapterSessionManager.interrupt(cid) + pipeline.interrupt()
    └── endSession()
        ├── inputAdapterSessionManager.endSession(conversationId)
        └── pipeline.destroy()
```

### 11.3 输入层改动点清单

| 模块 | 改动内容 | 改动程度 |
|------|---------|---------|
| **新增** `InputDataType` 枚举 | 输入数据类型定义（TEXT、AUDIO） | 新增 |
| **新增** `InputAdapter` 接口 | 输入适配器抽象（getSupportedType + init/destroy/interrupt） | 新增 |
| **新增** `InputAdapterFactory` | 适配器工厂（Spring 注入 + 按类型映射查找） | 新增 |
| **新增** `TextInputAdapter` | 文本输入适配器（handleTextInput + 生命周期 + getSupportedType） | 新增 |
| **新增** `AudioInputAdapter` | 音频输入适配器（handleAudioFrame + ASR 回调 + 生命周期 + getSupportedType） | 新增 |
| **新增** `TextInputController` | 文本输入 HTTP 端点 | 新增 |
| **新增** `AudioInputController` | 音频输入 WebSocket 端点 | 新增 |
| **新增** `InputAdapterSessionManager` | 会话级 Adapter 组装、收集与生命周期管理 | 新增 |
| **新增** `StandardMessage` 类 | 标准消息格式，写入存储层（共享契约） | 新增 |
| **改** `AudioServiceImpl` | initSession/endSession 委托给 InputAdapterSessionManager | 改造 |
| **不变** `AsrConnectionManager` | 调用方从 AudioServiceImpl → AudioInputAdapter | 不变 |
| **不变** `ConversationHistoryMessagesStorage` | 仅写入 temporary_messages | 不变 |
| **不变** `ConversationWebSocketManager` | 仅 Adapter 内部直接调用（不再通过 EchoListener） | 不变 |
| **不变** `ConversationAggregateScheduler` | 时间轮调度逻辑不变 | 不变 |

---

## 12. 生命周期管理

### 12.1 当前模式

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

### 12.2 三层模式

```
initSession(inputTypes, outputType) {
    // 输入层：SessionManager 根据 inputTypes 组装 + 初始化 + 注册
    inputAdapterSessionManager.register(inputTypes, userId, conversationId)
        ├── factory.assemble(inputTypes)     ← 工厂根据 InputDataType 查找适配器
        └── adapter.init(userId, cid)         ← 统一初始化（如 AudioInputAdapter → asrManager.register）

    // 输出层：[见 output-layer-pipeline-design.md]
}

endSession(conversationId) {
    // 输入层清理（通过 SessionManager 统一操作）
    inputAdapterSessionManager.endSession(conversationId)
        └── if AudioInputAdapter → asrManager.cancel(userId)

    // 输出层：[见 output-layer-pipeline-design.md]
}
```

### 12.3 输入模式切换

输入模式在 `initSession` 时确定，会话期间不切换。如需切换，先 `endSession` 再重新 `initSession`：

```java
// 从 TEXT 切换到 AUDIO
endSession(oldConversationId);
initSession(newConversationId, inputType=AUDIO, outputType=...);
```

---

## 13. 扩展性与未来规划

### 13.1 新增输入类型

只需三步：

**步骤一**：实现新的 `InputAdapter`

```java
@Component
class VideoInputAdapter implements InputAdapter {

    private final VideoAnalysisService videoService;
    private final ConversationHistoryMessagesStorage messagesStorage;
    private final ConversationWebSocketManager wsManager;

    // 专用方法
    public void handleVideoFrame(Long userId, Long conversationId, byte[] frameData) {
        // 关键帧提取 → 文本描述 → 存储 → 可选回显
        String description = videoService.analyze(frameData);
        messagesStorage.storeTemporaryMessage(conversationId,
                StandardMessage.builder().content(description)...build());
        wsManager.sendStatus(userId, conversationId, "frame_processed");
    }

    @Override
    public void init(Long userId, Long conversationId) {
        videoService.openSession(userId, conversationId);
    }

    @Override
    public void destroy() {
        videoService.closeSession(userId);
    }

    @Override
    public void interrupt() {
        videoService.interrupt(userId);
    }
}
```

**步骤二**：新增 Controller 端点

```java
@Controller
class VideoInputController {

    @Autowired private VideoInputAdapter videoAdapter;

    @MessageMapping("/ws/video/{conversationId}")
    public void handleVideoFrame(
            @DestinationVariable Long conversationId, byte[] frameData) {
        videoAdapter.handleVideoFrame(getCurrentUserId(), conversationId, frameData);
    }
}
```

**步骤三**：无需修改任何现有代码——Spring 自动注入新 Adapter 到 `InputAdapterFactory`

```java
// VideoInputAdapter 实现 InputAdapter 接口并标注 @Component，
// InputAdapterFactory 通过构造器注入 List<InputAdapter> 自动发现，
// 按 getSupportedType() 建立映射，无需额外配置。
```

通过 `InputAdapterFactory` 的 Spring 自动注入机制，新增输入类型只需实现 `InputAdapter` 接口并标注 `@Component`，工厂零改动，`AudioServiceImpl` 也无需修改。

### 13.2 适配器的独立演进

新增 `VideoInputAdapter` 时：
- **不影响** `TextInputAdapter` 和 `AudioInputAdapter`
- **不影响** 任何输出层节点
- **不影响** 中间存储层

### 13.3 未来输入类型展望

| 新输入类型 | 新 Adapter | 核心动作 |
|-----------|-----------|---------|
| 视频 | `VideoInputAdapter` | 关键帧提取 → 文本描述 → 存储到消息表 |
| 图像 | `ImageInputAdapter` | 视觉理解 API → 文本 → 存储到消息表 |
| 生理数据 | `BioSignalInputAdapter` | 心率/脑电 → 特征提取 → 结构化文本 → 存储到消息表 |
| 表情捕获 | `FaceExpressionAdapter` | 摄像头 → BlendShape 权重 → JSON → 存储到消息表 |

---

## 14. 与输出层的关系

### 14.1 接口抽象：完全独立

```
输入侧                           输出侧
══════════                      ══════════
InputAdapter 接口                OutputNode 接口
  ├── init()                      ├── getNodeName()
  ├── destroy()                   ├── getRequiredContextTypes()
  └── interrupt()                 ├── getOutputType()
                                  ├── getIOMode()
        △                         ├── init() / destroy() / interrupt()
        │                         └── process(OutputContext, Flux<?>)
  ┌─────┴─────┐                         △
  │           │                         │
TextInput   AudioInput          ┌───────┴───────┐
Adapter     Adapter             │               │
                            ModelNode        TtsNode
```

### 14.2 生命周期：完全独立（时间解耦）

```
时间线 ─────────────────────────────────────────────────────────▶

[输入侧生命周期]
  initSession  →  handleXxxInput  →  handleXxxInput  →  ...  →  endSession
  (adapter.init)  (接收数据)         (接收数据)                  (adapter.destroy)
                                                                  │
[输出侧生命周期]                                                    │
                    [时间轮触发]                                    │
                    →  createPipeline  →  execute  →  destroy      │
                        可多次触发（每次时间轮到期）                    │
```

### 14.3 实例持有：互不持有

```
InputAdapterSessionManager         OutputPipelineSessionManager
  (管理输入层 Adapter)               (管理输出层 Pipeline)
  │                                    │
  ├── TextInputAdapter                 ├── OutputPipeline
  │     ├── messagesStorage            │     ├── ModelNode
  │     ├── wsManager                  │     └── TtsNode
  │     └── scheduler                  │
  │                                    │
  └── AudioInputAdapter
        ├── asrManager
        ├── messagesStorage
        └── wsManager
```

`InputAdapterSessionManager` 和 `OutputPipelineSessionManager` 是两个独立的 Spring Bean，互相不知道对方的存在。

### 14.4 共享的只是"契约"，不是"实现"

| 共享元素 | 本质 | 是否是耦合？ |
|---------|------|------------|
| `DataType` 枚举 | 纯数据定义，无行为 | 健康的共享常量 |
| `StandardMessage` 类 | 纯数据载体 | 存储层契约，输入层生产、输出层消费，通过存储层间接通信 |
| `ConversationHistoryMessagesStorage` | 基础设施服务 | 输入层写入 temporary_messages；输出层通过 SideEffect 写入最终回复 |
| `ConversationWebSocketManager` | 基础设施服务 | 输入层直接调用做实时回显；输出层通过 SideEffect 做结果推送 |

### 14.5 可独立验证

| 维度 | 说明 |
|------|------|
| 独立编译 | 输入层代码不 `import` 输出层包（除了共享的 `DataType` / `StandardMessage`） |
| 独立测试 | `AudioInputAdapter` 的测试 mock `AsrConnectionManager` + `messagesStorage` + `wsManager`，不需要输出层类 |
| 独立演进 | 新增 `VideoInputAdapter` 不影响任何输出层节点 |

---

## 15. 待讨论的开放问题

### 15.1 会话级别状态管理

`AudioInputAdapter` 在 `init()` 中持有 `userId` 变量作为会话上下文。是否需要引入 `InputSessionContext` 对象统一管理会话状态？

- 方案 A：各 Adapter 自行管理状态（当前设计，简单直接）
- 方案 B：引入 `InputSessionContext` 对象，在 `init()` 时传入

### 15.2 时间轮"可选"的适配器

当前设计中，TextInputAdapter 和 AudioInputAdapter 都无条件启动时间轮调度。如果未来有"不需要 LLM 处理"的适配器（如实时表情识别直接将分析结果推前端，不进 LLM），如何声明"不需要时间轮"？

- 方案 A：在 `InputAdapterSessionManager` 中配置，根据 inputType 决定是否注册时间轮
- 方案 B：Adapter 接口中增加 `boolean requiresTimeWheel()` 方法

### 15.3 输入层错误处理

TextInputAdapter 中如果 `messagesStorage.storeTemporaryMessage()` 失败：

- 方案 A：向上抛异常，由 Controller 返回错误给前端
- 方案 B：存储到死信队列，记录日志并降级，前端认为消息已发送

AudioInputAdapter 中 ASR 识别失败：

- onAsrError 回调中推送错误状态给前端，不写入消息表
- 是否需要一个"超时自动触发时间轮"的机制（即使没有最终识别结果也触发一次输出层）？

### 15.4 性能监控

每个 Adapter 的处理耗时、吞吐量如何监控？可结合现有的 AOP 日志体系，为 Adapter 的专用处理方法添加拦截记录。

---

## 16. 总结

### 核心思路

1. **InputAdapter 仅定义生命周期 + 类型标识**：`getSupportedType()` / `init()` / `destroy()` / `interrupt()` 四个方法，不统一定义数据处理签名
2. **InputAdapterFactory 负责适配器查找与组装**：Spring 自动注入所有实现类，按 `getSupportedType()` 建立映射，`InputAdapterSessionManager.register()` 统一调用工厂组装和初始化
3. **每个 Adapter 有专用方法和独立 Controller 端点**：客户端直接向对应路径发送数据
4. **推送逻辑内聚在 Adapter 中**：直接调用 `ConversationWebSocketManager`，不经过 EchoListener 抽象层
5. **存储是 Adapter 内部行为**：不通过返回值暴露给外部
6. **与输出层完全解耦**：通过中间存储层异步通信，时间轮作为断点

### 输入层核心接口/类一览

| 类型 | 名称 | 职责 |
|------|------|------|
| 枚举 | `InputDataType` | 输入数据类型定义（TEXT、AUDIO） |
| 接口 | `InputAdapter` | 输入适配器抽象（getSupportedType + init/destroy/interrupt） |
| 类 | `InputAdapterFactory` | 适配器工厂（Spring 注入 + 按类型映射查找） |
| 类 | `TextInputAdapter` | 文本输入处理（handleTextInput + 存储 + 回显 + 时间轮） |
| 类 | `AudioInputAdapter` | 音频输入处理（handleAudioFrame + ASR 回调 + 实时推送 + 存储 + 时间轮） |
| 类 | `TextInputController` | 文本输入 HTTP 端点 |
| 类 | `AudioInputController` | 音频输入 WebSocket 端点 |
| 类 | `InputAdapterSessionManager` | 会话级 Adapter 组装、收集与生命周期管理 |
| 类 | `StandardMessage` | 标准消息格式（共享契约，写入存储层） |