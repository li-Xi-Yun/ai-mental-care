package org.lixiyun.server.constant;

/**
 * 会话缓存常量
 * <p>定义Redis中会话缓存数据的Key和Field常量</p>
 * <p>
 * Redis中使用一个Hash结构进行会话数据的存储：
 * <ul>
 *     <li>主Key格式：conversation:cache:{conversationId} (Hash结构)</li>
 *     <li>Hash中的每个field对应一种会话数据，支持原子性操作</li>
 * </ul>
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-14 16:40
 */
public interface ConversationCacheConstant {

    /** 会话缓存Key前缀，用于构建Redis Hash的主Key */
    String CONVERSATION_CACHE_KEY_PREFIX = "conversation:cache:";

    /** 消息ZSet集合Key前缀，用于构建消息定时任务的SortedSet Key */
    String CONVERSATION_MESSAGE_ZSET_KEY_PREFIX = "conversation:message:zset:";

    /** Hash Field - 会话基本元数据信息，包含历史上下文语义压缩后的内容 */
    String HASH_FIELD_METADATA = "metadata";

    /** Hash Field - 会话历史上下文消息列表，采用后进先出（LIFO）的增量存储模式 */
    String HASH_FIELD_HISTORY_MESSAGES = "history_messages";

    /** Hash Field - 历史情绪分析结果列表，用于追踪情绪变化趋势 */
    String HASH_FIELD_EMOTION_ANALYSIS_LIST = "emotion_analysis_list";

    /** Hash Field - 历史心理诊断结果，只保留最新一次诊断数据 */
    String HASH_FIELD_PSYCHOLOGICAL_DIAGNOSIS = "psychological_diagnosis";

    /** Hash Field - 模型处理状态标识，防止并发重复处理 */
    String HASH_FIELD_PROCESS_FLAG = "process_flag";

    /** Hash Field - 会话私有映射表，存储Skill工具调用链的原始数据 */
    String HASH_FIELD_PRIVATE_MAPPING_TABLE = "private_mapping_table";

    /** Hash Field - 会话模式/类型标识，决定后续处理逻辑分支 */
    String HASH_FIELD_CONVERSATION_TYPE = "conversation_type";

    /** Hash Field - 会话中断标识，用于异常恢复和流程控制 */
    String HASH_FIELD_INTERRUPT_FLAG = "interrupt_flag";



    /** 会话缓存Hash的过期时间（秒） */
    long CONVERSATION_CACHE_EXPIRE_SECONDS = 3600L * 3;

    /** 消息ZSet集合的过期时间（秒），默认7200秒（2小时） */
    long MESSAGE_ZSET_EXPIRE_SECONDS = 7200L;

    /** zset集合消息重入时的指定时间间隔（秒） */
    int MESSAGE_ZSET_READD_INTERVAL_SECONDS = 2;

       /** 消息处理状态 - 未处理 */
    int PROCESS_FLAG_NOT_PROCESSED = 0;

    /** 消息处理状态 - 处理中 */
    int PROCESS_FLAG_PROCESSING = 1;

    /** 消息处理状态 - 已处理 */
//    int PROCESS_FLAG_PROCESSED = 2;

    /** 会话类型 - 文本模式 */
    String CONVERSATION_TYPE_TEXT = "text";

    /** 会话类型 - 语音模式 */
    String CONVERSATION_TYPE_AUDIO = "audio";

    /** 中断标识 - 激活状态（已中断） */
    String INTERRUPT_FLAG_ACTIVE = "1";

    /** 中断标识 - 非激活状态（正常/未中断） */
    String INTERRUPT_FLAG_INACTIVE = "0";

    static String buildConversationCacheKey(Long conversationId) {
        return CONVERSATION_CACHE_KEY_PREFIX + conversationId;
    }
}