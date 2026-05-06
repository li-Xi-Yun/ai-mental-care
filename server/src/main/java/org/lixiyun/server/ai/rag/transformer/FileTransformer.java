package org.lixiyun.server.ai.rag.transformer;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.constant.prompt.CommonConstant;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * RAG文件存储流程第二步：细粒度语义分割转换器
 * <p>
 * 功能特性：
 * <ul>
 *     <li>接收第一步粗粒度切割的VectorData列表</li>
 *     <li>对每个VectorData进行语义级别的精细分割</li>
 *     <li>使用AI模型识别语义边界，保持文本完整性</li>
 *     <li>流式处理，批量消费分割结果</li>
 *     <li>支持多种ChatModel（Ollama/DashScope/DeepSeek）</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-04-28 18:25
 */
@Slf4j
@Builder
public class FileTransformer {

    private final ChatModel chatModel;

    // 单个Document最大字符数限制：超过此值才需要进行语义分割
    private static final int MAX_SEGMENT_CHARS = 3_000;

    // 批量传递给Consumer的Document数量阈值
    private static final int BATCH_SIZE = 15;

    private ReactAgent agent;

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
     * @param interruptedHandler 中断处理函数，用于处理线程中断时的清理工作
     * @param consumer 消费者接口，接收固定数量的VectorData列表进行向量存储处理，不能为null
     */
    public void transformDocuments(List<VectorData> documents,
                                   Consumer<Long> interruptedHandler,
                                   Consumer<List<VectorData>> consumer) {
        if (documents == null || documents.isEmpty()) {
            log.warn("RAG(FileTransformer)-待处理的VectorData列表为空，跳过语义分割");
            return;
        }

        log.info("RAG(FileTransformer)-开始对 {} 个VectorData进行细粒度语义分割", documents.size());

        List<VectorData> batchBuffer = new ArrayList<>(BATCH_SIZE);
        int globalIndex = 0;

        for (VectorData doc : documents) {
            if(Thread.currentThread().isInterrupted()){
                long fileId = doc.getFileId();
                log.warn("RAG(FileTransformer)-线程被中断");
                interruptedHandler.accept(fileId);
            }

            if (!isValidDocument(doc)) {
                continue;
            }

            String content = doc.getContent();

            if (content.length() <= MAX_SEGMENT_CHARS) {
                log.debug("RAG(FileTransformer)-VectorData长度{}未超过阈值，直接保留", content.length());
                doc.setChunkLevel2Idx(globalIndex++); // 用于识别二次分割的索引
                batchBuffer.add(doc);
            } else {
                log.debug("RAG(FileTransformer)-VectorData长度{}超过阈值，进行语义分割", content.length());
                List<VectorData> secondaryDivisionDocs = semanticSegmentation(doc, globalIndex);
                globalIndex += secondaryDivisionDocs.size();
                batchBuffer.addAll(secondaryDivisionDocs);
            }

            // 达到批量阈值则触发消费
            if (batchBuffer.size() >= BATCH_SIZE) {
                consumer.accept(new ArrayList<>(batchBuffer));
                batchBuffer.clear();
            }
        }

        // 处理剩余的 VectorData
        if (!batchBuffer.isEmpty()) {
            consumer.accept(batchBuffer);
        }

        log.info("RAG(FileTransformer)-VectorData细粒度语义分割及批量分发完成");
    }

