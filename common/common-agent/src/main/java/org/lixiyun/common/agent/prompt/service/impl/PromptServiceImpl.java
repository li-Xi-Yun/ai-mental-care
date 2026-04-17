package org.lixiyun.common.agent.prompt.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.prompt.service.PromptService;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    public List<String> getAllFileNames() {
        String basePath = fileUrlProperties.getUploadPrompts();
        Path promptsPath = Paths.get(basePath);
        log.debug("获取所有提示词文件名，路径: {}", basePath);

        if (!Files.exists(promptsPath) || !Files.isDirectory(promptsPath)) {
            log.warn("提示词文件夹不存在或不是目录: {}", promptsPath.toAbsolutePath());
            return List.of();
        }

        List<String> fileNames = new ArrayList<>();
        try (var stream = Files.list(promptsPath)) {
            stream.filter(Files::isRegularFile)
                  .map(path -> getFileNameWithoutExtension(path.getFileName().toString()))
                  .sorted(String::compareTo)
                  .forEach(fileNames::add);
        } catch (IOException e) {
            log.error("遍历提示词文件夹失败: {}", promptsPath, e);
            throw new RuntimeException("获取提示词文件列表失败", e);
        }

        log.info("获取所有提示词文件名成功，数量: {}", fileNames.size());
        return fileNames;
    }

    @Override
    public String getFileContent(String fileName) {
        String basePath = fileUrlProperties.getUploadPrompts();
        Path filePath = findPromptFile(basePath, fileName);
        log.debug("查询指定提示词文件内容，文件名: {}", fileName);

        if (filePath == null) {
            log.warn("提示词文件不存在: {}", fileName);
            return "";
        }

        try {
            String content = Files.readString(filePath);
            log.info("获取提示词文件内容成功，文件大小: {} 字符", content.length());
            return content;
        } catch (IOException e) {
            log.error("读取提示词文件内容失败: {}", filePath, e);
            return "";
        }
    }

    @Override
    public void updateFileContent(String fileName, String content) {
        String basePath = fileUrlProperties.getUploadPrompts();
        Path filePath = findPromptFile(basePath, fileName);
        log.debug("修改提示词文件内容，文件名: {}, 内容长度: {}", fileName, content.length());

        if (filePath == null) {
            log.error("提示词文件不存在: {}", fileName);
            throw new RuntimeException("提示词文件不存在: " + fileName);
        }

        try {
            Files.writeString(filePath, content);
            log.debug("提示词文件内容覆盖成功: {}", filePath);
        } catch (IOException e) {
            log.error("写入提示词文件内容失败: {}", filePath, e);
            throw new RuntimeException("写入提示词文件内容失败", e);
        }

        PromptUtil.setPrompt(fileName, content);
        log.info("提示词文件内容修改完成并同步到缓存，文件名: {}", fileName);
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
