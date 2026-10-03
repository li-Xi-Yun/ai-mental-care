package org.lixiyun.server.infrastructure.interaction.adapter;

import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;

import java.util.List;

/**
 * 输入适配器接口——仅定义生命周期契约与类型标识，不定义统一的数据处理签名
 *
 * <p>设计要点：
 * <ul>
 * <li>不统一定义 handleInput 方法签名——不同 Adapter 接收的数据类型不同，
 *     文本接收 String、音频接收 byte[]、视频接收二进制流，统一签名反而引入强制转型</li>
 * <li>不统一定义返回值——存储是 Adapter 内部行为，不应把消息对象返回给外部</li>
 * <li>推送逻辑直接内聚在 Adapter 中，直接调用 ConversationWebSocketManager</li>
 * <li>接口的作用是提供统一的生命周期契约，具体数据处理方法由各子类自行定义</li>
 * </ul>
 *
 * <h3>实例化与身份约定（与输出层节点对称）：</h3>
 * <ul>
 * <li>适配器声明为 <b>prototype</b> 作用域，由 {@code InputAdapterSessionManager} 为每个会话
 *     通过 {@code InputAdapterFactory}（ObjectProvider）创建独立实例</li>
 * <li>{@link #init(Long, Long)} 时将会话身份写入实例字段；{@link #destroy()} / {@link #interrupt()}
 *     <b>不再传参</b>，直接使用 init 注入的字段——实例即会话，会话即实例</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
public interface InputAdapter {

    /**
     * 返回该适配器支持的输入数据类型
     *
     * <p>用于工厂在组装时根据 InputDataType 分发适配器，一个适配器对应一种输入类型</p>
     *
     * @return 支持的输入数据类型
     */
    InputDataType getSupportedType();

    /**
     * 为指定会话初始化资源，并将会话身份写入实例字段
     *
     * <p>实现类应在此方法中将 userId / conversationId 保存为实例字段，
     * 供后续 {@link #destroy()} / {@link #interrupt()} 无参调用时使用。</p>
     *
     * @param userId         用户ID
     * @param conversationId 会话ID
     */
    void init(Long userId, Long conversationId);

    /**
     * 销毁会话持有的资源（如取消 ASR 连接、清空缓冲区）
     *
     * <p>会话身份由 {@link #init(Long, Long)} 时写入的实例字段提供，不再传参。</p>
     */
    void destroy();

    /**
     * 中断正在进行的处理（保留资源和连接，仅中止当前操作）
     *
     * <p>会话身份由 {@link #init(Long, Long)} 时写入的实例字段提供，不再传参。</p>
     */
    void interrupt();

    /**
     * 获取该节点中的 Socket 路径和对应的说明信息
     *
     * <p>客户端根据返回的端点列表，连接对应的 WebSocket 路径收发数据。
     * 例如 ModelNode 返回文本流端点，TtsNode 返回音频流端点。</p>
     *
     * @return 该节点的端点信息列表，一定不为null，至少是一个空集合
     */
    List<NodeEndpoint> getSocketInfo();
}