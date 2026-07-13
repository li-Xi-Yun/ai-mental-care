package org.lixiyun.common.agent.skill.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.skill.config.TerminalDockerProperties;
import org.lixiyun.common.agent.skill.config.TerminalSecurityProperties;
import org.lixiyun.common.agent.skill.constant.SkillConstant;
import org.lixiyun.common.agent.skill.constant.TerminalCommandConstants;
import org.lixiyun.common.agent.skill.pojo.entity.DockerExecutionResult;
import org.lixiyun.common.agent.skill.pojo.entity.Skill;
import org.lixiyun.common.agent.skill.pojo.entity.SkillToolResponse;
import org.lixiyun.common.agent.skill.utils.SkillUtil;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.lixiyun.common.core.utils.StringUtils;
import org.lixiyun.common.file.utils.FileUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Skill工具类 - AI模型调用的技能加载工具
 * <p>
 * 提供完整的Skill元数据管理功能，包括：
 * <ul>
 *   <li>一级Skill元数据加载</li>
 *   <li>子集Skill元数据提取</li>
 *   <li>Skill具体内容获取</li>
 *   <li>补充资料获取</li>
 * </ul>
 * 所有方法返回统一的 {@link SkillToolResponse} 格式，提供清晰的语义化字段和AI友好的错误恢复建议。
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Slf4j
@RequiredArgsConstructor
public class SkillTool {

    private final TerminalSecurityProperties terminalSecurityProperties;
    private final TerminalDockerProperties terminalDockerProperties;

    /**
     * 一级Skill元数据加载工具
     * <p>
     * 获取缓存中的全局影子表数据（从Redis实时读取），判断是否为空，
     * 若为空则触发全局影子表加载（分布式锁+深度遍历），
     * 然后层次遍历构建一级Skill会话映射表，
     * 更新DB会话映射表、更新Redis会话映射表，
     * 最后返回该一级会话映射表。
     * </p>
     *
     * @param conversationId 会话ID，用于关联会话映射表的存储位置
     * @return 统一格式的响应：包含skills列表、总数等信息
     */
    @Tool(name = "loadFirstLevelSkills", description = "加载所有一级Skill的元数据信息列表，" +
            "每个Skill都有唯一的context_id用于后续操作。" +
            "当需要了解系统中有哪些可用的Skill工具时调用此方法。" +
            "返回包含context_id、名称、描述、版本等信息的Skill列表。")
    public Map<String, Object> loadFirstLevelSkills(
            @ToolParam(description = "当前AI对话的会话ID，用于存储和管理该会话的Skill映射关系") Long conversationId) {

        log.info("开始加载一级Skill元数据，conversationId：{}", conversationId);

        try {
            Map<String, String> sessionMapping = buildSessionMappingTableWithValidation(conversationId);
            List<Map<String, Object>> skillsMetadataList = buildSkillsMetadataList(conversationId, sessionMapping);

            Map<String, Object> data = new HashMap<>();
            data.put("skills", skillsMetadataList);
            data.put("totalCount", skillsMetadataList.size());

            log.info("一级Skill元数据加载完成，conversationId：{}，数量：{}", conversationId, skillsMetadataList.size());
            return SkillToolResponse.success("一级Skill加载完成", data).toMap();

        } catch (Exception e) {
            log.error("加载一级Skill元数据失败，conversationId：{}", conversationId, e);
            return SkillToolResponse.error(
                    "SKILL_LOAD_ERROR",
                    "加载Skill元数据失败：" + e.getMessage(),
                    "请检查Redis连接状态或Skill目录配置",
                    null
            ).toMap();
        }
    }

    /**
     * 子集Skill元数据提取工具
     * <p>
     * 根据传入的context_id获取该Skill的所有子节点（子集Skill），
     * 为每个子集Skill生成新的context_id，并返回子集元数据与对应的context_id。
     * </p>
     *
     * @param contextId      当前Skill的context_id
     * @param conversationId 会话ID
     * @return 统一格式的响应：包含childrenSkills列表、总数、父级context_id等
     */
    @Tool(name = "loadChildrenSkills", description = "加载指定Skill的所有子级Skill元数据。" +
            "当需要查看某个Skill下有哪些子功能时调用此方法。" +
            "传入父级Skill的context_id，返回所有子级Skill的列表及其新的context_id。" +
            "每个子级Skill都有独立的context_id用于后续操作。")
    public Map<String, Object> loadChildrenSkills(
            @ToolParam(description = "父级Skill的context_id，来自loadFirstLevelSkills或前一次loadChildrenSkills的返回结果") String contextId,
            @ToolParam(description = "当前AI对话的会话ID") Long conversationId) {

        log.info("开始加载子集Skill元数据，contextId：{}，conversationId：{}", contextId, conversationId);

        try {
            String shadowKey = getAndValidateShadowKey(conversationId, contextId, "加载子集Skill");
            if (shadowKey == null) {
                return SkillToolResponse.<Map<String, Object>>contextIdInvalid("loadChildrenSkills").toMap();
            }

            List<String> childrenContextIds = SkillUtil.buildChildrenContextIds(conversationId, contextId);

            if (childrenContextIds.isEmpty()) {
                log.info("当前Skill没有子节点，contextId：{}，shadowKey：{}", contextId, shadowKey);
                return SkillToolResponse.skip(
                        "当前Skill没有子节点",
                        "可以尝试使用getSkillContent获取当前Skill的具体内容"
                ).toMap();
            }

            List<Map<String, Object>> childrenMetadataList = buildChildrenMetadataList(conversationId, childrenContextIds);

            Map<String, Object> data = new HashMap<>();
            data.put("childrenSkills", childrenMetadataList);
            data.put("totalCount", childrenMetadataList.size());
            data.put("parentContextId", contextId);

            log.info("子集Skill元数据加载完成，parentContextId：{}，子集数量：{}", contextId, childrenMetadataList.size());
            return SkillToolResponse.success("子集Skill加载完成", data).toMap();

        } catch (Exception e) {
            log.error("加载子集Skill元数据失败，contextId：{}，conversationId：{}", contextId, conversationId, e);
            return SkillToolResponse.error(
                    "CHILDREN_SKILLS_ERROR",
                    "加载子集Skill元数据失败：" + e.getMessage(),
                    "请检查父级Skill的context_id是否正确",
                    null
            ).toMap();
        }
    }

