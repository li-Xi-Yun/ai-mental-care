package org.lixiyun.server.ai.rag.reader.strategy;

/**
 * RAG 文件读取策略接口。
 * <p>
 * 每个实现类对应"同一类文档"的专属解析方式（PDF / Tika / 纯文本等），
 * 由门面 {@code FileReader} 通过注册表按扩展名路由分发。
 * 新增文件类型只需新增一个实现类并声明 {@link #supports(String)} 与 {@link #order()}，
 * 路由循环零改动。
 *
 * @author lixiyun
 * @since 2026-10-22
 */
public interface DocumentReader {

    /**
     * 是否支持该扩展名。
     *
     * @param extension 文件扩展名，已转为小写且不含点号（如 {@code "pdf"}）
     * @return 支持返回 true；兜底策略对所有扩展名返回 true
     */
    boolean supports(String extension);

    /**
     * 注册表匹配优先级，数值越小越优先匹配。
     * <p>
     * 兜底策略应声明最大值置底，保证"未匹配扩展名回落到兜底实现"由首匹配路由天然保证。
     *
     * @return 匹配优先级
     */
    default int order() {
        return 100;
    }

    /**
     * 执行整份文件的读取与分批消费。
     * <p>
     * 文件路径校验、大小校验、流打开/关闭、攒批与收尾 flush 由抽象基类模板统一承载，
     * 实现类只需专注各自的解析循环。
     *
     * @param ctx 读取上下文
     */
    void read(ReaderContext ctx);
}
