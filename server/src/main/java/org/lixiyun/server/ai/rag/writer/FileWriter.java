package org.lixiyun.server.ai.rag.writer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.service.vector.response.InsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.KnowledgeBaseExceptionEnum;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.knowledge.KnowledgeDocument;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.model.EmbeddingModelResolver;
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
 *
 * @author lixiyun
 * @since 2026-04-29 08:48
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileWriter {

    public static final String NODE_NAME = "fileWriterNode";

    private final MilvusUtil milvusUtil;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final EmbeddingModelResolver embeddingModelResolver;

    private static final Gson GSON = new Gson();

    /**
     * 批量存储VectorData到向量数据库。
     *
     * @param documents 待存储的VectorData列表
     */
    @Retryable(
            label = "milvus.insert",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void writeDocuments(List<VectorData> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("RAG(FileWriter)-待存储的VectorData列表为空，跳过向量存储");
            return;
        }
        log.info("RAG(FileWriter)-开始处理 {} 个VectorData的向量化与入库", documents.size());
        validate(documents);

        List<String> texts = documents.stream()
                .map(VectorData::getContent)
                .collect(Collectors.toList());

        List<float[]> vectors;
        try {
            EmbeddingModel embeddingModel = embeddingModelResolver.resolve(NODE_NAME);
            vectors = embeddingModel.embed(texts);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAG(FileWriter)-文本向量化失败，文件ID: {}", documents.getFirst().getFileId(), e);
            throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_EMBEDDING_ERROR);
        }

        if (vectors.size() != documents.size()) {
            throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_EMBEDDING_ERROR);
        }

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

            knowledgeDocumentList.add(KnowledgeDocument.builder()
                    .fileId(doc.getFileId())
                    .content(doc.getContent())
                    .chunkLevel1Idx(doc.getChunkLevel1Idx())
                    .chunkLevel2Idx(doc.getChunkLevel2Idx())
                    .build());
        }

        InsertResp response;
        try {
            response = milvusUtil.insert(rows);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAG(FileWriter)-Milvus向量数据写入失败", e);
            throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_VECTOR_STORE_ERROR);
        }

        log.info("RAG(FileWriter)-分片数据向量库写入成功: {} 条, 主键: {}", response.getInsertCnt(), response.getPrimaryKeys());
        List<Long> milvusSliceIdList = response.getPrimaryKeys().stream().map(item -> (Long) item).toList();
        if (milvusSliceIdList.size() != documents.size()) {
            throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_VECTOR_STORE_ERROR);
        }

        for (int i = 0; i < documents.size(); i++) {
            knowledgeDocumentList.get(i).setSliceId(milvusSliceIdList.get(i));
        }

        try {
            knowledgeDocumentMapper.insert(knowledgeDocumentList);
        } catch (Exception e) {
            log.error("RAG(FileWriter)-knowledge_document入库失败", e);
            throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_VECTOR_STORE_ERROR);
        }

        log.info("RAG(FileWriter)-分片数据DB存储完成，共存储 {} 条数据", knowledgeDocumentList.size());
    }

    private void validate(List<VectorData> documents) {
        if (documents == null) {
            throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
        }

        for (VectorData doc : documents) {
            if (doc == null || doc.getFileId() == null || doc.getChunkLevel1Idx() == null
                    || doc.getChunkLevel2Idx() == null || doc.getContent() == null || doc.getContent().isBlank()) {
                throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
            }
        }
    }
}
