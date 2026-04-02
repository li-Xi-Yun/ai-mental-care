package org.lixiyun.common.websocket.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.pojo.tool.LoginUser;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-03-29 22:36
 */
@Slf4j
@Component
public class WebSocketInterceptor implements ChannelInterceptor {

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        log.info("WebSocket前置拦截器执行");
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        // 仅处理客户端连接请求
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("WebSocket前置拦截器，建立会话：{}", accessor.getUser());
            String token = accessor.getFirstNativeHeader("token");
            Long userId = JwtUtil.parseJwtWithRedis(token);

            // 查询Redis，得到用户信息
            String userStr = JwtUtil.getUserInfoFromRedis(userId);
            if(StrUtil.isBlank(userStr)){
                return message;
            }

            LoginUser loginUser = JSONUtil.toBean(userStr, LoginUser.class);
            if(loginUser == null || loginUser.getBasicsUser() == null){
                return message;
            }

            // 刷新jwt时间
            JwtUtil.refreshJwtTTLWithRedis(userId);

            // 获取权限信息封装到Authentication中
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            // 绑定用户身份
            accessor.setUser(authentication);
            // 把用户相关信息放到SecurityContext上下文对象中
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("WebSocket前置拦截器，连接成功，用户ID：{}", userId);
        }

//        if(StompCommand.SEND.equals(accessor.getCommand())){
//            log.info("WebSocket前置拦截器，消息接收：{}", accessor.getUser());
//            SecurityContextHolder.getContext().setAuthentication(auth);
//        }

        return message;
    }

}
