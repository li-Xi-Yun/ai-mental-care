package org.lixiyun.common.authentication.handler;

import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.tool.LoginUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        // 从认证对象获取用户信息
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String userId = loginUser.getBasicsUser().getId().toString();
        String jwt;

        // 生成 JWT 令牌
        jwt = JwtUtil.createJwtWithRedis(loginUser, true);

        Map<String, Object> map = new HashMap<>();
        map.put("token", jwt);
        if(loginUser.isMerge()){
            map.put("merge", true);
        }

        // 将令牌返回前端
        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(Result.success(map)));
    }
}