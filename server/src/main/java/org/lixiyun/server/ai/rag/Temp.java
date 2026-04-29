package org.lixiyun.server.ai.rag;

import org.lixiyun.server.ai.rag.reader.FileReader;
import org.lixiyun.server.ai.rag.transformer.FileTransformer;
import org.lixiyun.server.ai.rag.writer.FileWriter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-04-29 08:14
 */
@Component
public class Temp {

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

    public void test() {
        FileReader fileReader = new FileReader();
        FileTransformer fileTransformer = FileTransformer.builder().chatModel(dashScopeChatModel).build();
        fileReader.readLocalFile("D:\\temp\\test.txt", documents -> {
            fileTransformer.transformDocuments(documents, fileWriter::writeDocuments);
        });
    }

}
