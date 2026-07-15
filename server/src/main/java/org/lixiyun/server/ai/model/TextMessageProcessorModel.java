package org.lixiyun.server.ai.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.ai.message.ThinkMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 文本消息处理器模型配置
 * <p>
 * 专门为心理健康陪伴场景优化的AI模型参数配置中心。
 * 核心职责：
 * <ul>
 *     <li>根据不同模型类型（Ollama/DashScope/DeepSeek）提供最优化的心理陪伴参数</li>
 *     <li>封装流式响应处理逻辑，支持函数式回调</li>
 *     <li>自动持久化关键对话数据（思考过程、回复内容、工具调用）</li>
 * </ul>
 * </p>
 *
 * <h3>业务场景特征：</h3>
 * <ul>
 *     <li>需要温暖、共情的情感支持回复</li>
 *     <li>理解并回应用户的情绪状态（焦虑、抑郁、压力等）</li>
 *     <li>使用通俗易懂的语言，避免生硬的专业术语</li>
 *     <li>保持回复的一致性和稳定性（80-150字）</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-07-14 23:38
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextMessageProcessorModel {

    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    /**
     * 系统提示词：定义AI的角色和行为准则
     */
    // todo 之后要提取这个系统提示词到配置文件中
    private final String systemPrompt = "你是一位温暖专业的心理陪伴师。你的任务是：\n" +
            "1. 用共情的方式理解用户的情绪状态\n" +
            "2. 提供温暖、专业的情感支持建议\n" +
            "3. 使用简洁通俗的语言（避免专业术语）\n" +
            "4. 回复控制在80-150字，保持亲切自然\n" +
            "5. 如遇严重心理问题，建议寻求专业心理咨询师帮助";

    /**
     * 最大生成token数（约150汉字）
     */
    private static final int maxToken = 200;

    /**
     * 构建ReactAgent构建器
     *
     * @param chatModel AI聊天模型实例
     * @return 配置完成的Agent构建器
     * @throws BusinessException 当模型为null时抛出
     */
    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuild(ChatModel chatModel) {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        return ReactAgent.builder()
                .model(chatModel)
                .name("emotionalCompanion")
                .description("专业心理健康陪伴助手")
                .chatOptions(chatOptions(chatModel))
                .enableLogging(false);
    }

    /**
     * 根据不同的大模型类型构建适合心理陪伴场景的聊天选项配置
     * <p>
     * 业务场景：心理健康陪伴助手，需要温暖、专业、具有共情能力的回复。
     * 核心目标：
     * <ul>
     *     <li>生成温暖自然的情感支持回复（80-150字）</li>
     *     <li>理解并回应用户的情绪状态（焦虑、抑郁、压力等）</li>
     *     <li>使用通俗易懂的语言，避免生硬的专业术语</li>
     *     <li>保持回复的一致性和稳定性，避免风格突变</li>
     * </ul>
     * </p>
     *
     * @param chatModel AI聊天模型实例
     * @return 针对心理陪伴场景优化的聊天选项配置
     */
    private ChatOptions chatOptions(ChatModel chatModel) {
        if (chatModel instanceof OllamaChatModel) {
            return buildOllamaCompanionOptions();
        } else if (chatModel instanceof DashScopeChatModel) {
            return buildDashScopeCompanionOptions();
        } else if (chatModel instanceof DeepSeekChatModel) {
            return buildDeepSeekCompanionOptions();
        } else {
            return buildDefaultCompanionOptions();
        }
    }

    /**
     * 构建Ollama本地模型的心理陪伴配置
     * <p>
     * 适用场景：私有化部署、数据敏感、低延迟要求的场景
     * 推荐模型：qwen3:7b-chat-thinking（思考版，共情能力强）
     * </p>
     *
     * <h3>核心优化点：</h3>
     * <ul>
     *     <li><b>temperature=0.65</b> - 略高于默认值，增加回复的温暖感和自然度</li>
     *     <li><b>topK=25</b> - 缩小候选词范围，减少生僻词和机械表达</li>
     *     <li><b>repeatPenalty=1.18</b> - 强化去重，避免"我理解、我理解"类重复</li>
     *     <li><b>enableThinking()</b> - 启用思考模式，理解"emo/内耗/摆烂"等青年表达</li>
     *     <li><b>numCtx=8192</b> - 扩大上下文窗口，容纳更多历史对话</li>
     * </ul>
     *
     * @return Ollama模型的优化配置
     */
    private ChatOptions buildOllamaCompanionOptions() {
        return OllamaChatOptions.builder()

                .model(ollamaModelName)
                .keepAlive("45m")

                .temperature(0.65)
                .topK(25)
                .topP(0.88)
                .numPredict(maxToken)
                .seed(42)

                .repeatPenalty(1.18)
                .frequencyPenalty(0.45)
                .presencePenalty(0.15)
                .repeatLastN(25)
                .penalizeNewline(true)

                .stop(List.of("\n\n\n", "```", "## ", "### "))
                .truncate(true)
                .format(null)

                .numCtx(8192)
                .numThread(Math.max(2, Runtime.getRuntime().availableProcessors() / 2))
                .numBatch(1024)

                .enableThinking()
                .mirostat(2)
                .mirostatTau(3.0f)
                .mirostatEta(0.08f)

                .useMMap(true)
                .useMLock(false)
                .numGPU(-1)
                .lowVRAM(false)

                .build();
    }

    /**
     * 构建通义千问（DashScope）的心理陪伴配置
     * <p>
     * 适用场景：云端部署、需要强大中文理解能力、多轮对话场景
     * 优势：优秀的中文语义理解、情绪识别准确、回复自然流畅
     * </p>
     *
     * <h3>核心优化点：</h3>
     * <ul>
     *     <li><b>model="qwen-max"</b> - 使用最强版本，提升情绪理解的准确性</li>
     *     <li><b>thinkingBudget=8</b> - 增加思考预算，深度分析用户情绪</li>
     *     <li><b>enableSearch=false</b> - 禁用联网搜索，专注基于上下文的情感支持</li>
     *     <li><b>toolChoice="none"</b> - 强制禁用工具调用，确保纯文本陪伴输出</li>
     * </ul>
     *
     * @return DashScope模型的优化配置
     */
    private ChatOptions buildDashScopeCompanionOptions() {
        return DashScopeChatOptions.builder()

                .model(dashscopeModelName)
                .temperature(0.65)
                .topP(0.88)
                .topK(25)
                .seed(42)
                .maxToken(maxToken)

                .repetitionPenalty(1.18)

                .stop(List.of("\n\n\n", "```", "【", "】"))
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(DashScopeResponseFormat.Type.TEXT)
                        .build())

                .enableThinking(true)
                .thinkingBudget(8)

                .enableSearch(false)
                .stream(true)
                .incrementalOutput(true)
                .multiModel(false)
                .vlHighResolutionImages(false)

                .tools(null)
                .toolChoice("none")
                .internalToolExecutionEnabled(false)
                .toolContext(java.util.Map.of())
//                .toolNames(java.util.List.of())

                .build();
    }

    /**
     * 构建DeepSeek模型的心理陪伴配置
     * <p>
     * 适用场景：需要强逻辑推理、复杂情绪分析的深度咨询场景
     * 优势：推理能力强、善于分析因果关系、回复有深度
     * 注意：成本相对较高，适合重要对话节点
     * </p>
     *
     * <h3>核心优化点：</h3>
     * <ul>
     *     <li><b>temperature=0.65</b> - 平衡创造性与一致性</li>
     *     <li><b>frequencyPenalty=0.45</b> - 有效抑制高频重复词汇</li>
     *     <li><b>stop列表</b> - 拦截编号列表格式，强制自然语言表达</li>
     *     <li><b>toolChoice="none"</b> - 完全禁用工具，专注于纯文本情感交流</li>
     * </ul>
     *
     * @return DeepSeek模型的优化配置
     */
    private ChatOptions buildDeepSeekCompanionOptions() {
        return DeepSeekChatOptions.builder()

                .model(deepseekModelName)
                .temperature(0.65)
                .topP(0.88)
                .maxTokens(maxToken)

                .frequencyPenalty(0.45)
                .presencePenalty(0.15)

                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.TEXT)
                        .build())

                .stop(List.of("\n\n\n", "```", "1.", "2.", "3."))

                .logprobs(false)
                .topLogprobs(null)

                .tools(null)
                .toolChoice("none")
                .internalToolExecutionEnabled(false)
                .toolContext(java.util.Map.of())
                .toolCallbacks(java.util.List.of())

                .build();
    }

    /**
     * 构建默认通用的心理陪伴配置
     * <p>
     * 作为兜底方案，适用于其他未知类型的模型
     * 采用保守但合理的参数设置
     * </p>
     *
     * @return 默认通用配置
     */
    private ChatOptions buildDefaultCompanionOptions() {
        return ChatOptions.builder()
                .topK(30)
                .topP(0.88)
                .frequencyPenalty(0.5)
                .presencePenalty(0.15)
                .temperature(0.65)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "text-message-stream",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return reactAgentBuild(chatModel)
                .systemPrompt(systemPrompt).build()
                .stream(userPrompt);
    }

    /**
     * 处理文本消息的流式响应（带函数式回调）
     * <p>
     * 封装LLM流式调用的完整处理逻辑，通过函数式接口提供灵活的事件回调机制。
     * 支持流式思考、流式结果、工具调用、工具结果等多种事件类型的处理，
     * 并自动将关键数据持久化到数据库。
     * </p>
     *
     * <h3>函数式参数说明：</h3>
     * <ul>
     *     <li><b>thinkingConsumer</b> - 流式思考内容回调，接收模型推理过程中的思考文本</li>
     *     <li><b>streamingContentConsumer</b> - 流式输出内容回调，接收模型的实时输出片段</li>
     *     <li><b>toolCallConsumer</b> - 工具调用请求回调，接收工具名称和参数信息</li>
     *     <li><b>toolResponseConsumer</b> - 工具执行结果回调，接收工具返回的数据</li>
     *     <li><b>completeConsumer</b> - 最终完成回调（包含完整回复内容），用于最终处理和存储</li>
     *     <li><b>errorConsumer</b> - 异常错误回调，用于错误处理和日志记录</li>
     *     <li><b>finishRunnable</b> - 整个流程结束回调，用于清理资源和状态更新</li>
     * </ul>
     *
     * <h3>数据持久化：</h3>
     * <p>以下4种数据会自动保存到数据库（通过 {@code conversationHistoryMessagesStorage}）：</p>
     * <ol>
     *     <li>思考过程（THINKING类型）</li>
     *     <li>流式输出结果（ASSISTANT类型）</li>
     *     <li>工具调用请求（ASSISTANT_TOOL类型）</li>
     *     <li>工具执行结果（TOOL类型）</li>
     * </ol>
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * textMessageProcessorModel.processStreamWithCallbacks(
     *     chatModel,
     *     systemPrompt,
     *     userPrompt,
     *     userId,
     *     conversationId,
     *     currentRound,
     *     storage,
     *     thinking -> log.info("思考: {}", thinking),
     *     content -> webSocketService.send(content),
     *     toolCall -> log.info("工具调用: {}", toolCall),
     *     toolResponse -> log.info("工具结果: {}", toolResponse),
     *     completeMessage -> saveToDatabase(completeMessage),
     *     error -> log.error("错误", error),
     *     () -> log.info("完成")
     * );
     * }</pre>
     *
     * @param nodeOutputFlux                    LLM流式输出的Flux，包含所有节点输出（思考、流式结果、工具调用、工具结果）
     * @param userId                            用户ID，用于数据库记录归属
     * @param conversationId                    会话ID，用于关联会话历史
     * @param currentRound                      当前对话轮次，用于消息排序
     * @param thinkingConsumer                  流式思考内容消费者，参数为思考文本字符串
     * @param streamingContentConsumer          流式输出内容消费者，参数为实时输出的文本片段
     * @param toolCallConsumer                  工具调用请求消费者，参数格式："工具名 : 参数"
     * @param toolResponseConsumer              工具执行结果消费者，参数格式："工具名 : 返回结果"
     * @param completeConsumer                  完成回调消费者，参数为完整的AssistantMessage对象
     * @param errorConsumer                     错误回调消费者，参数为异常对象
     * @param finishRunnable                    结束回调 Runnable，无参数
     * @see org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage
     */
    public void processStreamWithCallbacks(
            Flux<NodeOutput> nodeOutputFlux,
            Long userId,
            Long conversationId,
            int currentRound,

            Consumer<String> thinkingConsumer,
            Consumer<String> streamingContentConsumer,
            Consumer<String> toolCallConsumer,
            Consumer<String> toolResponseConsumer,
            Consumer<AssistantMessage> completeConsumer,
            Consumer<Throwable> errorConsumer,
            Runnable finishRunnable

    ) {
        log.info("开始处理文本消息流式响应，用户ID：{}，会话ID：{}，轮次：{}", userId, conversationId, currentRound);

        StringBuilder thinkingBuilder = new StringBuilder();

        AtomicReference<LocalDateTime> localDateTime = new AtomicReference<>(LocalDateTime.now());
        nodeOutputFlux.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        output -> {
                            if (output instanceof StreamingOutput streamingOutput) {
                                OutputType type = streamingOutput.getOutputType();
                                Message message = streamingOutput.message();

                                if (type == OutputType.AGENT_MODEL_STREAMING) {
                                    if (message instanceof AssistantMessage assistantMessage) {
                                        Object reasoningContent = assistantMessage.getMetadata().get("reasoningContent");
                                        if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                            thinkingBuilder.append(reasoningContent);
                                            thinkingConsumer.accept(reasoningContent.toString());
//                                    log.debug("文本对话-[模型思考输出] {}", reasoningContent);
                                        } else {
                                            localDateTime.set(LocalDateTime.now());
                                            String content = assistantMessage.getText();
                                            streamingContentConsumer.accept(content);
//                                    log.debug("文本对话-[模型流式输出结果] ============>>> {}", content);
                                        }
                                    }
                                } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                    if (message instanceof AssistantMessage assistantMessage) {
                                        if(assistantMessage.hasToolCalls()){
                                            // 工具调用请求
                                            conversationHistoryMessagesStorage.save(userId, conversationId, currentRound, message);
                                            assistantMessage.getToolCalls().forEach(toolCall -> {
                                                log.debug("文本对话-[Tool Call] {} : {}", toolCall.name(), toolCall.arguments());
                                                toolCallConsumer.accept(toolCall.name() + " : " + toolCall.arguments());
                                            });
                                        } else {
                                            // 到这里说明流式输出已经结束，这里的Output参数中的message属性中，存储的是模型流式输出的完整内容
                                            completeConsumer.accept(assistantMessage);
                                            conversationHistoryMessagesStorage.save(userId, conversationId, currentRound, message);

                                            log.info("文本对话-模型流式输出文本结果：{}", message.getText());
                                            log.info("文本对话-流式输出类实例完整结果：{}", message);
                                        }
                                    }
                                } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                    // 处理工具执行结果
                                    if (message instanceof ToolResponseMessage toolResponse) {
                                        conversationHistoryMessagesStorage.save(userId, conversationId, currentRound, message);
                                        toolResponse.getResponses().forEach(response -> {
                                            log.debug("文本对话-工具调用 {} : {}", response.name(), response.responseData());
                                            toolResponseConsumer.accept(response.name() + " : " + response.responseData());
                                        });
                                    }
                                } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                                    // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
                                    log.debug("文本对话-Hook 执行完成: {}", output.node());
                                }
                            } else {
                                // 到这里说明是普通的NodeOutput类型
                            }
                        },
                        error -> {
                            log.error("文本消息流式处理错误，会话ID：{}，错误：{}", conversationId, error.getMessage(), error);
                            if (errorConsumer != null) {
                                errorConsumer.accept(error);
                            }
                        },
                        () -> {
                            log.info("文本消息流式处理完成，会话ID：{}", conversationId);
                            // 存储最终思考内容
                            if (!thinkingBuilder.isEmpty()) {
                                log.debug("文本对话-最终模型思考流式输出结果：{}", thinkingBuilder);
                                Message thinkMessage = new ThinkMessage(thinkingBuilder.toString());
                                conversationHistoryMessagesStorage.save(userId, conversationId, currentRound, thinkMessage);
                            }
                            if (finishRunnable != null) {
                                finishRunnable.run();
                            }
                        }
                );
    }

}