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
import org.lixiyun.server.infrastructure.audio.AsrConnectionManager;
import org.lixiyun.server.infrastructure.audio.AsrQuotaHandler;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.adapter.InputAdapter;
import org.lixiyun.server.infrastructure.interaction.adapter.InputDataType;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputPipelineSessionManager;
import org.lixiyun.server.mapper.ConversationMapper;
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
    private final ConversationCacheManager conversationCacheManager;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final AsrConnectionManager asrConnectionManager;
    private final AsrQuotaHandler asrQuotaHandler;
    private final ConversationAggregateScheduler aggregateScheduler;
    private final OutputPipelineSessionManager outputPipelineSessionManager;

    /** 会话级状态：由 init() 注入，适配器实例与单一会话绑定（prototype） */
    private Long userId;
    private Long conversationId;

    /** 音频能量采样：上次打点时间戳（毫秒），0=尚未打点 */
    private long lastEnergyLogTime = 0;
    /** 音频能量采样：历史最大 RMS */
    private double maxEnergySeen = 0;

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

        // 音频能量采样：首帧与每 ~2s 打点，叠加历史最大 RMS（全程能量≈0 说明是静音/采集侧问题）
        long now = System.currentTimeMillis();
        double energy = computePcmRms(audioMessage);
        if (energy > maxEnergySeen) {
            maxEnergySeen = energy;
        }
        if (lastEnergyLogTime == 0 || now - lastEnergyLogTime >= 2000) {
            lastEnergyLogTime = now;
            log.info(" [音频输入适配器] 音频能量采样，会话ID：{}，RMS：{}，历史最大RMS：{}",
                    conversationId, String.format("%.4f", energy), String.format("%.4f", maxEnergySeen));
        }

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
     * 计算 16bit 小端 PCM 帧的均方根能量（RMS），用于判断真实语音还是静音/无效采集
     *
     * @param pcmBytes 待计算 PCM 字节数组
     * @return RMS 值（约等于 FLOAT32 归一化采样的均方根，静音≈0，正常说话远大于 0）
     */
    private double computePcmRms(byte[] pcmBytes) {
        if (pcmBytes == null || pcmBytes.length == 0) {
            return 0;
        }
        long sumSquares = 0;
        int samples = pcmBytes.length / 2;
        for (int i = 0; i < samples; i++) {
            int lo = pcmBytes[i * 2] & 0xFF;
            int hi = pcmBytes[i * 2 + 1];
            short sample = (short) ((hi << 8) | lo);
            sumSquares += (long) sample * sample;
        }
        return Math.sqrt((double) sumSquares / samples);
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
                // 空中间结果不推送：避免把前端已展示的中间文本清空
                if (text.isBlank()) {
                    return;
                }
                log.debug(" [音频输入适配器] ASR回调-中间识别结果，会话ID：{}，句子编号：{}，文本：{}",
                        conversationId, result.getSentenceIndex(), text);
                conversationWebSocketManager.sendAsrIntermediateResult(userId, conversationId, text);
            }

            @Override
            public void onSentenceEnd(AsrResult result) {
                String text = result.getText() != null ? result.getText() : "";
                log.info(" [音频输入适配器] ASR回调-句子识别完成，会话ID：{}，句子编号：{}，文本：{}，置信度：{}",
                        conversationId, result.getSentenceIndex(), text, result.getConfidence());

                // 服务端 VAD 在整轮长连接中仍可能返回无文本分句（utterance.text=null，
                // 仅词级确认/尾音残留，definite=true）。空文本落库会触发无意义的 AI 回复，直接跳过。
                if (text.isBlank()) {
                    log.info(" [音频输入适配器] ASR分句文本为空（VAD尾音/无文本确认），跳过该句，会话ID：{}，句子编号：{}",
                            conversationId, result.getSentenceIndex());
                    return;
                }

                int currentRound = resolveCurrentRound(conversationId);

                // AI配额（@QuotaLimit 切面拦截）：被拒则未落库、未触发管道，仅推送拒绝事件
                if (!asrQuotaHandler.commitRecognizedSentence(userId, conversationId, text, currentRound)) {
                    log.info("[AI配额] 用户{}语音识别次数已达上限，丢弃该句，会话ID：{}", userId, conversationId);
                }
            }

            @Override
            public void onError(String taskId, String statusText) {
                log.error("[音频输入适配器] ASR回调-识别失败，会话ID：{}，任务ID：{}，错误：{}", conversationId, taskId, statusText);
            }
        };
    }

    /**
     * 解析当前会话轮次（优先缓存，兜底数据库）
     *
     * @param conversationId 会话ID
     * @return 当前轮次
     */
    private int resolveCurrentRound(Long conversationId) {
        Conversation cacheMetadata = conversationCacheManager.getCacheMetadata(conversationId);
        if (cacheMetadata != null && cacheMetadata.getCurrentRound() != null) {
            return cacheMetadata.getCurrentRound();
        }
        Conversation conversation = conversationMapper.selectById(conversationId);
        return (conversation != null && conversation.getCurrentRound() != null)
                ? conversation.getCurrentRound() : 1;
    }

}