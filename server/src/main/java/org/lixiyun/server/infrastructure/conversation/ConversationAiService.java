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
        log.debug("[语义压缩] 检查是否需要语义压缩，会话ID：{}", conversationId);
        List<ConversationMemory> conversationHistory = processContext.getConversationHistory();

        if (conversationHistory.isEmpty()) {
            log.debug("历史消息数据格式异常，跳过语义压缩检查，会话ID：{}", conversationId);
            return;
        }

        int messageCount = conversationHistory.size();
        log.debug("[语义压缩] 历史消息数量：{}，阈值：{}，会话ID：{}", messageCount, MAX_CONTEXT_MESSAGES, conversationId);

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
        log.debug("[语义压缩] 构建压缩BO，历史消息数：{}，情绪分析数：{}，当前轮次：{}，会话ID：{}",
                historyMessages.size(), emotionAnalyses != null ? emotionAnalyses.size() : 0, conversation.getCurrentRound(), conversationId);

        HistoryCompressionBO historyCompressionBO = HistoryCompressionBO.builder()
                .conversation(conversation)
                .historyMessages(historyMessages)
                .emotionAnalyses(emotionAnalyses)
                .build();

        long startTime = System.currentTimeMillis();
        String historyMessageCompression = historyMessageCompressionNode.apply(historyCompressionBO);
        log.debug("[语义压缩] 历史消息压缩完成，耗时：{}ms，摘要长度：{}，会话ID：{}",
                System.currentTimeMillis() - startTime, historyMessageCompression != null ? historyMessageCompression.length() : 0, conversationId);

        long startTime2 = System.currentTimeMillis();
        String historyAnalysisCompression = historyAnalysisCompressionNode.apply(historyCompressionBO);
        log.debug("[语义压缩] 历史分析压缩完成，耗时：{}ms，摘要长度：{}，会话ID：{}",
                System.currentTimeMillis() - startTime2, historyAnalysisCompression != null ? historyAnalysisCompression.length() : 0, conversationId);

        log.debug("[语义压缩] 更新数据库压缩摘要，压缩轮次：{}，会话ID：{}", conversation.getCurrentRound(), conversationId);
        conversationRepository.updateConversationSummary(
                conversationId,
                historyMessageCompression,
                historyAnalysisCompression,
                conversation.getCurrentRound()
        );

        Conversation updatedConversation = conversationRepository.getConversationById(conversationId);
        log.debug("[语义压缩] 查询更新后的会话实体，当前轮次：{}，会话ID：{}", updatedConversation != null ? updatedConversation.getCurrentRound() : "null", conversationId);
        conversationCacheManager.updateCacheMetadata(conversationId, updatedConversation);
        log.debug("[语义压缩] 缓存元数据更新完成，会话ID：{}", conversationId);
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
        log.debug("[分析诊断] 开始情绪识别，会话ID：{}", conversationId);
        long startTime = System.currentTimeMillis();
        emotionRecognitionNode.apply(processContext);
        log.debug("[分析诊断] 情绪识别完成，耗时：{}ms，会话ID：{}", System.currentTimeMillis() - startTime, conversationId);

        log.debug("[分析诊断] 开始执行诊断图，会话ID：{}", conversationId);
        long startTime2 = System.currentTimeMillis();
        diagnosisGraph.executeGraph(processContext);
        log.debug("[分析诊断] 诊断图执行完成，耗时：{}ms，会话ID：{}", System.currentTimeMillis() - startTime2, conversationId);
    }
}