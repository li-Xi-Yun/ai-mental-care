package org.lixiyun.server.ai.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.rag.milvus.properties.MilvusProperties;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 向量模型解析器（节点级独立配置 + 动态加载）
 * <p>
 * 每个知识库检索节点（symptomKnowledgeRepositoryNode / interventionPlanRepositoryNode /
 * diagnosisStandardRepositoryNode）各自在 {@code ai_node_config} 中维护独立的向量模型配置，
 * 本解析器按 {@code nodeKey} 读取配置，运行期动态构建对应的 {@link EmbeddingModel}：
 * <ul>
 *   <li>Ollama（modelType=0）→ {@link OllamaEmbeddingModel}（复用全局 {@link OllamaApi} Bean）</li>
 *   <li>DashScope（modelType=2）→ {@link DashScopeEmbeddingModel}（自建 {@link DashScopeApi}，api-key 取自 yaml）</li>
 *   <li>其他 / 未配置 / 禁用 → 回退默认 Ollama（与现状行为一致）</li>
 * </ul>
 * 模型按 nodeKey 缓存，以配置 {@code version} 对账：后台保存 → version+1 → 自动重建，保存即生效、无需重启。
 * <p>
 * 向量化调用统一走 {@link #embedWithRetry(String, String)}，内置独立的指数退避重试
 * （读取该节点配置的 retryMaxAttempts / retryDelay / retryMultiplier），不复用 {@link BaseModel} 的重试逻辑。
 *
 * @author lixiyun
 * @since 2026-10-18
 */
@Slf4j
@Component
public class EmbeddingModelResolver {

    /** 节点配置缺失/禁用时的默认 Ollama 嵌入模型 */
    @Value("${ai-model-config.ollama.embedding-model:nomic-embed-text}")
    private String defaultOllamaEmbeddingModel;

    /** 节点配置缺失时的默认 DashScope 嵌入模型 */
    @Value("${ai-model-config.dashscope.embedding-model:text-embedding-v3}")
    private String defaultDashscopeEmbeddingModel;

    /** DashScope Api Key（与聊天模型共用 yaml 配置） */
    @Value("${ai-model-config.dashscope.api:}")
    private String dashscopeApiKey;

    private final AiNodeConfigManager aiNodeConfigManager;
    private final OllamaApi ollamaApi;
    private final MilvusProperties milvusProperties;
    private final ObservationRegistry observationRegistry;

    /** nodeKey → 缓存项（含构建时的配置版本，用于对账失效） */
    private final ConcurrentMap<String, CachedEntry> resolvedCache = new ConcurrentHashMap<>();

    public EmbeddingModelResolver(AiNodeConfigManager aiNodeConfigManager,
                                  OllamaApi ollamaApi,
                                  MilvusProperties milvusProperties,
                                  ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        this.aiNodeConfigManager = aiNodeConfigManager;
        this.ollamaApi = ollamaApi;
        this.milvusProperties = milvusProperties;
        this.observationRegistry = observationRegistryProvider.getIfUnique(() -> ObservationRegistry.NOOP);
    }

    /**
     * 按节点向量化文本，带独立的指数退避重试。
     * <p>重试参数读取该节点 ai_node_config 的 retryMaxAttempts / retryDelay / retryMultiplier；
     * 未配置时回退默认（3次 / 1000ms / 2x）。失败重试，耗尽后抛原始异常。</p>
     *
     * @param nodeKey 节点唯一标识（即检索节点自身的 NODE_NAME）
     * @param prompt  待向量化文本
     * @return 向量化结果
     */
    public float[] embedWithRetry(String nodeKey, String prompt) {
        AiNodeConfig config = aiNodeConfigManager.getConfigWithLoad(nodeKey);
        RetryTemplate retryTemplate = buildRetryTemplate(config);
        try {
            return retryTemplate.execute(ctx -> resolve(nodeKey).embed(prompt));
        } catch (org.springframework.retry.ExhaustedRetryException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("[向量模型解析器] 节点 {} 向量化重试耗尽，最终异常：{}", nodeKey, cause.getMessage(), cause);
            throw new IllegalStateException("[向量模型解析器] 节点 " + nodeKey + " 向量化重试耗尽", cause);
        }
    }

    /**
     * 按节点读取向量模型配置并（重新）解析出该节点的 EmbeddingModel。
     * <p>缓存失效策略：以 nodeKey → 配置 version 对账，version 变化时重建模型；
     * 未配置 / 禁用 / 不支持的类型回退默认 Ollama（与现状一致）。</p>
     *
     * @param nodeKey 节点唯一标识
     * @return 该节点当前应使用的 EmbeddingModel
     */
    public EmbeddingModel resolve(String nodeKey) {
        AiNodeConfig config = aiNodeConfigManager.getConfigWithNoLoad(nodeKey);
        if (config == null || !config.isEnabledFlag()) {
            log.warn("[向量模型解析器] 节点未配置或已禁用，使用默认 Ollama 向量模型，nodeKey={}", nodeKey);
            return buildOllamaEmbedding(defaultOllamaEmbeddingModel);
        }
        int version = config.getVersion() == null ? 0 : config.getVersion();
        return resolvedCache.compute(nodeKey, (k, old) -> {
            if (old != null && old.version == version) {
                return old;
            }
            EmbeddingModel model = buildFromConfig(config);
            log.info("[向量模型解析器] 命中配置节点，nodeKey={}，modelType={}，version={}",
                    nodeKey, ChatModelType.fromType(config.getModelType()), version);
            return new CachedEntry(version, model);
        }).model;
    }

    // ==================== 模型构建 ====================

    private EmbeddingModel buildFromConfig(AiNodeConfig config) {
        ChatModelType type = ChatModelType.fromType(config.getModelType());
        return switch (type) {
            case DASH_SCOPE -> buildDashScopeEmbedding(resolveModelName(config, type));
            default -> buildOllamaEmbedding(resolveModelName(config, type));
        };
    }

    private String resolveModelName(AiNodeConfig config, ChatModelType type) {
        return switch (type) {
            case DASH_SCOPE -> config.getDashscopeModelName() != null ? config.getDashscopeModelName() : defaultDashscopeEmbeddingModel;
            default -> config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaEmbeddingModel;
        };
    }

    private EmbeddingModel buildOllamaEmbedding(String modelName) {
        ModelManagementOptions managementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.NEVER)   // 不主动拉取模型，避免构建时依赖 Ollama 在线
                .build();
        return new OllamaEmbeddingModel(
                ollamaApi,
                OllamaEmbeddingOptions.builder()
                        .model(modelName)
                        .build(),
                observationRegistry,
                managementOptions
        );
    }

    private EmbeddingModel buildDashScopeEmbedding(String modelName) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .apiKey(dashscopeApiKey)
                .build();
        DashScopeEmbeddingOptions.Builder options = DashScopeEmbeddingOptions.builder()
                .model(modelName);
        // 目标维度取自 Milvus 集合配置（单一来源）；若未配置则不强制指定，由模型默认决定（同维度约束）
        Integer dimension = milvusProperties.getEmbeddingDimension();
        if (dimension != null && dimension > 0) {
            options.dimensions(dimension);
        }
        return new DashScopeEmbeddingModel(
                dashScopeApi,
                MetadataMode.EMBED,
                options.build(),
                noRetryTemplate(),   // 重试统一由 embedWithRetry 外部控制，禁用模型内部默认重试避免双重重试
                observationRegistry
        );
    }

    // ==================== 独立重试逻辑（不复用 BaseModel） ====================

    /**
     * 构建指数退避重试模板，参数读取该节点 ai_node_config。
     * 未配置时回退默认：maxAttempts=3、initial=1000ms、multiplier=2。
     */
    private RetryTemplate buildRetryTemplate(AiNodeConfig config) {
        int maxAttempts = (config != null && config.getRetryMaxAttempts() != null) ? config.getRetryMaxAttempts() : 3;
        long delay = (config != null && config.getRetryDelay() != null) ? config.getRetryDelay() : 1000;
        double multiplier = (config != null && config.getRetryMultiplier() != null) ? config.getRetryMultiplier() : 2.0;

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(delay);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(10000);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(maxAttempts, Map.of(Exception.class, true));

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOff);
        return template;
    }

    /** 不重试的 RetryTemplate（注入 DashScope 模型以禁用其内部默认重试，避免与外部重试叠加） */
    private RetryTemplate noRetryTemplate() {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new SimpleRetryPolicy(1));
        return template;
    }

    /** 缓存项：记录构建时的配置版本，用于对账失效 */
    private record CachedEntry(int version, EmbeddingModel model) {
    }
}
