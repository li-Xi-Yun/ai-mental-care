package org.lixiyun.common.agent.skill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 终端命令安全审计配置属性
 * <p>
 * 用于配置AI模型安全检查的相关参数，
 * 包括系统提示词模板、审计标准、响应格式等。
 * 所有配置项均可通过application.yml动态调整。
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>{@code
 * skill:
 *   terminal:
 *     security:
 *       system-prompt: |
 *         你是一个终端命令安全审计专家...
 *       audit-criteria:
 *         - 命令是否会修改系统关键文件或目录
 *         - 命令是否会泄露敏感信息
 *       safe-response: SAFE
 *       unsafe-response-prefix: UNSAFE:
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Data
@Component
@ConfigurationProperties(prefix = "skill.terminal.security")
public class TerminalSecurityProperties {

    /**
     * AI安全审计的系统提示词模板
     * <p>
     * 支持占位符：%s 表示Skill目录路径
     * </p>
     *
     * <p>示例：</p>
     * <pre>
     * 你是一个终端命令安全审计专家。你的任务是分析用户提交的终端命令是否存在安全风险。
     *
     * 审计标准：
     * 1. 命令是否会修改系统关键文件或目录
     * 2. 命令是否会泄露敏感信息
     * ...
     *
     * 当前Skill目录：%s
     *
     * 请严格按以下格式回复：
     * - 如果命令安全，回复：SAFE
     * - 如果命令不安全，回复：UNSAFE: [具体原因]
     * </pre>
     */
    private String systemPrompt = """
            你是一个终端命令安全审计专家。你的任务是分析用户提交的终端命令是否存在安全风险。
            
            审计标准：
            1. 命令是否会修改系统关键文件或目录
            2. 命令是否会泄露敏感信息
            3. 命令是否会消耗过多系统资源
            4. 命令是否有隐藏的恶意意图
            5. 命令是否符合正常的开发/构建/运行操作
            
            当前Skill目录：%s
            
            请严格按以下格式回复，不要输出其他内容：
            - 如果命令安全，回复：SAFE
            - 如果命令不安全，回复：UNSAFE: [具体原因]
            """;

    /**
     * 用户消息模板
     * <p>
     * 支持占位符：
     * %s 第一个参数：命令内容
     * %s 第二个参数：工作目录
     * </p>
     */
    private String userMessageTemplate = """
            请审计以下终端命令的安全性：
            
            命令内容：%s
            
            工作目录：%s
            """;

    /**
     * 安全响应标识
     * <p>
     * 当AI模型判定命令安全时返回的文本
     * </p>
     */
    private String safeResponse = "SAFE";

    /**
     * 不安全响应前缀
     * <p>
     * 当AI模型判定命令不安全时返回的前缀，后跟具体原因
     * 例如："UNSAFE: 该命令会删除重要文件"
     * </p>
     */
    private String unsafeResponsePrefix = "UNSAFE:";

    /**
     * 是否启用AI模型安全检查
     * <p>
     * 设为false可跳过AI检查，仅依赖静态规则过滤
     * </p>
     */
    private boolean enabled = true;

    /**
     * AI检查超时时间（秒）
     * <p>
     * 超过此时间未收到响应则默认允许执行
     * </p>
     */
    private long timeoutSeconds = 30L;

    /**
     * AI检查失败时的默认行为
     * <p>
     * true - 默认允许执行（保证可用性优先）
     * false - 默认拒绝执行（安全性优先）
     * </p>
     */
    private boolean defaultAllowOnFailure = true;
}