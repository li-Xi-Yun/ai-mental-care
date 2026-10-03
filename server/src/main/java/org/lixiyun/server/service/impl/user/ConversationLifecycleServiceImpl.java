package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.user.conversation.ConversationLifecycleInitDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.vo.user.conversation.ConversationLifecycleInitVO;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.conversation.ConversationRepository;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.adapter.InputAdapterSessionManager;
import org.lixiyun.server.infrastructure.interaction.adapter.InputDataType;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputDataType;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputPipelineSessionManager;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.scheduler.ConversationAggregateScheduler;
import org.lixiyun.server.service.user.ConversationLifecycleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对话生命周期服务实现
 *
 * <p>编排 {@link InputAdapterSessionManager} 和 {@link OutputPipelineSessionManager}，
 * 实现输入层适配器和输出层管道的完整生命周期管理。</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationLifecycleServiceImpl implements ConversationLifecycleService {

    private final InputAdapterSessionManager inputAdapterSessionManager;
    private final OutputPipelineSessionManager outputPipelineSessionManager;
    private final ConversationRepository conversationRepository;
    private final ConversationCacheManager conversationCacheManager;
    private final ConversationAggregateScheduler aggregateScheduler;
    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;

    @Override
    public ConversationLifecycleInitVO initSession(ConversationLifecycleInitDTO request) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        Long conversationId = request.getConversationId();
        Set<InputDataType> inputTypes = InputDataType.fromStrings(request.getInputTypes());
        Set<OutputDataType> outputTypes = OutputDataType.fromStrings(request.getOutputTypes());

        log.info("[对话生命周期] 开始初始化，会话ID：{}，用户ID：{}，inputTypes：{}，outputTypes：{}",
                conversationId, userId, inputTypes, outputTypes);

        // 会话ID为空时创建新会话；非空时校验会话归属，防止跨用户越权绑定资源
        if (conversationId == null) {
            conversationId = createNewConversation(userId);
            log.info("[对话生命周期] 会话ID为空，已创建新会话，会话ID：{}，用户ID：{}", conversationId, userId);
        } else {
            validateConversationOwnership(conversationId, userId);
        }

        // 1. 输入层：工厂组装适配器 → 统一初始化 → 纳入会话管理
        inputAdapterSessionManager.register(inputTypes, userId, conversationId);

        // 2. 输出层：工厂组装节点链 → 校验类型 → 初始化 → 绑定到会话
        outputPipelineSessionManager.initSession(conversationId, userId, outputTypes);

        // 3. 构建客户端所需的 Socket 端点路径
        List<NodeEndpoint> inputEndpoints = inputAdapterSessionManager.buildEndpointInfo(conversationId);
        List<NodeEndpoint> outputEndpoints = outputPipelineSessionManager.buildEndpointInfo(conversationId);

        ConversationLifecycleInitVO vo = ConversationLifecycleInitVO.builder()
                .conversationId(conversationId)
                .inputEndpoints(convertEndpoints(inputEndpoints))
                .outputEndpoints(convertEndpoints(outputEndpoints))
                .build();

        log.info("[对话生命周期] 初始化完成，会话ID：{}，输入端点{}个，输出端点{}个",
                conversationId, inputEndpoints.size(), outputEndpoints.size());
        return vo;
    }

    @Override
    public void endSession(Long conversationId) {
        log.info("[对话生命周期] 开始销毁，会话ID：{}", conversationId);

        // 输入层和输出层各自独立销毁，一方失败不影响另一方
        inputAdapterSessionManager.endSession(conversationId);
        outputPipelineSessionManager.endSession(conversationId);

        // 清理时间轮聚合任务与 Redis ZSet 队列，避免到期任务重复触发空管道或残留会话ID
        aggregateScheduler.cancelTask(conversationId);
        conversationCacheManager.removeCacheZSetValue(conversationId);

        // 会话无有效数据消息时删除（本次对话未产生任何消息，视为空会话）
        deleteEmptyConversation(conversationId);

        log.info("[对话生命周期] 销毁完成，会话ID：{}", conversationId);
    }

    /**
     * 创建新的文本模式会话
     *
     * @param userId 用户ID
     * @return 新创建的会话ID
     */
    private Long createNewConversation(Long userId) {
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .chatMode(ConversationCacheConstant.CONVERSATION_TYPE_TEXT)
                .build();

        conversationMapper.insert(conversation);
        log.debug("[对话生命周期] 新会话创建成功，会话ID：{}，用户ID：{}", conversation.getId(), userId);
        return conversation.getId();
    }

    /**
     * 检查并删除无有效数据消息的空会话
     *
     * <p>会话无任何消息记录时，删除会话及关联数据，避免产生空会话残留。</p>
     *
     * @param conversationId 会话ID
     */
    private void deleteEmptyConversation(Long conversationId) {
        long messageCount = conversationMemoryMapper.selectCount(
                new LambdaQueryWrapper<ConversationMemory>()
                        .eq(ConversationMemory::getConversationId, conversationId)
        );

        if (messageCount == 0) {
            conversationMapper.deleteById(conversationId);
            log.warn("[对话生命周期] 会话{}无消息记录，已删除空会话", conversationId);
        } else {
            log.debug("[对话生命周期] 会话{}含{}条消息记录，保留会话", conversationId, messageCount);
        }
    }

    /**
     * 校验会话存在且归属当前用户
     *
     * <p>会话不存在、已删除（逻辑删除）或不属于当前用户时抛出业务异常。</p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID（ThreadLocal）
     * @throws BusinessException 会话不存在或无权访问
     */
    private void validateConversationOwnership(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.getConversationById(conversationId);
        if (conversation == null) {
            log.error("[对话生命周期] 会话不存在，无法初始化，会话ID：{}，用户ID：{}", conversationId, userId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }
        if (!userId.equals(conversation.getUserId())) {
            log.error("[对话生命周期] 会话不属于当前用户，无权初始化，会话ID：{}，会话归属用户{}，请求用户{}",
                    conversationId, conversation.getUserId(), userId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }
        log.debug("[对话生命周期] 会话归属校验通过，会话ID：{}，用户ID：{}", conversationId, userId);
    }

    /**
     * 将 NodeEndpoint 列表转换为 VO 的 EndpointInfo 列表
     */
    private List<ConversationLifecycleInitVO.EndpointInfo> convertEndpoints(List<NodeEndpoint> endpoints) {
        if (endpoints == null) {
            return List.of();
        }
        return endpoints.stream()
                .map(e -> ConversationLifecycleInitVO.EndpointInfo.builder()
                        .description(e.getDescription())
                        .path(e.getPath())
                        .build())
                .collect(Collectors.toList());
    }
}