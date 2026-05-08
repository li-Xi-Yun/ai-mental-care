package org.lixiyun.server.ai.rag;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.rag.reader.FileReader;
import org.lixiyun.server.ai.rag.transformer.FileTransformer;
import org.lixiyun.server.ai.rag.writer.FileWriter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author lixiyun
 * @since 2026-04-29 20:17
 */
@Slf4j
@Component
public class RagStore {

    @Autowired
    @Qualifier("deepSeekChatModel")
    private ChatModel deepSeekChatModel;

    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel dashScopeChatModel;

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel ollamaChatModel;

    @Autowired
    @Qualifier("ollamaEmbeddingModel")
    private EmbeddingModel ollamaEmbeddingModel;

    @Autowired
    @Qualifier("dashscopeEmbeddingModel")
    private EmbeddingModel dashscopeEmbeddingModel;

    @Autowired
    private FileWriter fileWriter;

    @Autowired
    private MilvusUtil milvusUtil;


    // RAG文件处理专用线程池
    private static final int CORE_POOL_SIZE = 4;           // 核心线程数：根据CPU核数和IO密集型特点设置
    private static final int MAX_POOL_SIZE = 8;            // 最大线程数：应对突发流量
    private static final long KEEP_ALIVE_TIME = 60L;       // 空闲线程存活时间（秒）
    private static final int QUEUE_CAPACITY = 100;         // 任务队列容量：防止内存溢出
    
    private final ThreadPoolExecutor ragThreadPoolExecutor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：由调用线程执行，避免任务丢失
    );

    /**
     * 存储文件到向量数据库
     * <p>
     * RAG文件存储完整流程：
     * <ol>
     *     <li>{@link FileReader} - 粗粒度读取和切割文件</li>
     *     <li>{@link FileTransformer} - 细粒度语义分割</li>
     *     <li>{@link FileWriter} - 向量存储入库</li>
     * </ol>
     *
     * @param filePath 本地文件的绝对或相对路径，不能为空且文件必须存在
     * @param vectorData 文件元数据信息 {@link VectorData}
     */
    public void storeFileToVector(String filePath, VectorData vectorData, Consumer<Long> interruptedHandler) {
        FileReader fileReader = new FileReader();
        FileTransformer fileTransformer = FileTransformer.builder().chatModel(dashScopeChatModel).build();

        fileReader.readLocalFile(filePath, vectorData, documents -> {
            fileTransformer.transformDocuments(documents, interruptedHandler, item -> {
                fileWriter.writeDocuments(item, dashscopeEmbeddingModel);
            });
        });
    }

    /**
     * 异步存储文件到向量数据库
     * <p>
     * 使用专用线程池异步执行RAG文件处理流程，避免阻塞主线程。
     * 适用于批量文件导入、后台任务等场景。
     * <p>
     * 需自动处理异常
     * <p>同时，需要自行在上层调用 {@link RagInterruptManager} 中的 clearInterruptFlag 方法进行数据清除</p>
     * @param filePath 本地文件的绝对或相对路径，不能为空且文件必须存在
     * @param vectorData 文件元数据信息 {@link VectorData}
     * @param interruptedHandler 中断处理函数，任务中断时需要执行的功能，{@link Consumer}
     * @return CompletableFuture 对象，可用于监听任务完成状态或获取异常信息 {@link CompletableFuture}
     */
    public CompletableFuture<Void> storeFileToVectorAsync(String filePath,
                                                          VectorData vectorData,
                                                          Consumer<Long> interruptedHandler) {
        if (vectorData == null || vectorData.getFileId() == null){
            throw new BusinessException(FileExceptionEnum.FILE_PARAMS_ERROR);
        }
        return CompletableFuture.runAsync(() -> {
            long fileId = vectorData.getFileId();
            try {
                // 清除可能存在的旧中断标识（防止重复任务干扰）
                RagInterruptManager.clearInterruptFlag(fileId);
                
                log.info("RAG异步任务开始处理文件: {}，文件ID: {}", filePath, fileId);
                storeFileToVector(filePath, vectorData, interruptedHandler);
                log.info("RAG异步任务完成处理文件: {}", filePath);
            } catch (Throwable e) {
                log.error("RAG异步任务处理文件失败: {}", filePath, e);
                throw e;
            }
        }, ragThreadPoolExecutor);
    }

    /**
     * 恢复向量删除元数据信息
     * <p>
     * 将指定文件的所有向量数据的deleted字段设置为0（未删除状态），
     * 用于文件恢复场景。当文件被重新上传或从删除状态恢复时调用此方法。
     * <p>
     * 底层通过MilvusUtil工具类执行查询和批量Upsert操作，
     * 自动处理数据库和集合配置信息。
     *
     * @param fileId 文件ID {@link Long}
     * @throws BusinessException 当更新操作失败时抛出 {@link BusinessException}
     */
    public void restoreVectorDeletedMetadata(Long fileId) {
        milvusUtil.batchUpdateDeleted(fileId, DeleteConstant.DELETE_FLAG_NO);
    }

    /**
     * 添加向量删除元数据信息
     * <p>
     * 将指定文件的所有向量数据的deleted字段设置为1（已删除状态），
     * 用于文件删除场景。当文件被逻辑删除时调用此方法标记向量数据为已删除。
     * <p>
     * 底层通过MilvusUtil工具类执行查询和批量Upsert操作，
     * 自动处理数据库和集合配置信息。
     *
     * @param fileId 文件ID {@link Long}
     * @throws BusinessException 当更新操作失败时抛出 {@link BusinessException}
     */
    public void addVectorDeletedMetadata(Long fileId) {
        milvusUtil.batchUpdateDeleted(fileId, DeleteConstant.DELETE_FLAG_YES);
    }


    /**
     * 销毁线程池资源
     * <p>
     * 在Spring容器关闭时优雅地关闭线程池，确保所有任务执行完毕。
     */
    @PreDestroy
    public void destroy() {
        log.info("开始关闭RAG文件处理线程池...");
        ragThreadPoolExecutor.shutdown();
        try {
            if (!ragThreadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("线程池未能在30秒内正常关闭，强制关闭");
                ragThreadPoolExecutor.shutdownNow();
                if (!ragThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("线程池强制关闭失败");
                }
            }
            log.info("RAG文件处理线程池已关闭");
        } catch (InterruptedException e) {
            log.error("线程池关闭过程被中断", e);
            ragThreadPoolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


}
