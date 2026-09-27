package org.lixiyun.common.websocket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.common.websocket.constant.WebSocketConstant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 一次性 Ticket 控制器
 * <p>前端在建立原生 WebSocket 连接前，先携带长期 JWT（user-token Header）请求本接口换取临时 ticket，
 * 再将 ticket 拼入 WebSocket URL 的 query 参数完成握手阶段的身份认证</p>
 *
 * @author lixiyun
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "WebSocket Ticket接口", description = "WebSocket Ticket 相关接口")
public class WsTicketController {

    /**
     * 申请一次性 WebSocket 连接 Ticket
     * <p>由 {@code JwtAuthenticationTokenFilter} 完成 JWT 校验并设置 SecurityContext，
     * 本接口直接从中提取当前用户 ID，生成 UUID 凭证存入 Redis（10s 过期）</p>
     *
     * @return 包含 ticket 字符串的 Result
     */
    @GetMapping("/api/get-ws-ticket")
    @Operation(summary = "获取 WebSocket Ticket", description = """
            获取 WebSocket 一次性 Ticket 接口，用于建立 WebSocket 连接
            - 前端在建立原生 WebSocket 连接前，先携带长期 JWT（user-token Header）请求本接口换取临时 ticket
            - 再将 ticket 拼入 WebSocket URL 的 query 参数完成握手阶段的身份认证
            """)
    public Result<String> getWsTicket() {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        String ticket = UUID.randomUUID().toString().replace("-", "");

        RedisUtils.setCacheObject(
                WebSocketConstant.WS_TICKET_KEY_PREFIX + ticket,
                userId.toString(),
                10,
                TimeUnit.SECONDS
        );

        log.info("为用户 {} 生成 WebSocket 一次性 ticket", userId);
        return Result.success(ticket);
    }
}