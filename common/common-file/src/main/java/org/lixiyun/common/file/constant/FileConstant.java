package org.lixiyun.common.file.constant;

import org.lixiyun.common.core.constant.MimeTypeConstant;

import java.util.Set;

/**
 * 文件操作常量
 * <p>定义文件类型扩展名集合、MIME类型映射等常量</p>
 *
 * @author lixiyun
 * @since 2026-07-13 11:00
 */
public interface FileConstant {

    int FILE_TYPE = 0;

    int FOLDER_TYPE = 1;

    String ZIP_EXTENSION = ".zip";

    // 文本文件扩展名集合
    Set<String> TEXT_EXTENSIONS = Set.of(
            MimeTypeConstant.TXT, "md", "markdown", "json",
            MimeTypeConstant.XML, MimeTypeConstant.HTML, "htm",
            "java", "py", "js", "css", "sql", "yaml", "yml", "properties"
    );

    // 图片文件扩展名集合
    Set<String> IMAGE_EXTENSIONS = Set.of(
            MimeTypeConstant.PNG, MimeTypeConstant.JPG, MimeTypeConstant.JPEG,
            MimeTypeConstant.GIF, MimeTypeConstant.BMP
    );

    // PDF文件扩展名集合
    Set<String> PDF_EXTENSIONS = Set.of(MimeTypeConstant.PDF);

    // Office文档扩展名集合
    Set<String> OFFICE_EXTENSIONS = Set.of(
            MimeTypeConstant.DOC, MimeTypeConstant.DOCX,
            MimeTypeConstant.XLS, MimeTypeConstant.XLSX,
            MimeTypeConstant.PPT, MimeTypeConstant.PPTX
    );

    // Markdown文件扩展名集合
    Set<String> MARKDOWN_EXTENSIONS = Set.of("md", "markdown");

    // 图片MIME类型映射
    String IMAGE_MIME_PNG = MimeTypeConstant.IMAGE_PNG;
    String IMAGE_MIME_JPG = MimeTypeConstant.IMAGE_JPG;
    String IMAGE_MIME_JPEG = MimeTypeConstant.IMAGE_JPEG;
    String IMAGE_MIME_GIF = MimeTypeConstant.IMAGE_GIF;
    String IMAGE_MIME_BMP = MimeTypeConstant.IMAGE_BMP;

    // 文档MIME类型
    String MIME_TEXT_PLAIN = "text/plain";
    String MIME_TEXT_MARKDOWN = "text/markdown";
    String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
}
