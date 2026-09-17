package org.lixiyun.server.infrastructure.conversation;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.node.conversation.ConversationNameGenerationNode;
import org.lixiyun.server.infrastructure.conversation.processor.MessageProcessor;
import org.lixiyun.server.infrastructure.conversation.processor.ProcessorHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会话消息处理器（流程编排器）
 * <p>只保留核心流程编排，自身不再包含任何Redis、DB、AI具体实现</p>
 * <p>流程步骤：</p>
 * <ol>
 *     <li>调用令牌管理器抢占处理权；失败重入队列</li>
 *     <li>调用缓存管理器加载会话上下文</li>
 *     <li>调用仓储查询未处理消息</li>
 *     <li>组装上下文BO、路由对应MessageProcessor</li>
 *     <li>提交异步任务到线程池（调用AiService）</li>
 *     <li>执行主线程消息处理</li>
 *     <li>调用仓储更新DB状态（事务保护）</li>
 *     <li>调用缓存管理器刷新元数据</li>
 *     <li>释放处理令牌</li>
 * </ol>
 *
 * @author lixiyun
 * @since 2026-07-14 17:36
 */
@Slf4j
@Component
public class ConversationMessageProcessor {

    @Autowired
    private ConversationCacheManager conversationCacheManager;
    @Autowired
    private ConversationProcessTokenManager conversationProcessTokenManager;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private ConversationAiService conversationAiService;
    @Autowired
    private ProcessorHolder processorHolder;
    @Autowired
    private ConversationNameGenerationNode conversationNameGenerationNode;
    @Autowired
    private ConversationWebSocketManager conversationWebSocketManager;

    @Autowired
    @Qualifier("diagnosisThreadPoolTaskExecutor")
    private ThreadPoolTaskExecutor diagnosisExecutor;

