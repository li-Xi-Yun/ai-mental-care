package org.lixiyun.server.ai.rag.reader;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.rag.reader.strategy.AbstractDocumentReader;
import org.lixiyun.server.ai.rag.reader.strategy.DocumentReader;
import org.lixiyun.server.ai.rag.reader.strategy.ReaderContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * RAG 文件读取门面（策略注册表）。
 * <p>
 * 只负责：路径校验 → 按扩展名路由到对应读取策略（{@link DocumentReader} 实现类）→ 委托执行。
 * 各文件类型的解析逻辑分别收敛在 {@code reader/strategy/} 子包的策略实现类中
 * （PDF / Tika / 纯文本三类），本门面不含任何解析代码。
 *
 * <p>路由规则（注册表 + 首匹配）：构造期按 {@link DocumentReader#order()} 升序固化全量策略；
 * {@link #resolve(String)} 取第一个 {@code supports(extension)} 命中者。兜底文本策略
 * {@code supports()==true} 且 order 置底，故未匹配扩展名自动回落到纯文本解析。</p>
 *
 * <p>新增文件类型只需新增一个 {@code @Component} 策略实现类（声明 supports/order），
 * 路由循环与门面零改动。</p>
 *
 * @author lixiyun
 * @since 2026-04-28 15:30
 */
@Slf4j
@Component
public class FileReader {

    private final List<DocumentReader> readers;

    /**
     * 构造器注入全部读取策略并按 order 升序固化，保证路由确定性。
     *
     * @param readers 全部 DocumentReader 策略（Spring 自动收集容器内实现）
     */
    public FileReader(List<DocumentReader> readers) {
        this.readers = readers.stream()
                .sorted(Comparator.comparingInt(DocumentReader::order))
                .toList();
        log.debug("RAG(FileReader)-注册读取策略: {}", readers.stream()
                .map(r -> r.getClass().getSimpleName() + "@" + r.order())
                .toList());
    }

    /**
     * 流式读取本地文件并分块消费。
     * <p>
     * 根据文件扩展名自动路由到对应读取策略：
     * <ul>
     *     <li>pdf → PDF 策略（按页流式读取，每页生成一个 Document）</li>
     *     <li>Office/Markdown/HTML → Tika 策略（按内容块生成 Document）</li>
     *     <li>其他/无扩展名 → 纯文本策略（按行读取，每 100 行/10000 字符生成一个 Document）</li>
     * </ul>
     * 所有类型的文件均遵循"攒够指定数量的 Document 后批量触发 Consumer"的原则。
     *
     * @param filePath 本地文件的绝对或相对路径，不能为空且文件必须存在
     * @param vectorData 文件元数据模板，用于复制生成每个分块的基础信息
     * @param consumer 消费者接口，接收包含多个 Document 的列表进行后续处理（如向量入库），不能为 null
     */
    public void readLocalFile(String filePath, VectorData vectorData, Consumer<List<VectorData>> consumer) {
        AbstractDocumentReader.validatePath(filePath);
        String extension = AbstractDocumentReader.getFileExtension(filePath).toLowerCase();
        resolve(extension).read(new ReaderContext(filePath, extension, vectorData, consumer));
    }

    /**
     * 按扩展名首匹配路由到读取策略。
     *
     * @param extension 已小写化、不含点号的扩展名
     * @return 命中的读取策略；兜底文本策略恒命中，不会走到 orElseThrow
     */
    private DocumentReader resolve(String extension) {
        return readers.stream()
                .filter(reader -> reader.supports(extension))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("没有可用的读取策略: " + extension));
    }
}