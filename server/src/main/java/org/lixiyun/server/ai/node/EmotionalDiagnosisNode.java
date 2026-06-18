package org.lixiyun.server.ai.node;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.constant.prompt.EmotionConstant;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.SpringUtils;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.server.ai.rag.graph.RagGraph;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 用户文本情绪诊断节点
 * @author lixiyun
 * @since 2026-03-15 13:36
 */
@Slf4j
@Builder
public class EmotionalDiagnosisNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "emotionalDiagnosisNode";
    private final int maxRound = 5;

    private final String userInputContextPrompt = PromptUtil.getPrompt(EmotionConstant.USER_INPUT_CONTEXT);
    private final String emotionDiagnosisPrompt = PromptUtil.getPrompt(EmotionConstant.DIAGNOSIS_OF_PSYCHOLOGICAL_STATE_WITH_MULTIPLE_ROUNDS);

    private final ChatModel chatModel;
    private final RagGraph ragGraph;
    private final EmotionDiagnosisMapper emotionDiagnosisMapper = SpringUtils.getBean(EmotionDiagnosisMapper.class);
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor = SpringUtils.getBean(ThreadPoolTaskExecutor.class);

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("emotionDiagnosis")
                .description("情感诊断")
                .chatOptions(chatOptions())
                .enableLogging(false);   // 启用日志记录,这个会将Agent中的提示词和每次生成的结果都打印出来
    }

    private ChatOptions chatOptions() {
        if(chatModel == null){
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        if (chatModel instanceof OllamaChatModel){
            return OllamaChatOptions.builder()
//                    .model("qwen3-7b-chat:thinking")  // 推荐使用带思考模式的千问3，适配青年语境情绪分析
                    // .model("deepseek-r1:latest")     // 备选：DeepSeek R1，推理能力强，适合情绪归因分析

                    .temperature(0.1)          // 极低随机性（0.0-1.0），确保相同对话生成一致的情绪诊断结果
                    .topK(25)                  // 缩小候选词范围，聚焦核心情绪标签（焦虑/开心/抑郁等）
                    .topP(0.8)                 // 聚焦高概率词汇，避免无意义的情绪标签
                    .numPredict(3000)          // 足够容纳完整JSON输出（情绪诊断JSON约500-800token）
                    .seed(42)                  // 固定随机种子，保证相同输入生成相同诊断结果

                    .repeatPenalty(1.3)        // 强化重复惩罚，避免"焦虑、焦虑、焦虑"这类重复标签
                    .frequencyPenalty(0.8)     // 降低高频词重复，优化诊断文本的自然度
                    .presencePenalty(0.4)      // 轻微惩罚已出现的情绪主题，避免分析内容重复
                    .repeatLastN(60)           // 监控最近60个token，覆盖JSON生成核心区域

                    .format("json")            // 强制JSON格式输出（Ollama 0.12+支持）
//                    .stop(List.of("//", "/*", "```")) // 终止符：拦截注释、换行、代码块等冗余内容
                    .truncate(true)            // 自动截断超长对话文本，避免上下文溢出

                    .numCtx(4096)              // 扩大上下文窗口，容纳多轮对话文本+诊断规则
                    .numThread(Runtime.getRuntime().availableProcessors()) // 适配CPU核心数，提升响应速度
                    .numBatch(1024)            // 增大批处理大小，提升长文本处理效率

                    .useMMap(true)             // 内存映射加载模型，提升加载速度
                    .useMLock(false)           // 不锁定内存，避免服务器内存不足
                    .numGPU(-1)                // 自动适配GPU（无GPU则用CPU）
                    .lowVRAM(false)            // 非低显存模式，保证模型推理效率

//                    .enableThinking()          // 启用思考模式，深入理解青年语境（如"摆烂""emo"等）
//                    .thinkOption(ThinkOption.ThinkBoolean.ENABLED)

                    .mirostat(2)               // Mirostat 2.0采样，优化输出连贯性
                    .mirostatTau(2.5f)         // 降低困惑度，减少无意义内容
                    .mirostatEta(0.05f)        // 缓慢调整采样策略，保证输出稳定

                    .build();

        } else if (chatModel instanceof DashScopeChatModel){
            return DashScopeChatOptions.builder()
//                    .model("qwen3-7b-chat")  // 基础版：平衡性能与精度；如需更高精度可换 qwen3-14b-chat

                    .temperature(0.1)        // 极低随机性（0-2），避免情绪标签/置信度波动，确保诊断结果一致
                    .topP(0.8)               // 聚焦高概率词汇（0-1），减少无意义输出，贴合青年语境
                    .topK(40)                // 限制候选词范围（1-100），提升输出确定性，避免无效标签
                    .seed(42)                // 固定随机种子，保证相同输入生成一致的情绪诊断结果
                    .maxToken(3000)          // 足够容纳完整JSON输出（含1000字诊断内容+多维度量化数据）

                    .repetitionPenalty(1.2)  // 惩罚重复token（默认1.1），避免"焦虑、焦虑"这类重复标签

                    .responseFormat(DashScopeResponseFormat.builder()
                            .type(DashScopeResponseFormat.Type.JSON_OBJECT)  // 强制输出合法JSON对象
                            .build())

//                    .stop(List.of("//", "/*", "```"))  // 拦截冗余前缀/符号

                    .enableThinking(true)    // 启用模型思考模式，深入理解"摆烂/emo/内卷"等青年专属语境
                    .thinkingBudget(5)       // 思考预算（1-10），平衡分析深度与响应速度

                    .enableSearch(true)     // 情绪分析无需联网搜索，禁用减少响应时间
                    .stream(false)           // 非流式输出，直接获取完整JSON结果
                    .incrementalOutput(false)// 关闭增量输出，保证结果完整性
                    .multiModel(false)       // 单模型即可满足需求，禁用多模型调度
                    .vlHighResolutionImages(false) // 禁用视觉相关配置，减少资源消耗

//                    .tools(null)             // 清空工具列表
//                    .toolChoice("none")      // 强制模型不调用工具，仅生成情绪分析JSON
//                    .internalToolExecutionEnabled(false)

//                    .toolContext(Map.of())   // 清空工具上下文，避免干扰
//                    .extraBody(Map.of(       // 扩展参数：补充模型调优配置
//                            "logprobs", false,       // 关闭logprobs，避免返回token概率等无关数据
//                            "top_logprobs", null     // 无需返回token概率排名
//                    ))
                    .build();
        } else if (chatModel instanceof DeepSeekChatModel){
            return DeepSeekChatOptions.builder()
//                    .model("deepseek-chat")  // 优先选择对话模型，如需更高推理能力可换 deepseek-reasoner

                    .temperature(0.1)        // 极低随机性（0-2），避免情绪标签/置信度波动，确保诊断结果一致
                    .topP(0.8)               // 聚焦高概率词汇（0-1），减少无意义输出，贴合青年语境
                    .maxTokens(3000)         // 足够容纳完整JSON输出（含1000字诊断内容+多维度量化数据）

                    .frequencyPenalty(0.8)   // 惩罚高频重复token（-2~2），避免"焦虑、焦虑"这类重复标签
                    .presencePenalty(0.4)    // 轻微惩罚已出现的主题（-2~2），避免诊断文本重复

                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormat.Type.JSON_OBJECT)  // 强制输出合法JSON对象
                            .build())

//                    .stop(List.of("//", "/*", "```"))  // 阻止输出JSON外的额外内容，如注释、换行、冗余说明

                    .logprobs(false)         // 关闭logprobs，避免返回token概率等无关数据
                    .topLogprobs(null)       // 无需返回token概率排名

//                    .tools(null)             // 清空工具列表
//                    .toolChoice("none")      // 强制模型不调用工具，仅生成情绪分析JSON
//                    .internalToolExecutionEnabled(false)
//                    .toolContext(Map.of())   // 清空工具上下文，避免干扰

                    .build();
        } else {
            return ChatOptions.builder()
                    .topK(40)   // 每次只看前 40 个最可能的 token
                    .topP(0.9)   // 只考虑概率最高的那些词，直到它们的总概率 ≥ 90%
                    .frequencyPenalty(0.6)  // 降低已出现 token 的再次出现概率,减少重复（如“好的好的好的”）,越低越重复
                    .presencePenalty(0.2)   // 降低任何已出现 token 的再次出现概率（只要出现过就惩罚）
                    .temperature(0.4)  // 控制输出的随机性 / 创造性,越低越保守
                    .maxTokens(3000)   // 限制模型单次生成的最大 token 数
                    .build();
        }
    }


    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws GraphRunnerException {
        log.debug("情感诊断节点开始执行");
        Optional<Object> currentRoundOpl = config.metadata(GraphConstant.CURRENT_ROUND);
        int currentRound = (int) currentRoundOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR));
        if (currentRound % maxRound != 0){
            return Map.of();
        }

        // 数据准备
        Optional<String> threadIdOpl = config.threadId();
        String threadId = threadIdOpl.orElseThrow(() -> {
            log.error("情感诊断threadId:会话不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_FOUND);
        });
        Optional<List<Message>> userMessageOpl = state.value(GraphConstant.MESSAGES);
        List<Message> userMessages = userMessageOpl.orElseThrow(() -> {
            log.error("情感诊断userMessages:会话上下文不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR);
        });
        Optional<String> inputOpl = state.value(GraphConstant.INPUT);
        String userInput = inputOpl.orElseThrow(() -> {
            log.error("情感诊断input:用户输入不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR);
        });
        String input = String.format(userInputContextPrompt, currentRound, userInput);

        Optional<Object> currentIdOpl = config.metadata(GraphConstant.USER_ID);
        Long currentId = (Long) currentIdOpl.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));


        EmotionDiagnosis beforeDiagnosis = emotionDiagnosisMapper.selectOne(new LambdaQueryWrapper<EmotionDiagnosis>()
                .eq(EmotionDiagnosis::getConversationId, threadIdOpl.get()));

        String prompt = String.format(emotionDiagnosisPrompt, Objects.requireNonNullElse(beforeDiagnosis, "")) + input;

        CompletableFuture.runAsync(() -> {
            try {
                // 构建包含RAG结果的提示词
                String finalPrompt = prompt;
                if (ragGraph != null){
                    // 获取RAG流程查询到的内容，作为模型输入
                    String ragResult = ragGraph.executeRag(userMessages, userInput);
                    log.info("情感诊断节点：RAG查询结果:{}", ragResult);
                    if(!ragResult.isBlank()){
                        finalPrompt = finalPrompt + ragResult;
                    }
                }

                // 模型调用生成完整数据信息
                AssistantMessage call = reactAgentBuilder()
                        .systemPrompt(finalPrompt)
                        .outputType(BriefEmotionDiagnosis.class)
                        .build()
                        .call(userMessages);
                String modelOutput = call.getText();
                log.info("情感诊断节点：模型输出结果:{}", modelOutput);

                BriefEmotionDiagnosis briefEmotionDiagnosis = JsonUtils.parseObject(modelOutput, BriefEmotionDiagnosis.class);
                EmotionDiagnosis diagnosis = getEmotionDiagnosis(briefEmotionDiagnosis);

                diagnosis.setConversationId(Long.valueOf(threadId));
                diagnosis.setUserId(currentId);
                diagnosis.setRoundNum(currentRound);
                log.debug("情感诊断节点：转换结果:{}", diagnosis);
                // 初始化诊断书数据
                emotionDiagnosisMapper.insert(diagnosis);
                log.info("情感诊断节点：异步诊断数据保存成功，会话ID:{}", threadId);
            } catch (Exception e) {
                log.error("情感诊断节点：异步执行失败，会话ID:{}", threadId, e);
            }
        }, threadPoolTaskExecutor);

        return Map.of();
    }

    private static EmotionDiagnosis getEmotionDiagnosis(BriefEmotionDiagnosis briefEmotionDiagnosis) {
        EmotionDiagnosis diagnosis = EmotionDiagnosis.builder()
                .diagnosisContent(briefEmotionDiagnosis.getDiagnosisContent())
                .coreEmotionLabel(briefEmotionDiagnosis.getCoreEmotionLabel())
                .coreEmotionConfAvg(briefEmotionDiagnosis.getCoreEmotionConfAvg())
                .coreEmotionIntensity(briefEmotionDiagnosis.getCoreEmotionIntensity())
                .secondaryEmotion(briefEmotionDiagnosis.getSecondaryEmotion())
                .negativeEmotionRatio(briefEmotionDiagnosis.getNegativeEmotionRatio())
                .positiveEmotionRatio(briefEmotionDiagnosis.getPositiveEmotionRatio())
                .neutralEmotionRatio(briefEmotionDiagnosis.getNeutralEmotionRatio())
                .negativeEmotionDetail(briefEmotionDiagnosis.getNegativeEmotionDetail())
                .positiveEmotionDetail(briefEmotionDiagnosis.getPositiveEmotionDetail())
                .emotionTrend(briefEmotionDiagnosis.getEmotionTrend())
                .emotionPeakRound(briefEmotionDiagnosis.getEmotionPeakRound())
                .emotionValleyRound(briefEmotionDiagnosis.getEmotionValleyRound())
                .emotionFluctuationAmplitude(briefEmotionDiagnosis.getEmotionFluctuationAmplitude())
                .emotionStableRounds(briefEmotionDiagnosis.getEmotionStableRounds())
                .coreTriggerScene(briefEmotionDiagnosis.getCoreTriggerScene())
                .coreTriggerKeywords(briefEmotionDiagnosis.getCoreTriggerKeywords())
                .triggerRoundNum(briefEmotionDiagnosis.getTriggerRoundNum())
                .emotionRiskLevel(briefEmotionDiagnosis.getEmotionRiskLevel())
                .emotionAdjustSuggestion(briefEmotionDiagnosis.getEmotionAdjustSuggestion())
                .needManualIntervene(briefEmotionDiagnosis.getNeedManualIntervene())
                .build();
        return diagnosis;
    }

    @Data
    public static class BriefEmotionDiagnosis {
        private String diagnosisContent;
        private String coreEmotionLabel;
        private BigDecimal coreEmotionConfAvg;
        private String coreEmotionIntensity;
        private Map<String, BigDecimal> secondaryEmotion;
        private BigDecimal negativeEmotionRatio;
        private BigDecimal positiveEmotionRatio;
        private BigDecimal neutralEmotionRatio;
        private Map<String, BigDecimal> negativeEmotionDetail;
        private Map<String, BigDecimal> positiveEmotionDetail;
        private String emotionTrend;
        private Integer emotionPeakRound;
        private Integer emotionValleyRound;
        private BigDecimal emotionFluctuationAmplitude;
        private Integer emotionStableRounds;
        private String coreTriggerScene;
        private String coreTriggerKeywords;
        private Integer triggerRoundNum;
        private String emotionRiskLevel;
        private String emotionAdjustSuggestion;
        private Integer needManualIntervene;
    }

}
