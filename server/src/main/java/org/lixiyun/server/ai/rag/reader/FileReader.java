package org.lixiyun.server.ai.rag.reader;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.xml.sax.ContentHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 流式分块文件读取器（严格满足RAG流式消费）
 * <p>
 * 功能特性：
 * <ul>
 *     <li>PDF：按页读取，攒够指定页数 → 批量消费</li>
 *     <li>文本：按行读取，攒够指定文档数 → 批量消费</li>
 *     <li>消费的List必须包含多个Document</li>
 *     <li>全流式读取，无OOM风险</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-04-28 15:30
 */
@Slf4j
public class FileReader {

    // 文件大小限制
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    // 单行最大字符数限制：用于处理超长文本行的自动切分，避免单个Document内容过大导致内存问题或处理效率低下
    private static final int MAX_LINE_CHARS = 100_000;

    // 单个Document最大字符数限制：控制每个Document对象的最大内容长度，确保单个Document不会过大，有利于后续的向量嵌入处理和检索效率
    private static final int MAX_DOC_CHARS  = 20_000;

    // 批量消费的Document数量阈值：流式读取时的批处理大小控制，设置为5是为了平衡内存占用和批量处理效率，避免频繁的小批量操作
    private static final int BATCH_DOC_COUNT = 15;

    // 文本文档每Document包含的行数：针对纯文本文件的分块策略配置
    private static final int TEXT_LINES_PER_DOC = 100;

