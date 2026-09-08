package org.lixiyun.server.infrastructure.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.mapper.EmotionAnalysisMapper;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话仓储层
 * <p>全量DB原子操作收口，方便后续分库分表、读写分离改造</p>
 *
 * @author lixiyun
 * @since 2026-08-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationRepository {

    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final EmotionAnalysisMapper emotionAnalysisMapper;
    private final EmotionDiagnosisMapper emotionDiagnosisMapper;

    /**
     * 根据ID查询会话基础信息
     *
     * @param conversationId 会话ID
     * @return 会话实体，不存在时返回{@code null}
     */
    public Conversation getConversationById(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        log.debug("[仓储] 根据ID查询会话，会话ID：{}，结果：{}", conversationId, conversation != null ? "存在" : "空");
        return conversation;
    }

    /**
     * 根据ID查询会话基础信息（不存在时抛出业务异常）
     *
     * @param conversationId 会话ID
     * @return 会话实体
     * @throws BusinessException 会话不存在时抛出
     */
    private Conversation getConversationInfo(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            log.warn("会话基础信息查询为空，会话ID：{}", conversationId);
            throw new BusinessException(AIChatExceptionEnum.CONVERSATION_NOT_FOUND);
        }
        log.debug("会话基础信息查询成功，会话ID：{}，当前轮次：{}", conversation.getId(), conversation.getCurrentRound());
        return conversation;
    }

    /**
     * 查询指定会话的未处理消息
     * <p>按轮次和创建时间升序排列，仅查询状态为"未处理"的消息。</p>
     *
     * @param conversationId 会话ID
     * @return 未处理消息列表，无数据时返回空列表
     */
    public List<ConversationMemory> queryUnprocessedMessages(Long conversationId) {
        List<ConversationMemory> messages = conversationMemoryMapper.selectList(
                new LambdaQueryWrapper<ConversationMemory>()
                        .eq(ConversationMemory::getConversationId, conversationId)
                        .eq(ConversationMemory::getState, ConversationCacheConstant.PROCESS_FLAG_NOT_PROCESSED)
                        .orderByAsc(ConversationMemory::getRoundNum, ConversationMemory::getCreatedTime)
        );

        log.debug("查询到未处理消息数量：{}，会话ID：{}", messages.size(), conversationId);
        return messages;
    }

    /**
     * 查询已处理的历史消息
     * <p>从语义压缩轮次开始查询，仅查询状态为"已处理"的消息，按轮次升序排列。
     * 若语义压缩轮次为空或无效，则从第1轮开始查询。</p>
     *
     * @param conversation   会话实体（用于获取语义压缩轮次）
     * @param conversationId 会话ID
     * @return 已处理消息列表
     */
    private List<ConversationMemory> queryProcessedMessages(Conversation conversation, Long conversationId) {
        Integer contextSummaryRound = conversation.getContextSummaryRound();

        if (contextSummaryRound == null || contextSummaryRound <= 0) {
            log.debug("语义压缩轮次为空或无效，查询所有已处理消息，会话ID：{}", conversationId);
            contextSummaryRound = 1;
        }

        long startRound = contextSummaryRound;

        List<ConversationMemory> messages = conversationMemoryMapper.selectList(
                new LambdaQueryWrapper<ConversationMemory>()
                        .eq(ConversationMemory::getConversationId, conversationId)
                        .eq(ConversationMemory::getState, ConversationMemory.STATE_PROCESSED)
                        .ge(ConversationMemory::getRoundNum, startRound)
                        .orderByAsc(ConversationMemory::getRoundNum)
        );

        log.debug("查询到已处理消息数量：{}（语义压缩轮次：{}，起始轮次：{}），会话ID：{}",
                messages.size(), conversation.getContextSummaryRound(), startRound, conversationId);
        return messages;
    }

    /**
     * 查询情绪分析记录
     * <p>从语义压缩轮次开始查询，按创建时间降序排列。
     * 若语义压缩轮次为空或无效，则从第1轮开始查询。</p>
     *
     * @param conversation   会话实体（用于获取语义压缩轮次）
     * @param conversationId 会话ID
     * @return 情绪分析记录列表
     */
    private List<EmotionAnalysis> queryEmotionAnalyses(Conversation conversation, Long conversationId) {
        Integer contextSummaryRound = conversation.getContextSummaryRound();

        if (contextSummaryRound == null || contextSummaryRound <= 0) {
            log.debug("语义压缩轮次为空或无效，查询所有情绪分析记录，会话ID：{}", conversationId);
            contextSummaryRound = 1;
        }

        long startRound = contextSummaryRound;

        List<EmotionAnalysis> analyses = emotionAnalysisMapper.selectList(
                new LambdaQueryWrapper<EmotionAnalysis>()
                        .eq(EmotionAnalysis::getConversationId, conversationId)
                        .ge(EmotionAnalysis::getRoundNum, startRound)
                        .orderByDesc(EmotionAnalysis::getCreatedTime)
        );

        log.debug("查询到情绪分析数量：{}（语义压缩轮次：{}，当前轮次：{}），会话ID：{}",
                analyses.size(), contextSummaryRound, conversation.getCurrentRound(), conversationId);
        return analyses;
    }

    /**
     * 查询最新的心理诊断记录
     *
     * @param conversationId 会话ID
     * @return 最新的心理诊断实体，不存在时返回{@code null}
     */
    private EmotionDiagnosis queryLatestDiagnosis(Long conversationId) {
        EmotionDiagnosis diagnosis = emotionDiagnosisMapper.selectOne(
                new LambdaQueryWrapper<EmotionDiagnosis>()
                        .eq(EmotionDiagnosis::getConversationId, conversationId)
                        .orderByDesc(EmotionDiagnosis::getCreatedTime)
                        .last("LIMIT 1")
        );

        if (diagnosis != null) {
            log.debug("查询到最新心理诊断，时间：{}", diagnosis.getCreatedTime());
        } else {
            log.debug("未找到心理诊断记录，会话ID：{}", conversationId);
        }

        return diagnosis;
    }

    /**
     * 查询会话完整数据
     * <p>聚合查询会话基础信息、已处理消息、情绪分析记录和最新诊断，
     * 组装为包含所有缓存字段的Map，用于首次加载缓存。</p>
     *
     * @param conversationId 会话ID
     * @return 会话完整数据Map，包含元数据、历史消息、情绪分析、诊断等字段
     */
    public Map<String, Object> queryConversationFullData(Long conversationId) {
        log.debug("从数据库查询会话完整数据，会话ID：{}", conversationId);

        Conversation conversation = getConversationInfo(conversationId);
        List<ConversationMemory> processedMessages = queryProcessedMessages(conversation, conversationId);
        List<EmotionAnalysis> emotionAnalyses = queryEmotionAnalyses(conversation, conversationId);
        EmotionDiagnosis latestDiagnosis = queryLatestDiagnosis(conversationId);

        Map<String, Object> conversationData = new HashMap<>();
        conversationData.put(ConversationCacheConstant.HASH_FIELD_METADATA, conversation);
        conversationData.put(ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES, processedMessages);
        conversationData.put(ConversationCacheConstant.HASH_FIELD_EMOTION_ANALYSIS_LIST, emotionAnalyses);
        conversationData.put(ConversationCacheConstant.HASH_FIELD_PSYCHOLOGICAL_DIAGNOSIS, latestDiagnosis);
        conversationData.put(ConversationCacheConstant.HASH_FIELD_PRIVATE_MAPPING_TABLE, conversation.getSessionMapping());
        conversationData.put(ConversationCacheConstant.HASH_FIELD_CONVERSATION_TYPE, conversation.getChatMode());
        conversationData.put(ConversationCacheConstant.HASH_FIELD_INTERRUPT_FLAG, ConversationCacheConstant.INTERRUPT_FLAG_INACTIVE);
        conversationData.put(ConversationCacheConstant.HASH_FIELD_PROCESS_FLAG, ConversationCacheConstant.PROCESS_FLAG_NOT_PROCESSED);

        log.debug("会话完整数据查询完成，会话ID：{}，消息数：{}，情绪分析数：{}",
                conversationId, processedMessages.size(), emotionAnalyses.size());

        return conversationData;
    }

    /**
     * 更新会话状态（事务性操作）
     * <p>在同一事务中完成：将未处理消息状态更新为已处理、递增会话当前轮次。</p>
     *
     * @param conversationId      会话ID
     * @param unprocessedMessages 本轮处理的未处理消息列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConversationState(Long conversationId, List<ConversationMemory> unprocessedMessages) {
        log.debug("[仓储] 更新会话状态（事务），会话ID：{}，消息数：{}", conversationId, unprocessedMessages.size());
        updateMessagesToProcessedStatus(conversationId, unprocessedMessages);
        incrementConversationRound(conversationId);
        log.debug("[仓储] 会话状态更新完成，会话ID：{}", conversationId);
    }

    /**
     * 将消息状态批量更新为已处理
     *
     * @param conversationId      会话ID
     * @param unprocessedMessages 待更新的消息列表
     */
    public void updateMessagesToProcessedStatus(Long conversationId, List<ConversationMemory> unprocessedMessages) {
        if (unprocessedMessages.isEmpty()) {
            log.debug("[仓储] 未处理消息列表为空，跳过状态更新，会话ID：{}", conversationId);
            return;
        }

        log.debug("[仓储] 批量更新消息状态为已处理，数量：{}，会话ID：{}", unprocessedMessages.size(), conversationId);
        List<ConversationMemory> conversationMemoryList = unprocessedMessages.stream().map(item ->
                ConversationMemory.builder()
                    .id(item.getId())
                    .state(ConversationMemory.STATE_PROCESSED)
                    .build()
        ).toList();
        conversationMemoryMapper.updateById(conversationMemoryList);

        log.debug("更新{}条消息状态为已处理，会话ID：{}", unprocessedMessages.size(), conversationId);
    }

    /**
     * 递增会话当前轮次并更新最后活跃时间
     *
     * @param conversationId 会话ID
     */
    public void incrementConversationRound(Long conversationId) {
        log.debug("[仓储] 递增会话轮次，会话ID：{}", conversationId);
        conversationMapper.update(null,
                new LambdaUpdateWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .setSql("current_round = current_round + 1")
                        .set(Conversation::getLastActiveTime, LocalDateTime.now())
        );

        log.debug("递增会话轮次，会话ID：{}", conversationId);
    }

    /**
     * 更新会话语义压缩摘要
     * <p>将历史消息压缩摘要、分析压缩摘要和压缩轮次更新到数据库。</p>
     *
     * @param conversationId       会话ID
     * @param contextSummary       历史消息压缩摘要
     * @param analysisContextSummary 分析压缩摘要
     * @param contextSummaryRound  压缩时的轮次
     */
    public void updateConversationSummary(Long conversationId, String contextSummary, String analysisContextSummary, Integer contextSummaryRound) {
        log.debug("[仓储] 更新语义压缩摘要，会话ID：{}，压缩轮次：{}，消息摘要长度：{}，分析摘要长度：{}",
                conversationId, contextSummaryRound,
                contextSummary != null ? contextSummary.length() : 0,
                analysisContextSummary != null ? analysisContextSummary.length() : 0);
        conversationMapper.updateById(Conversation.builder()
                .id(conversationId)
                .contextSummary(contextSummary)
                .analysisContextSummary(analysisContextSummary)
                .contextSummaryRound(contextSummaryRound)
                .build());

        log.debug("更新会话语义压缩摘要，会话ID：{}，压缩轮次：{}", conversationId, contextSummaryRound);
    }

    /**
     * 更新会话名称
     *
     * @param conversationId 会话ID
     * @param name           新的会话名称
     */
    public void updateConversationName(Long conversationId, String name) {
        log.debug("[仓储] 更新会话名称，会话ID：{}，新名称：{}", conversationId, name);
        conversationMapper.updateById(Conversation.builder()
                .id(conversationId)
                .name(name)
                .build());

        log.debug("更新会话名称，会话ID：{}，新名称：{}", conversationId, name);
    }
}