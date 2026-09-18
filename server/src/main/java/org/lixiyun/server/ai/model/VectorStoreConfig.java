package org.lixiyun.server.ai.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 向量存储配置类
 * <p>
 * 解决多个 EmbeddingModel 存在时的自动配置冲突问题
 * 
 * @author lixiyun
 * @since 2026-05-06
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    /**
     * 指定用于向量存储的默认嵌入模型
     * <p>
     * 由于项目中同时存在 dashscopeEmbeddingModel 和 ollamaEmbeddingModel，
     * Spring AI 的 MilvusVectorStoreAutoConfiguration 无法确定使用哪个模型。
     * <p>
     * 通过将此 Bean 标记为 @Primary，确保在需要注入 EmbeddingModel 时优先使用此模型。
     * <p>
     * 根据配置文件 application.yaml 中的设置：
     * - spring.ai.dashscope.embedding.enabled: false (DashScope 嵌入已禁用)
     * - 因此选择 ollamaEmbeddingModel 作为默认的向量存储嵌入模型
     *
     * @param ollamaEmbeddingModel Ollama 嵌入模型
     * @return 标记为 Primary 的嵌入模型
     */
    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
        log.info("配置主嵌入模型为: ollamaEmbeddingModel");
        return ollamaEmbeddingModel;
    }
}
