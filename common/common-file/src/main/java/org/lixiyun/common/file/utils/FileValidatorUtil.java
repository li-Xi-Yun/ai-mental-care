package org.lixiyun.common.file.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 文件/文件夹名称验证与创建工具类
 * <p>
 * 提供通用的文件操作功能，包括：
 * <ul>
 *     <li>名称合法性验证（非法字符、长度限制）</li>
 *     <li>同名文件/文件夹存在性检查</li>
 *     <li>安全创建文件和文件夹</li>
 * </ul>
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-13 12:00
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileValidatorUtil {

    // 文件名/文件夹名非法字符正则（Windows和Linux通用）
    private static final Pattern INVALID_NAME_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");

    // 文件名/文件夹名最大长度限制
    private static final int MAX_NAME_LENGTH = 255;

    /**
     * 验证文件名合法性
     * <p>检查文件名是否为空、包含非法字符、超过长度限制</p>
     *
     * @param fileName 文件名
     * @throws BusinessException 当文件名为空或不合法时抛出异常
     */
    public static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            log.error("文件名不能为空");
            throw new BusinessException(FileExceptionEnum.FILE_NAME_INVALID);
        }
        validateNameFormat(fileName, "文件");
    }

    /**
     * 验证文件夹名称合法性
     * <p>检查文件夹名是否为空、包含非法字符、超过长度限制</p>
     *
     * @param folderName 文件夹名称
     * @throws BusinessException 当文件夹名为空或不合法时抛出异常
     */
    public static void validateFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            log.error("文件夹名称不能为空");
            throw new BusinessException(FileExceptionEnum.FOLDER_NAME_INVALID);
        }
        validateNameFormat(folderName, "文件夹");
    }

    /**
     * 验证名称格式（通用方法）
     * <p>检查名称是否包含非法字符、超过长度限制</p>
     *
     * @param name      名称（文件名或文件夹名）
     * @param nameType  名称类型描述（用于日志）
     * @throws BusinessException 当名称不合法时抛出异常
     */
    public static void validateNameFormat(String name, String nameType) {
        if (INVALID_NAME_PATTERN.matcher(name).find()) {
            log.error("{}名称包含非法字符: {}", nameType, name);
            throw new BusinessException(FileExceptionEnum.FILE_NAME_INVALID);
        }

        if (name.length() > MAX_NAME_LENGTH) {
            log.error("{}名称过长: 长度={}, 最大允许长度={}", nameType, name.length(), MAX_NAME_LENGTH);
            throw new BusinessException(FileExceptionEnum.FILE_NAME_INVALID);
        }
    }

    /**
     * 检查指定路径下是否存在同名文件夹
     * <p>使用Stream API高效遍历目录查找同名文件夹</p>
     *
     * @param parentPath 父目录路径 {@link Path}
     * @param folderName 要检查的文件夹名称
     * @return 是否存在同名文件夹
     * @throws BusinessException 当读取目录失败时抛出异常
     */
    public static boolean isFolderExists(Path parentPath, String folderName) {
        try (Stream<Path> pathStream = Files.list(parentPath)) {
            return pathStream.anyMatch(path -> Files.isDirectory(path) &&
                    path.getFileName().toString().equals(folderName));
        } catch (IOException e) {
            log.error("读取父目录内容失败: {}", parentPath, e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 检查指定路径下是否存在同名文件
     * <p>使用Stream API高效遍历目录查找同名文件</p>
     *
     * @param parentPath 父目录路径 {@link Path}
     * @param fileName   要检查的文件名称
     * @return 是否存在同名文件
     * @throws BusinessException 当读取目录失败时抛出异常
     */
    public static boolean isFileExists(Path parentPath, String fileName) {
        try (Stream<Path> pathStream = Files.list(parentPath)) {
            return pathStream.anyMatch(path -> Files.isRegularFile(path) &&
                    path.getFileName().toString().equals(fileName));
        } catch (IOException e) {
            log.error("读取父目录内容失败: {}", parentPath, e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 安全创建新文件夹
     * <p>使用Files.createDirectory()创建文件夹，自动处理权限等问题</p>
     *
     * @param parentPath 父目录路径 {@link Path}
     * @param folderName 新文件夹名称
     * @return 创建后的文件夹路径 {@link Path}
     * @throws BusinessException 当创建失败时抛出异常
     */
    public static Path createNewFolder(Path parentPath, String folderName) {
        Path newFolderPath = parentPath.resolve(folderName);

        try {
            Files.createDirectory(newFolderPath);
            log.info("成功创建文件夹: {}", newFolderPath);
            return newFolderPath;
        } catch (IOException e) {
            log.error("创建文件夹失败: {}", newFolderPath, e);
            throw new BusinessException(FileExceptionEnum.FILE_WRITE_ERROR);
        } catch (SecurityException e) {
            log.error("无权限创建文件夹: {}", newFolderPath, e);
            throw new BusinessException(FileExceptionEnum.FILE_WRITE_ERROR);
        }
    }

    /**
     * 安全创建新的空文件
     * <p>使用Files.createFile()创建空文件，自动处理权限等问题</p>
     *
     * @param parentPath 父目录路径 {@link Path}
     * @param fileName   新文件名称
     * @return 创建后的文件路径 {@link Path}
     * @throws BusinessException 当创建失败时抛出异常
     */
    public static Path createNewFile(Path parentPath, String fileName) {
        Path newFilePath = parentPath.resolve(fileName);

        try {
            Files.createFile(newFilePath);
            log.info("成功创建文件: {}", newFilePath);
            return newFilePath;
        } catch (IOException e) {
            log.error("创建文件失败: {}", newFilePath, e);
            throw new BusinessException(FileExceptionEnum.FILE_WRITE_ERROR);
        } catch (SecurityException e) {
            log.error("无权限创建文件: {}", newFilePath, e);
            throw new BusinessException(FileExceptionEnum.FILE_WRITE_ERROR);
        }
    }
}