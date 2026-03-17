package org.lixiyun.server.infrastructure.storage;

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

import static org.lixiyun.server.infrastructure.storage.FileStorage.StorageType.FILE;

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

    /**
     * 获取文件存储路径
     * @param storageType 文件存储类型 {@link StorageType}
     * @return 文件存储目录路径
     */
    private String getFileUrl(StorageType storageType) {
        return switch (storageType) {
            case IMAGE -> fileUrlProperties.getUploadImages();
            case VIDEO -> fileUrlProperties.getUploadVideos();
            case CONVERSATION -> fileUrlProperties.getUploadConversations();
            default -> fileUrlProperties.getUploadFiles();
        };
    }

    public enum StorageType {
        FILE,
        IMAGE,
        VIDEO,
        CONVERSATION
    }

}
