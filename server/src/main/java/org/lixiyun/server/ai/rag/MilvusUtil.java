package org.lixiyun.server.ai.rag;

import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.server.config.properties.MilvusProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Milvus向量数据库通用工具类
 * <p>
 * 封装Milvus原生SDK的常用操作，自动处理数据库和集合信息，
 * 使得所有使用原生数据库的操作不需要手动添加数据库与集合信息。
 * <p>
 * 主要功能：
 * <ul>
 *     <li>自动注入数据库和集合配置（通过{@link MilvusProperties}）</li>
 *     <li>封装查询、插入、更新、删除等常用操作</li>
 *     <li>提供Builder模式简化请求构建</li>
 *     <li>支持分页查询、统计数量、存在性检查等高级功能</li>
 *     <li>支持根据ID列表、过滤表达式、元数据等多种方式删除数据</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 1. 简单查询
 * QueryResp resp = milvusUtil.query("file_id == 123", Arrays.asList("id", "content"));
 * 
 * // 2. 分页查询
 * QueryResp pageResp = milvusUtil.queryWithPagination(
 *     "file_id == 123", 
 *     Arrays.asList("id", "content"), 
 *     0,  // offset
 *     10  // limit
 * );
 * 
 * // 3. 统计数量
 * long count = milvusUtil.count("file_id == 123 && deleted == 0");
 * 
 * // 4. 插入数据
 * List<JsonObject> data = ...;
 * InsertResp insertResp = milvusUtil.insert(data);
 * 
 * // 5. 部分更新（Upsert）
 * List<JsonObject> updateData = ...;
 * UpsertResp upsertResp = milvusUtil.upsert(updateData, true);  // true表示部分更新
 * 
 * // 6. 删除数据 - 根据过滤表达式
 * DeleteResp deleteResp = milvusUtil.delete("file_id == 123");
 * 
 * // 7. 删除数据 - 根据ID列表
 * List<String> ids = Arrays.asList("id1", "id2", "id3");
 * DeleteResp deleteByIdsResp = milvusUtil.deleteByIds(ids);
 * 
 * // 8. 删除数据 - 根据元数据
 * DeleteResp deleteByMetaResp = milvusUtil.deleteByMetadata("file_id", 123);
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-05-06 15:39
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusUtil {

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties milvusProperties;

    // 更新操作时，每批写入数量
    private static final int BATCH_SIZE = 500;

    /**
     * 获取配置的集合名称
     *
     * @return 集合名称
     */
    public String getCollectionName() {
        return milvusProperties.getCollectionName();
    }

    /**
     * 获取配置的数据库名称
     *
     * @return 数据库名称
     */
    public String getDatabaseName() {
        return milvusProperties.getDatabaseName();
    }

    /**
     * 构建查询请求
     * <p>
     * 自动设置集合名称，简化查询请求的构建过程。
     *
     * @param filterExpression 过滤表达式
     * @param outputFields 输出字段列表
     * @return QueryReq对象
     */
    public QueryReq buildQueryReq(String filterExpression, List<String> outputFields) {
        return QueryReq.builder()
                .collectionName(getCollectionName())
                .filter(filterExpression)
                .outputFields(outputFields != null ? outputFields : Collections.singletonList("id"))
                .build();
    }

    /**
     * 构建查询请求（仅查询ID）
     *
     * @param filterExpression 过滤表达式
     * @return QueryReq对象
     */
    public QueryReq buildQueryIdReq(String filterExpression) {
        return buildQueryReq(filterExpression, Collections.singletonList("id"));
    }

    /**
     * 执行查询操作
     * <p>
     * 根据过滤条件查询向量数据，自动使用配置的集合名称。
     *
     * @param filterExpression 过滤表达式
     * @param outputFields 输出字段列表
     * @return 查询结果 {@link QueryResp}
     */
    public QueryResp query(String filterExpression, List<String> outputFields) {
        QueryReq req = buildQueryReq(filterExpression, outputFields);
        return executeQuery(req);
    }

    /**
     * 执行查询操作（仅查询ID）
     *
     * @param filterExpression 过滤表达式
     * @return 查询结果 {@link QueryResp}
     */
    public QueryResp queryIds(String filterExpression) {
        return query(filterExpression, Collections.singletonList("id"));
    }

    /**
     * 执行分页查询操作
     * <p>
     * 支持分页参数，适用于大数据量查询场景。
     *
     * @param filterExpression 过滤表达式
     * @param outputFields 输出字段列表
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 查询结果 {@link QueryResp}
     */
    public QueryResp queryWithPagination(String filterExpression, List<String> outputFields,
                                         long offset, long limit) {
        QueryReq req = QueryReq.builder()
                .collectionName(getCollectionName())
                .filter(filterExpression)
                .outputFields(outputFields)
                .offset(offset)
                .limit(limit)
                .build();
        return executeQuery(req);
    }

    /**
     * 执行查询请求
     *
     * @param req 查询请求对象 {@link QueryReq}
     * @return 查询结果 {@link QueryResp}
     */
    public QueryResp executeQuery(QueryReq req) {
        log.debug("执行Milvus查询操作，过滤条件：{}", req.getFilter());
        return milvusClient.query(req);
    }

    /**
     * 构建插入请求
     * <p>
     * 自动设置集合名称，简化插入请求的构建过程。
     *
     * @param data 要插入的数据列表
     * @return InsertReq对象
     */
    public InsertReq buildInsertReq(List<JsonObject> data) {
        return InsertReq.builder()
                .collectionName(getCollectionName())
                .data(data)
                .build();
    }

    /**
     * 执行插入操作
     * <p>
     * 将向量数据插入到Milvus集合中，自动使用配置的集合名称。
     *
     * @param data 要插入的数据列表
     * @return 插入结果 {@link InsertResp}
     */
    public InsertResp insert(List<JsonObject> data) {
        InsertReq req = buildInsertReq(data);
        return executeInsert(req);
    }

    /**
     * 执行插入请求
     *
     * @param req 插入请求对象 {@link InsertReq}
     * @return 插入结果 {@link InsertResp}
     */
    public InsertResp executeInsert(InsertReq req) {
        log.debug("执行Milvus插入操作，数据量：{}", req.getData() != null ? req.getData().size() : 0);
        return milvusClient.insert(req);
    }

    /**
     * 构建Upsert请求
     * <p>
     * 自动设置集合名称，支持部分更新模式。
     *
     * @param data 要Upsert的数据列表
     * @param partialUpdate 是否为部分更新（true-仅更新指定字段，false-覆盖全部字段）
     * @return UpsertReq对象
     */
    public UpsertReq buildUpsertReq(List<JsonObject> data, boolean partialUpdate) {
        return UpsertReq.builder()
                .collectionName(getCollectionName())
                .data(data)
                .partialUpdate(partialUpdate)
                .build();
    }

    /**
     * 执行Upsert操作
     * <p>
     * Upsert操作：数据存在则更新，不存在则插入。
     * 自动使用配置的集合名称。
     *
     * @param data 要Upsert的数据列表
     * @param partialUpdate 是否为部分更新
     * @return Upsert结果 {@link UpsertResp}
     */
    public UpsertResp upsert(List<JsonObject> data, boolean partialUpdate) {
        UpsertReq req = buildUpsertReq(data, partialUpdate);
        return executeUpsert(req);
    }

    /**
     * 执行Upsert请求
     *
     * @param req Upsert请求对象 {@link UpsertReq}
     * @return Upsert结果 {@link UpsertResp}
     */
    public UpsertResp executeUpsert(UpsertReq req) {
        log.debug("执行Milvus Upsert操作，数据量：{}", req.getData() != null ? req.getData().size() : 0);
        return milvusClient.upsert(req);
    }

    /**
     * 构建删除请求
     * <p>
     * 自动设置集合名称，简化删除请求的构建过程。
     *
     * @param filterExpression 过滤表达式
     * @return DeleteReq对象
     */
    public DeleteReq buildDeleteReq(String filterExpression) {
        return DeleteReq.builder()
                .collectionName(getCollectionName())
                .filter(filterExpression)
                .build();
    }

    /**
     * 执行删除操作
     * <p>
     * 根据过滤条件删除向量数据，自动使用配置的集合名称。
     *
     * @param filterExpression 过滤表达式
     * @return 删除结果 {@link DeleteResp}
     */
    public DeleteResp delete(String filterExpression) {
        DeleteReq req = buildDeleteReq(filterExpression);
        return executeDelete(req);
    }

    /**
     * 执行删除请求
     *
     * @param req 删除请求对象 {@link DeleteReq}
     * @return 删除结果 {@link DeleteResp}
     */
    public DeleteResp executeDelete(DeleteReq req) {
        log.debug("执行Milvus删除操作，过滤条件：{}", req.getFilter());
        return milvusClient.delete(req);
    }

    /**
     * 查询并统计数量
     * <p>
     * 根据过滤条件查询数据并返回匹配的记录数量。
     *
     * @param filterExpression 过滤表达式
     * @return 匹配的记录数量
     */
    public long count(String filterExpression) {
        QueryResp resp = queryIds(filterExpression);
        return resp.getQueryResults() != null ? resp.getQueryResults().size() : 0;
    }

    /**
     * 检查数据是否存在
     * <p>
     * 根据过滤条件检查是否存在匹配的向量数据。
     *
     * @param filterExpression 过滤表达式
     * @return true-存在匹配数据，false-不存在
     */
    public boolean exists(String filterExpression) {
        return count(filterExpression) > 0;
    }

    /**
     * 根据文档ID列表删除向量数据
     * <p>
     * 使用Milvus原生SDK根据主键ID列表批量删除向量数据。
     *
     * @param idList 文档ID列表，不能为空 {@link List}
     * @return 删除结果 {@link DeleteResp}
     */
    public DeleteResp deleteByIds(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            log.warn("删除操作：文档ID列表为空，跳过删除");
            return null;
        }

        log.info("开始根据ID删除 {} 个文档", idList.size());
        
        // 构建过滤表达式: id in ['id1', 'id2', ...]
        String idListStr = idList.stream()
                .map(id -> "'" + id.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        String filterExpression = "id in [" + idListStr + "]";
        
        DeleteReq req = DeleteReq.builder()
                .collectionName(getCollectionName())
                .filter(filterExpression)
                .build();
        
        DeleteResp resp = milvusClient.delete(req);
        log.info("成功根据ID删除 {} 个文档", idList.size());
        return resp;
    }

    /**
     * 根据过滤表达式删除向量数据（支持元数据过滤）
     * <p>
     * 使用Milvus原生SDK根据过滤表达式删除向量数据。
     * 过滤表达式支持Milvus的所有查询语法。
     *
     * @param filterExpression 过滤表达式，用于指定要删除的文档条件，不能为空 {@link String}
     * @return 删除结果 {@link DeleteResp}
     */
    public DeleteResp deleteByFilter(String filterExpression) {
        if (filterExpression == null || filterExpression.trim().isEmpty()) {
            log.warn("删除操作：过滤表达式为空，跳过删除");
            return null;
        }

        log.info("开始根据过滤表达式删除文档: {}", filterExpression);
        
        DeleteReq req = DeleteReq.builder()
                .collectionName(getCollectionName())
                .filter(filterExpression)
                .build();
        
        DeleteResp resp = milvusClient.delete(req);
        log.info("成功根据过滤表达式删除文档");
        return resp;
    }

    /**
     * 根据元数据键值对删除向量数据
     * <p>
     * 此方法构建一个简单的相等条件过滤器来删除具有指定元数据的文档。
     * 适用于根据单个元数据字段进行精确匹配删除的场景。
     *
     * @param metadataKey 元数据键名，不能为空
     * @param metadataValue 元数据值，不能为空
     * @return 删除结果 {@link DeleteResp}
     */
    public DeleteResp deleteByMetadata(String metadataKey, Object metadataValue) {
        if (metadataKey == null || metadataKey.trim().isEmpty()) {
            log.warn("删除操作：元数据键名为空，跳过删除");
            return null;
        }
        if (metadataValue == null) {
            log.warn("删除操作：元数据值为空，跳过删除");
            return null;
        }

        // 构建简单的相等过滤表达式: key == value
        String filterExpression = String.format("%s == '%s'", metadataKey, metadataValue.toString().replace("'", "\\'"));

        log.info("开始根据元数据删除文档: {} = {}", metadataKey, metadataValue);
        DeleteResp resp = deleteByFilter(filterExpression);
        log.info("成功根据元数据删除文档: {} = {}", metadataKey, metadataValue);
        return resp;
    }

    /**
     * 通用：批量更新向量的deleted字段
     * <p>
     * 使用MilvusUtil工具类简化Milvus操作，自动处理集合名称配置。
     * 通过查询-构建-批量Upsert的方式实现部分字段更新。
     *
     * @param fileId 文件ID
     * @param targetDeleted 目标删除状态
     */
    public void batchUpdateDeleted(Long fileId, int targetDeleted) {
        if (fileId == null) {
            log.warn("批量更新deleted字段：文件ID为空，跳过操作");
            return;
        }

        log.info("开始批量更新向量deleted字段，文件ID：{}，目标状态：{}", fileId, targetDeleted);

        String filter = "file_id == " + fileId +
                (targetDeleted == DeleteConstant.DELETE_FLAG_YES ? " && deleted == " + DeleteConstant.DELETE_FLAG_NO : " && deleted == " + DeleteConstant.DELETE_FLAG_YES);

        try {
            QueryResp resp = queryIds(filter);
            List<QueryResp.QueryResult> results = resp.getQueryResults();

            if (results == null || results.isEmpty()) {
                log.debug("未找到需要更新的向量数据，文件ID：{}", fileId);
                return;
            }

            log.info("找到 {} 条需要更新的向量数据，文件ID：{}", results.size(), fileId);

            List<JsonObject> updateData = results.stream()
                    .map(r -> {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("id", ((Number) r.getEntity().get("id")).longValue());
                        obj.addProperty("deleted", targetDeleted);
                        return obj;
                    })
                    .collect(Collectors.toList());

            for (int i = 0; i < updateData.size(); i += BATCH_SIZE) {
                List<JsonObject> batch = updateData.subList(i, Math.min(i + BATCH_SIZE, updateData.size()));
                upsert(batch, true);
            }

            log.info("成功批量更新向量deleted字段，文件ID：{}，更新数量：{}", fileId, updateData.size());

        } catch (Exception e) {
            log.error("批量更新向量deleted字段失败，文件ID：{}", fileId, e);
            throw new BusinessException(FileExceptionEnum.FILE_VECTOR_UPDATE_ERROR);
        }
    }
}
