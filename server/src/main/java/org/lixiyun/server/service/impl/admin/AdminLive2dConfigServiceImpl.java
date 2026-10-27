package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.enums.Live2dConfigExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.file.utils.ZipUtils;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.config.Live2dConfigDTO;
import org.lixiyun.pojo.dto.admin.config.Live2dConfigQueryDTO;
import org.lixiyun.pojo.entity.config.Live2dConfig;
import org.lixiyun.pojo.vo.admin.config.Live2dConfigAdminVO;
import org.lixiyun.server.infrastructure.live2d.Live2dProperties;
import org.lixiyun.server.infrastructure.live2d.ModelFileSigner;
import org.lixiyun.server.mapper.Live2dConfigMapper;
import org.lixiyun.server.service.admin.AdminLive2dConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 管理员 Live2D 模型配置管理服务实现类
 * <p>模型文件存放于 {@code live2d.model-root-path}/{modelName}/（独立目录，<b>不入库 static-resources 静态映射</b>，
 * 访问统一走 {@link ModelFileSigner} 签名下载接口，避免模型文件裸链可下载）；库中存储不带前导斜杠的相对路径，
 * 与前端 {@code resolveResourceUrl} 拼接规则一致（参考 UploadServiceImpl.imageUpload 的返回约定）。</p>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLive2dConfigServiceImpl implements AdminLive2dConfigService {

    /** 模型包最大大小：200MB（对齐 spring.servlet.multipart.max-request-size） */
    private static final long MAX_MODEL_ZIP_SIZE = 200L * 1024 * 1024;

    /** 模型名称白名单：字母/数字/下划线/中划线，防路径穿越 */
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /** exp3/motion3 默认后缀（未指定表达式/动作清单文件名时按此模糊匹配扫描，Live2D Cubism 默认命名） */
    private static final String DEFAULT_EXPRESSION_SUFFIX = ".exp3.json";
    private static final String DEFAULT_MOTION_SUFFIX = ".motion3.json";

    private final Live2dConfigMapper live2dConfigMapper;
    private final Live2dProperties live2dProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadModel(MultipartFile file, String modelName, String aliasName, String expressionFileName,
                            String motionFileName, String semanticPrompt, Integer sort, String remark) {
        log.info("[Live2D模型配置] 上传模型包，modelName：{}，aliasName：{}，原始文件名：{}",
                modelName, aliasName, file.getOriginalFilename());

        // 1. 基础校验：modelName 白名单 + 大小 + zip 属性
        validateModelName(modelName);

        // 同名模型预检（含软删行占用唯一索引）：先查库，再查目录
        if (live2dConfigMapper.countByModelNameIncludingDeleted(modelName) > 0) {
            log.error("[Live2D模型配置] 模型名称已存在（含已删除记录占用唯一索引），modelName：{}", modelName);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_NAME_EXISTS);
        }

        if (!ZipUtils.validateZipFileSize(file, MAX_MODEL_ZIP_SIZE)) {
            log.error("[Live2D模型配置] 模型包大小超出限制，modelName：{}", modelName);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_UPLOAD_ZIP_SIZE_EXCEEDED);
        }
        if (file.isEmpty()) {
            log.error("[Live2D模型配置] 上传的模型包为空，modelName：{}", modelName);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_UPLOAD_ZIP_INVALID);
        }

        // 2. 安全解压至 {模型根目录}/{modelName}/
        Path targetDir = Paths.get(modelRoot() + modelName);
        if (Files.exists(targetDir)) {
            log.error("[Live2D模型配置] 同名模型目录已存在，modelName：{}", modelName);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_UPLOAD_MODEL_DIR_EXISTS);
        }
        try {
            Files.createDirectories(targetDir);
            ZipUtils.extractZipToDirectory(file, targetDir);
        } catch (BusinessException e) {
            log.error("[Live2D模型配置] 模型包解压失败，modelName：{}，原因：{}", modelName, e.getMessage());
            // 解压失败清理残留目录，避免二次上传同名目录冲突
            deleteDirectoryQuietly(targetDir);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_UPLOAD_ZIP_INVALID);
        } catch (IOException e) {
            log.error("[Live2D模型配置] 创建模型目录失败，modelName：{}", modelName, e);
            throw new BusinessException(FileExceptionEnum.FILE_WRITE_ERROR);
        }

        boolean committed = false;
        try {
            // 3. 一次遍历收集全部文件 → 内存分类（替代原先最多 4 次 Files.walk）
            List<Path> allFiles = walkAllFiles(targetDir);

            // 4. 定位 .model3.json 入口文件
            String model3RelativePath = lookupModel3(allFiles, modelName);
            if (model3RelativePath == null) {
                log.error("[Live2D模型配置] 模型包中未找到 .model3.json，modelName：{}", modelName);
                throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_FILE_NOT_FOUND);
            }

            // 5. 解析白名单（文件名精确或默认后缀模糊匹配）
            List<String> expressionList = parseAssetIds(allFiles, expressionFileName, DEFAULT_EXPRESSION_SUFFIX);
            List<String> motionList = parseAssetIds(allFiles, motionFileName, DEFAULT_MOTION_SUFFIX);

            // 6. 落库：coverImagePath / semanticPrompt 未传一律 null（由管理员编辑时手动录入）
            Live2dConfig config = Live2dConfig.builder()
                    .modelName(modelName)
                    .aliasName(aliasName)
                    .modelUrl(model3RelativePath)
                    .modelRelativePath(modelRoot() + modelName + "/")                    .coverImagePath(null)
                    .enabled(Live2dConfig.ENABLED_NO)
                    .sort(sort != null ? sort : 0)
                    .semanticPrompt(null)
                    .motionList(toAssetMap(motionList))
                    .expressionList(toAssetMap(expressionList))
                    .idleExpressionList(new ArrayList<>())
                    .idleMotionList(new ArrayList<>())
                    .idleIntervalSeconds(0)
                    .idleAutoPlay(Live2dConfig.ENABLED_YES)
                    .remark(remark)
                    .build();
            int insert = live2dConfigMapper.insert(config);
            if (insert == 0) {
                log.error("[Live2D模型配置] 新增失败，modelName：{}", modelName);
                throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_CONFIG_ADD_FAIL);
            }
            committed = true;

            log.info("[Live2D模型配置] 上传成功，id：{}，modelName：{}，expressionList：{}，motionList：{}",
                    config.getId(), modelName, expressionList, motionList);
            return config.getId();
        } finally {
            // 任何解析/落库失败都清理目录，避免残留垃圾占用目录名
            if (!committed) {
                deleteDirectoryQuietly(targetDir);
            }
        }
    }

    @Override
    public Live2dConfigAdminVO getDetail(Long id) {
        log.info("[Live2D模型配置] 获取详情，id：{}", id);

        Live2dConfig config = live2dConfigMapper.selectById(id);
        if (config == null) {
            log.error("[Live2D模型配置] 记录不存在，id：{}", id);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_CONFIG_NOT_FOUND);
        }

        Live2dConfigAdminVO vo = new Live2dConfigAdminVO();
        BeanUtil.copyProperties(config, vo, CopyOptions.create().setIgnoreNullValue(true));
        log.info("[Live2D模型配置] 获取详情成功，id：{}，modelName：{}", id, vo.getModelName());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Live2dConfigDTO dto) {
        log.info("[Live2D模型配置] 修改，id：{}", id);

        Live2dConfig oldConfig = live2dConfigMapper.selectById(id);
        if (oldConfig == null) {
            log.error("[Live2D模型配置] 记录不存在，id：{}", id);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_CONFIG_NOT_FOUND);
        }

        // 改名唯一性预检（排除自身；含软删行占用唯一索引，理由同 uploadModel）
        String newName = dto.getModelName();
        if (newName != null && !newName.isBlank()
                && live2dConfigMapper.countByModelNameIncludingDeletedExcludeId(newName, id) > 0) {
            log.error("[Live2D模型配置] 模型名称已存在（含已删除记录占用唯一索引），modelName：{}，id：{}", newName, id);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_NAME_EXISTS);
        }

        // 字段为空不覆盖旧值（CopyOptions().setIgnoreNullValue(true)）
        Live2dConfig newConfig = new Live2dConfig();
        BeanUtil.copyProperties(dto, newConfig, CopyOptions.create().setIgnoreNullValue(true));
        newConfig.setId(id);

        // 白名单 Map 只合并中文 value：以库内 keySet 为基准逐 key 取提交 value，新增 key 忽略、key 不可删
        // （copy 会整体带过提交 map，这里按库内白名单回写，保证资产 id 上传后固定）
        if (dto.getExpressionList() != null) {
            newConfig.setExpressionList(mergeLabels(dto.getExpressionList(), oldConfig.getExpressionList()));
        }
        if (dto.getMotionList() != null) {
            newConfig.setMotionList(mergeLabels(dto.getMotionList(), oldConfig.getMotionList()));
        }

        // 待机配置：列表仅保留「提交 ∩ 白名单 keySet」（去重保序、上限），避免越权待机白名单外资产
        if (dto.getIdleExpressionList() != null) {
            newConfig.setIdleExpressionList(sanitizeIdleList(dto.getIdleExpressionList(), oldConfig.getExpressionList()));
        }
        if (dto.getIdleMotionList() != null) {
            newConfig.setIdleMotionList(sanitizeIdleList(dto.getIdleMotionList(), oldConfig.getMotionList()));
        }
        // idleIntervalSeconds 走整体 copy（setIgnoreNullValue：null 不覆盖旧值；范围由 DTO @NumberOfRanges 校验）

        int row = live2dConfigMapper.updateById(newConfig);
        if (row == 0) {
            log.error("[Live2D模型配置] 更新失败，id：{}", id);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_CONFIG_UPDATE_FAIL);
        }
        log.info("[Live2D模型配置] 修改成功，id：{}，modelName：{}", id, newConfig.getModelName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        log.info("[Live2D模型配置] 删除，id：{}", id);

        Live2dConfig config = live2dConfigMapper.selectById(id);
        if (config == null) {
            log.error("[Live2D模型配置] 记录不存在，id：{}", id);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_CONFIG_NOT_FOUND);
        }

        int row = live2dConfigMapper.deleteById(id);
        if (row == 0) {
            log.error("[Live2D模型配置] 删除失败，id：{}", id);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_CONFIG_DELETE_FAIL);
        }

        log.info("[Live2D模型配置] 删除成功，id：{}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id, Integer enabled) {
        log.info("[Live2D模型配置] 启用/禁用，id：{}，enabled：{}", id, enabled);

        if (live2dConfigMapper.selectById(id) == null) {
            log.error("[Live2D模型配置] 记录不存在，id：{}", id);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_CONFIG_NOT_FOUND);
        }

        if (enabled == Live2dConfig.ENABLED_YES) {
            // 启用互斥：先全量置 0，再置目标为 1 → 全局同一时刻仅一条启用
            live2dConfigMapper.update(null, new LambdaUpdateWrapper<Live2dConfig>()
                    .set(Live2dConfig::getEnabled, Live2dConfig.ENABLED_NO));
        }

        int row = live2dConfigMapper.update(null, new LambdaUpdateWrapper<Live2dConfig>()
                .eq(Live2dConfig::getId, id)
                .set(Live2dConfig::getEnabled, enabled));
        if (row == 0) {
            log.error("[Live2D模型配置] 启用/禁用失败，id：{}", id);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_CONFIG_ENABLED_FAIL);
        }
        log.info("[Live2D模型配置] 启用/禁用成功，id：{}，enabled：{}", id, enabled);
    }

    @Override
    public PageResult<Live2dConfigAdminVO> page(Live2dConfigQueryDTO queryDTO) {
        String modelName = queryDTO.getModelName();
        Integer enabled = queryDTO.getEnabled();
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();

        log.info("[Live2D模型配置] 分页查询，modelName：{}，enabled：{}，pageNum：{}，pageSize：{}",
                modelName, enabled, pageNum, pageSize);

        Page<Live2dConfig> page = new PageQuery(pageSize, pageNum).build();
        // 关键词同时模糊匹配模型名称与模型别名（单搜索框命中任一即返回）
        boolean hasKeyword = modelName != null && !modelName.isBlank();
        Page<Live2dConfig> result = live2dConfigMapper.selectPage(page, new LambdaQueryWrapper<Live2dConfig>()
                .and(hasKeyword, w -> w.like(Live2dConfig::getModelName, modelName)
                        .or()
                        .like(Live2dConfig::getAliasName, modelName))
                .eq(enabled != null, Live2dConfig::getEnabled, enabled)
                .orderByDesc(Live2dConfig::getSort)
                .orderByDesc(Live2dConfig::getUpdatedTime)
        );

        log.info("[Live2D模型配置] 分页查询完成，总数：{}", result.getTotal());
        return PageResult.convert(result, Live2dConfigAdminVO.class);
    }

    // ==================== 私有工具方法 ====================

    /** 模型根目录（含尾斜杠），与 {@link ModelFileSigner} 一致 */
    private String modelRoot() {
        String root = live2dProperties.getModelRootPath();
        if (root == null || root.isBlank()) {
            root = "live2d-models/";
        }
        return root.endsWith("/") ? root : root + "/";
    }

    /**
     * 单次遍历目录树收集全部普通文件
     */
    private List<Path> walkAllFiles(Path targetDir) {
        try (Stream<Path> stream = Files.walk(targetDir)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            log.error("[Live2D模型配置] 遍历模型目录失败，targetDir：{}", targetDir, e);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_ASSET_PARSE_FAIL);
        }
    }

    /**
     * 从全部文件列表定位 .model3.json 入口文件，返回相对路径（{modelRoot}/{modelName}/xxx.model3.json）
     */
    private String lookupModel3(List<Path> allFiles, String modelName) {
        for (Path p : allFiles) {
            String name = p.getFileName().toString().toLowerCase();
            if (name.endsWith(".model3.json")) {
                return toRelativePath(p, modelName);
            }
        }
        return null;
    }

    /**
     * 解析资产白名单：指定文件名时精确匹配单个文件；未指定时按默认后缀模糊匹配扫描全部
     *
     * @param allFiles   单次遍历收集的全部文件
     * @param specified  前端指定的清单文件名（可为空）
     * @param defaultSuffix 默认后缀（*.exp3.json / *.motion3.json）
     * @return 资产 id 列表（去重保序）
     */
    private List<String> parseAssetIds(List<Path> allFiles, String specified, String defaultSuffix) {
        List<Path> matched;
        if (specified != null && !specified.isBlank()) {
            Path relative = Paths.get(specified.replace("\\", "/")).normalize();
            matched = new ArrayList<>();
            for (Path p : allFiles) {
                if (relative.toString().equalsIgnoreCase(toRelativePathUnderModel(p))) {
                    matched.add(p);
                    break;
                }
            }
            if (matched.isEmpty()) {
                log.warn("[Live2D模型配置] 指定的清单文件不存在，specified：{}", specified);
                throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_ASSET_NOT_FOUND);
            }
        } else {
            matched = allFiles.stream()
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(defaultSuffix))
                    .toList();
        }

        Set<String> ids = new LinkedHashSet<>();
        for (Path file : matched) {
            List<String> parsedIds = parseIdsFromJson(file);
            if (parsedIds.isEmpty()) {
                // 退化：取文件名去除后缀作为资产 id（设计文档约定取自 motions/ exp/ 目录真实文件名）
                parsedIds.add(extractBaseName(file));
            }
            for (String id : parsedIds) {
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 从 exp3/motion3 JSON 中解析资产 id 列表
     * <ul>
     *   <li>exp3.json：根节点 {@code Id}（新版 Cubism 表达式有）；缺失返回空走文件名退化</li>
     *   <li>motion3.json：{@code FileReferences.Motions} 各 group 数组中所有 motion 的 {@code Id}</li>
     * </ul>
     */
    private List<String> parseIdsFromJson(Path file) {
        List<String> ids = new ArrayList<>();
        JsonNode root;
        byte[] bytes = null;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            log.warn("[Live2D模型配置] 资产文件读取失败，走文件名退化，file：{}", file);
            return List.of();
        }
        try {
            root = JsonUtils.readTree(bytes);
        } catch (RuntimeException e) {
            log.warn("[Live2D模型配置] 资产文件解析失败，走文件名退化，file：{}，原因：{}", file, e.getMessage());
            return List.of();
        }
        if (root == null) {
            return List.of();
        }

        if (file.getFileName().toString().toLowerCase().endsWith(".exp3.json")) {
            JsonNode idNode = root.get("Id");
            if (idNode != null && !idNode.isNull() && !idNode.asText().isBlank()) {
                ids.add(idNode.asText());
            }
            return ids;
        }
        // motion3.json：收集所有 group 下所有 motion 的 Id
        JsonNode motions = root.path("FileReferences").path("Motions");
        for (JsonNode group : motions) {
            if (!group.isArray()) {
                continue;
            }
            for (JsonNode motion : group) {
                JsonNode idNode = motion.get("Id");
                if (idNode != null && !idNode.isNull() && !idNode.asText().isBlank()) {
                    ids.add(idNode.asText());
                }
            }
        }
        return ids;
    }

    /** 相对模型根目录的相对路径（{modelName}/...） */
    private String toRelativePathUnderModel(Path p) {
        // p 形如 {modelRoot}/{modelName}/xxx；去掉根目录前缀得到 {modelName}/xxx
        String root = modelRoot();
        String asString = p.toString().replace("\\", "/");
        Path rootPath = Paths.get(root).normalize();
        Path filePath = Paths.get(asString).normalize();
        if (filePath.startsWith(rootPath)) {
            return rootPath.relativize(filePath).toString().replace("\\", "/");
        }
        return filePath.getFileName().toString();
    }

    /** 完整相对路径（{modelRoot}/{modelName}/xxx）；toRelativePathUnderModel 已含 {modelName}/ 前缀，禁止再拼 modelName */
    private String toRelativePath(Path p, String modelName) {
        return modelRoot() + toRelativePathUnderModel(p);
    }

    /**
     * 取文件名去除资产后缀的 base name：
     * 优先去双后缀 {.motion3.json / .exp3.json}（与设计文档固定词汇语义一致：haru_g_idle / F01），
     * 否则去最后一个 .json 后缀（如 xxx.userdata3.json → xxx.userdata3）。
     */
    private String extractBaseName(Path file) {
        String fileName = file.getFileName().toString();
        String lower = fileName.toLowerCase();
        for (String suffix : new String[]{".motion3.json", ".exp3.json"}) {
            if (lower.endsWith(suffix)) {
                return fileName.substring(0, fileName.length() - suffix.length());
            }
        }
        int dotIndex = lower.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * 白名单 List → Map（key=资产id，value=空串中文含义，待后台编辑）
     */
    private Map<String, String> toAssetMap(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String id : ids) {
            map.put(id, "");
        }
        return map;
    }

    /** 待机 id 列表最大长度（防异常大数据写入） */
    private static final int MAX_IDLE_LIST_SIZE = 50;

    /**
     * 待机 id 列表清洗：只保留「提交 ∩ 对应白名单 keySet」的 id，去重保序、超限截断。
     * 空列表返回空列表（清空待机配置）；null 由调用方（setIgnoreNullValue）决定是否覆盖。
     */
    private List<String> sanitizeIdleList(List<String> submitted, Map<String, String> whitelist) {
        Set<String> whitelistKeys = whitelist == null ? Set.of() : whitelist.keySet();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        int limit = 0;
        for (String id : submitted) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (limit >= MAX_IDLE_LIST_SIZE) {
                break;
            }
            if (whitelistKeys.contains(id)) {
                result.add(id);
                limit += 1;
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 编辑提交的标签 Map 与库内白名单合并：
     * <ul>
     *   <li>key 以库内白名单（existing）为基准——提交中新增的 key 忽略、库内 key 不可删除；</li>
     *   <li>value 取自提交 map（中文含义可增删改）；提交未含某 key 或值为 null/空串 → 中文清空保留 key。</li>
     * </ul>
     */
    private Map<String, String> mergeLabels(Map<String, String> submitted, Map<String, String> existing) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        for (Map.Entry<String, String> entry : submitted.entrySet()) {
            if (merged.containsKey(entry.getKey())) {
                // 白名单 key 命中才写 value（null → 空串，语义为清空中文）
                merged.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return merged;
    }

    /**
     * 校验模型名称合法性（白名单正则，防路径穿越）
     */
    private void validateModelName(String modelName) {
        if (modelName == null || modelName.isBlank() || !MODEL_NAME_PATTERN.matcher(modelName).matches()) {
            log.error("[Live2D模型配置] 模型名称不合法，modelName：{}", modelName);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_NAME_INVALID);
        }
    }

    /**
     * 静默删除目录（解析失败后的资源清理）
     */
    private void deleteDirectoryQuietly(Path dir) {
        try {
            if (dir != null && Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                log.warn("[Live2D模型配置] 清理模型目录失败，path：{}", p, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("[Live2D模型配置] 清理模型目录失败，dir：{}", dir, e);
        }
    }
}