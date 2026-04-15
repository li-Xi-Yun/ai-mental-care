package org.lixiyun.file.storage;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lixiyun.file.storage.FileStorage.StorageType.FILE;

/**
 * @author lixiyun
 * @since 2026-01-05 21:45
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileStorage {

    private final FileUrlProperties fileUrlProperties;

    /**
     * 本地文件存储
     * @param file 文件
     * @return 文件名
     */
    public String localFileStorage(MultipartFile file) {
        return localFileStorage(new MultipartFile[]{file}, FILE).get(0);
    }

    /**
     * 本地文件存储
     * @param file 文件
     * @param storageType 文件存储类型 {@link StorageType}
     * @return 文件名
     */
    public String localFileStorage(MultipartFile file, StorageType storageType) {
        return localFileStorage(new MultipartFile[]{file}, storageType).get(0);
    }

    /**
     * 本地文件存储
     * @param files 文件集合
     * @return 文件名集合
     */
    public List<String> localFileStorage(MultipartFile[] files){
        return localFileStorage(files, FILE);
    }

    /**
     * 本地文件存储
     * @param files 文件集合
     * @param storageType 文件存储类型 {@link StorageType}
     * @return 文件名集合
     */
    public List<String> localFileStorage(MultipartFile[] files, StorageType storageType){
        String uploadUrl = getFileUrl(storageType);
        
        List<String> list = new ArrayList<>();
        for (MultipartFile file : files) {
            Path targetDir = Paths.get(uploadUrl);
            try {
                // 确保目录存在
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }

                // 生成新的文件名
                LocalDateTime currentDate  = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                String format = currentDate.format(formatter);
                String newFileName  = format + StrUtil.uuid() + file.getOriginalFilename();
                list.add(uploadUrl + newFileName);

                // 保存文件
                Path targetPath = targetDir.resolve(newFileName);
                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("文件上传失败", e);
                throw new BusinessException(SystemExceptionEnum.FILE_UPLOAD_ERROR);
            }
        }
        return list;
    }

    /**
     * 本地文件存储
     * @param fileMap 文件名-文件
     */
    @Async
    public void localFileStorage(Map<String, MultipartFile> fileMap) {
        localFileStorage(fileMap, StorageType.FILE);
    }

    /**
     * 本地文件存储
     * @param fileMap 文件名-文件
     * @param storageType 文件存储类型 {@link StorageType}
     */
    public void localFileStorage(Map<String, MultipartFile> fileMap, StorageType storageType) {
        String uploadUrl = getFileUrl(storageType);
        fileMap.forEach((key, value) -> {
            Path targetDir = Paths.get(uploadUrl);
            try {
                // 确保目录存在
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }

                // 保存文件
                Path targetPath = targetDir.resolve(key);
                Files.copy(value.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("文件上传失败", e);
                throw new BusinessException(SystemExceptionEnum.FILE_UPLOAD_ERROR);
            }
        });
    }

    /**
     * 本地文件删除
     * @param fileNames 文件名集合
     */
    @Async
    public void localFileDelete(List<String> fileNames) {
        localFileDelete(fileNames, StorageType.FILE);
    }

    /**
     * 本地文件删除
     * @param fileNames 文件名集合
     * @param storageType 文件存储类型 {@link StorageType}
     */
    @Async
    public void localFileDelete(List<String> fileNames, StorageType storageType) {
        String uploadUrl = getFileUrl(storageType);

        for(String fileName : fileNames){
            Path path = Paths.get(uploadUrl + fileName);
            localFileDelete(path);
        }
    }

    /**
     * 本地文件删除
     * @param fileUrl 文件本地路径
     */
    @Async
    public void localFileDelete(String fileUrl) {
        Path path = Paths.get(fileUrl);
        localFileDelete(path);
    }

    /**
     * 本地文件删除
     * @param path 文件本地路径
     */
    public void localFileDelete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            // 记录删除失败的日志，但不中断其他文件的删除
            log.error("本地文件删除失败，路径: {}，异常：{} ", path, e);
        }
    }

    // ===================== 二进制数据存储方法 =====================
    /**
     * 通用二进制数据本地存储（指定存储类型）
     * @param binaryData 二进制字节数组（任意文件：音频/图片/视频/文档等）
     * @param fileSuffix 文件后缀名（示例：wav、mp3、png、jpg、pdf，无需带 . ）
     * @param storageType 文件存储类型 {@link StorageType}
     * @return 存储后的文件完整路径
     */
    public String localBinaryStorage(byte[] binaryData, String fileSuffix, StorageType storageType) {
        // 核心参数校验（对齐原有逻辑）
        if (binaryData == null || binaryData.length == 0) {
            log.error("二进制数据为空，文件存储失败");
            throw new BusinessException(SystemExceptionEnum.FILE_DATA_EMPTY);
        }
        if (StrUtil.isBlank(fileSuffix)) {
            log.error("文件后缀名为空，文件存储失败");
            throw new BusinessException(SystemExceptionEnum.FILE_EXTENTION_ERROR);
        }

        // 获取配置的存储根路径
        String uploadUrl = getFileUrl(storageType);
        try {
            Path targetDir = Paths.get(uploadUrl);
            // 自动创建多级目录（对齐原有逻辑）
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // 生成标准文件名（完全复用你项目的命名规则：日期+UUID）
            String dateFormat = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            // 自动处理后缀，统一格式：.wav
            String suffix = fileSuffix.startsWith(".") ? fileSuffix : "." + fileSuffix;
            String newFileName = dateFormat + StrUtil.uuid() + suffix;

            // 写入二进制数据到文件（JDK NIO，与原有文件写入保持一致）
            Path targetPath = targetDir.resolve(newFileName);
            Files.write(targetPath, binaryData);

            // 返回完整文件路径（与原有方法返回格式完全一致）
            String filePath = uploadUrl + newFileName;
            log.info("二进制文件存储成功 | 存储类型:{} | 路径:{}", storageType, filePath);
            return filePath;

        } catch (IOException e) {
            log.error("二进制文件存储失败 | 存储类型:{}", storageType, e);
            throw new BusinessException(SystemExceptionEnum.FILE_UPLOAD_ERROR);
        }
    }

    /**
     * 获取文件存储路径
     * @param storageType 文件存储类型 {@link StorageType}
     * @return 文件存储目录路径
     */
    private String getFileUrl(StorageType storageType) {
        return switch (storageType) {
            case IMAGE -> fileUrlProperties.getUploadImages();
            case VIDEO -> fileUrlProperties.getUploadVideos();
            case AUDIO -> fileUrlProperties.getUploadAudio();
            case CONVERSATION -> fileUrlProperties.getUploadConversations();
            default -> fileUrlProperties.getUploadFiles();
        };
    }

    public enum StorageType {
        FILE,
        IMAGE,
        VIDEO,
        AUDIO,
        CONVERSATION;
    }

}
