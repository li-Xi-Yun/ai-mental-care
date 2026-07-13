package org.lixiyun.common.agent.prompt.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.prompt.dto.FolderContentItem;
import org.lixiyun.common.agent.prompt.service.PromptService;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.lixiyun.common.file.constant.FileConstant;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Prompt服务实现类
 * @author lixiyun
 * @since 2026-04-17 20:55
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final FileUrlProperties fileUrlProperties;

    @Override
    public String getFileContent(String folderUrl, String fileName) {
        String basePath = fileUrlProperties.getUploadPrompts();
        Path filePath = findPromptFileByPath(basePath, folderUrl, fileName);
        log.debug("查询指定提示词文件内容, 文件夹路径: {}, 文件名: {}", folderUrl, fileName);

        if (filePath == null) {
            log.warn("提示词文件不存在, 文件夹路径: {}, 文件名: {}", folderUrl, fileName);
            return "";
        }

        try {
            String content = Files.readString(filePath);
            log.info("获取提示词文件内容成功, 文件大小: {} 字符", content.length());
            return content;
        } catch (IOException e) {
            log.error("读取提示词文件内容失败: {}", filePath, e);
            return "";
        }
    }

    @Override
    public void updateFileContent(String folderUrl, String fileName, String content) {
        String basePath = fileUrlProperties.getUploadPrompts();
        Path filePath = findPromptFileByPath(basePath, folderUrl, fileName);
        log.debug("修改提示词文件内容, 文件夹路径: {}, 文件名: {}, 内容长度: {}", folderUrl, fileName, content.length());

        if (filePath == null) {
            log.error("提示词文件不存在, 文件夹路径: {}, 文件名: {}", folderUrl, fileName);
            throw new RuntimeException("提示词文件不存在: " + folderUrl + "/" + fileName);
        }

        try {
            Files.writeString(filePath, content);
            log.debug("提示词文件内容覆盖成功: {}", filePath);
        } catch (IOException e) {
            log.error("写入提示词文件内容失败: {}", filePath, e);
            throw new RuntimeException("写入提示词文件内容失败", e);
        }

        PromptUtil.setPrompt(fileName, content);
        log.info("提示词文件内容修改完成并同步到缓存, 文件夹路径: {}, 文件名: {}", folderUrl, fileName);
    }

    @Override
    public List<FolderContentItem> getFolderContents(String folderUrl) {
        String basePath = fileUrlProperties.getUploadPrompts();
        Path folderPath = buildFolderPath(basePath, folderUrl);
        log.debug("查询指定文件夹下的所有内容, 文件夹路径: {}", folderUrl);

        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            log.warn("文件夹不存在或不是目录: {}", folderPath.toAbsolutePath());
            return List.of();
        }

        List<FolderContentItem> contents = new ArrayList<>();
        try (var stream = Files.list(folderPath)) {
            stream.map(path -> {
                        String name = path.getFileName().toString();
                        Integer type = Files.isDirectory(path) ? FileConstant.Folder_TYPE : FileConstant.File_TYPE;
                        return FolderContentItem.builder()
                                .name(name)
                                .type(type)
                                .build();
                    })
                    .sorted(Comparator.comparing(FolderContentItem::getName))
                    .forEach(contents::add);
        } catch (IOException e) {
            log.error("遍历文件夹失败: {}", folderPath, e);
            throw new RuntimeException("获取文件夹内容失败", e);
        }

        log.info("获取文件夹内容成功, 路径: {}, 数量: {}", folderUrl, contents.size());
        return contents;
    }

    /**
     * 根据文件夹路径和文件名查找提示词文件（支持多级目录结构）
     * @param basePath 提示词根文件夹路径
     * @param folderUrl 文件夹路径（相对于根路径的多级目录）
     * @param fileName 文件名（可带或不带扩展名）
     * @return 文件路径，如果不存在则返回null
     */
    private Path findPromptFileByPath(String basePath, String folderUrl, String fileName) {
        Path promptsPath = Paths.get(basePath);
        
        if (!Files.exists(promptsPath) || !Files.isDirectory(promptsPath)) {
            log.warn("提示词根文件夹不存在或不是目录: {}", promptsPath.toAbsolutePath());
            return null;
        }

        Path filePath = buildFolderPath(basePath, folderUrl);
        
        Path fileWithExt = filePath.resolve(fileName);
        if (Files.exists(fileWithExt) && Files.isRegularFile(fileWithExt)) {
            return fileWithExt;
        }
        
        String nameWithoutExt = getFileNameWithoutExtension(fileName);
        if (nameWithoutExt != null && !nameWithoutExt.equals(fileName)) {
            Path fileWithoutExt = filePath.resolve(nameWithoutExt);
            if (Files.exists(fileWithoutExt) && Files.isRegularFile(fileWithoutExt)) {
                return fileWithoutExt;
            }
        }
        
        log.debug("提示词文件不存在, 完整路径: {}/{}", folderUrl, fileName);
        return null;
    }

    /**
     * 构建提示词文件路径（支持带扩展名和不带扩展名的文件名）
     * @param basePath 提示词根文件夹路径
     * @param folderUrl 文件夹路径（相对于根路径的多级目录）
     */
    private Path buildFolderPath(String basePath, String folderUrl) {
        String[] folders = folderUrl == null || folderUrl.isEmpty() ? new String[0] : folderUrl.split("/");
        return Paths.get(basePath, folders);
    }

    /**
     * 查找提示词文件路径（支持带扩展名和不带扩展名的文件名）
     * @param basePath 提示词文件夹路径
     * @param fileName 文件名（可带或不带扩展名）
     * @return 文件路径，如果不存在则返回null
     */
    private Path findPromptFile(String basePath, String fileName) {
        Path promptsPath = Paths.get(basePath);
        
        if (!Files.exists(promptsPath) || !Files.isDirectory(promptsPath)) {
            return null;
        }

        try (var stream = Files.list(promptsPath)) {
            return stream.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            String nameWithoutExt = getFileNameWithoutExtension(name);
                            return fileName.equals(name) || fileName.equals(nameWithoutExt);
                        })
                        .findFirst()
                        .orElse(null);
        } catch (IOException e) {
            log.error("查找提示词文件失败: {}", fileName, e);
            return null;
        }
    }

    /**
     * 获取不带扩展名的文件名
     * @param fileName 完整文件名
     * @return 不带扩展名的文件名，如果文件名为空则返回null
     */
    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
}
