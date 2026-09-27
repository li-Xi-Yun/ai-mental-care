package org.lixiyun.common.websocket.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.common.websocket.constant.WebSocketConstant;
import org.lixiyun.pojo.tool.LoginUser;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器 — 一次性 Ticket 鉴权
 * <p>在原生 WebSocket 握手阶段从 URL query 参数中读取一次性 ticket，
 * 通过 Redis 校验其有效性（10s TTL + 使用即删），
 * 校验通过后将用户身份存入 WebSocket Session attributes，
 * 后续 STOMP 层 {@code WebSocketInboundInterceptor} 通过 {@code accessor.getUser()} 获取</p>
 *
 * @author lixiyun
 */
@Slf4j
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        log.info("WebSocket 握手前置拦截执行");

        String ticket = extractTicketFromQuery(request);
        if (StrUtil.isBlank(ticket)) {
            log.warn("WebSocket 握手失败：URL 中未携带 wsTicket 参数");
            return false;
        }

        String redisKey = WebSocketConstant.WS_TICKET_KEY_PREFIX + ticket;

        String userIdStr = RedisUtils.getCacheObject(redisKey);
        if (StrUtil.isBlank(userIdStr)) {
            log.warn("WebSocket 握手失败：ticket 无效或已过期，ticket={}", ticket);
            return false;
        }

        RedisUtils.deleteObject(redisKey);

        Long userId = Long.valueOf(userIdStr);

        String userStr = JwtUtil.getUserInfoFromRedis(userId, JwtType.USER);
        if (StrUtil.isBlank(userStr)) {
            log.warn("WebSocket 握手失败：Redis 中未找到用户信息，userId={}", userId);
            return false;
        }

        LoginUser loginUser = JSONUtil.toBean(userStr, LoginUser.class);
        if (loginUser == null || loginUser.getBasicsUser() == null) {
            log.warn("WebSocket 握手失败：用户信息解析失败，userId={}", userId);
            return false;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());

        attributes.put(WebSocketConstant.SIMP_USER_KEY, authentication);

        log.info("WebSocket 握手成功，用户 ID：{}", userId);
        return true;
    }

    /**
     * 从 URL query 参数中提取 wsTicket
     */
    private String extractTicketFromQuery(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (StrUtil.isBlank(query)) {
            return null;
        }

        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "wsTicket".equals(kv[0])) {
                return kv[1];
            }
        }

        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception ex) {
    }
}