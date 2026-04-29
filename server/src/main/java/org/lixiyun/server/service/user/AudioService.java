package org.lixiyun.server.service.user;

import org.lixiyun.pojo.dto.user.conversation.AudioModelDTO;
import org.lixiyun.pojo.vo.user.conversation.ConversationVO;

import java.io.OutputStream;

/**
 * 音频服务接口
 * <p>
 * 提供音频模型处理、会话控制及会话名称初始化等功能。
 * </p>
 *
 * @author lixiyun
 * @since 2026-03-29 18:17
 */
public interface AudioService {

    /**
     * 处理音频模型请求
     * <p>
     * 根据传入的音频模型参数，生成音频数据并写入输出流。
     * 支持实时音频流式传输。
     * </p>
     *
     * @param audioModelDTO 音频模型数据传输对象，包含音频生成所需参数
     * @param outputStream  输出流，用于接收生成的音频数据
     */
    void audioModel(AudioModelDTO audioModelDTO, OutputStream outputStream);

    /**
     * 停止音频播放
     * <p>
     * 根据会话ID停止当前正在进行的音频播放或生成任务。
     * </p>
     *
     * @param conversationId 会话ID，标识需要停止音频的会话
     */
    ConversationVO audioStop(Long conversationId);

    /**
     * 初始化会话名称
     * <p>
     * 根据会话内容自动生成或初始化会话的名称标识。
     * </p>
     *
     * @param conversationId 会话ID，标识需要初始化名称的会话
     * @return 初始化后的会话名称
     */
    String initializeConversationName(Long conversationId);
}
