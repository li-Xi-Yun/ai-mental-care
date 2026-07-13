package org.lixiyun.common.agent.skill.utils;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.skill.constant.SkillConstant;
import org.lixiyun.common.agent.skill.pojo.entity.Skill;
import org.lixiyun.common.agent.skill.service.SkillConversationService;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Skill工具类 - 基于Redis的全局影子表与会话映射管理
 *
 * <p>所有数据存储在Redis中，不使用本地内存缓存</p>
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Slf4j
public class SkillUtil {

    /** Redis中全局影子表的Key，存储所有Skill的树形结构数据（Hash类型） */
    private static final String REDIS_GLOBAL_SHADOW_TABLE_KEY = "skill:global:shadow:table";
    /** Redis中全局影子表构建锁的Key，用于多实例并发构建时的分布式互斥 */
    private static final String REDIS_LOCK_KEY = "skill:global:shadow:table:lock";
    /** Redis中会话缓存的前缀，完整格式：conversation:{conversationId}（Hash类型） */
    private static final String CONVERSATION_CACHE_PREFIX = "conversation:";
    /** 会话Hash中的字段名，用于存储当前会话的Skill映射关系（JSON字符串格式） */
    private static final String SESSION_MAPPING_FIELD = "sessionMapping";
    /** 分布式锁最大等待时间（秒），超过此时间未获取到锁则放弃并等待其他实例完成 */
    private static final long LOCK_WAIT_TIME = 10;
    /** 分布式锁持有时间（秒），防止持有锁的实例宕机导致死锁，超时自动释放 */
    private static final long LOCK_LEASE_TIME = 30;

    /** FileUrlProperties Bean缓存，避免重复从Spring容器获取（性能优化） */
    private static volatile FileUrlProperties cachedFileUrlProperties;

    /** SKILL.md文件的前后分隔符（用于元数据提取） */
    private static final String FRONT_MATTER_DELIMITER = "---";

    /** 指数退避算法的底数（用于等待重试） */
    private static final int EXPONENTIAL_BASE = 3;

    /** 日志打印间隔（每N次重试打印一次日志） */
    private static final int LOG_INTERVAL = 5;

    /**
     * 获取FileUrlProperties Bean（带缓存）
     * <p>
     * 使用双重检查锁定模式（Double-Checked Locking）缓存Spring Bean，
     * 避免高频调用时反复从Spring容器获取，显著提升性能。
     * </p>
     *
     * @return FileUrlProperties 配置对象
     */
    private static FileUrlProperties getFileUrlProperties() {
        if (cachedFileUrlProperties == null) {
            synchronized (SkillUtil.class) {
                if (cachedFileUrlProperties == null) {
                    cachedFileUrlProperties = SpringUtil.getBean(FileUrlProperties.class);
                    log.debug("FileUrlProperties Bean已缓存");
                }
            }
        }
        return cachedFileUrlProperties;
    }

    /**
     * 重置FileUrlProperties缓存（测试或配置变更时使用）
     * <p>
     * 强制下次调用时重新从Spring容器获取最新的配置。
     * </p>
     */
    public static void resetFileUrlPropertiesCache() {
        synchronized (SkillUtil.class) {
            cachedFileUrlProperties = null;
            log.info("FileUrlProperties缓存已重置");
        }
    }

    /**
     * 获取全局影子表（从Redis实时读取）
     *
     * @return 全局影子表Map，如果不存在返回空Map
     */
    public static Map<String, ShadowNode> getGlobalShadowTable() {
        Map<String, ShadowNode> shadowTable = RedisUtils.getCacheObject(REDIS_GLOBAL_SHADOW_TABLE_KEY);
        return shadowTable != null ? shadowTable : new ConcurrentHashMap<>();
    }

    /**
     * 判断全局影子表是否为空
     *
     * @return true-为空或不存在，false-有数据
     */
    public static boolean isGlobalShadowTableEmpty() {
        return !RedisUtils.hasKey(REDIS_GLOBAL_SHADOW_TABLE_KEY);
    }

    /**
     * 加载或刷新全局影子表
     * <p>
     * 使用分布式锁保证并发安全，优先从Redis加载，
     * 如果Redis中不存在则构建并持久化到Redis
     * </p>
     */
    public static void loadGlobalShadowTable() {
        if (!isGlobalShadowTableEmpty()) {
            log.info("Skill工具类-全局影子表已存在，跳过加载");
            return;
        }
        tryAcquireLockAndLoad();
    }

    /**
     * 强制刷新全局影子表（删除后重建）
     */
    public static void forceRefreshGlobalShadowTable() {
        tryAcquireLockAndLoad(true);
    }

