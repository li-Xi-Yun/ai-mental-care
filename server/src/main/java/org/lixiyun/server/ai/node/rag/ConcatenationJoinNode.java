package org.lixiyun.server.ai.node.rag;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 检索后置处理组件：用于RAG流程的最后一步，将检索的文档数据合并为一个字符串，给予其他Agent使用
 * <p>该节点接收重排序后的文档列表，将所有文档内容拼接成一个完整的上下文字符串，供后续的对话Agent使用</p>
 * @author lixiyun
 * @since 2026-04-27 09:18
 */
@Slf4j
@Builder
public class ConcatenationJoinNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "concatenationJoinNode";

    private static final String DOCUMENT_SEPARATOR = "\n\n---\n\n";  // 文档之间的分隔符
    private static final String DOCUMENT_PREFIX = "【参考文档】(你需要根据这个文档中的内容进行内容生成)\n";  // 文档前缀

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.debug("文档拼接节点开始执行");

        // 获取重排序后的文档列表
        Optional<List<Document>> rerankedDocsOpl = state.value(RerankerNode.NODE_NAME);
        List<Document> rerankedDocuments = rerankedDocsOpl.orElse(List.of());

        log.debug("文档拼接节点：重排序后的文档数量={}", rerankedDocuments.size());

        if (rerankedDocuments.isEmpty()) {
            log.warn("文档拼接节点：重排序结果为空，无法进行拼接");
            return Map.of();
        }

        // 使用Stream处理文档列表，提取文本内容并拼接
        String concatenatedContent = rerankedDocuments.stream()
                .map(Document::getText)  // 提取文档文本内容
                .filter(text -> text != null && !text.isEmpty())  // 过滤空内容
                .collect(Collectors.joining(DOCUMENT_SEPARATOR));  // 使用分隔符拼接

        log.info("文档拼接节点：拼接完成，总字符数={}", concatenatedContent.length());

        if (concatenatedContent.isEmpty()) {
            log.warn("文档拼接节点：拼接后内容为空");
            return Map.of();
        }

        // 添加前缀标识
        String finalContent = DOCUMENT_PREFIX + concatenatedContent;

        log.debug("文档拼接节点：最终内容长度={}", finalContent.length());

        log.debug("文档拼接节点：存入上下文完成");

        // 将拼接后的内容存入state，供后续Agent使用
        return Map.of(GraphConstant.RAG_RESULT, finalContent);
    }
}
