package org.lixiyun.server.controller.temp;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tts.api.TtsResultCallback;
import org.lixiyun.common.agent.tts.vendor.volcengine.VolcengineTtsProvider;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.common.websocket.utils.WebSocketUtils;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.mapper.ConversationMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 语音对话自动化测试控制器
 * <p>
 * 用文本模拟语音输入，走完整链路：查Redis上下文 → 同步调LLM → TTS流式转语音 → WebSocket推送音频二进制，
 * 模拟真人说话效果。
 * </p>
 *
 * <h3>流程</h3>
 * <ol>
 *     <li>从Redis获取会话元数据和历史上下文</li>
 *     <li>拼接历史上下文 + 当前用户消息构建提示词</li>
 *     <li>同步调用AI模型生成完整回复文本</li>
 *     <li>将回复文本送入TTS进行流式语音合成</li>
 *     <li>TTS每生成一段音频chunk，立即通过WebSocket推送给前端</li>
 *     <li>同时通过WebSocket推送文字流（/audio/reply）</li>
 *     <li>等待TTS合成全部完成后返回HTTP响应</li>
 * </ol>
 *
 * <h3>前端接入</h3>
 * <ul>
 *     <li>WebSocket订阅路径 - 音频文字流：/user/queue/audio/reply/{conversationId}</li>
 *     <li>WebSocket订阅路径 - 音频二进制流：/user/queue/audio/binary/{conversationId}</li>
 *     <li>前端收到二进制音频数据后，通过AudioContext或&lt;audio&gt;标签播放</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-09-24
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/temp/audio-conversation-test")
@Tag(name = "语音对话自动化测试接口", description = "文本模拟语音输入，同步调LLM + TTS流式转语音 + WebSocket推送音频，模拟真人说话")
public class AudioConversationTestController {

    @Autowired
    @Qualifier("deepSeekChatModel")
    private ChatModel chatModel;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationWebSocketManager conversationWebSocketManager;

    @Autowired
    private VolcengineTtsProvider ttsProvider;

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

