package org.lixiyun.server.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 会话缓存管理器
 * <p>只负责Redis缓存读写、预热、刷新、上下文BO组装；DB查询全委托Repository</p>
 *
 * @author lixiyun
 * @since 2026-08-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationCacheManager {

    private final ConversationRepository conversationRepository;

    /**
     * 加载会话缓存
     * <p>检查Redis中是否已存在该会话的缓存数据，若已存在则跳过加载；
     * 若不存在则从数据库查询完整会话数据并写入Redis缓存。</p>
     *
     * @param conversationId 会话ID
     * @return {@code true} 缓存加载成功（含已存在的情况）；{@code false} 数据库中无数据导致加载失败
     */
    public boolean loadConversationCache(Long conversationId) {
        log.info("开始会话缓存加载，会话ID：{}", conversationId);

        String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);

        if (checkCacheExists(cacheKey)) {
            log.debug("会话缓存数据已存在，跳过加载，会话ID：{}", conversationId);
            return true;
        }

        log.debug("会话缓存不存在，从数据库查询，会话ID：{}", conversationId);
        Map<String, Object> conversationData = conversationRepository.queryConversationFullData(conversationId);

        if (conversationData.isEmpty()) {
            log.warn("未查询到会话完整数据，会话ID：{}", conversationId);
            return false;
        }

        saveToCache(cacheKey, conversationData);

        log.info("会话缓存加载完成，会话ID：{}", conversationId);
        return true;
    }

    /**
     * 检查缓存是否存在
     *
     * @param cacheKey 缓存Key
     * @return {@code true} 缓存存在；{@code false} 缓存不存在
     */
    private boolean checkCacheExists(String cacheKey) {
        boolean exists = RedisUtils.isExistsObject(cacheKey);
        log.debug("检查会话缓存Hash是否存在：{}，结果：{}", cacheKey, exists);
        return exists;
    }

    /**
     * 从Redis缓存中获取会话上下文数据
     *
     * @param conversationId 会话ID
     * @return 会话上下文数据Map，若缓存为空则返回空Map
     */
    private Map<String, Object> getConversationContextData(Long conversationId) {
        String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);
        Map<String, Object> contextData = RedisUtils.getCacheMap(cacheKey);

        if (contextData == null || contextData.isEmpty()) {
            log.warn("会话缓存数据为空，会话ID：{}", conversationId);
            return new HashMap<>();
        }

        log.debug("获取到会话缓存数据，字段数量：{}，会话ID：{}", contextData.size(), conversationId);
        return contextData;
    }

    /**
     * 构建会话处理上下文BO
     * <p>从Redis缓存中读取会话元数据、历史消息、情绪分析和诊断数据，
     * 结合传入的临时消息，组装为完整的处理上下文对象。</p>
     *
     * @param conversationId    会话ID
     * @param temporaryMessages 本轮待处理的临时消息列表
     * @return 组装完成的会话处理上下文BO
     */
    public ConversationProcessContextBO getProcessContext(Long conversationId, List<ConversationMemory> temporaryMessages) {
        log.debug("开始构建处理上下文，会话ID：{}", conversationId);

        Map<String, Object> contextData = getConversationContextData(conversationId);

        Conversation conversation = (Conversation) contextData.get(ConversationCacheConstant.HASH_FIELD_METADATA);
        List<ConversationMemory> conversationHistory = (List<ConversationMemory>) contextData.get(ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);
        List<EmotionAnalysis> emotionAnalyses = (List<EmotionAnalysis>) contextData.get(ConversationCacheConstant.HASH_FIELD_EMOTION_ANALYSIS_LIST);
        EmotionDiagnosis emotionDiagnosis = (EmotionDiagnosis) contextData.get(ConversationCacheConstant.HASH_FIELD_PSYCHOLOGICAL_DIAGNOSIS);

        ConversationProcessContextBO processContextBO = ConversationProcessContextBO.builder()
                .conversation(conversation)
                .temporaryMessages(temporaryMessages)
                .conversationHistory(conversationHistory)
                .emotionAnalyses(emotionAnalyses)
                .emotionDiagnosis(emotionDiagnosis)
                .build();

        log.debug("处理上下文构建完成，会话ID：{}，临时消息数：{}，情绪分析数：{}",
                conversationId,
                temporaryMessages != null ? temporaryMessages.size() : 0,
                emotionAnalyses != null ? emotionAnalyses.size() : 0);

        return processContextBO;
    }

    /**
     * 获取会话类型（聊天模式）
     * <p>优先从处理上下文中获取会话类型，若为空则从数据库查询并回写缓存。</p>
     *
     * @param conversationId 会话ID
     * @param processContext  会话处理上下文
     * @return 会话类型字符串，默认为文本类型
     */
    public String getConversationType(Long conversationId, ConversationProcessContextBO processContext) {
        String chatMode = processContext.getConversation().getChatMode();

        if (chatMode == null || chatMode.isBlank()) {
            Conversation conversation = conversationRepository.getConversationById(conversationId);
            String type = conversation != null ? conversation.getChatMode() : ConversationCacheConstant.CONVERSATION_TYPE_TEXT;

            String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);
            RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_CONVERSATION_TYPE, type);

            log.debug("从数据库获取会话类型：{}，会话ID：{}", type, conversationId);
            return type;
        }

        log.debug("从缓存获取会话类型：{}，会话ID：{}", chatMode, conversationId);
        return chatMode;
    }

    /**
     * 刷新会话元数据到缓存
     * <p>从数据库重新查询会话信息并更新到Redis缓存中，异常时仅记录日志不抛出。</p>
     *
     * @param conversationId 会话ID
     */
    public void refreshMetadata(Long conversationId) {
        try {
            String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);

            Conversation conversation = conversationRepository.getConversationById(conversationId);
            if (conversation != null) {
                RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_METADATA, conversation);

                log.debug("保存元数据和时间戳成功，当前轮次：{}，会话ID：{}", conversation.getCurrentRound(), conversationId);
            }
        } catch (Exception e) {
            log.error("保存元数据失败，会话ID：{}", conversationId, e);
        }
    }

    /**
     * 更新缓存中的会话元数据
     *
     * @param conversationId 会话ID
     * @param conversation   最新的会话实体
     */
    public void updateCacheMetadata(Long conversationId, Conversation conversation) {
        updateCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_METADATA, conversation);
    }

    /**
     * 更新缓存中的会话数据
     *
     * @param conversationId 会话ID
     * @param hKey           缓存字段
     * @param value          最新的值
     */
    public void updateCacheMapValue(Long conversationId, String hKey, Object value) {
        String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);
        RedisUtils.setCacheMapValue(cacheKey, hKey, value);
    }

    /**
     * 获取缓存中的会话元数据
     *
     * @param conversationId 会话ID
     * @return 会话实体
     */
    public Conversation getCacheMetadata(Long conversationId) {
        return (Conversation) getCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_METADATA);
    }

    /**
     * 获取缓存中的会话数据
     *
     * @param conversationId 会话ID
     * @param hKey           缓存字段
     * @return 缓存值
     */
    public Object getCacheMapValue(Long conversationId, String hKey) {
        String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);
        return RedisUtils.getCacheMapValue(cacheKey, hKey);
    }

    /**
     * 将会话数据保存到Redis缓存
     * <p>写入缓存Map并设置过期时间，失败时抛出运行时异常。</p>
     *
     * @param cacheKey          缓存Key
     * @param conversationData  会话完整数据Map
     * @throws RuntimeException 缓存写入失败时抛出
     */
    private void saveToCache(String cacheKey, Map<String, Object> conversationData) {
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