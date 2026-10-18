package org.lixiyun.server.ai.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置类
 * <p>
 * 说明：本配置原持有 {@code @Primary primaryEmbeddingModel}，将向量化硬编码为
 * spring-ai 自动配置的 ollamaEmbeddingModel Bean。
 * <p>
 * 自 2026-10-18 起，知识库检索的向量化统一收敛到 {@link EmbeddingModelResolver}：
 * 每个检索节点（symptomKnowledgeRepositoryNode / interventionPlanRepositoryNode /
 * diagnosisStandardRepositoryNode）从 {@code ai_node_config} 读取各自独立的向量模型配置，
 * 由解析器运行期动态构建（Ollama / DashScope）并带独立重试，不再依赖固定 Bean 与 {@code @Primary}。
 * <p>
 * 故本类仅保留空壳配置，防止历史 Bean 命名冲突；所有 {@code @Autowired EmbeddingModel}
 * 注入点需显式改走
 *
 * @author lixiyun
 * @since 2026-05-06
 */
@Slf4j
@Configuration
@Deprecated
public class VectorStoreConfig {

    // 移除 @Bean @Primary primaryEmbeddingModel —— 向量模型统一由 EmbeddingModelResolver 动态解析
}
