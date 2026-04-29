package org.lixiyun.server.ai.rag.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG文件存储流程第三步：向量存储器
 * <p>
 * 功能特性：
 * <ul>
 *     <li>接收第二步细粒度分割后的Document列表</li>
 *     <li>将Document转换为向量并存入向量数据库</li>
 *     <li>保留完整的元数据信息用于后续检索</li>
 *     <li>支持批量向量存储，提高入库效率</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-04-29 08:48
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileWriter {

    private final VectorStore vectorStore;

    /**
     * 批量存储Document到向量数据库
     * <p>
     * 将经过粗粒度切割和细粒度语义分割后的Document列表存入向量数据库。
     * VectorStore会自动处理文本向量化和索引构建过程。
     * 使用 @Retryable 实现自动重试，最多重试3次（退避策略）。
     *
     * @param documents 待存储的Document列表，包含完整的文本内容和元数据，不能为null或空
     * @throws BusinessException 当文档列表为空或向量存储失败时抛出业务异常
     */
    @Retryable(
            label = "vectorStore.writeDocuments",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void writeDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("RAG(FileWriter)-待存储的Document列表为空，跳过向量存储");
            return;
        }

        log.info("RAG(FileWriter)-开始存储 {} 个Document到向量数据库", documents.size());

        // 调用VectorStore进行批量向量存储
        vectorStore.accept(documents);
        log.info("RAG(FileWriter)-成功存储 {} 个Document到向量数据库", documents.size());
    }
}