    /**
     * 获取分布式锁并执行加载逻辑
     *
     * @param forceRefresh 是否强制刷新
     */
    private static void tryAcquireLockAndLoad(boolean forceRefresh) {
        boolean acquired = tryAcquireLock(REDIS_LOCK_KEY, LOCK_WAIT_TIME, LOCK_LEASE_TIME);
        if (!acquired) {
            log.warn("Skill工具类-获取分布式锁失败，等待其他实例完成加载");
            waitForLoadingComplete();
            return;
        }
        try {
            boolean redisExists = RedisUtils.hasKey(REDIS_GLOBAL_SHADOW_TABLE_KEY);
            if (redisExists && !forceRefresh) {
                log.info("Skill工具类-全局影子表已存在于Redis，无需重复加载");
                return;
            }

            if (forceRefresh && redisExists) {
                RedisUtils.deleteObject(REDIS_GLOBAL_SHADOW_TABLE_KEY);
                log.info("Skill工具类-已删除旧的全局影子表");
            }

            buildAndPersistShadowTable();
        } catch (Exception e) {
            log.error("Skill工具类-加载全局影子表异常", e);
        } finally {
            releaseLock(REDIS_LOCK_KEY);
        }
    }

    private static void tryAcquireLockAndLoad() {
        tryAcquireLockAndLoad(false);
    }

