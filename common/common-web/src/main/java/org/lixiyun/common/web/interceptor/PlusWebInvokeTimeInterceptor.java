package org.lixiyun.common.web.interceptor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.MapUtil;
import com.alibaba.ttl.TransmittableThreadLocal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.lixiyun.common.core.utils.StringUtils;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.common.web.filter.RepeatedlyRequestWrapper;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * web的调用时间统计拦截器
 * dev环境有效
 *
 * @author lixiyun
 * @since 3.3.0
 */
@Slf4j
public class PlusWebInvokeTimeInterceptor implements HandlerInterceptor {

    private final TransmittableThreadLocal<StopWatch> invokeTimeTL = new TransmittableThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String url = request.getMethod() + " " + request.getRequestURI();
        String domainName =  request.getServerName();
        log.info("域名信息：{}",domainName);
        // 打印请求参数
        if (isJsonRequest(request)) {
            String jsonParam = "";
            if (request instanceof RepeatedlyRequestWrapper) {
                BufferedReader reader = request.getReader();
                jsonParam = IoUtil.read(reader);
            }
            log.debug("[PLUS]开始请求 => URL[{}],参数类型[json],参数:[{}]", url, jsonParam);
        } else {
            Map<String, String[]> parameterMap = request.getParameterMap();
            if (MapUtil.isNotEmpty(parameterMap)) {
                String parameters = JsonUtils.toJsonString(parameterMap);
                log.debug("[PLUS]开始请求 => URL[{}],参数类型[param],参数:[{}]", url, parameters);
            } else {
                log.debug("[PLUS]开始请求 => URL[{}],无参数", url);
            }
        }
        StopWatch stopWatch = new StopWatch();
        invokeTimeTL.set(stopWatch);
        stopWatch.start();

        return true;
    }


    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String url = request.getMethod() + " " + request.getRequestURI();
        StopWatch stopWatch = invokeTimeTL.get();
        if (stopWatch != null) {
            stopWatch.stop();  // 停止计时
            // 打印执行时间
            log.debug("结束请求 => URL[{}], 执行时间: {} ms", url, stopWatch.getTime(TimeUnit.MILLISECONDS));
            invokeTimeTL.remove();  // 清理 ThreadLocal
        } else {
            log.debug("结束请求 => URL[{}]", request.getRequestURI());
        }
        invokeTimeTL.remove();
    }

    /**
     * 判断本次请求的数据类型是否为json
     *
     * @param request request
     * @return boolean
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null) {
            return StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE);
        }
        return false;
    }
}
