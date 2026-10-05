package org.lixiyun.server.infrastructure.interaction.adapter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.user.conversation.UserMessageSendDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.vo.user.conversation.UserMessageSendVO;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.interaction.adapter.InputAdapter;
import org.lixiyun.server.infrastructure.interaction.adapter.InputDataType;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.scheduler.ConversationAggregateScheduler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文本输入适配器
 *
 * <p>负责处理文本类型的用户输入消息，将其持久化到数据库并触发后续的AI对话流程。
 * 实现采用与 {@code AIChatServiceImpl} 一致的存储模式，直接通过 Mapper 操作数据库，
 * 确保消息状态标记、会话活跃时间、ZSet消息队列均正确维护。</p>
 *
 * <h3>处理流程：</h3>
 * <ol>
 *     <li>会话兜底：conversationId为空时自动创建新会话</li>
 *     <li>会话校验：conversationId非空时验证会话归属与有效性</li>
 *     <li>消息持久化：构建 {@link ConversationMemory} 实体，标记为未处理状态</li>
 *     <li>状态维护：更新会话最后活跃时间</li>
 *     <li>时序记录：将消息时间戳写入Redis ZSet供聚合处理器轮询</li>
 *     <li>触发聚合：向 {@link ConversationAggregateScheduler} 提交定时任务</li>
 * </ol>
 *
 * <h3>与AIChatServiceImpl的关系：</h3>
 * <p>本适配器是AIChatServiceImpl中 {@code sendUserMessage} 逻辑的适配器层等价实现，
 * 保留相同的会话创建/校验/存储/状态更新/ZSet时序/聚合触发逻辑，
 * 以便后续通过 {@link InputAdapter} 的 {@link #init} 生命周期统一接管会话资源。</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class TextInputAdapter implements InputAdapter {

    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final ConversationCacheManager conversationCacheManager;
    private final ConversationAggregateScheduler aggregateScheduler;

    /** 会话级状态：由 init() 注入，适配器实例与单一会话绑定（prototype） */
    private Long userId;
    private Long conversationId;

    @Override
    public InputDataType getSupportedType() {
        return InputDataType.TEXT;
    }

    @Override
    public void init(Long userId, Long conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
        log.info("[文本输入适配器] 会话初始化完成，conversationId={}，userId={}",
                conversationId, userId);
    }

    @Override
    public void destroy() {
        log.info("[文本输入适配器] 会话已销毁，conversationId={}", conversationId);
    }

    @Override
    public void interrupt() {
        log.info("[文本输入适配器] 会话已中断，conversationId={}", conversationId);
    }

    @Override
    public List<NodeEndpoint> getSocketInfo() {
        return List.of();
    }

    /**
     * 处理文本输入消息（核心业务入口）
     * <p>
     * 会话兜底创建 → 校验归属 → 持久化 → 更新活跃时间 → 写ZSet时序 → 触发聚合调度。
     * conversationId为空时自动创建新文本会话，非空时校验会话有效性。</p>
     *
     * @param userMessageSendDTO 用户消息发送DTO，包含会话ID和消息内容
     * @return UserMessageSendVO 包含实际会话ID和当前对话轮次
     * @throws BusinessException 当会话不存在或无权限访问时抛出（{@link ConversationExceptionEnum#CONVERSATION_NOT_EXIST}）
     * @throws BusinessException 当用户未登录、ThreadLocal中无用户ID时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public UserMessageSendVO handleTextInput(UserMessageSendDTO userMessageSendDTO) {
        String message = userMessageSendDTO.getMessage();
        Long conversationId = userMessageSendDTO.getConversationId();
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        log.info("[文本输入适配器] 开始处理消息，userId={}，conversationId={}，消息长度={}",
                currentId, conversationId, message != null ? message.length() : 0);

        Conversation conversation;
        Integer currentRound;

        conversation = validateAndGetConversation(conversationId, currentId);
        currentRound = conversation.getCurrentRound();
        log.debug("[文本输入适配器] 会话校验通过，conversationId={}，currentRound={}", conversationId, currentRound);

        saveUserMessageToDB(conversationId, currentId, message, currentRound);

        LocalDateTime messageTime = LocalDateTime.now();
        updateConversationState(conversationId, messageTime);

        saveMessageToZSet(conversationId, messageTime);

        aggregateScheduler.addTask(conversationId);

        log.info("[文本输入适配器] 消息处理完成，conversationId={}，currentRound={}",
                conversationId, currentRound);
        return UserMessageSendVO.builder()
                .conversationId(conversationId)
                .currentRound(currentRound)
                .build();
    }

    /**
     * 校验会话归属与有效性
     *
     * <p>通过会话ID + 用户ID + 未删除标记三重条件查询，
     * 确保当前用户只操作自己的会话。</p>
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 通过校验的会话实体
     * @throws BusinessException 会话不存在、已删除或不属于当前用户时抛出
     */
    private Conversation validateAndGetConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getDeleted, DeleteConstant.DELETE_FLAG_NO)
        );

        if (conversation == null) {
            log.error("[文本输入适配器] 会话不存在或无权限访问，conversationId={}，userId={}",
                    conversationId, userId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }

        return conversation;
    }

    /**
     * 将用户消息持久化到数据库
     *
     * <p>消息状态标记为 {@link ConversationCacheConstant#PROCESS_FLAG_NOT_PROCESSED}，
     * 供后续 {@code ConversationMessageProcessor} 拉取并处理。</p>
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param message        消息文本内容
     * @param roundNum       当前对话轮次
     */
    private void saveUserMessageToDB(Long conversationId, Long userId, String message, Integer roundNum) {
        ConversationMemory conversationMemory = ConversationMemory.builder()
                .userId(userId)
                .conversationId(conversationId)
                .content(message)
                .type(MessageType.USER.getName())
                .state(ConversationCacheConstant.PROCESS_FLAG_NOT_PROCESSED)
                .roundNum(roundNum)
                .build();

        conversationMemoryMapper.insert(conversationMemory);
        log.debug("[文本输入适配器] 消息已持久化，conversationId={}，roundNum={}，消息长度={}",
                conversationId, roundNum, message != null ? message.length() : 0);
    }

    /**
     * 更新会话的最后活跃时间
     *
     * <p>仅在最后一次活跃时间刷新为当前时刻，用于会话排序和超时判断。</p>
     *
     * @param conversationId 会话ID
     * @param messageTime    消息发送时间戳
     */
    private void updateConversationState(Long conversationId, LocalDateTime messageTime) {
        conversationMapper.update(null,
                new LambdaUpdateWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .set(Conversation::getLastActiveTime, messageTime)
        );
        log.debug("[文本输入适配器] 会话活跃时间已更新，conversationId={}，lastActiveTime={}",
                conversationId, messageTime);
    }

    /**
     * 将消息时间戳写入Redis ZSet，供聚合处理器按时间窗口拉取
     *
     * @param conversationId 会话ID
     * @param messageTime    消息发送时间戳
     */
    private void saveMessageToZSet(Long conversationId, LocalDateTime messageTime) {
        conversationCacheManager.saveMessageToZSet(conversationId, messageTime);
        log.debug("[文本输入适配器] 消息ZSet时序已记录，conversationId={}", conversationId);
    }

}