    // 支持的格式
    private static final Set<String> PDF_EXT = Set.of("pdf");
    private static final Set<String> TIKA_EXT = Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "markdown", "xml", "html", "htm");
    private static final Set<String> TEXT_EXT = Set.of("txt", "json", "java", "csv", "log");

    /**
     * 流式读取本地文件并分块消费
     * <p>
     * 根据文件扩展名自动选择解析策略：
     * <ul>
     *     <li>PDF：按页流式读取，每页生成一个 Document</li>
     *     <li>Office/Markdown：使用 Tika 解析，按内容块生成 Document</li>
     *     <li>纯文本：按行读取，每 50 行生成一个 Document</li>
     * </ul>
     * 所有类型的文件均遵循"攒够 指定数量的 Document 后批量触发 Consumer"的原则。
     *
     * @param filePath 本地文件的绝对或相对路径，不能为空且文件必须存在
     * @param consumer 消费者接口，接收包含多个 Document 的列表进行后续处理（如向量入库），不能为 null
     * @throws BusinessException 当文件不存在、大小超限（超过50MB）或读取失败时抛出业务异常
     */
    public void readLocalFile(String filePath, VectorData vectorData, Consumer<List<VectorData>> consumer) {
        validatePath(filePath);
        Path path = Paths.get(filePath);
        String ext = getFileExtension(filePath).toLowerCase();

        try {
            checkFileSize(Files.size(path));
            try (InputStream is = Files.newInputStream(path)) {
                // 分发处理：PDF /  Office文档 / 纯文本
                if (PDF_EXT.contains(ext)) {
                    processPdfByPage(is, vectorData, consumer);
                } else if (TIKA_EXT.contains(ext)) {
                    processTikaDocument(is, vectorData, consumer);
                } else if (TEXT_EXT.contains(ext)) {
                    processTextByLine(is, vectorData, consumer);
                } else {
                    processTextByLine(is, vectorData, consumer);
                }
            }
        } catch (IOException e) {
            log.error("文件读取失败: {}", filePath, e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 按页流式处理 PDF 文件
     * <p>
     * 使用 Apache PDFBox 逐页提取内容，每页封装为一个 VectorData。
     * 采用磁盘缓存模式避免内存溢出，支持大文件处理。
     * 当累积的 VectorData 数量达到 {@link #BATCH_DOC_COUNT}（默认15个）时，立即调用 Consumer 进行消费。
     *
     * @param is PDF 文件的输入流，方法执行完毕后会自动关闭相关资源
     * @param vectorData 文件元数据模板，用于复制生成每个分块的基础信息
     * @param consumer 用于批量消费 VectorData 列表的回调函数，每个批次最多包含15个VectorData
     * @throws BusinessException 当PDF解析失败或IO异常时抛出业务异常
     */
    private void processPdfByPage(InputStream is, VectorData vectorData, Consumer<List<VectorData>> consumer) {
        List<VectorData> batch = new ArrayList<>(BATCH_DOC_COUNT);

        try (
                // 关键：InputStream → RandomAccessRead
                RandomAccessRead readBuffer = new RandomAccessReadBuffer(is);

                // 关键：磁盘缓存模式
                PDDocument pdf = Loader.loadPDF(readBuffer, IOUtils.createTempFileOnlyStreamCache())
        ) {

            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = pdf.getNumberOfPages();

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String content = stripper.getText(pdf);
                VectorData data = BeanUtil.copyProperties(vectorData, VectorData.class);
                data.setContent(content);
                data.setChunkLevel1Idx(pageNum);

                if (isValidDoc(data)) {
                    batch.add(data);
                    if (batch.size() >= BATCH_DOC_COUNT) {
                        consumer.accept(new ArrayList<>(batch));
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) {
                consumer.accept(batch);
            }

        } catch (IOException e) {

            log.error("PDF低内存流式读取失败", e);

            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 流式处理 Office 及 Markdown 文档
     * <p>
     * 使用 Apache Tika 解析 Word、Excel、PPT、Markdown 等复杂格式文档。
     * 由于这些格式通常不具备统一的"页"概念，采用 Tika 内部的分块逻辑（每20000字符为一个chunk）。
     * 同样遵循攒够指定数量的 VectorData 后批量触发的机制。
     *
     * @param is 文档文件的输入流，方法执行完毕后会自动关闭相关资源
     * @param vectorData 文件元数据模板，用于复制生成每个分块的基础信息
     * @param consumer 用于批量消费 VectorData 列表的回调函数，每个批次最多包含15个VectorData
     * @throws BusinessException 当Tika解析失败或IO异常时抛出业务异常
     */
    private void processTikaDocument(InputStream is, VectorData vectorData, Consumer<List<VectorData>> consumer) {
        List<VectorData> batch = new ArrayList<>(BATCH_DOC_COUNT);
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
                        emitChunk(buffer, vectorData, batch, consumer, chunkIndex[0]++);
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
                emitChunk(buffer, vectorData, batch, consumer, chunkIndex[0]);
            }

        } catch (Exception e) {
            log.error("Tika流式解析失败", e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }

        if (!batch.isEmpty()) {
            consumer.accept(new ArrayList<>(batch));
        }
    }

    private void emitChunk(
            StringBuilder buffer,
            VectorData vectorData,
            List<VectorData> batch,
            Consumer<List<VectorData>> consumer,
            int chunkIndex) {

        if (buffer.isEmpty()) {
            return;
        }

        VectorData data = BeanUtil.copyProperties(vectorData, VectorData.class);
        data.setContent(buffer.toString());
        data.setChunkLevel1Idx(chunkIndex);

        batch.add(data);
        buffer.setLength(0);

        if (batch.size() >= BATCH_DOC_COUNT) {
            consumer.accept(new ArrayList<>(batch));
            batch.clear();
        }
    }

    /**
     * 按行流式处理纯文本文档
     * <p>
     * 使用 {@link BufferedReader} 逐行读取内容（强制 UTF-8 编码）。
     * 每读取 {@link #TEXT_LINES_PER_DOC}（默认100）行内容，将其封装为一个 VectorData。
     * 支持超长行自动切分（单行最大100,000字符）和超大VectorData保护（最大20,000字符）。
     * 当累积的 VectorData 数量达到 {@link #BATCH_DOC_COUNT}（默认15个）时，触发 Consumer。
     *
     * @param is 文本文件的输入流，方法执行完毕后会自动关闭相关资源
     * @param vectorData 文件元数据模板，用于复制生成每个分块的基础信息
     * @param consumer 用于批量消费 VectorData 列表的回调函数，每个批次最多包含15个VectorData
     * @throws IOException 当读取流发生 IO 错误或字符编码问题时抛出
     */
    private void processTextByLine(InputStream is, VectorData vectorData, Consumer<List<VectorData>> consumer) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            List<VectorData> batch = new ArrayList<>(BATCH_DOC_COUNT);
            StringBuilder sb = new StringBuilder(MAX_DOC_CHARS);
        
            int lineCount = 0;
            String line;
            int chunkIndex = 0;
        
            while ((line = br.readLine()) != null) {
                lineCount++;
        
                // ---------- 处理超长行 ----------
                int offset = 0;
        
                while (offset < line.length()) {
                    int end = Math.min(offset + MAX_LINE_CHARS, line.length());
                    sb.append(line, offset, end).append('\n');

                    offset = end;
        
                    // ---------- 按行或按字符切分 ----------
                    if (lineCount >= TEXT_LINES_PER_DOC || sb.length() >= MAX_DOC_CHARS) {
                        VectorData data = BeanUtil.copyProperties(vectorData, VectorData.class);
                        data.setContent(sb.toString());
                        data.setChunkLevel1Idx(chunkIndex++);
                                
                        batch.add(data);
        
                        sb.setLength(0);
                        lineCount = 0;
        
                        // ---------- 批处理 ----------
                        if (batch.size() >= BATCH_DOC_COUNT) {
                            consumer.accept(new ArrayList<>(batch));
                            batch.clear();
                        }
                    }
                }
            }
        
            // ---------- 收尾 ----------
            if (!sb.isEmpty()) {
                VectorData data = BeanUtil.copyProperties(vectorData, VectorData.class);
                data.setContent(sb.toString());
                data.setChunkLevel1Idx(chunkIndex);
        
                batch.add(data);
            }
        
            if (!batch.isEmpty()) {
                consumer.accept(new ArrayList<>(batch));
            }
        }
    }

    // ====================== 工具方法 ======================

    /**
     * 校验 Document 是否有效
     * <p>
     * 检查Document对象及其文本内容是否为空或仅包含空白字符。
     *
     * @param doc 待校验的文档对象，可以为null
     * @return 如果文档非null、文本非null且包含非空白字符则返回 true，否则返回 false
     */
    private boolean isValidDoc(VectorData doc) {
        return doc != null && doc.getContent() != null && !doc.getContent().isBlank();
    }

    /**
     * 校验文件路径的合法性与存在性
     * <p>
     * 验证文件路径不为空、不为空白字符串，且对应的文件在文件系统中实际存在。
     *
     * @param filePath 待校验的文件路径字符串，支持绝对路径和相对路径
     * @throws BusinessException 当路径为null、空字符串、空白字符串或文件不存在时抛出文件未找到异常
     */
    private void validatePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }
    }

    /**
     * 检查文件大小是否超出系统限制
     * <p>
     * 验证文件大小不超过系统设定的最大限制（默认50MB）。
     *
     * @param size 文件的字节大小，必须为非负数
     * @throws BusinessException 当文件大小超过 {@link #MAX_FILE_SIZE}（50MB）时抛出文件大小超限异常
     */
    private void checkFileSize(long size) {
        if (size > MAX_FILE_SIZE) {
            throw new BusinessException(FileExceptionEnum.FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * 从文件路径中提取小写形式的扩展名
     * <p>
     * 从完整文件路径中提取文件名部分，然后获取最后一个点号后的扩展名并转换为小写。
     *
     * @param filePath 文件的完整路径，可以是绝对路径或相对路径
     * @return 文件扩展名（不含点号，已转为小写），若无扩展名则返回空字符串 ""
     */
    private String getFileExtension(String filePath) {
        String fileName = FileUtil.getName(filePath);
        int idx = fileName.lastIndexOf('.');
        return idx == -1 ? "" : fileName.substring(idx + 1);
    }
}