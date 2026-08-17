package org.lixiyun.server.service.user;

import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.pojo.dto.user.conversation.AudioInterruptDTO;
import org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO;
import org.lixiyun.pojo.vo.user.conversation.AudioSessionInitVO;

/**
 * 音频服务接口
 * <p>
 * 提供语音会话初始化、语音消息发送、语音中断、语音会话结束等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-03-29 18:17
 */
public interface AudioService {

    /**
     * 初始化语音会话
     * <p>
     * 判断会话ID是否存在，如果不存在则创建新会话并返回会话信息和WebSocket连接地址；
     * 如果存在则检查状态是否正常，正常则直接返回信息，异常则记录日志报错。
     * </p>
     *
     * @param conversationId 会话ID（可选），第一次对话时为null
     * @return 语音会话初始化响应，包含会话ID和WebSocket连接地址
     */
    AudioSessionInitVO initSession(Long conversationId);

    /**
     * 发送语音消息
     * <p>
     * 接收用户语音消息，进行ASR语音转文本，WebSocket实时展示，
     * 实时文本收集，DB新增历史上下文数据，设置最新会话基础表中的最后消息发送时间等操作。
     * </p>
     * <p>
     * 处理流程：
     * <ul>
     *     <li>参数校验：校验会话ID和音频数据有效性</li>
     *     <li>ASR识别：将音频数据发送至ASR引擎进行实时语音转文本</li>
     *     <li>实时展示：通过WebSocket实时推送中间识别结果</li>
     *     <li>文本收集：收集完整句子并保存至{@link org.lixiyun.pojo.entity.conversation.ConversationMemory}</li>
     *     <li>会话更新：更新会话基础表的最后活跃时间</li>
     *     <li>缓存管理：将会话ID加入ZSet集合用于聚合调度</li>
     *     <li>时间轮调度：启动延迟聚合任务处理消息</li>
     * </ul>
     * </p>
     *
     * @param audioMessageSendDTO 语音消息发送请求DTO，包含音频数据和会话ID
     * @see org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO
     */
    void sendAudioMessage(AudioMessageSendDTO audioMessageSendDTO);

    /**
     * 中断语音对话
     * <p>
     * 用于在模型处理过程中主动中断当前的语音对话流程，
     * 适用于用户取消操作、切换话题等场景。该方法通过缓存机制实现高效的状态检查和资源释放。
     * </p>
     *
     * <h3>处理流程</h3>
     * <ol>
     *     <li>从{@link org.lixiyun.pojo.dto.user.conversation.AudioInterruptDTO}获取会话ID</li>
     *     <li>从Redis缓存获取模型处理标识（{@code process_flag}）</li>
     *     <li>判断会话是否处于处理中状态：
     *         <ul>
     *             <li>未处于处理中状态 → 直接结束，无需中断</li>
     *             <li>处于处理中状态 → 继续后续流程</li>
     *         </ul>
     *     </li>
     *     <li>判断缓存中是否存在中断标识（{@code interrupt_flag}）：
     *         <ul>
     *             <li>已存在中断标识 → 直接结束，避免重复中断</li>
     *             <li>不存在 → 继续设置中断标志并执行中断</li>
     *         </ul>
     *     </li>
     *     <li>设置中断标志到缓存</li>
     *     <li>从会话元数据中获取用户ID</li>
     *     <li>中断ASR连接和TTS合成执行</li>
     * </ol>
     *
     * <h3>异常场景</h3>
     * <ul>
     *     <li>会话元数据未配置 → 抛出{@link org.lixiyun.common.core.error.enums.ConversationExceptionEnum#CONVERSATION_METADATA_NOT_CONFIGURED}</li>
     * </ul>
     *
     * @param audioInterruptDTO 语音中断请求DTO，包含会话ID
     * @see org.lixiyun.pojo.dto.user.conversation.AudioInterruptDTO
     */
    void interruptAudio(AudioInterruptDTO audioInterruptDTO);

    /**
     * 结束语音会话
     * <p>
     * 完整的语音会话结束流程，包括状态校验、资源释放、模式切换等操作。
     * 该方法确保语音会话能够被优雅地终止，所有相关资源得到正确清理。
     * </p>
     *
     * <h3>处理流程</h3>
     * <ol>
     *     <li>DB校验：判断{@link org.lixiyun.pojo.entity.conversation.Conversation}是否存在且状态正常，
     *         不存在则记录错误日志并抛出异常</li>
     *     <li>消息检查：DB判断是否存在会话消息记录（{@link org.lixiyun.pojo.entity.conversation.ConversationMemory}），
     *         无消息则删除该会话</li>
     *     <li>缓存校验：判断Redis中会话缓存元数据是否存在</li>
     *     <li>模式判断：判断当前会话缓存模式是否是语音对话模式，
     *         非语音模式则直接返回无需处理</li>
     *     <li>语音中断：设置中断标识、取消LLM流式订阅、中断TTS合成</li>
     *     <li>任务清理：取消时间轮调度器中的聚合任务</li>
     *     <li>ZSet移除：从Redis有序集合中移除该会话ID，停止自动聚合调度</li>
     *     <li>DB模式切换：更新数据库中{@link org.lixiyun.pojo.entity.conversation.Conversation#chatMode}为文本对话模式</li>
     *     <li>缓存模式切换：同步更新Redis Hash中的会话类型字段为文本模式</li>
     *     <li>ASR关闭：调用ASR连接管理器关闭用户的ASR长连接并释放资源</li>
     *     <li>TTS关闭：调用TTS连接管理器取消用户的TTS会话上下文并释放资源</li>
     *     <li>WebSocket关闭：关闭该会话相关的所有WebSocket连接（文本流、音频流、ASR中间结果等）</li>
     * </ol>
     *
     * <h3>事务保证</h3>
     * <p>
     * 该方法使用{@code @Transactional(rollbackFor = Exception.class)}注解，
     * 确保数据库操作的事务原子性。当任何步骤出现异常时，
     * 所有已执行的数据库修改操作将自动回滚。
     * </p>
     *
     * <h3>异常场景</h3>
     * <ul>
     *     <li>会话不存在 → 抛出{@link ConversationExceptionEnum#CONVERSATION_NOT_EXIST}</li>
     *     <li>会话无消息 → 抛出{@link ConversationExceptionEnum#DIALOGUE_NOT_EXIST}并删除空会话</li>
     * </ul>
     *
     * @param conversationId 会话ID，用于定位待结束的语音会话
     * @see org.lixiyun.pojo.entity.conversation.Conversation
     * @see org.lixiyun.pojo.entity.conversation.ConversationMemory
     */
    void endSession(Long conversationId);

}