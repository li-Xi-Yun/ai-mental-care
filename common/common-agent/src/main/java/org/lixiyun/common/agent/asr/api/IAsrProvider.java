package org.lixiyun.common.agent.asr.api;

/**
 * 实时语音识别Provider策略接口
 * <p>定义多厂商ASR语音识别引擎的统一行为规范，通过策略模式实现阿里云NLS、
 * 火山引擎等厂商的灵活对接与动态切换。</p>
 * <p><b>注意：</b>Provider 与用户概念解耦——接口只认抽象会话ID（sessionId），
 * 由防腐层（AsrConnectionManager）负责 userId ↔ sessionId 映射及业务规则。</p>
 *
 * <h3>生命周期时序</h3>
 * <pre>
 * Spring启动 → init()               ← 初始化SDK客户端、获取Token、建立连接
 *
 * 业务调用:
 *   createSession()                 ← 创建抽象ASR会话，返回sessionId
 *   sendAudio()                     ← 反复调用，持续发送音频帧
 *   cancel()                        ← 取消并关闭会话
 *
 * Spring销毁 → shutdown()           ← 关闭所有会话、清理调度器与连接池
 * </pre>
 *
 * <h3>会话管理原则</h3>
 * <ul>
 *   <li>全局会话数上限保护，超出上限的注册请求被拒绝</li>
 *   <li>空闲会话超时自动回收</li>
 *   <li>会话关闭后丢弃所有回调事件</li>
 *   <li>原子会话生命周期管理，消除竞态</li>
 * </ul>
 *
 * <h3>配置切换方式</h3>
 * <pre>{@code
 * # application.yml
 * asr:
 *   provider: alibaba_nls    # 激活阿里云Provider
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-09-26
 */
public interface IAsrProvider {

    /**
     * 初始化Provider：建立SDK客户端连接、获取Token/认证
     */
    void init();

    /**
     * 销毁Provider：关闭所有活跃会话、释放连接资源
     */
    void shutdown();

    /**
     * 创建抽象ASR会话并绑定识别结果回调，返回唯一的会话标识
     *
     * @param callback 识别结果回调
     * @return 会话唯一标识（Provider内部生成）
     */
    String createSession(AsrResultCallback callback);

    /**
     * 发送音频帧（全量发送）
     *
     * @param sessionId 会话唯一标识
     * @param data      音频数据字节数组
     */
    void sendAudio(String sessionId, byte[] data);

    /**
     * 发送音频帧（部分发送）
     *
     * @param sessionId 会话唯一标识
     * @param data      音频数据字节数组
     * @param length    实际发送长度
     */
    void sendAudio(String sessionId, byte[] data, int length);

    /**
     * 取消ASR会话：关闭连接并清理所有资源，释放会话槽位
     *
     * @param sessionId 会话唯一标识
     */
    void cancel(String sessionId);

    /**
     * 查询会话是否处于活跃状态
     *
     * @param sessionId 会话唯一标识
     * @return {@code true}表示会话活跃
     */
    boolean isSessionActive(String sessionId);

    /**
     * 获取当前活跃的ASR会话总数
     *
     * @return 活跃会话数
     */
    int getActiveSessionCount();

    /**
     * 获取当前Provider对应的厂商类型
     *
     * @return 厂商类型枚举
     */
    AsrProviderType getProviderType();
}