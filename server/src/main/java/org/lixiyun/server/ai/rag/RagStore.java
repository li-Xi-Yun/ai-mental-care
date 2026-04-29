package org.lixiyun.server.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.rag.reader.FileReader;
import org.lixiyun.server.ai.rag.transformer.FileTransformer;
import org.lixiyun.server.ai.rag.writer.FileWriter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-04-29 20:17
 */
@Slf4j
@Component
public class RagStore {

    @Autowired
    @Qualifier("deepSeekChatModel")
    private ChatModel deepSeekChatModel;

    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel dashScopeChatModel;

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel ollamaChatModel;

    @Autowired
    private FileWriter fileWriter;

    /**
     * 存储文件到向量数据库
     * <p>
     * RAG文件存储完整流程：
     * <ol>
     *     <li>{@link FileReader} - 粗粒度读取和切割文件</li>
     *     <li>{@link FileTransformer} - 细粒度语义分割</li>
     *     <li>{@link FileWriter} - 向量存储入库</li>
     * </ol>
     *
     * @param filePath 本地文件的绝对或相对路径，不能为空且文件必须存在
     */
    public void storeFileToVector(String filePath) {
        FileReader fileReader = new FileReader();
        FileTransformer fileTransformer = FileTransformer.builder().chatModel(dashScopeChatModel).build();
        fileReader.readLocalFile(filePath, documents -> {
            fileTransformer.transformDocuments(documents, fileWriter::writeDocuments);
        });
    }


}
