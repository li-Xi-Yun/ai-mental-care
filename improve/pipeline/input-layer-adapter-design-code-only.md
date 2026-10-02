## InputDataType枚举

```java
/** 管道中流动的数据类型 */
public enum InputDataType {
    TEXT,
    AUDIO
}
```

## InputAdapter接口

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

## InputAdapterFactory类

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

## InputAdapterSessionManager类

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