package org.lixiyun.server.ai.model;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

/**
 * @author lixiyun
 * @since 2026-08-13 15:59
 */
public interface Model {

    /**
     * 调用模型
     * @param chatModel 模型
     * @param userPrompt 用户提示词
     * @param config 配置
     * @return 模型输出
     * @throws GraphRunnerException 异常
     */
    AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException;

    /**
     * 调用模型
     * @param chatModel 模型
     * @param userPrompt 用户提示词
     * @param config 配置
     * @param runnableConfig 运行时配置
     * @return 模型输出
     * @throws GraphRunnerException 异常
     */
    AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException;

    /**
     * 流式调用模型
     * @param chatModel 模型
     * @param userPrompt 用户提示词
     * @param config 配置
     * @return 模型输出流
     * @throws GraphRunnerException 异常
     */
    Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException;

    /**
     * 流式调用模型
     * @param chatModel 模型
     * @param userPrompt 用户提示词
     * @param config 配置
     * @param runnableConfig 运行时配置
     * @return 模型输出流
     * @throws GraphRunnerException 异常
     */
    Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException;
}