package org.lixiyun.server.ai.rag.reader.strategy;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.rag.pipeline.BatchCollector;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * PDF 流式读取策略。
 * <p>
 * 使用 Apache PDFBox 逐页提取内容，每页封装为一个 VectorData（chunkLevel1Idx=页号）。
 * 采用磁盘缓存模式（{@code Loader.loadPDF(readBuffer, tempFileOnlyStreamCache)}）避免内存溢出，支持大文件处理。
 * 当累积的 VectorData 数量达到 {@link AbstractDocumentReader#BATCH_DOC_COUNT}（默认30个）时，
 * 由基类攒批收集器自动批量消费。
 *
 * @author lixiyun
 * @since 2026-10-22
 */
@Slf4j
@Component
public class PdfDocumentReader extends AbstractDocumentReader {

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public int order() {
        // 仅匹配 pdf 扩展名，优先级高于兜底文本策略
        return 10;
    }

    @Override
    protected void doRead(ReaderContext ctx, InputStream is, BatchCollector<VectorData> collector) {
        Long fileId = fileIdOf(ctx);

        try (
                // 关键：InputStream → RandomAccessRead
                RandomAccessRead readBuffer = new RandomAccessReadBuffer(is);

                // 关键：磁盘缓存模式
                PDDocument pdf = Loader.loadPDF(readBuffer, IOUtils.createTempFileOnlyStreamCache())
        ) {

            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = pdf.getNumberOfPages();

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                // 检查Redis中的中断标识
                if (isInterrupted(fileId)) {
                    log.warn("RAG(FileReader)-PDF处理过程中检测到中断标识，已处理{}/{}页后放弃", pageNum - 1, totalPages);
                    break;
                }

                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String content = stripper.getText(pdf);

                // 添加调试日志
                log.debug("RAG(FileReader)-PDF第{}页提取内容长度: {}", pageNum, content != null ? content.length() : 0);
                if (content != null && !content.trim().isEmpty()) {
                    log.debug("RAG(FileReader)-PDF第{}页前100字符: {}", pageNum, content.substring(0, Math.min(100, content.length())));
                }

                VectorData data = copyFromTemplate(ctx.vectorData());
                data.setContent(content);
                data.setChunkLevel1Idx(pageNum);

                if (isValidDoc(data)) {
                    collector.add(data);
                    log.debug("RAG(FileReader)-PDF第{}页内容有效，已加入批次", pageNum);
                } else {
                    log.warn("RAG(FileReader)-PDF第{}页内容无效（为空或纯空白），跳过", pageNum);
                }
            }
        } catch (IOException e) {
            log.error("PDF低内存流式读取失败", e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }
}
