package org.lixiyun.server.infrastructure.log;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI流程执行上下文管理器
 * <p>封装所有Redis Hash操作，管理流程执行期间的上下文数据。</p>
 * <p>底层存储为Redis Hash，key为 flow:ctx:{conversation_id}，常量定义在{@link FlowExecutionConstant}中。</p>
 * <p>所有AOP切面和拦截器通过该类调用，底层通过{@link RedisUtils}统一操作Redis。</p>
 *
 * @author lixiyun
 * @since 2026-09-11
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class FlowExecutionContextManager {

    /**
     * 初始化流程上下文（流程入口AOP调用）
     * <p>写入全部初始字段并设置TTL</p>
     *
     * @param conversationId 会话ID
     * @param traceId        流程唯一标识（雪花算法）
     * @param roundNum       当前轮次
     * @param userId         用户ID
     */
    public void initContext(Long conversationId, Long traceId, Integer roundNum, Long userId) {
        String key = FlowExecutionConstant.buildCtxKey(conversationId);
        Map<String, String> ctx = new HashMap<>();
        ctx.put(FlowExecutionConstant.FIELD_TRACE_ID, String.valueOf(traceId));
        ctx.put(FlowExecutionConstant.FIELD_CONVERSATION_ID, String.valueOf(conversationId));
        ctx.put(FlowExecutionConstant.FIELD_ROUND_NUM, String.valueOf(roundNum));
        ctx.put(FlowExecutionConstant.FIELD_USER_ID, String.valueOf(userId));
        ctx.put(FlowExecutionConstant.FIELD_NODE_SEQUENCE, "0");
        RedisUtils.setCacheMap(key, ctx);
        RedisUtils.expire(key, FlowExecutionConstant.CTX_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("初始化流程上下文，conversationId={}，traceId={}", conversationId, traceId);
    }

    /**
     * 获取trace_id
     *
     * @param conversationId 会话ID
     * @return trace_id，若上下文不存在则返回null
     */
    public Long getTraceId(Long conversationId) {
        String key = FlowExecutionConstant.buildCtxKey(conversationId);
        String val = RedisUtils.getCacheMapValue(key, FlowExecutionConstant.FIELD_TRACE_ID);
        return val != null ? Long.valueOf(val) : null;
    }

    /**
     * 原子递增node_sequence并返回递增后的值
     * <p>底层使用Redis HINCRBY命令，天然保证原子性，适用于并行节点场景</p>
     *
     * @param conversationId 会话ID
     * @return 递增后的node_sequence值
     */
    public Long incrementNodeSequence(Long conversationId) {
        String key = FlowExecutionConstant.buildCtxKey(conversationId);
        return RedisUtils.incrementCacheMapValue(key, FlowExecutionConstant.FIELD_NODE_SEQUENCE, 1);
    }

    /**
     * 获取round_num
     *
     * @param conversationId 会话ID
     * @return round_num，若上下文不存在则返回null
     */
    public Integer getRoundNum(Long conversationId) {
        String key = FlowExecutionConstant.buildCtxKey(conversationId);
        String val = RedisUtils.getCacheMapValue(key, FlowExecutionConstant.FIELD_ROUND_NUM);
        return val != null ? Integer.valueOf(val) : null;
    }

    /**
     * 获取user_id
     *
     * @param conversationId 会话ID
     * @return user_id，若上下文不存在则返回null
     */
    public Long getUserId(Long conversationId) {
        String key = FlowExecutionConstant.buildCtxKey(conversationId);
        String val = RedisUtils.getCacheMapValue(key, FlowExecutionConstant.FIELD_USER_ID);
        return val != null ? Long.valueOf(val) : null;
    }

    /**
     * 判断上下文是否存在（降级判断用）
     * <p>若返回false（如流程入口AOP未执行或Redis Key已过期），
     * 切面应跳过日志记录，仅执行原方法</p>
     *
     * @param conversationId 会话ID
     * @return true=上下文存在；false=上下文不存在
     */
    public boolean existsContext(Long conversationId) {
        String key = FlowExecutionConstant.buildCtxKey(conversationId);
        return RedisUtils.isExistsObject(key);
    }

    /**
     * 删除流程上下文（流程结束AOP finally调用）
     *
     * @param conversationId 会话ID
     */
    public void removeContext(Long conversationId) {
        String key = FlowExecutionConstant.buildCtxKey(conversationId);
        RedisUtils.deleteObject(key);
        log.debug("删除流程上下文，conversationId={}", conversationId);
    }
}