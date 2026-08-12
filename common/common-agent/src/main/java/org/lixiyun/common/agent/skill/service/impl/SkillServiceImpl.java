package org.lixiyun.common.agent.skill.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.skill.constant.SkillConstant;
import org.lixiyun.common.agent.skill.pojo.entity.Skill;
import org.lixiyun.common.agent.skill.pojo.vo.SkillContentItemVO;
import org.lixiyun.common.agent.skill.service.SkillService;
import org.lixiyun.common.agent.skill.utils.SkillUtil;
import org.lixiyun.common.core.error.enums.SkillExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.core.utils.StringUtils;
import org.lixiyun.common.file.constant.FileConstant;
import org.lixiyun.common.file.utils.FileContentReaderUtil;
import org.lixiyun.common.file.utils.FileUtils;
import org.lixiyun.common.file.utils.FileValidatorUtil;
import org.lixiyun.common.file.utils.ZipUtils;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill服务实现类
 * <p>提供Skill文件夹和文件的管理功能实现</p>
 *
 * @author lixiyun
 * @since 2026-04-16 20:35
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final FileUrlProperties fileUrlProperties;

    private final String skillLockKey = "skill:terminal:lock:";
    private final int waitTimeSec = 0;
    private final int lockTimeout = 300;

    /**
     * 获取Skill基础目录路径
     *
     * @return Skill文件存储的基础目录
     */
    private String getSkillsBasePath() {
        return fileUrlProperties.getUploadSkills();
    }

    /**
     * 路径检查辅助方法
     * <p>
     * 流程：
     * 1. 判断补充文件夹路径是否存在，存在则拼接两个路径信息
     * 2. 进行路径安全检查（防止目录穿越攻击）
     * 3. 判断最终路径是否真实存在
     * </p>
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @return 最终验证通过的绝对路径 {@link Path}
     */
    protected Path checkAndGetFolderPath(String skillFolderUrl, String supplementaryFolderUrl) {
        log.debug("开始路径检查, skillFolderUrl: {}, supplementaryFolderUrl: {}", skillFolderUrl, supplementaryFolderUrl);

        String skillsBasePath = getSkillsBasePath();

        // 拼接路径
        String targetRelativePath = buildTargetPath(skillFolderUrl, supplementaryFolderUrl);

        // 路径安全检查
        validatePathSecurity(targetRelativePath, skillsBasePath);

        // 解析为绝对路径并规范化
        Path resolvedPath = FileUtils.resolveSecurePath(skillsBasePath, targetRelativePath);
        if (resolvedPath == null) {
            log.error("安全路径解析失败: baseDir={}, childPath={}", skillsBasePath, targetRelativePath);
            throw new BusinessException(SkillExceptionEnum.SKILL_PATH_UNSAFE);
        }

        log.debug("路径安全检查通过，规范化后的路径: {}", resolvedPath);

        // 判断路径是否存在
        if (!Files.exists(resolvedPath)) {
            log.warn("目标路径不存在: {}", resolvedPath);
            throw new BusinessException(SkillExceptionEnum.SKILL_FOLDER_NOT_FOUND);
        }

        log.info("路径检查完成，路径有效: {}", resolvedPath);
        return resolvedPath;
    }

    /**
     * 路径检查辅助方法（带文件名版本）
     * <p>在checkAndGetFolderPath基础上追加文件名，用于文件操作场景</p>
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @param fileName               文件名
     * @return 最终验证通过的文件绝对路径 {@link Path}
     */
    protected Path checkAndGetFilePath(String skillFolderUrl, String supplementaryFolderUrl, String fileName) {
        log.debug("开始文件路径检查, skillFolderUrl: {}, supplementaryFolderUrl: {}, fileName: {}",
                skillFolderUrl, supplementaryFolderUrl, fileName);

        // 使用FileValidator验证文件名合法性
        FileValidatorUtil.validateFileName(fileName);

        Path folderPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);

        Path filePath = folderPath.resolve(fileName).normalize();

        log.info("文件路径构建完成: {}", filePath);
        return filePath;
    }

    /**
     * 构建目标相对路径
     * <p>根据是否提供补充文件夹路径进行智能拼接</p>
     *
     * @param skillFolderUrl              主文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @return 拼接后的相对路径字符串
     */
    private String buildTargetPath(String skillFolderUrl, String supplementaryFolderUrl) {
        if (StringUtils.isNotBlank(supplementaryFolderUrl)) {
            String combinedPath = Paths.get(skillFolderUrl).resolve(supplementaryFolderUrl).toString();
            log.info("检测到补充文件夹路径，进行路径拼接: {} + {} -> {}", skillFolderUrl, supplementaryFolderUrl, combinedPath);
            return combinedPath;
        }

        String targetPath = StringUtils.isNotBlank(skillFolderUrl) ? skillFolderUrl : "";
        log.info("未提供补充文件夹路径，使用原始路径: {}", targetPath);
        return targetPath;
    }

    /**
     * 验证路径安全性
     * <p>防止目录穿越攻击和非法访问</p>
     *
     * @param targetPath    目标相对路径
     * @param baseDirectory 基础安全目录
     */
    private void validatePathSecurity(String targetPath, String baseDirectory) {
        if (!FileUtils.isSecurePath(targetPath, baseDirectory)) {
            log.error("路径安全校验失败，可能存在路径穿透攻击: 目标路径={}, 基础目录={}", targetPath, baseDirectory);
            throw new BusinessException(SkillExceptionEnum.SKILL_PATH_UNSAFE);
        }
    }

    /**
     * 将Path对象转换为VO对象
     *
     * @param path     文件或文件夹的Path对象
     * @param basePath 基础目录Path
     * @return {@link SkillContentItemVO} 视图对象
     */
    private SkillContentItemVO convertToVO(Path path, Path basePath) {
        boolean isDirectory = Files.isDirectory(path);
        return SkillContentItemVO.builder()
                .name(path.getFileName().toString())
                .type(isDirectory ? FileConstant.FOLDER_TYPE : FileConstant.FILE_TYPE)
                .relativePath(basePath.relativize(path).toString().replace("\\", "/"))
                .build();
    }

    /**
     * 列出文件夹内容的核心方法
     * <p>使用Stream API高效遍历目录</p>
     *
     * @param folderPath 文件夹路径
     * @return 内容项列表
     */
    private List<SkillContentItemVO> listFolderContents(Path folderPath) {
        Path basePath = Paths.get(getSkillsBasePath()).toAbsolutePath().normalize();

        try (Stream<Path> pathStream = Files.list(folderPath)) {
            return pathStream
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(path -> convertToVO(path, basePath))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("读取文件夹内容失败: {}", folderPath, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_READ_ERROR);
        }
    }

    /**
     * 构建文件流式响应
     * <p>用于非文本文件的流式返回，设置Content-Disposition等响应头</p>
     *
     * @param filePath 文件路径
     * @param fileName 文件名
     * @param mimeType MIME类型
     * @return ResponseEntity包含Resource资源
     */
    private ResponseEntity<Resource> buildFileStreamResponse(Path filePath, String fileName, String mimeType) {
        try {
            Resource resource = new UrlResource(filePath.toUri());
            long fileSize = Files.size(filePath);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.parseMediaType(mimeType))
                    .contentLength(fileSize)
                    .body(resource);
        } catch (IOException e) {
            log.error("构建文件流响应失败: {}", filePath, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_READ_ERROR);
        }
    }

    /**
     * 检查目标文件夹不存在（统一方法）
     * <p>用于创建文件夹、重命名文件夹、上传文件夹等场景</p>
     *
     * @param parentPath  父目录路径 {@link Path}
     * @param folderName  文件夹名称
     * @throws BusinessException 如果同名文件夹已存在则抛出异常
     */
    private void checkTargetFolderNotExists(Path parentPath, String folderName) {
        if (FileValidatorUtil.isFolderExists(parentPath, folderName)) {
            log.warn("目标目录下已存在同名文件夹: {}", folderName);
            throw new BusinessException(SkillExceptionEnum.SKILL_FOLDER_ALREADY_EXISTS);
        }
        log.debug("新文件夹名称可用: {}", folderName);
    }

    /**
     * 检查目标文件不存在（统一方法）
     * <p>用于创建文件、重命名文件、上传文件等场景</p>
     *
     * @param parentPath 父目录路径 {@link Path}
     * @param fileName   文件名称
     * @throws BusinessException 如果同名文件已存在则抛出异常
     */
    private void checkTargetFileNotExists(Path parentPath, String fileName) {
        if (FileValidatorUtil.isFileExists(parentPath, fileName)) {
            log.warn("目标目录下已存在同名文件: {}", fileName);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_ALREADY_EXISTS);
        }
        log.debug("新文件名可用: {}", fileName);
    }

    @Override
    public List<SkillContentItemVO> queryFolderContents(String skillFolderUrl, String supplementaryFolderUrl) {
        log.info("开始查询Skill文件夹内容, skillFolderUrl: {}, supplementaryFolderUrl: {}", skillFolderUrl, supplementaryFolderUrl);

        if (StringUtils.isBlank(skillFolderUrl)) {
            log.info("未指定文件夹路径，查询Skill根目录内容");
            Path rootPath = Paths.get(getSkillsBasePath()).toAbsolutePath().normalize();
            return listFolderContents(rootPath);
        }

        Path folderPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);
        return listFolderContents(folderPath);
    }

    @Override
    public ResponseEntity<?> queryFileContent(String skillFolderUrl, String supplementaryFolderUrl, String fileName) {
        log.info("开始查询Skill文件内容, skillFolderUrl: {}, supplementaryFolderUrl: {}, fileName: {}",
                skillFolderUrl, supplementaryFolderUrl, fileName);

        Path filePath = checkAndGetFilePath(skillFolderUrl, supplementaryFolderUrl, fileName);

        if (!Files.exists(filePath)) {
            log.warn("文件不存在: {}", filePath);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_NOT_FOUND);
        }

        if (Files.isDirectory(filePath)) {
            log.error("指定路径是文件夹而非文件: {}", filePath);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_NOT_FOUND);
        }

        String fileType = FileContentReaderUtil.getFileExtension(fileName.toLowerCase());
        String mimeType = FileContentReaderUtil.determineMimeType(fileType);

        if (FileContentReaderUtil.isTextFile(fileType)) {
            log.info("检测到文本文件，直接返回文本内容, 类型: {}", fileType);
            String content = FileContentReaderUtil.readTextFile(filePath);
            return ResponseEntity.ok(Result.success(content));
        }

        log.info("检测到非文本文件，返回文件数据流, 类型: {}, MIME: {}", fileType, mimeType);
        return buildFileStreamResponse(filePath, fileName, mimeType);
    }

    @Override
    public void createFolder(String skillFolderUrl, String supplementaryFolderUrl, String folderName) {
        log.info("开始新建Skill文件夹, skillFolderUrl: {}, supplementaryFolderUrl: {}, folderName: {}",
                skillFolderUrl, supplementaryFolderUrl, folderName);

        // 步骤1：路径检查
        Path parentPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);

        // 步骤2：判断文件夹名称是否合法（调用FileValidator通用方法）
        FileValidatorUtil.validateFolderName(folderName);

        // 步骤3：判断该文件夹下是否存在同名文件夹
        checkTargetFolderNotExists(parentPath, folderName);

        // 步骤4：在该文件夹路径下创建新文件夹（调用FileValidator通用方法）
        FileValidatorUtil.createNewFolder(parentPath, folderName);
    }

    @Override
    public void createFile(String skillFolderUrl, String supplementaryFolderUrl, String fileName) {
        log.info("开始新建Skill文件, skillFolderUrl: {}, supplementaryFolderUrl: {}, fileName: {}",
                skillFolderUrl, supplementaryFolderUrl, fileName);

        // 步骤1：路径检查
        Path parentPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);

        // 步骤2：判断文件名是否合法（调用FileValidator通用方法）
        FileValidatorUtil.validateFileName(fileName);

        // 步骤3：判断该文件夹下是否存在同名文件
        checkTargetFileNotExists(parentPath, fileName);

        // 步骤4：在该文件夹路径下创建新文件（调用FileValidator通用方法）
        FileValidatorUtil.createNewFile(parentPath, fileName);
    }

    @Override
    public void deleteFolder(String skillFolderUrl, String supplementaryFolderUrl) {
        log.info("开始删除Skill文件夹, skillFolderUrl: {}, supplementaryFolderUrl: {}", skillFolderUrl, supplementaryFolderUrl);

        Path folderPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);

        checkNotSkillRootDirectory(folderPath);

        String shadowKey = Path.of(skillFolderUrl).toString();
        String lockKey = buildDistributedLockKey(shadowKey);

        try {
            // 尝试获取分布式锁
            acquireDistributedLock(lockKey);

            // 如果 supplementaryFolderUrl 不为空，则递归删除目录并返回
            if(!StringUtils.isBlank(supplementaryFolderUrl)){
                recursivelyDeleteDirectory(folderPath);
                return;
            }

            // 从影子表中删除该文件夹及其所有子孙节点
            deleteSkillNodesFromShadowTable(shadowKey);

            // 递归删除目录
            recursivelyDeleteDirectory(folderPath);

            log.info("Skill文件夹删除成功: {}", folderPath);
        } finally {
            // 释放分布式锁
            releaseDistributedLock(lockKey);
        }
    }

    @Override
    public void deleteFile(String skillFolderUrl, String supplementaryFolderUrl, String fileName) {
        log.info("开始删除Skill文件, skillFolderUrl: {}, supplementaryFolderUrl: {}, fileName: {}",
                skillFolderUrl, supplementaryFolderUrl, fileName);

        Path filePath = checkAndGetFilePath(skillFolderUrl, supplementaryFolderUrl, fileName);

        String shadowKey = Path.of(skillFolderUrl).toString();
        String lockKey = buildDistributedLockKey(shadowKey);

        try {
            // 尝试获取分布式锁
            acquireDistributedLock(lockKey);

            // 判断是否为SKILL.md文件
            boolean isSkillMdFile = fileName.equals(SkillConstant.SKILL_NAME);

            Files.delete(filePath);

            if (isSkillMdFile) {
                // 从影子表中删除该文件节点
                deleteSkillNodesFromShadowTable(shadowKey);
                log.info("SKILL.md文件已删除并清除影子表数据: {}", filePath);
            } else {
                log.info("普通文件删除成功: {}", filePath);
            }
        } catch (IOException e) {
            log.error("文件删除失败: {}", filePath, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_DELETE_ERROR);
        } finally {
            releaseDistributedLock(lockKey);
        }
    }

    @Override
    public void uploadFile(MultipartFile file, String skillFolderUrl, String supplementaryFolderUrl) {
        log.info("开始上传文件, 文件名: {}, 路径: {}, 补充路径: {}", file.getOriginalFilename(), skillFolderUrl, supplementaryFolderUrl);

        Path targetPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);

        validateUploadFile(file, targetPath);

        boolean isSkillMdFile = SkillConstant.SKILL_NAME.equals(file.getOriginalFilename());

        saveUploadedFile(file, targetPath);

        if (isSkillMdFile) {
            SkillUtil.addSkillFolderToShadowTable(targetPath);
            log.info("SKILL.md文件上传完成并已更新影子表: {}", file.getOriginalFilename());
        } else {
            log.info("普通文件上传完成: {}", file.getOriginalFilename());
        }
    }

    /**
     * 验证上传文件的合法性
     * <p>
     * 包括文件大小检查和同名文件存在性检查
     * </p>
     *
     * @param file       上传的文件 {@link MultipartFile}
     * @param targetPath 目标文件夹路径 {@link Path}
     */
    private void validateUploadFile(MultipartFile file, Path targetPath) {
        if (file.getSize() > SkillConstant.MAX_UPLOAD_FILE_SIZE_BYTES) {
            log.warn("上传文件大小超出限制: {} bytes，最大允许: {} MB", file.getSize(), SkillConstant.MAX_UPLOAD_FILE_SIZE_BYTES / (1024 * 1024));
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_UPLOAD_SIZE_EXCEEDED);
        }

        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            checkTargetFileNotExists(targetPath, fileName);
        }
    }

    /**
     * 保存上传的文件到目标路径
     *
     * @param file       上传的文件 {@link MultipartFile}
     * @param targetPath 目标文件夹路径 {@link Path}
     */
    private void saveUploadedFile(MultipartFile file, Path targetPath) {
        try {
            Path filePath = targetPath.resolve(Objects.requireNonNull(file.getOriginalFilename()));
            file.transferTo(filePath.toFile());
            log.debug("文件已保存到: {}", filePath);
        } catch (IOException e) {
            log.error("文件上传保存失败: {}", e.getMessage(), e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_READ_ERROR);
        }
    }

    @Override
    public void uploadFolder(MultipartFile zipFile, String skillFolderUrl, String supplementaryFolderUrl) {
        log.info("开始上传压缩包文件夹, 压缩包名: {}, 路径: {}, 补充路径: {}", zipFile.getOriginalFilename(), skillFolderUrl, supplementaryFolderUrl);

        Path targetPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);

        validateZipUpload(zipFile, targetPath);

        ZipUtils.extractZipToDirectory(zipFile, targetPath);

        String zipFileName = zipFile.getOriginalFilename()
                .substring(0, zipFile.getOriginalFilename().length() - FileConstant.ZIP_EXTENSION.length());

        if (!StringUtils.isBlank(supplementaryFolderUrl)) {
            return;
        }

        if(targetPath.resolve(zipFileName).resolve(SkillConstant.SKILL_NAME).toFile().exists()){
            log.info("压缩包解压后已存在SKILL.md文件，跳过影子表更新: {}", zipFileName);
            return;
        }

        SkillUtil.addSkillFolderToShadowTable(Path.of(skillFolderUrl).toAbsolutePath().normalize());

        log.info("压缩包上传并解压完成: {}", zipFile.getOriginalFilename());
    }

    /**
     * 验证压缩包上传的合法性
     * <p>
     * 包括文件大小检查和同名文件夹存在性检查。
     * 使用ZIP文件名（去掉.zip扩展名）作为预期的根目录名称进行冲突检测。
     * </p>
     *
     * @param zipFile    上传的压缩包 {@link MultipartFile}
     * @param targetPath 目标文件夹路径 {@link Path}
     */
    private void validateZipUpload(MultipartFile zipFile, Path targetPath) {
        boolean isSizeValid = ZipUtils.validateZipFileSize(zipFile, SkillConstant.MAX_UPLOAD_FILE_SIZE_BYTES);
        if (!isSizeValid) {
            throw new BusinessException(SkillExceptionEnum.SKILL_FOLDER_UPLOAD_SIZE_EXCEEDED);
        }

        String originalFileName = zipFile.getOriginalFilename();
        if (originalFileName != null && originalFileName.toLowerCase().endsWith(".zip")) {
            String expectedFolderName = originalFileName.substring(0, originalFileName.length() - 4);
            checkTargetFolderNotExists(targetPath, expectedFolderName);
        }
    }

    @Override
    public void updateFileContent(String newContent, String fileName, String skillFolderUrl, String supplementaryFolderUrl) {
        log.info("开始修改文件内容, 文件名: {}, 路径: {}, 补充路径: {}", fileName, skillFolderUrl, supplementaryFolderUrl);

        Path filePath = checkAndGetFilePath(skillFolderUrl, supplementaryFolderUrl, fileName);

        validateFileExists(filePath);

        validateTextFile(fileName);

        String shadowKey = Path.of(skillFolderUrl).toString();
        String lockKey = buildDistributedLockKey(shadowKey);

        try {
            acquireDistributedLock(lockKey);

            if (SkillConstant.SKILL_NAME.equals(fileName)) {
                handleSkillMdFileUpdate(newContent, filePath, shadowKey);
            } else {
                writeToFile(filePath, newContent);
                log.info("普通文本文件内容修改成功: {}", filePath);
            }
        } finally {
            releaseDistributedLock(lockKey);
        }
    }

    @Override
    public void renameFolder(String newFolderName, String skillFolderUrl, String supplementaryFolderUrl) {
        log.info("开始修改文件夹名称, 新名称: {}, 原路径: {}, 补充路径: {}", newFolderName, skillFolderUrl, supplementaryFolderUrl);

        Path folderPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);

        validateFolderName(newFolderName);

        Path parentPath = folderPath.getParent();
        checkTargetFolderNotExists(parentPath, newFolderName);

        String shadowKey = Path.of(skillFolderUrl).toString();
        String lockKey = buildDistributedLockKey(shadowKey);

        try {
            acquireDistributedLock(lockKey);

            if(!StringUtils.isBlank(supplementaryFolderUrl)){
                Path newPath = parentPath.resolve(newFolderName);
                Files.move(folderPath, newPath);
                log.info("补充文件夹重命名成功, 原路径: {}, 新路径: {}", folderPath, newPath);
                return;
            }

            Map<String, SkillUtil.ShadowNode> globalShadowTable = SkillUtil.getGlobalShadowTable();

            Set<String> nodesToDelete = SkillUtil.collectAllDescendantNodes(globalShadowTable, shadowKey);

            nodesToDelete.stream()
                    .filter(globalShadowTable::containsKey)
                    .forEach(globalShadowTable::remove);

            log.debug("已从影子表中删除旧路径的所有节点, 删除数量: {}", nodesToDelete.size());

            Path newPath = parentPath.resolve(newFolderName);
            Files.move(folderPath, newPath);

            SkillUtil.addSkillFolderToShadowTable(newPath);

            log.info("文件夹重命名成功, 原路径: {}, 新路径: {}", folderPath, newPath);
        } catch (IOException e) {
            log.error("文件夹重命名失败, 原路径: {}, 新名称: {}", folderPath, newFolderName, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FOLDER_NAME_INVALID);
        } finally {
            releaseDistributedLock(lockKey);
        }
    }

    /**
     * 验证文件夹名称合法性
     * <p>调用FileValidator通用方法验证文件夹名称格式</p>
     *
     * @param folderName 文件夹名称
     * @throws BusinessException 如果文件夹名称不合法则抛出异常
     */
    private void validateFolderName(String folderName) {
        FileValidatorUtil.validateFolderName(folderName);
        log.debug("文件夹名称验证通过: {}", folderName);
    }

    /**
     * 验证文件是否存在
     * <p>检查目标文件路径是否真实存在</p>
     *
     * @param filePath 文件路径 {@link Path}
     * @throws BusinessException 如果文件不存在则抛出异常
     */
    private void validateFileExists(Path filePath) {
        if (!Files.exists(filePath)) {
            log.warn("文件不存在: {}", filePath);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_NOT_FOUND);
        }
    }

    /**
     * 验证是否为纯文本文件
     * <p>只允许修改纯文本文件，非文本文件不允许通过此接口修改内容</p>
     *
     * @param fileName 文件名
     * @throws BusinessException 如果不是文本文件则抛出异常
     */
    private void validateTextFile(String fileName) {
        String fileType = FileContentReaderUtil.getFileExtension(fileName.toLowerCase());
        if (!FileContentReaderUtil.isTextFile(fileType)) {
            log.error("不允许修改非文本文件: {}, 文件类型: {}", fileName, fileType);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_NOT_TEXT_FILE);
        }
        log.debug("文件类型验证通过，是文本文件: {}, 类型: {}", fileName, fileType);
    }

    /**
     * 将内容写入文件
     * <p>使用Files工具类将字符串内容写入指定文件路径</p>
     *
     * @param filePath 目标文件路径 {@link Path}
     * @param content  要写入的文件内容
     * @throws BusinessException 如果写入失败则抛出异常
     */
    private void writeToFile(Path filePath, String content) {
        try {
            Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
            log.debug("文件内容写入成功: {}", filePath);
        } catch (IOException e) {
            log.error("文件内容写入失败: {}", filePath, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_CONTENT_UPDATE_ERROR);
        }
    }

    /**
     * 处理SKILL.md文件的更新操作
     * <p>
     * 实现流程：
     * 1. 解析新内容的元数据格式并验证正确性
     * 2. 从影子表获取原先的元数据
     * 3. 比较新旧元数据是否相同（相同则直接修改）
     * 4. 写入新内容到文件
     * 5. 更新影子表中该节点及子节点的元数据
     * </p>
     *
     * @param newContent 新的文件内容
     * @param filePath   SKILL.md文件路径 {@link Path}
     * @param shadowKey  影子表key（Skill节点绝对路径）
     */
    private void handleSkillMdFileUpdate(String newContent, Path filePath, String shadowKey) {
        log.info("开始处理SKILL.md文件更新, 文件路径: {}", filePath);

        // 解析新内容的元数据格式并验证正确性
        Skill newSkill = parseAndValidateSkillMetadata(newContent);

        Map<String, SkillUtil.ShadowNode> globalShadowTable = SkillUtil.getGlobalShadowTable();
        SkillUtil.ShadowNode existingNode = globalShadowTable.get(shadowKey);

        if (existingNode == null || existingNode.getSkill() == null) {
            log.debug("影子表中无原数据或原数据为空，直接修改文件");
            writeToFile(filePath, newContent);
            if(newSkill.getDeleteFlag() == DeleteConstant.DELETE_FLAG_NO){
                // 新增技能文件夹到影子表
                SkillUtil.addSkillFolderToShadowTable(filePath.getParent());
            }
            log.info("SKILL.md文件修改完成并已新增到影子表: {}", filePath);
            return;
        }

        Skill existingSkill = existingNode.getSkill();
        if(existingSkill.equals(newSkill)){
            log.debug("新元数据与原数据相同，无需更新文件");
            return;
        }

        writeToFile(filePath, newContent);

        SkillUtil.updateOrDeleteSingleNode(shadowKey, newSkill);

        log.info("SKILL.md文件修改完成并已更新影子表: {}", filePath);
    }

    /**
     * 解析并验证SKILL.md文件的元数据格式
     * <p>
     * 使用SkillUtil工具类解析元数据，
     * 并验证解析结果的有效性
     * </p>
     *
     * @param content SKILL.md文件内容
     * @return 解析后的Skill实体对象 {@link Skill}
     * @throws BusinessException 如果元数据格式错误则抛出异常
     */
    private Skill parseAndValidateSkillMetadata(String content) {
        try {
            Path tempFilePath = Files.createTempFile("skill_md_temp", ".md");
            Files.write(tempFilePath, content.getBytes(StandardCharsets.UTF_8));

            Skill skill = SkillUtil.parseSkillFromFile(tempFilePath);

            Files.deleteIfExists(tempFilePath);

            if (skill == null) {
                log.error("SKILL.md元数据格式错误，解析结果为空");
                throw new BusinessException(SkillExceptionEnum.SKILL_MD_METADATA_FORMAT_ERROR);
            }

            log.debug("SKILL.md元数据解析成功, name: {}, version: {}", skill.getName(), skill.getVersion());
            return skill;
        } catch (IOException e) {
            log.error("创建临时文件或解析元数据失败", e);
            throw new BusinessException(SkillExceptionEnum.SKILL_MD_METADATA_FORMAT_ERROR);
        }
    }

    @Override
    public void renameFile(String newFileName, String originalFileName, String skillFolderUrl, String supplementaryFolderUrl) {
        log.info("开始修改文件名, 新文件名: {}, 原文件名: {}, 路径: {}, 补充路径: {}", newFileName, originalFileName, skillFolderUrl, supplementaryFolderUrl);

        Path filePath = checkAndGetFilePath(skillFolderUrl, supplementaryFolderUrl, originalFileName);

        validateFileExists(filePath);

        validateNewFileName(newFileName);

        checkIsNotSkillMdFile(originalFileName);

        Path parentPath = filePath.getParent();
        checkTargetFileNotExists(parentPath, newFileName);

        String shadowKey = Path.of(skillFolderUrl).toString();
        String lockKey = buildDistributedLockKey(shadowKey);

        try {
            acquireDistributedLock(lockKey);

            Path newPath = parentPath.resolve(newFileName);
            Files.move(filePath, newPath);
            log.info("文件重命名成功, 原文件: {}, 新文件: {}", filePath, newPath);
        } catch (IOException e) {
            log.error("文件重命名失败, 原路径: {}, 新名称: {}", filePath, newFileName, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_NAME_INVALID);
        } finally {
            releaseDistributedLock(lockKey);
        }
    }

    /**
     * 验证新文件名合法性
     * <p>调用FileValidator通用方法验证文件名格式</p>
     *
     * @param fileName 文件名
     * @throws BusinessException 如果文件名不合法则抛出异常
     */
    private void validateNewFileName(String fileName) {
        FileValidatorUtil.validateFileName(fileName);
        log.debug("新文件名验证通过: {}", fileName);
    }

    /**
     * 检查是否为SKILL.md文件
     * <p>SKILL.md是Skill的元数据文件，不允许重命名</p>
     *
     * @param fileName 原文件名
     * @throws BusinessException 如果是SKILL.md文件则抛出异常
     */
    private void checkIsNotSkillMdFile(String fileName) {
        if (SkillConstant.SKILL_NAME.equals(fileName)) {
            log.error("不允许重命名SKILL.md文件: {}", fileName);
            throw new BusinessException(SkillExceptionEnum.SKILL_MD_FILE_OPERATION_ERROR);
        }
        log.debug("非SKILL.md文件检查通过: {}", fileName);
    }

    @Override
    public ResponseEntity<Resource> downloadFolderAsZip(String skillFolderUrl, String supplementaryFolderUrl) {
        log.info("开始下载文件夹为ZIP压缩包, 路径: {}, 补充路径: {}", skillFolderUrl, supplementaryFolderUrl);

        Path folderPath = checkAndGetFolderPath(skillFolderUrl, supplementaryFolderUrl);

        validateFolderExists(folderPath);

        try {
            String zipFileName = folderPath.getFileName().toString() + ".zip";
            ByteArrayResource zipResource = ZipUtils.createZipFromFolder(folderPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", URLEncoder.encode(zipFileName, StandardCharsets.UTF_8));

            log.info("文件夹ZIP压缩包生成成功, 文件夹: {}, ZIP大小: {}KB", folderPath, zipResource.contentLength() / 1024);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipResource);
        } catch (IOException e) {
            log.error("创建ZIP压缩包失败, 路径: {}", folderPath, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FOLDER_NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Resource> downloadFile(String skillFolderUrl, String supplementaryFolderUrl, String fileName) {
        log.info("开始下载文件, 路径: {}, 补充路径: {}, 文件名: {}", skillFolderUrl, supplementaryFolderUrl, fileName);

        Path filePath = checkAndGetFilePath(skillFolderUrl, supplementaryFolderUrl, fileName);

        validateFileExists(filePath);

        try {
            Resource fileResource = new UrlResource(filePath.toUri());

            if (!fileResource.exists()) {
                log.error("资源不存在: {}", filePath);
                throw new BusinessException(SkillExceptionEnum.SKILL_FILE_NOT_FOUND);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            headers.setContentLength(Files.size(filePath));

            log.info("文件下载准备完成, 文件: {}, 大小: {}KB", filePath, Files.size(filePath) / 1024);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileResource);
        } catch (IOException e) {
            log.error("读取文件资源失败, 路径: {}", filePath, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FILE_NOT_FOUND);
        }
    }

    /**
     * 检查是否为Skill根目录
     * <p>
     * Skill根目录不允许删除，防止误操作导致所有Skill数据丢失。
     * 通过比较路径的绝对规范化路径与Skill基础目录来判断。
     * </p>
     *
     * @param folderPath 待检查的文件夹路径 {@link Path}
     * @throws BusinessException 如果是Skill根目录则抛出异常
     */
    private void checkNotSkillRootDirectory(Path folderPath) {
        Path normalizedFolderPath = folderPath.toAbsolutePath().normalize();
        Path normalizedBasePath = Paths.get(getSkillsBasePath()).toAbsolutePath().normalize();

        if (normalizedFolderPath.equals(normalizedBasePath)) {
            log.error("不允许删除Skill根目录: {}", folderPath);
            throw new BusinessException(SkillExceptionEnum.SKILL_ROOT_CANNOT_DELETE);
        }

        log.debug("路径检查通过，不是Skill根目录: {}", folderPath);
    }

    /**
     * 构建分布式锁Key
     * <p>
     * 根据Skill节点的shadowKey生成唯一的分布式锁标识，
     * 用于确保同一时间只有一个实例能操作该Skill节点。
     * </p>
     *
     * @param shadowKey Skill节点的绝对路径（作为影子表key）
     * @return 分布式锁的Key字符串
     */
    private String buildDistributedLockKey(String shadowKey) {
        return skillLockKey + shadowKey;
    }

    /**
     * 获取分布式锁
     * <p>
     * 使用SkillUtil工具类提供的通用锁获取方法，
     * 如果获取失败则抛出业务异常中断操作。
     * </p>
     *
     * @param lockKey 分布式锁的Key
     * @throws BusinessException 如果锁获取失败则抛出异常
     */
    private void acquireDistributedLock(String lockKey) {
        log.debug("尝试获取分布式锁, lockKey: {}", lockKey);

        boolean lockAcquired = SkillUtil.tryAcquireLock(lockKey, waitTimeSec, lockTimeout);

        if (!lockAcquired) {
            log.error("分布式锁获取失败, lockKey: {}", lockKey);
            throw new BusinessException(SkillExceptionEnum.SKILL_DISTRIBUTED_LOCK_ACQUIRE_FAILED);
        }

        log.info("分布式锁获取成功, lockKey: {}", lockKey);
    }

    /**
     * 释放分布式锁
     * <p>
     * 使用SkillUtil工具类提供的通用锁释放方法，
     * 在finally块中确保锁资源被正确释放。
     * </p>
     *
     * @param lockKey 分布式锁的Key
     */
    private void releaseDistributedLock(String lockKey) {
        log.debug("释放分布式锁, lockKey: {}", lockKey);
        SkillUtil.releaseLock(lockKey);
    }

    /**
     * 从影子表中批量删除Skill节点及其所有子孙节点
     * <p>
     * 实现流程：
     * 1. 从Redis获取全局影子表数据
     * 2. 查找目标节点及其所有子节点、孙节点（递归遍历）
     * 3. 批量移除这些节点的影子表数据
     * 4. 更新父节点的childrenKeys列表（移除被删除的子节点引用）
     * 5. 将更新后的影子表持久化到Redis
     * </p>
     *
     * <h3>使用Stream API优化：</h3>
     * <ul>
     *   <li>使用Stream递归收集所有子孙节点</li>
     *   <li>使用Stream批量过滤和操作数据</li>
     * </ul>
     *
     * @param targetShadowKey 目标Skill节点的绝对路径（影子表key）
     */
    private void deleteSkillNodesFromShadowTable(String targetShadowKey) {
        log.info("开始从影子表中删除Skill节点及子孙节点, targetShadowKey: {}", targetShadowKey);

        Map<String, SkillUtil.ShadowNode> globalShadowTable = SkillUtil.getGlobalShadowTable();

        Set<String> nodesToDelete = SkillUtil.collectAllDescendantNodes(globalShadowTable, targetShadowKey);

        log.info("待删除的节点数量: {}, 节点列表: {}", nodesToDelete.size(), nodesToDelete);

        nodesToDelete.forEach(globalShadowTable::remove);

        updateParentChildrenKeys(globalShadowTable, targetShadowKey);

        SkillUtil.persistShadowTableToRedis(globalShadowTable);

        log.info("影子表节点批量删除完成");
    }

    /**
     * 更新父节点的childrenKeys列表
     * <p>
     * 当子节点被删除后，需要从父节点的childrenKeys列表中移除该子节点的引用，
     * 保持影子表数据的一致性。
     * </p>
     *
     * @param shadowTable     全局影子表数据
     * @param deletedNodeKey  被删除节点的key
     */
    private void updateParentChildrenKeys(Map<String, SkillUtil.ShadowNode> shadowTable, String deletedNodeKey) {

        SkillUtil.ShadowNode deletedNode = shadowTable.get(deletedNodeKey);

        if (deletedNode != null && deletedNode.getParentKey() != null) {
            String parentKey = deletedNode.getParentKey();
            SkillUtil.ShadowNode parentNode = shadowTable.get(parentKey);

            if (parentNode != null && parentNode.getChildrenKeys() != null) {
                boolean removed = parentNode.getChildrenKeys().remove(deletedNodeKey);

                if (removed) {
                    log.debug("已从父节点的childrenKeys中移除子节点, parentKey: {}, deletedNodeKey: {}",
                            parentKey, deletedNodeKey);
                }
            }
        }
    }

    /**
     * 递归删除目录及其所有内容
     * <p>
     * 使用Files.walk()方法的Stream API深度优先遍历目录树，
     * 按照逆序（从最深层的文件开始）依次删除所有文件和子目录，
     * 最后删除目标目录本身。
     * </p>
     *
     * <h3>实现细节：</h3>
     * <ul>
     *   <li>使用try-with-resources确保Stream资源被正确关闭</li>
     *   <li>使用Comparator.reverseOrder()实现逆序遍历</li>
     *   <li>先删除文件，再删除空目录，避免目录非空异常</li>
     *   <li>使用Stream的forEach方法进行批量删除操作</li>
     * </ul>
     *
     * @param directoryPath 要删除的目标目录路径 {@link Path}
     * @throws BusinessException 如果删除过程中发生IO异常则抛出业务异常
     */
    private void recursivelyDeleteDirectory(Path directoryPath) {
        log.info("开始递归删除目录, path: {}", directoryPath);

        try (Stream<Path> pathStream = Files.walk(directoryPath)) {
            pathStream
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        boolean deleted = file.delete();

                        if (deleted) {
                            log.debug("已删除: {}", file.getPath());
                        } else {
                            log.warn("删除失败: {}", file.getPath());
                        }
                    });

            log.info("目录递归删除成功: {}", directoryPath);
        } catch (IOException e) {
            log.error("目录递归删除失败: {}", directoryPath, e);
            throw new BusinessException(SkillExceptionEnum.SKILL_FOLDER_DELETE_ERROR);
        }
    }

    /**
     * 验证文件夹是否存在
     *
     * @param folderPath 文件夹路径 {@link Path}
     * @throws BusinessException 如果文件夹不存在
     */
    private void validateFolderExists(Path folderPath) {
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            log.error("文件夹不存在或不是目录: {}", folderPath);
            throw new BusinessException(SkillExceptionEnum.SKILL_FOLDER_NOT_FOUND);
        }
        log.debug("文件夹存在性验证通过: {}", folderPath);
    }
}