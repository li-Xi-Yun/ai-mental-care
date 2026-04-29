package org.lixiyun.common.agent.prompt.utils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词工具类
 * <p>用于管理和加载提示词文件，支持项目启动时自动加载所有提示词文件到内存中</p>
 *
 * @author lixiyun
 * @since 2026-04-17 19:30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptUtil {

    // key:文件名（不含扩展名），value:提示词文本内容，key要与constant/prompt/ 中定义的常量名一致
    private static final ConcurrentHashMap<String, String> PROMPT_MAP = new ConcurrentHashMap<>();

    private final FileUrlProperties fileUrlProperties;

    /**
     * 初始化方法，在项目启动时自动执行
     * <p>从配置的prompts文件夹中加载所有提示词文件到内存缓存中</p>
     */
    @PostConstruct
    public void init() {
        long start = System.currentTimeMillis();
        loadPrompts();
        log.info("提示词初始化完成，共加载提示词数量：{}，耗时：{}ms", PROMPT_MAP.size(), System.currentTimeMillis() - start);
    }

    /**
     * 从配置的prompts文件夹中加载所有提示词文件（支持多层级目录结构）
     */
    private void loadPrompts() {
        String promptsFolderUrl = fileUrlProperties.getUploadPrompts();
        Path promptsPath = Paths.get(promptsFolderUrl);

        if (!Files.exists(promptsPath) || !Files.isDirectory(promptsPath)) {
            log.warn("提示词初始化-prompts文件夹不存在或不是目录: {}", promptsPath.toAbsolutePath());
            return;
        }

        try (var stream = Files.walk(promptsPath)) {
            stream.filter(Files::isRegularFile)
                  .forEach(file -> {
                      try {
                          // 读取文件内容
                          String content = Files.readString(file);
                          // 获取文件名（不含扩展名）作为key
                          String fileName = file.getFileName().toString();
                          String key = getFileNameWithoutExtension(fileName);

                          if (key != null && !key.isEmpty()) {
                              PROMPT_MAP.put(key, content);
                              log.debug("提示词初始化-加载提示词文件: {}, 路径: {}", key, file.toAbsolutePath());
                          }
                      } catch (IOException e) {
                          log.error("提示词初始化-读取文件失败: {}", file.getFileName(), e);
                      }
                  });
        } catch (IOException e) {
            log.error("提示词初始化-遍历prompts文件夹失败", e);
        }
    }

    /**
     * 获取不带扩展名的文件名
     *
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

    /**
     * 根据key获取提示词内容
     *
     * @param key 提示词key，应与constant/prompt/ 中定义的常量名一致
     * @return 提示词文本内容，如果不存在则返回空字符串
     */
    public static String getPrompt(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String prompt = PROMPT_MAP.get(key);
        if(prompt == null){
            log.error("提示词内容为空，请检查配置文件：{}", key);
            return "";
        }
        return prompt;
    }

    /**
     * 设置提示词内容
     *
     * @param key   提示词key
     * @param value 提示词文本内容
     */
    public static void setPrompt(String key, String value) {
        PROMPT_MAP.put(key, value);
    }

    /**
     * 移除指定key的提示词
     *
     * @param key 提示词key
     */
    public static void removePrompt(String key) {
        PROMPT_MAP.remove(key);
    }

    /**
     * 清空所有提示词
     */
    public static void clearPrompt() {
        PROMPT_MAP.clear();
    }

}
