package org.lixiyun.common.web.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * 获取请求头国际化信息
 *
 * @author lixiyun
 */
public class I18nLocaleResolver implements LocaleResolver {

    @Override
    public Locale resolveLocale(HttpServletRequest httpServletRequest) {
        // 每次 HTTP 请求处理的早期阶段都会被执行一次
        String language = httpServletRequest.getHeader("content-language");
        Locale locale = Locale.getDefault();
        if (language != null && language.length() > 0) {
            String[] split = language.split("_");
            locale = new Locale(split[0], split[1]);
        }
        return locale;
    }

    @Override
    public void setLocale(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Locale locale) {
        // setLocale 方法是空实现，表示这个解析器不支持在运行时更改用户的语言环境，只能通过请求头来确定

    }

    // Client Request
    //    ↓
    //Filter (过滤器) - 最早执行
    //    ↓
    //DispatcherServlet
    //    ↓
    //LocaleResolver (I18nLocaleResolver.resolveLocale())
    //    ↓
    //HandlerInterceptor (拦截器)
    //    ↓
    //Controller Method
}
