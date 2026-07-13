package org.lixiyun.common.websocket.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.lixiyun.common.authentication.enums.JwtType;
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

import java.security.Principal;

/**
 * 专门处理来自客户端的WebSocket消息
 * @author lixiyun
 * @since 2026-03-29 22:36
 */
@Slf4j
@Component
public class WebSocketInboundInterceptor implements ChannelInterceptor {

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
//        log.debug("WebSocket客户端消息前置拦截器-执行");
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
//        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        // 仅处理客户端连接请求
        if (StompCommand.CONNECT.equals(command)) {
            log.info("WebSocket客户端消息前置拦截器-建立会话，用户身份：{}", accessor.getUser());
            String token = accessor.getFirstNativeHeader("token");

            // 一次性解析 JWT，获取类型和用户 ID（避免重复解析）
            JwtType jwtType = JwtType.USER;
            Long personId = JwtUtil.parseJwtWithType(token, jwtType);
            
            if (personId == null) {
                log.error("WebSocket客户端消息前置拦截器-建立会话失败，用户身份信息错误或Token无效");
                return null;
            }

            // 查询Redis，得到用户信息
            String userStr = JwtUtil.getUserInfoFromRedis(personId, jwtType);
            if(StrUtil.isBlank(userStr)){
                log.warn("WebSocket客户端消息前置拦截器-Redis中未找到用户信息，personId: {}", personId);
                return message;
            }

            LoginUser loginUser = JSONUtil.toBean(userStr, LoginUser.class);
            if(loginUser == null || loginUser.getBasicsUser() == null){
                log.warn("WebSocket客户端消息前置拦截器-用户信息解析失败，personId: {}", personId);
                return message;
            }

            // 刷新jwt时间
            JwtUtil.refreshJwtTTLWithRedis(personId, jwtType);

            // 获取权限信息封装到Authentication中
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            // 绑定用户身份
            accessor.setUser(authentication);
            // 把用户相关信息放到SecurityContext上下文对象中
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("WebSocket客户端消息前置拦截器-连接成功，用户ID：{}，类型：{}", personId, jwtType);
        }

        if(StompCommand.SUBSCRIBE.equals(command)){
            Principal user = accessor.getUser();
            if(user == null){
                log.error("WebSocket客户端消息前置拦截器-订阅失败，用户未登录");
                return null;
            }
            log.info("WebSocket客户端消息前置拦截器-订阅成功，用户ID：{}，订阅路径：{}，订阅ID：{}",
                    user.getName(), accessor.getDestination(), accessor.getSubscriptionId());
        }

        if(StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())){
            Principal user = accessor.getUser();
            if(user == null){
                log.error("WebSocket客户端消息前置拦截器-取消订阅失败，用户未登录");
                return null;
            }
            // 取消订阅中，是不能获取到对应的订阅路径的，只能获取到订阅ID
            log.info("WebSocket客户端消息前置拦截器-取消订阅成功，用户ID：{}，订阅ID：{}",
                    user.getName(), accessor.getSubscriptionId());
        }

        return message;
    }

}
