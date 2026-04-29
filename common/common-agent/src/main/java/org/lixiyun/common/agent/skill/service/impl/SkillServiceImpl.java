package org.lixiyun.common.agent.skill.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.constant.skill.SkillConstant;
import org.lixiyun.common.agent.skill.pojo.entity.Skill;
import org.lixiyun.common.agent.skill.service.SkillService;
import org.lixiyun.common.agent.skill.utils.SkillUtil;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author lixiyun
 * @since 2026-04-16 20:35
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final FileUrlProperties fileUrlProperties;

    @Override
    public List<String> getAllFolderName(Integer pageNum, Integer pageSize) {
        String basePath = fileUrlProperties.getUploadSkills();
        log.debug("获取技能文件夹列表，路径: {}, 页码: {}, 每页数量: {}", basePath, pageNum, pageSize);

        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.warn("技能文件夹路径不存在或不是目录: {}", basePath);
            return List.of();
        }

        File[] files = baseDir.listFiles(File::isDirectory);
        if (files == null || files.length == 0) {
            log.warn("技能文件夹下没有子文件夹: {}", basePath);
            return List.of();
        }

        // 获取所有文件夹名称
        List<String> allFolderNames = new ArrayList<>();
        Arrays.stream(files).map(File::getName).sorted(String::compareTo).forEach(allFolderNames::add);

        // 暂不处理分页操作
