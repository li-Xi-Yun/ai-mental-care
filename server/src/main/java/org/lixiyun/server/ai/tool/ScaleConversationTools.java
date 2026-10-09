package org.lixiyun.server.ai.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tool.Tools;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationPendingAction;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.entity.scale.ScaleVersion;
import org.lixiyun.pojo.vo.user.tool.ScaleActionDataVO;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.mapper.*;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会话量表人工交互工具类
 *
 * @author lixiyun
 * @since 2026-10-06
 */
@Slf4j
@Component(ScaleConversationTools.NAME)
@RequiredArgsConstructor
public class ScaleConversationTools implements Tools {

    public static final String NAME = "ScaleConversationTools";

    public static final String LIST_AVAILABLE_SCALES = "listAvailableScales";
    public static final String CREATE_SCALE_PENDING_ACTION = "createScalePendingAction";
    public static final String QUERY_TOOL_RESULT = "queryToolResult";

    private final ScaleMapper scaleMapper;
    private final ScaleVersionMapper scaleVersionMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationPendingActionMapper pendingActionMapper;
    private final ConversationWebSocketManager conversationWebSocketManager;

    @Tool(name = LIST_AVAILABLE_SCALES,
            description = "获取所有可用的心理测评量表列表，包含量表名称、题目数和简要描述，用于根据用户症状选择合适的量表进行推荐")
    public String listAvailableScales() {
        log.info("[量表工具] 开始查询所有可用量表");

        List<Scale> scales = scaleMapper.selectList(
                new LambdaQueryWrapper<Scale>()
                        .eq(Scale::getStatus, Scale.STATUS_ENABLE)
                        .eq(Scale::getDeleted, DeleteConstant.DELETE_FLAG_NO));

        if (CollUtil.isEmpty(scales)) {
            log.warn("[量表工具] 未查询到任何启用的量表");
            return "当前没有可用的心理测评量表。";
        }

        StringBuilder sb = new StringBuilder("以下是当前可用的心理测评量表：\n");
        for (int i = 0; i < scales.size(); i++) {
            Scale scale = scales.get(i);
            sb.append(i + 1).append(". ").append("量表ID：").append(scale.getId())
                    .append("，名称：").append(scale.getScaleName());

            if (scale.getCurrentVersionId() != null) {
                ScaleVersion version = scaleVersionMapper.selectById(scale.getCurrentVersionId());
                String description = version != null ? StrUtil.nullToEmpty(version.getDescription()) : "";

                Long questionCount = scaleQuestionMapper.selectCount(
                        new LambdaQueryWrapper<ScaleQuestion>()
                                .eq(ScaleQuestion::getScaleVersionId, scale.getCurrentVersionId())
                                .eq(ScaleQuestion::getDeleted, DeleteConstant.DELETE_FLAG_NO));
                int count = questionCount != null ? questionCount.intValue() : 0;

                sb.append("，题目数：").append(count).append("题");
                if (StrUtil.isNotBlank(description)) {
                    sb.append("，描述：").append(description);
                }
            } else {
                sb.append("，题目数：未知");
            }

            sb.append("\n");
        }

        log.info("[量表工具] 查询完成，共 {} 个可用量表", scales.size());
        return sb.toString();
    }

