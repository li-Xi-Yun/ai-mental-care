package org.lixiyun.server.ai.rag.transformer;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.KnowledgeBaseExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.rag.SemanticSegmentationModel;
import org.lixiyun.server.ai.rag.RagInterruptManager;
import org.lixiyun.server.ai.rag.pipeline.BatchCollector;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * RAG文件存储流程第二步：细粒度语义分割转换器
 * <p>
 * 功能特性：
 * <ul>
 *     <li>接收第一步粗粒度切割的VectorData列表</li>
 *     <li>对每个VectorData进行语义级别的精细分割</li>
 *     <li>使用AI模型识别语义边界，保持文本完整性</li>
 *     <li>流式处理，批量消费分割结果</li>
 *     <li>模型配置由 {@code ai_node_config} 表的 semanticSegmentationNode 节点驱动（传递 {@link SemanticSegmentationModel}）</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-04-28 18:25
 */
@Slf4j
@Component
public class FileTransformer {

    /** 语义分割模型节点 key（AiNodeConfigManager 按此读取 DB 配置，管理员可编辑、保存即生效） */
    public static final String NODE_NAME = "semanticSegmentationNode";

    private final SemanticSegmentationModel semanticSegmentationModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    /**
     * 构造器注入依赖。
     */
    public FileTransformer(SemanticSegmentationModel semanticSegmentationModel,
                           AiNodeConfigManager aiNodeConfigManager,
                           ChatModelFactory chatModelFactory) {
        this.semanticSegmentationModel = semanticSegmentationModel;
        this.aiNodeConfigManager = aiNodeConfigManager;
        this.chatModelFactory = chatModelFactory;
    }

    // 单个Document最大字符数限制：超过此值才需要进行语义分割
    private static final int MAX_SEGMENT_CHARS = 2_000;

    // 批量传递给模型与Consumer的Document数量阈值
    private static final int BATCH_SIZE = 25;

    /**
     * 流式处理VectorData列表并进行细粒度语义分割
     * <p>
     * 对输入的VectorData列表进行遍历，对每个VectorData检查其内容长度：
     * <ul>
     *     <li>如果内容长度小于等于 {@link #MAX_SEGMENT_CHARS}，直接加入待处理队列并设置二级索引</li>
     *     <li>如果内容长度超过 {@link #MAX_SEGMENT_CHARS}，调用AI模型进行语义分割</li>
     * </ul>
     * 分割或处理后的结果会被封装为新的 {@link VectorData} 对象。
     * 当累积的 VectorData 数量达到 {@link #BATCH_SIZE} 时，通过 Consumer 回调函数批量传递给第三步向量存储。
     *
     * @param documents 第一步粗粒度切割后的VectorData列表，不能为null或空
     * @param consumer 消费者接口，接收固定数量的VectorData列表进行向量存储处理，不能为null
     */
    public void transformDocuments(List<VectorData> documents,
                                   Consumer<List<VectorData>> consumer) {
        if (documents == null || documents.isEmpty()) {
            log.warn("RAG(FileTransformer)-待处理的VectorData列表为空，跳过语义分割");
            return;
        }

        log.info("RAG(FileTransformer)-开始对 {} 个VectorData进行细粒度语义分割", documents.size());

        BatchCollector<VectorData> batchCollector = new BatchCollector<>(BATCH_SIZE, consumer);

        for (VectorData doc : documents) {
            // 检查Redis中的中断标识
            if (RagInterruptManager.isInterrupted(doc.getFileId())) {
                log.warn("RAG(FileTransformer)-检测到中断标识，准备执行中断处理");
                throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_PARSE_INTERRUPTED);
            }

            if (!isValidDocument(doc)) {
                continue;
            }

            String content = doc.getContent();

            if (content.length() <= MAX_SEGMENT_CHARS) {
                log.debug("RAG(FileTransformer)-VectorData长度{}未超过阈值，直接保留", content.length());
                doc.setChunkLevel2Idx(0); // 用于识别二次分割的索引
                batchCollector.add(doc);
            } else {
                log.debug("RAG(FileTransformer)-VectorData长度{}超过阈值，进行语义分割", content.length());
                int globalIndex = 0;
                List<VectorData> secondaryDivisionDocs = semanticSegmentation(doc, globalIndex);
                batchCollector.addAll(secondaryDivisionDocs);
            }
        }

