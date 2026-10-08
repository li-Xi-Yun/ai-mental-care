package org.lixiyun.server.service.tool.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.bo.conversation.tool.ScaleToolResult;
import org.lixiyun.pojo.dto.user.tool.ScaleConversationBindRecordDTO;
import org.lixiyun.pojo.dto.user.tool.ScaleConversationCompleteDTO;
import org.lixiyun.pojo.entity.conversation.ConversationPendingAction;
import org.lixiyun.pojo.entity.scale.ScaleUserRecord;
import org.lixiyun.pojo.vo.user.tool.ScaleActionDataVO;
import org.lixiyun.server.ai.node.tool.ScaleAnalysisNode;
import org.lixiyun.server.mapper.ConversationPendingActionMapper;
import org.lixiyun.server.mapper.ScaleUserRecordMapper;
import org.lixiyun.server.scheduler.ConversationAggregateScheduler;
import org.lixiyun.server.service.tool.ScaleConversationToolService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI对话量表工具服务实现
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleConversationToolServiceImpl implements ScaleConversationToolService {

    /** 已作答标识 */
    private static final int ANSWERED_YES = 1;

    private final ConversationPendingActionMapper pendingActionMapper;
    private final ScaleUserRecordMapper scaleUserRecordMapper;
    private final ScaleAnalysisNode scaleAnalysisNode;
    private final ConversationAggregateScheduler conversationAggregateScheduler;

    @Override
    public void bindRecord(ScaleConversationBindRecordDTO dto) {
        Long toolId = dto.getToolId();
        Long conversationId = dto.getConversationId();
        Long recordId = dto.getRecordId();
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        log.info("[量表工具服务-保存记录ID]，参数：toolId={}，conversationId={}，recordId={}，userId={}",
                toolId, conversationId, recordId, userId);

        ConversationPendingAction pendingAction = getValidPendingAction(toolId, conversationId, userId);
        log.debug("[量表工具服务-保存记录ID]，待处理记录校验通过，toolId={}，status={}", toolId, pendingAction.getStatus());

        ScaleUserRecord record = scaleUserRecordMapper.selectById(recordId);
        if (record == null) {
            log.error("[量表工具服务-保存记录ID]，测评记录不存在，recordId={}", recordId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_FOUND);
        }
        if (record.getUserId() == null || !record.getUserId().equals(userId)) {
            log.error("[量表工具服务-保存记录ID]，测评记录不属于当前用户，recordId={}，userId={}", recordId, userId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_OWNED);
        }

        ScaleActionDataVO actionData = JsonUtils.parseObject(pendingAction.getActionData(), ScaleActionDataVO.class);
        if (actionData == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_TOOL_PENDING_ACTION_INVALID);
        }
        if (actionData.getScaleId() != null && !actionData.getScaleId().equals(record.getScaleId())) {
            log.error("[量表工具服务-保存记录ID]，记录量表与待处理量表不一致，toolId={}，recordScaleId={}，actionScaleId={}",
                    toolId, record.getScaleId(), actionData.getScaleId());
            throw new BusinessException(ScaleExceptionEnum.SCALE_TOOL_PENDING_ACTION_INVALID);
        }

        actionData.setRecordId(recordId);
        pendingAction.setActionData(JsonUtils.toJsonString(actionData));
        pendingActionMapper.updateById(pendingAction);

        log.info("[量表工具服务-保存记录ID]，保存成功，toolId={}，conversationId={}，recordId={}",
                toolId, conversationId, recordId);
    }

    @Override
    public void completeAnswer(ScaleConversationCompleteDTO dto) {
        Long toolId = dto.getToolId();
        Long conversationId = dto.getConversationId();
        Long recordId = dto.getRecordId();
        Integer answered = dto.getAnswered();
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        log.info("[量表工具服务-作答完成]，参数：toolId={}，conversationId={}，recordId={}，answered={}，userId={}",
                toolId, conversationId, recordId, answered, userId);

        ConversationPendingAction pendingAction = getValidPendingAction(toolId, conversationId, userId);
        if (!pendingAction.isWaiting()) {
            log.warn("[量表工具服务-作答完成]，待处理记录非等待状态，忽略本次请求，toolId={}，status={}",
                    toolId, pendingAction.getStatus());
            return;
        }
        log.debug("[量表工具服务-作答完成]，待处理记录校验通过，toolId={}", toolId);

        LocalDateTime now = LocalDateTime.now();

        // 用户未作答：直接取消本次待处理交互
        if (!Integer.valueOf(ANSWERED_YES).equals(answered)) {
            pendingAction.setStatus(ConversationPendingAction.STATUS_CANCELLED);
            pendingAction.setCompletedTime(now);
            pendingActionMapper.updateById(pendingAction);
            log.info("[量表工具服务-作答完成]，用户未作答，已取消待处理交互，toolId={}，conversationId={}",
                    toolId, conversationId);

            // 通知时间轮执行
            conversationAggregateScheduler.addTask(conversationId, 0);

            return;
        }

        // 用户已作答：定位测评记录（优先取入参，为空时回退取 action_data 中已保存的记录ID）
        Long actualRecordId = recordId != null ? recordId : getBoundRecordId(pendingAction);
        if (actualRecordId == null) {
            log.error("[量表工具服务-作答完成]，未找到测评记录ID，toolId={}", toolId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_FOUND);
        }

        ScaleUserRecord record = scaleUserRecordMapper.selectById(actualRecordId);
        if (record == null) {
            log.error("[量表工具服务-作答完成]，测评记录不存在，recordId={}", actualRecordId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_FOUND);
        }
        if (record.getUserId() == null || !record.getUserId().equals(userId)) {
            log.error("[量表工具服务-作答完成]，测评记录不属于当前用户，recordId={}，userId={}", actualRecordId, userId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_OWNED);
        }
        if (!record.isFinished()) {
            log.error("[量表工具服务-作答完成]，测评尚未完成提交，recordId={}，finishStatus={}",
                    actualRecordId, record.getFinishStatus());
            throw new BusinessException(ScaleExceptionEnum.SCALE_TOOL_RECORD_UNFINISHED);
        }
        log.debug("[量表工具服务-作答完成]，测评记录校验通过，recordId={}，scaleName={}",
                actualRecordId, record.getScaleName());

        // 调用分析节点生成分析文本
        ScaleToolResult toolResult = scaleAnalysisNode.apply(conversationId, userId, actualRecordId);
        log.debug("[量表工具服务-作答完成]，分析节点执行完成，toolId={}，分析文本长度：{}",
                toolId, toolResult.getAnalysisText() != null ? toolResult.getAnalysisText().length() : 0);

        // 回写 tool_result，状态置为待对话注入
        pendingAction.setToolResult(JsonUtils.toJsonString(toolResult));
        pendingAction.setStatus(ConversationPendingAction.STATUS_PENDING_INJECT);
        pendingAction.setCompletedTime(now);
        pendingActionMapper.updateById(pendingAction);

        // 通知时间轮执行
        conversationAggregateScheduler.addTask(conversationId, 0);


        log.info("[量表工具服务-作答完成]，分析结果已写入，状态→待对话注入，toolId={}，conversationId={}，recordId={}",
                toolId, conversationId, actualRecordId);
    }

    /**
     * 加载并校验待处理记录：存在、会话匹配、归属当前用户、交互类型为量表测评
     *
     * @param toolId         工具ID（conversation_pending_action.id）
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @return 校验通过的待处理记录
     */
    private ConversationPendingAction getValidPendingAction(Long toolId, Long conversationId, Long userId) {
        ConversationPendingAction pendingAction = pendingActionMapper.selectById(toolId);
        if (pendingAction == null) {
            log.error("[量表工具服务-校验]，待处理记录不存在，toolId={}", toolId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_TOOL_PENDING_ACTION_INVALID);
        }
        if (pendingAction.getConversationId() == null || !pendingAction.getConversationId().equals(conversationId)) {
            log.error("[量表工具服务-校验]，待处理记录会话不匹配，toolId={}，conversationId={}，actualConversationId={}",
                    toolId, conversationId, pendingAction.getConversationId());
            throw new BusinessException(ScaleExceptionEnum.SCALE_TOOL_PENDING_ACTION_INVALID);
        }
        if (pendingAction.getUserId() == null || !pendingAction.getUserId().equals(userId)) {
            log.error("[量表工具服务-校验]，待处理记录不属于当前用户，toolId={}，userId={}，actualUserId={}",
                    toolId, userId, pendingAction.getUserId());
            throw new BusinessException(ScaleExceptionEnum.SCALE_TOOL_PENDING_ACTION_INVALID);
        }
        if (pendingAction.getActionType() == null
                || pendingAction.getActionType() != ConversationPendingAction.ACTION_TYPE_SCALE) {
            log.error("[量表工具服务-校验]，交互类型不是量表测评，toolId={}，actionType={}",
                    toolId, pendingAction.getActionType());
            throw new BusinessException(ScaleExceptionEnum.SCALE_TOOL_PENDING_ACTION_INVALID);
        }
        return pendingAction;
    }

    /**
     * 从待处理记录的 action_data 中取出已保存的测评记录ID
     *
     * @param pendingAction 待处理记录
     * @return 测评记录ID，未保存时返回 null
     */
    private Long getBoundRecordId(ConversationPendingAction pendingAction) {
        ScaleActionDataVO actionData = JsonUtils.parseObject(pendingAction.getActionData(), ScaleActionDataVO.class);
        return actionData == null ? null : actionData.getRecordId();
    }

}
