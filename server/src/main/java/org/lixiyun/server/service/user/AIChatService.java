package org.lixiyun.server.service.user;

import org.lixiyun.pojo.dto.user.conversation.UserMessageSendDTO;
import org.lixiyun.pojo.vo.user.conversation.UserMessageSendVO;

/**
 * AI聊天服务接口
 * <p>提供用户消息发送、会话管理、定时任务、消息处理等核心功能</p>
 *
 * @author lixiyun
 * @since 2026-07-14 16:42
 * @deprecated 已被 {@code AdapterService#sendTextMessage} + {@code TextInputAdapter} 取代，
 * 文本消息发送统一走输入适配器链路。保留供回退参考。
 */
@Deprecated
public interface AIChatService {

    UserMessageSendVO sendUserMessage(UserMessageSendDTO userMessageSendDTO);

}