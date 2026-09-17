package org.lixiyun.server.infrastructure.log;

/**
 * AI流程执行上下文相关常量
 * <p>定义Redis中流程执行上下文数据的Key和Field常量</p>
 * <p>
 * Redis中使用一个Hash结构进行流程上下文的存储：
 * <ul>
 *     <li>主Key格式：flow:ctx:{conversationId} (Hash结构)</li>
 *     <li>Hash中的每个field对应一种上下文字段，支持原子性操作</li>
 * </ul>
 * </p>
 *
 * @author lixiyun
 * @since 2026-09-11
 */
public interface FlowExecutionConstant {

    /** 流程上下文Key前缀，用于构建Redis Hash的主Key */
    String CTX_KEY_PREFIX = "flow:ctx:";

    /** Hash Field - 流程唯一标识（雪花算法） */
    String FIELD_TRACE_ID = "trace_id";

    /** Hash Field - 会话ID */
    String FIELD_CONVERSATION_ID = "conversation_id";

    /** Hash Field - 当前轮次编号 */
    String FIELD_ROUND_NUM = "round_num";

    /** Hash Field - 用户ID */
    String FIELD_USER_ID = "user_id";

    /** Hash Field - 节点执行顺序计数器（从1递增） */
    String FIELD_NODE_SEQUENCE = "node_sequence";

    /** 流程上下文TTL（分钟） */
    int CTX_TTL_MINUTES = 5;

    /**
     * 构建流程上下文Redis Hash的Key
     *
     * @param conversationId 会话ID
     * @return Redis Hash Key
     */
    static String buildCtxKey(Long conversationId) {
        return CTX_KEY_PREFIX + conversationId;
    }
}