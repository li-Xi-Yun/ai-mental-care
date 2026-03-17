package org.lixiyun.common.vector.utils;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.constant.MimeTypeConstant;
import org.lixiyun.common.core.error.enums.KnowledgeBaseExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.lixiyun.common.core.utils.SpringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class VectorStoreUtil {

    private static final VectorStore vectorStore = SpringUtils.getBean(VectorStore.class);

    private static final FileUrlProperties fileUrlProperties = SpringUtils.getBean(FileUrlProperties.class);

    public static void preFileDataVectorStore(String fileUrl, Map<String, Object> map) {
        String fileName = fileUrl.substring(fileUrl.lastIndexOf("/"));
        Path path = Paths.get(fileUrlProperties.getUploadFiles() + fileName);
        try (InputStream inputStream = new FileInputStream(path.toFile())) {
            String fileType = FileTypeDetectorUtil.detectFileType(inputStream, fileName);
            // 重新打开流，因为detectFileType已经读取了部分内容
            try (InputStream fileInputStream = new FileInputStream(path.toFile())) {
                if (fileType.equals(MimeTypeConstant.PDF)) {
                    //pdf
                    vectorStorePdf(fileInputStream, map);
                } else if (fileType.equals(MimeTypeConstant.DOC) || fileType.equals(MimeTypeConstant.DOCX)) {
                    //doc
                    vectorStoreDoc(fileInputStream, map);
                } else if (fileType.equals(MimeTypeConstant.PPT) || fileType.equals(MimeTypeConstant.PPTX)) {
                    //ppt
                    vectorStorePpt(fileInputStream, map);
                } else if (fileType.equals(MimeTypeConstant.TXT)) {
                    //txt
                    vectorStoreTxt(fileInputStream, map);
                } else if (fileType.equals(MimeTypeConstant.MARKDOWN)) {
                    //md
                    vectorStoreMd(fileInputStream, map);
                }
            }
        } catch (IOException e) {
            throw new BusinessException(KnowledgeBaseExceptionEnum.FILE_PARSE_ERROR);
        }
    }

    public static void vectorStorePdf(InputStream inputStream, Map<String, Object> map) {
        Resource resource = new InputStreamResource(inputStream);
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource,
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build());
        List<Document> documentList = new ArrayList<>();
        pdfReader.get().forEach(document -> {
            documentList.add(new Document(Objects.requireNonNull(document.getText()), map));
        });
        vectorAdd(documentList);
    }

    public static void vectorStoreDoc(InputStream inputStream, Map<String, Object> map) {
        Resource resource = new InputStreamResource(inputStream);
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
        List<Document> documentList = new ArrayList<>();
        tikaDocumentReader.get().forEach(document -> {
            documentList.add(new Document(Objects.requireNonNull(document.getText()), map));
        });
        vectorAdd(documentList);
    }

    public static void vectorStorePpt(InputStream inputStream, Map<String, Object> map) {

    }

    public static void vectorStoreTxt(InputStream inputStream, Map<String, Object> map) {
        Resource resource = new InputStreamResource(inputStream);
        TextReader textReader = new TextReader(resource);
        List<Document> documentList = new ArrayList<>();
        textReader.get().forEach(document -> {
            documentList.add(new Document(Objects.requireNonNull(document.getText()), map));
        });
        vectorAdd(documentList);
    }

    public static void vectorStoreMd(InputStream inputStream, Map<String, Object> map) {
        Resource resource = new InputStreamResource(inputStream);
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(true)
                .withIncludeBlockquote(true)
                .build();
        MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
        List<Document> documentList = new ArrayList<>();
        markdownDocumentReader.get().forEach(document -> {
            documentList.add(new Document(Objects.requireNonNull(document.getText()), map));
        });
        vectorAdd(documentList);
    }

    private static void vectorAdd(List<Document> documentList){
        int batchNumber = 10;
        if(documentList.size() > batchNumber){
            for (int i = 0; i < documentList.size(); i = i + batchNumber) {
                if(documentList.size() - i < batchNumber){
                    vectorStore.add(documentList.subList(i, documentList.size()));
                    break;
                }
                log.debug("正在处理第 {} - {} 条数据", i + 1, i + batchNumber);
                vectorStore.add(documentList.subList(i, i + batchNumber));
            }
        } else{
            vectorStore.add(documentList);
        }
        log.debug("处理完成");
    }


}