    @Tool(name = CREATE_SCALE_PENDING_ACTION,
            description = "为用户创建量表测评任务。传入模型选择的量表ID和推荐理由，系统会生成待处理记录并通过WebSocket实时推送到前端展示量表卡片。返回工具ID供后续查询")
    public String createScalePendingAction(
            ToolContext toolContext,
            @ToolParam(description = "量表ID（从 listAvailableScales 返回的 scaleId 中选择）") Long scaleId,
            @ToolParam(description = "推荐此量表的理由（基于用户描述的症状）") String recommendReason) {

        log.info("[量表工具] 创建量表待处理记录，scaleId={}", scaleId);

        Scale scale = scaleMapper.selectById(scaleId);
        if (scale == null || scale.deleteFlat() || !scale.isEnabled()) {
            log.warn("[量表工具] 量表不可用，scaleId={}", scaleId);
            return "错误：量表不存在或已停用，无法创建测评任务。";
        }

        Long questionCount = 0L;
        if (scale.getCurrentVersionId() != null) {
            questionCount = scaleQuestionMapper.selectCount(
                    new LambdaQueryWrapper<ScaleQuestion>()
                            .eq(ScaleQuestion::getScaleVersionId, scale.getCurrentVersionId())
                            .eq(ScaleQuestion::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        }

        int totalQuestions = questionCount != null ? questionCount.intValue() : 0;

        ScaleActionDataVO actionData = ScaleActionDataVO.builder()
                .scaleId(scale.getId())
                .scaleName(scale.getScaleName())
                .totalQuestions(totalQuestions)
                .build();

        ConversationMetadata conversationMetadata = (ConversationMetadata) toolContext.getContext().get(ConversationMetadata.NAME);
        if (conversationMetadata == null) {
            log.error("[量表工具] 会话元数据不存在，无法创建测评任务。");
            return "错误：会话元数据不存在，请无法创建测评任务，跳过该操作。";
        }
        Long conversationId = conversationMetadata.getConversationId();

        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || conversation.isDeleted()) {
            log.warn("[量表工具] 会话不存在，conversationId={}", conversationId);
            return "错误：会话不存在，请无法创建测评任务。";
        }
        Long userId = conversationMetadata.getUserId();
        Integer currentRound = conversationMetadata.getCurrentRound();

        ConversationPendingAction pendingAction = ConversationPendingAction.builder()
                .conversationId(conversationId)
                .userId(userId)
                .roundNum(currentRound)
                .actionType(ConversationPendingAction.ACTION_TYPE_SCALE)
                .callReason(recommendReason)
                .actionData(JsonUtils.toJsonString(actionData))
                .status(ConversationPendingAction.STATUS_WAITING)
                .build();

        pendingActionMapper.insert(pendingAction);

        conversationWebSocketManager.sendPendingAction(userId, conversationId, pendingAction);
        log.info("[量表工具] 待处理记录已创建，id={}", pendingAction.getId());

        return "已为用户创建量表测评任务。工具ID：" + pendingAction.getId()
                + "，量表名称：" + scale.getScaleName()
                + "，共 " + totalQuestions + " 道题。请告知用户量表卡片正在推送中，";
    }

    @Tool(name = QUERY_TOOL_RESULT,
            description = "根据工具ID查询工具执行结果。当用户提到已完成或想查看之前推荐的量表结果时调用，若结果尚未生成则返回等待状态")
    public String queryToolResult(
            @ToolParam(description = "工具ID（createScalePendingAction 返回的 toolId）") Long toolId) {

        log.info("[量表工具] 查询工具结果，toolId={}", toolId);

        ConversationPendingAction pendingAction = pendingActionMapper.selectById(toolId);
        if (pendingAction == null || pendingAction.getDeleted() == DeleteConstant.DELETE_FLAG_YES) {
            log.warn("[量表工具] 待处理记录不存在，toolId={}", toolId);
            return "错误：该工具记录不存在，请检查工具ID是否正确。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("工具ID：").append(toolId)
                .append("\n调用轮次：").append(pendingAction.getRoundNum())
                .append("\n创建时间：").append(pendingAction.getCreatedTime().toString())
                .append("\n调用理由：").append(pendingAction.getCallReason())
                .append("\n类型：").append(pendingAction.getActionTypeText())
                .append("\n当前状态：").append(pendingAction.getStatusText());

        if (pendingAction.getToolResult() != null) {
            sb.append("\n测评结果：").append(pendingAction.getToolResult());
        }

        log.info("[量表工具] 查询完成，toolId={}，status={}", toolId, pendingAction.getStatus());
        return sb.toString();
    }
}