    /**
     * 处理会话消息的核心流程编排方法
     * <p>完整流程：获取处理令牌 → 加载缓存 → 查询未处理消息 → 构建上下文 →
     * 异步语义压缩 → 异步分析与诊断 → 主线程消息处理 → 更新数据库状态 →
     * 刷新缓存元数据 → 释放处理令牌。任何步骤异常均会清理处理标识。</p>
     *
     * @param conversationId 会话ID
     * @throws RuntimeException 消息处理失败时抛出
     */
    public void processConversationMessage(Long conversationId) {
        log.info("[流程编排] 开始会话消息处理，会话ID：{}", conversationId);
        log.debug("[流程编排] 步骤1/9：尝试获取处理令牌，会话ID：{}", conversationId);

        try {
            boolean tokenAcquired = conversationProcessTokenManager.acquireProcessingToken(conversationId);
            if (!tokenAcquired) {
                log.warn("[流程编排] 获取处理令牌失败，重新加入ZSet队列，会话ID：{}", conversationId);
                log.debug("[流程编排] 令牌抢占失败，将重新入队，会话ID：{}", conversationId);
                conversationProcessTokenManager.readdToZSetQueue(conversationId);
                return;
            }

            log.debug("[流程编排] 成功获取处理令牌，开始处理，会话ID：{}", conversationId);
            log.debug("[流程编排] 步骤2/9：加载会话缓存，会话ID：{}", conversationId);

            boolean cacheLoaded = conversationCacheManager.loadConversationCache(conversationId);
            if (!cacheLoaded) {
                log.warn("[流程编排] 会话缓存加载失败，清理处理标识并结束，会话ID：{}", conversationId);
                log.debug("[流程编排] 缓存加载返回false，提前退出流程，会话ID：{}", conversationId);
                conversationProcessTokenManager.clearProcessingFlag(conversationId);
                return;
            }
            log.debug("[流程编排] 会话缓存加载成功，会话ID：{}", conversationId);

            log.debug("[流程编排] 步骤3/9：查询未处理消息，会话ID：{}", conversationId);
            List<ConversationMemory> unprocessedMessages = conversationRepository.queryUnprocessedMessages(conversationId);
            log.debug("[流程编排] 查询到未处理消息数量：{}，会话ID：{}", unprocessedMessages.size(), conversationId);
            if (unprocessedMessages.isEmpty()) {
                log.info("没有待处理的临时消息，清理处理标识并结束，会话ID：{}", conversationId);
                conversationProcessTokenManager.clearProcessingFlag(conversationId);
                return;
            }

            log.debug("[流程编排] 步骤4/9：构建处理上下文，会话ID：{}，消息数：{}", conversationId, unprocessedMessages.size());
            ConversationProcessContextBO processContext = conversationCacheManager.getProcessContext(conversationId, unprocessedMessages);
            log.debug("[流程编排] 处理上下文构建完成，会话ID：{}，ConversationProcessContextBO：{}", conversationId, processContext);

            String conversationType = conversationCacheManager.getConversationType(conversationId, processContext);
            log.debug("[流程编排] 会话类型路由结果：{}，会话ID：{}", conversationType, conversationId);

            MessageProcessor executorInstance = processorHolder.getProcessor(conversationType);
            log.debug("[流程编排] 消息处理器实例：{}，会话ID：{}", executorInstance != null ? executorInstance.getClass().getSimpleName() : "null", conversationId);

            log.debug("[流程编排] 步骤5/9：提交异步语义压缩任务，会话ID：{}", conversationId);
            diagnosisExecutor.execute(() -> {
                try {
                    log.debug("[流程编排 - 异步] 语义压缩任务开始执行，会话ID：{}", conversationId);
                    conversationAiService.checkAndTriggerSemanticCompression(conversationId, processContext);
                    log.debug("[流程编排 - 异步] 语义压缩任务执行完成，会话ID：{}", conversationId);
                } catch (Exception e) {
                    log.error("异步语义压缩异常，会话ID：{}", conversationId, e);
                }
            });

            log.debug("[流程编排] 步骤6/9：主线程消息处理，会话ID：{}", conversationId);
            executeMainThread(conversationId, processContext, executorInstance);

            log.debug("[流程编排] 步骤7/9：提交异步分析与诊断任务，会话ID：{}", conversationId);
            diagnosisExecutor.execute(() -> {
                try {
                    log.debug("[流程编排 - 异步] 分析与诊断任务开始执行，会话ID：{}", conversationId);
                    conversationAiService.analysisAndDiagnosis(conversationId, processContext);
                    log.debug("[流程编排 - 异步] 分析与诊断任务执行完成，会话ID：{}", conversationId);
                } catch (Exception e) {
                    log.error("异步分析与诊断异常，会话ID：{}", conversationId, e);
                }
            });

            log.debug("[流程编排] 提交异步会话名称生成任务，会话ID：{}", conversationId);
            diagnosisExecutor.execute(() -> {
                try {
                    log.debug("[流程编排 - 异步] 会话名称生成任务开始执行，会话ID：{}", conversationId);
                    generateConversationNameIfFirstRound(conversationId, processContext);
                    log.debug("[流程编排 - 异步] 会话名称生成任务执行完成，会话ID：{}", conversationId);
                } catch (Exception e) {
                    log.error("异步会话名称生成异常，会话ID：{}", conversationId, e);
                }
            });

            log.debug("[流程编排] 步骤8/9：更新数据库状态，会话ID：{}，消息数：{}", conversationId, unprocessedMessages.size());
            conversationRepository.updateConversationState(conversationId, unprocessedMessages);
            log.debug("[流程编排] 数据库状态更新完成，会话ID：{}", conversationId);

            log.debug("[流程编排] 步骤9/9：刷新缓存元数据并释放处理令牌，会话ID：{}", conversationId);
            conversationCacheManager.refreshMetadata(conversationId);
            log.debug("[流程编排] 缓存元数据刷新完成，会话ID：{}", conversationId);

            conversationProcessTokenManager.clearProcessingFlag(conversationId);
            log.debug("[流程编排] 处理令牌已释放，会话ID：{}", conversationId);

            log.info("[流程编排] 会话消息处理完成，会话ID：{}，处理消息数：{}", conversationId, unprocessedMessages.size());
        } catch (Exception e) {
            log.error("[[流程编排 - 异常] 会话消息处理异常，会话ID：{}", conversationId, e);
            log.debug("[流程编排] 异常后清理处理标识，会话ID：{}", conversationId);
            conversationProcessTokenManager.clearProcessingFlag(conversationId);
            throw new RuntimeException("会话消息处理失败", e);
        }
    }

    /**
     * 加载会话缓存（委托给{@link ConversationCacheManager}）
     *
     * @param conversationId 会话ID
     * @return {@code true} 缓存加载成功；{@code false} 加载失败
     */
    public boolean loadConversationCache(Long conversationId) {
        log.debug("[缓存加载] 委托ConversationCacheManager加载会话缓存，会话ID：{}", conversationId);
        boolean result = conversationCacheManager.loadConversationCache(conversationId);
        log.debug("[缓存加载] 缓存加载结果：{}，会话ID：{}", result, conversationId);
        return result;
    }