    /**
     * Skill具体内容获取工具
     * <p>
     * 根据传入的context_id读取对应Skill的SKILL.md文件内容，
     * 返回Skill的具体实现内容供AI模型参考。
     * </p>
     *
     * @param contextId      Skill的context_id
     * @param conversationId 会话ID
     * @return 统一格式的响应：包含SKILL.md内容、Skill名称等
     */
    @Tool(name = "getSkillContent", description = "获取指定Skill的具体内容（SKILL.md文件）。" +
            "当需要了解某个Skill的详细实现逻辑时调用此方法。" +
            "传入Skill的context_id，返回该Skill的完整说明文档内容。" +
            "这是执行具体任务前的必要步骤，让模型了解如何使用该Skill。")
    public Map<String, Object> getSkillContent(
            @ToolParam(description = "目标Skill的context_id，来自loadFirstLevelSkills或loadChildrenSkills的返回结果") String contextId,
            @ToolParam(description = "当前AI对话的会话ID") Long conversationId) {

        log.info("开始获取Skill具体内容，contextId：{}，conversationId：{}", contextId, conversationId);

        try {
            String shadowKey = getAndValidateShadowKey(conversationId, contextId, "获取Skill内容");
            if (shadowKey == null) {
                return SkillToolResponse.contextIdInvalid("getSkillContent").toMap();
            }

            FileUrlProperties fileUrlProperties = cn.hutool.extra.spring.SpringUtil.getBean(FileUrlProperties.class);
            String skillsBasePath = fileUrlProperties.getUploadSkills();

            if (!FileUtils.isSecurePath(shadowKey, skillsBasePath)) {
                log.warn("路径安全校验失败，可能存在路径穿透攻击：{}", shadowKey);
                return SkillToolResponse.error(
                        "PATH_SECURITY_ERROR",
                        "Skill路径不安全，可能存在非法访问",
                        "检测到潜在的路径穿透攻击，已拒绝访问",
                        null
                ).toMap();
            }

            Path skillPath = Paths.get(shadowKey).normalize().toAbsolutePath();
            if (!Files.exists(skillPath)) {
                log.warn("Skill路径不存在：{}", shadowKey);
                return SkillToolResponse.error(
                        "SKILL_PATH_NOT_FOUND",
                        "Skill路径不存在：" + shadowKey,
                        "请检查Skill文件是否存在或已被移动",
                        null
                ).toMap();
            }

            Path skillMdPath = skillPath.resolve(SkillConstant.SKILL_NAME);
            if (!Files.exists(skillMdPath) || !Files.isRegularFile(skillMdPath)) {
                log.warn("SKILL.md文件不存在：{}", skillMdPath);
                return SkillToolResponse.error(
                        "SKILL_MD_NOT_FOUND",
                        "SKILL.md文件不存在：" + skillMdPath,
                        "该Skill可能未正确配置，缺少必要的说明文档",
                        null
                ).toMap();
            }

            String content = FileUtil.readUtf8String(skillMdPath.toFile());
            if (StringUtils.isBlank(content)) {
                log.warn("SKILL.md文件内容为空：{}", skillMdPath);
                return SkillToolResponse.error(
                        "SKILL_MD_EMPTY",
                        "SKILL.md文件内容为空",
                        "该Skill的文档内容为空，无法提供有效信息",
                        null
                ).toMap();
            }

            Skill skillMetadata = SkillUtil.getSkillMetadata(conversationId, contextId);
            String skillName = skillMetadata != null && skillMetadata.getName() != null ? skillMetadata.getName() : "未命名";

            Map<String, Object> data = new HashMap<>();
            data.put("content", content);
            data.put("skillName", skillName);
            data.put("contextId", contextId);

            log.info("Skill具体内容获取成功，contextId：{}，skillName：{}，内容长度：{}", contextId, skillName, content.length());
            return SkillToolResponse.success("Skill内容获取成功", data).toMap();

        } catch (Exception e) {
            log.error("获取Skill具体内容失败，contextId：{}，conversationId：{}", contextId, conversationId, e);
            return SkillToolResponse.error(
                    "SKILL_CONTENT_ERROR",
                    "读取Skill内容失败：" + e.getMessage(),
                    "请稍后重试或联系管理员",
                    null
            ).toMap();
        }
    }

