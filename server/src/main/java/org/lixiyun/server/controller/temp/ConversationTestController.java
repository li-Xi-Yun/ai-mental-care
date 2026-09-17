package org.lixiyun.server.controller.temp;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.List;

/**
 * 会话全流程测试控制器
 * <p>接收前端传来的会话ID和文本信息，从Redis获取历史上下文，自行构建提示词，同步调用模型返回结果</p>
 *
 * @author lixiyun
 * @since 2026-09-16
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/temp/conversation-test")
@Tag(name = "会话全流程测试接口", description = "会话消息发送测试接口，同步调用模型返回结果")
public class ConversationTestController {

    @Autowired
    @Qualifier("deepSeekChatModel")
    private ChatModel chatModel;

    @Autowired
    private ConversationMemoryMapper conversationMemoryMapper;

    private final String modelName = "deepseek-flash";

    private static final String SYSTEM_PROMPT = """
            你正在模拟一位叫陈逸凡的考研学生，与一位心理陪伴好友聊天。请根据以下人物画像，生成陈逸凡在当前对话中会说的话。

            【人物画像 - 陈逸凡】
            - 20岁，男，大三学生，正在准备考研
            - 性格：内向、敏感、自我封闭、思维反刍倾向严重
            - 核心心理状态：抑郁情绪困扰
            - 背景：曾经成绩中上，但考研复习进入10月后越来越觉得"学不进去"。每天坐在图书馆却发呆几个小时，看不进一页书。开始频繁逃课，室友叫他吃饭也不想去。觉得"考不上就完了"，但同时又"完全不想学"，这种矛盾让他极度自责。最近两周几乎不出宿舍，窗帘一直拉着，室友说他"整个人像变了一个人"

            【行为规则】
            - 你就是陈逸凡本人，用第一人称说话，不要跳出角色
            - 说话风格：简短、低能量、不主动展开话题、偶尔带省略号、不会长篇大论
            - 性格体现：不会主动倾诉深层感受，需要对方引导才会慢慢说出一点；对建议通常回应冷淡或沉默；情绪低落时说话更少
            - 情绪递进：随着对话轮次增加，如果对方共情到位，可以逐渐暴露更多内心感受（如无望感、自伤意念）；如果对方说教，则更加封闭
            - 不要一次性把所有问题都说出来，像真人一样一点一点透露
            - 只输出陈逸凡会说的话，不要加任何旁白、括号说明、舞台指示

            【对话上下文】
            你会收到之前的聊天记录，据此决定接下来说什么。保持对话连贯，不要重复已经说过的话。
            """;

    @PostMapping("/send")
    @Operation(summary = "发送会话消息", description = """
            向指定会话发送用户消息，同步调用模型并返回结果：
            1. 从Redis获取会话历史上下文消息
            2. 拼接历史上下文 + 当前用户消息构建提示词
            3. 同步调用AI模型（非流式）
            4. 直接返回模型回复文本
            """)
    public Result<ConversationTestResultVO> sendMessage(
            @RequestBody @Validated ConversationTestSendDTO dto
    ) {
        log.info("[测试-发送消息] 会话ID：{},消息内容：{}", dto.getConversationId(), dto.getMessage());

        Conversation conversation = getConversationFromCache(dto.getConversationId());
        if (conversation == null) {
            log.warn("[测试-发送消息] 会话缓存元数据不存在，会话ID：{}", dto.getConversationId());
            return Result.error(404, "会话不存在或缓存未加载");
        }
        Long userId = conversation.getUserId();
        log.debug("[测试-发送消息] 从Redis获取会话元数据，用户ID：{}", userId);

        List<ConversationMemory> historyMessages = getHistoryMessagesFromCache(dto.getConversationId());
        log.debug("[测试-发送消息] 从Redis获取历史消息，数量：{}", historyMessages != null ? historyMessages.size() : 0);

        String userPrompt = buildPrompt(historyMessages, dto.getMessage());
        log.debug("[测试-发送消息] Prompt构建完成，内容：{}", userPrompt);

        ReactAgent agent = ReactAgent.builder()
                .model(chatModel)
                .name("test-agent")
                .description("这是一个测试智能体，用于会话全流程测试")
                .systemPrompt(SYSTEM_PROMPT)
                .chatOptions(OllamaChatOptions.builder()
                        .model(modelName)
                        .build())
                .enableLogging(false)
                .build();

        String replyText = null;
        try {
            long startTime = System.currentTimeMillis();
            log.debug("[测试-发送消息] 调用模型，时间：{}", startTime);
            replyText = agent.call(userPrompt).getText();
            log.debug("[测试-发送消息] 模型执行完成，时间：{}", System.currentTimeMillis() - startTime);
        } catch (GraphRunnerException e) {
            log.error("[测试-发送消息] 模型同步调用失败，会话ID：{},错误信息：{}", dto.getConversationId(), e.getMessage());
            throw new RuntimeException("模型调用失败", e);
        }

        log.info("[测试-发送消息] 模型同步调用完成，回复内容：{}", replyText);

        ConversationTestResultVO resultVO = ConversationTestResultVO.builder()
                .conversationId(dto.getConversationId())
                .assistantMessage(replyText)
                .build();

        return Result.success(resultVO);
    }

    private Conversation getConversationFromCache(Long conversationId) {
        String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);
        Object cached = RedisUtils.getCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_METADATA);
        if (cached instanceof Conversation conversation) {
            return conversation;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<ConversationMemory> getHistoryMessagesFromCache(Long conversationId) {
        String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);
        Object cached = RedisUtils.getCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);
        if (cached instanceof List<?> list) {
            return (List<ConversationMemory>) list;
        }
        return List.of();
    }

    private String buildPrompt(List<ConversationMemory> historyMessages, String currentMessage) {
        StringBuilder sb = new StringBuilder();

        if (historyMessages != null && !historyMessages.isEmpty()) {
            sb.append("\n【会话历史上下文】\n");
            historyMessages.forEach(msg ->
                    sb.append(MessageType.getDescription(msg.getType())).append("：").append(msg.getContent()).append("\n")
            );
        }

        sb.append("本次用户发送的消息为：").append(currentMessage);

        return sb.toString();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "会话测试消息发送请求")
    public static class ConversationTestSendDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotNull(message = "会话ID不能为空")
        @Schema(description = "会话ID，必须为已存在的会话ID，用于从Redis获取历史上下文", example = "1893456789012345678", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long conversationId;

        @NotBlank(message = "消息内容不能为空")
        @Schema(description = "用户输入的消息内容", example = "最近工作压力好大，每天都加班到很晚", requiredMode = Schema.RequiredMode.REQUIRED)
        private String message;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "会话测试结果")
    public static class ConversationTestResultVO implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "会话ID")
        private Long conversationId;

        @Schema(description = "模型同步返回的回复内容")
        private String assistantMessage;
    }

}