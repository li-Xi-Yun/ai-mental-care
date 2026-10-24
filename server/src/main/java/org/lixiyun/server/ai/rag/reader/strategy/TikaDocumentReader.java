package org.lixiyun.server.ai.rag.reader.strategy;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.rag.pipeline.BatchCollector;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Set;

/**
 * Office / Markdown / HTML 流式读取策略。
 * <p>
 * 使用 Apache Tika 解析 Word、Excel、PPT、Markdown 等复杂格式文档。
 * 由于这些格式通常不具备统一的"页"概念，采用 Tika 内部的分块逻辑
 * （每 {@link AbstractDocumentReader#MAX_DOC_CHARS}（默认10000）字符为一个 chunk）。
 * 同样遵循攒够指定数量的 VectorData 后由基类攒批收集器批量触发的机制。
 *
 * @author lixiyun
 * @since 2026-10-22
 */
@Slf4j
@Component
public class TikaDocumentReader extends AbstractDocumentReader {

    // 支持的扩展名：Office 文档 + Markdown + HTML/XML，走 Tika 解析
    private static final Set<String> EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "markdown", "xml", "html", "htm");

    @Override
    public boolean supports(String extension) {
        return EXTENSIONS.contains(extension);
    }

    @Override
    public int order() {
        // 仅匹配 Office/Markdown/HTML 扩展名，优先级高于兜底文本策略
        return 20;
    }

    @Override
    protected void doRead(ReaderContext ctx, InputStream is, BatchCollector<VectorData> collector) throws IOException {
        Long fileId = fileIdOf(ctx);
        Metadata metadata = new Metadata();
        Parser parser = new AutoDetectParser();
        StringBuilder buffer = new StringBuilder(2000);
        final int[] chunkIndex = {0};

        try {
            ContentHandler handler = new BodyContentHandler(new Writer() {

                @Override
                public void write(char[] cbuf, int off, int len) {
                    buffer.append(cbuf, off, len);

                    // 达到 chunk 大小
                    if (buffer.length() >= MAX_DOC_CHARS) {
                        emitChunk(buffer, ctx, collector, chunkIndex[0]++);
                    }
                }

                @Override
                public void flush() {}

                @Override
                public void close() {}
            });

            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);
            parser.parse(is, handler, metadata, context);

            // 收尾
            if (!buffer.isEmpty()) {
                emitChunk(buffer, ctx, collector, chunkIndex[0]);
            }

        } catch (Exception e) {
            log.error("Tika流式解析失败", e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 将缓冲内容封装为一个 chunk 并加入攒批收集器。
     * <p>
     * 解析过程中检测到中断标识时直接放弃当前 chunk（不清空缓冲区），
     * 基类模板在收尾时检测到中断则跳过 flush。
     *
     * @param buffer    内容缓冲
     * @param ctx       读取上下文
     * @param collector 攒批收集器
     * @param chunkIndex 当前 chunk 序号
     */
    private void emitChunk(StringBuilder buffer,
                           ReaderContext ctx,
                           BatchCollector<VectorData> collector,
                           int chunkIndex) {

        if (buffer.isEmpty()) {
            return;
        }

        // 检查Redis中的中断标识
        if (isInterrupted(fileIdOf(ctx))) {
            log.warn("RAG(FileReader)-Tika解析过程中检测到中断标识，放弃当前chunk");
            return;
        }

        VectorData data = copyFromTemplate(ctx.vectorData());
        data.setContent(buffer.toString());
        data.setChunkLevel1Idx(chunkIndex);

        collector.add(data);
        buffer.setLength(0);
    }
}