package org.lixiyun.server.node;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationException;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.Conversation;
import org.lixiyun.pojo.entity.EmotionDiagnosis;
import org.lixiyun.server.Saver.CustomMysqlSaver;
import org.lixiyun.server.config.prompt.EmotionPromptWord;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
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
 * @since 2026-03-15 13:36
 */
@Slf4j
@Component
public class EmotionalDiagnosisNode implements NodeActionWithConfig, Node {

    private final String NODE_NAME = "emotionalDiagnosis";

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel chatModel;
    @Autowired
    private EmotionDiagnosisMapper emotionDiagnosisMapper;
    @Autowired
    private CustomMysqlSaver customMysqlSaver;

    @Override
    public String getNodeName() {
        return NODE_NAME;
    }

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("emotionDiagnosis")
                .description("情感诊断")
                .chatOptions(ChatOptions.builder()
                        .topK(40)   // 每次只看前 40 个最可能的 token
                        .topP(0.9)   // 只考虑概率最高的那些词，直到它们的总概率 ≥ 90%
                        .frequencyPenalty(0.6)  // 降低已出现 token 的再次出现概率,减少重复（如“好的好的好的”）,越低越重复
                        .presencePenalty(0.2)   // 降低任何已出现 token 的再次出现概率（只要出现过就惩罚）
                        .temperature(0.4)  // 控制输出的随机性 / 创造性,越低越保守
                        .maxTokens(3000)   // 限制模型单次生成的最大 token 数
                        .build()
                )
                .enableLogging(false)   // 启用日志记录,这个会将Agent中的提示词和每次生成的结果都打印出来
                .saver(customMysqlSaver);
    }


    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws GraphRunnerException {
        Optional<Object> currentRoundOpl = config.metadata(Conversation.CURRENT_ROUND);
        int currentRound = (int) currentRoundOpl.orElseThrow(() -> new BusinessException(ConversationException.CONVERSATION_PARAM_ERROR));
        if(currentRound < 5){
            return Map.of();
        }

        Optional<String> threadIdOpl = config.threadId();
        String threadId = threadIdOpl.orElseThrow(() -> {
            log.error("情感诊断threadId:会话不存在");
            return new BusinessException(ConversationException.CONVERSATION_NOT_FOUND);
        });
        Optional<List<Message>> userMessageOpl = state.value("messages");
        List<Message> userMessages = userMessageOpl.orElseThrow(() -> {
            log.error("情感诊断userMessages:会话不存在");
            return new BusinessException(ConversationException.CONVERSATION_NOT_FOUND);
        });
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        EmotionDiagnosis beforeDiagnosis = emotionDiagnosisMapper.selectOne(new LambdaQueryWrapper<EmotionDiagnosis>()
                .eq(EmotionDiagnosis::getConversationId, threadIdOpl.get()));

        EmotionDiagnosis diagnosis;
        // 模型调用生成完整数据信息
        AssistantMessage call = reactAgentBuilder()
                .systemPrompt(String.format(EmotionPromptWord.DIAGNOSIS_OF_PSYCHOLOGICAL_STATE_WITH_MULTIPLE_ROUNDS, currentRound, beforeDiagnosis.toString()))
                .outputType(EmotionDiagnosis.class)
                .build()
                .call(userMessages, config);
        diagnosis = BeanUtil.copyProperties(call.getText(), EmotionDiagnosis.class);

        diagnosis.setConversationId(Long.valueOf(threadId));
        diagnosis.setUserId(currentId);
        diagnosis.setId(null);
        diagnosis.setCreatedBy(null);
        diagnosis.setCreatedTime(null);
        diagnosis.setUpdatedTime(null);
        diagnosis.setDeleted(null);
        
        if(currentRound == 5){
            // 初始化诊断书数据
            emotionDiagnosisMapper.insert(diagnosis);
        } else if(currentRound % 3 == 0){
            // 修改诊断书数据
            emotionDiagnosisMapper.update(diagnosis, new LambdaQueryWrapper<EmotionDiagnosis>()
                    .eq(EmotionDiagnosis::getConversationId, threadIdOpl.get()));
        }

        return Map.of();
    }

}
