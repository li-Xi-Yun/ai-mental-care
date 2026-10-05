package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.aop.annotation.RateLimit;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO;
import org.lixiyun.pojo.dto.user.conversation.UserMessageSendDTO;
import org.lixiyun.pojo.vo.user.conversation.UserMessageSendVO;
import org.lixiyun.server.service.user.AdapterService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 输入适配器控制器
 *
 * <p>为文本适配器与音频适配器提供接入层，遵循 MVC 分层：
 * Controller → Service → Adapter（基础设施层）。</p>
 *
 * <h3>接口一览：</h3>
 * <ul>
 *   <li><b>文本</b>：{@code POST /user/conversation/adapter/text/send} — 发送用户文本消息（HTTP）</li>
 *   <li><b>音频（数据接收）</b>：{@code STOMP /user/conversation/adapter/audio/frame} — 上传音频帧交由 ASR 识别（WebSocket）</li>
 *   <li><b>音频（VAD 停止）</b>：{@code STOMP /user/conversation/adapter/audio/vad-stop} — 前端 VAD 检测到静音，通知 ASR 结束音频流（WebSocket）</li>
 * </ul>
 *
 * <p>说明：音频帧数据需要持续、实时地流式上送，且识别结果依赖回调推送，
 * 因此音频相关接口使用 WebSocket（STOMP）实现；文本消息为一次性请求，使用 HTTP 即可。</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversation/adapter")
@Tag(name = "输入适配器接口", description = "文本/音频输入适配器的消息发送与停止信令接口")
public class AdapterController {

    private final AdapterService adapterService;

    /**
     * 发送用户文本消息（HTTP）
     */
    @PostMapping("/text/send")
    @Operation(summary = "发送用户文本消息", description = """
            发送用户文本消息，交由文本输入适配器持久化并触发 AI 对话流程。
            校验会话归属后发送。
            """)
    @RateLimit(type = RateLimit.RateLimitType.INTERFACE, key = "conversation-text", maxRequests = 1, windowSizeInMillis = 200)
    public Result<UserMessageSendVO> sendTextMessage(@RequestBody @Validated UserMessageSendDTO dto) {
        log.info("[适配器控制层] 收到文本消息发送请求，会话ID：{}", dto.getConversationId());
        UserMessageSendVO vo = adapterService.sendTextMessage(dto);
        return Result.success(vo);
    }

    /**
     * 上传音频帧数据（WebSocket，ASR 数据接收接口）
     *
     * <p>STOMP 目的地：{@code /user/conversation/adapter/audio/frame}。
     * ASR 识别结果（中间弹幕/最终文本）由服务端通过 WebSocket 反向推送。</p>
     */
    @MessageMapping("/audio/frame")
    @Operation(summary = "上传音频帧数据（WebSocket）", description = """
            通过 STOMP 上传一帧音频数据，交由音频输入适配器转发 ASR 引擎进行语音识别。
            前端在说话过程中持续发送本消息。
            """)
    public void sendAudioFrame(@Payload @Valid AudioMessageSendDTO dto) {
        log.info("[适配器控制层] 收到音频帧上传消息，会话ID：{}，数据长度：{}字节",
                dto.getConversationId(), dto.getAudioMessage() == null ? 0 : dto.getAudioMessage().length);
        adapterService.sendAudioFrame(dto);
    }

}