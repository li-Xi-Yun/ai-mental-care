package org.lixiyun.server.ai.rag.reader.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.rag.RagInterruptManager;
import org.lixiyun.server.ai.rag.pipeline.BatchCollector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * RAG 文件读取策略抽象基类。
 * <p>
 * 承载所有流派共用的公共逻辑：路径校验、文件大小上限、扩展名提取、
 * 统一流生命周期（打开/关闭）、BatchCollector 攒批与收尾 flush、中断检查。
 * 实现类只写自己的解析循环（{@link #doRead}），零资源管理、零重复校验。
 *
 * <p>中断语义：解析循环内检测到中断标识时优雅停止（break/return），
 * 基类模板在收尾时检测到中断则跳过 flush，避免半截数据交给下游——
 * 与重构前 {@code FileReader} 的行为完全一致，且中断不抛异常（抛出点仍在 Transformer 层）。</p>
 *
 * @author lixiyun
 * @since 2026-10-22
 */
@Slf4j
public abstract class AbstractDocumentReader implements DocumentReader {

    // 批量消费的Document数量阈值：流式读取时的批处理大小控制，平衡内存占用和批量处理效率
    protected static final int BATCH_DOC_COUNT = 30;

    // 单个Document最大字符数限制：控制每个Document对象的最大内容长度，Tika与文本流派共用
    protected static final int MAX_DOC_CHARS = 10_000;

    // 文件大小限制
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    /**
     * 校验文件路径的合法性与存在性。
     * <p>
     * 验证文件路径不为空、不为空白字符串，且对应的文件在文件系统中实际存在。
     * 由门面在路由前调用——快速失败，全链仅此一次。
     *
     * @param filePath 待校验的文件路径字符串，支持绝对路径和相对路径
     * @throws BusinessException 当路径为null、空字符串、空白字符串或文件不存在时抛出文件未找到异常
     */
    public static void validatePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }
    }

    /**
     * 检查文件大小是否超出系统限制。
     * <p>
     * 验证文件大小不超过系统设定的最大限制（默认100MB）。
     * 由基类模板在打开流前调用，全链仅此一次。
     *
     * @param size 文件的字节大小，必须为非负数
     * @throws BusinessException 当文件大小超过 {@link #MAX_FILE_SIZE}（100MB）时抛出文件大小超限异常
     */
    public static void checkFileSize(long size) {
        if (size > MAX_FILE_SIZE) {
            throw new BusinessException(FileExceptionEnum.FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * 从文件路径中提取小写形式的扩展名。
     *
     * @param filePath 文件的完整路径，可以是绝对路径或相对路径
     * @return 文件扩展名（不含点号，已转为小写），若无扩展名则返回空字符串 ""
     */
    public static String getFileExtension(String filePath) {
        String fileName = FileUtil.getName(filePath);
        int idx = fileName.lastIndexOf('.');
        return idx == -1 ? "" : fileName.substring(idx + 1);
    }

    /**
     * 模板方法：统一一次读取的完整生命周期。
     * <p>
     * 顺序：大小校验 → 打开输入流（try-with-resources）→ 创建攒批收集器 →
     * 执行实现类解析循环 → 无中断时收尾 flush。
     * 中断时不 flush 剩余批次，避免半截数据交给下游。
     *
     * @param ctx 读取上下文
     */
    @Override
    public final void read(ReaderContext ctx) {
        try {
            Path path = Paths.get(ctx.filePath());
            checkFileSize(Files.size(path));
            try (InputStream is = Files.newInputStream(path)) {
                BatchCollector<VectorData> collector = new BatchCollector<>(BATCH_DOC_COUNT, ctx.consumer());
                doRead(ctx, is, collector);
                if (!isInterrupted(fileIdOf(ctx))) {
                    collector.flush();
                }
            }
        } catch (IOException e) {
            log.error("文件读取失败: {}", ctx.filePath(), e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 实现类解析循环。
     * <p>
     * 由基类模板在流已打开、攒批收集器已就绪时调用；
     * 实现类将分块通过 {@code collector.add(data)} 累积，无需自行 flush。
     *
     * @param ctx       读取上下文
     * @param is        已打开的输入流（基类负责关闭）
     * @param collector 攒批收集器，攒满 {@link #BATCH_DOC_COUNT} 自动批量消费
     */
    protected abstract void doRead(ReaderContext ctx, InputStream is,
                                   BatchCollector<VectorData> collector) throws IOException;

    /**
     * 复制模板元数据生成一个新的分块 VectorData。
     *
     * @param template 元数据模板
     * @return 复制后的 VectorData，实现类填充 content / chunkLevel*Idx 后加入攒批
     */
    protected VectorData copyFromTemplate(VectorData template) {
        return BeanUtil.copyProperties(template, VectorData.class);
    }

    /**
     * 校验 Document 是否有效。
     * <p>
     * 检查Document对象及其文本内容是否为空或仅包含空白字符。
     *
     * @param doc 待校验的文档对象，可以为null
     * @return 如果文档非null、文本非null且包含非空白字符则返回 true，否则返回 false
     */
    protected boolean isValidDoc(VectorData doc) {
        return doc != null && doc.getContent() != null && !doc.getContent().isBlank();
    }

    /**
     * 查询 Redis 中的中断标识。
     *
     * @param fileId 文件ID
     * @return 已中断返回 true
     */
    protected boolean isInterrupted(Long fileId) {
        return RagInterruptManager.isInterrupted(fileId);
    }

    /**
     * 从读取上下文取文件ID。
     *
     * @param ctx 读取上下文
     * @return 文件ID
     */
    protected static Long fileIdOf(ReaderContext ctx) {
        return ctx.vectorData().getFileId();
    }
}
