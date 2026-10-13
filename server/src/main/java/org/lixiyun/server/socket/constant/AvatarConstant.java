package org.lixiyun.server.socket.constant;

/**
 * 虚拟形象（Live2D）WebSocket 推送通道常量
 *
 * <p>表情与动作分别走独立通道：
 * <ul>
 *   <li>表情是持续状态（切换后保持直到下次切换）</li>
 *   <li>动作是一次性触发（播放完毕后自动回归）</li>
 * </ul>
 * 客户端订阅时需在路径后拼接 /{conversationId}。
 *
 * @author lixiyun
 * @since 2026-10-12
 */
public interface AvatarConstant {

    /** AI 虚拟形象表情指令，客户端订阅时需在路径后添加/{conversationId} */
    String AI_AVATAR_EXPRESSION = "/avatar-2d/expression";

    /** AI 虚拟形象动作指令，客户端订阅时需在路径后添加/{conversationId} */
    String AI_AVATAR_MOTION = "/avatar-2d/motion";

}