    @PostMapping("/stream")
    @Operation(summary = "语音对话全链路测试（文本→LLM→TTS→WebSocket音频推送）", description = """
            用文本模拟语音对话的完整链路：
            1. 从Redis获取会话历史上下文
            2. 同步调用AI模型生成回复文本
            3. TTS流式将回复文本转为语音
            4. 逐块通过WebSocket推送音频二进制数据到前端（模拟真人说话）
            5. 同时推送文字流到 /audio/reply 路径
            6. 等待TTS全部完成后返回HTTP响应
            """)
    public Result<AudioTestResultVO> audioStreamTest(
            @RequestBody @Validated AudioTestSendDTO dto
    ) {
        log.info("[语音测试-流式] 会话ID：{}，消息内容：{}", dto.getConversationId(), dto.getMessage());

        Conversation conversation = getConversation(dto.getConversationId());
        if (conversation == null) {
            log.warn("[语音测试-流式] 会话不存在，会话ID：{}", dto.getConversationId());
            return Result.error(404, "会话不存在或缓存未加载");
        }
        Long conversationId = conversation.getId();
        Long userId = conversation.getUserId();

        String replyText;
        if (dto.getMessage() == null || dto.getMessage().isEmpty()) {
            List<ConversationMemory> historyMessages = getHistoryMessagesFromCache(conversationId);
            log.debug("[语音测试-流式] 历史消息数量：{}", historyMessages != null ? historyMessages.size() : 0);

            String userPrompt = buildPrompt(historyMessages, dto.getMessage());
            log.debug("[语音测试-流式] Prompt构建完成，长度：{}", userPrompt.length());

            ReactAgent agent = ReactAgent.builder()
                    .model(chatModel)
                    .name("audio-test-agent")
                    .description("语音对话自动化测试智能体，用于模拟语音对话场景")
                    .systemPrompt(SYSTEM_PROMPT)
                    .chatOptions(DeepSeekChatOptions.builder()
                            .model(modelName)
                            .build())
                    .enableLogging(false)
                    .build();

            try {
                long llmStart = System.currentTimeMillis();
                replyText = agent.call(userPrompt).getText();
                log.info("[语音测试-流式] LLM调用完成，耗时：{}ms，回复长度：{}",
                        System.currentTimeMillis() - llmStart, replyText != null ? replyText.length() : 0);
            } catch (GraphRunnerException e) {
                log.error("[语音测试-流式] LLM调用失败，会话ID：{}", conversationId, e);
                throw new RuntimeException("模型调用失败", e);
            }

            if (replyText == null || replyText.isEmpty()) {
                log.warn("[语音测试-流式] LLM返回空内容，会话ID：{}", conversationId);
                return Result.success(AudioTestResultVO.builder()
                        .conversationId(conversationId)
                        .assistantMessage("")
                        .audioChunkCount(0)
                        .build());
            }
        } else {
            replyText = dto.getMessage();
            log.info("[语音测试-流式] 直接返回消息内容，会话ID：{}，消息内容：{}", conversationId, replyText);
        }

        AtomicInteger audioChunkCount = new AtomicInteger(0);
        CountDownLatch ttsLatch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                log.info("[语音测试-流式] 开始语音合成，会话ID：{}，文本长度：{}", conversationId, replyText.length());
                executeTtsAndPush(userId, conversationId, replyText, audioChunkCount);
            } finally {
                ttsLatch.countDown();
            }
        }, "tts-push-" + conversationId).start();

        try {
            ttsLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[语音测试-流式] 等待TTS完成被中断，会话ID：{}", conversationId);
        }

        log.info("[语音测试-流式] 全链路完成，会话ID：{}，音频chunk数：{}", conversationId, audioChunkCount.get());

        return Result.success(AudioTestResultVO.builder()
                .conversationId(conversationId)
                .assistantMessage(replyText)
                .audioChunkCount(audioChunkCount.get())
                .build());
    }

    private void executeTtsAndPush(Long userId, Long conversationId, String text,
                                   AtomicInteger audioChunkCount) {
        log.info("[语音测试-TTS] 开始语音合成（火山引擎），会话ID：{}，文本长度：{}", conversationId, text.length());

        CountDownLatch ttsCompleteLatch = new CountDownLatch(1);

        TtsResultCallback callback = new TtsResultCallback() {
            @Override
            public void onAudioData(byte[] audioData) {
                int count = audioChunkCount.incrementAndGet();
                log.debug("[语音测试-TTS] 音频chunk#{}，大小：{}字节，会话ID：{}", count, audioData.length, conversationId);
                WebSocketUtils.sendToUserBySubDestination(
                        userId.toString(), "audio/test/" + conversationId, audioData);
            }

            @Override
            public void onSynthesisComplete() {
                log.info("[语音测试-TTS] 语音合成完成，总chunk数：{}，会话ID：{}", audioChunkCount.get(), conversationId);
                conversationWebSocketManager.sendAudioStream(userId, conversationId, "[TTS_COMPLETE]");
                ttsCompleteLatch.countDown();
            }

            @Override
            public void onFail(String taskId, String statusText) {
                log.error("[语音测试-TTS] 语音合成失败，taskId={}, statusText={}, 会话ID={}",
                        taskId, statusText, conversationId);
                ttsCompleteLatch.countDown();
            }
        };

        try {
            String sessionId = ttsProvider.createSession(callback);
            log.info("[语音测试-TTS] 会话{}创建成功，会话ID：{}", sessionId, conversationId);

            ttsProvider.sendTextSegment(sessionId, text);
            log.info("[语音测试-TTS] 文本已发送，会话ID：{}", conversationId);

            ttsProvider.finishSynthesis(sessionId);
            log.info("[语音测试-TTS] 合成已完成，会话ID：{}", conversationId);

            ttsCompleteLatch.await();
        } catch (Exception e) {
            log.error("[语音测试-TTS] 合成执行异常，会话ID：{}", conversationId, e);
        }
    }

    private Conversation getConversation(Long conversationId) {
        Conversation conversation = getConversationFromCache(conversationId);
        if (conversation == null) {
            conversation = conversationMapper.selectById(conversationId);
            log.debug("[语音测试-流式] Redis缓存未命中，从DB获取会话，会话ID：{}", conversationId);
        }
        return conversation;
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
    @Schema(description = "语音对话测试请求")
    public static class AudioTestSendDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotNull(message = "会话ID不能为空")
        @Schema(description = "会话ID，必须为已存在的会话ID", example = "1893456789012345678", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long conversationId;

        @Schema(description = "模拟语音输入的文字内容", example = "最近感觉怎么样？", requiredMode = Schema.RequiredMode.REQUIRED)
        private String message;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "语音对话测试结果")
    public static class AudioTestResultVO implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "会话ID")
        private Long conversationId;

        @Schema(description = "AI模型生成的完整回复文本")
        private String assistantMessage;

        @Schema(description = "TTS生成的音频chunk数量（推送到前端的次数）")
        private int audioChunkCount;
    }
}