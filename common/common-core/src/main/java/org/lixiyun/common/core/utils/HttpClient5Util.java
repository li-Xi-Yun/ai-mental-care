package org.lixiyun.common.core.utils;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.util.Map;

public class HttpClient5Util {

    // 连接池（提升并发性能）
    private static final PoolingHttpClientConnectionManager connManager;
    private static final CloseableHttpClient httpClient;

    static {
        connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(200); // 最大连接数
        connManager.setDefaultMaxPerRoute(50); // 单路由最大连接数

        // 全局超时配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(30)) // 响应超时
                .setConnectionRequestTimeout(Timeout.ofSeconds(5)) // 请求超时
                .build();

        httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    /**
     * 发送 GET 请求
     * @param url         基础URL（可含路径参数占位符 {param}）
     * @param pathParams 路径参数Map（替换占位符）
     * @param queryParams 查询参数Map
     */
    public static String doGet(String url, Map<String, String> pathParams, Map<String, String> queryParams) throws Exception {
        String finalUrl = buildUrl(url, pathParams, queryParams);
        HttpGet httpGet = new HttpGet(finalUrl);
        return executeRequest(httpGet);
    }

    /**
     * 发送 POST 请求（JSON格式）
     * @param url    请求URL
     * @param jsonBody JSON请求体
     */
    public static String doPostJson(String url, String jsonBody) throws Exception {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        return executeRequest(httpPost);
    }

    /**
     * 发送 PUT 请求（JSON格式）
     * @param url    请求URL
     * @param jsonBody JSON请求体
     */
    public static String doPutJson(String url, String jsonBody) throws Exception {
        HttpPut httpPut = new HttpPut(url);
        httpPut.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        httpPut.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        return executeRequest(httpPut);
    }

    /**
     * 发送 DELETE 请求
     * @param url 请求URL（可含路径参数）
     * @param pathParams 路径参数Map
     */
    public static String doDelete(String url, Map<String, String> pathParams) throws Exception {
        String finalUrl = buildUrl(url, pathParams, null);
        HttpDelete httpDelete = new HttpDelete(finalUrl);
        return executeRequest(httpDelete);
    }

    /**
     * 执行请求核心逻辑
      */
    private static String executeRequest(HttpUriRequestBase request) throws Exception {
        BasicHttpClientResponseHandler basicHttpClientResponseHandler = new BasicHttpClientResponseHandler();
        return httpClient.execute(request, basicHttpClientResponseHandler);
    }

    /**
     * 构建完整URL（处理路径参数 + 查询参数）
      */
    private static String buildUrl(String url, Map<String, String> pathParams, Map<String, String> queryParams) throws Exception {
        // 替换路径参数（如 /user/{id} -> /user/123）
        if (pathParams != null) {
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                url = url.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        // 添加查询参数
        URIBuilder uriBuilder = new URIBuilder(url);
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                uriBuilder.addParameter(entry.getKey(), entry.getValue());
            }
        }
        return uriBuilder.build().toString();
    }

    public static void returnError(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK); // 必须返回200
        response.getWriter().print("{\"Status\":\"verify not ok\", \"Message\":\"" + msg + "\"}");
    }
}