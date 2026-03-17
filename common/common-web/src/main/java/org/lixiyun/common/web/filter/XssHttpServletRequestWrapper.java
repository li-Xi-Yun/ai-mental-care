package org.lixiyun.common.web.filter;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.lixiyun.common.core.utils.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * XSS过滤处理
 *
 * @author ruoyi
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {
    /**
     * @param request
     */
    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    // 方法调用是按需执行的
    // String[] hobbies = request.getParameterValues("hobby"); // 此时执行 getParameterValues 方法
    // ServletInputStream stream = request.getInputStream(); // 此时执行 getInputStream 方法

    @Override
    public String[] getParameterValues(String name) {
        // 1. 调用父类方法获取原始参数值
        // 参数 name 是要获取的参数名称
        // 返回值是该参数的所有值组成的字符串数组
        String[] values = super.getParameterValues(name);
        // 2. 如果参数存在值
        if (values != null) {
            int length = values.length;
            String[] escapseValues = new String[length];
            // 3. 遍历每个参数值进行清理
            for (int i = 0; i < length; i++) {
                // 防xss攻击和过滤前后空格
                // 移除所有 HTML 标签，并保留其中的文本信息
                escapseValues[i] = HtmlUtil.cleanHtmlTag(values[i]).trim();
            }
            // 4. 返回清理后的参数值数组
            return escapseValues;
        }
        // 5. 如果参数不存在，返回原始结果
        return super.getParameterValues(name);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        // 非json类型，直接返回
        if (!isJsonRequest()) {
            return super.getInputStream();
        }

        // 为空，直接返回
        String json = StrUtil.str(IoUtil.readBytes(super.getInputStream(), false), StandardCharsets.UTF_8);
        if (StringUtils.isEmpty(json)) {
            return super.getInputStream();
        }

        // xss过滤
        json = HtmlUtil.cleanHtmlTag(json).trim();
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        final ByteArrayInputStream bis = IoUtil.toStream(jsonBytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return true;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public int available() throws IOException {
                return jsonBytes.length;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return bis.read();
            }
        };
    }

    /**
     * 是否是Json请求
     */
    public boolean isJsonRequest() {
        String header = super.getHeader(HttpHeaders.CONTENT_TYPE);
        return StringUtils.startsWithIgnoreCase(header, MediaType.APPLICATION_JSON_VALUE);
    }
}
