package org.lixiyun.common.authentication.handler;

import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthenticationHandler implements AuthenticationEntryPoint {
    // 认证失败处理器（默认返回401状态码）
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
//        throw new BusinessException(ExceptionEnum.USER_NOT_LOGIN);
        // 直接返回 JSON 响应
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(Result.error(AuthenticationExceptionEnum.USER_NOT_LOGIN)));
        // 这里必须使用这种方式进行返回，因为SpringSecurity的默认报错是在过滤器链中的，在Controller之前的，
        // 而全局处理器的执行是在Controller之后的，所以这里必须使用这种方式进行返回
        // 拦截器中只有preHandle方法是在Controller之前的，因此不会触发 Controller 和全局异常处理器
    }
}