    /**
     * 执行主线程消息处理
     * <p>调用对应的{@link MessageProcessor}处理消息，记录执行耗时。
     * 处理器为空时抛出业务异常，业务异常直接抛出，系统异常包装为业务异常抛出。</p>
     *
     * @param conversationId  会话ID
     * @param processContext   会话处理上下文
     * @param executorInstance 消息处理器实例
     * @throws BusinessException 处理器未找到或执行异常时抛出
     */
    private void executeMainThread(Long conversationId, ConversationProcessContextBO processContext, MessageProcessor executorInstance) {
        String executorName = executorInstance != null ? executorInstance.getClass().getSimpleName() : "null";
        log.info("[主线程] 开始主线程执行，会话ID：{}，执行器：{}", conversationId, executorName);
        log.debug("[主线程] 会话ID：{}，处理器类型：{}，上下文轮次：{}", conversationId, executorName,
                processContext.getConversation() != null ? processContext.getConversation().getCurrentRound() : "null");

        if (executorInstance == null) {
            log.debug("[主线程] 处理器实例为空，抛出PROCESSOR_NOT_FOUND异常，会话ID：{}", conversationId);
            throw new BusinessException(AIChatExceptionEnum.PROCESSOR_NOT_FOUND);
        }

        try {
            log.info("[主线程] 开始调用消息处理器，会话ID：{}", conversationId);
            log.debug("[主线程] 调用{}.processMessage()，会话ID：{}", executorName, conversationId);

            long startTime = System.currentTimeMillis();

            executorInstance.processMessage(processContext);

            long endTime = System.currentTimeMillis();
            long elapsed = endTime - startTime;

            log.info("[主线程] 主线程执行完成，会话ID：{}，耗时：{}ms", conversationId, elapsed);
            log.debug("[主线程] 消息处理器执行耗时明细：{}ms，处理器：{}，会话ID：{}", elapsed, executorName, conversationId);
        } catch (BusinessException e) {
            log.error("[主线程] 主线程执行业务异常，会话ID：{}，错误代码：{}，错误信息：{}", conversationId, e.getCode(), e.getMessage());
            log.debug("[主线程] 业务异常详情，错误代码：{}，会话ID：{}", e.getCode(), conversationId);
            throw e;
        } catch (Exception e) {
            log.error("主线程执行系统异常，会话ID：{}", conversationId, e);
            log.debug("[主线程] 系统异常将包装为MAIN_THREAD_EXECUTION_FAILED，会话ID：{}", conversationId);
            throw new BusinessException(AIChatExceptionEnum.MAIN_THREAD_EXECUTION_FAILED);
        }
    }

    /**
     * 判断是否是第一次对话，如果是则生成会话名称并更新
     * <p>根据当前轮次是否为1判断是否是首次对话，首次对话时调用AI模型
     * 根据用户消息内容生成会话名称，并更新到数据库和缓存中。</p>
     *
     * @param conversationId 会话ID
     * @param processContext  会话处理上下文
     */
    private void generateConversationNameIfFirstRound(Long conversationId, ConversationProcessContextBO processContext) {
        Conversation conversation = processContext.getConversation();
        Integer currentRound = conversation.getCurrentRound();
        Long userId = conversation.getUserId();
        log.debug("[会话命名] 判断是否首次对话，会话ID：{}，当前轮次：{}，用户ID：{}", conversationId, currentRound, userId);

        if (currentRound != null && currentRound == 1) {
            log.info("[会话命名] 首次对话，开始生成会话名称，会话ID：{}", conversationId);
            try {
                long startTime = System.currentTimeMillis();
                String conversationName = conversationNameGenerationNode.apply(processContext);
                log.debug("[会话命名] AI生成会话名称完成，会话ID：{}，名称：{}，耗时：{}ms", conversationId, conversationName, System.currentTimeMillis() - startTime);

                conversationRepository.updateConversationName(conversationId, conversationName);
                log.debug("[会话命名] 数据库会话名称已更新，会话ID：{}，名称：{}", conversationId, conversationName);

                Conversation updatedConversation = conversationRepository.getConversationById(conversationId);
                log.debug("[会话命名] 查询更新后的会话实体，会话ID：{}，名称：{}", conversationId, updatedConversation != null ? updatedConversation.getName() : "null");

                conversationCacheManager.updateCacheMetadata(conversationId, updatedConversation);
                log.debug("[会话命名] 缓存元数据已更新，会话ID：{}", conversationId);

                conversationWebSocketManager.sendConversationName(userId, conversationId, conversationName);
                log.debug("[会话命名] WebSocket推送会话名称完成，用户ID：{}，会话ID：{}", userId, conversationId);

                log.info("[会话命名] 会话名称生成完成，会话ID：{}，名称：{}", conversationId, conversationName);
            } catch (Exception e) {
                log.error("[会话命名] 会话名称生成异常，会话ID：{}", conversationId, e);
            }
        } else {
            log.debug("[会话命名] 非首次对话，跳过会话名称生成，会话ID：{}，当前轮次：{}", conversationId, currentRound);
        }
    }
}