//        // 分页处理
//        int total = allFolderNames.size();
//        int fromIndex = (pageNum - 1) * pageSize;
//        int toIndex = Math.min(fromIndex + pageSize, total);
//
//        if (fromIndex >= total) {
//            log.info("分页超出范围，总数: {}, 起始索引: {}", total, fromIndex);
//            return List.of();
//        }
//
//        List<String> pagedFolders = allFolderNames.subList(fromIndex, toIndex);
//        log.info("获取技能文件夹列表成功，总数: {}, 当前页返回: {} 个", total, pagedFolders.size());
        return allFolderNames;
    }

    @Override
    public List<String> getAllFileByFolderName(String folderName) {
        String basePath = fileUrlProperties.getUploadSkills();
        String folderPath = basePath + File.separator + folderName;
        log.debug("获取文件夹下所有文件，文件夹: {}, 完整路径: {}", folderName, folderPath);

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            log.warn("文件夹不存在或不是目录: {}", folderPath);
            return List.of();
        }

        File[] files = folder.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            log.warn("文件夹下没有文件: {}", folderPath);
            return List.of();
        }

        List<String> fileNames = new ArrayList<>();
        Arrays.stream(files).map(File::getName).sorted(String::compareTo).forEach(fileNames::add);

        log.info("获取文件夹下所有文件成功，文件夹: {}, 文件数量: {}", folderName, fileNames.size());
        return fileNames;
    }

    @Override
    public String getFileContext(String folderName, String fileName) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path filePath = Paths.get(basePath, folderName, fileName);
        log.debug("查询指定文件内容，文件夹: {}, 文件名: {}, 完整路径: {}", folderName, fileName, filePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.warn("文件不存在或不是普通文件: {}", filePath);
            return "";
        }

        boolean textFile = isTextFile(fileName);
        if (!textFile) {
            log.warn("文件不是文本文件: {}", filePath);
            return "";
        }

        try {
            String content = Files.readString(filePath);
            log.info("获取文件内容成功，文件大小: {} 字符", content.length());
            return content;
        } catch (IOException e) {
            log.error("读取文件内容失败: {}", filePath, e);
            return "";
        }
    }

    @Override
    public void updateFolderName(String oldFolderName, String newFolderName) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path oldFolderPath = Paths.get(basePath, oldFolderName);
        Path newFolderPath = Paths.get(basePath, newFolderName);
        
        log.debug("修改文件夹名，旧名称: {}, 新名称: {}", oldFolderName, newFolderName);
        
        // 1. 判断旧文件夹是否存在
        if (!Files.exists(oldFolderPath) || !Files.isDirectory(oldFolderPath)) {
            log.error("旧文件夹不存在: {}", oldFolderPath);
            throw new RuntimeException("旧文件夹不存在: " + oldFolderName);
        }
        
        // 2. 判断是否重名（新文件夹名是否已存在）
        if (Files.exists(newFolderPath)) {
            log.error("新文件夹名称已存在: {}", newFolderPath);
            throw new RuntimeException("新文件夹名称已存在: " + newFolderName);
        }
        
        // 3. 本地文件夹重命名
        try {
            Files.move(oldFolderPath, newFolderPath);
            log.debug("本地文件夹重命名成功: {} -> {}", oldFolderName, newFolderName);
        } catch (IOException e) {
            log.error("文件夹重命名失败: {} -> {}", oldFolderName, newFolderName, e);
            throw new RuntimeException("文件夹重命名失败", e);
        }
        
        // 4. 判断MAP与METADATA_HASH中是否存在，如果存在则进行key替换
        Map<String, Skill> skillMap = SkillUtil.getSkillMap();
        Map<String, Integer> metadataHash = SkillUtil.getMetadataHash();
        
        if (skillMap.containsKey(oldFolderName)) {
            skillMap.put(newFolderName, skillMap.remove(oldFolderName));
            log.debug("更新SkillUtil.MAP缓存: {} -> {}", oldFolderName, newFolderName);
        }
        
        if (metadataHash.containsKey(oldFolderName)) {
            metadataHash.put(newFolderName, metadataHash.remove(oldFolderName));
            log.debug("更新SkillUtil.METADATA_HASH缓存: {} -> {}", oldFolderName, newFolderName);
        }
        
        log.info("文件夹重命名完成: {} -> {}", oldFolderName, newFolderName);
    }

    @Override
    public void updateFileName(String folderName, String oldFileName, String newFileName) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path folderPath = Paths.get(basePath, folderName);
        Path oldFilePath = folderPath.resolve(oldFileName);
        Path newFilePath = folderPath.resolve(newFileName);

        log.debug("修改文件名，文件夹: {}, 旧文件名: {}, 新文件名: {}", folderName, oldFileName, newFileName);

        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            log.error("文件夹路径不存在: {}", folderPath);
            throw new RuntimeException("文件夹路径不存在: " + folderName);
        }

        if (oldFileName.equals(newFileName)) {
            log.error("新旧文件名相同: {}", oldFileName);
            throw new RuntimeException("新旧文件名不能相同");
        }

        try {
            Files.move(oldFilePath, newFilePath);
            log.debug("本地文件重命名成功: {} -> {}", oldFileName, newFileName);
        } catch (IOException e) {
            log.error("文件重命名失败: {} -> {}", oldFileName, newFileName, e);
            throw new RuntimeException("文件重命名失败", e);
        }

        if (SkillConstant.SKILL_NAME.equals(oldFileName)) {
            SkillUtil.hotDelete(folderName);
            log.debug("旧文件为SKILL.md，执行热删除，文件夹: {}", folderName);
        }

        if (SkillConstant.SKILL_NAME.equals(newFileName)) {
            SkillUtil.hotUpdate(folderName);
            log.debug("新文件为SKILL.md，执行热更新，文件夹: {}", folderName);
        }

        log.info("文件重命名完成，文件夹: {}, {} -> {}", folderName, oldFileName, newFileName);
    }

    @Override
    public void updateFileContext(String folderName, String fileName, String fileContext) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path filePath = Paths.get(basePath, folderName, fileName);

        log.debug("修改文件内容，文件夹: {}, 文件名: {}, 内容长度: {}", folderName, fileName, fileContext.length());

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.error("文件不存在: {}", filePath);
            throw new RuntimeException("文件不存在: " + fileName);
        }

        // 检查文件类型，只允许修改文本文件
        boolean isTextFile = isTextFile(fileName);

        if (!isTextFile) {
            log.error("不支持修改非文本文件: {}", fileName);
            throw new RuntimeException("不支持修改非文本文件，仅支持修改 .md、.txt、.html、.json、.xml、.yaml、.js、.ts、.css、.py、.java 等文本文件");
        }

        try {
            // 文件不存在则创建；文件存在则清空内容覆写
            Files.writeString(filePath, fileContext);
            log.debug("文件内容覆盖成功: {}", filePath);
        } catch (IOException e) {
            log.error("写入文件内容失败: {}", filePath, e);
            throw new RuntimeException("写入文件内容失败", e);
        }

        if (SkillConstant.SKILL_NAME.equals(fileName)) {
            Path skillMdPath = Path.of(basePath, folderName, SkillConstant.SKILL_NAME);
            String metadata = SkillUtil.extractMetadata(skillMdPath);
            if(metadata == null){
                log.error("SKILL.md元数据为空，请检查文件内容");
                throw new RuntimeException("SKILL.md元数据为空，请检查文件内容");
            }
            int newHash = metadata.hashCode();
            Map<String, Integer> metadataHash = SkillUtil.getMetadataHash();
            Integer oldHash = metadataHash.get(folderName);

            if (oldHash != null && oldHash.equals(newHash)) {
                log.debug("SKILL.md元数据哈希未变化，跳过热更新，文件夹: {}", folderName);
                return;
            }

            SkillUtil.hotDelete(folderName);
            log.debug("SKILL.md内容变更，执行热删除，文件夹: {}", folderName);

            SkillUtil.hotUpdate(folderName);
            log.debug("SKILL.md内容变更，执行热更新，文件夹: {}", folderName);
        }

        log.info("文件内容修改完成，文件夹: {}, 文件名: {}", folderName, fileName);
    }

    /**
     * 判断是否是文本文件
     * @param fileName 文件名
     * @return true: 是文本文件，false: 非文本文件
     */
    private static boolean isTextFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".md")
                || lowerFileName.endsWith(".markdown")
                || lowerFileName.endsWith(".txt")
                || lowerFileName.endsWith(".html")
                || lowerFileName.endsWith(".htm")
                || lowerFileName.endsWith(".json")
                || lowerFileName.endsWith(".xml")
                || lowerFileName.endsWith(".yaml")
                || lowerFileName.endsWith(".yml")
                || lowerFileName.endsWith(".js")
                || lowerFileName.endsWith(".ts")
                || lowerFileName.endsWith(".css")
                || lowerFileName.endsWith(".py")
                || lowerFileName.endsWith(".java")
                || lowerFileName.endsWith(".properties");
    }

    @Override
    public void deleteFolder(String folderName) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path folderPath = Paths.get(basePath, folderName);

        log.debug("删除文件夹，文件夹: {}, 完整路径: {}", folderName, folderPath);

        try (Stream<Path> paths = Files.walk(folderPath)) {
            paths.sorted(Comparator.reverseOrder())
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                         log.debug("删除文件/文件夹: {}", path);
                     } catch (IOException e) {
                         log.error("删除文件/文件夹失败: {}", path, e);
                     }
                 });
            log.debug("本地文件夹删除成功: {}", folderPath);
        } catch (IOException e) {
            log.error("删除文件夹失败: {}", folderPath, e);
            throw new RuntimeException("删除文件夹失败", e);
        }

        log.debug("执行热删除，文件夹: {}", folderName);
        SkillUtil.hotDelete(folderName);

        log.info("文件夹删除完成，文件夹: {}", folderName);
    }

    @Override
    public void deleteFile(String folderName, String fileName) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path filePath = Paths.get(basePath, folderName, fileName);
        log.debug("删除文件，文件夹: {}, 文件名: {}, 完整路径: {}", folderName, fileName, filePath);

        try {
            Files.delete(filePath);
            log.debug("本地文件删除成功: {}", filePath);
        } catch (IOException e) {
            log.error("删除文件失败: {}", filePath, e);
            throw new RuntimeException("删除文件失败: " + fileName, e);
        }

        if (SkillConstant.SKILL_NAME.equals(fileName)) {
            SkillUtil.hotDelete(folderName);
            log.debug("删除的是SKILL.md文件，执行热删除，文件夹: {}", folderName);
        }

        log.info("文件删除完成，文件夹: {}, 文件名: {}", folderName, fileName);
    }

    @Override
    public void addFolder(String folderName) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path folderPath = Paths.get(basePath, folderName);

        log.debug("尝试新增文件夹，名称: {}, 完整路径: {}", folderName, folderPath);

        if (Files.exists(folderPath)) {
            log.error("文件夹名称已存在: {}", folderPath);
            throw new RuntimeException("文件夹名称已存在: " + folderName);
        }

        try {
            Files.createDirectory(folderPath);
            log.info("文件夹新增成功: {}", folderName);
        } catch (IOException e) {
            log.error("创建文件夹失败: {}", folderPath, e);
            throw new RuntimeException("创建文件夹失败", e);
        }
    }

    @Override
    public void addFile(String folderName, String fileName, String fileContext) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path filePath = Paths.get(basePath, folderName, fileName);
        log.debug("尝试新增文件，文件夹: {}, 文件名: {}, 完整路径: {}", folderName, fileName, filePath);

        if (Files.exists(filePath)) {
            log.error("文件名称已存在: {}", filePath);
            throw new RuntimeException("文件名称已存在: " + fileName);
        }

        try {
            // 文件不存在则创建；文件存在则清空内容覆写
            Files.writeString(filePath, fileContext);
            log.debug("本地文件新增成功: {}", filePath);
        } catch (IOException e) {
            log.error("创建文件失败: {}", filePath, e);
            throw new RuntimeException("创建文件失败", e);
        }

        if (SkillConstant.SKILL_NAME.equals(fileName)) {
            SkillUtil.hotUpdate(folderName);
            log.debug("新增的是SKILL.md文件，执行热更新，文件夹: {}", folderName);
        }

        log.info("文件新增完成，文件夹: {}, 文件名: {}", folderName, fileName);
    }

    @Override
    public void uploadFile(String folderName, MultipartFile file) {
        String basePath = fileUrlProperties.getUploadSkills();
        Path folderPath = Paths.get(basePath, folderName);
        log.debug("尝试上传文件，文件夹: {}, 完整路径: {}", folderName, folderPath);

        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            log.error("文件夹不存在: {}", folderPath);
            throw new RuntimeException("文件夹不存在: " + folderName);
        }

        String fileName = file.getOriginalFilename();
        if(fileName == null || fileName.isBlank()){
            log.error("文件名不能为空");
            throw new RuntimeException("文件名不能为空");
        } else if(fileName.endsWith(".zip")){
            log.error("不能上传文件夹");
            throw new RuntimeException("不能上传文件夹");
        }
        Path targetPath = folderPath.resolve(fileName);

        if (Files.exists(targetPath)) {
            log.error("文件已存在: {}", targetPath);
            throw new RuntimeException("文件已存在: " + fileName);
        }

        try {
            Files.copy(file.getInputStream(), targetPath);
            log.debug("本地文件存储成功: {}", targetPath);
        } catch (IOException e) {
            log.error("文件上传失败: {}", targetPath, e);
            throw new RuntimeException("文件上传失败", e);
        }

        if (SkillConstant.SKILL_NAME.equals(fileName)) {
            SkillUtil.hotUpdate(folderName);
            log.debug("上传的是SKILL.md文件，执行热更新，文件夹: {}", folderName);
        }

        log.info("文件上传完成，文件夹: {}, 文件名: {}", folderName, fileName);
    }

    @Override
    public void uploadFolder(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".zip")) {
            log.error("仅支持 zip 格式的压缩文件，当前文件: {}", originalFilename);
            throw new RuntimeException("仅支持 zip 格式的压缩文件");
        }

        String folderName = originalFilename.substring(0, originalFilename.lastIndexOf("."));
        String basePath = fileUrlProperties.getUploadSkills();
        Path targetDir = Paths.get(basePath, folderName);

        log.debug("尝试上传文件夹，名称: {}, 完整路径: {}", folderName, targetDir);

        if (Files.exists(targetDir)) {
            log.error("文件夹已存在: {}", targetDir);
            throw new RuntimeException("文件夹已存在: " + folderName);
        }

        try {
            Files.createDirectories(targetDir);
            //  创建解压流：自动关闭资源
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                // 循环遍历：读取 ZIP 压缩包里的每一个文件/文件夹
                // 这里的代码会将 ZIP 压缩包里的所有文件全部解压到指定目录下，是所有文件与文件夹，多层的
                while ((entry = zis.getNextEntry()) != null) {
                    // 终存储路径 + 规范化路径
                    Path entryPath = targetDir.resolve(entry.getName()).normalize();
                    // 安全校验：防止【路径穿越攻击】
                    // 在文件名里加 ../（跳转到上级目录），骗过解压程序，把恶意文件写到任意位置
                    if (!entryPath.startsWith(targetDir)) {
                        throw new RuntimeException("非法的文件路径: " + entry.getName());
                    }
                    // 判断当前条目是【文件夹】还是【文件】
                    if (entry.isDirectory()) {
                        // 如果是文件夹：直接创建对应的目录（递归创建）
                        Files.createDirectories(entryPath);
                    } else {
                        // 如果是文件：先创建文件的父级文件夹
                        Files.createDirectories(entryPath.getParent());
                        // 将 ZIP 内的文件流，复制写入到服务器本地文件
                        Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    // 关闭当前条目，释放资源，读取下一个
                    zis.closeEntry();
                }
            }
            log.debug("文件夹解压并存储成功: {}", targetDir);
        } catch (IOException e) {
            log.error("文件夹上传解压失败: {}", folderName, e);
            throw new RuntimeException("文件夹上传失败", e);
        }

        Path skillMdPath = targetDir.resolve(SkillConstant.SKILL_NAME);
        if (Files.exists(skillMdPath)) {
            SkillUtil.hotUpdate(folderName);
            log.debug("包含SKILL.md文件，执行热更新，文件夹: {}", folderName);
        }

        log.info("文件夹上传完成，文件夹: {}", folderName);
    }
}
