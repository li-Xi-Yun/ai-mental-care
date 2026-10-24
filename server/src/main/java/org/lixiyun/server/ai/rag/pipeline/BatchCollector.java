package org.lixiyun.server.ai.rag.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 通用攒批收集器
 * <p>
 * 将单个元素累积到内部缓冲，达到批大小后通过 Consumer 批量消费。
 *
 * @param <T> 元素类型
 */
public class BatchCollector<T> {

    private final int batchSize;
    private final Consumer<List<T>> consumer;
    private final List<T> buffer = new ArrayList<>();

    public BatchCollector(int batchSize, Consumer<List<T>> consumer) {
        this.batchSize = batchSize;
        this.consumer = consumer;
    }

    /**
     * 添加单个元素，达到批大小则触发批量消费。
     *
     * @param item 待积累的元素
     */
    public void add(T item) {
        buffer.add(item);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    /**
     * 批量添加元素，达到批大小则触发批量消费。
     *
     * @param items 待积累的元素集合，不能为null
     */
    public void addAll(List<T> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        buffer.addAll(items);
        while (buffer.size() >= batchSize) {
            flush();
        }
    }

    /**
     * 将缓冲中的元素按 batchSize 分片批量消费并清空缓冲。
     * <p>单次 consumer 收到的列表大小 ≤ batchSize：原实现一次性发走全部缓冲，
     * 当 addAll 一次性灌入大于 batchSize 的元素时，下游会收到超限大列表
     * （如 DashScope 向量化单批上限 25，导致 "The input texts limit 25"）。
     * 现按 batchSize 分片消费，保证下游批量调用不越过单批上限。</p>
     */
    public void flush() {
        while (!buffer.isEmpty()) {
            int chunkSize = Math.min(batchSize, buffer.size());
            consumer.accept(new ArrayList<>(buffer.subList(0, chunkSize)));
            buffer.subList(0, chunkSize).clear();
        }
    }
}
