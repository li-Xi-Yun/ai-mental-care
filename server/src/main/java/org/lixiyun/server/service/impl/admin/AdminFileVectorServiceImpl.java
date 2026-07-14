package org.lixiyun.server.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.entity.conversation.KnowledgeDocument;
import org.lixiyun.pojo.entity.file.InfraFile;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.pojo.vo.admin.file.FileVectorVO;
import org.lixiyun.server.ai.rag.MilvusUtil;
import org.lixiyun.server.ai.rag.RagInterruptManager;
import org.lixiyun.server.ai.rag.RagStore;
import org.lixiyun.server.mapper.InfraFileMapper;
import org.lixiyun.server.mapper.KnowledgeDocumentMapper;
import org.lixiyun.server.service.admin.AdminFileVectorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 文件向量管理服务实现类
 *
 * @author lixiyun
 * @since 2026-05-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFileVectorServiceImpl implements AdminFileVectorService {

    private final InfraFileMapper infraFileMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final RagStore ragStore;
    private final MilvusUtil milvusUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void loadFileVector(Long fileId, Integer knowledgeType) {
        log.info("开始文件向量加载，文件ID：{}", fileId);

        // 1. DB查询判断文件是否存在
        InfraFile infraFile = infraFileMapper.selectById(fileId);
        if (infraFile == null) {
            log.error("文件向量加载-文件不存在，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        // 2. 校验文件删除状态
        if (infraFile.deleteFlat()) {
            log.error("文件向量加载-文件已删除，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        // 3. 校验文件状态
        if (infraFile.isParsing() || infraFile.isParseCompleted()) {
            log.error("文件向量加载-文件状态不允许加载，当前状态：{}，文件ID：{}", infraFile.getStatus(), fileId);
            throw new BusinessException(FileExceptionEnum.FILE_PARAMS_ERROR);
        }

        // 4. 获取文件元数据
        String filePath = infraFile.getFileUrl();
        log.debug("文件路径：{}", filePath);

        // 5. DB修改文件状态为解析中
        int updateCount = infraFileMapper.update(null,
                new LambdaUpdateWrapper<InfraFile>()
                        .eq(InfraFile::getId, fileId)
                        .in(InfraFile::getStatus, InfraFile.STATUS_PENDING, InfraFile.STATUS_PARSE_FAILED)
                        .set(InfraFile::getStatus, InfraFile.STATUS_PARSING));
        if (updateCount == 0) {
            log.error("文件向量加载-更新文件状态失败，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_PARAMS_ERROR);
        }
        log.debug("文件状态已更新为解析中，文件ID：{}", fileId);

        // 6. 构建VectorData对象
        VectorData vectorData = VectorData.builder()
                .fileId(fileId)
                .knowledgeType(knowledgeType)
                .build();

        // 7. 异步向量加载
        Consumer<Long> interruptedHandler = this::handleInterrupt;

        CompletableFuture<Void> future = ragStore.storeFileToVectorAsync(filePath, vectorData, interruptedHandler);

        // 8. 添加消费者参数：向量加载完成后更新文件状态
        future.thenRun(() -> {
            if (RagInterruptManager.isInterrupted(fileId)){
                log.info("文件向量加载中断，结束RAG文件加载流程：{}", fileId);
                return;
            }
            log.info("文件向量加载完成，文件ID：{}", fileId);

            // 更新文件状态为解析完成
            infraFileMapper.update(null,
                    new LambdaUpdateWrapper<InfraFile>()
                            .eq(InfraFile::getId, fileId)
                            .eq(InfraFile::getStatus, InfraFile.STATUS_PARSING)  // 确保只有解析中的文件才能更新为完成
                            .set(InfraFile::getStatus, InfraFile.STATUS_PARSE_COMPLETED)
                            .set(InfraFile::getVectorStatus, InfraFile.VECTOR_STATUS_ENABLE)
            );
            log.debug("文件状态已更新为解析完成，文件ID：{}", fileId);
        }).exceptionally(ex -> {
            log.error("文件向量加载异步任务异常，文件ID：{}", fileId, ex);
            handleVectorLoadException(fileId, (Exception) ex);
            return null;
        }).whenComplete((result, throwable) -> {
            // 无论成功还是失败，都会清除中断标识
            RagInterruptManager.clearInterruptFlag(fileId);
            log.debug("文件向量加载任务结束，已清除中断标识，文件ID：{}", fileId);
        });
    }

    @Override
    public void deleteFileVector(Long fileId) {
        log.info("开始文件向量删除，文件ID：{}", fileId);

        // 1. DB查询判断文件是否存在
        InfraFile infraFile = infraFileMapper.selectById(fileId);
        if (infraFile == null) {
            log.error("文件向量删除-文件不存在，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        // 2. 校验文件删除状态
        if (infraFile.deleteFlat()) {
            log.error("文件向量删除-文件已删除，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        // 3. 校验文件状态
        if (!infraFile.isParseCompleted()) {
            log.error("文件向量删除-文件状态不允许删除，当前状态：{}，文件ID：{}", infraFile.getStatus(), fileId);
            throw new BusinessException(FileExceptionEnum.FILE_PARAMS_ERROR);
        }

        // 物理删除向量数据
        deleteVectorsByFileId(fileId);

        // 删除知识文档
        knowledgeDocumentMapper.delete(new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getFileId, fileId));

        // 更新文件状态为待解析
        infraFileMapper.update(null, new LambdaUpdateWrapper<InfraFile>()
                        .eq(InfraFile::getId, fileId)
                        .set(InfraFile::getStatus, InfraFile.STATUS_PENDING));
        log.info("文件向量删除完成，文件状态已重置为待解析，文件ID：{}", fileId);
    }

    @Override
    public PageResult<FileVectorVO> queryFileVector(Long fileId, Integer pageNum, Integer pageSize) {
        log.info("开始文件向量分页查询，文件ID：{}，页码：{}，每页数量：{}",
                fileId, pageNum, pageSize);

        // DB查询判断文件是否存在
        InfraFile infraFile = infraFileMapper.selectById(fileId);
        if (infraFile == null) {
            log.error("文件向量查询-文件不存在，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        // 校验文件删除状态
        if (infraFile.deleteFlat()) {
            log.error("文件向量查询-文件已删除，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        // 校验文件状态
        if (!infraFile.isParseCompleted()) {
            log.debug("文件向量查询-文件未解析完成，返回空分页结果，当前状态：{}，文件ID：{}", infraFile.getStatus(), fileId);
            return new PageResult<>(0L, Collections.emptyList());
        }

        // 根据文件ID分页查询向量集合
        Page<KnowledgeDocument> build = new PageQuery(pageSize, pageNum).build();
        Page<KnowledgeDocument> pageResult = knowledgeDocumentMapper.selectPage(build, new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getFileId, fileId));

        PageResult<FileVectorVO> result = PageResult.convert(pageResult, FileVectorVO.class);

        result.setRecords(result.getRecords()
                .stream()
                .sorted(Comparator.comparing(FileVectorVO::getChunkLevel1Idx)
                        .thenComparing(FileVectorVO::getChunkLevel2Idx)).toList()
        );

        log.info("文件向量分页查询完成，文件ID：{}，总记录数：{}，当前页记录数：{}",
                fileId, pageResult.getTotal(), pageResult.getRecords().size());
        return result;
    }

    @Override
    public void interruptFileVectorParsing(Long fileId) {
        log.info("开始文件向量解析中断，文件ID：{}", fileId);

        // 1. 在Redis中设置中断标识
        boolean interrupted = RagInterruptManager.setInterruptFlag(fileId);

        // 2. 判断是否成功设置中断标识
        if (!interrupted) {
            log.error("文件向量解析中断-设置中断标识失败，文件ID：{}", fileId);
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        log.info("文件向量解析中断成功，文件ID：{}", fileId);
    }

    /**
     * 根据文件ID物理删除向量数据
     * <p>从向量数据库中删除指定文件的所有向量数据，使用MilvusUtil工具类执行删除操作</p>
     *
     * @param fileId 文件ID
     */
    private void deleteVectorsByFileId(Long fileId) {
        log.info("开始物理删除向量数据，文件ID：{}", fileId);

        // 构建删除表达式：file_id == fileId
        String filterExpression = String.format("file_id == %d", fileId);

        try {
            // 使用MilvusUtil的删除方法
            milvusUtil.deleteByFilter(filterExpression);
            log.info("物理删除向量数据完成，文件ID：{}", fileId);

        } catch (Exception e) {
            log.error("物理删除向量数据失败，文件ID：{}", fileId, e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 处理向量加载异常
     * <p>当向量加载失败时，更新文件状态为解析失败，并存储异常信息</p>
     *
     * @param fileId 文件ID
     * @param exception 异常信息
     */
    private void handleVectorLoadException(Long fileId, Exception exception) {
        log.error("文件向量加载异常，文件ID：{}", fileId, exception);

        try {
            // 更新文件状态为解析失败，并存储异常信息
            String failReason = exception.getMessage();
            if (exception instanceof BusinessException businessException){
                failReason = businessException.getMsg();
            } else if (failReason != null && failReason.length() > 500) {
                failReason = failReason.substring(0, 500);
            }

            // 物理删除知识文档
            knowledgeDocumentMapper.delete(new LambdaUpdateWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getFileId, fileId));

            infraFileMapper.update(null,
                    new LambdaUpdateWrapper<InfraFile>()
                            .eq(InfraFile::getId, fileId)
                            .eq(InfraFile::getStatus, InfraFile.STATUS_PARSING)
                            .set(InfraFile::getStatus, InfraFile.STATUS_PARSE_FAILED)
                            .set(InfraFile::getFailReason, failReason));

            log.debug("文件状态已更新为解析失败，文件ID：{}", fileId);
        } catch (Exception e) {
            log.error("更新文件失败状态异常，文件ID：{}", fileId, e);
        }
    }

    /**
     * 处理中断请求
     * <p>根据文件ID，物理删除已加载的向量数据，并更新文件状态为待解析</p>
     *
     * @param fileId 文件ID
     */
    private void handleInterrupt(Long fileId) {
        log.info("处理向量加载中断请求，文件ID：{}", fileId);

        try {
            // 物理删除已加载的向量数据
            deleteVectorsByFileId(fileId);

            // 物理删除知识文档
            knowledgeDocumentMapper.delete(new LambdaUpdateWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getFileId, fileId));

            // 更新文件状态为待解析
            infraFileMapper.update(null, new LambdaUpdateWrapper<InfraFile>()
                            .eq(InfraFile::getId, fileId)
                            .eq(InfraFile::getStatus, InfraFile.STATUS_PARSING)
                            .set(InfraFile::getStatus, InfraFile.STATUS_PENDING));

            log.info("向量加载中断处理完成，文件ID：{}", fileId);
        } catch (Exception e) {
            log.error("处理向量加载中断请求失败，文件ID：{}", fileId, e);
        }
    }
}
