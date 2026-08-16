package org.lixiyun.server.service.user;

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
     *
     * @param audioMessageSendDTO 语音消息发送请求DTO
     */
    void sendAudioMessage(AudioMessageSendDTO audioMessageSendDTO);

    /**
     * 中断语音对话
     * <p>
     * 缓存获取模型处理标识，判断会话处于处理中状态，
     * 判断缓存中是否有中断标识，设置中断标志，
     * 获取LLM与TTS的会话数据，中断LLM与TTS执行。
     * </p>
     *
     * @param audioInterruptDTO 语音中断请求DTO
     */
    void interruptAudio(AudioInterruptDTO audioInterruptDTO);

    /**
     * 结束语音会话
     * <p>
     * DB判断会话是否存在、状态是否正常，记录日志报错；
     * DB判断是否在会话消息中；判断会话缓存是否存在；
     * 判断缓存模式是否是语音对话模式；
     * 获取中断转移时间轮，从ZSet中移除该会话ID；
     * DB更新该会话基础表模式为文本对话模式；
     * 缓存更新为文本对话模式；关闭ASR音频流连接；关闭该会话所有WebSocket连接。
     * </p>
     *
     * @param conversationId 会话ID
     */
    void endSession(Long conversationId);

}