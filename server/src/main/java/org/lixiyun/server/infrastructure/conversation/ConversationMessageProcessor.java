package org.lixiyun.server.infrastructure.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.HistoryCompressionBO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.server.ai.node.EmotionRecognitionNode;
import org.lixiyun.server.ai.node.HistoryAnalysisCompressionNode;
import org.lixiyun.server.ai.node.HistoryMessageCompressionNode;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.conversation.processor.MessageProcessor;
import org.lixiyun.server.infrastructure.conversation.processor.ProcessorHolder;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.mapper.EmotionAnalysisMapper;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 会话消息处理器
 * <p>负责会话缓存数据的加载、存储和管理</p>
 *
 * @author lixiyun
 * @since 2026-07-14 17:36
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationMessageProcessor {

    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final EmotionAnalysisMapper emotionAnalysisMapper;
    private final EmotionDiagnosisMapper emotionDiagnosisMapper;
    private final ProcessorHolder processorHolder;
    private final HistoryMessageCompressionNode historyMessageCompressionNode;
    private final HistoryAnalysisCompressionNode historyAnalysisCompressionNode;
    private final EmotionRecognitionNode emotionRecognitionNode;

    private static final int MAX_CONTEXT_MESSAGES = 50;

    /**
     * 会话消息处理
     * <p>根据图片流程实现的完整消息处理逻辑：</p>
     * <ol>
     *     <li>接收原子会话令牌，设置模型处理标识</li>
     *     <li>会话缓存加载</li>
     *     <li>DB获取临时消息数据（状态：未处理）</li>
     *     <li>判断消息是否为空</li>
     *     <li>获取对应的会话缓存数据</li>
     *     <li>异步并行：分析与诊断、获取执行器、判断是否需要语义压缩</li>
     *     <li>主线程执行（暂不实现）</li>
     *     <li>DB更新临时消息轮次状态为已处理</li>
     *     <li>DB更新会话最新时间与当前轮次</li>
     *     <li>保存会话元数据/数据信息时间与轮次</li>
     *     <li>删除缓存模型处理标识</li>
     * </ol>
     *
     * @param conversationId 会话ID
     */
    public void processConversationMessage(Long conversationId) {
        log.info("开始会话消息处理，会话ID：{}", conversationId);

        try {
            // 获取处理令牌
            boolean tokenAcquired = acquireProcessingToken(conversationId);
            if (!tokenAcquired) {
                log.warn("获取处理令牌失败，重新加入ZSet队列，会话ID：{}", conversationId);
                readdToZSetQueue(conversationId);
                return;
            }

            log.debug("成功获取处理令牌，开始处理，会话ID：{}", conversationId);

            // 加载会话缓存
            boolean cacheLoaded = loadConversationCache(conversationId);
            if (!cacheLoaded) {
                log.warn("会话缓存加载失败，清理处理标识并结束，会话ID：{}", conversationId);
                // 清理处理标识
                clearProcessingFlag(conversationId);
                return;
            }

            // 查询未处理的临时消息
            List<ConversationMemory> unprocessedMessages = queryUnprocessedMessages(conversationId);
            if (unprocessedMessages.isEmpty()) {
                log.info("没有待处理的临时消息，清理处理标识并结束，会话ID：{}", conversationId);
                clearProcessingFlag(conversationId);
                return;
            }

            // 获取缓存会话上下文数据
            Map<String, Object> contextData = getConversationContextData(conversationId);

            // 获取会话类型
            String conversationType = getConversationType(contextData, conversationId);

            // 获取执行器实例
            MessageProcessor executorInstance = processorHolder.getProcessor(conversationType);

            // 检查是否需要语义压缩
            checkAndTriggerSemanticCompression(contextData, conversationId);

            // 构建处理上下文
            ConversationProcessContextBO processContext = buildProcessContext(conversationId, contextData, unprocessedMessages);

            // todo 分析与诊断
            analysisAndDiagnosis(conversationId, processContext);

            // 主线程执行
            executeMainThread(conversationId, processContext, executorInstance);

            // 更新临时消息轮次状态为已处理
            updateMessagesToProcessedStatus(conversationId, unprocessedMessages);

            // 更新会话最新时间与当前轮次
            updateConversationInfo(conversationId);

            // 保存会话元数据/数据信息时间与轮次
            saveMetadataWithTimestampAndRound(conversationId, contextData);

            // 清理处理标识
            clearProcessingFlag(conversationId);

            log.info("会话消息处理完成，会话ID：{}，处理消息数：{}", conversationId, unprocessedMessages.size());
        } catch (Exception e) {
            log.error("会话消息处理异常，会话ID：{}", conversationId, e);
            // 清理处理标识
            clearProcessingFlag(conversationId);
            throw new RuntimeException("会话消息处理失败", e);
        }
    }

    /**
     * 获取原子会话令牌并设置模型处理标识
     * <p>使用RedisUtils的原子性CAS (Compare-And-Swap) 操作保证并发安全</p>
     *
     * @param conversationId 会话ID
     * @return 是否成功获取令牌
     */
    private boolean acquireProcessingToken(Long conversationId) {
        String cacheKey = buildConversationCacheKey(conversationId);
        String processFlagField = ConversationCacheConstant.HASH_FIELD_PROCESS_FLAG;
        String notProcessedValue = String.valueOf(ConversationCacheConstant.PROCESS_FLAG_NOT_PROCESSED);
        String processingValue = String.valueOf(ConversationCacheConstant.PROCESS_FLAG_PROCESSING);

        try {
            Object currentFlag = RedisUtils.getCacheMapValue(cacheKey, processFlagField);

            Object expectValue = (currentFlag == null || notProcessedValue.equals(currentFlag.toString()))
                    ? currentFlag : null;

            if (expectValue != null || currentFlag == null) {
                boolean success = RedisUtils.compareAndSwapMapValue(
                        cacheKey,
                        processFlagField,
                        expectValue,
                        processingValue
                );

                if (success) {
                    log.debug("成功获取处理令牌并设置为处理中，会话ID：{}", conversationId);
                    return true;
                }
            }

            log.debug("会话正在处理中或已处理，跳过，当前标识：{}，会话ID：{}", currentFlag, conversationId);
            return false;
        } catch (Exception e) {
            log.error("获取处理令牌异常，会话ID：{}", conversationId, e);
            return false;
        }
    }

    /**
     * 带重试机制的获取处理令牌
     * <p>在acquireProcessingToken基础上增加重试机制，适用于需要等待锁释放的场景</p>
     *
     * @param conversationId 会话ID
     * @return 是否成功获取令牌
     */
    private boolean acquireProcessingTokenWithRetry(Long conversationId) {
        final int MAX_RETRY = 50;
        final long RETRY_INTERVAL_MS = 100;

        for (int i = 0; i < MAX_RETRY; i++) {
            boolean acquired = acquireProcessingToken(conversationId);
            if (acquired) {
                log.debug("重试第{}次成功获取处理令牌，会话ID：{}", i + 1, conversationId);
                return true;
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("获取处理令牌重试被中断，会话ID：{}", conversationId, e);
                return false;
            }
        }

        log.warn("获取处理令牌重试耗尽，会话ID：{}", conversationId);
        return false;
    }

    /**
     * 重新将会话加入ZSet队列（获取令牌失败时调用）
     *
     * @param conversationId 会话ID
     */
    private void readdToZSetQueue(Long conversationId) {
        try {
            String zSetKey = ConversationCacheConstant.CONVERSATION_MESSAGE_ZSET_KEY_PREFIX;
            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(ConversationCacheConstant.MESSAGE_ZSET_EXPIRE_SECONDS);

            RedisUtils.addToScoredSortedSet(zSetKey, expireTime, String.valueOf(conversationId));
            RedisUtils.expire(zSetKey, ConversationCacheConstant.MESSAGE_ZSET_EXPIRE_SECONDS, TimeUnit.SECONDS);

            log.info("重新加入ZSet队列成功，Key：{}，延迟时间：60秒，会话ID：{}", zSetKey, conversationId);
        } catch (Exception e) {
            log.error("重新加入ZSet队列失败，会话ID：{}", conversationId, e);
        }
    }

    /**
     * 清理模型处理标识
     *
     * @param conversationId 会话ID
     */
    private void clearProcessingFlag(Long conversationId) {
        try {
            String cacheKey = buildConversationCacheKey(conversationId);
            RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_PROCESS_FLAG, ConversationCacheConstant.PROCESS_FLAG_NOT_PROCESSED);
            log.debug("清理模型处理标识成功，会话ID：{}", conversationId);
        } catch (Exception e) {
            log.error("清理处理标识异常，会话ID：{}", conversationId, e);
        }
    }

    /**
     * 查询未处理的临时消息（状态：未处理）
     *
     * @param conversationId 会话ID
     * @return 未处理的对话记忆列表
     */
    private List<ConversationMemory> queryUnprocessedMessages(Long conversationId) {
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
     * 获取对应的会话缓存数据
     *
     * @param conversationId 会话ID
     * @return 会话上下文数据Map
     */
    private Map<String, Object> getConversationContextData(Long conversationId) {
        String cacheKey = buildConversationCacheKey(conversationId);
        Map<String, Object> contextData = RedisUtils.getCacheMap(cacheKey);

        if (contextData == null || contextData.isEmpty()) {
            log.warn("会话缓存数据为空，会话ID：{}", conversationId);
            return new HashMap<>();
        }

        log.debug("获取到会话缓存数据，字段数量：{}，会话ID：{}", contextData.size(), conversationId);
        return contextData;
    }

    /**
     * 获取会话类型
     *
     * @param contextData     会话上下文数据
     * @param conversationId  会话ID
     * @return 会话类型字符串
     */
    private String getConversationType(Map<String, Object> contextData, Long conversationId) {
        Object typeObj = contextData.get(ConversationCacheConstant.HASH_FIELD_CONVERSATION_TYPE);

        if (typeObj == null || typeObj.toString().isEmpty()) {
            Conversation conversation = conversationMapper.selectById(conversationId);
            String type = conversation != null ? conversation.getChatMode() : ConversationCacheConstant.CONVERSATION_TYPE_TEXT;

            // 缓存会话类型
            String cacheKey = buildConversationCacheKey(conversationId);
            RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_CONVERSATION_TYPE, type);

            log.debug("从数据库获取会话类型：{}，会话ID：{}", type, conversationId);
            return type;
        }

        log.debug("从缓存获取会话类型：{}，会话ID：{}", typeObj, conversationId);
        return typeObj.toString();
    }

    /**
     * 判断历史上下文数据量是否超过指定数量，触发语义压缩
     *
     * @param contextData     会话上下文数据
     * @param conversationId  会话ID
     */
    @SuppressWarnings("unchecked")
    private void checkAndTriggerSemanticCompression(Map<String, Object> contextData, Long conversationId) {
        Object historyMessagesObj = contextData.get(ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);

        if (!(historyMessagesObj instanceof List<?> historyMessages)) {
            log.debug("历史消息数据格式异常，跳过语义压缩检查，会话ID：{}", conversationId);
            return;
        }

        int messageCount = historyMessages.size();

        if (messageCount > MAX_CONTEXT_MESSAGES) {
            log.info("历史消息数量（{}）超过阈值（{}），需要语义压缩（暂不实现），会话ID：{}", messageCount, MAX_CONTEXT_MESSAGES, conversationId);
            semanticCompression(conversationId, contextData);
        } else {
            log.debug("历史消息数量（{}）未超过阈值（{}），无需压缩，会话ID：{}", messageCount, MAX_CONTEXT_MESSAGES, conversationId);
        }
    }

    /**
     * 主线程执行
     * <p>根据会话类型获取对应的MessageProcessor，构建处理上下文BO，并调用处理器执行完整的消息处理流程</p>
     *
     * @param conversationId   会话ID
     * @param executorInstance 执行器实例（具体的MessageProcessor实现类）
     */
    private void executeMainThread(Long conversationId, ConversationProcessContextBO processContext, MessageProcessor executorInstance) {
        log.info("开始主线程执行，会话ID：{}，执行器：{}", conversationId,
                executorInstance != null ? executorInstance.getClass().getSimpleName() : "null");

        if (executorInstance == null) {
            throw new BusinessException(AIChatExceptionEnum.PROCESSOR_NOT_FOUND);
        }

        try {
            log.info("开始调用消息处理器，会话ID：{}", conversationId);

            long startTime = System.currentTimeMillis();

            executorInstance.processMessage(processContext);

            long endTime = System.currentTimeMillis();

            log.info("主线程执行完成，会话ID：{}，耗时：{}ms", conversationId, endTime - startTime);
        } catch (BusinessException e) {
            log.error("主线程执行业务异常，会话ID：{}，错误代码：{}，错误信息：{}", conversationId, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("主线程执行系统异常，会话ID：{}", conversationId, e);
            throw new BusinessException(AIChatExceptionEnum.MAIN_THREAD_EXECUTION_FAILED);
        }
    }

    /**
     * 构建消息处理上下文业务对象
     * <p>从Redis缓存数据Map中提取各字段数据，组装成 {@link ConversationProcessContextBO} 对象</p>
     *
     * @param conversationId      会话ID
     * @param contextData         Redis缓存中的会话上下文数据
     * @param unprocessedMessages 待处理的用户消息列表
     * @return 消息处理上下文BO
     */
    private ConversationProcessContextBO buildProcessContext(Long conversationId, Map<String, Object> contextData, List<ConversationMemory> unprocessedMessages) {
        log.debug("开始构建处理上下文，会话ID：{}", conversationId);

        Conversation conversation = (Conversation) contextData.get(ConversationCacheConstant.HASH_FIELD_METADATA);

        List<ConversationMemory> conversationHistory = (List<ConversationMemory>) contextData.get(ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);

        List<EmotionAnalysis> emotionAnalyses = (List<EmotionAnalysis>) contextData.get(ConversationCacheConstant.HASH_FIELD_EMOTION_ANALYSIS_LIST);

        EmotionDiagnosis emotionDiagnosis = (EmotionDiagnosis) contextData.get(ConversationCacheConstant.HASH_FIELD_PSYCHOLOGICAL_DIAGNOSIS);

        ConversationProcessContextBO processContextBO = ConversationProcessContextBO.builder()
                .conversation(conversation)
                .temporaryMessages(unprocessedMessages)
                .conversationHistory(conversationHistory)
                .emotionAnalyses(emotionAnalyses)
                .emotionDiagnosis(emotionDiagnosis)
                .build();

        log.debug("处理上下文构建完成，会话ID：{}，临时消息数：{}，情绪分析数：{}",
                conversationId,
                unprocessedMessages != null ? unprocessedMessages.size() : 0,
                emotionAnalyses != null ? emotionAnalyses.size() : 0);

        return processContextBO;
    }

    /**
     * 分析与诊断
     *
     * @param conversationId 会话ID
     */
    private void analysisAndDiagnosis(Long conversationId, ConversationProcessContextBO processContext) {
        log.info("分析与诊断功能，会话ID：{}", conversationId);
        emotionRecognitionNode.apply(processContext);
    }

    /**
     * 语义压缩
     *
     * @param conversationId 会话ID
     * @param contextData    会话上下文数据
     */
    private void semanticCompression(Long conversationId, Map<String, Object> contextData) {
        log.info("语义压缩功能，会话ID：{}", conversationId);
        Object historyMessagesObj = contextData.get(ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);
        Object conversationObj = contextData.get(ConversationCacheConstant.HASH_FIELD_METADATA);
        Object emotionListObj = contextData.get(ConversationCacheConstant.HASH_FIELD_EMOTION_ANALYSIS_LIST);
        List<ConversationMemory> historyMessages = (List<ConversationMemory>) historyMessagesObj;
        Conversation conversation = (Conversation) conversationObj;
        List<EmotionAnalysis> emotionAnalyses = (List<EmotionAnalysis>) emotionListObj;

        HistoryCompressionBO historyCompressionBO = HistoryCompressionBO.builder()
                .conversation(conversation)
                .historyMessages(historyMessages)
                .emotionAnalyses(emotionAnalyses)
                .build();

        // 历缩历史消息
        String historyMessageCompression = historyMessageCompressionNode.apply(historyCompressionBO);

        // 历缩历史情绪分析
        String historyAnalysisCompression = historyAnalysisCompressionNode.apply(historyCompressionBO);

        conversationMapper.updateById(Conversation.builder()
                .id(conversationId)
                .contextSummary(historyMessageCompression)
                .analysisContextSummary(historyAnalysisCompression)
                .contextSummaryRound(conversation.getCurrentRound())
                .build());

        // 更新缓存中的会话元数据（带重试的CAS操作）
        boolean lockAcquired = acquireProcessingTokenWithRetry(conversationId);
        try {
            if (lockAcquired) {
                String cacheKey = buildConversationCacheKey(conversationId);
                RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_METADATA, conversation);
            } else {
                throw new RuntimeException("获取会话缓存更新锁超时，conversationId:" + conversationId);
            }
        } finally {
            if (lockAcquired) {
                clearProcessingFlag(conversationId);
            }
        }

    }

    /**
     * 更新临时消息状态为已处理
     *
     * @param conversationId       会话ID
     * @param unprocessedMessages 未处理的消息列表
     */
    private void updateMessagesToProcessedStatus(Long conversationId, List<ConversationMemory> unprocessedMessages) {
        if (unprocessedMessages.isEmpty()) {
            return;
        }

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
     * 更新会话最新时间与当前轮次
     *
     * @param conversationId 会话ID
     */
    private void updateConversationInfo(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            log.error("会话不存在，无法更新信息，会话ID：{}", conversationId);
            return;
        }

        Integer newCurrentRound = conversation.getCurrentRound() + 1;

        conversationMapper.update(null,
                new LambdaUpdateWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .set(Conversation::getCurrentRound, newCurrentRound)
                        .set(Conversation::getLastActiveTime, java.time.LocalDateTime.now())
        );

        log.debug("更新会话信息，新轮次：{}，会话ID：{}", newCurrentRound, conversationId);
    }

    /**
     * 保存会话元数据/数据信息时间与轮次
     *
     * @param conversationId 会话ID
     * @param contextData    会话上下文数据
     */
    private void saveMetadataWithTimestampAndRound(Long conversationId, Map<String, Object> contextData) {
        try {
            String cacheKey = buildConversationCacheKey(conversationId);

            Conversation conversation = conversationMapper.selectById(conversationId);
            if (conversation != null) {
//                contextData.put(ConversationCacheConstant.HASH_FIELD_METADATA, conversation);
                RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_METADATA, conversation);

                log.debug("保存元数据和时间戳成功，当前轮次：{}，会话ID：{}", conversation.getCurrentRound(), conversationId);
            }
        } catch (Exception e) {
            log.error("保存元数据失败，会话ID：{}", conversationId, e);
        }
    }

    /**
     * 会话缓存加载
     * <ol>
     *     <li>判断会话缓存数据是否存在</li>
     *     <li>DB查询会话完整数据信息（状态：已处理）</li>
     *     <li>消息与分析储备压缩后的轮次</li>
     *     <li>将会话数据缓存到Redis Hash结构</li>
     * </ol>
     *
     * @param conversationId 会话ID
     * @return 是否成功加载（true=加载成功或已存在，false=数据不存在）
     */
    public boolean loadConversationCache(Long conversationId) {
        log.info("开始会话缓存加载，会话ID：{}", conversationId);

        // 构建缓存Key
        String cacheKey = buildConversationCacheKey(conversationId);

        // 检查缓存是否存在
        boolean cacheExists = checkConversationCacheExists(cacheKey);
        if (cacheExists) {
            log.debug("会话缓存数据已存在，跳过加载，会话ID：{}", conversationId);
            return true;
        }

        log.debug("会话缓存不存在，从数据库查询，会话ID：{}", conversationId);
        Map<String, Object> conversationData = queryConversationCompleteDataFromDB(conversationId);

        if (conversationData.isEmpty()) {
            log.warn("未查询到会话完整数据，会话ID：{}", conversationId);
            return false;
        }

        log.info("压缩消息与分析数据，准备缓存，会话ID：{}", conversationId);

        // 缓存会话数据到Redis Hash结构
        saveConversationDataToCache(cacheKey, conversationData);

        log.info("会话缓存加载完成，会话ID：{}", conversationId);
        return true;
    }

    /**
     * 构建会话缓存的Redis Key
     *
     * @param conversationId 会话ID
     * @return Redis Key字符串
     */
    private String buildConversationCacheKey(Long conversationId) {
        return ConversationCacheConstant.CONVERSATION_CACHE_KEY_PREFIX + conversationId;
    }

    /**
     * 检查会话缓存数据是否存在
     *
     * @param cacheKey 缓存Key
     * @return 是否存在
     */
    private boolean checkConversationCacheExists(String cacheKey) {
        boolean exists = RedisUtils.isExistsObject(cacheKey);
        log.debug("检查会话缓存Hash是否存在：{}，结果：{}", cacheKey, exists);
        return exists;
    }

    /**
     * 从数据库查询会话完整数据（状态：已处理）
     *
     * @param conversationId 会话ID
     * @return 会话完整数据Map，如果不存在返回null
     */
    private Map<String, Object> queryConversationCompleteDataFromDB(Long conversationId) {
        log.debug("从数据库查询会话完整数据，会话ID：{}", conversationId);

        Conversation conversation = queryConversationInfo(conversationId);

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
     * 查询会话基础信息
     *
     * @param conversationId 会话ID
     * @return 会话实体对象
     */
    private Conversation queryConversationInfo(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            log.warn("会话基础信息查询为空，会话ID：{}", conversationId);
            throw new BusinessException(AIChatExceptionEnum.CONVERSATION_NOT_FOUND);
        }
        log.debug("会话基础信息查询成功，会话ID：{}，当前轮次：{}", conversation.getId(), conversation.getCurrentRound());
        return conversation;
    }

    /**
     * 查询已处理的最新消息列表（根据语义压缩轮次获取数据）
     *
     * @param conversation   会话实体对象（包含语义压缩轮次信息）
     * @param conversationId 会话ID
     * @return 语义压缩轮次后的已处理对话记忆列表
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
     * 查询最新的情绪分析结果列表（根据语义压缩轮次获取数据）
     *
     * @param conversation   会话实体对象（包含语义压缩轮次信息）
     * @param conversationId 会话ID
     * @return 语义压缩轮次后的情绪分析结果列表
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
     * 查询最近一次心理诊断结果
     *
     * @param conversationId 会话ID
     * @return 最新的诊断记录，如果没有返回null
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
     * 将会话数据保存到Redis缓存
     *
     * @param cacheKey        Redis Key
     * @param conversationData 会话数据Map
     */
    private void saveConversationDataToCache(String cacheKey, Map<String, Object> conversationData) {
        try {
            RedisUtils.setCacheMap(cacheKey, conversationData);
            RedisUtils.expire(cacheKey, ConversationCacheConstant.CONVERSATION_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

            log.info("会话数据缓存成功，Key：{}，过期时间：{}秒", cacheKey, ConversationCacheConstant.CONVERSATION_CACHE_EXPIRE_SECONDS);
        } catch (Exception e) {
            log.error("会话数据缓存失败，Key：{}", cacheKey, e);
            throw new RuntimeException("会话数据缓存失败", e);
        }
    }




}