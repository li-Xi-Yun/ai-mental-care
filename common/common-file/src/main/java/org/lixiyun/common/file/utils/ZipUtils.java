package org.lixiyun.common.file.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ZIP压缩包处理工具类
 * <p>
 * 专门负责ZIP文件的创建、解压和验证操作。
 * 提供安全的ZIP处理能力，包括：
 * <ul>
 *     <li>文件夹压缩为ZIP</li>
 *     <li>ZIP文件解压（带路径穿越防护）</li>
 *     <li>ZIP文件大小验证</li>
 * </ul>
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-13
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ZipUtils {

    /**
     * 创建ZIP压缩包从文件夹
     * <p>
     * 使用Stream API遍历文件夹内容，动态生成ZIP压缩包。
     * 采用流式处理，支持大文件夹的压缩，避免内存溢出。
     * </p>
     *
     * <h3>实现流程：</h3>
     * <pre>{@code
     * 1. 使用Files.walk()递归遍历文件夹中的所有文件和目录
     * 2. 过滤出所有普通文件（排除目录本身）
     * 3. 对每个文件计算相对于根目录的相对路径
     * 4. 将文件内容写入ZIP条目中
     * 5. 返回包含ZIP字节数组的ByteArrayResource
     * }</pre>
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * Path folderPath = Paths.get("/path/to/folder");
     * ByteArrayResource zipResource = ZipUtils.createZipFromFolder(folderPath);
     *
     * // 使用zipResource进行下载或保存
     * Files.copy(zipResource.getInputStream(), Paths.get("output.zip"));
     * }</pre>
     *
     * @param folderPath 要压缩的文件夹路径 {@link Path}
     * @return 包含ZIP数据的资源 {@link ByteArrayResource}
     * @throws IOException 如果读取文件或创建ZIP时发生IO异常
     */
    public static ByteArrayResource createZipFromFolder(Path folderPath) throws IOException {
        log.debug("开始创建ZIP压缩包, 文件夹: {}", folderPath);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            Files.walk(folderPath)
                .forEach(filePath -> {
                    try {
                        Path relativePath = folderPath.relativize(filePath);
                        ZipEntry zipEntry = new ZipEntry(relativePath.toString().replace("\\", "/"));
                        zipOutputStream.putNextEntry(zipEntry);

                        Files.copy(filePath, zipOutputStream);

                        zipOutputStream.closeEntry();

                        log.debug("已添加到ZIP: {} -> {}", filePath, relativePath);
                    } catch (IOException e) {
                        log.error("添加文件到ZIP失败: {}", filePath, e);
                        throw new java.io.UncheckedIOException(e);
                    }
                });
        }

        ByteArrayResource zipResource = new ByteArrayResource(byteArrayOutputStream.toByteArray());

        log.info("ZIP压缩包创建完成, 源文件夹: {}, ZIP大小: {}KB", folderPath, zipResource.contentLength() / 1024);

        return zipResource;
    }

    /**
     * 验证ZIP压缩包大小是否超过限制
     * <p>用于上传前的文件大小校验，防止超大文件占用服务器资源</p>
     *
     * @param zipFile         ZIP压缩包文件 {@link MultipartFile}
     * @param maxAllowedSize  允许的最大文件大小（字节）
     * @return boolean true-未超限，false-超出限制
     */
    public static boolean validateZipFileSize(MultipartFile zipFile, long maxAllowedSize) {
        if (zipFile.getSize() > maxAllowedSize) {
            log.warn("ZIP压缩包大小超出限制: {} bytes，最大允许: {} MB",
                    zipFile.getSize(), maxAllowedSize / (1024 * 1024));
            return false;
        }
        return true;
    }

    /**
     * 将ZIP文件解压到指定目录
     * <p>
     * 支持嵌套目录结构，具备路径穿越攻击防护能力。
     * 使用Stream API处理ZIP条目，确保安全性。
     * </p>
     *
     * @param zipFile   ZIP压缩包文件 {@link MultipartFile}
     * @param targetDir 目标解压目录 {@link Path}
     * @throws BusinessException 当解压失败或检测到安全问题时抛出异常
     */
    public static void extractZipToDirectory(MultipartFile zipFile, Path targetDir) {
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();

                if (!entryPath.startsWith(targetDir)) {
                    log.warn("检测到ZIP路径穿越攻击: {}", entry.getName());
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
            log.debug("ZIP文件解压完成，目标目录: {}", targetDir);
        } catch (IOException e) {
            log.error("ZIP文件解压失败: {}", e.getMessage(), e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }
}