package org.lixiyun.server.infrastructure.conversation.processor;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.enums.OutputPipelineExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.DateUtils;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.tool.ScaleToolResult;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.conversation.ConversationPendingAction;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.ai.model.conversation.ChatMessageProcessorModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputContext;
import org.lixiyun.server.mapper.ConversationPendingActionMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI对话消息处理器
 * <p>主线程执行流程：
 * <ol>
 *     <li>接收参数：临时消息数据、会话历史上下文、历史情绪分析结果、历史心理诊断结果</li>
 *     <li>调用LLM生成文本回答</li>
 *     <li>将LLM输出文本送入TTS模块，执行文本转语音</li>
 *     <li>实时检测缓存内是否存在中断标志：
 *         <ul>
 *             <li>若存在中断标志：执行停止发送消息逻辑，直接跳转至DB数据存储会话上下文步骤</li>
 *             <li>若不存在中断标志：通过WebSocket以流式方式推送语音消息</li>
 *         </ul>
 *     </li>
 *     <li>持久化会话上下文数据至数据库</li>
 *     <li>更新缓存中的历史上下文数据</li>
 *     <li>清除缓存里的中断标识，流程结束</li>
 * </ol>
 * 整个流程统一兜底处理：报错捕获、重试机制，保障数据一致性。
 * </p>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageProcessor {

    public static final String NODE_NAME = "chatMessageProcessor";

    private final ChatMessageProcessorModel chatMessageProcessorModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;
    private final ConversationPendingActionMapper pendingActionMapper;

    public Flux<NodeOutput> processMessage(ConversationProcessContextBO context, OutputContext outputContext) {
        if (context == null || context.getTemporaryMessages() == null || context.getTemporaryMessages().isEmpty()) {
            log.error("[AI对话处理器] 临时消息为空，跳过处理");
            throw new BusinessException(AIChatExceptionEnum.TEMPORARY_MESSAGES_EMPTY);
        }

        // 输出模式缺省时按纯文本处理（兜底，保证时间轮老路径可运行）
        if (outputContext == null || outputContext.getOutputTypes() == null || outputContext.getOutputTypes().isEmpty()) {
            log.error("[AI对话处理器] outputContext 为空或未携带输出模式");
            throw new BusinessException(OutputPipelineExceptionEnum.PIPELINE_NOT_FOUND);
        }

        Long conversationId = context.getConversation().getId();
        int currentRound = context.getConversation().getCurrentRound();
        Long userId = context.getConversation().getUserId();
        log.info("[AI对话处理器] 开始语音消息处理，会话ID：{}，临时消息数：{}", conversationId, context.getTemporaryMessages().size());
        log.debug("[AI对话处理器] 会话ID：{}，用户ID：{}，当前轮次：{}，历史消息数：{}，情绪分析数：{}",
                conversationId, userId, currentRound,
                context.getConversationHistory() != null ? context.getConversationHistory().size() : 0,
                context.getEmotionAnalyses() != null ? context.getEmotionAnalyses().size() : 0);

        try {
            // 加载所有待对话注入的记录
            List<ConversationPendingAction> pendingActions = loadPendingInjectActions(context.getConversation().getId());

            String prompt = buildPrompt(context, pendingActions);
            log.debug("[AI对话处理器] Prompt构建完成，长度：{}，会话ID：{}", prompt.length(), conversationId);

            log.debug("[AI对话处理器] AgentStreamProcessor构建完成，会话ID：{}", conversationId);

            AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfigWithLoad(NODE_NAME);
            ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
            log.debug("[AI对话处理器] ChatModel获取完成，模型类型：{}，会话ID：{}", aiNodeConfig.getModelType(), conversationId);

            ConversationMetadata metadata = ConversationMetadata.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .currentRound(currentRound)
                    .build();
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .addMetadata(ConversationMetadata.NAME, metadata)
                    .build();
            Flux<NodeOutput> stream = chatMessageProcessorModel.stream(chatModel, prompt, aiNodeConfig, runnableConfig);

            // LLM 成功生成回复后，将本会话已注入的待对话交互（status=1）批量置为已响应（status=2）
            List<Long> pendingActionIds = pendingActions.stream().map(ConversationPendingAction::getId).toList();
            markPendingInjectResponded(conversationId, pendingActionIds);
            log.debug("[AI对话处理器] 已响应的待对话交互状态回写完成，会话ID：{}，待对话交互ID：{}", conversationId, pendingActionIds);

            log.info("AI对话语音处理器-语音消息处理完成，会话ID：{}", conversationId);
            return stream;
        } catch (BusinessException e) {
            log.error("AI对话语音处理器-语音消息处理业务异常，会话ID：{}，错误代码：{}，错误信息：{}", conversationId, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI对话语音处理器-语音消息处理失败，会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
            throw new BusinessException(AIChatExceptionEnum.MAIN_THREAD_EXECUTION_FAILED);
        }
    }

    /**
     * 构建LLM提示词，拼接会话历史、情绪分析、心理诊断及当前临时消息
     *
     * @param context 会话处理上下文
     * @return 拼接后的提示词字符串
     */
    private String buildPrompt(ConversationProcessContextBO context, List<ConversationPendingAction> pendingActions) {
        log.debug("[AI对话文本处理器] 开始构建Prompt");
        StringBuilder sb = new StringBuilder();

        if (context.getConversation() != null) {
            sb.append("\n【会话基本信息】\n");
            sb.append("会话名称：").append(context.getConversation().getName()).append("\n");
            sb.append("会话ID：").append(context.getConversation().getId()).append("\n");
            log.debug("[AI对话文本处理器] 拼接会话基本信息");
       }

        if (context.getConversation() != null && context.getConversation().getContextSummary() != null && !context.getConversation().getContextSummary().isEmpty()) {
            sb.append("\n【会话上下文压缩文本】\n");
            sb.append(context.getConversation().getContextSummary());
            log.debug("[AI对话文本处理器] 拼接会话上下文压缩文本");
        }

        if (context.getConversation() != null && context.getConversation().getAnalysisContextSummary() != null && !context.getConversation().getAnalysisContextSummary().isEmpty()) {
            sb.append("\n【历史情绪分析压缩文本】\n");
            sb.append(context.getConversation().getAnalysisContextSummary());
            log.debug("[AI对话文本处理器] 拼接历史情绪分析压缩文本");
        }

        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            sb.append("\n【会话历史上下文】\n");
            context.getConversationHistory().forEach(msg ->
                    sb.append(MessageType.getDescription(msg.getType()))
                            .append("（").append(DateUtils.toRelativeTime(msg.getCreatedTime())).append("）：")
                            .append(msg.getContent()).append("\n")
            );
            log.debug("[AI对话文本处理器] 拼接历史消息，数量：{}", context.getConversationHistory().size());
        }

        if (context.getEmotionAnalyses() != null && !context.getEmotionAnalyses().isEmpty()) {
            sb.append("\n【历史情绪分析结果】\n");
            context.getEmotionAnalyses().forEach(analysis ->
                    sb.append("（").append(DateUtils.toRelativeTime(analysis.getCreatedTime())).append("）")
                            .append(analysis.toPromptString()).append("\n")
            );
            log.debug("[AI对话文本处理器] 拼接情绪分析，数量：{}", context.getEmotionAnalyses().size());
        }

        if (context.getEmotionDiagnosis() != null) {
            sb.append("\n【最近一次心理评估结果】\n");
            sb.append("（").append(DateUtils.toRelativeTime(context.getEmotionDiagnosis().getCreatedTime())).append("）")
                    .append(context.getEmotionDiagnosis().toPromptString());
            log.debug("[AI对话文本处理器] 拼接心理评估结果");
        }

        // 拼接待对话注入的工具结果（status=1 的人工交互数据）
        if (pendingActions != null && !pendingActions.isEmpty()) {
            sb.append("\n【待对话注入的工具结果】\n");
                for (ConversationPendingAction pendingAction : pendingActions) {
                    appendPendingActionPrompt(sb, pendingAction);
                }
            log.debug("[AI对话文本处理器] 拼接待对话注入工具结果，数量：{}", pendingActions.size());
        }

        sb.append("\n【本次用户发送的消息为】\n");
        context.getTemporaryMessages().forEach(msg ->
                sb.append("（").append(DateUtils.toRelativeTime(msg.getCreatedTime())).append("）").append(msg.getContent()).append("\n")
        );
        log.debug("[AI对话文本处理器] 拼接临时消息，数量：{}", context.getTemporaryMessages().size());

        return sb.toString();
    }

    /**
     * 查询会话中所有"待对话注入"（status=1）的待处理交互数据
     *
     * @param conversationId 会话ID
     * @return 待对话注入的待处理交互列表
     */
    private List<ConversationPendingAction> loadPendingInjectActions(Long conversationId) {
        log.debug("[AI对话处理器] 查询待对话注入工具结果，会话ID：{}", conversationId);
        return pendingActionMapper.selectList(new LambdaQueryWrapper<ConversationPendingAction>()
                .eq(ConversationPendingAction::getConversationId, conversationId)
                .eq(ConversationPendingAction::getStatus, ConversationPendingAction.STATUS_PENDING_INJECT)
                .eq(ConversationPendingAction::getDeleted, DeleteConstant.DELETE_FLAG_NO));
    }

    /**
     * LLM 成功生成回复后，将会话中所有"待对话注入"（status=1）的记录批量置为"已响应"（status=2）
     *
     * @param conversationId 会话ID
     */
    private void markPendingInjectResponded(Long conversationId, List<Long> pendingActionIds) {
        // 无待对话注入记录是常态（普通聊天回合）：必须跳过，否则 MyBatis-Plus 会生成非法的 IN ()
        if (pendingActionIds == null || pendingActionIds.isEmpty()) {
            log.debug("[AI对话处理器] 无待对话注入记录，跳过状态回写，会话ID：{}", conversationId);
            return;
        }
        log.debug("[AI对话处理器] 批量更新待对话注入状态为已响应，会话ID：{}，待对话交互ID：{}", conversationId, pendingActionIds);
        pendingActionMapper.update(null, new LambdaUpdateWrapper<ConversationPendingAction>()
                .eq(ConversationPendingAction::getConversationId, conversationId)
                .in(ConversationPendingAction::getId, pendingActionIds)
                .eq(ConversationPendingAction::getStatus, ConversationPendingAction.STATUS_PENDING_INJECT)
                .set(ConversationPendingAction::getStatus, ConversationPendingAction.STATUS_RESPONDED)
                .set(ConversationPendingAction::getCompletedTime, java.time.LocalDateTime.now()));
    }

    /**
     * 将单条待处理交互数据按 action_type 拼装为提示词文本
     * <p>当前支持量表测评（action_type=1）：从 tool_result 中提取分析文本注入。</p>
     *
     * @param sb            提示词构建器
     * @param pendingAction 待处理交互记录
     */
    private void appendPendingActionPrompt(StringBuilder sb, ConversationPendingAction pendingAction) {
        if (pendingAction.isActionTypeScale()) {
            ScaleToolResult toolResult = JsonUtils.parseObject(pendingAction.getToolResult(), ScaleToolResult.class);
            String analysisText = toolResult == null ? null : toolResult.getAnalysisText();
            if (analysisText != null && !analysisText.isEmpty()) {
                sb.append("\n--- 量表测评分析 ---\n");
                sb.append("工具ID：").append(pendingAction.getId()).append("\n");
                sb.append("分析文本：").append(analysisText).append("\n");
                log.debug("[AI对话处理器] 拼接量表测评分析文本，工具ID：{}", pendingAction.getId());
            }
        } else {
            log.debug("[AI对话处理器] 暂不支持的工具交互类型，工具ID：{}，actionType：{}",
                    pendingAction.getId(), pendingAction.getActionType());
        }
    }

}