    /**
     * 对单个文本内容进行语义分割
     * <p>
     * 调用AI模型对超长文本进行语义级别的精细分割，确保每个片段语义完整且长度适中。
     * 支持Ollama、DashScope、DeepSeek等多种模型。
     *
     * @param doc 待分割的VectorData对象
     * @param globalIndex 起始的全局二级分块索引
     * @return 分割后的VectorData列表，如果分割失败则返回字符截断后的列表
     * @throws BusinessException 当模型调用失败或响应解析失败时抛出业务异常
     */
    private List<VectorData> semanticSegmentation(VectorData doc, int globalIndex) {
        String content = doc.getContent();

        try {
            log.debug("RAG(FileTransformer)-调用AI模型进行语义分割，原文长度: {}", content.length());


            // 缓存Agent实例，避免重复创建
            if(agent == null){
                String systemPrompt = PromptUtil.getPrompt(CommonConstant.SEMANTIC_SEGMENTATION_SYSTEM_PROMPT);

                if (systemPrompt.isEmpty()) {
                    log.error("RAG(FileTransformer)-语义分割系统提示词加载失败");
                    throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
                }
                agent = reactAgentBuilder(systemPrompt)
                        .build();
            }

            List<Message> messages = new ArrayList<>();
            messages.add(new UserMessage(content));
            AssistantMessage response = agent.call(messages);

            String responseText = response.getText();

            if (responseText == null || responseText.isBlank()) {
                log.warn("RAG(FileTransformer)-模型返回内容为空，使用字符截断方式分割");
                return splitByCharCount(content, doc, globalIndex);
            }

            List<String> segments = parseSegmentsFromResponse(responseText);

            if (segments.isEmpty()) {
                log.warn("RAG(FileTransformer)-解析后的片段列表为空，使用字符截断方式分割");
                return splitByCharCount(content, doc, globalIndex);
            }

            log.info("RAG(FileTransformer)-语义分割成功，将{}字符分割为{}个片段", content.length(), segments.size());

            // 构建多个 VectorData，并递增 globalIndex
            List<VectorData> result = new ArrayList<>();
            for (String segment : segments) {
                VectorData newDoc = createVectorDataWithMetadata(segment, doc, globalIndex++);
                result.add(newDoc);
            }
            return result;
        } catch (Exception e) {
            log.error("RAG(FileTransformer)-语义分割失败，使用字符截断方式分割", e);
            return splitByCharCount(content, doc, globalIndex);
        }
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
     * 从模型响应中解析文本片段列表
     * <p>
     * 解析模型返回的JSON格式响应，提取segments数组中的文本片段。
     * 支持多种JSON格式的容错处理。
     *
     * @param responseText 模型返回的原始响应文本
     * @return 解析后的文本片段列表，如果解析失败则返回空列表
     */
    private List<String> parseSegmentsFromResponse(String responseText) {
        try {
            log.debug("RAG(FileTransformer)-开始解析模型响应，响应长度: {}", responseText.length());

            Map<String, Object> jsonResponse = JsonUtils.parseMap(responseText);

            if (jsonResponse == null || !jsonResponse.containsKey("segments")) {
                log.warn("RAG(FileTransformer)-响应中未找到segments字段");
                return List.of();
            }

            Object segmentsObj = jsonResponse.get("segments");
            if (!(segmentsObj instanceof List)) {
                log.warn("RAG(FileTransformer)-segments字段不是List类型");
                return List.of();
            }

            @SuppressWarnings("unchecked")
            List<Object> segmentsRaw = (List<Object>) segmentsObj;

            List<String> segments = segmentsRaw.stream()
                    .filter(obj -> obj instanceof String)
                    .map(obj -> (String) obj)
                    .filter(segment -> !segment.isBlank())
                    .collect(Collectors.toList());

            log.debug("RAG(FileTransformer)-成功解析出{}个文本片段", segments.size());
            return segments;

        } catch (Exception e) {
            log.error("RAG(FileTransformer)-解析模型响应失败", e);
            return List.of();
        }
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
     * 构建语义分割专用的 ReactAgent
     * <p>
     * 使用低温度和高确定性参数，确保模型严格按照提示词要求进行语义边界识别。
     *
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent 构建器
     */
    private com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder(String systemPrompt) {
        return ReactAgent.builder()
                .model(chatModel)
                .name("semanticSegmentation")
                .description("文档细粒度语义分割")
                .systemPrompt(systemPrompt)
                .chatOptions(buildChatOptions())
                .enableLogging(false);
    }

    /**
     * 根据ChatModel类型构建对应的ChatOptions配置
     * <p>
     * 针对不同模型类型（Ollama/DashScope/DeepSeek）设置最优的参数配置，
     * 以确保语义分割的准确性和稳定性。
     *
     * @return 针对当前模型类型优化的ChatOptions配置
     */
    private ChatOptions buildChatOptions() {
        if (chatModel instanceof OllamaChatModel) {
            return OllamaChatOptions.builder()
                    .temperature(0.1)
                    .topK(30)
                    .topP(0.85)
                    .numPredict(8000)
                    .seed(42)
                    .repeatPenalty(1.2)
                    .frequencyPenalty(0.7)
                    .presencePenalty(0.3)
                    .format("json")
                    .truncate(true)
                    .numCtx(8192)
                    .numThread(Runtime.getRuntime().availableProcessors())
                    .numBatch(512)
                    .useMMap(true)
                    .useMLock(false)
                    .numGPU(-1)
                    .lowVRAM(false)
                    .build();

        } else if (chatModel instanceof DashScopeChatModel) {
            return com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions.builder()
                    .temperature(0.1)
                    .topP(0.85)
                    .topK(40)
                    .maxToken(8000)
                    .seed(42)
                    .repetitionPenalty(1.2)
                    .responseFormat(DashScopeResponseFormat.builder()
                            .type(DashScopeResponseFormat.Type.JSON_OBJECT)
                            .build())
                    .enableThinking(false)
                    .incrementalOutput(false)
                    .multiModel(false)
                    .vlHighResolutionImages(false)
                    .build();

        } else if (chatModel instanceof DeepSeekChatModel) {
            return DeepSeekChatOptions.builder()
                    .temperature(0.1)
                    .topP(0.85)
                    .maxTokens(8000)
                    .frequencyPenalty(0.7)
                    .presencePenalty(0.3)
                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormat.Type.JSON_OBJECT)
                            .build())
                    .logprobs(false)
                    .topLogprobs(null)
                    .build();
        } else {
            return ChatOptions.builder()
                    .topK(30)
                    .topP(0.85)
                    .frequencyPenalty(0.7)
                    .presencePenalty(0.3)
                    .temperature(0.1)
                    .maxTokens(8000)
                    .build();
        }
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