    /**
     * 补充资料获取工具
     * <p>
     * 根据传入的context_id和资料文件名，读取Skill目录下的补充资料文件，
     * 返回文件内容供AI模型参考。
     * </p>
     *
     * @param contextId      Skill的context_id
     * @param fileName       资料文件名
     * @param conversationId 会话ID
     * @return 统一格式的响应：包含补充资料内容、文件名、路径等
     */
    @Tool(name = "getSupplementaryMaterial", description = "获取指定Skill目录下的补充资料文件内容。" +
            "当Skill需要额外的参考文档、示例代码、配置文件等时调用此方法。" +
            "传入Skill的context_id和文件名，返回该文件的内容。" +
            "如果文件不存在会给出明确提示，让模型决定是否跳过或尝试其他操作。")
    public Map<String, Object> getSupplementaryMaterial(
            @ToolParam(description = "目标Skill的context_id，来自loadFirstLevelSkills或loadChildrenSkills的返回结果") String contextId,
            @ToolParam(description = "要获取的资料文件名，如：example.txt、config.json、README.md等") String fileName,
            @ToolParam(description = "当前AI对话的会话ID") Long conversationId) {

        log.info("开始获取补充资料，contextId：{}，fileName：{}，conversationId：{}", contextId, fileName, conversationId);

        try {
            if (StrUtil.isBlank(fileName)) {
                log.warn("文件名为空，无法获取补充资料");
                return SkillToolResponse.error(
                        "FILE_NAME_EMPTY",
                        "文件名不能为空",
                        "请提供有效的文件名参数",
                        null
                ).toMap();
            }

            String shadowKey = getAndValidateShadowKey(conversationId, contextId, "获取补充资料");
            if (shadowKey == null) {
                return SkillToolResponse.contextIdInvalid("getSupplementaryMaterial").toMap();
            }

            Path secureMaterialPath = FileUtils.resolveSecurePath(shadowKey, fileName);
            if (secureMaterialPath == null) {
                log.warn("补充资料路径安全校验失败，可能存在路径穿透攻击：skillPath={}, fileName={}", shadowKey, fileName);
                return SkillToolResponse.error(
                        "补充资料路径不安全，已跳过",
                        "检测到可能的路径穿透攻击尝试，建议检查文件名参数是否合法"
                ).toMap();
            }

            if (!Files.exists(secureMaterialPath) || !Files.isRegularFile(secureMaterialPath)) {
                log.warn("补充资料文件不存在：{}", secureMaterialPath);
                return SkillToolResponse.error(
                        "补充资料文件不存在：" + secureMaterialPath,
                        "可以尝试其他文件名或跳过该资料继续执行任务"
                ).toMap();
            }

            String content = FileUtil.readUtf8String(secureMaterialPath.toFile());
            if (StrUtil.isBlank(content)) {
                log.warn("补充资料文件内容为空：{}", secureMaterialPath);
                return SkillToolResponse.error(
                        "补充资料文件内容为空：" + secureMaterialPath,
                        "该文件无有效内容，可以跳过继续执行任务"
                ).toMap();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("content", content);
            data.put("fileName", fileName);
            data.put("filePath", secureMaterialPath.toString());
            data.put("contextId", contextId);

            log.info("补充资料获取成功，contextId：{}，fileName：{}，内容长度：{}", contextId, fileName, content.length());
            return SkillToolResponse.success("补充资料获取成功", data).toMap();

        } catch (Exception e) {
            log.error("获取补充资料失败，contextId：{}，fileName：{}，conversationId：{}", contextId, fileName, conversationId, e);
            return SkillToolResponse.error(
                    "MATERIAL_ERROR",
                    "读取补充资料失败：" + e.getMessage(),
                    "请检查文件权限或稍后重试",
                    null
            ).toMap();
        }
    }

    /**
     * 终端执行工具（完整实现流程图）
     * <p>
     * 在指定的Skill目录环境下通过Docker容器安全地执行终端命令。
     * 完整实现流程图要求的所有步骤：
     * </p>
     *
     * <h3>完整流程：</h3>
     * <ol>
     *   <li><b>参数传入</b>：context_id, conversation_id, terminalCommand</li>
     *   <li><b>黑名单命令列表过滤</b>：静态规则预检</li>
     *   <li><b>获取映射表value</b>：查询会话映射表定位Skill路径</li>
     *   <li><b>模型调用动态安全检查</b>：AI模型语义级安全判断</li>
     *   <li><b>争夺Skill节点分布式锁</b>：并发控制（失败则轮询重试）</li>
     *   <li><b>启动临时Docker容器</b>：完全隔离的执行环境</li>
     *   <li><b>切换到Skill文件夹目录</b>：设置工作目录</li>
     *   <li><b>执行终端命令</b>：带资源限制的超时控制</li>
     *   <li><b>判断执行成功</b>：检查退出码并返回结果</li>
     * </ol>
     *
     * @param contextId       Skill的context_id
     * @param terminalCommand 要执行的终端命令
     * @param conversationId  会话ID
     * @return 统一格式的响应
     */
    @Tool(name = "executeTerminalCommand", description = "在指定Skill的Docker容器环境中安全地执行终端命令。" +
            "当需要运行脚本、构建项目、执行测试等操作时调用此方法。" +
            "传入Skill的context_id和要执行的命令，系统会在Docker容器中执行并返回结果。" +
            "支持Python、Node.js、Java等多种运行环境的命令执行。" +
            "所有命令都经过黑名单过滤、模型动态安全检查、分布式锁保护后在Docker容器中运行。")
    public Map<String, Object> executeTerminalCommand(
            @ToolParam(description = "目标Skill的context_id，来自loadFirstLevelSkills或loadChildrenSkills的返回结果") String contextId,
            @ToolParam(description = "要执行的终端命令，如：python main.py、npm run test、java -jar app.jar等") String terminalCommand,
            @ToolParam(description = "当前AI对话的会话ID") Long conversationId) {

        log.info("【Step 1】开始执行终端命令，contextId：{}，command：{}，conversationId：{}", contextId, terminalCommand, conversationId);

        try {

            if (StrUtil.isBlank(terminalCommand)) {
                log.warn("【Step 2】终端命令为空，拒绝执行");
                return SkillToolResponse.error(
                        "COMMAND_EMPTY",
                        "终端命令不能为空",
                        "请提供要执行的具体命令",
                        null
                ).toMap();
            }

            log.debug("【Step 2-1】开始黑名单命令列表过滤...");
            if (!validateBlacklistFilter(terminalCommand)) {
                log.warn("【Step 2-2】黑名单过滤未通过，command：{}，已跳过执行", terminalCommand);
                return SkillToolResponse.skip(
                        "黑名单命令过滤未通过，已跳过执行",
                        "检测到命令包含危险模式或不允许的操作，建议修改命令内容"
                ).toMap();
            }
            log.debug("【Step 2-3】黑名单过滤通过");

            log.debug("【Step 3-1】获取映射表value...");
            String shadowKey = getAndValidateShadowKey(conversationId, contextId, "执行终端命令");
            if (shadowKey == null) {
                log.warn("【Step 3-2】获取映射表value失败，contextId无效");
                return SkillToolResponse.contextIdInvalid("executeTerminalCommand").toMap();
            }
            log.debug("【Step 3-3】成功获取映射表value，shadowKey：{}", shadowKey);

            FileUrlProperties fileUrlProperties = SpringUtil.getBean(FileUrlProperties.class);
            String skillsBasePath = fileUrlProperties.getUploadSkills();

            if (!FileUtils.isSecurePath(shadowKey, skillsBasePath)) {
                log.warn("【Step 3-4】路径安全校验失败，可能存在路径穿透攻击：{}", shadowKey);
                return SkillToolResponse.skip(
                        "Skill路径不安全，已跳过命令执行",
                        "检测到潜在的路径穿透攻击尝试，已拒绝执行该命令"
                ).toMap();
            }

            Path skillPath = Paths.get(shadowKey).normalize().toAbsolutePath();
            if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
                log.warn("【Step 3-5】Skill目录不存在或不是有效目录：{}", skillPath);
                return SkillToolResponse.error(
                        "SKILL_DIRECTORY_NOT_FOUND",
                        "Skill目录不存在或无效：" + skillPath,
                        "请检查Skill路径是否正确",
                        null
                ).toMap();
            }

            log.debug("【Step 4-1】开始模型动态安全检查...");
            boolean isModelSafe = validateCommandWithAIModel(terminalCommand, skillPath);
            if (!isModelSafe) {
                log.warn("【Step 4-2】模型动态安全检查未通过，打印警告让模型跳过该Skill命令执行，command：{}", terminalCommand);
                return SkillToolResponse.skip(
                        "模型安全检查未通过，已跳过该Skill命令执行",
                        "AI模型判断该命令可能存在安全风险，建议修改命令内容或使用其他方式完成任务。如果确认命令安全，可以尝试简化命令或分步执行。"
                ).toMap();
            }
            log.debug("【Step 4-3】模型动态安全检查通过");

            log.debug("【Step 5-1】争夺Skill节点分布式锁...");
            boolean lockAcquired = acquireDistributedLockWithRetry(shadowKey);
            if (!lockAcquired) {
                log.warn("【Step 5-2】分布式锁获取失败，限时轮询等待重试均失败");
                return SkillToolResponse.skip(
                        "分布式锁获取失败，已跳过命令执行",
                        "当前有其他实例正在操作该Skill节点，请稍后重试或等待其他操作完成后再试"
                ).toMap();
            }
            log.debug("【Step 5-3】分布式锁获取成功");

            try {
                log.debug("【Step 6-1】启动临时Docker容器...");
                Map<String, Object> dockerExecutionResult = executeCommandInDockerContainer(
                        skillPath,
                        terminalCommand,
                        contextId
                );

                boolean dockerSuccess = (boolean) dockerExecutionResult.getOrDefault("success", false);
                if (!dockerSuccess) {
                    String errorMessage = (String) dockerExecutionResult.getOrDefault("errorMessage", "未知错误");
                    log.warn("【Step 8-9】Docker容器执行失败，error：{}，打印警告让模型跳过该Skill命令执行", errorMessage);
                    return SkillToolResponse.skip(
                            "Docker容器执行失败：" + errorMessage,
                            "命令在Docker容器中执行失败，可以尝试修改命令参数或检查Skill配置后重新执行"
                    ).toMap();
                }

                int exitCode = (int) dockerExecutionResult.get("exitCode");
                if (exitCode != 0) {
                    log.warn("【Step 9-1】命令执行不成功，exitCode：{}，打印警告让模型跳过该Skill命令执行", exitCode);
                    return SkillToolResponse.skip(
                            String.format("命令执行失败，退出码：%d", exitCode),
                            "命令执行返回非零退出码，表示执行过程中出现错误。可以查看stderr输出了解具体原因，然后修改命令或修复问题后重试。"
                    ).toMap();
                }

                log.debug("【Step 10】命令执行成功，返回执行结果");
                Map<String, Object> data = new HashMap<>();
                data.put("command", terminalCommand);
                data.put("exitCode", dockerExecutionResult.get("exitCode"));
                data.put("stdout", dockerExecutionResult.get("stdout"));
                data.put("stderr", dockerExecutionResult.get("stderr"));
                data.put("executionTime", dockerExecutionResult.get("executionTime"));
                data.put("skillName", dockerExecutionResult.get("skillName"));
                data.put("containerName", dockerExecutionResult.get("containerName"));
                data.put("contextId", contextId);
                data.put("executionEnvironment", "DOCKER_CONTAINER");

                log.info("终端命令执行成功，contextId：{}，command：{}，exitCode：{}，耗时：{}ms，容器：{}",
                        contextId, terminalCommand, exitCode, dockerExecutionResult.get("executionTime"),
                        dockerExecutionResult.get("containerName"));

                return SkillToolResponse.success("终端命令执行成功", data).toMap();

            } finally {
                releaseDistributedLock(shadowKey);
                log.debug("【Step 11】释放分布式锁完成");
            }

        } catch (Exception e) {
            log.error("执行终端命令异常，contextId：{}，command：{}，conversationId：{}", contextId, terminalCommand, conversationId, e);
            return SkillToolResponse.error(
                    "TERMINAL_EXECUTION_ERROR",
                    "执行终端命令时发生异常：" + e.getMessage(),
                    "请检查命令语法或联系管理员",
                    null
            ).toMap();
        }
    }

