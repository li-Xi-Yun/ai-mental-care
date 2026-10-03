package org.lixiyun.server.infrastructure.interaction.adapter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.asr.api.AsrResultCallback;
import org.lixiyun.common.agent.asr.model.AsrResult;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.InputAdapterExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.infrastructure.audio.AsrConnectionManager;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.adapter.InputAdapter;
import org.lixiyun.server.infrastructure.interaction.adapter.InputDataType;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputPipelineSessionManager;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.scheduler.ConversationAggregateScheduler;
import org.lixiyun.server.socket.constant.ConversationConstant;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 音频输入适配器
 *
 * <p>负责处理音频类型的用户输入，对接ASR（语音识别）服务。
 * 将音频帧转发给ASR，通过实现{@link AsrResultCallback}处理识别结果回调，
 * 中间结果实时推送到前端，最终结果持久化到数据库并触发AI对话流程。</p>
 *
 * <h3>处理流程：</h3>
 * <ol>
 *     <li>init：注册ASR连接 + 启动聚合定时器</li>
 *     <li>handleAudioFrame：音频帧转发给ASR Provider</li>
 *     <li>ASR回调（onIntermediateResult）：实时推送到前端弹幕</li>
 *     <li>ASR回调（onSentenceEnd）：持久化识别结果 + 重置聚合定时器</li>
 *     <li>destroy：cancel ASR连接 + 清理资源</li>
 * </ol>
 *
 * <h3>线程安全：</h3>
 * <p>本类为 prototype Bean——每个会话通过 {@code InputAdapterFactory}（ObjectProvider）创建独立实例，
 * userId / conversationId 由 {@link #init(Long, Long)} 写入实例字段，实例与单一会话绑定。
 * ASR回调中使用的userId和conversationId在{@link #createAsrCallback}创建时闭包捕获，线程安全。</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class AudioInputAdapter implements InputAdapter {

    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final ConversationCacheManager conversationCacheManager;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final AsrConnectionManager asrConnectionManager;
    private final ConversationAggregateScheduler aggregateScheduler;
    private final OutputPipelineSessionManager outputPipelineSessionManager;

    /** 会话级状态：由 init() 注入，适配器实例与单一会话绑定（prototype） */
    private Long userId;
    private Long conversationId;

    @Override
    public InputDataType getSupportedType() {
        return InputDataType.AUDIO;
    }

    @Override
    public void init(Long userId, Long conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
        asrConnectionManager.register(userId, createAsrCallback(conversationId, userId));
        aggregateScheduler.addTask(conversationId);
        log.info("[音频输入适配器] 会话{}初始化完成，用户ID={}，已注册ASR连接与聚合定时器",
                conversationId, userId);
    }

    @Override
    public void destroy() {
        if (userId != null && conversationId != null) {
            asrConnectionManager.cancel(userId);
            log.info("[音频输入适配器] 会话{}销毁完成，已关闭ASR连接", conversationId);
        }
    }

    @Override
    public void interrupt() {
        log.info("[音频输入适配器] 会话{}中断", conversationId);
    }

    @Override
    public List<NodeEndpoint> getSocketInfo() {
        return Collections.singletonList(
                NodeEndpoint.builder()
                        .description("ASR实时中间识别结果弹幕推送，需要在后面路径中添加/{conversationId}")
                        .path(ConversationConstant.ASR_INTERMEDIATE_RESULT)
                        .build()
        );
    }

    /**
     * 处理音频帧输入
     *
     * <p>将客户端发送的音频数据转发至ASR引擎进行语音识别。
     * 处理前校验会话归属，若输出管道正在生成中则先中断旧输出，
     * 最后更新会话活跃时间。</p>
     *
     * @param audioMessageSendDTO 音频消息DTO，包含会话ID和音频字节数据
     * @throws BusinessException 音频数据为空时抛出{@link InputAdapterExceptionEnum#AUDIO_DATA_EMPTY}
     * @throws BusinessException 会话不存在时抛出{@link InputAdapterExceptionEnum#CONVERSATION_NOT_FOUND}
     */
    public void handleAudioFrame(AudioMessageSendDTO audioMessageSendDTO) {
        byte[] audioMessage = audioMessageSendDTO.getAudioMessage();
        Long conversationId = audioMessageSendDTO.getConversationId();

        log.info(" [音频输入适配器] 开始发送语音消息，会话ID：{}，音频数据长度：{}字节", conversationId,
                audioMessage == null ? 0 : audioMessage.length);

        if (audioMessage == null || audioMessage.length == 0) {
            log.error(" [音频输入适配器] 语音消息数据为空，会话ID：{}", conversationId);
            throw new BusinessException(InputAdapterExceptionEnum.AUDIO_DATA_EMPTY);
        }

        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        validateConversationExistence(conversationId, userId);

        log.debug(" [音频输入适配器] 参数校验通过，会话ID：{}，用户ID：{}，音频数据长度：{}字节", conversationId, userId, audioMessage.length);

        if(outputPipelineSessionManager.isActive(conversationId)){
            outputPipelineSessionManager.interrupt(conversationId);
            log.info(" [音频输入适配器] 会话{}已活跃，用户ID={}，音频数据长度：{}字节，已中断", conversationId, userId, audioMessage.length);
        }

        asrConnectionManager.sendAudio(userId, audioMessage);
        log.debug(" [音频输入适配器] 音频数据已发送至ASR引擎，会话ID：{}，用户ID：{}", conversationId, userId);

        Conversation conversationUpdate = Conversation.builder()
                .id(conversationId)
                .lastActiveTime(LocalDateTime.now())
                .build();

        conversationMapper.updateById(conversationUpdate);
        log.debug(" [音频输入适配器] 数据库活跃时间已更新，会话ID：{}", conversationId);

        log.info(" [音频输入适配器] 语音消息发送完成，会话ID：{}，用户ID：{}", conversationId, userId);
    }

    /**
     * 校验会话存在性和状态有效性
     * <p>
     * 从数据库查询会话信息，校验会话必须存在且状态正常，
     * 不符合条件时记录错误日志并抛出业务异常
     * </p>
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @throws BusinessException 会话不存在时抛出{@link InputAdapterExceptionEnum#CONVERSATION_NOT_FOUND}
     */
    private void validateConversationExistence(Long conversationId, Long userId) {
        log.debug(" [音频输入适配器] 校验会话存在性，会话ID：{}，用户ID：{}", conversationId, userId);

        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId)
        );

        if (conversation == null) {
            log.error(" [音频输入适配器] 会话不存在或无权访问，会话ID：{}，用户ID：{}", conversationId, userId);
            throw new BusinessException(InputAdapterExceptionEnum.CONVERSATION_NOT_FOUND);
        }

        log.debug(" [音频输入适配器] 会话存在性校验通过，会话ID：{}，所属用户ID：{}，当前模式：{}，当前轮次：{}",
                conversationId, conversation.getUserId(), conversation.getChatMode(), conversation.getCurrentRound());
    }

    /**
     * 发送音频流结束信号
     *
     * <p>通知ASR服务音频已全部发送完毕，触发最后的识别结果回调</p>
     *
     * @param userId 用户ID
     */
    public void handleEndOfStream(Long userId) {
        log.info("[音频输入适配器] 发送音频流结束信号，用户ID={}", userId);
        asrConnectionManager.sendEndOfStream(userId);
    }

    /**
     * 创建ASR识别结果回调实例
     * <p>
     * 实现文本收集与实时弹幕WebSocket推送：
     * <ul>
     *     <li>onIntermediateResult：实时弹幕推送，将中间识别文本通过WebSocket推送给前端</li>
     *     <li>onSentenceEnd：文本收集，将完整句子保存到数据库并触发聚合调度</li>
     * </ul>
     * </p>
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return ASR结果回调接口实现
     */
    private AsrResultCallback createAsrCallback(Long conversationId, Long userId) {
        return new AsrResultCallback() {
            @Override
            public void onSentenceBegin(AsrResult result) {
                log.debug(" [音频输入适配器] 设置语音处理标识为活跃，会话ID：{}", conversationId);
            }

            @Override
            public void onIntermediateResult(AsrResult result) {
                String text = result.getText() != null ? result.getText() : "";
                log.debug(" [音频输入适配器] ASR回调-中间识别结果，会话ID：{}，句子编号：{}，文本：{}",
                        conversationId, result.getSentenceIndex(), text);
                conversationWebSocketManager.sendAsrIntermediateResult(userId, conversationId, text);
            }

            @Override
            public void onSentenceEnd(AsrResult result) {
                String text = result.getText() != null ? result.getText() : "";
                log.info(" [音频输入适配器] ASR回调-句子识别完成，会话ID：{}，句子编号：{}，文本：{}，置信度：{}",
                        conversationId, result.getSentenceIndex(), text, result.getConfidence());

                Conversation cacheMetadata = conversationCacheManager.getCacheMetadata(conversationId);
                int currentRound;
                if (cacheMetadata != null && cacheMetadata.getCurrentRound() != null) {
                    currentRound = cacheMetadata.getCurrentRound();
                } else {
                    Conversation conversation = conversationMapper.selectById(conversationId);
                    currentRound = (conversation != null && conversation.getCurrentRound() != null)
                            ? conversation.getCurrentRound() : 1;
                }

                ConversationMemory conversationMemory = ConversationMemory.builder()
                        .userId(userId)
                        .conversationId(conversationId)
                        .content(text)
                        .type(MessageType.USER.getName())
                        .state(ConversationMemory.STATE_NOT_PROCESSED)
                        .roundNum(currentRound)
                        .build();

                conversationMemoryMapper.insert(conversationMemory);
                log.debug("[音频输入适配器] ASR回调-用户消息保存成功，会话ID：{}，轮次：{}，消息ID：{}", conversationId, currentRound, conversationMemory.getId());

                saveMessageToZSet(conversationId);

                aggregateScheduler.addTask(conversationId, 2);
                log.debug("[音频输入适配器] ASR回调-聚合调度器已添加任务，会话ID：{}", conversationId);
            }

            @Override
            public void onError(String taskId, String statusText) {
                log.error("[音频输入适配器] ASR回调-识别失败，会话ID：{}，任务ID：{}，错误：{}", conversationId, taskId, statusText);
            }
        };
    }

    /**
     * 将会话ID加入Redis ZSet延迟队列，触发聚合调度
     *
     * @param conversationId 会话ID
     */
    private void saveMessageToZSet(Long conversationId) {
        conversationCacheManager.saveMessageToZSet(conversationId, LocalDateTime.now());
        log.debug("[音频输入适配器] 消息ZSet集合保存成功，会话ID：{}", conversationId);
    }

}