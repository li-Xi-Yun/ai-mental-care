package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.user.conversation.AudioInterruptDTO;
import org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.vo.user.conversation.AudioSessionInitVO;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.audio.AsrConnectionManager;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.scheduler.ConversationAggregateScheduler;
import org.lixiyun.server.service.user.AudioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 音频服务实现类
 * <p>
 * 提供语音会话的初始化、消息发送、中断和结束等功能，
 * 基于阿里云ASR实现语音识别，使用WebSocket进行实时通信
 * </p>
 *
 * @author lixiyun
 * @since 2026-03-29 18:17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioServiceImpl implements AudioService {

    private final ConversationMapper conversationMapper;
    private final ConversationCacheManager conversationCacheManager;
    private final AsrConnectionManager asrConnectionManager;
    private final ConversationAggregateScheduler aggregateScheduler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioSessionInitVO initSession(Long conversationId) {
        log.info("音频Service-开始初始化语音会话，会话ID：{}", conversationId);

        Long currentUserId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        if (conversationId == null) {
            return createNewAudioConversation(currentUserId);
        }

        return handleExistingConversation(conversationId, currentUserId);
    }

    /**
     * 创建新的语音会话
     * <p>
     * 当用户首次发起语音对话时，创建新的会话记录并设置为语音模式，
     * 同时初始化ASR连接和时间轮调度器
     * </p>
     *
     * @param userId 当前用户ID
     * @return 语音会话初始化响应VO，包含新创建的会话ID
     */
    private AudioSessionInitVO createNewAudioConversation(Long userId) {
        log.info("音频Service-创建新语音会话，用户ID：{}", userId);

        Conversation newConversation = Conversation.builder()
                .userId(userId)
                .chatMode(ConversationCacheConstant.CONVERSATION_TYPE_AUDIO)
                .lastActiveTime(LocalDateTime.now())
                .build();

        conversationMapper.insert(newConversation);

        Long newConversationId = newConversation.getId();
        log.info("音频Service-新会话创建成功，会话ID：{}", newConversationId);

        initializeAudioResources(newConversationId, userId);

        return AudioSessionInitVO.builder()
                .conversationId(newConversationId)
                .build();
    }

    /**
     * 处理已存在的会话
     * <p>
     * 校验会话存在性和状态，根据当前模式决定是否需要切换到语音模式，
     * 并确保所有音频资源已正确初始化
     * </p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @return 语音会话初始化响应VO
     * @throws BusinessException 会话不存在或状态异常时抛出
     */
    private AudioSessionInitVO handleExistingConversation(Long conversationId, Long userId) {
        log.info("音频Service-处理已存在会话，会话ID：{}，用户ID：{}", conversationId, userId);

        Conversation existingConversation = queryAndValidateConversation(conversationId, userId);

        String currentChatMode = existingConversation.getChatMode();
        log.debug("音频Service-当前会话模式：{}，会话ID：{}", currentChatMode, conversationId);

        if (ConversationCacheConstant.CONVERSATION_TYPE_AUDIO.equals(currentChatMode)) {
            log.info("音频Service-会话已是语音模式，直接返回，会话ID：{}", conversationId);
            ensureAudioResourcesInitialized(conversationId, userId);
            return AudioSessionInitVO.builder()
                    .conversationId(conversationId)
                    .build();
        }

        log.info("音频Service-会话需要从文本模式切换为语音模式，会话ID：{}", conversationId);
        switchToAudioMode(conversationId, existingConversation, userId);

        return AudioSessionInitVO.builder()
                .conversationId(conversationId)
                .build();
    }

    /**
     * 查询并校验会话有效性
     * <p>
     * 从数据库查询会话信息，校验会话是否存在且属于当前用户，
     * 不存在或无权限时抛出业务异常
     * </p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @return 会话实体对象
     * @throws BusinessException 会话不存在或不属于当前用户时抛出
     */
    private Conversation queryAndValidateConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId)
        );

        if (conversation == null) {
            log.error("音频Service-会话不存在或无权访问，会话ID：{}，用户ID：{}", conversationId, userId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }

        log.debug("音频Service-会话校验通过，会话ID：{}", conversationId);
        return conversation;
    }

    /**
     * 将会话切换为语音模式
     * <p>
     * 更新数据库中的会话模式和活跃时间，
     * 刷新缓存数据，并重新初始化所有音频相关资源
     * </p>
     *
     * @param conversationId          会话ID
     * @param conversation            会话实体对象
     * @param userId                  当前用户ID
     */
    private void switchToAudioMode(Long conversationId, Conversation conversation, Long userId) {
        log.info("音频Service-开始切换会话模式为语音模式，会话ID：{}", conversationId);

        conversation.setChatMode(ConversationCacheConstant.CONVERSATION_TYPE_AUDIO);
        conversation.setLastActiveTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        log.debug("音频Service-数据库模式更新完成，会话ID：{}", conversationId);

        conversationCacheManager.updateCacheMetadata(conversationId, conversation);
        log.debug("音频Service-缓存元数据刷新完成，会话ID：{}", conversationId);

        initializeAudioResources(conversationId, userId);

        log.info("音频Service-会话模式切换完成，会话ID：{}", conversationId);
    }

    /**
     * 初始化音频相关资源
     * <p>
     * 为会话创建ASR连接并在时间轮调度器中注册槽位，
     * 用于后续的语音识别和消息聚合处理
     * </p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     */
    private void initializeAudioResources(Long conversationId, Long userId) {
        log.info("音频Service-开始初始化音频资源，会话ID：{}，用户ID：{}", conversationId, userId);

        try {
            asrConnectionManager.register(userId, createAsrCallback(conversationId));
            log.debug("音频Service-ASR连接创建成功，会话ID：{}", conversationId);
        } catch (Exception e) {
            log.error("音频Service-ASR连接创建失败，会话ID：{}，用户ID：{}", conversationId, userId, e);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
        }

        aggregateScheduler.resetAggregateTimer(conversationId, 30);
        log.debug("音频Service-时间轮槽位预留成功，会话ID：{}", conversationId);

        log.info("音频Service-音频资源初始化完成，会话ID：{}", conversationId);
    }

    /**
     * 确保音频资源已初始化
     * <p>
     * 检查并确保ASR连接和时间轮槽位可用，
     * 若资源不存在则重新初始化
     * </p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     */
    private void ensureAudioResourcesInitialized(Long conversationId, Long userId) {
        log.debug("音频Service-检查音频资源状态，会话ID：{}", conversationId);

        try {
            asrConnectionManager.register(userId, createAsrCallback(conversationId));
            log.debug("音频Service-ASR连接确认就绪，会话ID：{}", conversationId);
        } catch (Exception e) {
            log.warn("音频Service-ASR连接需要重建，会话ID：{}，原因：{}", conversationId, e.getMessage());
            try {
                asrConnectionManager.register(userId, createAsrCallback(conversationId));
                log.info("音频Service-ASR连接重建成功，会话ID：{}", conversationId);
            } catch (Exception ex) {
                log.error("音频Service-ASR连接重建失败，会话ID：{}", conversationId, ex);
                throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
            }
        }

        aggregateScheduler.resetAggregateTimer(conversationId, 30);
        log.debug("音频Service-时间轮槽位更新完成，会话ID：{}", conversationId);
    }

    /**
     * 创建ASR识别结果回调实例
     * <p>
     * 生成用于接收ASR识别结果的回调对象，
     * 所有回调方法都会记录日志以便调试和问题追踪
     * </p>
     *
     * @param conversationId 会话ID，用于日志标识
     * @return ASR结果回调接口实现
     */
    private AsrConnectionManager.AsrResultCallback createAsrCallback(Long conversationId) {
        return new AsrConnectionManager.AsrResultCallback() {
            @Override
            public void onIntermediateResult(String text, int sentenceIndex) {
                log.debug("音频Service-ASR中间识别结果，会话ID：{}，句子编号：{}，文本：{}",
                        conversationId, sentenceIndex, text);
            }

            @Override
            public void onTranscriberStart(String taskId) {
                log.info("音频Service-ASR识别开始，会话ID：{}，任务ID：{}", conversationId, taskId);
            }

            @Override
            public void onSentenceBegin(String text, int sentenceIndex) {
                log.debug("音频Service-ASR句子开始，会话ID：{}，句子编号：{}，初始文本：{}",
                        conversationId, sentenceIndex, text);
            }

            @Override
            public void onSentenceEnd(String text, int sentenceIndex, long beginTime, long time, double confidence) {
                log.info("音频Service-ASR句子结束，会话ID：{}，句子编号：{}，最终文本：{}，置信度：{}",
                        conversationId, sentenceIndex, text, confidence);
            }

            @Override
            public void onComplete() {
                log.info("音频Service-ASR整轮识别完成，会话ID：{}", conversationId);
            }

            @Override
            public void onError(String taskId, String statusText) {
                log.error("音频Service-ASR识别错误，会话ID：{}，任务ID：{}，错误信息：{}",
                        conversationId, taskId, statusText);
            }
        };
    }

    @Override
    public void sendAudioMessage(AudioMessageSendDTO audioMessageSendDTO) {

    }

    @Override
    public void interruptAudio(AudioInterruptDTO audioInterruptDTO) {

    }

    @Override
    public void endSession(Long conversationId) {

    }
}