    private Map<String, String> buildSessionMappingTableWithValidation(Long conversationId) {
        try {
            return SkillUtil.buildSessionMappingTable(conversationId);
        } catch (Exception e) {
            log.error("构建会话映射表异常", e);
            throw new RuntimeException("构建会话映射表失败：" + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildSkillsMetadataList(Long conversationId, Map<String, String> sessionMapping) {
        List<Map<String, Object>> skillsList = new ArrayList<>();

        for (Map.Entry<String, String> entry : sessionMapping.entrySet()) {
            String contextId = entry.getKey();
            String shadowKey = entry.getValue();

            Map<String, Object> skillInfo = buildSingleSkillInfo(conversationId, contextId, shadowKey);
            if (skillInfo != null) {
                skillsList.add(skillInfo);
            }
        }

        return skillsList;
    }

    private Map<String, Object> buildSingleSkillInfo(Long conversationId, String contextId, String shadowKey) {
        String validatedShadowKey = SkillUtil.getMappingValue(conversationId, contextId);
        if (validatedShadowKey == null) {
            log.warn("校验未通过，跳过该Skill，contextId：{}", contextId);
            return null;
        }

        Skill skill = SkillUtil.getSkillMetadata(conversationId, contextId);
        if (skill == null) {
            log.warn("获取Skill元数据失败，contextId：{}", contextId);
            return null;
        }

        return createSkillInfo(contextId, skill);
    }

    private Map<String, Object> createSkillInfo(String contextId, Skill skill) {
        Map<String, Object> info = new HashMap<>();
        info.put("contextId", contextId);
        info.put("name", skill.getName() != null ? skill.getName() : "未命名");
        info.put("description", skill.getDescription() != null ? skill.getDescription() : "");
        info.put("version", skill.getVersion() != null ? skill.getVersion() : "1.0.0");
        info.put("author", skill.getAuthor() != null ? skill.getAuthor() : "system");
        info.put("tags", skill.getTags() != null ? skill.getTags() : List.of());
        return info;
    }

    private List<Map<String, Object>> buildChildrenMetadataList(Long conversationId, List<String> childrenContextIds) {
        List<Map<String, Object>> childrenList = new ArrayList<>();

        for (String childContextId : childrenContextIds) {
            String childShadowKey = SkillUtil.getMappingValue(conversationId, childContextId);
            if (childShadowKey == null) {
                log.warn("子集Skill校验未通过，跳过，childContextId：{}", childContextId);
                continue;
            }

            boolean isDeleted = !SkillUtil.checkAncestorDeleteFlag(childShadowKey);
            if (isDeleted) {
                log.warn("子集Skill或其祖先被标记删除，跳过，childContextId：{}", childContextId);
                continue;
            }

            Skill childSkill = SkillUtil.getSkillMetadata(conversationId, childContextId);
            if (childSkill == null) {
                log.warn("子集Skill元数据获取失败，跳过，childContextId：{}", childContextId);
                continue;
            }

            Map<String, Object> childInfo = createSkillInfo(childContextId, childSkill);
            childrenList.add(childInfo);
        }

        return childrenList;
    }

    private String getAndValidateShadowKey(Long conversationId, String contextId, String operationName) {
        String shadowKey = SkillUtil.getMappingValue(conversationId, contextId);
        if (shadowKey == null) {
            log.warn("{}时校验未通过，contextId无效或祖先被删除，operation：{}", operationName, contextId);
        }
        return shadowKey;
    }

    /**
     * 黑名单命令列表过滤（增强版）
     * <p>
     * 实现流程图Step 2：黑名单命令列表过滤。
     * 相比原来的validateTerminalCommandSecurity方法，增强了以下安全检测：
     * </p>
     *
     * <h3>增强的安全检查：</h3>
     * <ul>
     *   <li><b>命令长度限制</b>：超过MAX_COMMAND_LENGTH拒绝</li>
     *   <li><b>危险模式匹配</b>：使用正则表达式精确匹配，防止空格绕过</li>
     *   <li><b>命令注入防护</b>：检测$()和反引号模式本身，而非字面量</li>
     *   <li><b>管道符数量限制</b>：防止复杂命令注入</li>
     *   <li><b>白名单前缀校验</b>：仅允许已知安全的命令前缀</li>
     *   <li><b>安全字符验证</b>：即使通过白名单也要进行格式校验</li>
     * </ul>
     *
     * @param command 要过滤的终端命令
     * @return boolean true-命令安全，false-包含危险模式
     */
    private boolean validateBlacklistFilter(String command) {
        if (StrUtil.isBlank(command)) {
            return false;
        }

        String normalizedCommand = command.trim();

        if (normalizedCommand.length() > TerminalCommandConstants.MAX_COMMAND_LENGTH) {
            log.warn("命令长度超过安全限制（{}字符），实际长度：{}",
                    TerminalCommandConstants.MAX_COMMAND_LENGTH, normalizedCommand.length());
            return false;
        }

        for (String pattern : TerminalCommandConstants.DANGEROUS_COMMAND_PATTERNS) {

            String normalizedPattern = pattern.trim().toLowerCase();
            String normalizedCmdForCheck = normalizedCommand.toLowerCase()
                    .replaceAll("\\s+", " ")       // ① 把多个空白压缩成单个空格
                    .replaceAll("\\$\\{[^}]*}", "") // ② 去掉 ${变量名} 形式的变量替换
                    .replaceAll("\\$\\([^)]*\\)", ""); // ③ 去掉 $(命令) 形式的命令替换

            if (normalizedCmdForCheck.contains(normalizedPattern) ||
                    normalizedCommand.matches("(?i).*" + java.util.regex.Pattern.quote(pattern) + ".*")) {
                log.warn("检测到危险命令模式：{}，命令内容：{}", pattern, command);
                return false;
            }
        }

        if (normalizedCommand.contains("$(") || normalizedCommand.contains("`")) {
            log.warn("检测到命令注入模式（$()或反引号），command：{}", command);
            return false;
        }

        if (normalizedCommand.contains("&&") || normalizedCommand.contains(";") || normalizedCommand.contains("|")) {
            String[] parts = normalizedCommand.split("[;&|]");
            if (parts.length > TerminalCommandConstants.MAX_PIPE_CONNECTOR_COUNT) {
                log.warn("命令包含过多的管道或连接符（{}个），可能存在命令注入风险",
                        parts.length);
                return false;
            }
        }

        boolean hasAllowedPrefix = TerminalCommandConstants.ALLOWED_COMMAND_PREFIXES.stream()
                .anyMatch(normalizedCommand::startsWith);

        if (!hasAllowedPrefix && !normalizedCommand.matches(TerminalCommandConstants.SAFE_COMMAND_PATTERN)) {
            log.warn("命令不符合允许的格式规范，command：{}", command);
            return false;
        }

        if (hasAllowedPrefix) {
            // 防止 python test.py; rm -rf / 这种命令执行
            String afterPrefix = normalizedCommand.substring(
                    TerminalCommandConstants.ALLOWED_COMMAND_PREFIXES.stream()
                            .filter(normalizedCommand::startsWith)
                            .findFirst()
                            .orElse("")
                            .length()
            ).trim();

            if (afterPrefix.contains(";") || afterPrefix.contains("|") || afterPrefix.contains("&&")) {
                log.warn("白名单命令后接危险连接符，command：{}", command);
                return false;
            }
        }

        log.debug("黑名单过滤通过，command：{}", command);
        return true;
    }

    /**
     * 使用AI模型进行动态安全检查
     * <p>
     * 实现流程图Step 4：模型调用，检查命令是否安全。
     * 调用AI模型对命令进行语义级别的安全判断，能够识别：
     * </p>
     *
     * <h3>AI模型可识别的攻击类型：</h3>
     * <ul>
     *   <li><b>变形注入</b>：空格绕过、编码绕过、变量拼接等</li>
     *   <li><b>逻辑漏洞</b>：看似无害但实际危险的组合命令</li>
     *   <li><b>上下文相关风险</b>：结合Skill目录内容的潜在威胁</li>
     *   <li><b>社会工程学</b>：诱导执行恶意操作的命令</li>
     * </ul>
     *
     * @param command   待检查的终端命令
     * @param skillPath Skill目录路径（用于上下文分析）
     * @return boolean true-模型判定安全，false-存在安全风险
     */
    private boolean validateCommandWithAIModel(String command, Path skillPath) {
        try {

            if (!terminalSecurityProperties.isEnabled()) {
                log.debug("AI模型安全检查已禁用，跳过检查");
                return true;
            }

            ChatModel chatModel = SpringUtil.getBean(ChatModel.class);

            ChatClient chatClient = ChatClient.create(chatModel);

            String systemPrompt = terminalSecurityProperties.getSystemPrompt();
            String userMessageTemplate = terminalSecurityProperties.getUserMessageTemplate();

            String userMessage = String.format(
                    userMessageTemplate,
                    command,
                    skillPath.toAbsolutePath()
            );

            String aiResponse = chatClient.prompt()
                    .system(systemPrompt.formatted(skillPath.toAbsolutePath()))
                    .user(userMessage)
                    .call()
                    .content();

            if (aiResponse == null) {
                log.warn("AI模型安全检查返回空结果，默认行为：{}",
                        terminalSecurityProperties.isDefaultAllowOnFailure() ? "允许执行" : "拒绝执行");
                return terminalSecurityProperties.isDefaultAllowOnFailure();
            }

            String trimmedResponse = aiResponse.trim().toUpperCase();
            String unsafePrefix = terminalSecurityProperties.getUnsafeResponsePrefix().toUpperCase();
            String safeResponse = terminalSecurityProperties.getSafeResponse().toUpperCase();

            if (trimmedResponse.startsWith(unsafePrefix)) {
                String reason = trimmedResponse.substring(unsafePrefix.length()).trim();
                log.warn("AI模型判定命令不安全，原因：{}，command：{}", reason, command);
                return false;
            }

            if (trimmedResponse.equals(safeResponse)) {
                log.info("AI模型判定命令安全，command：{}", command);
                return true;
            }

            log.warn("AI模型返回无法识别的结果：{}，默认行为：{}",
                    aiResponse,
                    terminalSecurityProperties.isDefaultAllowOnFailure() ? "允许执行" : "拒绝执行");
            return terminalSecurityProperties.isDefaultAllowOnFailure();

        } catch (Exception e) {
            log.error("AI模型安全检查异常，默认行为：{}，error：{}",
                    terminalSecurityProperties.isDefaultAllowOnFailure() ? "允许执行" : "拒绝执行",
                    e.getMessage(), e);
            return terminalSecurityProperties.isDefaultAllowOnFailure();
        }
    }

    /**
     * 获取分布式锁并支持轮询重试
     * <p>
     * 实现流程图Step 5：争夺Skill节点分布式锁（失败则限时轮询等待+重试）。
     * 在多实例并发场景下，确保同一时间只有一个实例能操作某个Skill节点。
     * </p>
     *
     * <h3>锁机制：</h3>
     * <ul>
     *   <li><b>锁Key</b>：skill:terminal:lock:{shadowKey}</li>
     *   <li><b>最大等待时间</b>：30秒</li>
     *   <li><b>持有时间</b>：5分钟</li>
     *   <li><b>重试间隔</b>：1秒</li>
     *   <li><b>最大重试次数</b>：30次</li>
     * </ul>
     *
     * @param shadowKey      Skill路径（用于锁标识）
     * @return boolean true-成功获取锁，false-获取失败
     */
    private boolean acquireDistributedLockWithRetry(String shadowKey) {
        String normalizedShadowKey = shadowKey.replace("/", ":").replace("\\", ":");
        String lockKey = terminalDockerProperties.getLockKeyPrefix() + normalizedShadowKey;

        int maxRetries = terminalDockerProperties.getLockMaxRetries();
        long retryIntervalMs = terminalDockerProperties.getLockRetryIntervalMs();
        long leaseTimeSeconds = terminalDockerProperties.getLockLeaseTimeSeconds();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("尝试获取分布式锁，第{}次，lockKey：{}，最大重试：{}次", attempt, lockKey, maxRetries);

                boolean acquired = SkillUtil.tryAcquireLock(lockKey, 0, leaseTimeSeconds);

                if (acquired) {
                    log.info("分布式锁获取成功，lockKey：{}，attempt：{}/{}", lockKey, attempt, maxRetries);
                    return true;
                }

                log.debug("分布式锁获取失败，等待重试，attempt：{}/{}，间隔：{}ms", attempt, maxRetries, retryIntervalMs);

                if (attempt < maxRetries) {
                    Thread.sleep(retryIntervalMs);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("分布式锁重试被中断，attempt：{}/{}", attempt, maxRetries);
                return false;
            } catch (Exception e) {
                log.error("分布式锁获取异常，attempt：{}/{}，error：{}", attempt, maxRetries, e.getMessage(), e);
            }
        }

        log.warn("分布式锁获取失败，已达到最大重试次数：{}/{}，lockKey：{}", maxRetries, maxRetries, lockKey);
        return false;
    }

    /**
     * 释放分布式锁
     * <p>
     * 在命令执行完成后释放锁资源，允许其他实例获取锁。
     * </p>
     *
     * @param shadowKey      Skill路径
     */
    private void releaseDistributedLock(String shadowKey) {
        try {
            String normalizedShadowKey = shadowKey.replace("/", ":").replace("\\", ":");
            String lockKey = terminalDockerProperties.getLockKeyPrefix() + normalizedShadowKey;

            SkillUtil.releaseLock(lockKey);
            log.info("分布式锁释放成功，lockKey：{}", lockKey);

        } catch (Exception e) {
            log.error("释放分布式锁异常，error：{}", e.getMessage(), e);
        }
    }

    /**
     * 在Docker容器中执行命令（完整实现）
     * <p>
     * 实现流程图Step 6-9：启动临时Docker容器 → 切换到Skill文件夹 → 执行命令 → 资源限制检查
     * 所有配置项均从 {@link TerminalDockerProperties} 读取，支持动态调整。
     * </p>
     *
     * @param skillPath       Skill目录路径
     * @param command         要执行的命令
     * @param contextId       context_id
     * @return 统一格式的执行结果Map（内部使用实体类封装）
     */
    private Map<String, Object> executeCommandInDockerContainer(
            Path skillPath,
            String command,
            String contextId) {

        long startTime = System.currentTimeMillis();
        String baseImage = terminalDockerProperties.getBaseImage();
        String containerName = terminalDockerProperties.getContainerNamePrefix() + contextId + "-" + System.currentTimeMillis();
        String workspaceDir = terminalDockerProperties.getWorkspaceDir();

        try {
            String dockerRunCommand = buildDockerRunCommand(containerName, skillPath, baseImage, workspaceDir, command);

            log.info("【Step 6】启动临时Docker容器，containerName：{}，image：{}，command：{}",
                    containerName, baseImage, command);
            log.info("【Step 7】切换到Skill文件夹目录：{}（映射自 {}）", workspaceDir, skillPath.toAbsolutePath());

            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", dockerRunCommand);
            processBuilder.redirectErrorStream(false);

            Process dockerProcess = processBuilder.start();

            CommandOutput output = captureProcessOutput(dockerProcess);

            log.info("【Step 8】执行终端命令，开始监控资源限制...");

            long timeoutSeconds = terminalDockerProperties.getTimeoutSeconds();
            boolean completed = dockerProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                log.warn("【Step 8-资源限制检查】命令执行超时（{}秒），正在强制终止容器...", timeoutSeconds);
                forceCleanupContainer(containerName);

                String timeoutErrorMessage = buildTimeoutErrorMessage(timeoutSeconds);
                DockerExecutionResult timeoutResult = buildDockerExecutionResult(
                        false, -1,
                        output.stdout.toString(),
                        output.stderr + timeoutErrorMessage,
                        "命令执行超时/资源限制触发",
                        startTime,
                        skillPath,
                        containerName
                );
                return timeoutResult.toMap();
            }

            int exitCode = dockerProcess.exitValue();

            log.info("【Step 9】判断是否执行成功，exitCode：{}", exitCode);

            String errorMessage = (exitCode != 0)
                    ? String.format("命令执行失败，退出码：%d", exitCode)
                    : null;

            DockerExecutionResult executionResult = buildDockerExecutionResult(
                    exitCode == 0,
                    exitCode,
                    output.stdout.toString(),
                    output.stderr.toString(),
                    errorMessage,
                    startTime,
                    skillPath,
                    containerName
            );

            log.info("Docker容器执行完成，containerName：{}，exitCode：{}，耗时：{}ms",
                    containerName, exitCode, executionResult.getExecutionTime());

            return executionResult.toMap();

        } catch (IOException e) {
            log.error("启动Docker容器进程失败，error：{}", e.getMessage(), e);
            DockerExecutionResult ioExceptionResult = buildDockerExecutionResult(
                    false, -1,
                    "", "",
                    "启动Docker容器进程失败：" + e.getMessage() + buildIoExceptionHints(),
                    startTime,
                    skillPath,
                    containerName
            );
            return ioExceptionResult.toMap();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Docker容器执行被中断，error：{}", e.getMessage(), e);
            DockerExecutionResult interruptedExceptionResult = buildDockerExecutionResult(
                    false, -1,
                    "", "",
                    "Docker容器执行被中断：" + e.getMessage(),
                    startTime,
                    skillPath,
                    containerName
            );
            return interruptedExceptionResult.toMap();
        }
    }

    /**
     * 构建Docker运行命令
     * <p>
     * 根据配置属性生成完整的docker run命令字符串。
     * </p>
     */
    private String buildDockerRunCommand(String containerName, Path skillPath,
                                         String baseImage, String workspaceDir, String command) {
        return String.format(
                "docker run --rm --name %s " +
                "--memory=%s --cpus=%.1f " +
                "--memory-swap=%s " +
                "--network %s %s " +
                "-v %s:%s:rw " +
                "-w %s " +
                "%s %s",
                containerName,
                terminalDockerProperties.getMemoryLimit(),
                terminalDockerProperties.getCpuLimit(),
                terminalDockerProperties.getMemorySwap(),
                terminalDockerProperties.getNetworkMode(),
                terminalDockerProperties.isReadOnlyFs() ? "--read-only" : "",
                skillPath.toAbsolutePath(),
                workspaceDir,
                workspaceDir,
                baseImage,
                command
        ).replaceAll("  ", " ").trim();
    }

    /**
     * 捕获进程输出（标准输出和错误输出）
     */
    private CommandOutput captureProcessOutput(Process process) throws IOException {
        CommandOutput output = new CommandOutput();

        try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = stdoutReader.readLine()) != null) {
                output.stdout.append(line).append("\n");
            }

