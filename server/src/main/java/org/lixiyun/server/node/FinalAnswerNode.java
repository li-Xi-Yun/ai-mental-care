package org.lixiyun.server.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationException;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.Saver.CustomMysqlSaver;
import org.lixiyun.server.config.prompt.EmotionPromptWord;
import org.lixiyun.server.constant.StreamConstant;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author lixiyun
 * @since 2026-03-15 19:50
 */
@Slf4j
@Component
public class FinalAnswerNode implements NodeActionWithConfig, Node {

    private final String NODE_NAME = "finalAnswer";

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel chatModel;
    @Autowired
    private CustomMysqlSaver customMysqlSaver;


    @Override
    public String getNodeName() {
        return NODE_NAME;
    }

    public ReactAgent reactAgent() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("emotionDiagnosis")
                .description("专业情感陪伴师")
                .systemPrompt(EmotionPromptWord.PROFESSIONAL_EMOTIONAL_COMPANION)
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
                .saver(customMysqlSaver)
                .build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        Optional<List<Message>> userMessageOpl = state.value("messages");
        List<Message> userMessages = userMessageOpl.orElseThrow(() -> {
            log.error("专业情感陪伴师userMessages:会话不存在");
            return new BusinessException(ConversationException.CONVERSATION_NOT_FOUND);
        });

        // 模型调用生成完整数据信息
        Flux<NodeOutput> stream = reactAgent().stream(userMessages, config);

        return Map.of(StreamConstant.STREAM_RESULT, stream);
    }

}
