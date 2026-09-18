package org.lixiyun.server.infrastructure.log;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.log.AiModelCall;
import org.lixiyun.server.ai.interceptor.ModelInterceptor.BaseModelInterceptor;
import org.lixiyun.server.ai.model.BaseModel;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.mapper.AiModelCallMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步模型调用记录拦截器
 *
 * <p>继承{@link BaseModelInterceptor}，在{@code interceptBaseModel}中完成同步调用的完整记录，
 * 将每次模型调用的输入、输出、耗时、token用量等信息同步写入{@code ai_model_call}表。</p>
 *
 * <h3>上下文传播机制</h3>
 * <p>业务上下文（conversationId、userId、currentRound）的传播链路：</p>
 * <pre>
 * Graph构建RunnableConfig（注入ConversationMetadata）
 *   → Node.apply(state, config)
 *     → Model.callForResult(chatModel, prompt, aiNodeConfig, config)
 *       → BaseModel.doCall → ReactAgent.call(prompt, runnableConfig)
 *         → 框架将config.metadata()注入ModelRequest.context
 *           → 本拦截器通过request.getContext()获取ConversationMetadata
 * </pre>
 *
 * <h3>记录字段来源</h3>
 * <table>
 *   <tr><th>字段</th><th>来源</th></tr>
 *   <tr><td>conversationId / userId / roundNum</td><td>{@code request.getContext().get(ConversationMetadata.NAME)}</td></tr>
 *   <tr><td>traceId</td><td>{@link FlowExecutionContextManager} 从Redis获取</td></tr>
 *   <tr><td>modelName</td><td>{@code request.getOptions().getModel()}</td></tr>
 *   <tr><td>systemPrompt</td><td>{@code request.getSystemMessage().getText()}</td></tr>
 *   <tr><td>userPrompt</td><td>从{@code request.getMessages()}中提取MessageType.USER的消息</td></tr>
 *   <tr><td>modelOutput</td><td>{@code response.getMessage()}转为{@link AssistantMessage}后getText()</td></tr>
 *   <tr><td>inputTokens / outputTokens / totalTokens</td><td>{@code response.getChatResponse().getMetadata().getUsage()}</td></tr>
 *   <tr><td>durationMs</td><td>System.currentTimeMillis()差值</td></tr>
 *   <tr><td>status</td><td>调用成功=1({@link AiModelCall#STATUS_COMPLETED})，异常=2({@link AiModelCall#STATUS_FAILED})</td></tr>
 *   <tr><td>errorCode / errorMessage</td><td>异常的类名和消息（截断至2000字符）</td></tr>
 * </table>
 *
 * <h3>开关控制</h3>
 * <p>通过{@code ai.evaluation.recording.enabled=true}配置开启，默认关闭。
 * 关闭时{@link BaseModel}中{@code @Autowired(required=false)}注入为null，不注册拦截器，原有逻辑不受影响。</p>
 *
 * <h3>异常安全</h3>
 * <p>持久化操作在finally块中执行，自身被try-catch保护，即使写入失败也不影响业务调用结果返回。</p>
 *
 * @author lixiyun
 * @since 2026-09-11
 * @see BaseModelInterceptor
 * @see ConversationMetadata
 * @see AiModelCall
 */
@Slf4j
@Component(RecordingModelInterceptor.NAME)
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class RecordingModelInterceptor extends BaseModelInterceptor {

    public static final String NAME = "RecordingModelInterceptor";

    private final AiModelCallMapper mapper;

    private final FlowExecutionContextManager flowExecutionContextManager;

    public RecordingModelInterceptor(AiModelCallMapper mapper,
                                     FlowExecutionContextManager flowExecutionContextManager) {
        this.mapper = mapper;
        this.flowExecutionContextManager = flowExecutionContextManager;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 拦截同步模型调用，记录完整的输入输出信息并持久化到数据库
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>构建{@link AiModelCall}记录，提取请求侧信息（提示词、模型名称、业务上下文、traceId）</li>
     *   <li>调用{@code handler.call(request)}执行实际模型调用</li>
     *   <li>在finally中提取响应侧信息（模型输出、token用量、耗时），同步写入数据库</li>
     * </ol>
     *
     * <p>异常安全：持久化失败仅记录日志，不影响业务调用结果返回。</p>
     *
     * @param request 模型调用请求，包含messages、context、options等
     * @param handler 调用链下游处理器
     * @return 模型调用响应
     */
    @Override
    public ModelResponse interceptBaseModel(ModelRequest request, ModelCallHandler handler) {
        AiModelCall record = new AiModelCall();
        record.setStartedAt(LocalDateTime.now());
        record.setCallType(AiModelCall.CALL_TYPE_SYNC);

        Boolean streamFlag = (Boolean) request.getContext().get("_stream_");
        if (Boolean.TRUE.equals(streamFlag)) {
            log.debug("[模型同步调用日志拦截器-流式调用]: {}", request);
        }


        extractPrompts(request, record);

        ConversationMetadata conversationMetadata = (ConversationMetadata) request.getContext().get(ConversationMetadata.NAME);
        log.debug("[模型同步调用日志拦截器-模型上下文]: {}", conversationMetadata);

        record.setModelName(
                request.getOptions() != null ? request.getOptions().getModel() : "unknown"
        );

        AiNodeConfig aiNodeConfig = (AiNodeConfig) request.getContext().get(AiNodeConfig.NAME);
        log.debug("[模型同步调用日志拦截器-节点配置]: {}", aiNodeConfig);

        if (aiNodeConfig != null) {
            record.setNodeKey(aiNodeConfig.getNodeKey());
            record.setNodeName(aiNodeConfig.getNodeName());
            record.setModelProvider(ChatModelType.fromType(aiNodeConfig.getModelType()).factoryKey);

            Map<String, Object> modelParams = new HashMap<>();
            if (aiNodeConfig.getTemperature() != null) {
                modelParams.put("temperature", aiNodeConfig.getTemperature());
            }
            if (aiNodeConfig.getTopP() != null) {
                modelParams.put("topP", aiNodeConfig.getTopP());
            }
            if (aiNodeConfig.getTopK() != null) {
                modelParams.put("topK", aiNodeConfig.getTopK());
            }
            if (aiNodeConfig.getMaxToken() != null) {
                modelParams.put("maxToken", aiNodeConfig.getMaxToken());
            }
            if (aiNodeConfig.getStopSequences() != null) {
                modelParams.put("stopSequences", aiNodeConfig.getStopSequences());
            }
            if (aiNodeConfig.getFrequencyPenalty() != null) {
                modelParams.put("frequencyPenalty", aiNodeConfig.getFrequencyPenalty());
            }
            if (aiNodeConfig.getPresencePenalty() != null) {
                modelParams.put("presencePenalty", aiNodeConfig.getPresencePenalty());
            }
            if (!modelParams.isEmpty()) {
                record.setModelParams(modelParams);
            }
        }

        Long traceId = null;
        if (conversationMetadata != null) {
            traceId = flowExecutionContextManager.getTraceId(conversationMetadata.getConversationId());
            record.setTraceId(traceId);
            log.debug("[模型同步调用日志拦截器-traceId]: {}", traceId);
        }

        long start = System.currentTimeMillis();
        ModelResponse response = null;
        Throwable caughtException = null;
        try {
            response = handler.call(request);
            log.debug("[模型同步调用日志拦截器] 节点名称：{}, 轮次：{}, 模型调用响应: {}", aiNodeConfig.getNodeName(), conversationMetadata.getCurrentRound(), JsonUtils.toJsonString(response));
        } catch (Throwable t) {
            caughtException = t;
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            record.setDurationMs(durationMs);
            record.setFinishedAt(LocalDateTime.now());

            if (caughtException == null) {
                extractOutput(response, record);
                record.setStatus(AiModelCall.STATUS_COMPLETED);
                extractUsage(response, record);
            } else {
                record.setStatus(AiModelCall.STATUS_FAILED);
                record.setErrorCode(caughtException.getClass().getSimpleName());
                String msg = caughtException.getMessage();
                record.setErrorMessage(msg != null && msg.length() > 2000 ? msg.substring(0, 2000) : msg);
            }

            try {
                mapper.insert(record);
            } catch (Exception e) {
                log.error("[模型同步调用日志拦截器] 模型调用记录持久化失败, 会话ID: {}, traceId:{}, 节点名称: {}, 节点key: {}", conversationMetadata.getConversationId(), traceId, aiNodeConfig.getNodeName(), aiNodeConfig.getNodeKey(), e);
            }
        }

        if (!(response.getMessage() instanceof AssistantMessage)) {
            log.debug("[模型同步调用日志拦截器] 模型流式返回，结束同步日志拦截");
            return response;
        }

        if (response.getChatResponse().hasToolCalls()) {
            log.debug("[模型同步调用日志拦截器] 模型调用响应工具调用: {}", response.getChatResponse().getResult().getOutput().getToolCalls());
        }

        return response;
    }

    /**
     * 从请求中提取系统提示词和用户提示词
     * <p>系统提示词优先从{@code request.getSystemMessage()}获取，
     * 用户提示词从{@code request.getMessages()}中查找第一条{@link MessageType#USER}类型的消息。</p>
     *
     * @param request 模型调用请求
     * @param record  待填充的记录对象
     */
    private void extractPrompts(ModelRequest request, AiModelCall record) {
        if (request.getSystemMessage() != null) {
            record.setSystemPrompt(request.getSystemMessage().getText());
        }

        List<Message> messages = request.getMessages();
        String userPrompt = null;
        if (messages != null) {
            for (Message msg : messages) {
                if (msg.getMessageType() == MessageType.USER) {
                    userPrompt = msg.getText();
                }
            }
        }
        record.setUserPrompt(userPrompt);
    }

    /**
     * 从响应中提取模型输出文本
     * <p>仅当{@code response.getMessage()}为{@link AssistantMessage}实例时提取文本内容。</p>
     *
     * @param response 模型调用响应
     * @param record   待填充的记录对象
     */
    private void extractOutput(ModelResponse response, AiModelCall record) {
        Object message = response.getMessage();
        if (message instanceof AssistantMessage assistantMessage) {
            record.setModelOutput(assistantMessage.getText());
        }
    }

    /**
     * 从响应中提取token使用量
     * <p>从{@code response.getChatResponse().getMetadata().getUsage()}中提取
     * promptTokens、completionTokens、totalTokens，null值保持原样不转换。</p>
     *
     * @param response 模型调用响应
     * @param record   待填充的记录对象
     */
    private void extractUsage(ModelResponse response, AiModelCall record) {
        ChatResponse chatResponse = response.getChatResponse();
        if (chatResponse != null && chatResponse.getMetadata().getUsage() != null) {
            var usage = chatResponse.getMetadata().getUsage();
            record.setInputTokens(usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null);
            record.setOutputTokens(usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null);
            record.setTotalTokens(usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null);
        }
    }
}