package org.lixiyun.server.service.impl.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.enums.InputAdapterExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.user.conversation.AudioInterruptDTO;
import org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO;
import org.lixiyun.pojo.dto.user.conversation.UserMessageSendDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.vo.user.conversation.UserMessageSendVO;
import org.lixiyun.server.infrastructure.conversation.ConversationRepository;
import org.lixiyun.server.infrastructure.interaction.adapter.InputAdapterSessionManager;
import org.lixiyun.server.infrastructure.interaction.adapter.InputDataType;
import org.lixiyun.server.infrastructure.interaction.adapter.impl.AudioInputAdapter;
import org.lixiyun.server.infrastructure.interaction.adapter.impl.TextInputAdapter;
import org.lixiyun.server.service.user.AdapterService;
import org.springframework.stereotype.Service;

/**
 * 输入适配器服务实现
 *
 * <p>统一提供文本/音频输入适配器的消息发送与停止信令入口：</p>
 * <ol>
 *   <li>校验会话归属：会话必须存在且属于当前用户</li>
 *   <li>从 {@link InputAdapterSessionManager} 获取会话绑定的适配器实例</li>
 *   <li>委托适配器执行具体逻辑</li>
 * </ol>
 *
 * @author lixiyun
 * @since 2026-10-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdapterServiceImpl implements AdapterService {

    private final InputAdapterSessionManager inputAdapterSessionManager;
    private final ConversationRepository conversationRepository;

    @Override
    public UserMessageSendVO sendTextMessage(UserMessageSendDTO dto) {
        Long conversationId = dto.getConversationId();
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        validateConversationOwnership(conversationId, userId);
        log.info("[输入适配器] 发送文本消息，会话ID：{}，用户ID：{}", conversationId, userId);

        TextInputAdapter adapter = (TextInputAdapter) inputAdapterSessionManager
                .getInputAdapter(conversationId, InputDataType.TEXT);
        if (adapter == null) {
            log.error("[输入适配器] 会话{}未注册文本输入适配器，用户ID：{}", conversationId, userId);
            throw new BusinessException(InputAdapterExceptionEnum.SESSION_NOT_FOUND);
        }

        UserMessageSendVO result = adapter.handleTextInput(dto);
        log.info("[输入适配器] 文本消息处理完成，会话ID：{}，轮次：{}", result.getConversationId(), result.getCurrentRound());
        return result;
    }

    @Override
    public void sendAudioFrame(AudioMessageSendDTO dto) {
        Long conversationId = dto.getConversationId();
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        validateConversationOwnership(conversationId, userId);
        log.info("[输入适配器] 发送音频帧，会话ID：{}，用户ID：{}，数据长度：{}字节",
                conversationId, userId, dto.getAudioMessage() == null ? 0 : dto.getAudioMessage().length);

        AudioInputAdapter adapter = getAudioAdapter(conversationId);
        adapter.handleAudioFrame(dto);
        log.info("[输入适配器] 音频帧发送完成，会话ID：{}", conversationId);
    }

    /**
     * 校验会话存在且归属当前用户
     *
     * <p>会话不存在、已删除或不属于当前用户时抛出业务异常。</p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID（ThreadLocal）
     * @throws BusinessException 会话不存在或无权访问
     */
    private void validateConversationOwnership(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.getConversationById(conversationId);
        if (conversation == null) {
            log.error("[输入适配器] 会话不存在，会话ID：{}，用户ID：{}", conversationId, userId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }
        if (!userId.equals(conversation.getUserId())) {
            log.error("[输入适配器] 会话不属于当前用户，无权操作，会话ID：{}，会话归属用户{}，请求用户{}",
                    conversationId, conversation.getUserId(), userId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }
        log.debug("[输入适配器] 会话归属校验通过，会话ID：{}，用户ID：{}", conversationId, userId);
    }

    /**
     * 从会话适配器管理中心获取音频输入适配器实例
     *
     * @param conversationId 会话ID
     * @return 音频输入适配器实例
     * @throws BusinessException 会话未注册音频适配器时抛出
     */
    private AudioInputAdapter getAudioAdapter(Long conversationId) {
        AudioInputAdapter adapter = (AudioInputAdapter) inputAdapterSessionManager
                .getInputAdapter(conversationId, InputDataType.AUDIO);
        if (adapter == null) {
            log.error("[输入适配器] 会话{}未注册音频输入适配器", conversationId);
            throw new BusinessException(InputAdapterExceptionEnum.SESSION_NOT_FOUND);
        }
        return adapter;
    }
}