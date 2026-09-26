package org.lixiyun.common.agent.tts.api;

/**
 * 流式TTS服务Provider策略接口
 * <p>定义多厂商TTS语音合成引擎的统一行为规范，通过策略模式实现阿里云NLS、
 * 火山引擎等厂商的灵活对接与动态切换。</p>
 * <p><b>注意：</b>Provider 与用户概念解耦——接口只认抽象会话ID（sessionId），
 * 由防腐层（TtsConnectionManager）负责 userId ↔ sessionId 映射及业务规则。</p>
 *
 * <h3>生命周期时序</h3>
 * <pre>
 * Spring启动 → init()               ← 初始化SDK客户端、获取Token、建立连接
 *
 * 业务调用:
 *   createSession()                 ← 创建抽象TTS会话，返回sessionId
 *   sendTextSegment()               ← 反复调用，流式发送文本分片
 *   finishSynthesis()               ← 通知服务端文本已全部发送，等待收尾
 *   interrupt()                     ← 中断当前合成（会话保留，可再次调用sendTextSegment）
 *   cancel()                        ← 取消并关闭会话
 *
 * Spring销毁 → shutdown()           ← 关闭所有会话、清理调度器与连接池
 * </pre>
 *
 * <h3>会话管理原则</h3>
 * <ul>
 *   <li>全局会话数上限保护，超出上限的注册请求被拒绝</li>
 *   <li>同一会话同一时刻仅允许一路合成（CAS自旋争用）</li>
 *   <li>空闲会话超时自动回收</li>
 *   <li>synthesisId机制保证异步回调不跨合成污染状态</li>
 * </ul>
 *
 * <h3>配置切换方式</h3>
 * <pre>{@code
 * # application.yml
 * tts:
 *   provider: alibaba_nls    # 激活阿里云Provider
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-09-25
 */
public interface ITtsProvider {

    /**
     * 初始化Provider：建立SDK客户端连接、获取Token/认证
     */
    void init();

    /**
     * 销毁Provider：关闭所有活跃会话、释放连接资源
     */
    void shutdown();

    /**
     * 创建抽象TTS会话并绑定合成结果回调，返回唯一的会话标识
     *
     * @param callback 合成结果回调
     * @return 会话唯一标识（Provider内部生成）
     */
    String createSession(TtsResultCallback callback);

    /**
     * 发送文本分片，首次调用时自动建立合成器连接；
     * 同一会话可反复调用此方法进行流式合成
     *
     * @param sessionId 会话唯一标识
     * @param text      待合成的文本分片
     */
    void sendTextSegment(String sessionId, String text);

    /**
     * 完成合成：通知服务端文本流已全部发送，等待收尾音频数据
     *
     * @param sessionId 会话唯一标识
     */
    void finishSynthesis(String sessionId);

    /**
     * 中断当前合成：关闭合成器，保留会话上下文，允许再次合成
     *
     * @param sessionId 会话唯一标识
     */
    void interrupt(String sessionId);

    /**
     * 取消TTS会话：关闭会话并清理所有资源，释放会话槽位
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
     * 获取当前活跃的TTS会话总数
     *
     * @return 活跃会话数
     */
    int getActiveSessionCount();

    /**
     * 获取当前Provider对应的厂商类型
     *
     * @return 厂商类型枚举
     */
    TtsProviderType getProviderType();
}