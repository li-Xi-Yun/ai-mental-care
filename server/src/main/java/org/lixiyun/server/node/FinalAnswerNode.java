package org.lixiyun.server.node;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.Conversation;
import org.lixiyun.server.config.prompt.EmotionPromptWord;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author lixiyun
 * @since 2026-03-15 19:50
 */
@Slf4j
@Builder
public class FinalAnswerNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "finalAnswerNode";

    private final ChatModel chatModel;

    private final int maxToken = 200;

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuild() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("finalAnswer")
                .description("专业情感陪伴师")
                .chatOptions(chatOptions())
                .enableLogging(false);   // 启用日志记录,这个会将Agent中的提示词和每次生成的结果都打印出来
//                .saver(CustomMysqlSaver.builder().build())
    }

    private ChatOptions chatOptions() {
        if(chatModel == null){
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        if (chatModel instanceof OllamaChatModel){
            return OllamaChatOptions.builder()
//                    .model("qwen3:7b-chat-thinking") // 推荐：qwen3思考版（适配青年语境）/deepseek-chat（共情能力优）
//                    .keepAlive("30m")                // 模型加载缓存30分钟，减少重复加载耗时

                    .temperature(0.6)                // 中等随机性（0.0-1.0）：既保证共情的自然度，又不偏离陪伴师角色
                    .topK(30)                        // 限制候选词范围（20-40），避免生成生僻/生硬词汇
                    .topP(0.9)                       // 聚焦高概率词汇（0.85-0.9），保证回复通俗亲切
                    .numPredict(maxToken)            // 严格限制最大token数（80字≈60token），避免超长回复
                    .seed(42)                        // 固定随机种子，保证相同情绪输入生成风格一致的回复

                    .repeatPenalty(1.15)             // 轻微惩罚重复token（默认1.1），避免"难受、难受"这类重复
                    .frequencyPenalty(0.4)           // 降低高频词重复（0.3-0.5），避免"我觉得、我觉得"这类冗余
                    .presencePenalty(0.1)            // 极轻微惩罚已出现主题，保证上下文连贯
                    .repeatLastN(20)                 // 监控最近20个token，覆盖单轮回复长度，避免局部重复
                    .penalizeNewline(false)          // 允许自然换行（但prompt要求单行，最终由终止符控制）

//                    .stop(List.of("//", "/*", "```", "。。", "，，")) // 终止符：拦截换行、重复标点、冗余符号
                    .truncate(true)                  // 自动截断超长输入，避免上下文溢出
                    .format(null)                    // 禁用JSON强制格式（情感陪伴需自然文本，非结构化输出）

                    .numCtx(4096)                    // 足够的上下文窗口，容纳多轮对话历史+prompt规则
                    .numThread(Runtime.getRuntime().availableProcessors()) // 适配CPU核心数，提升生成速度
                    .numBatch(512)                   // 批量处理prompt，减少响应延迟

                    .enableThinking()                // 启用模型思考模式（Qwen3/DeepSeek专属），理解"emo/摆烂"等青年表达
                    .mirostat(2)                     // Mirostat 2.0采样，平衡回复的自然度与一致性
                    .mirostatTau(2.0f)               // 降低困惑度，保证回复贴合陪伴师角色设定
                    .mirostatEta(0.05f)              // 缓慢调整采样策略，避免回复风格突变

                    .useMMap(true)                   // 内存映射加载模型，提升加载速度
                    .useMLock(false)                 // 不锁定内存（避免低配服务器OOM）
                    .numGPU(-1)                      // 自动适配GPU（无GPU则用CPU）
                    .lowVRAM(false)                  // 非低显存模式，保证生成效率

//                    .internalToolExecutionEnabled(false)
//                    .toolNames()
//                    .toolCallbacks(List.of())
//                    .toolContext(Map.of())

                    .build();

        } else if (chatModel instanceof DashScopeChatModel){
            return DashScopeChatOptions.builder()
                    // 1. 基础模型选择（优先适配对话/共情能力的模型）
//                    .model("qwen3-7b-chat")  // 核心选择：qwen3-7b-chat（轻量化，响应快）；如需更强共情可选 qwen3-14b-chat

                    .temperature(0.6)        // 中等随机性（0-2）：既保证共情回复的自然度，又不偏离"温柔陪伴"角色设定
                    .topP(0.9)               // 聚焦高概率词汇（0-1）：保证回复通俗亲切，避免生僻/生硬的心理术语
                    .topK(30)                // 限制候选词范围（1-100）：减少无意义词汇，提升回复的流畅度
                    .seed(42)                // 固定随机种子：保证相同情绪输入生成风格一致的陪伴回复
                    .maxToken(maxToken)            // 严格限制生成长度：80token ≈ 100汉字（留冗余），确保回复在40-80字区间

                    .repetitionPenalty(1.15) // 轻微惩罚重复token（默认1.1）：避免"难受、难受"或"我能感受到你、我能感受到你"这类重复

//                    .stop(List.of("//", "/*", "```", "。。", "，，")) // 终止符：拦截换行、重复标点、代码块等冗余内容
                    .responseFormat(DashScopeResponseFormat.builder()
                            .type(DashScopeResponseFormat.Type.TEXT)
                            .build())

                    .enableThinking(true)    // 启用模型思考模式（Qwen3专属）：理解"emo/摆烂/内耗"等青年情绪表达
                    .thinkingBudget(5)       // 思考预算（1-10）：平衡思考深度与响应速度，适配短文本回复场景

                    .enableSearch(true)     // 禁用联网搜索：情感陪伴无需实时信息，避免无关内容干扰
                    .stream(true)           // 非流式输出：直接返回完整回复，适配一对一聊天场景
                    .incrementalOutput(true)// 关闭增量输出：保证回复一次性完整返回
                    .multiModel(false)       // 禁用多模型：单模型足够满足情感陪伴需求
                    .vlHighResolutionImages(false) // 禁用视觉模型：纯文本场景无需图片处理

//                    .tools(null)             // 清空工具列表：避免模型调用函数/工具
//                    .toolChoice("none")      // 强制禁用工具调用：确保模型仅生成情感陪伴文本
//                    .internalToolExecutionEnabled(false) // 禁用内部工具执行生命周期
//                    .toolContext(Map.of())   // 清空工具上下文：避免无关上下文干扰
//                    .toolNames(List.of())    // 清空工具名称：无工具调用需求

                    .build();
        } else if (chatModel instanceof DeepSeekChatModel){
            return DeepSeekChatOptions.builder()
//                    .model("deepseek-chat")  // 核心选择：deepseek-chat（对话型，共情能力优）；如需更强推理可选 deepseek-reasoner

                    .temperature(1.4)        // 中等随机性（0-2）：既保证共情回复的自然度，又不偏离"温柔陪伴"角色设定
                    .topP(0.9)               // 聚焦高概率词汇（0-1）：保证回复通俗亲切，避免生僻/生硬的心理术语
                    .maxTokens(maxToken)           // 严格限制生成长度：80token ≈ 100汉字（留冗余），确保回复在40-80字区间

                    .frequencyPenalty(0.4)   // 重复抑制（-2~2）：降低高频词重复（如"我觉得、我觉得"），保证回复简洁
                    .presencePenalty(0.1)    // 主题惩罚（-2~2）：极轻微惩罚已出现主题，保证上下文连贯，不刻意切换话题

                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormat.Type.TEXT)  // 强制输出合法JSON对象
                            .build())    // 禁用JSON强制格式：情感陪伴需自然文本输出，而非结构化JSON

//                    .stop(List.of("//", "/*", "```", "。。", "，，")) // 终止符：拦截换行、重复标点、代码块等冗余内容

                    .logprobs(false)         // 关闭日志概率：生产环境无需返回token概率，减少输出冗余
                    .topLogprobs(null)       // 无需返回token概率排名：避免无关数据干扰

//                    .tools(null)             // 清空工具列表：避免模型调用函数/工具，专注生成纯文本陪伴回复
//                    .toolChoice("none")      // 强制禁用工具调用：确保模型仅生成情感陪伴文本，不输出工具调用指令
//                    .internalToolExecutionEnabled(false) // 禁用内部工具执行生命周期

//                    .toolContext(Map.of())   // 清空工具上下文：避免无关上下文干扰回复生成
//                    .toolCallbacks(List.of())// 清空工具回调：无工具调用需求，减少资源消耗
//                    .toolNames()    // 清空工具名称：避免模型识别到不必要的工具调用指令

                    .build();
        } else {
            return ChatOptions.builder()
                    .topK(40)   // 每次只看前 40 个最可能的 token
                    .topP(0.9)   // 只考虑概率最高的那些词，直到它们的总概率 ≥ 90%
                    .frequencyPenalty(0.6)  // 降低已出现 token 的再次出现概率,减少重复（如“好的好的好的”）,越低越重复
                    .presencePenalty(0.2)   // 降低任何已出现 token 的再次出现概率（只要出现过就惩罚）
                    .temperature(0.7)  // 控制输出的随机性 / 创造性,越低越保守
                    .maxTokens(maxToken)   // 限制模型单次生成的最大 token 数
                    .build();
        }
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.debug("专业情感陪伴师节点：开始执行");
        Optional<Object> currentRoundOpl = config.metadata(Conversation.CURRENT_ROUND);
        int currentRound = (int) currentRoundOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR));
        Optional<List<Message>> userMessageOpl = state.value(GraphConstant.MESSAGES);
        List<Message> userMessages = userMessageOpl.orElseThrow(() -> {
            log.error("专业情感陪伴师userMessages:会话不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_FOUND);
        });
        Optional<String> inputOpl = state.value(OverAllState.DEFAULT_INPUT_KEY);
        String input = String.format(EmotionPromptWord.USER_INPUT_CONTEXT, currentRound, inputOpl.get());

        // 模型调用生成完整数据信息
        log.debug("专业情感陪伴师节点：开始调用模型:{}", userMessages);
        Flux<NodeOutput> stream = reactAgentBuild()
                .systemPrompt(EmotionPromptWord.PROFESSIONAL_EMOTIONAL_COMPANION + input).build()
                .stream(userMessages);
        log.debug("专业情感陪伴师节点：结束执行");

        return Map.of(GraphConstant.STREAM_RESULT, stream);
    }

}
