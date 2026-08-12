package org.lixiyun.common.file.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.constant.MimeTypeConstant;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.file.constant.FileConstant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件内容读取工具类
 * <p>
 * 提供文本文件的读取能力，以及文件类型判断、MIME类型映射等辅助功能。
 * 非文本文件（图片/PDF/Word等）由调用方直接以数据流方式返回，无需提取文本内容。
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-13 11:10
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileContentReaderUtil {

    /**
     * 读取纯文本文件内容
     *
     * @param filePath 文件路径 {@link Path}
     * @return 文本内容
     * @throws BusinessException 当读取失败时抛出异常
     */
    public static String readTextFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            log.debug("成功读取文本文件, 大小: {} bytes", Files.size(filePath));
            return content;
        } catch (IOException e) {
            log.error("读取文本文件失败: {}", filePath, e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        } catch (OutOfMemoryError e) {
            log.error("文件过大导致内存溢出: {}", filePath, e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 获取文件扩展名（小写，不含点号）
     *
     * @param fileName 文件名
     * @return 小写的扩展名，无扩展名时返回 {@link MimeTypeConstant#FILE_TYPE_UNKNOWN}
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return MimeTypeConstant.FILE_TYPE_UNKNOWN;
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return MimeTypeConstant.FILE_TYPE_UNKNOWN;
    }

    /**
     * 判断是否为文本文件
     *
     * @param fileType 文件扩展名（小写）
     * @return 是否为文本文件
     */
    public static boolean isTextFile(String fileType) {
        return FileConstant.TEXT_EXTENSIONS.contains(fileType);
    }

    /**
     * 确定文件的MIME类型
     * <p>基于文件扩展名映射MIME类型，使用已有常量避免硬编码</p>
     *
     * @param fileType 文件扩展名（小写）
     * @return MIME类型字符串
     */
    public static String determineMimeType(String fileType) {
        if (FileConstant.IMAGE_EXTENSIONS.contains(fileType)) {
            return switch (fileType) {
                case MimeTypeConstant.PNG -> MimeTypeConstant.IMAGE_PNG;
                case MimeTypeConstant.JPG, MimeTypeConstant.JPEG -> MimeTypeConstant.IMAGE_JPEG;
                case MimeTypeConstant.GIF -> MimeTypeConstant.IMAGE_GIF;
                case MimeTypeConstant.BMP -> MimeTypeConstant.IMAGE_BMP;
                default -> MimeTypeConstant.IMAGE;
            };
        }

        if (FileConstant.PDF_EXTENSIONS.contains(fileType)) {
            return "application/" + MimeTypeConstant.PDF;
        }

        if (FileConstant.OFFICE_EXTENSIONS.contains(fileType)) {
            return switch (fileType) {
                case MimeTypeConstant.DOC -> "application/msword";
                case MimeTypeConstant.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case MimeTypeConstant.XLS -> "application/vnd.ms-excel";
                case MimeTypeConstant.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case MimeTypeConstant.PPT -> "application/vnd.ms-powerpoint";
                case MimeTypeConstant.PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                default -> FileConstant.MIME_APPLICATION_OCTET_STREAM;
            };
        }

        if (FileConstant.MARKDOWN_EXTENSIONS.contains(fileType)) {
            return FileConstant.MIME_TEXT_MARKDOWN;
        }

        return FileConstant.MIME_TEXT_PLAIN;
    }

    /**
     * 安全获取文件大小
     *
     * @param filePath 文件路径 {@link Path}
     * @return 文件大小（字节），获取失败返回0
     */
    public static long getFileSizeSafe(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            log.warn("获取文件大小失败: {}", filePath, e);
            return 0L;
        }
    }
}
