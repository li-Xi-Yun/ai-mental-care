## OutputDataType枚举

```java
/** 管道中流动的数据类型 */
public enum OutputDataType {
    TEXT,
    AUDIO
}
```

## OutputNode 接口

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

## OutputPipeline 类

```java
/**
 * 输出层管道——管理多个 OutputNode 的有序组合
 *
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
     * 获取该节点中的Socket路径和对应的说明信息
     * @return NodeEndpoint 包含Socket路径 + 说明
     */
    public List<NodeEndpoint> getSocketInfo(){
        List<NodeEndpoint> list = new ArrayList();
        for (OutputNode node : nodes) {
            list.addAll(node.getSocketInfo());
        }
        return list;
    }
}
```

## OutputPipelineFactory类


```java
/**
 * 输出管道组装工厂
 *
 * <p>职责：
 * <ul>
 *   <li>根据 outputType 组装节点链（ModelNode、TtsNode 等）</li>
 *   <li>校验相邻节点的输入输出类型是否适配</li>
 *   <li>对需要资源初始化的节点进行 init()</li>
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
     */
    private List<OutputNode> buildNodeChain(Set<OutputDataType> outputTypes) {
        boolean needAudio = outputTypes.contains(OutputDataType.AUDIO);

        if (needAudio) {
            // 音频输出需要 ModelNode 提供文本流 → TtsNode 消费
            return List.of(modelNode, ttsNode);
        } else {
            // 纯文本输出（或其他不依赖 TTS 的模式）
            return List.of(modelNode);
        }
    }
}
```

## OutputPipelineSessionManager类


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
     */
    public void initSession(Long conversationId, Long userId, Set<OutputDataType> outputTypes) {
        // 先销毁旧管道
        OutputPipeline old = sessionPipelines.remove(conversationId);
        if (old != null) {
            old.destroy();
        }
        
        // 1. Factory 组装节点链 → 校验类型 → 产出 Pipeline
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

## NodeEndpoint类


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

## OutputContext类


```java
/**
 * 输出上下文——贯穿整个管道执行过程的状态池
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>fluxStore</b>：存储各节点产出的 Flux 流，key 为节点产出标识（如 "model_output"）</li>
 *   <li><b>dataStore</b>：存储非流式数据（诊断结果、意图标签等），key 为数据标识</li>
 *   <li><b>outputTypes</b>：前端请求的输出模式集合，各节点据此决定是否附加推送副作用</li>
 * </ul>
 *
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