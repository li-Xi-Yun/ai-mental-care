package org.lixiyun.common.file.utils;

import cn.hutool.core.io.FileUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文件处理工具类
 *
 * @author lixiyun
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileUtils extends FileUtil {

    private static final String FILE_EXTENTION_SPLIT = ".";

    /**
     * 格式化文件大小
     * @param originalSize 原始文件大小
     * @param originalUnit 原始单位（如"Byte"）
     * @param targetUnit 目标单位（如"KB", "MB", "GB"等）
     * @return 格式化后的文件大小字符串
     */
    public static String formatFileSize(Long originalSize, FileSizeUnitConstant originalUnit, FileSizeUnitConstant targetUnit) {
        if (originalSize == null || originalSize < 0) {
            return "0" + targetUnit.unit;
        }

        if(originalUnit == targetUnit){
            return originalSize + targetUnit.unit;
        }

        // 转换为字节
        long sizeInBytes = originalSize;
        if (originalUnit != FileSizeUnitConstant.Byte) {
            sizeInBytes = originalSize * originalUnit.value;
        }

        // 转为目标单位（使用 double 避免整除）
        double sizeInTargetUnit = (double) sizeInBytes / targetUnit.value;

        // 保留1位小数，但如果是整数则不显示 .0
        if (sizeInTargetUnit == (long) sizeInTargetUnit) {
            return (long) sizeInTargetUnit + targetUnit.unit;
        } else {
            return String.format("%.1f", sizeInTargetUnit) + targetUnit.unit;
        }
    }

    public static String formatFileSizeByteToKB(Long sizeInBytes) {
        return formatFileSize(sizeInBytes, FileSizeUnitConstant.Byte, FileSizeUnitConstant.KB);
    }

    /**
     * 校验文件路径安全性，防止路径穿透攻击
     * <p>
     * 检查目标路径是否在允许的基础目录范围内，防止通过 "../" 等方式访问敏感文件。
     * 该方法会进行以下安全检查：
     * <ul>
     *   <li>检查路径是否包含路径遍历字符（..）</li>
     *   <li>规范化路径并验证是否在基础目录内</li>
     *   <li>检查路径是否为绝对路径（防止直接访问系统目录）</li>
     *   <li>检查路径中是否包含非法字符</li>
     * </ul>
     *
     * @param targetPath      目标文件/目录路径
     * @param baseDirectory   允许访问的基础目录（安全边界）
     * @return boolean true-路径安全，false-存在安全风险
     */
    public static boolean isSecurePath(String targetPath, String baseDirectory) {
        if (StringUtils.isBlank(targetPath) || StringUtils.isBlank(baseDirectory)) {
            log.debug("路径校验失败：目标路径或基础目录为空");
            return false;
        }

        try {
            Path basePath = Paths.get(baseDirectory).normalize().toAbsolutePath();
            Path resolvedPath = basePath.resolve(targetPath).normalize().toAbsolutePath();

            if (!resolvedPath.startsWith(basePath)) {
                log.warn("路径穿透攻击检测：目标路径 {} 超出基础目录 {}", resolvedPath, basePath);
                return false;
            }

            if (targetPath.contains("..")) {
                log.warn("路径遍历攻击检测：目标路径包含非法序列 ..");
                return false;
            }

            List<String> dangerousPatterns = List.of(
                    "/etc/", "/proc/", "/sys/",
                    "C:\\Windows\\", "C:\\ProgramData\\",
                    "..\\", "../"
            );

            String normalizedTarget = targetPath.replace("\\", "/");
            for (String pattern : dangerousPatterns) {
                if (normalizedTarget.toLowerCase().contains(pattern.toLowerCase())) {
                    log.warn("危险路径模式检测：目标路径包含敏感路径 {}", pattern);
                    return false;
                }
            }

            log.debug("路径校验通过：{} 在安全范围 {} 内", resolvedPath, basePath);
            return true;

        } catch (InvalidPathException e) {
            log.error("路径校验异常：无效的路径格式 - {}", targetPath, e);
            return false;
        } catch (Exception e) {
            log.error("路径校验异常：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 安全地解析子路径，防止路径穿透攻击
     * <p>
     * 在基础目录下解析子路径，如果子路径不安全则返回null。
     * 推荐在所有需要根据用户输入构建文件路径的场景中使用此方法。
     * </p>
     *
     * @param baseDirectory 基础目录（安全边界）
     * @param childPath     子路径（用户输入）
     * @return Path 解析后的安全路径，如果不安全返回null
     */
    public static Path resolveSecurePath(String baseDirectory, String childPath) {
        if (!isSecurePath(childPath, baseDirectory)) {
            return null;
        }

        try {
            return Paths.get(baseDirectory).resolve(childPath).normalize().toAbsolutePath();
        } catch (InvalidPathException e) {
            log.error("安全路径解析失败：{}", childPath, e);
            return null;
        }
    }

    @Getter
    public enum FileSizeUnitConstant {
        Byte(1L, "Byte"),
        KB(1024L, "KB"),
        MB(1024L * 1024L, "MB"),
        GB(1024L * 1024L * 1024L, "GB"),
        TB(1024L * 1024L * 1024L * 1024L, "TB");

        private final long value;
        private final String unit;

        FileSizeUnitConstant(long value, String unit) {
            this.value = value;
            this.unit = unit;
        }

    }
}