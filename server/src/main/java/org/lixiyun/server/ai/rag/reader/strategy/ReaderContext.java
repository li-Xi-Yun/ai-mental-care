package org.lixiyun.server.ai.rag.reader.strategy;

import org.lixiyun.pojo.entity.vector.VectorData;

import java.util.List;
import java.util.function.Consumer;

/**
 * 读取策略上下文。
 * <p>
 * 门面路由后构造并传入策略实现，承载一次读取所需的全部入参。
 * 不含 InputStream——流由抽象基类模板统一打开/关闭，实现类零资源管理。
 *
 * @param filePath   本地文件的绝对或相对路径，门面已校验存在
 * @param extension  文件扩展名，已转为小写且不含点号（如 {@code "pdf"}）
 * @param vectorData 文件元数据模板（fileId / knowledgeType / deleted 等），
 *                   各分块通过复制该模板生成
 * @param consumer   批量消费回调，接收攒够批大小的 VectorData 列表
 * @author lixiyun
 * @since 2026-10-22
 */
public record ReaderContext(
        String filePath,
        String extension,
        VectorData vectorData,
        Consumer<List<VectorData>> consumer) {
}