    private static void waitForLoadingComplete() {
        int retryCount = 0;
        int maxRetries = 20;
        long initialSleepMs = 200;  // 初始等待时间
        long maxSleepMs = 2000;     // 最大等待时间

        while (retryCount < maxRetries && isGlobalShadowTableEmpty()) {
            try {
                long sleepTime = Math.min(initialSleepMs * (long) Math.pow(2, retryCount / EXPONENTIAL_BASE), maxSleepMs);
                Thread.sleep(sleepTime);
                retryCount++;

                if (retryCount % LOG_INTERVAL == 0) {  // 每LOG_INTERVAL次打印一次
                    long totalWaitMs = calculateTotalWaitTime(retryCount);
                    log.info("Skill工具类-仍在等待全局影子表加载，已重试{}次，累计等待{}ms", retryCount, totalWaitMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Skill工具类-等待被中断");
                break;
            }
        }

        if (isGlobalShadowTableEmpty()) {
            long totalWaitMs = calculateTotalWaitTime(maxRetries);
            log.error("Skill工具类-等待全局影子表加载超时（已等待{}ms）", totalWaitMs);
        } else {
            long totalWaitMs = calculateTotalWaitTime(retryCount);
            log.debug("Skill工具类-全局影子表加载完成，共等待{}次，累计{}ms", retryCount, totalWaitMs);
        }
    }

    /**
     * 计算累计等待时间（指数退避算法）
     *
     * @param retryCount 当前重试次数
     * @return 累计等待时间（毫秒）
     */
    private static long calculateTotalWaitTime(int retryCount) {
        long totalWaitMs = 0;
        long initialSleepMs = 200;
        long maxSleepMs = 2000;

        for (int i = 0; i < retryCount; i++) {
            totalWaitMs += Math.min(initialSleepMs * (long) Math.pow(2, i / EXPONENTIAL_BASE), maxSleepMs);
        }

        return totalWaitMs;
    }

    private static void buildAndPersistShadowTable() {
        FileUrlProperties fileUrlProperties = getFileUrlProperties();
        String skillsPath = fileUrlProperties.getUploadSkills();
        Path rootPath = Paths.get(skillsPath);

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            log.warn("Skill工具类-Skills根目录不存在：{}", skillsPath);
            return;
        }

        Map<String, ShadowNode> shadowTable = new ConcurrentHashMap<>();
        depthFirstBuildShadowTree(rootPath, shadowTable, null);

        RedisUtils.setCacheObject(REDIS_GLOBAL_SHADOW_TABLE_KEY, shadowTable);

        log.info("Skill工具类-全局影子表构建完成并持久化到Redis，节点数量：{}", shadowTable.size());
    }

    /**
     * 深度优先构建影子树
     *
     * @param currentPath 当前路径，绝对路径
     * @param shadowTable 影子表
     * @param parentKey   父节点key，绝对路径
     */
    private static void depthFirstBuildShadowTree(Path currentPath, Map<String, ShadowNode> shadowTable, String parentKey) {
        if (!Files.exists(currentPath)) {
            return;
        }

        if (!Files.isDirectory(currentPath)) {
            return;
        }

        String relativePath = toRelativePath(currentPath);
        Path skillMdPath = currentPath.resolve(SkillConstant.SKILL_NAME);
        boolean hasSkillMd = Files.exists(skillMdPath) && Files.isRegularFile(skillMdPath);

        if (!hasSkillMd || isEmptyDirectory(currentPath)) {
            log.warn("Skill工具类-空文件夹或无SKILL.md文件：{}", relativePath);
            return;
        }

        Skill skill = parseSkillFromFile(skillMdPath);
        if (skill != null) {
            ShadowNode node = ShadowNode.builder()
                    .skill(skill)
                    .relativePath(relativePath)
                    .parentKey(parentKey)
                    .childrenKeys(new ArrayList<>())
                    .build();

            // 如果Skill已删除，跳过该节点
            if(skill.getDeleteFlag() == DeleteConstant.DELETE_FLAG_YES){
                return;
            }
            shadowTable.put(relativePath, node);

            List<Path> children = listChildrenDirectories(currentPath);
            for (Path child : children) {
                depthFirstBuildShadowTree(child, shadowTable, relativePath);
                String childRelativePath = toRelativePath(child);
                if (shadowTable.containsKey(childRelativePath)) {
                    node.getChildrenKeys().add(childRelativePath);
                }
            }
        } else {
            log.warn("Skill工具类-解析SKILL.md失败，跳过该文件夹：{}", relativePath);
        }
    }

    private static boolean isEmptyDirectory(Path directory) {
        try {
            return Files.list(directory).findFirst().isEmpty();
        } catch (IOException e) {
            log.error("无法读取目录内容，视为空目录: {}", directory, e);
            return true;
        }
    }

    private static List<Path> listChildrenDirectories(Path parent) {
        try {
            return Files.list(parent)
                    .filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Skill工具类-列出子目录失败：{}", parent, e);
            return List.of();
        }
    }

    /**
     * 构建一级Skill会话映射表并更新到Redis和DB
     *
     * <h3>完整流程：</h3>
     * <ol>
     *   <li>从Redis获取全局影子表数据</li>
     *   <li>判断是否为空 → 为空则触发全局影子表加载</li>
     *   <li>层次遍历构建一级Skill会话映射表（过滤已删除、非一级节点）</li>
     *   <li><b>DB更新会话映射表（Conversation.tSessionMapping字段）</b></li>
     *   <li>更新Redis会话映射表（conversation:{id}的sessionMapping字段）</li>
     *   <li>返回该一级会话映射表</li>
     * </ol>
     *
     * @param conversationId 会话ID，用于关联DB记录和Redis缓存
     * @return 一级Skill的context_id与绝对路径映射关系
     */
    public static Map<String, String> buildSessionMappingTable(Long conversationId) {
        Map<String, ShadowNode> globalShadowTable = getGlobalShadowTable();
        if (globalShadowTable.isEmpty()) {
            log.warn("Skill工具类-全局影子表为空，尝试触发加载");
            loadGlobalShadowTable();
            globalShadowTable = getGlobalShadowTable();
            if (globalShadowTable.isEmpty()) {
                log.error("Skill工具类-全局影子表仍为空，无法构建会话映射表");
                return new ConcurrentHashMap<>();
            }
        }

        FileUrlProperties fileUrlProperties = getFileUrlProperties();
        String skillsPath = fileUrlProperties.getUploadSkills();

        Map<String, String> sessionMapping = globalShadowTable.entrySet().stream()
                .filter(entry -> entry.getValue().getSkill() != null && entry.getValue().getSkill().getDeleteFlag() == DeleteConstant.DELETE_FLAG_NO)
                .filter(entry -> isRootLevelSkill(entry.getKey(), skillsPath))
                .collect(Collectors.toConcurrentMap(
                        entry -> IdUtil.fastSimpleUUID(),
                        entry -> entry.getValue().getRelativePath(),
                        (existing, replacement) -> existing,
                        ConcurrentHashMap::new
                ));

        updateSessionMappingToDB(conversationId, sessionMapping);
        updateSessionMappingToRedis(conversationId, sessionMapping);

        log.info("Skill工具类-会话映射表构建完成，conversationId：{}，一级Skill数量：{}", conversationId, sessionMapping.size());
        return sessionMapping;
    }

    private static boolean isRootLevelSkill(String relativePath, String skillsPath) {
        // 相对路径的一级Skill是那些路径不包含目录分隔符的（即直接在根目录下的）
        return !relativePath.contains("/") && !relativePath.contains("\\");
    }

    /**
     * 更新会话映射表到数据库（Conversation.tSessionMapping字段）
     * <p>
     * 将会话映射表数据序列化为JSON字符串后，更新到会话表的tSessionMapping字段。
     * 该操作确保数据持久化到DB，即使Redis缓存失效也能恢复。
     * </p>
     *
     * @param conversationId 会话ID
     * @param sessionMapping 会话映射表数据（context_id → 绝对路径）
     */
    public static void updateSessionMappingToDB(Long conversationId, Map<String, String> sessionMapping) {
        try {
            SkillConversationService skillConversationService = SpringUtil.getBean(SkillConversationService.class);
            int updated = skillConversationService.updateConversationSessionMapping(conversationId.toString(), sessionMapping);

            if (updated > 0) {
                log.debug("Skill工具类-已更新DB会话映射表，conversationId：{}，影响行数：{}", conversationId, updated);
            } else {
                log.warn("Skill工具类-DB会话映射表更新未生效，conversationId：{}，可能记录不存在", conversationId);
            }
        } catch (Exception e) {
            log.error("Skill工具类-更新DB会话映射表失败，conversationId：{}", conversationId, e);
        }
    }

    /**
     * 更新会话映射表到Redis会话缓存
     *
     * @param conversationId 会话ID
     * @param sessionMapping 会话映射表数据
     */
    public static void updateSessionMappingToRedis(Long conversationId, Map<String, String> sessionMapping) {
        String redisKey = CONVERSATION_CACHE_PREFIX + conversationId;
        try {
            String mappingJson = JsonUtils.toJsonString(sessionMapping);
            RedisUtils.setCacheMapValue(redisKey, SESSION_MAPPING_FIELD, mappingJson);
            log.debug("Skill工具类-已更新Redis会话映射表，conversationId：{}，数据大小：{}", conversationId, mappingJson.length());
        } catch (Exception e) {
            log.error("Skill工具类-序列化会话映射表失败", e);
        }
    }

    /**
     * 从Redis获取会话映射表
     *
     * @param conversationId 会话ID
     * @return 会话映射表，如果不存在返回空Map
     */
    public static Map<String, String> getSessionMappingFromRedis(Long conversationId) {
        String redisKey = CONVERSATION_CACHE_PREFIX + conversationId;
        String mappingJson = RedisUtils.getCacheMapValue(redisKey, SESSION_MAPPING_FIELD);
        if (StrUtil.isBlank(mappingJson)) {
            return new ConcurrentHashMap<>();
        }

        try {
            return JsonUtils.parseObject(mappingJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Skill工具类-反序列化会话映射表失败，conversationId：{}", conversationId, e);
            return new ConcurrentHashMap<>();
        }
    }

    /**
     * 根据context_id获取映射的全局影子表key（含完整校验）
     *
     * @param conversationId 会话ID
     * @param contextId      context_id
     * @return 全局影子表的key（绝对路径），校验失败返回null
     */
    public static String getMappingValue(Long conversationId, String contextId) {
        Map<String, String> sessionMapping = getSessionMappingFromRedis(conversationId);
        String shadowKey = sessionMapping.get(contextId);
        if (shadowKey == null) {
            log.warn("Skill工具类-未找到contextId对应的映射，conversationId：{}，contextId：{}", conversationId, contextId);
            return null;
        }

        if (!checkAncestorDeleteFlag(shadowKey)) {
            log.warn("Skill工具类-祖先节点存在删除标识，conversationId：{}，contextId：{}，路径：{}", conversationId, contextId, shadowKey);
            return null;
        }

        Path path = Paths.get(shadowKey);
        if (!Files.exists(path)) {
            log.warn("Skill工具类-路径不存在：{}", shadowKey);
            return null;
        }

        return shadowKey;
    }

    /**
     * 祖先删除标识检查（从Redis实时查询）
     * <p>
     * 递归检查指定节点到一级Skill层的所有祖先节点是否存在删除标识。
     * 该方法会沿着父节点链一直向上遍历，直到一级Skill层（parentKey为null），
     * 确保整个路径上没有任何被标记为已删除的祖先节点。
     * </p>
     *
     * <h3>检查范围：</h3>
     * <ul>
     *   <li><b>起始节点</b>：参数传入的shadowKey对应的节点</li>
     *   <li><b>终止条件</b>：到达一级Skill层（parentKey为null的根节点）</li>
     *   <li><b>检查内容</b>：路径上的每一个祖先节点的deleteFlag字段</li>
     * </ul>
     *
     * <h3>算法流程示例：</h3>
     * <pre>{@code
     * 假设目录结构：
     * skills/
     * ├── text-analysis/              (一级Skill, deleteFlag=0)
     * │   ├── sub-nlp/               (二级Skill, deleteFlag=1) ← 已删除！
     * │   │   └── deep-nlp/          (三级Skill, deleteFlag=0)
     * │   └── sub-classify/          (二级Skill, deleteFlag=0)
     *
     * 检查 deep-nlp 节点时：
     * Step 1: deep-nlp → parent=sub-nlp → deleteFlag=1 → 返回false ❌
     *
     * 检查 sub-classify 节点时：
     * Step 1: sub-classify → parent=text-analysis → deleteFlag=0 ✓
     * Step 2: text-analysis → parent=null → 结束循环 → 返回true ✅
     * }</pre>
     *
     * <h3>使用场景：</h3>
     * <ol>
     *   <li>AI模型调用工具获取Skill元数据前的前置校验</li>
     *   <li>构建会话映射表时的过滤条件</li>
     *   <li>获取子节点列表时的安全检查</li>
     * </ol>
     *
     * @param shadowKey 全局影子表中的key（Skill文件夹绝对路径）
     * @return boolean 检查结果：
     *         <ul>
     *           <li>true - 所有祖先节点均正常（无删除标识），可以访问该Skill</li>
     *           <li>false - 存在至少一个祖先节点被删除，或路径不完整，不可访问该Skill</li>
     *         </ul>
     */
    public static boolean checkAncestorDeleteFlag(String shadowKey) {
        Map<String, ShadowNode> globalShadowTable = getGlobalShadowTable();

        ShadowNode currentNode = globalShadowTable.get(shadowKey);
        if (currentNode == null) {
            log.warn("Skill工具类-祖先删除检查失败：节点不存在于全局影子表中，shadowKey：{}", shadowKey);
            return false;
        }

        String parentKey = currentNode.getParentKey();

        while (parentKey != null) {
            ShadowNode parentNode = globalShadowTable.get(parentKey);

            if (parentNode == null) {
                log.warn("Skill工具类-祖先删除检查失败：父节点不存在，shadowKey：{}，缺失父节点：{}", shadowKey, parentKey);
                return false;
            }

            if (parentNode.getSkill() != null && parentNode.getSkill().getDeleteFlag() != null && parentNode.getSkill().getDeleteFlag() == DeleteConstant.DELETE_FLAG_YES) {
                log.debug("Skill工具类-发现祖先删除标识，shadowKey：{}，已删除祖先：{}，层级：{}",
                        shadowKey, parentKey, getNodeDepth(shadowKey, parentKey));
                return false;
            }

            parentKey = parentNode.getParentKey();
        }

        log.trace("祖先删除检查通过，shadowKey：{}，已检查所有层级", shadowKey);
        return true;
    }

    /**
     * 计算两个节点之间的层级距离
     *
     * @param childKey  子节点key
     * @param ancestorKey 祖先节点key
     * @return 层级数（0表示相邻父子关系）
     */
    private static int getNodeDepth(String childKey, String ancestorKey) {
        Map<String, ShadowNode> globalShadowTable = getGlobalShadowTable();
        int depth = 0;
        String currentKey = childKey;

        while (currentKey != null && !currentKey.equals(ancestorKey)) {
            ShadowNode node = globalShadowTable.get(currentKey);
            if (node == null) {
                break;
            }
            currentKey = node.getParentKey();
            depth++;
        }

        return depth;
    }

    /**
     * 获取子节点列表信息并为每个子节点生成新的context_id
     *
     * @param conversationId 会话ID
     * @param contextId      当前context_id
     * @return 子节点的context_id列表
     */
    public static List<String> buildChildrenContextIds(Long conversationId, String contextId) {
        Map<String, String> sessionMapping = getSessionMappingFromRedis(conversationId);
        String shadowKey = sessionMapping.get(contextId);
        if (shadowKey == null) {
            return List.of();
        }

        Map<String, ShadowNode> globalShadowTable = getGlobalShadowTable();
        ShadowNode node = globalShadowTable.get(shadowKey);
        if (node == null || node.getChildrenKeys() == null || node.getChildrenKeys().isEmpty()) {
            return List.of();
        }

        List<String> childrenContextIds = new ArrayList<>();
        for (String childKey : node.getChildrenKeys()) {
            ShadowNode childNode = globalShadowTable.get(childKey);
            if (childNode != null && childNode.getSkill() != null && childNode.getSkill().getDeleteFlag() == DeleteConstant.DELETE_FLAG_NO) {
                String childContextId = IdUtil.fastSimpleUUID();
                sessionMapping.put(childContextId, childKey);
                childrenContextIds.add(childContextId);
            }
        }

        updateSessionMappingToDB(conversationId, sessionMapping);
        updateSessionMappingToRedis(conversationId, sessionMapping);
        return childrenContextIds;
    }

    /**
     * 获取Skill元数据信息
     *
     * @param conversationId 会话ID
     * @param contextId      context_id
     * @return Skill元数据
     */
    public static Skill getSkillMetadata(Long conversationId, String contextId) {
        String shadowKey = getMappingValue(conversationId, contextId);
        if (shadowKey == null) {
            return null;
        }

        Map<String, ShadowNode> globalShadowTable = getGlobalShadowTable();
        ShadowNode node = globalShadowTable.get(shadowKey);
        return node != null ? node.getSkill() : null;
    }

    /**
     * 更新指定路径的Skill数据到Redis全局影子表
     *
     * @param relativePath Skill相对于项目根目录的相对路径
     * @param skill        更新后的Skill对象
     */
    public static void updateSkillInShadowTable(String relativePath, Skill skill) {
        Map<String, ShadowNode> globalShadowTable = getGlobalShadowTable();
        ShadowNode existingNode = globalShadowTable.get(relativePath);
        if (existingNode != null) {
            existingNode.setSkill(skill);
            RedisUtils.setCacheObject(REDIS_GLOBAL_SHADOW_TABLE_KEY, globalShadowTable);
            log.info("Skill工具类-已更新Redis全局影子表中Skill数据：{}", relativePath);
        }
    }

    /**
     * 将修改后的影子表数据覆盖写入Redis缓存
     * <p>
     * 与 {@link #forceRefreshGlobalShadowTable()} 不同，此方法不会重新从磁盘构建影子表，
     * 而是直接将内存中已修改的影子表数据持久化到Redis，适用于删除/修改节点等增量变更场景。
     * </p>
     *
     * @param shadowTable 修改后的影子表数据
     */
    public static void persistShadowTableToRedis(Map<String, ShadowNode> shadowTable) {
        RedisUtils.setCacheObject(REDIS_GLOBAL_SHADOW_TABLE_KEY, shadowTable);
        log.info("Skill工具类-已将修改后的影子表覆盖写入Redis，节点数量：{}", shadowTable.size());
    }

    /**
     * 更新或删除缓存中的单一Skill节点
     * <p>
     * 根据新的Skill对象状态智能处理影子表节点：
     * <ul>
     *   <li>如果新Skill的deleteFlag为已删除标识（DELETE_FLAG_YES）：
     *       <ul>
     *         <li>判断是否为索引节点（isIndex == 1）</li>
     *         <li>如果是索引节点，递归删除其下所有子节点数据</li>
     *         <li>删除当前节点本身</li>
     *         <li>更新父节点的childrenKeys列表</li>
     *       </ul>
     *   </li>
     *   <li>如果新Skill的deleteFlag为未删除标识（DELETE_FLAG_NO）：
     *       <ul>
     *         <li>正常更新该节点的Skill对象数据</li>
     *         <li>如果节点不存在则创建新节点</li>
     *       </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * <h3>使用场景：</h3>
     * <pre>{@code
     * // 场景1：用户修改SKILL.md文件，将delete_flag改为1（标记为已删除）
     * Skill newSkill = Skill.builder().name("test").deleteFlag(1).isIndex(1).build();
     * SkillUtil.updateOrDeleteSingleNode("skills/test", newSkill);
     * // 结果：skills/test及其所有子节点从影子表中删除
     *
     * // 场景2：用户修改SKILL.md文件，更新名称和版本（保持未删除状态）
     * Skill newSkill = Skill.builder().name("newName").version("2.0").deleteFlag(0).build();
     * SkillUtil.updateOrDeleteSingleNode("skills/test", newSkill);
     * // 结果：skills/test节点的Skill对象被更新为新内容
     * }</pre>
     *
     * @param shadowKey 目标节点的相对路径（影子表key）
     * @param newSkill  新的Skill对象 {@link Skill}
     */
    public static void updateOrDeleteSingleNode(String shadowKey, Skill newSkill) {
        log.info("开始更新或删除单一Skill节点, shadowKey: {}, deleteFlag: {}, isIndex: {}",
                shadowKey, newSkill.getDeleteFlag(), newSkill.getIsIndex());

        Map<String, ShadowNode> globalShadowTable = getGlobalShadowTable();

        if (newSkill.getDeleteFlag() != null && newSkill.getDeleteFlag() == DeleteConstant.DELETE_FLAG_YES) {
            handleNodeDeletion(globalShadowTable, shadowKey, newSkill.getIsIndex());
        } else {
            handleNodeUpdate(globalShadowTable, shadowKey, newSkill);
        }

        persistShadowTableToRedis(globalShadowTable);
        log.info("单一Skill节点处理完成, shadowKey: {}", shadowKey);
    }

    /**
     * 处理节点删除逻辑
     * <p>
     * 当Skill被标记为已删除时执行此方法：
     * 1. 判断是否为索引节点
     * 2. 如果是索引节点，递归收集并删除所有子孙节点
     * 3. 删除目标节点本身
     * 4. 更新父节点的childrenKeys列表（移除被删除的子节点引用）
     * </p>
     *
     * @param globalShadowTable 全局影子表数据
     * @param targetShadowKey   要删除的目标节点key
     * @param isIndex           是否为索引节点（1-是，0-否）
     */
    private static void handleNodeDeletion(Map<String, ShadowNode> globalShadowTable,
                                           String targetShadowKey,
                                           Integer isIndex) {
        log.warn("检测到Skill节点需要删除, shadowKey: {}, isIndex: {}", targetShadowKey, isIndex);

        Set<String> nodesToDelete = new HashSet<>();
        nodesToDelete.add(targetShadowKey);

        if (isIndex != null && isIndex == 1) {
            log.info("检测到索引节点，将递归删除所有子节点: {}", targetShadowKey);
            nodesToDelete = collectAllDescendantNodes(globalShadowTable, targetShadowKey);
            log.info("待删除的总节点数: {}, 包含目标节点及{}个子节点", nodesToDelete.size(), nodesToDelete.size() - 1);
        }

        nodesToDelete.forEach(globalShadowTable::remove);

        updateParentChildrenKeysForDeletion(globalShadowTable, targetShadowKey);

        log.info("节点删除完成, 已删除节点数: {}, 节点列表: {}", nodesToDelete.size(), nodesToDelete);
    }

    /**
     * 处理节点更新逻辑
     * <p>
     * 当Skill处于正常状态（未删除）时执行此方法：
     * 1. 检查节点是否已存在于影子表
     * 2. 如果存在，更新Skill对象
     * 3. 如果不存在，创建新节点并添加到父节点的childrenKeys
     * </p>
     *
     * @param globalShadowTable 全局影子表数据
     * @param targetShadowKey   目标节点key
     * @param newSkill          新的Skill对象 {@link Skill}
     */
    private static void handleNodeUpdate(Map<String, ShadowNode> globalShadowTable,
                                         String targetShadowKey,
                                         Skill newSkill) {
        log.debug("更新Skill节点数据, shadowKey: {}", targetShadowKey);

        ShadowNode existingNode = globalShadowTable.get(targetShadowKey);

        if (existingNode != null) {
            existingNode.setSkill(newSkill);
            log.debug("已更新现有节点: {}", targetShadowKey);
        } else {
            Path targetPath = toAbsolutePath(targetShadowKey);
            Path parentPath = targetPath.getParent();
            String parentKey = parentPath != null ? toRelativePath(parentPath) : null;

            ShadowNode newNode = ShadowNode.builder()
                    .skill(newSkill)
                    .relativePath(targetShadowKey)
                    .parentKey(parentKey)
                    .childrenKeys(new ArrayList<>())
                    .build();

            globalShadowTable.put(targetShadowKey, newNode);

            if (parentKey != null && globalShadowTable.containsKey(parentKey)) {
                ShadowNode parentNode = globalShadowTable.get(parentKey);
                if (!parentNode.getChildrenKeys().contains(targetShadowKey)) {
                    parentNode.getChildrenKeys().add(targetShadowKey);
                    log.debug("已添加到父节点的childrenKeys, 父节点: {}, 子节点: {}", parentKey, targetShadowKey);
                }
            }

            log.info("已创建新节点并添加到影子表: {}", targetShadowKey);
        }
    }

    /**
     * 收集所有子孙节点（包含目标节点本身）
     * <p>
     * 使用广度优先搜索（BFS）算法遍历树形结构，
     * 收集从目标节点开始的所有后代节点以及目标节点本身。
     * </p>
     *
     * <h3>算法说明：</h3>
     * <pre>{@code
     * 示例树形结构：
     *         A (target)
     *        / \
     *       B   C
     *      / \   \
     *     D   E   F
     *
     * 调用 collectAllDescendantNodes(shadowTable, "A")
     * 返回结果：[A, B, C, D, E, F]
     * }</pre>
     *
     * <h3>使用场景：</h3>
     * <ul>
     *   <li>文件夹重命名时收集需要删除的旧节点</li>
     *   <li>删除Skill节点时级联删除所有子节点</li>
     *   <li>批量更新某棵子树的所有节点</li>
     * </ul>
     *
     * @param shadowTable      全局影子表数据
     * @param targetShadowKey  目标节点的key
     * @return 包含目标节点及其所有子孙节点key的Set集合
     */
    public static Set<String> collectAllDescendantNodes(Map<String, ShadowNode> shadowTable,
                                                         String targetShadowKey) {
        Set<String> descendantNodes = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(targetShadowKey);

        while (!queue.isEmpty()) {
            String currentNodeKey = queue.poll();
            if (descendantNodes.contains(currentNodeKey)) {
                continue;
            }

            descendantNodes.add(currentNodeKey);
            ShadowNode currentNode = shadowTable.get(currentNodeKey);

            if (currentNode != null && currentNode.getChildrenKeys() != null) {
                currentNode.getChildrenKeys().stream()
                        .filter(childKey -> !descendantNodes.contains(childKey))
                        .forEach(queue::add);
            }
        }

        return descendantNodes;
    }

    /**
     * 更新父节点的childrenKeys列表（删除场景专用）
     * <p>
     * 当子节点被删除后，需要从父节点的childrenKeys列表中移除该子节点的引用，
     * 保持影子表数据的一致性。
     * </p>
     *
     * @param shadowTable     全局影子表数据
     * @param deletedNodeKey  被删除节点的key
     */
    private static void updateParentChildrenKeysForDeletion(Map<String, ShadowNode> shadowTable,
                                                            String deletedNodeKey) {
        ShadowNode deletedNode = shadowTable.get(deletedNodeKey);

        if (deletedNode == null || deletedNode.getParentKey() == null) {
            log.debug("被删除节点无父节点信息或已被移除, deletedNodeKey: {}", deletedNodeKey);
            return;
        }

        String parentKey = deletedNode.getParentKey();
        ShadowNode parentNode = shadowTable.get(parentKey);

        if (parentNode != null && parentNode.getChildrenKeys() != null) {
            boolean removed = parentNode.getChildrenKeys().remove(deletedNodeKey);
            if (removed) {
                log.debug("已从父节点的childrenKeys中移除被删除的子节点, parentKey: {}, deletedNodeKey: {}",
                        parentKey, deletedNodeKey);
            } else {
                log.debug("父节点的childrenKeys中未找到该子节点（可能已被移除）, parentKey: {}, deletedNodeKey: {}",
                        parentKey, deletedNodeKey);
            }
        }
    }

    /**
     * 将新上传的Skill文件夹添加到影子表中（增量更新）
     * <p>
     * 用于文件上传场景，当用户上传SKILL.md文件时，将该Skill文件夹及其所有合法的子Skill节点
     * 添加到影子表中。与 {@link #forceRefreshGlobalShadowTable()} 不同，此方法只做增量更新，
     * 不影响其他已存在的节点数据。
     * </p>
     *
     * @param skillFolderPath Skill文件夹路径 {@link Path}
     */
    public static void addSkillFolderToShadowTable(Path skillFolderPath) {
        String relativePath = toRelativePath(skillFolderPath);
        Map<String, ShadowNode> globalShadowTable = getGlobalShadowTable();

        if (globalShadowTable.containsKey(relativePath)) {
            log.debug("Skill工具类-该Skill节点已存在于影子表：{}", relativePath);
            return;
        }

        Path parentPath = skillFolderPath.getParent();
        String parentKey = parentPath != null ? toRelativePath(parentPath) : null;

        depthFirstBuildShadowTree(skillFolderPath, globalShadowTable, parentKey);

        if (parentKey != null && globalShadowTable.containsKey(parentKey)) {
            ShadowNode parentNode = globalShadowTable.get(parentKey);
            if (!parentNode.getChildrenKeys().contains(relativePath)) {
                parentNode.getChildrenKeys().add(relativePath);
                log.debug("Skill工具类-已更新父节点的childrenKeys，父节点：{}，新增子节点：{}", parentKey, relativePath);
            }
        }

        persistShadowTableToRedis(globalShadowTable);
        log.info("Skill工具类-已完成Skill文件夹影子表的增量更新：{}", relativePath);
    }

    public static Skill parseSkillFromFile(Path skillMdPath) {
        String metadata = extractMetadata(skillMdPath);
        if (StrUtil.isBlank(metadata)) {
            return null;
        }
        return parseSkillInstance(metadata);
    }

    public static String extractMetadata(Path skillMdPath) {
        StringBuilder sb = new StringBuilder();
        boolean inFrontMatter = false;
        try (BufferedReader reader = Files.newBufferedReader(skillMdPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equals(FRONT_MATTER_DELIMITER)) {
                    inFrontMatter = !inFrontMatter;
                    if (!inFrontMatter) break;
                } else if (inFrontMatter) {
                    sb.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            log.error("Skill工具类-提取元数据失败：{}", skillMdPath, e);
            return null;
        }
        return sb.toString().trim();
    }

    public static Skill parseSkillInstance(String content) {
        Skill.SkillBuilder builder = Skill.builder();
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }
                String key = parts[0].trim();
                String value = parts[1].trim().replace("\"", "");
                switch (key) {
                    case "name" -> builder.name(value);
                    case "description" -> builder.description(value);
                    case "version" -> builder.version(value);
                    case "author" -> builder.author(value);
                    case "tags" -> builder.tags(Arrays.stream(value.replace("[", "").replace("]", "").split(","))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .toList());
                    case "is_index" -> {
                        try {
                            builder.isIndex(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            log.warn("is_index字段格式错误，使用默认值0: {}", value);
                            builder.isIndex(0);
                        }
                    }
                    case "delete_flag" -> {
                        try {
                            builder.deleteFlag(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            log.warn("delete_flag字段格式错误，使用默认值0: {}", value);
                            builder.deleteFlag(0);
                        }
                    }
                }
            }
        }
        return builder.build();
    }

    /**
     * 尝试获取分布式锁（通用方法）
     * <p>
     * 用于终端命令执行时的并发控制，确保同一时间只有一个实例能操作某个Skill节点。
     * </p>
     *
     * @param lockKey      锁的Key
     * @param waitTimeSec  等待时间（秒）
     * @param leaseTimeSec 锁持有时间（秒）
     * @return boolean true-成功获取锁，false-获取失败
     */
    public static boolean tryAcquireLock(String lockKey, long waitTimeSec, long leaseTimeSec) {
        boolean acquired = RedisUtils.tryAcquireLock(lockKey, waitTimeSec, leaseTimeSec);

        if (acquired) {
            log.info("Skill工具类-分布式锁获取成功，lockKey：{}，waitTime：{}秒，leaseTime：{}秒", lockKey, waitTimeSec, leaseTimeSec);
        } else {
            log.debug("Skill工具类-分布式锁获取失败，lockKey：{}", lockKey);
        }

        return acquired;
    }

    /**
     * 释放分布式锁（通用方法）
     * <p>
     * 在操作完成后释放锁资源，允许其他实例获取锁。
     * 只有锁的持有者才能释放锁，防止误释放。
     * </p>
     *
     * @param lockKey   锁的Key
     */
    public static void releaseLock(String lockKey) {
        RedisUtils.releaseLock(lockKey);
        log.info("Skill工具类-分布式锁释放成功，lockKey：{}", lockKey);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ShadowNode {
        private Skill skill;
        private String relativePath;
        private String parentKey;
        private List<String> childrenKeys;
    }

    /**
     * 将绝对路径转换为相对于Skill根目录的相对路径
     * <p>
     * 用于构建影子表时，将文件系统的绝对路径转换为统一的相对路径格式，
     * 确保影子表中的key和路径字段都使用相对路径，便于跨环境共享。
     * </p>
     *
     * @param absolutePath 文件系统的绝对路径 {@link Path}
     * @return 相对于Skill根目录的相对路径字符串
     */
    public static String toRelativePath(Path absolutePath) {
        try {
            FileUrlProperties fileUrlProperties = getFileUrlProperties();
            String skillsPath = fileUrlProperties.getUploadSkills();
            Path rootPath = Paths.get(skillsPath).toAbsolutePath().normalize();
            Path normalizedPath = absolutePath.toAbsolutePath().normalize();
            return rootPath.relativize(normalizedPath).toString().replace("\\", "/");
        } catch (Exception e) {
            log.warn("转换相对路径失败，使用原始路径: {}", absolutePath);
            return absolutePath.toString();
        }
    }

    /**
     * 将相对路径转换为绝对路径
     * <p>
     * 用于从影子表中的相对路径还原为文件系统的绝对路径，
     * 以便进行文件操作。
     * </p>
     *
     * @param relativePath 相对于Skill根目录的相对路径
     * @return 文件系统的绝对路径 {@link Path}
     */
    public static Path toAbsolutePath(String relativePath) {
        FileUrlProperties fileUrlProperties = getFileUrlProperties();
        String skillsPath = fileUrlProperties.getUploadSkills();
        return Paths.get(skillsPath, relativePath).toAbsolutePath().normalize();
    }
}