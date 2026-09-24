package org.lixiyun.common.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.concurrent.TimeUnit;

/**
 * Spring AI 全局 HTTP 远程调用配置
 * <p>
 * 为所有 Spring AI 模型（DeepSeek、DashScope、Ollama 等）的 RestClient 底层
 * 统一配置 Apache HttpClient 5 连接池，替代默认的 JDK HttpClient。
 * </p>
 *
 * <h3>架构说明</h3>
 * <pre>
 * 你的业务代码 (AiModelThread-线程)
 *     │  @Async 异步调用
 *     ▼
 * Spring AI ChatModel.call()
 *     │
 *     ▼
 * RestClient (同步阻塞)
 *     │
 *     ▼
 * HttpComponentsClientHttpRequestFactory
 *     │
 *     ▼
 * PoolingHttpClientConnectionManager (连接池)
 *     │  ┌─────────────────────────┐
 *     │  │ 连接1 → DeepSeek API    │
 *     │  │ 连接2 → DashScope API   │
 *     │  │ 连接3 → Ollama API      │
 *     │  │ ...                     │
 *     │  │ 连接N → (空闲等待复用)    │
 *     │  └─────────────────────────┘
 *     ▼
 * 远程 AI 模型 API
 * </pre>
 *
 * <h3>线程模型</h3>
 * RestClient 的同步调用直接阻塞调用线程（此处即 aiModelThreadPoolTaskExecutor 中的线程），
 * 不创建额外线程。底层 Apache HttpClient 5 的连接池也不创建业务线程，
 * I/O 事件由 HttpClient 内部的少量 daemon 线程处理。
 *
 * @author lixiyun
 * @since 2026-09-23
 */
@Slf4j
@Configuration
@ConditionalOnClass(HttpComponentsClientHttpRequestFactory.class)
public class AiHttpClientConfig {

    /**
     * 连接池管理器
     * <ul>
     *     <li>maxTotal=200：全局最大连接数</li>
     *     <li>defaultMaxPerRoute=50：每个目标 host 的最大连接数</li>
     *     <li>connectTimeout=30s：建立 TCP 连接的超时时间</li>
     *     <li>socketTimeout=120s：等待数据的超时时间（AI 模型响应可能很长）</li>
     *     <li>connectionRequestTimeout=10s：从连接池获取连接的超时时间</li>
     *     <li>validateAfterInactivity=5s：空闲连接超过 5s 后验证可用性</li>
     * </ul>
     */
    @Bean(destroyMethod = "close")
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(50);

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(30, TimeUnit.SECONDS)
                .setSocketTimeout(120, TimeUnit.SECONDS)
                .setValidateAfterInactivity(TimeValue.ofMilliseconds(TimeUnit.SECONDS.toMillis(5)))
                .build();
        cm.setDefaultConnectionConfig(connectionConfig);

        log.info("HTTP连接池已初始化: maxTotal=200, maxPerRoute=50, connectTimeout=30s, socketTimeout=120s");
        return cm;
    }

    /**
     * Apache HttpClient 5 客户端实例
     * <p>
     * 使用连接池管理器，禁用自动重试（重试由上层 {@code RetryTemplate} 控制），
     * 禁用 Cookie 管理（AI API 不需要），禁用默认的 User-Agent。
     * </p>
     */
    @Bean(destroyMethod = "close")
    public CloseableHttpClient closeableHttpClient(PoolingHttpClientConnectionManager connectionManager) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(10, TimeUnit.SECONDS)
                .build();

        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .disableCookieManagement()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(60))
                .build();
    }

    /**
     * Spring RestClient 与 Apache HttpClient 5 的适配桥接
     */
    @Bean
    public HttpComponentsClientHttpRequestFactory httpComponentsClientHttpRequestFactory(
            CloseableHttpClient closeableHttpClient) {
        return new HttpComponentsClientHttpRequestFactory(closeableHttpClient);
    }

    /**
     * RestClient 全局定制器
     * <p>
     * Spring Boot 会自动发现此 Bean 并应用到所有 RestClient.Builder 实例，
     * 包括 Spring AI 内部创建的 RestClient，从而统一替换底层 HTTP 客户端为连接池版本。
     * </p>
     */
    @Bean
    public RestClientCustomizer aiRestClientCustomizer(
            HttpComponentsClientHttpRequestFactory requestFactory) {
        return restClientBuilder -> {
            restClientBuilder.requestFactory(requestFactory);
            log.debug("RestClient 已配置为使用 Apache HttpClient 5 连接池");
        };
    }
}