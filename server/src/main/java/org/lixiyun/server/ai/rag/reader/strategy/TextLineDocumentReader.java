package org.lixiyun.server.ai.rag.reader.strategy;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.rag.pipeline.BatchCollector;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 纯文本流式读取策略。
 * <p>
 * 使用 {@link BufferedReader} 逐行读取内容（强制 UTF-8 编码）。
 * 每读取 {@link #TEXT_LINES_PER_DOC}（默认100）行内容，将其封装为一个 VectorData；
 * 单行超过 {@link #MAX_LINE_CHARS}（默认100,000）时自动切分；单个 Document 内容
 * 超过 {@link AbstractDocumentReader#MAX_DOC_CHARS}（默认10,000）时提前切分。
 * 当累积的 VectorData 数量达到 {@link AbstractDocumentReader#BATCH_DOC_COUNT}（默认30个）时，
 * 由基类攒批收集器自动批量消费。
 *
 * <p>作为注册表兜底策略：{@link #supports(String)} 对一切扩展名返回 true 且
 * {@link #order()} 声明为最大值（10000），保证门面首匹配路由对未匹配扩展名自动回落到本策略，
 * 与重构前 {@code FileReader} "else 分支走文本解析"的行为一致。</p>
 *
 * @author lixiyun
 * @since 2026-10-22
 */
@Slf4j
@Component
public class TextLineDocumentReader extends AbstractDocumentReader {

    // 文本文档每Document包含的行数：针对纯文本文件的分块策略配置
    private static final int TEXT_LINES_PER_DOC = 100;

    // 单行最大字符数限制：用于处理超长文本行的自动切分，避免单个Document内容过大导致内存问题或处理效率低下
    private static final int MAX_LINE_CHARS = 100_000;

    @Override
    public boolean supports(String extension) {
        // 兜底策略：支持一切扩展名（含无扩展名），由 order 置底保证最后匹配
        return true;
    }

    @Override
    public int order() {
        // 置底：后于 PDF/Tika 匹配，未匹配扩展名回落本策略
        return 10_000;
    }

    @Override
    protected void doRead(ReaderContext ctx, InputStream is, BatchCollector<VectorData> collector) throws IOException {
        Long fileId = fileIdOf(ctx);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(MAX_DOC_CHARS);

            int lineCount = 0;
            String line;
            int chunkIndex = 0;

            while ((line = br.readLine()) != null) {
                // 检查Redis中的中断标识
                if (isInterrupted(fileId)) {
                    log.warn("RAG(FileReader)-文本处理过程中检测到中断标识，已处理{}行后放弃", lineCount);
                    break;
                }

                lineCount++;

                // ---------- 处理超长行 ----------
                int offset = 0;

                while (offset < line.length()) {
                    int end = Math.min(offset + MAX_LINE_CHARS, line.length());
                    sb.append(line, offset, end).append('\n');

                    offset = end;

                    // ---------- 按行或按字符切分 ----------
                    if (lineCount >= TEXT_LINES_PER_DOC || sb.length() >= MAX_DOC_CHARS) {
                        VectorData data = copyFromTemplate(ctx.vectorData());
                        data.setContent(sb.toString());
                        data.setChunkLevel1Idx(chunkIndex++);

                        collector.add(data);

                        sb.setLength(0);
                        lineCount = 0;
                    }
                }
            }

            // ---------- 收尾 ----------
            if (!sb.isEmpty()) {
                VectorData data = copyFromTemplate(ctx.vectorData());
                data.setContent(sb.toString());
                data.setChunkLevel1Idx(chunkIndex);

                collector.add(data);
            }
        }
    }
}
