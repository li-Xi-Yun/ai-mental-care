package org.lixiyun.common.authentication.filer;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.properties.PermitUrlProperties;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.tool.LoginUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 这个用于认证用户是否登录(即：认证操作)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private final PermitUrlProperties permitUrlProperties;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 检查请求是否匹配任何放行规则
        String requestPath = request.getServletPath();

        // 防止浏览器默认行为
        if("/favicon.ico".equals(requestPath) || "/".equals(requestPath)
                || "/.well-known/appspecific/com.chrome.devtools.json".equals(requestPath)){
            return;
        }

        boolean isPermitAll = false;
        for (String permitAllUrl : permitUrlProperties.getPermitAllUrls()) {
            if(antPathMatcher.match(permitAllUrl, requestPath)){
                log.info("请求匹配放行规则: {}", requestPath);
                isPermitAll = true;
                break;
            }
        }

//        // 判断是否是 WebSocket 连接
//        boolean isWebSocket = isWebSocketRequest(request);
//        if (isWebSocket){
//            log.info("WebSocket 访问");
//        }
//
//        // 如果是 WebSocket → 从 URL 参数取 token，否则 → 从 header 取 token
//        String token;
//        if (isWebSocket) {
//            token = request.getParameter("token");
//            log.info("WebSocket 连接，从 URL 参数获取 token：{}", token);
//        } else {
//            // 获取请求体中的token
//            token = request.getHeader("token");
//        }

        // 进入以下代码，则表示需要进行认证
        String token = request.getHeader("token");

        // 如果是放行接口
        if (isPermitAll) {
            // 即使是放行接口，也尝试解析token获取用户信息（如果token存在且有效）
            if(StrUtil.isNotBlank(token)) {
                processToken(token);
            }

            // 直接放行
            filterChain.doFilter(request, response);
            return;
        }

        // 非放行接口需要强制认证
        if(StrUtil.isBlank(token)){
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSONUtil.toJsonStr(Result.error(AuthenticationExceptionEnum.JWT_ERROR)));
            log.warn("过滤器-用户未登录");
            return;
        }

        log.info("开始令牌校验");
        boolean tokenValid = processToken(token);

        if (!tokenValid) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSONUtil.toJsonStr(Result.error(AuthenticationExceptionEnum.USER_NOT_LOGIN)));
            log.warn("过滤器-用户未登录");
            return;
        }

        // 放行
        filterChain.doFilter(request, response);
    }


    /**
     * 处理token解析和用户信息获取，自动识别用户类型
     *
     * @param token JWT token
     * @return boolean token是否有效
     */
    private boolean processToken(String token) {
        // 尝试自动识别用户类型
        JwtType jwtType = JwtUtil.detectJwtType(token);
        return processTokenWithJwtType(token, jwtType);
    }

    /**
     * 处理token解析和用户信息获取，指定用户类型
     *
     * @param token   JWT token
     * @param jwtType JWT类型 {@link JwtType}
     * @return boolean token是否有效
     */
    private boolean processTokenWithJwtType(String token, JwtType jwtType) {
        if (jwtType == null) {
            log.warn("无法识别JWT类型");
            return false;
        }

        Long userId = JwtUtil.parseJwtWithRedis(token, jwtType);
        log.info("令牌中存放的id为{}，类型为{}", userId, jwtType);

        // 查询Redis，得到用户信息
        String userStr = JwtUtil.getUserInfoFromRedis(userId, jwtType);
        if (StrUtil.isBlank(userStr)) {
            log.warn("Redis中未找到用户信息，userId: {}", userId);
            return false;
        }

        LoginUser loginUser = JSONUtil.toBean(userStr, LoginUser.class);
        if (loginUser == null || loginUser.getBasicsUser() == null) {
            log.warn("用户信息解析失败，userId: {}", userId);
            return false;
        }

        // 刷新jwt时间
        JwtUtil.refreshJwtTTLWithRedis(userId, jwtType);

        // 获取权限信息封装到Authentication中
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        // 把用户相关信息放到SecurityContext上下文对象中
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return true;
    }


    /**
     * 判断是否为 WebSocket 请求
     */
    private boolean isWebSocketRequest(HttpServletRequest request) {
        String upgrade = request.getHeader("Upgrade");
        String connection = request.getHeader("Connection");
        return "websocket".equalsIgnoreCase(upgrade)
                && connection != null
                && connection.toLowerCase().contains("upgrade");
    }

}