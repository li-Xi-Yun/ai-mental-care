package org.lixiyun.server.ai.model;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI模型基类 - 模板方法模式
 * <p>
 * 定义AI模型调用的算法骨架，子类通过实现抽象钩子方法来定制具体行为。
 * </p>
 *
 * <h3>设计要点 - 重试机制接入：</h3>
 * <p>
 * {@code call()} 和 {@code stream()} 保持抽象，由子类标注 {@code @Retryable} 后委托调用
 * {@code doCall()} / {@code doStream()}，确保 Spring AOP 代理能正确拦截重试。
 * </p>
 *
 * <h3>固定JSON体结果（结构化输出）：</h3>
 * <ul>
 *     <li>子类实现 {@code call()} + {@code @Retryable} → 委托 {@code doCall()} 返回 AssistantMessage</li>
 *     <li>子类实现 {@code callForResult()} + {@code @Retryable} → 委托 {@code doCallForResult()} 返回反序列化结果类</li>
 * </ul>
 *
 * <h3>非固定JSON体结果（自由文本输出）：</h3>
 * <ul>
 *     <li>子类实现 {@code call()} + {@code @Retryable} → 委托 {@code doCall()} 同步返回</li>
 *     <li>子类实现 {@code stream()} → 委托 {@code doStream()} 流式返回</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-13 16:01
 */
@Slf4j
public abstract class BaseModel implements Model {

    // ==================== 抽象钩子方法 ====================

    /**
     * 获取系统提示词
     * <p>定义模型的角色、职责、输出格式等约束信息，作为System Message注入对话上下文</p>
     *
     * @param config 节点配置，可为null（使用默认值）
     * @return 系统提示词文本
     */
    protected abstract String getSystemPrompt(AiNodeConfig config);

    /**
     * 获取Agent名称
     * <p>用于标识当前模型Agent，在日志追踪与运行时监控中作为唯一标识符</p>
     *
     * @return Agent名称
     */
    protected abstract String getAgentName();

    /**
     * 获取Agent描述
     * <p>对当前模型Agent的功能概述，用于Agent注册与能力描述</p>
     *
     * @return Agent描述
     */
    protected abstract String getAgentDescription();

