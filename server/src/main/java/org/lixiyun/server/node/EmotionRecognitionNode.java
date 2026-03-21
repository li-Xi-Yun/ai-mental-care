package org.lixiyun.server.node;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.SpringUtils;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.entity.EmotionAnalysis;
import org.lixiyun.server.config.prompt.EmotionPromptWord;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.mapper.EmotionAnalysisMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author lixiyun
 * @since 2026-03-15 13:35
 */
@Slf4j
@Builder
public class EmotionRecognitionNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "emotionRecognitionNode";

    private final ChatModel chatModel;
    private final EmotionAnalysisMapper emotionAnalysisMapper = SpringUtils.getBean(EmotionAnalysisMapper.class);

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("emotionRecognition")
                .description("情感识别")
                .chatOptions(chatOptions())
                .enableLogging(false);   // 启用日志记录,这个会将Agent中的提示词和每次生成的结果都打印出来
    }

    private ChatOptions chatOptions() {
        if(chatModel == null){
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        if (chatModel instanceof OllamaChatModel){
            return OllamaChatOptions.builder()
                    .temperature(0.2)          // 极低随机性，确保情绪分析结果稳定、精准（0.0-1.0，越低越保守）
                    .topK(30)                  // 限制候选词范围，减少无意义输出（推荐20-40）
                    .topP(0.85)                // 聚焦高概率词汇，保证输出贴合规则（0.8-0.9）
                    .numPredict(500)           // 最大生成token数，覆盖完整JSON输出（情绪分析JSON最多200-300token）

                    .repeatPenalty(1.2)        // 惩罚重复token，避免"开心、开心、开心"这类重复标签
                    .frequencyPenalty(0.7)     // 降低高频词重复概率，优化分析文本的自然度
                    .presencePenalty(0.3)      // 轻微惩罚已出现的情绪标签，避免过度重复
                    .repeatLastN(50)           // 监控最近50个token的重复，覆盖JSON生成范围

                    .numCtx(4096)              // 足够的上下文窗口，容纳prompt规则+用户输入，默认2048
                    .numThread(Runtime.getRuntime().availableProcessors()) // 适配CPU核心数，提升响应速度

//                    .stop(List.of("//", "/*", "```")) // 终止符：拦截注释、换行、代码块等冗余内容
                    .format("json")            // 强制JSON格式输出（Ollama 0.12+支持）
                    .truncate(true)            // 自动截断超长输入，避免上下文溢出

                    .seed(42)                  // 固定随机种子，保证相同输入生成相同结果
                    .mirostat(2)               // Mirostat 2.0采样，平衡输出多样性与一致性
                    .mirostatTau(3.0f)         // 控制输出困惑度，降低无意义内容
                    .mirostatEta(0.05f)        // 缓慢调整采样策略，保证输出稳定

                    .useMLock(false)           // 不锁定内存（避免服务器内存不足）
                    .useMMap(true)             // 内存映射加载模型，提升加载速度
                    .numGPU(-1)                // 自动适配GPU（无GPU则自动使用CPU）
                    .lowVRAM(false)            // 非低显存模式，保证模型推理效率

//                    .enableThinking()          // 启用模型思考模式（Qwen3/DeepSeek等模型支持，提升情绪分析准确性）
//                    .thinkOption(ThinkOption.ThinkBoolean.ENABLED)
                    .build();

        } else if (chatModel instanceof DashScopeChatModel){
            return DashScopeChatOptions.builder()
//                    .model("qwen3-7b-chat") // 可选：qwen3-14b-chat（精度更高）/qwen-turbo（性价比高）

                    .temperature(0.2)          // 低随机性（0-2），确保情绪标签/置信度识别一致，避免乱输出
                    .topP(0.85)                // 聚焦高概率词汇（0-1），减少无意义内容，贴合青年语境
                    .topK(40)                  // 限制候选词范围（1-100），提升输出确定性
                    .seed(42)                  // 固定随机种子，保证相同输入生成一致结果
                    .maxToken(800)             // 足够容纳标准版情绪分析的完整JSON（多字段、100字分析文本）

                    .repetitionPenalty(1.2)    // 惩罚重复token（默认1.1），避免"emo、emo"这类重复标签

                    .responseFormat(DashScopeResponseFormat.builder()
                            .type(DashScopeResponseFormat.Type.JSON_OBJECT)
                            .build()) // 强制输出合法JSON

//                    .stop(List.of("//", "/*", "```"))  // 拦截冗余前缀/符号

                    .enableThinking(true)      // 启用模型思考模式（Qwen3系列专属），深入理解"摆烂/emo"等青年语境
                    .thinkingBudget(5)         // 思考预算（1-10），平衡分析深度与响应速度

                    .enableSearch(false)       // 情绪分析无需联网搜索，禁用减少响应时间
                    .stream(false)             // 非流式输出，直接获取完整JSON结果
                    .incrementalOutput(false)  // 关闭增量输出，保证结果完整性
                    .multiModel(false)         // 单模型即可满足需求，禁用多模型调度
                    .vlHighResolutionImages(false) // 禁用视觉相关配置，减少资源消耗

//                    .toolChoice("none")        // 禁用工具调用，避免模型生成函数调用内容
//                    .tools(null)               // 清空工具列表，专注情绪分析

                    .build();
        } else if (chatModel instanceof DeepSeekChatModel){
            return DeepSeekChatOptions.builder()
//                    .model("deepseek-chat") // 推荐使用deepseek-chat（对话型），也可使用deepseek-reasoner（推理型）

                    .temperature(0.2)          // 低随机性（0-2），确保情绪标签/置信度识别一致，避免乱输出
                    .topP(0.85)                // 聚焦高概率词汇（0-1），减少无意义内容，保证分析文本贴合青年语境
                    .maxTokens(800)            // 足够容纳完整JSON输出（标准版情绪分析最多600token，留冗余）

                    .frequencyPenalty(0.7)     // 惩罚高频重复token（-2~2），避免"emo、emo"这类重复标签
                    .presencePenalty(0.3)      // 轻微惩罚已出现的主题（-2~2），避免分析文本重复

                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormat.Type.JSON_OBJECT)
                            .build()) // 强制模型输出合法JSON

//                    .stop(List.of("//", "/*", "```"))  // 阻止输出JSON外的额外内容，如注释、换行、冗余说明

                    .logprobs(false)           // 关闭logprobs，减少输出冗余
                    .topLogprobs(null)         // 无需返回token概率

//                    .tools(null)
//                    .toolChoice("none")        // 强制模型不调用工具，仅生成情绪分析结果
//                    .internalToolExecutionEnabled(false)

                    .build();
        } else {
             return ChatOptions.builder()
                    .topK(40)   // 每次只看前 40 个最可能的 token
                    .topP(0.9)   // 只考虑概率最高的那些词，直到它们的总概率 ≥ 90%
                    .frequencyPenalty(0.6)  // 降低已出现 token 的再次出现概率,减少重复（如“好的好的好的”）,越低越重复
                    .presencePenalty(0.2)   // 降低任何已出现 token 的再次出现概率（只要出现过就惩罚）
                    .temperature(0.4)  // 控制输出的随机性 / 创造性,越低越保守
                    .maxTokens(200)   // 限制模型单次生成的最大 token 数
                    .build();
        }
     }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws GraphRunnerException {
        log.debug("情感识别节点开始执行");
        Optional<Object> currentRoundOpl = config.metadata(GraphConstant.CURRENT_ROUND);
        int currentRound = (int) currentRoundOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR));
        Optional<String> threadIdOpl = config.threadId();
        String threadId = threadIdOpl.orElseThrow(() -> {
            log.error("情感识别threadId:会话不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_FOUND);
        });
        Optional<List<Message>> userMessageOpl = state.value(GraphConstant.MESSAGES);
        List<Message> userMessages = userMessageOpl.orElseThrow(() -> {
            log.error("情感识别userMessages:上下文不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_FOUND);
        });
        Optional<String> inputOpl = state.value(OverAllState.DEFAULT_INPUT_KEY);
        String input = String.format(EmotionPromptWord.USER_INPUT_CONTEXT, currentRound, inputOpl.get());
        EmotionAnalysis.EmotionAnalysisBuilder analysis = EmotionAnalysis.builder();
        AssistantMessage call;
        String modelOutput;
        if(currentRound >= 5){
            // 模型调用生成完整数据信息
            call = reactAgentBuilder()
                    .systemPrompt(EmotionPromptWord.STANDARD_ANALYSIS_OF_YOUTH_CONTEXTUAL_EMOTIONS + input)
                    .outputType(CompleteEmotionAnalysis.class)
                    .build()
                    .call(userMessages);
            modelOutput = call.getText();
            log.debug("情感识别节点:模型调用生成完整数据信息:{}", modelOutput);
            CompleteEmotionAnalysis emotionAnalysis = JsonUtils.parseObject(modelOutput, CompleteEmotionAnalysis.class);
            analysis.analysisContent(emotionAnalysis.getAnalysisContent())
                    .emotionLabel(emotionAnalysis.getEmotionLabel())
                    .emotionSubLabel(emotionAnalysis.getEmotionSubLabel())
                    .emotionScore(emotionAnalysis.getEmotionScore())
                    .emotionTrend(emotionAnalysis.getEmotionTrend())
                    .negativeEmotionRatio(emotionAnalysis.getNegativeEmotionRatio())
                    .positiveEmotionRatio(emotionAnalysis.getPositiveEmotionRatio());
        } else{
            // 模型调用生成简略数据信息（情感标签、置信度）
            call = reactAgentBuilder()
                    .systemPrompt(EmotionPromptWord.BRIEF_ANALYSIS_OF_YOUTH_CONTEXTUAL_EMOTIONS + input)
                    .outputType(BriefEmotionAnalysis.class)
                    .build()
                    .call(userMessages);
            modelOutput = call.getText();
            log.debug("情感识别节点:模型调用生成简略数据信息:{}", modelOutput);
            BriefEmotionAnalysis emotionAnalysis = JsonUtils.parseObject(modelOutput, BriefEmotionAnalysis.class);
            analysis.analysisContent(emotionAnalysis.getAnalysisContent())
                    .emotionLabel(emotionAnalysis.getEmotionLabel())
                    .emotionScore(emotionAnalysis.getEmotionScore());
        }
        log.info("情感识别节点:识别结果:{}", modelOutput);
        log.info("情感识别节点:转换结果:{}", analysis);

        Optional<Object> userIdOpl = config.metadata(GraphConstant.USER_ID);
        Long userId = (Long) userIdOpl.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
        analysis.conversationId(Long.valueOf(threadId))
                .userId(userId)
                .roundNum(currentRound);

        emotionAnalysisMapper.insert(analysis.build());

        if(modelOutput == null){
            return Map.of();
        }

        log.debug("情感识别节点结束执行");
        String modelOutputResult = "第" + currentRound + "轮情绪识别结果：" + call.getText();
        return Map.of(GraphConstant.MESSAGES, new AssistantMessage(modelOutputResult));
    }


    @Data
    public static class BriefEmotionAnalysis {
        private String emotionLabel;
        private Double emotionScore;
        private String analysisContent;
    }

    @Data
    public static class CompleteEmotionAnalysis {
        private String analysisContent;
        private String emotionLabel;
        private String emotionSubLabel;
        private Double emotionScore;
        private String emotionTrend;
        private Double negativeEmotionRatio;
        private Double positiveEmotionRatio;
    }
}
