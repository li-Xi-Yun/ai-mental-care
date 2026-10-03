package org.lixiyun.server.infrastructure.interaction.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输出上下文——贯穿整个管道执行过程的状态池
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>fluxStore</b>：存储各节点产出的 Flux 流，key 为节点产出标识（如 "model_output"）</li>
 *   <li><b>dataStore</b>：存储非流式数据（诊断结果、意图标签、历史消息等），key 为数据标识</li>
 *   <li><b>outputTypes</b>：前端请求的输出模式集合，各节点据此决定是否附加推送副作用</li>
 * </ul>
 *
 * <p>数据来源（在时间轮触发时由编排器装配到 dataStore）：
 * <ul>
 *   <li>"temporaryMessages" — 消息表（ConversationHistoryMessagesStorage）</li>
 *   <li>"historyMessages" — Redis 缓存（ConversationCacheManager）</li>
 *   <li>"emotionAnalysis" — Redis 缓存</li>
 *   <li>"diagnosisAnalysis" — Redis 缓存</li>
 *   <li>"compressedSummary" — Redis 缓存</li>
 *   <li>"aiNodeConfig" — AiNodeConfigManager</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutputContext {

    private Long userId;
    private Long conversationId;

    /**
     * 前端请求的输出模式集合
     *
     * <p>各节点根据此字段判断是否附加对应的推送副作用：
     * <pre>
     * if (context.hasOutputType(OutputDataType.TEXT)) {
     *     textFlux = textFlux.doOnNext(chunk -&gt; webSocketManager.sendTextStream(...));
     * }
     * </pre>
     */
    private Set<OutputDataType> outputTypes;

    // ==================== 流式数据池 ====================

    /**
     * Flux 数据池——存储各节点产出的 Flux 流
     *
     * <p>所有存入的 Flux 必须先调用 {@code .cache()}，原因：
     * 多个下游节点可能订阅同一个 Flux，不 cache 会导致数据源被重复触发</p>
     */
    private final ConcurrentHashMap<String, Flux<?>> fluxStore = new ConcurrentHashMap<>();

    // ==================== 非流式数据池 ====================

    /**
     * 非流式数据池——存储其他类型、格式的数据
     */
    private final ConcurrentHashMap<String, Object> dataStore = new ConcurrentHashMap<>();

    // ==================== 便捷方法 ====================

    /**
     * 便捷方法：判断是否包含指定输出模式
     *
     * @param type 输出数据类型
     * @return true 如果 outputTypes 中包含该类型
     */
    public boolean hasOutputType(OutputDataType type) {
        return outputTypes != null && outputTypes.contains(type);
    }

    // ==================== Flux 存取 ====================

    /**
     * 存入 Flux 流
     *
     * <p>存入时对 flux 调用 .cache()，防止多订阅者重复触发数据源</p>
     *
     * @param key  产出标识（如 "model_output", "tts_output"）
     * @param flux 要存入的 Flux 流
     */
    public void putFlux(String key, Flux<?> flux) {
        Flux<?> cachedFlux = flux.cache();
        fluxStore.put(key, cachedFlux);
    }

    /**
     * 获取 Flux 流并按元素类型安全转换
     *
     * @param key         产出标识
     * @param elementType 期望的流元素类型
     * @param <T>         元素类型
     * @return 强转后的 Flux（key 不存在时返回空 Flux）
     */
    @SuppressWarnings("unchecked")
    public <T> Flux<T> getFlux(String key, Class<T> elementType) {
        Flux<?> flux = fluxStore.get(key);
        if (flux == null) {
            return Flux.empty();
        }
        return (Flux<T>) flux;
    }

    // ==================== 非流式数据存取 ====================

    /**
     * 存入任意类型数据
     *
     * @param key   数据标识（如 "temporaryMessages", "emotionAnalysis"）
     * @param value 数据值
     */
    public void putData(String key, Object value) {
        dataStore.put(key, value);
    }

    /**
     * 获取数据并按类型强转
     *
     * @param key  数据标识
     * @param type 期望的数据类型
     * @param <T>  数据类型
     * @return 强转后的数据（key 不存在时返回 null）
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = dataStore.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    // ==================== 供 Pipeline 使用 ====================

    /**
     * 获取 fluxStore 中所有的 Flux——供 Pipeline 统一订阅触发
     *
     * @return fluxStore 中所有 Flux 的快照集合
     */
    public Collection<Flux<?>> getAllFluxes() {
        return new ArrayList<>(fluxStore.values());
    }
}