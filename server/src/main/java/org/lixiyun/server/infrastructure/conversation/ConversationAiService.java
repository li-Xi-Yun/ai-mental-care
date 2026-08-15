package org.lixiyun.server.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.HistoryCompressionBO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.server.ai.node.conversation.EmotionRecognitionNode;
import org.lixiyun.server.ai.node.conversation.HistoryAnalysisCompressionNode;
import org.lixiyun.server.ai.node.conversation.HistoryMessageCompressionNode;
import org.lixiyun.server.ai.node.diagnosis.graph.DiagnosisGraph;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话AI能力服务（领域服务层）
 * <p>负责所有AI节点调用、语义压缩、情绪分析诊断逻辑</p>
 *
 * @author lixiyun
 * @since 2026-08-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationAiService {

    private static final int MAX_CONTEXT_MESSAGES = 50;

    private final HistoryMessageCompressionNode historyMessageCompressionNode;
    private final HistoryAnalysisCompressionNode historyAnalysisCompressionNode;
    private final EmotionRecognitionNode emotionRecognitionNode;
    private final DiagnosisGraph diagnosisGraph;
    private final ConversationRepository conversationRepository;
    private final ConversationCacheManager conversationCacheManager;

    /**
     * 检查并触发语义压缩
     * <p>当历史消息数量超过阈值（{@value MAX_CONTEXT_MESSAGES}）时，触发语义压缩流程，
     * 否则跳过压缩。若历史消息为空则记录异常日志并跳过。</p>
     *
     * @param conversationId 会话ID
     * @param processContext  会话处理上下文，包含历史消息等数据
     */
    public void checkAndTriggerSemanticCompression(Long conversationId, ConversationProcessContextBO processContext) {
        List<ConversationMemory> conversationHistory = processContext.getConversationHistory();

        if (conversationHistory.isEmpty()) {
            log.debug("历史消息数据格式异常，跳过语义压缩检查，会话ID：{}", conversationId);
            return;
        }

        int messageCount = conversationHistory.size();

        if (messageCount > MAX_CONTEXT_MESSAGES) {
            log.info("历史消息数量（{}）超过阈值（{}），需要语义压缩，会话ID：{}", messageCount, MAX_CONTEXT_MESSAGES, conversationId);
            semanticCompression(conversationId, processContext);
        } else {
            log.debug("历史消息数量（{}）未超过阈值（{}），无需压缩，会话ID：{}", messageCount, MAX_CONTEXT_MESSAGES, conversationId);
        }
    }

    /**
     * 执行语义压缩
     * <p>分别调用历史消息压缩节点和历史分析压缩节点，生成压缩摘要后更新到数据库，
     * 并刷新缓存中的会话元数据。</p>
     *
     * @param conversationId 会话ID
     * @param processContext  会话处理上下文，包含会话信息、历史消息和情绪分析列表
     */
    public void semanticCompression(Long conversationId, ConversationProcessContextBO processContext) {
        log.info("语义压缩功能，会话ID：{}", conversationId);
        List<ConversationMemory> historyMessages = processContext.getConversationHistory();
        Conversation conversation = processContext.getConversation();
        List<EmotionAnalysis> emotionAnalyses = processContext.getEmotionAnalyses();

        HistoryCompressionBO historyCompressionBO = HistoryCompressionBO.builder()
                .conversation(conversation)
                .historyMessages(historyMessages)
                .emotionAnalyses(emotionAnalyses)
                .build();

        String historyMessageCompression = historyMessageCompressionNode.apply(historyCompressionBO);

        String historyAnalysisCompression = historyAnalysisCompressionNode.apply(historyCompressionBO);

        conversationRepository.updateConversationSummary(
                conversationId,
                historyMessageCompression,
                historyAnalysisCompression,
                conversation.getCurrentRound()
        );

        Conversation updatedConversation = conversationRepository.getConversationById(conversationId);
        conversationCacheManager.updateCacheMetadata(conversationId, updatedConversation);
    }

    /**
     * 执行情绪分析与心理诊断
     * <p>依次调用情绪识别节点进行情绪分析，然后执行诊断图流程完成心理诊断。</p>
     *
     * @param conversationId 会话ID
     * @param processContext  会话处理上下文，会在方法内部被修改（写入情绪分析和诊断结果）
     */
    public void analysisAndDiagnosis(Long conversationId, ConversationProcessContextBO processContext) {
        log.info("分析与诊断功能，会话ID：{}", conversationId);
        emotionRecognitionNode.apply(processContext);
        diagnosisGraph.executeGraph(processContext);
    }
}