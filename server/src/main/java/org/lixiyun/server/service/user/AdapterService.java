package org.lixiyun.server.service.user;

import org.lixiyun.pojo.dto.user.conversation.AudioInterruptDTO;
import org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO;
import org.lixiyun.pojo.dto.user.conversation.UserMessageSendDTO;
import org.lixiyun.pojo.vo.user.conversation.UserMessageSendVO;

/**
 * 输入适配器服务接口
 *
 * <p>统一封装文本/音频输入适配器的消息发送与停止信令，
 * 供 {@code AdapterController} 调用的单一 Service 入口。</p>
 *
 * @author lixiyun
 * @since 2026-10-03
 */
public interface AdapterService {

    /**
     * 发送文本消息
     *
     * <p>校验会话归属后，交由文本输入适配器持久化并触发 AI 对话流程。</p>
     *
     * @param dto 用户文本消息请求（含会话ID和消息内容）
     * @return 实际会话ID + 当前轮次
     */
    UserMessageSendVO sendTextMessage(UserMessageSendDTO dto);

    /**
     * 发送音频帧数据
     *
     * <p>校验会话归属后，将客户端上传的音频帧转交给 ASR 引擎识别，
     * 同时触发输出管道中断检测（新语音到达时打断正在生成的旧回复）。</p>
     *
     * @param dto 音频消息请求（含会话ID和音频字节数据）
     */
    void sendAudioFrame(AudioMessageSendDTO dto);

    /**
     * 前端 VAD 检测到用户停止说话后通知后端
     *
     * <p>校验会话归属后，向 ASR 引擎发送音频流结束信号，触发最终识别结果回调。</p>
     *
     * @param dto 含会话ID的中断/停止请求
     */
    void notifyVadStop(AudioInterruptDTO dto);
}