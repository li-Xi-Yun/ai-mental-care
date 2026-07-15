package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.user.conversation.UserMessageSendDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.vo.user.conversation.UserMessageSendVO;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.service.user.AIChatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * AI聊天服务实现类
 * <p>实现用户消息发送、会话管理、定时任务、消息处理等核心功能</p>
 * <p>
 * Redis数据结构说明：
 * <ul>
 *     <li>主Key：conversation:cache:{conversationId} (Hash结构)</li>
 *     <li>ZSet Key：conversation:message:zset:{timestamp} (SortedSet结构)</li>
 * </ul>
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-14 16:45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatServiceImpl implements AIChatService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserMessageSendVO sendUserMessage(UserMessageSendDTO userMessageSendDTO) {
        String message = userMessageSendDTO.getMessage();
        Long conversationId = userMessageSendDTO.getConversationId();
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        log.info("开始处理用户消息发送，用户ID：{}，会话ID：{}，消息内容：{}", currentId, conversationId, message);

        Conversation conversation;
        Integer currentRound;

        if (conversationId == null) {
            log.debug("会话ID为空，创建新会话");
            conversation = createNewConversation(currentId);
            conversationId = conversation.getId();
            currentRound = 1;
        } else {
            log.debug("会话ID不为空，查询并验证会话");
            conversation = validateAndGetConversation(conversationId, currentId);
            currentRound = conversation.getCurrentRound();
        }

        log.debug("保存用户消息到数据库，会话ID：{}，轮次：{}", conversationId, currentRound);
        saveUserMessageToDB(conversationId, currentId, message, currentRound);

        LocalDateTime messageTime = LocalDateTime.now();
        log.debug("更新会话状态信息，会话ID：{}", conversationId);
        updateConversationState(conversationId, currentRound, messageTime);

        log.debug("在缓存中存储消息ZSet集合，会话ID：{}", conversationId);
        saveMessageToZSet(conversationId, messageTime);

        log.info("用户消息发送完成，会话ID：{}，当前轮次：{}", conversationId, currentRound);
        return UserMessageSendVO.builder()
                .conversationId(conversationId)
                .currentRound(currentRound)
                .build();
    }

    private Conversation createNewConversation(Long userId) {
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .chatMode(ConversationCacheConstant.CONVERSATION_TYPE_TEXT)
                .build();

        conversationMapper.insert(conversation);
        log.info("新会话创建成功，会话ID：{}，用户ID：{}", conversation.getId(), userId);
        return conversation;
    }

    private Conversation validateAndGetConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getDeleted, DeleteConstant.DELETE_FLAG_NO)
        );

        if (conversation == null) {
            log.error("会话不存在或无权限访问，会话ID：{}，用户ID：{}", conversationId, userId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }

        log.debug("会话验证通过，会话ID：{}，当前轮次：{}", conversationId, conversation.getCurrentRound());
        return conversation;
    }

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
        log.debug("用户消息保存成功，会话ID：{}，轮次：{}", conversationId, roundNum);
    }

    private void updateConversationState(Long conversationId, Integer currentRound, LocalDateTime messageTime) {
        conversationMapper.update(null,
                new LambdaUpdateWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .set(Conversation::getLastActiveTime, messageTime)
        );
        log.debug("会话状态更新成功，会话ID：{}，当前轮次：{}", conversationId, currentRound);
    }

    private void saveMessageToZSet(Long conversationId, LocalDateTime messageTime) {
        String zSetKey = ConversationCacheConstant.CONVERSATION_MESSAGE_ZSET_KEY_PREFIX;

        try {
            RedisUtils.addToScoredSortedSet(zSetKey, messageTime, String.valueOf(conversationId));
            RedisUtils.expire(zSetKey, ConversationCacheConstant.MESSAGE_ZSET_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.debug("消息ZSet集合保存成功，Key：{}", zSetKey);
        } catch (Exception e) {
            log.error("保存消息到ZSet集合失败，会话ID：{}", conversationId, e);
            throw new BusinessException(AIChatExceptionEnum.REDIS_OPERATION_FAILED);
        }
    }

}