    /**
     * 构建Ollama模型的ChatOptions配置
     * <p>针对Ollama本地部署模型的推理参数配置，包括温度、采样策略、上下文长度等</p>
     *
     * @param config 节点配置，可为null（使用默认值）
     * @return Ollama模型的ChatOptions实例
     */
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config){
        return null;
    }

    /**
     * 构建DashScope模型的ChatOptions配置
     * <p>针对阿里云DashScope云端模型的推理参数配置，包括温度、采样策略、思维链等</p>
     *
     * @param config 节点配置，可为null（使用默认值）
     * @return DashScope模型的ChatOptions实例
     */
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config){
        return null;
    }

    /**
     * 构建DeepSeek模型的ChatOptions配置
     * <p>针对DeepSeek云端模型的推理参数配置，包括温度、采样策略、JSON输出格式等</p>
     *
     * @param config 节点配置，可为null（使用默认值）
     * @return DeepSeek模型的ChatOptions实例
     */
    protected ChatOptions buildDeepSeekCompanionOptions(AiNodeConfig config){
        return null;
    }

    /**
     * 构建默认ChatOptions配置
     * <p>当ChatModel实例不属于Ollama/DashScope/DeepSeek时的兜底配置</p>
     *
     * @param config 节点配置，可为null（使用默认值）
     * @return 默认ChatOptions实例
     */
    protected abstract ChatOptions buildDefaultCompanionOptions(AiNodeConfig config);

    // ==================== 输出类型（默认null表示非固定JSON体） ====================

    /**
     * 获取固定JSON体输出的目标反序列化类型
     * <p>
     * 默认返回{@code null}，表示当前模型为非固定JSON体输出（自由文本）。
     * 子类若需要结构化输出，应重写此方法返回对应的结果类{@code Class}对象，
     * 以支持{@link #doCallForResult}自动反序列化。
     * </p>
     *
     * @return 结果类的{@code Class}对象，返回{@code null}表示非固定JSON体输出
     */
    protected Class<?> getOutputType() {
        return null;
    }

    // ==================== protected 模板方法：实际执行逻辑 ====================

    /**
     * 同步调用模型并返回AssistantMessage
     * <p>
     * 构建Agent后执行同步调用，返回模型原始的{@link AssistantMessage}响应。
     * 子类的{@code call()}方法应标注{@code @Retryable}后委托调用此方法。
     * </p>
     *
     * @param chatModel  具体的ChatModel实例（Ollama/DashScope/DeepSeek等）
     * @param userPrompt 用户提示词
     * @param config     节点配置，可为null（使用默认值）
     * @return 模型响应的AssistantMessage
     * @throws GraphRunnerException Agent执行异常
     */
    protected AssistantMessage doCall(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return buildAgent(chatModel, config).call(userPrompt);
    }

    /**
     * 流式调用模型并返回Flux流
     * <p>
     * 构建Agent后执行流式调用，返回{@link Flux}流供调用方逐帧消费。
     * 子类的{@code stream()}方法应委托调用此方法。
     * </p>
     *
     * @param chatModel  具体的ChatModel实例
     * @param userPrompt 用户提示词
     * @param config     节点配置，可为null（使用默认值）
     * @return 模型响应的NodeOutput流
     * @throws GraphRunnerException Agent执行异常
     */
    protected Flux<NodeOutput> doStream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return buildAgent(chatModel, config).stream(userPrompt);
    }

    /**
     * 同步调用模型并返回反序列化后的结果对象
     * <p>
     * 先通过{@link #doCall}获取{@link AssistantMessage}，
     * 再通过{@link #deserializeResult}将响应文本反序列化为{@link #getOutputType}指定的结果类型。
     * 子类的{@code callForResult()}方法应标注{@code @Retryable}后委托调用此方法。
     * </p>
     *
     * @param chatModel  具体的ChatModel实例
     * @param userPrompt 用户提示词
     * @param config     节点配置，可为null（使用默认值）
     * @param <T>        结果类型，由子类的{@link #getOutputType}决定
     * @return 反序列化后的结果对象
     * @throws GraphRunnerException           Agent执行异常
     * @throws UnsupportedOperationException 如果{@link #getOutputType}返回null（非固定JSON体输出）
     */
    @SuppressWarnings("unchecked")
    protected <T> T doCallForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        AssistantMessage message = doCall(chatModel, userPrompt, config);
        return (T) deserializeResult(message);
    }


    // ==================== 模板方法：构建Agent ====================

    /**
     * 构建ReactAgent实例
     * <p>
     * 基于{@link #reactAgentBuilder}创建Agent Builder，注入系统提示词，
     * 若{@link #getOutputType}返回非null则同时设置结构化输出类型约束。
     * </p>
     *
     * @param chatModel 具体的ChatModel实例
     * @param config    节点配置，可为null（使用默认值）
     * @return 构建完成的ReactAgent实例
     */
    protected ReactAgent buildAgent(ChatModel chatModel, AiNodeConfig config) {
        com.alibaba.cloud.ai.graph.agent.Builder builder = reactAgentBuilder(chatModel, config)
                .systemPrompt(getSystemPrompt(config));
        Class<?> outputType = getOutputType();
        if (outputType != null) {
            builder.outputType(outputType);
        }
        return builder.build();
    }

    // ==================== 反序列化结果 ====================

    /**
     * 将AssistantMessage的响应文本反序列化为结果对象
     * <p>
     * 依赖{@link #getOutputType}返回的Class对象进行JSON反序列化。
     * 若{@link #getOutputType}返回null，则抛出{@link UnsupportedOperationException}，
     * 表示当前模型不支持固定JSON体输出。
     * </p>
     *
     * @param message 模型响应的AssistantMessage
     * @return 反序列化后的结果对象
     * @throws UnsupportedOperationException 如果{@link #getOutputType}返回null
     */
    protected Object deserializeResult(AssistantMessage message) {
        Class<?> outputType = getOutputType();
        if (outputType == null) {
            throw new UnsupportedOperationException("当前模型不支持固定JSON体输出，请重写getOutputType()返回非null的结果类型");
        }
        return JsonUtils.parseObject(message.getText(), outputType);
    }

    // ==================== 公共基础设施 ====================

    /**
     * 创建ReactAgent的Builder实例，注入模型、名称、描述与ChatOptions
     * <p>
     * 作为Agent构建的基础设施方法，完成模型实例校验、Agent元信息设置与推理参数注入。
     * 子类或模板方法可在此基础上追加系统提示词、输出类型等配置后调用{@code build()}。
     * </p>
     *
     * @param chatModel 具体的ChatModel实例，不可为null
     * @param config    节点配置，可为null（使用默认值）
     * @return 已配置模型/名称/描述/ChatOptions的ReactAgent Builder
     * @throws BusinessException 如果chatModel为null，抛出{@link ConversationExceptionEnum#MODEL_NOT_EXIST}
     */
    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder(ChatModel chatModel, AiNodeConfig config) {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        return ReactAgent.builder()
                .model(chatModel)
                .name(getAgentName())
                .description(getAgentDescription())
                .chatOptions(chatOptions(chatModel, config))
                .enableLogging(false);
    }

    /**
     * 根据ChatModel实例类型路由到对应的ChatOptions构建方法
     * <p>
     * 支持的模型类型路由：
     * <ul>
     *     <li>{@link OllamaChatModel} → {@link #buildOllamaCompanionOptions(AiNodeConfig)}</li>
     *     <li>{@link DashScopeChatModel} → {@link #buildDashScopeCompanionOptions(AiNodeConfig)}</li>
     *     <li>{@link DeepSeekChatModel} → {@link #buildDeepSeekCompanionOptions(AiNodeConfig)}</li>
     *     <li>其他 → {@link #buildDefaultCompanionOptions(AiNodeConfig)}</li>
     * </ul>
     * </p>
     *
     * @param chatModel 具体的ChatModel实例
     * @param config    节点配置，可为null（使用默认值）
     * @return 与模型类型匹配的ChatOptions实例
     */
    protected ChatOptions chatOptions(ChatModel chatModel, AiNodeConfig config) {
        if (chatModel instanceof OllamaChatModel) {
            return buildOllamaCompanionOptions(config);
        } else if (chatModel instanceof DashScopeChatModel) {
            return buildDashScopeCompanionOptions(config);
        } else if (chatModel instanceof DeepSeekChatModel) {
            return buildDeepSeekCompanionOptions(config);
        } else {
            return buildDefaultCompanionOptions(config);
        }
    }

    // ==================== 配置读取辅助方法 ====================

    /**
     * 判断是否为结构化JSON输出格式
     *
     * @param config 节点配置，可为null
     * @return true表示JSON输出，false表示自由文本
     */
    protected boolean isJsonResponseFormat(AiNodeConfig config) {
        return config != null && config.getResponseFormat() != null && config.getResponseFormat() == 1;
    }

    /**
     * 从配置中解析停止序列（字符串类型）
     * <p>适用于Ollama、DeepSeek等{@code stop(List<String>)}签名的模型</p>
     *
     * @param config       节点配置，可为null
     * @param defaultStops 默认停止序列
     * @return 停止序列列表
     */
    protected List<String> resolveStopSequences(AiNodeConfig config, List<String> defaultStops) {
        if (config != null && StrUtil.isNotBlank(config.getStopSequences())) {
            try {
                List<String> parsed = JsonUtils.parseArray(config.getStopSequences(), String.class);
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("停止序列解析失败，使用默认值: {}", config.getStopSequences(), e);
            }
        }
        return defaultStops;
    }

    /**
     * 从配置中解析停止序列（DashScope适配类型）
     * <p>
     * DashScope的{@code stop}字段类型为{@code List<Object>}（支持字符串和整数token ID），
     * 此方法在{@link #resolveStopSequences}基础上进行泛型适配，供DashScope模型直接使用。
     * </p>
     *
     * @param config       节点配置，可为null
     * @param defaultStops 默认停止序列
     * @return 适配DashScope的Object类型停止序列
     */
    protected List<Object> resolveDashScopeStopSequences(AiNodeConfig config, List<String> defaultStops) {
        return resolveStopSequences(config, defaultStops).stream().map(s -> (Object) s).toList();
    }
}