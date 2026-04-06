package org.lixiyun.server.controller.temp;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.websocket.utils.WebSocketUtils;
import org.lixiyun.server.socket.constant.AudioConstant;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * @author lixiyun
 * @since 2026-04-03 21:27
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/temp")
@Tag(name = "socket测试相关接口", description = "socket测试相关接口")
public class SocketText {

    @PostMapping(value = "/server-to-client", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody audioModel() {
        log.info("服务端推送消息到客户端");
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        WebSocketUtils.sendToUserBySubDestination(String.valueOf(currentId), AudioConstant.AUDIO_CONVERSATION_ID, 13523);
        return null;
    }

}
