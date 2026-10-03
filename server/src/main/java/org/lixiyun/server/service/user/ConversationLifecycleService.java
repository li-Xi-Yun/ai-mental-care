package org.lixiyun.server.service.user;

import org.lixiyun.pojo.dto.user.conversation.ConversationLifecycleInitDTO;
import org.lixiyun.pojo.vo.user.conversation.ConversationLifecycleInitVO;

/**
 * 对话生命周期服务接口
 *
 * <p>管理输入层适配器和输出层管道的完整生命周期，
 * 包括会话初始化（注册适配器 + 组装管道）和会话销毁（释放所有资源）。</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
public interface ConversationLifecycleService {

    /**
     * 初始化对话生命周期
     *
     * <p>根据输入/输出类型：
     * <ol>
     *   <li>调用 {@code InputAdapterSessionManager.register()} 注册并初始化输入适配器</li>
     *   <li>调用 {@code OutputPipelineSessionManager.initSession()} 组装并初始化输出管道</li>
     *   <li>汇总两端 Socket 端点路径返回给客户端</li>
     * </ol>
     *
     * @param request 包含 conversationId、inputTypes、outputTypes 的请求
     * @return 会话ID + 输入层/输出层 Socket 端点信息
     */
    ConversationLifecycleInitVO initSession(ConversationLifecycleInitDTO request);

    /**
     * 销毁对话生命周期
     *
     * <p>依次销毁输入层适配器和输出层管道资源，
     * 从各自的会话 Map 中移除，释放所有连接和缓冲区。</p>
     *
     * @param conversationId 会话ID
     */
    void endSession(Long conversationId);
}