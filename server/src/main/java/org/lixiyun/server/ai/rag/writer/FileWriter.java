package org.lixiyun.server.ai.rag.writer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.service.vector.response.InsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.conversation.KnowledgeDocument;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.rag.milvus.MilvusUtil;
import org.lixiyun.server.mapper.KnowledgeDocumentMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG文件存储流程第三步：向量存储器
 * <p>
 * 功能特性：
 * <ul>
 *     <li>接收第二步细粒度分割后的VectorData列表</li>
 *     <li>将VectorData转换为向量并存入Milvus向量数据库</li>
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

    private final MilvusUtil milvusUtil;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    private static final Gson GSON = new Gson();


    /**
     * 批量存储VectorData到向量数据库
     * <p>
     * 1. 调用 EmbeddingModel 对文本内容进行向量化。
     * 2. 将向量结果存入 VectorData 对象。
     * 3. 组装 Milvus InsertParam 并执行批量插入。
     * 使用 @Retryable 实现自动重试，最多重试3次（退避策略）。
     *
     * @param documents 待存储的VectorData列表，包含完整的文本内容和元数据，不能为null或空
     * @param embeddingModel 向量嵌入模型，用于将文本转换为向量
     * @throws BusinessException 当文档列表为空或向量存储失败时抛出业务异常
     */
    @Retryable(
            label = "milvus.insert",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void writeDocuments(List<VectorData> documents, EmbeddingModel embeddingModel) {
        if (documents == null || documents.isEmpty()) {
            log.warn("RAG(FileWriter)-待存储的VectorData列表为空，跳过向量存储");
            return;
        }
        log.info("RAG(FileWriter)-开始处理 {} 个VectorData的向量化与入库", documents.size());
        validate(documents);

        // embedding
        List<String> texts = documents.stream()
                .map(VectorData::getContent)
                .collect(Collectors.toList());

        List<float[]> vectors = embeddingModel.embed(texts);

        if (vectors.size() != documents.size()) {
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        // 构建 JSON Row
        List<JsonObject> rows = new ArrayList<>(documents.size());
        List<KnowledgeDocument> knowledgeDocumentList = new ArrayList<>();

        for (int i = 0; i < documents.size(); i++) {
            VectorData doc = documents.get(i);
            float[] vector = vectors.get(i);
            JsonObject row = new JsonObject();
            row.add("vector", GSON.toJsonTree(vector));
            row.addProperty("file_id", doc.getFileId());
            row.addProperty("knowledge_type", doc.getKnowledgeType());
            row.addProperty("deleted", doc.getDeleted() != null ? doc.getDeleted() : DeleteConstant.DELETE_FLAG_NO);
            rows.add(row);

            KnowledgeDocument knowledgeDocument = KnowledgeDocument.builder()
                    .fileId(doc.getFileId())
                    .content(doc.getContent())
                    .chunkLevel1Idx(doc.getChunkLevel1Idx())
                    .chunkLevel2Idx(doc.getChunkLevel2Idx())
                    .build();

            knowledgeDocumentList.add(knowledgeDocument);
        }

        InsertResp resp = milvusUtil.insert(rows);

        log.info("RAG(FileWriter)-分片数据向量库写入成功: {} 条, 主键: {}", resp.getInsertCnt(), resp.getPrimaryKeys());

        List<Long> milvusSliceIdList = resp.getPrimaryKeys().stream().map(item -> (Long) item).toList();
        if (milvusSliceIdList.size() != documents.size()) {
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR.getCode(), "Milvus返回主键数量与文档不匹配");
        }

        for (int i = 0; i < documents.size(); i++) {
            Long milvusSliceId = milvusSliceIdList.get(i);
            KnowledgeDocument document = knowledgeDocumentList.get(i);
            document.setSliceId(milvusSliceId);
        }

        knowledgeDocumentMapper.insert(knowledgeDocumentList);

        log.info("RAG(FileWriter)-分片数据DB存储完成，共存储 {} 条数据", knowledgeDocumentList.size());
    }

    /**
     * 校验 VectorData 列表的完整性
     * <p>
     * 确保每个待入库的 VectorData 对象包含必要的元数据和内容，防止非法数据进入向量数据库。
     *
     * @param documents 待校验的 VectorData 列表
     * @throws BusinessException 当发现任何必填字段缺失时抛出参数校验异常
     */
    private void validate(List<VectorData> documents) {
        if (documents == null) {
            throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
        }

        for (VectorData doc : documents) {
            if (doc == null) {
                throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
            }

            if (doc.getFileId() == null) {
                throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
            }

            if (doc.getChunkLevel1Idx() == null) {
                throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
            }

            if (doc.getChunkLevel2Idx() == null) {
                throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
            }

            if (doc.getContent() == null || doc.getContent().isBlank()) {
                throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
            }
        }
    }
}