            while ((line = stderrReader.readLine()) != null) {
                output.stderr.append(line).append("\n");
            }
        }

        return output;
    }

    /**
     * 强制清理Docker容器（停止并删除）
     */
    private void forceCleanupContainer(String containerName) {
        long cleanupTimeout = terminalDockerProperties.getCleanupTimeoutSeconds();
        try {
            Process stopProcess = new ProcessBuilder("docker", "stop", containerName).start();
            stopProcess.waitFor(cleanupTimeout, TimeUnit.SECONDS);

            Process rmProcess = new ProcessBuilder("docker", "rm", "-f", containerName).start();
            rmProcess.waitFor(cleanupTimeout, TimeUnit.SECONDS);
        } catch (Exception cleanupEx) {
            log.warn("清理Docker容器失败，containerName：{}，error：{}", containerName, cleanupEx.getMessage());
        }
    }

    /**
     * 构建超时错误信息
     */
    private String buildTimeoutErrorMessage(long timeoutSeconds) {
        return String.format(
                "\n[ERROR] 命令执行超时（%d秒限制），已被强制终止。可能原因：\n" +
                "1. 命令运行时间过长\n" +
                "2. CPU/内存资源不足\n" +
                "3. 磁盘IO受限\n" +
                "4. 进程陷入死循环或等待",
                timeoutSeconds
        );
    }

    /**
     * 构建IO异常提示信息
     */
    private String buildIoExceptionHints() {
        return "\n可能原因：\n1. Docker服务未启动\n2. Docker镜像不存在\n3. 权限不足";
    }

    /**
     * 构建统一的Docker执行结果（核心封装方法）
     * <p>
     * 使用实体类 {@link DockerExecutionResult} 封装执行结果，
     * 提供类型安全和IDE支持。
     * 所有Docker执行场景（成功、失败、超时、异常）都使用此方法构建返回值。
     * </p>
     *
     * @param success          是否成功
     * @param exitCode         退出码
     * @param stdout           标准输出
     * @param stderr           错误输出
     * @param errorMessage     错误消息（可为null）
     * @param startTime        开始时间戳
     * @param skillPath        Skill路径
     * @param containerName    容器名称
     * @return 统一格式的执行结果实体类
     */
    private DockerExecutionResult buildDockerExecutionResult(
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            String errorMessage,
            long startTime,
            Path skillPath,
            String containerName) {

        if (success) {
            return DockerExecutionResult.success(
                    exitCode,
                    stdout != null ? stdout : "",
                    stderr != null ? stderr : "",
                    System.currentTimeMillis() - startTime,
                    skillPath.getFileName().toString(),
                    containerName
            );
        } else {
            return DockerExecutionResult.failure(
                    exitCode,
                    errorMessage,
                    stdout != null ? stdout : "",
                    stderr != null ? stderr : "",
                    System.currentTimeMillis() - startTime,
                    skillPath.getFileName().toString(),
                    containerName
            );
        }
    }

    /**
     * 命令输出封装类
     * <p>
     * 用于封装进程的标准输出和错误输出。
     * </p>
     */
    private static class CommandOutput {
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
    }
}