        batchCollector.flush();

        log.info("RAG(FileTransformer)-VectorData细粒度语义分割及批量分发完成");
    }

    /**
     * 对单个文本内容进行语义分割
     * <p>
     * 通过 {@link SemanticSegmentationModel} 调用 AI 模型对超长文本进行语义级别的精细分割，
     * 系统提示词与模型参数来自 {@code ai_node_config} 表的 semanticSegmentationNode 节点（DB 驱动，保存即生效）。
     * 输出经结构化反序列化为 {@link SemanticSegmentationModel.SemanticSegmentationResult}。
     * </p>
     *
     * @param doc 待分割的VectorData对象
     * @param globalIndex 起始的全局二级分块索引
     * @return 分割后的VectorData列表，如果分割失败则返回字符截断后的列表
     */
    private List<VectorData> semanticSegmentation(VectorData doc, int globalIndex) {
        String content = doc.getContent();

        try {
            log.debug("RAG(FileTransformer)-调用AI模型进行语义分割，原文长度: {}", content.length());

            // 检查Redis中的中断标识
            if (RagInterruptManager.isInterrupted(doc.getFileId())) {
                log.warn("RAG(FileTransformer)-语义分割前检测到中断标识，放弃处理");
                throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_PARSE_INTERRUPTED);
            }

            // 读取 DB 驱动的语义分割节点配置（缺失或禁用时降级为字符截断）
            AiNodeConfig config = aiNodeConfigManager.getConfigWithLoad(NODE_NAME);
            if (config == null) {
                log.warn("RAG(FileTransformer)-语义分割节点配置缺失或已禁用，使用字符截断方式分割，nodeKey={}", NODE_NAME);
                return splitByCharCount(content, doc, globalIndex);
            }

            ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(config.getModelType()));

            // 再次检查中断标识（防止在读取配置/解析模型过程中被中断）
            if (RagInterruptManager.isInterrupted(doc.getFileId())) {
                log.warn("RAG(FileTransformer)-调用AI模型前检测到中断标识，放弃处理");
                throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_PARSE_INTERRUPTED);
            }

            // 调用 Model 层（结构化输出，内部自带 DB 驱动的指数退避重试）
            SemanticSegmentationModel.SemanticSegmentationResult result =
                    semanticSegmentationModel.callForResult(chatModel, content, config);

            List<String> segments = result != null && result.getSegments() != null
                    ? result.getSegments().stream().filter(s -> s != null && !s.isBlank()).toList()
                    : List.of();

            if (segments.isEmpty()) {
                log.warn("RAG(FileTransformer)-解析后的片段列表为空，使用字符截断方式分割");
                return splitByCharCount(content, doc, globalIndex);
            }

            if (RagInterruptManager.isInterrupted(doc.getFileId())) {
                log.warn("RAG(FileTransformer)-模型调用成功，但检测到中断标识，放弃处理");
                throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_PARSE_INTERRUPTED);
            }

            log.info("RAG(FileTransformer)-语义分割成功，将{}字符分割为{}个片段", content.length(), segments.size());

            // 构建多个 VectorData，并递增 globalIndex
            List<VectorData> resultList = new ArrayList<>();
            for (String segment : segments) {
                // 在循环中检查中断标识
                if (RagInterruptManager.isInterrupted(doc.getFileId())) {
                    log.warn("RAG(FileTransformer)-构建VectorData过程中检测到中断标识，已生成{}个片段后放弃", resultList.size());
                    throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_PARSE_INTERRUPTED);
                }
                VectorData newDoc = createVectorDataWithMetadata(segment, doc, globalIndex++);
                resultList.add(newDoc);
            }
            return resultList;
        } catch (GraphRunnerException e) {
            // 模型调用/反序列化失败（重试耗尽后），降级为字符截断
            log.warn("RAG(FileTransformer)-语义分割模型调用失败，使用字符截断方式分割", e);
            return splitByCharCount(content, doc, globalIndex);
        } catch (Exception e) {
            // 中断等 RAG 领域异常直接向上抛出，其他失败降级为字符截断
            BusinessException be = findRagInterruptedException(e);
            if (be != null) {
                throw be;
            }
            log.error("RAG(FileTransformer)-语义分割失败，使用字符截断方式分割", e);
            return splitByCharCount(content, doc, globalIndex);
        }
    }

    /**
     * 在异常链中查找 RAG 领域的中断异常。
     */
    private BusinessException findRagInterruptedException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof BusinessException be
                    && be.getCode() != null
                    && be.getCode().equals(KnowledgeBaseExceptionEnum.FILE_PARSE_INTERRUPTED.getCode())) {
                return be;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 按字符数量截断文本为多个符合要求的VectorData
     * <p>
     * 当语义分割失败时，使用此方法作为降级策略，将长文本按固定字符数截断为多个片段。
     * 每个片段都会保留原始VectorData的元数据信息，并添加分段索引。
     *
     * @param content 待截断的文本内容
     * @param originalDoc 原始VectorData，用于复制元数据
     * @param globalIndex 起始索引值
     * @return 截断后的VectorData列表
     */
    private List<VectorData> splitByCharCount(String content, VectorData originalDoc, int globalIndex) {
        if (content == null || content.isEmpty()) {
            log.warn("RAG(FileTransformer)-待截断内容为空，返回空列表");
            return List.of();
        }

        List<VectorData> result = new ArrayList<>();
        int totalLength = content.length();
        int offset = 0;

        while (offset < totalLength) {
            // 检查Redis中的中断标识
            if (RagInterruptManager.isInterrupted(originalDoc.getFileId())) {
                log.warn("RAG(FileTransformer)-字符截断过程中检测到中断标识，已生成{}个片段后放弃", result.size());
                throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_PARSE_INTERRUPTED);
            }

            // 计算当前片段的结束位置
            int endOffset = Math.min(offset + MAX_SEGMENT_CHARS, totalLength);
            String segment = content.substring(offset, endOffset);

            // 创建带有元数据的新VectorData
            VectorData newDoc = createVectorDataWithMetadata(segment, originalDoc, globalIndex++);
            result.add(newDoc);

            log.debug("RAG(FileTransformer)-字符截断生成第{}个片段，长度: {}", globalIndex, segment.length());

            // 移动到下一个片段
            offset = endOffset;
        }

        log.info("RAG(FileTransformer)-字符截断完成，将{}字符分割为{}个片段", totalLength, result.size());
        return result;
    }

    /**
     * 为文本片段创建带有元数据的VectorData对象
     * <p>
     * 复制原始VectorData的元数据信息，并添加全局索引标识，确保分割后的VectorData保留完整的上下文信息。
     *
     * @param text 文本片段内容
     * @param originalDoc 原始VectorData，用于复制元数据
     * @param globalIndex 全局索引值，用于标识片段顺序
     * @return 包含元数据的新VectorData对象
     */
    private VectorData createVectorDataWithMetadata(String text, VectorData originalDoc, int globalIndex) {
        VectorData data = BeanUtil.copyProperties(originalDoc, VectorData.class);
        data.setChunkLevel2Idx(globalIndex);
        data.setContent(text);
        return data;
    }

    /**
     * 校验VectorData是否有效
     * <p>
     * 检查VectorData对象及其文本内容是否为空或仅包含空白字符。
     *
     * @param doc 待校验的VectorData对象，可以为null
     * @return 如果VectorData非null、文本非null且包含非空白字符则返回true，否则返回false
     */
    private boolean isValidDocument(VectorData doc) {
        return doc != null && doc.getContent() != null && !doc.getContent().isBlank();
    }
}