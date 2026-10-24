package org.lixiyun.common.websocket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
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
 * <p>客户端在建立原生 WebSocket 连接前，先携带长期 JWT（user-token / admin-token Header）请求本接口换取临时 ticket，
 * 再将 ticket 拼入 WebSocket URL 的 query 参数完成握手阶段的身份认证。ticket 值形如 {@code {userId}:{JwtType}}，
 * 供 {@code AuthHandshakeInterceptor} 握手时按用户类型加载对应的 Redis 用户信息。</p>
 *
 * @author lixiyun
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "WebSocket Ticket接口", description = "WebSocket Ticket 相关接口")
public class WsTicketController {

    /**
     * 申请一次性 WebSocket 连接 Ticket（普通用户）
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
        return Result.success(generateWsTicket(userId, JwtType.USER));
    }

    /**
     * 申请一次性 WebSocket 连接 Ticket（管理员）
     * <p>路径带 {@code /admin} 前缀，使 {@code JwtAuthenticationTokenFilter} 按 ADMIN 策略解析 admin-token 并设置 SecurityContext。
     * 管理端（如文件向量解析状态实时推送）建立 WebSocket 前调用本接口换取 ticket。</p>
     *
     * @return 包含 ticket 字符串的 Result
     */
    @GetMapping("/admin/get-ws-ticket")
    @Operation(summary = "获取 WebSocket Ticket（管理员）", description = """
            管理员获取 WebSocket 一次性 Ticket，用于管理端实时通知推送
            - 前端在建立原生 WebSocket 连接前，先携带长期 JWT（admin-token Header）请求本接口换取临时 ticket
            - 再将 ticket 拼入 WebSocket URL 的 query 参数完成握手阶段的身份认证
            """)
    public Result<String> getAdminWsTicket() {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        return Result.success(generateWsTicket(userId, JwtType.ADMIN));
    }

    /**
     * 生成一次性 ticket 并写入 Redis
     * <p>值为 {@code "{userId}:{JwtType}"}，10s 过期，使用即删</p>
     *
     * @param userId  用户ID
     * @param jwtType 用户类型（USER/ADMIN）
     * @return ticket 字符串
     */
    private String generateWsTicket(Long userId, JwtType jwtType) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        RedisUtils.setCacheObject(
                WebSocketConstant.WS_TICKET_KEY_PREFIX + ticket,
                userId + ":" + jwtType.name(),
                10,
                TimeUnit.SECONDS
        );
        log.info("为用户 {}（类型 {}）生成 WebSocket 一次性 ticket", userId, jwtType);
        return ticket;
    }
}