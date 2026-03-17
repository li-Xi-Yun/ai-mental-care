package org.lixiyun.server.node;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationException;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.Conversation;
import org.lixiyun.pojo.entity.EmotionAnalysis;
import org.lixiyun.server.Saver.CustomMysqlSaver;
import org.lixiyun.server.config.prompt.EmotionPromptWord;
import org.lixiyun.server.mapper.EmotionAnalysisMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author lixiyun
 * @since 2026-03-15 13:35
 */
@Slf4j
@Builder
@Component
public class EmotionRecognitionNode implements NodeActionWithConfig, Node {

    private  static final String NODE_NAME = "emotionRecognition";

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel chatModel;
    @Autowired
    private EmotionAnalysisMapper emotionAnalysisMapper;
    @Autowired
    private CustomMysqlSaver customMysqlSaver;

    @Override
    public String getNodeName() {
        return NODE_NAME;
    }

    private static class BriefEmotionAnalysis {
        private String emotionLabel;
        private Double emotionScore;
    }

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder() {
        MemorySaver saver = CustomMysqlSaver.builder().build();
        return ReactAgent.builder()
                .model(chatModel)
                .name("emotionRecognition")
                .description("情感识别")
                .chatOptions(ChatOptions.builder()
                                .topK(40)   // 每次只看前 40 个最可能的 token
                                .topP(0.9)   // 只考虑概率最高的那些词，直到它们的总概率 ≥ 90%
                                .frequencyPenalty(0.6)  // 降低已出现 token 的再次出现概率,减少重复（如“好的好的好的”）,越低越重复
                                .presencePenalty(0.2)   // 降低任何已出现 token 的再次出现概率（只要出现过就惩罚）
                                .temperature(0.4)  // 控制输出的随机性 / 创造性,越低越保守
                                .maxTokens(80)   // 限制模型单次生成的最大 token 数
                                .build()
                )
                .enableLogging(false)   // 启用日志记录,这个会将Agent中的提示词和每次生成的结果都打印出来
                .saver(customMysqlSaver);
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws GraphRunnerException {
        Optional<Object> currentRoundOpl = config.metadata(Conversation.CURRENT_ROUND);
        int currentRound = (int) currentRoundOpl.orElseThrow(() -> new BusinessException(ConversationException.CONVERSATION_PARAM_ERROR));
        Optional<String> threadIdOpl = config.threadId();
        String threadId = threadIdOpl.orElseThrow(() -> {
            log.error("情感识别threadId:会话不存在");
            return new BusinessException(ConversationException.CONVERSATION_NOT_FOUND);
        });
        Optional<List<Message>> userMessageOpl = state.value("messages");
//        List<Message> userMessages = userMessageOpl.orElseThrow(() -> {
//            log.error("情感识别userMessages:会话不存在");
//            return new BusinessException(ConversationException.CONVERSATION_NOT_FOUND);
//        });
        Optional<String> inputOpl = state.value(OverAllState.DEFAULT_INPUT_KEY);
        EmotionAnalysis analysis;
        if(currentRound >= 5){
            // 模型调用生成完整数据信息
            AssistantMessage call = reactAgentBuilder()
                    .systemPrompt(EmotionPromptWord.STANDARD_ANALYSIS_OF_YOUTH_CONTEXTUAL_EMOTIONS)
                    .outputType(EmotionAnalysis.class)
                    .build()
                    .call(inputOpl.get(), config);
//                    .call(userMessages, config);
            analysis = BeanUtil.copyProperties(call.getText(), EmotionAnalysis.class);
        } else{
            // 模型调用生成简略数据信息（情感标签、置信度）
            AssistantMessage call = reactAgentBuilder()
                    .systemPrompt(EmotionPromptWord.BRIEF_ANALYSIS_OF_YOUTH_CONTEXTUAL_EMOTIONS)
                    .outputType(BriefEmotionAnalysis.class)
                    .build()
                    .call(inputOpl.get(), config);
//                    .call(userMessages, config);
            analysis = BeanUtil.copyProperties(call.getText(), EmotionAnalysis.class);
        }

        analysis.setConversationId(Long.valueOf(threadId));
        analysis.setRoundNum(currentRound);
        analysis.setId(null);
        analysis.setCreatedBy(null);
        analysis.setCreatedTime(null);
        analysis.setDeleted(null);
        emotionAnalysisMapper.insert(analysis);

        return Map.of("messages", analysis);
    }
}
