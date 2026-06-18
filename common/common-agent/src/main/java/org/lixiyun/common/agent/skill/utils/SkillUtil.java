package org.lixiyun.common.agent.skill.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.constant.skill.SkillConstant;
import org.lixiyun.common.agent.skill.pojo.entity.Skill;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author lixiyun
 * @since 2026-04-15 19:41
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillUtil {

    // todo 存储所有的一级Skill列表
    // 内存中技能列表，线程安全，适合读多写少的场景
    private static final List<Skill> SKILLS = new CopyOnWriteArrayList<>();
    // 用于动态热加载，key:存储文件夹名称，value:技能对象
    private static final ConcurrentHashMap<String, Skill> MAP = new ConcurrentHashMap<>();
    // 缓存技能元数据的哈希值，key:存储文件夹名称，value:元数据内容的哈希值
    private static final ConcurrentHashMap<String, Integer> METADATA_HASH = new ConcurrentHashMap<>();

    private final FileUrlProperties fileUrlProperties;

    @PostConstruct
    public void init() {
        long start = System.currentTimeMillis();
        // 1. 首次初始化加载技能
        reloadSkills();

        log.info("skill初始化完成，共加载技能数量：{}，耗时：{}ms", SKILLS.size(), System.currentTimeMillis() - start);
    }

    /**
     * 重新加载所有技能（覆盖内存数据）
     */
    private void reloadSkills() {
        List<Skill> newSkills = getSkills();
        SKILLS.clear();
        SKILLS.addAll(newSkills);
        newSkills.forEach(item -> {
            String url = item.getUrl();
            String folderName = getSecondFolderName(url);
            if (folderName != null) {
                MAP.put(folderName, item);
            }
        });
    }

    /**
     * 提取 skills/文件夹名称/SKILL.md 中的 文件夹名称
     * @param filePath  文件路径：如 skills/demo/SKILL.md、skills\test\SKILL.md、classpath:skills/abc/SKILL.md
     * @return 中间文件夹名称，格式错误返回 null
     */
    public static String getSecondFolderName(String filePath) {
        // 1. 空值校验
        if (StrUtil.isBlank(filePath)) {
            log.warn("文件路径为空，无法提取文件夹名称");
            return null;
        }

        // 2. 统一路径分隔符为 /（兼容Windows \）
        String path = filePath.replace("\\", "/");

        // 3. 按 / 分割路径
        String[] parts = path.split("/");

        // 4. 遍历查找 skills 节点，取下一个节点（目标文件夹名）
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            // 匹配到 skills 文件夹
            if ("skills".equals(part)) {
                // 确保下一个节点存在（即目标文件夹）
                if (i + 1 < parts.length) {
                    String folderName = parts[i + 1].trim();
                    // 排除空名称、SKILL.md 文件名
                    if (StrUtil.isNotBlank(folderName) && !SkillConstant.SKILL_NAME.equals(folderName)) {
                        return folderName;
                    }
                }
                break;
            }
        }

        // 格式不匹配
        log.warn("路径格式不匹配，无法提取文件夹名称：{}", filePath);
        return null;
    }

    /**
     * 对外提供获取技能列表的方法
     */
    public static List<Skill> getSkillList() {
        return SKILLS;
    }

    /**
     * 对外提供获取技能映射的方法
     * @return 技能映射，key:文件夹名称，value:技能对象
     */
    public static Map<String, Skill> getSkillMap() {
        return MAP;
    }

    /**
     * 对外提供获取技能元数据哈希缓存的方法
     * @return 技能元数据哈希缓存，key:文件夹名称，value:元数据内容的哈希值
     */
    public static Map<String, Integer> getMetadataHash() {
        return METADATA_HASH;
    }

    /**
     * 热更新
     * <p>这里不做删除处理的，是直接将新的skill文件进行加载的，如果缓存中是有数据，要先调用热删除方法</p>
     * @param folderName 技能文件夹名称
     */
    public static void hotUpdate(String folderName) {
        try {
            // 获取SKILL.md头信息（元数据）
            Path skillMdPath = Path.of(folderName, SkillConstant.SKILL_NAME);
            String metadata = extractMetadata(skillMdPath);
            log.info("热更新-提取元数据成功，文件夹: {}", folderName);

            // 封装Skill类
            Skill skill = parseSkillStatic(metadata);
            if (skill == null) {
                log.error("热更新-解析Skill对象失败，文件夹: {}", folderName);
                return;
            }

            // 设置url
            FileUrlProperties fileUrlProperties = SpringUtil.getBean(FileUrlProperties.class);
            String skillsFolderUrl = fileUrlProperties.getUploadSkills();
            skill.setUrl(Path.of(skillsFolderUrl, folderName, SkillConstant.SKILL_NAME).toString());

            // 哈希计算
            int metadataHash = metadata.hashCode();

            // SKILL集合添加
            SKILLS.add(skill);
            log.debug("热更新-SKILLS集合更新成功，文件夹: {}", folderName);

            // MAP集合添加
            MAP.put(folderName, skill);
            log.debug("热更新-MAP集合更新成功，文件夹: {}", folderName);

            // METADATA_HASH集合添加
            METADATA_HASH.put(folderName, metadataHash);
            log.debug("热更新-METADATA_HASH集合更新成功，文件夹: {}, 哈希值: {}", folderName, metadataHash);

            log.info("热更新完成，文件夹: {}", folderName);
        } catch (Exception e) {
            log.error("热更新失败，文件夹: {}", folderName, e);
        }
    }

    /**
     * 热删除
     * <p>这里是将三个缓存中的数据进行删除，如果不存在数据则会直接取消执行</p>
     * @param folderName 技能文件夹名称
     */
    public static void hotDelete(String folderName) {
        if (!MAP.containsKey(folderName)) {
            return;
        }
        // 删除内存中的技能
        Skill skill = MAP.remove(folderName);
        // 删除内存中的技能列表
        SKILLS.remove(skill);
        // 删除缓存中的技能元数据
        METADATA_HASH.remove(folderName);
        log.info("热删除完成，文件夹: {}", folderName);
    }

    /**
     * 从项目的skills文件夹中获取所有技能信息。
     *
     * @return 技能列表
     */
    public List<Skill> getSkills() {
        String skillsFolderUrl = fileUrlProperties.getUploadSkills();
        Path skillsPath = Paths.get(skillsFolderUrl);
        
        if (!Files.exists(skillsPath) || !Files.isDirectory(skillsPath)) {
            log.warn("skill初始化-skills文件夹不存在或不是目录: {}", skillsPath.toAbsolutePath());
            return List.of();
        }

        try {
            return Files.list(skillsPath)
                    .filter(Files::isDirectory)
                    .map(folder -> {
                        try {
                            Path skillMdPath = folder.resolve(SkillConstant.SKILL_NAME);
                            if (!Files.exists(skillMdPath) || !Files.isRegularFile(skillMdPath)) {
                                log.warn("skill初始化-SKILL.md文件不存在: {}", skillMdPath);
                                return null;
                            }

                            // 提取元数据并缓存哈希
                            String metadata = extractMetadata(skillMdPath);
                            METADATA_HASH.put(folder.getFileName().toString(), metadata.hashCode());

                            // 解析内容为Skill对象
                            Skill skill = parseSkill(metadata);
                            if (skill != null) {
                                // 设置相对路径
                                skill.setUrl(Path.of(skillsFolderUrl, folder.getFileName().toString(), SkillConstant.SKILL_NAME).toString());
                            }
                            return skill;
                        } catch (Exception e) {
                            log.error("skill初始化-处理文件夹 {} 时出现异常", folder.getFileName(), e);
                            return null;
                        }
                    })
                    .filter(skill -> skill != null)
                    .toList();
        } catch (IOException e) {
            log.error("skill初始化-获取技能列表时出现异常", e);
            return List.of();
        }
    }

    /**
     * 从SKILL.md文件中提取元数据内容（位于前置标记 --- 之间的部分）。
     * @param skillMdPath SKILL.md文件路径
     * @return 提取的元数据内容
     */
    public static String extractMetadata(Path skillMdPath) {
        StringBuilder sb = new StringBuilder();
        boolean inFrontMatter = false;
        try (BufferedReader reader = Files.newBufferedReader(skillMdPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equals("---")) {
                    inFrontMatter = !inFrontMatter;
                    if (!inFrontMatter) break;
                } else if (inFrontMatter) {
                    sb.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            // 捕获并处理异常，例如记录日志并返回空字符串或 null
            log.error("提取元数据失败: {}", skillMdPath, e);
            return null;
        }
        return sb.toString().trim();
    }


    /**
     * 解析内容为Skill对象（静态版本，供热更新使用）。
     *
     * @param content 提取的内容
     * @return Skill对象
     */
    private static Skill parseSkillStatic(String content) {
        return parseSkillInstance(content);
    }

    /**
     * 解析内容为Skill对象（实例版本，供初始化使用）。
     *
     * @param content 提取的内容
     * @return Skill对象
     */
    private Skill parseSkill(String content) {
        return parseSkillInstance(content);
    }

    /**
     * 解析内容为Skill对象的通用实现。
     *
     * @param content 提取的内容
     * @return Skill对象
     */
    private static Skill parseSkillInstance(String content) {
        Skill.SkillBuilder builder = Skill.builder();
        // 兼容所有系统换行符
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if(parts.length != 2) {
                    log.warn("skill初始化-无法解析行: {}", line);
                    continue;
                }
                String key = parts[0].trim();
                String value = parts[1].trim().replace("\"", "");
                switch (key) {
                    case "name" -> builder.name(value);
                    case "description" -> builder.description(value);
                    case "version" -> builder.version(value);
                    case "author" -> builder.author(value);
                    case "tags" -> {
                        List<String> tagList = Arrays.stream(value.replace("[", "").replace("]", "").split(","))
                                .map(String::trim)
                                .filter(s -> !s.isBlank())
                                .toList();
                        builder.tags(tagList);
                    }
                    // url is set separately
                }
            }
        }
        return builder.build();
    }

}
