package org.lixiyun.server.service.user;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.lixiyun.pojo.dto.user.conversation.ScenarioChatDTO;
import reactor.core.publisher.Flux;

/**
 * 聊天服务接口
 * <p>
 * 提供智能对话功能，支持流式响应输出
 * </p>
 *
 * @author lixiyun
 * @since 2026-03-16 13:02
 */
public interface TextService {

    /**
     * 心理文本场景对话
     * <p>
     * 根据用户的输入，通过 AI 模型生成回复内容，并以流式方式返回结果
     * </p>
     *
     * @param scenarioChatDTO {@link ScenarioChatDTO} 对话请求参数，包含会话 ID、用户消息等信息
     * @return 流式响应，包含 AI 生成的回复内容片段
     * @throws GraphStateException 当图状态异常时抛出（如图构建失败、节点执行错误等）
     */
    Flux<String> scenarioChat(ScenarioChatDTO scenarioChatDTO) throws GraphStateException;
}
