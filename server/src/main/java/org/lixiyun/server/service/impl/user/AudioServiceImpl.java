package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.asr.api.AsrResultCallback;
import org.lixiyun.common.agent.asr.model.AsrResult;
import org.lixiyun.common.agent.tts.api.TtsResultCallback;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.user.conversation.AudioInterruptDTO;
import org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.vo.user.conversation.AudioSessionInitVO;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.audio.AsrConnectionManager;
import org.lixiyun.server.infrastructure.audio.TtsConnectionManager;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.conversation.ConversationStreamHolder;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.scheduler.ConversationAggregateScheduler;
import org.lixiyun.server.service.user.AudioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final ConversationCacheManager conversationCacheManager;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final AsrConnectionManager asrConnectionManager;
    private final TtsConnectionManager ttsConnectionManager;
    private final ConversationAggregateScheduler aggregateScheduler;
    private final ConversationStreamHolder conversationStreamHolder;

    /** 会话ID映射表，用于存储用户ID到会话ID的映射关系 */
    private final ConcurrentHashMap<Long, Long> conversationIdMap = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioSessionInitVO initSession(Long conversationId) {
        Long currentUserId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[AI语音交互] 开始初始化语音会话，会话ID：{}，用户ID：{}", conversationId, currentUserId);

        AudioSessionInitVO sessionInitVO;
        if (conversationId == null) {
            log.debug("[AI语音交互] 会话ID为空，将创建新语音会话，用户ID：{}", currentUserId);
            sessionInitVO = createNewAudioConversation(currentUserId);
            conversationId = sessionInitVO.getConversationId();
        } else {
            log.debug("[AI语音交互] 会话ID非空，处理已存在会话，会话ID：{}，用户ID：{}", conversationId, currentUserId);
            sessionInitVO = handleExistingConversation(conversationId, currentUserId);
        }

        conversationIdMap.put(currentUserId, conversationId);
        log.debug("[AI语音交互] 会话ID映射已记录，用户ID：{} -> 会话ID：{}", currentUserId, conversationId);

        log.info("[AI语音交互] 语音会话初始化完成，会话ID：{}，用户ID：{}", conversationId, currentUserId);
        return sessionInitVO;
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
        log.info("[AI语音交互] 创建新语音会话，用户ID：{}", userId);

        Conversation newConversation = Conversation.builder()
                .userId(userId)
                .chatMode(ConversationCacheConstant.CONVERSATION_TYPE_AUDIO)
                .lastActiveTime(LocalDateTime.now())
                .build();

        int insertCount = conversationMapper.insert(newConversation);
        Long newConversationId = newConversation.getId();
        log.debug("[AI语音交互] 数据库插入结果：{}，新会话ID：{}，会话模式：{}，活跃时间：{}",
                insertCount, newConversationId, newConversation.getChatMode(), newConversation.getLastActiveTime());

        log.info("[AI语音交互] 新会话创建成功，会话ID：{}", newConversationId);

        initializeAudioResources(newConversationId, userId);

        log.debug("[AI语音交互] 返回AudioSessionInitVO，会话ID：{}", newConversationId);
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
        log.info("[AI语音交互] 处理已存在会话，会话ID：{}，用户ID：{}", conversationId, userId);

        Conversation existingConversation = validateConversationExistence(conversationId, userId);
        log.debug("[AI语音交互] 会话校验通过，当前轮次：{}，当前模式：{}",
                existingConversation.getCurrentRound(), existingConversation.getChatMode());

        Conversation cacheMetadata = conversationCacheManager.getCacheMetadata(conversationId);
        String currentChatMode = cacheMetadata != null ? cacheMetadata.getChatMode() : null;

        log.debug("[AI语音交互] 缓存中的会话模式：{}，会话ID：{}", currentChatMode, conversationId);

        if (!ConversationCacheConstant.CONVERSATION_TYPE_AUDIO.equals(currentChatMode)) {
            log.info("[AI语音交互] 会话需要从文本模式（{}）切换为语音模式，会话ID：{}", currentChatMode, conversationId);
            switchToAudioMode(conversationId, existingConversation);
        } else {
            log.debug("[AI语音交互] 会话已是语音模式，无需切换，会话ID：{}", conversationId);
        }

        initializeAudioResources(conversationId, userId);

        log.debug("[AI语音交互] 已存在会话处理完成，会话ID：{}，用户ID：{}", conversationId, userId);
        return AudioSessionInitVO.builder()
                .conversationId(conversationId)
                .build();
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
     */
    private void switchToAudioMode(Long conversationId, Conversation conversation) {
        log.info("[AI语音交互] 开始切换会话模式为语音模式，会话ID：{}，原始模式：{}", conversationId, conversation.getChatMode());

        conversation.setChatMode(ConversationCacheConstant.CONVERSATION_TYPE_AUDIO);
        conversation.setLastActiveTime(LocalDateTime.now());
        int updateCount = conversationMapper.updateById(conversation);
        log.debug("[AI语音交互] 数据库模式更新完成，影响行数：{}，会话ID：{}", updateCount, conversationId);

        conversationCacheManager.updateCacheMetadata(conversationId, conversation);
        log.debug("[AI语音交互] 缓存元数据已刷新，会话ID：{}", conversationId);

        conversationCacheManager.updateCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_CONVERSATION_TYPE, ConversationCacheConstant.CONVERSATION_TYPE_AUDIO);
        log.debug("[AI语音交互] 缓存Map字段已更新，会话ID：{}", conversationId);

        log.info("[AI语音交互] 会话模式切换完成，会话ID：{}", conversationId);
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
        log.info("[AI语音交互] 开始初始化音频资源，会话ID：{}，用户ID：{}", conversationId, userId);

        Long oldConversationId = conversationIdMap.remove(userId);
        if (oldConversationId != null) {
            log.warn("[AI语音交互] 用户存在旧的音频资源映射，旧会话ID：{}，新会话ID：{}，用户ID：{}",
                    oldConversationId, conversationId, userId);
            endSession(oldConversationId);
        }

        asrConnectionManager.register(userId, createAsrCallback(conversationId, userId));
        log.debug("[AI语音交互] ASR连接注册成功，会话ID：{}，用户ID：{}", conversationId, userId);

        ttsConnectionManager.register(userId, createTtsCallback(conversationId, userId));
        log.debug("[AI语音交互] TTS连接注册成功，会话ID：{}，用户ID：{}", conversationId, userId);

        log.info("[AI语音交互] 音频资源初始化完成，会话ID：{}，用户ID：{}", conversationId, userId);
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
                conversationCacheManager.updateCacheMapValue(conversationId,
                        ConversationCacheConstant.HASH_FIELD_AUDIO_PROCESSING_FLAG,
                        ConversationCacheConstant.AUDIO_PROCESSING_FLAG_ACTIVE);
                log.debug("[AI语音交互] 设置语音处理标识为活跃，会话ID：{}", conversationId);
            }

            @Override
            public void onIntermediateResult(AsrResult result) {
                String text = result.getText() != null ? result.getText() : "";
                log.debug("[AI语音交互] ASR回调-中间识别结果，会话ID：{}，句子编号：{}，文本：{}",
                        conversationId, result.getSentenceIndex(), text);
                conversationWebSocketManager.sendAsrIntermediateResult(userId, conversationId, text);
            }

            @Override
            public void onSentenceEnd(AsrResult result) {
                String text = result.getText() != null ? result.getText() : "";
                log.info("[AI语音交互] ASR回调-句子识别完成，会话ID：{}，句子编号：{}，文本：{}，置信度：{}",
                        conversationId, result.getSentenceIndex(), text, result.getConfidence());

                Conversation cacheMetadata = conversationCacheManager.getCacheMetadata(conversationId);
                int currentRound;
                if (cacheMetadata != null && cacheMetadata.getCurrentRound() != null) {
                    currentRound = cacheMetadata.getCurrentRound();
                } else {
                    Conversation conversation = conversationMapper.selectById(conversationId);
                    currentRound = conversation.getCurrentRound() != null ? conversation.getCurrentRound() : 1;
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
                log.debug("[AI语音交互] ASR回调-用户消息保存成功，会话ID：{}，轮次：{}，消息ID：{}", conversationId, currentRound, conversationMemory.getId());

                saveMessageToZSet(conversationId);

                aggregateScheduler.addTask(conversationId, 2);
                log.debug("[AI语音交互] ASR回调-聚合调度器已添加任务，会话ID：{}", conversationId);
            }

            @Override
            public void onError(String taskId, String statusText) {
                log.error("[AI语音交互] ASR回调-识别失败，会话ID：{}，任务ID：{}，错误：{}", conversationId, taskId, statusText);
            }
        };
    }

    /**
     * 创建TTS合成结果回调实例
     * <p>
     * 实现语音WebSocket推送：
     * <ul>
     *     <li>onAudioData：将TTS合成的音频二进制数据通过WebSocket实时推送给前端</li>
     * </ul>
     * </p>
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return TTS结果回调接口实现
     */
    private TtsResultCallback createTtsCallback(Long conversationId, Long userId) {
        return new TtsResultCallback() {
            @Override
            public void onAudioData(byte[] audioData) {
                log.debug("[AI语音交互] TTS回调-音频数据推送，会话ID：{}，数据长度：{}字节", conversationId, audioData.length);
                conversationWebSocketManager.sendAudioBinary(userId, conversationId, audioData);
            }

            @Override
            public void onSynthesisComplete() {
                conversationCacheManager.deleteCacheMapField(conversationId,
                        ConversationCacheConstant.HASH_FIELD_AUDIO_PROCESSING_FLAG);
                log.debug("[AI语音交互] 删除语音处理标识（合成完成），会话ID：{}", conversationId);
            }

            @Override
            public void onFail(String taskId, String statusText) {
                conversationCacheManager.deleteCacheMapField(conversationId,
                        ConversationCacheConstant.HASH_FIELD_AUDIO_PROCESSING_FLAG);
                log.error("[AI语音交互] TTS回调-合成失败，会话ID：{}，任务ID：{}，错误：{}", conversationId, taskId, statusText);
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
        log.debug("[AI语音交互] 消息ZSet集合保存成功，会话ID：{}", conversationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendAudioMessage(AudioMessageSendDTO audioMessageSendDTO) {
        byte[] audioMessage = audioMessageSendDTO.getAudioMessage();
        Long conversationId = audioMessageSendDTO.getConversationId();

        log.info("[AI语音交互] 开始发送语音消息，会话ID：{}，音频数据长度：{}字节", conversationId,
                audioMessage == null ? 0 : audioMessage.length);

        if (audioMessage == null || audioMessage.length == 0) {
            log.error("[AI语音交互] 语音消息数据为空，会话ID：{}", conversationId);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_DATA_NOT_EXIST);
        }

        Long currentUserId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        validateConversationExistence(conversationId, currentUserId);

        log.debug("[AI语音交互] 参数校验通过，会话ID：{}，用户ID：{}，音频数据长度：{}字节", conversationId, currentUserId, audioMessage.length);

        Integer processingFlag = (Integer) conversationCacheManager.getCacheMapValue(conversationId,
                ConversationCacheConstant.HASH_FIELD_AUDIO_PROCESSING_FLAG);
        if (processingFlag != null && processingFlag == ConversationCacheConstant.AUDIO_PROCESSING_FLAG_ACTIVE) {
            log.info("[AI语音交互] 检测到上次语音处理未完成，触发中断，会话ID：{}", conversationId);
            this.interruptAudio(AudioInterruptDTO.builder().conversationId(conversationId).build());

            conversationCacheManager.deleteCacheMapField(conversationId, ConversationCacheConstant.HASH_FIELD_AUDIO_PROCESSING_FLAG);
            log.debug("[AI语音交互] 中断执行完毕，删除语音处理标识，会话ID：{}", conversationId);
        }

        asrConnectionManager.sendAudio(currentUserId, audioMessage);
        log.debug("[AI语音交互] 音频数据已发送至ASR引擎，会话ID：{}，用户ID：{}", conversationId, currentUserId);

        Conversation conversationUpdate = Conversation.builder()
                .id(conversationId)
                .lastActiveTime(LocalDateTime.now())
                .build();

        conversationMapper.updateById(conversationUpdate);
        log.debug("[AI语音交互] 数据库活跃时间已更新，会话ID：{}", conversationId);

        log.info("[AI语音交互] 语音消息发送完成，会话ID：{}，用户ID：{}", conversationId, currentUserId);
    }

    @Override
    public void interruptAudio(AudioInterruptDTO audioInterruptDTO) {
        Long conversationId = audioInterruptDTO.getConversationId();
        log.info("[AI语音交互] 开始执行语音中断，会话ID：{}", conversationId);

        log.debug("[AI语音交互] 获取语音处理状态标识，会话ID：{}", conversationId);
        Integer audioProcessingFlag = (Integer) conversationCacheManager.getCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_AUDIO_PROCESSING_FLAG);

        if (audioProcessingFlag == null || audioProcessingFlag != ConversationCacheConstant.AUDIO_PROCESSING_FLAG_ACTIVE) {
            log.debug("[AI语音交互] 语音未处于处理中状态（audioProcessingFlag={}），无需中断，会话ID：{}", audioProcessingFlag, conversationId);
            return;
        }

        conversationStreamHolder.cancelStream(conversationId);
        log.debug("[AI语音交互] LLM流式订阅已取消，会话ID：{}", conversationId);

        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        ttsConnectionManager.interrupt(currentId);
        log.debug("[AI语音交互] TTS合成已中断，用户ID：{}", currentId);

        log.info("[AI语音交互] 语音中断执行完成，会话ID：{}，用户ID：{}", conversationId, currentId);
    }

    @Override
    public void stopSpeaking(Long conversationId) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[AI语音交互] 收到用户停止说话信号，会话ID：{}，用户ID：{}", conversationId, userId);
        asrConnectionManager.sendEndOfStream(userId);
        log.debug("[AI语音交互] 音频流结束信号已发送，等待ASR识别结果，会话ID：{}", conversationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endSession(Long conversationId) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[AI语音交互] 开始结束语音会话，会话ID：{}，用户ID：{}", conversationId, userId);

        Conversation conversation = validateConversationExistence(conversationId, userId);
        log.debug("[AI语音交互] 会话校验通过，准备检查消息记录，会话ID：{}", conversationId);

        boolean isCleanSuccess = checkAndCleanEmptyConversation(conversationId);

        Conversation cacheMetadata = conversationCacheManager.getCacheMetadata(conversationId);
        if (cacheMetadata != null) {
            String currentChatMode = cacheMetadata.getChatMode();
            log.debug("[AI语音交互] 缓存中存在会话元数据，当前模式：{}，会话ID：{}", currentChatMode, conversationId);
            if (!ConversationCacheConstant.CONVERSATION_TYPE_AUDIO.equals(currentChatMode)) {
                log.debug("[AI语音交互] 当前会话非语音模式，无需处理，会话ID：{}，当前模式：{}", conversationId, currentChatMode);
                return;
            }
        } else {
            log.debug("[AI语音交互] 缓存中不存在会话元数据，会话ID：{}", conversationId);
        }

        log.debug("[AI语音交互] 开始执行语音中断流程，会话ID：{}", conversationId);
        interruptAudio(AudioInterruptDTO.builder().conversationId(conversationId).build());

        log.debug("[AI语音交互] 关闭ASR连接，用户ID：{}", userId);
        asrConnectionManager.cancel(userId);

        log.debug("[AI语音交互] 关闭TTS连接，用户ID：{}", userId);
        ttsConnectionManager.cancel(userId);

        log.debug("[AI语音交互] 取消时间轮中的聚合任务，会话ID：{}", conversationId);
        aggregateScheduler.cancelTask(conversationId);

        log.debug("[AI语音交互] 从ZSet中移除会话ID，会话ID：{}", conversationId);
        conversationCacheManager.removeCacheZSetValue(conversationId);

        if (isCleanSuccess) {
            log.info("[AI语音交互] 会话无消息记录，删除会话及所有相关数据，会话ID：{}", conversationId);
            conversationMemoryMapper.delete(
                    new LambdaQueryWrapper<ConversationMemory>()
                            .eq(ConversationMemory::getConversationId, conversationId)
            );
        } else {
            log.debug("[AI语音交互] 更新数据库会话模式为文本模式，会话ID：{}", conversationId);
            updateConversationModeToText(conversationId, conversation);

            log.debug("[AI语音交互] 更新缓存会话模式为文本模式，会话ID：{}", conversationId);
            conversationCacheManager.updateCacheMapValue(
                    conversationId,
                    ConversationCacheConstant.HASH_FIELD_CONVERSATION_TYPE,
                    ConversationCacheConstant.CONVERSATION_TYPE_TEXT
            );
        }

        log.info("[AI语音交互] 语音会话结束完成，会话ID：{}，用户ID：{}", conversationId, userId);
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
     * @return 会话实体对象
     * @throws BusinessException 会话不存在时抛出{@link ConversationExceptionEnum#CONVERSATION_NOT_EXIST}
     */
    private Conversation validateConversationExistence(Long conversationId, Long userId) {
        log.debug("[AI语音交互] 校验会话存在性，会话ID：{}，用户ID：{}", conversationId, userId);

        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId)
        );

        if (conversation == null) {
            log.error("[AI语音交互] 会话不存在或无权访问，会话ID：{}，用户ID：{}", conversationId, userId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }

        log.debug("[AI语音交互] 会话存在性校验通过，会话ID：{}，所属用户ID：{}，当前模式：{}，当前轮次：{}",
                conversationId, conversation.getUserId(), conversation.getChatMode(), conversation.getCurrentRound());
        return conversation;
    }

    /**
     * 检查并清理无消息的空会话
     * @param conversationId 会话ID
     * @return 是否清理成功
     */
    private boolean checkAndCleanEmptyConversation(Long conversationId) {
        log.debug("[AI语音交互] 检查会话是否包含消息记录，会话ID：{}", conversationId);

        long messageCount = conversationMemoryMapper.selectCount(
                new LambdaQueryWrapper<ConversationMemory>()
                        .eq(ConversationMemory::getConversationId, conversationId)
        );

        if (messageCount == 0) {
            log.warn("[AI语音交互] 会话无消息记录，执行删除操作，会话ID：{}", conversationId);
            conversationMapper.deleteById(conversationId);
            return true;
        }

        log.debug("[AI语音交互] 会话包含消息记录，消息数：{}，会话ID：{}", messageCount, conversationId);
        return false;
    }

    /**
     * 更新数据库中的会话模式为文本模式
     * <p>
     * 将会话的基础表chatMode字段更新为文本类型，
     * 同时更新最后活跃时间为当前时间
     * </p>
     *
     * @param conversationId 会话ID
     * @param conversation   会话实体对象
     */
    private void updateConversationModeToText(Long conversationId, Conversation conversation) {
        conversation.setChatMode(ConversationCacheConstant.CONVERSATION_TYPE_TEXT);
        conversation.setLastActiveTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        log.debug("[AI语音交互] 数据库模式更新成功，会话ID：{}", conversationId);
    }

}