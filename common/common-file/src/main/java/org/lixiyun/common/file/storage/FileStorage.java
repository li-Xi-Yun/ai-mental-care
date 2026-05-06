package org.lixiyun.common.file.storage;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lixiyun.common.file.storage.FileStorage.StorageType.FILE;

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
     * 将单个文件存储到本地默认路径（/static-resources/files/）
     *
     * @param file 待上传的文件对象
     * @return 存储后的完整文件路径名
     */
    public String localFileStorage(MultipartFile file) {
        return localFileStorage(new MultipartFile[]{file}, FILE).get(0);
    }

    /**
     * 将单个文件存储到本地指定类型路径
     *
     * @param file 待上传的文件对象
     * @param storageType 文件存储类型枚举 {@link StorageType}
     * @return 存储后的完整文件路径名
     */
    public String localFileStorage(MultipartFile file, StorageType storageType) {
        return localFileStorage(new MultipartFile[]{file}, storageType).get(0);
    }

    /**
     * 批量将文件存储到本地默认路径（/static-resources/files/）
     *
     * @param files 待上传的文件数组
     * @return 存储后的完整文件路径名列表，顺序与输入数组一致
     */
    public List<String> localFileStorage(MultipartFile[] files){
        return localFileStorage(files, FILE);
    }

    /**
     * 批量将文件存储到本地指定类型路径
     * <p>文件夹按年月划分（格式：yyyy-MM），文件名使用内容MD5值命名</p>
     *
     * @param files 待上传的文件数组
     * @param storageType 文件存储类型枚举 {@link StorageType}
     * @return 存储后的完整文件路径名列表，顺序与输入数组一致
     */
    public List<String> localFileStorage(MultipartFile[] files, StorageType storageType){
        String uploadUrl = getFileUrl(storageType);
        
        List<String> list = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                // 1. 获取原始文件名后缀
                String originalFilename = file.getOriginalFilename();
                String suffix = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
                }

                // 2. 计算文件内容的 MD5 作为新文件名
                String md5 = SecureUtil.md5(file.getInputStream());
                String newFileName = md5 + suffix;

                // 3. 按年月创建子目录 (格式: yyyy-MM)
                LocalDateTime currentDate = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
                String monthDir = currentDate.format(formatter);
                Path targetDir = Paths.get(uploadUrl, monthDir);

                // 确保目录存在
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }

                // 4. 保存文件并记录路径
                Path targetPath = targetDir.resolve(newFileName);
                // 注意：由于 InputStream 已被 SecureUtil.md5 读取，这里需要重新获取或使用 bytes
                Files.write(targetPath, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                
                list.add(targetPath.toString());
            } catch (IOException e) {
                log.error("文件上传失败", e);
                throw new BusinessException(SystemExceptionEnum.FILE_UPLOAD_ERROR);
            }
        }
        return list;
    }

    /**
     * 异步批量存储文件到本地默认路径（以Map中的Key作为文件名）
     *
     * @param fileMap 键值对映射，Key为目标文件名，Value为文件对象
     */
    @Async
    public void localFileStorageAsync(Map<String, MultipartFile> fileMap) {
        localFileStorage(fileMap, StorageType.FILE);
    }

    /**
     * 异步批量存储文件到本地指定类型路径（以Map中的Key作为文件名）
     *
     * @param fileMap 键值对映射，Key为目标文件名，Value为文件对象
     * @param storageType 文件存储类型枚举 {@link StorageType}
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
     * 异步批量删除本地默认路径下的文件
     *
     * @param fileNames 待删除的文件名列表（不含路径前缀）
     */
    @Async
    public void localFileDeleteAsync(List<String> fileNames) {
        localFileDeleteAsync(fileNames, StorageType.FILE);
    }

    /**
     * 异步批量删除本地指定类型路径下的文件
     *
     * @param fileNames 待删除的文件名列表（不含路径前缀）
     * @param storageType 文件存储类型枚举 {@link StorageType}
     */
    @Async
    public void localFileDeleteAsync(List<String> fileNames, StorageType storageType) {
        String uploadUrl = getFileUrl(storageType);

        for(String fileName : fileNames){
            Path path = Paths.get(uploadUrl + fileName);
            localFileDelete(path);
        }
    }

    /**
     * 异步删除指定完整路径的本地文件
     *
     * @param fileUrl 文件的完整本地绝对路径或相对路径字符串
     */
    @Async
    public void localFileDeleteAsync(String fileUrl) {
        Path path = Paths.get(fileUrl);
        localFileDelete(path);
    }

    /**
     * 同步删除指定路径的本地文件（内部核心方法）
     * <p>若删除失败仅记录错误日志，不会抛出异常</p>
     *
     * @param path 待删除文件的路径对象
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
            case SKILL -> fileUrlProperties.getUploadSkills();
            case PROMPT -> fileUrlProperties.getUploadPrompts();
            default -> fileUrlProperties.getUploadFiles();
        };
    }

    public enum StorageType {
        FILE,
        IMAGE,
        VIDEO,
        AUDIO,
        CONVERSATION,
        SKILL,
        PROMPT
         ;
    }

}
