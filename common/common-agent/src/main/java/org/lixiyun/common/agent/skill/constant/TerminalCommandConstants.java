package org.lixiyun.common.agent.skill.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 终端命令安全校验常量
 * <p>
 * 定义终端命令执行时的安全策略相关常量，
 * 包括危险命令模式黑名单、安全命令白名单、执行限制等。
 * 用于 {@link org.lixiyun.common.agent.skill.tools.SkillTool} 的命令安全性校验。
 * </p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li><b>最小权限原则</b>：仅允许必要的命令和操作</li>
 *   <li><b>黑名单+白名单</b>：双重过滤机制确保安全</li>
 *   <li><b>可配置性</b>：支持通过配置文件覆盖默认值</li>
 *   <li><b>可扩展性</b>：便于添加新的规则和限制</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-07-12
 */
public class TerminalCommandConstants {

    private TerminalCommandConstants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    /**
     * 命令最大长度限制（字符数）
     * <p>
     * 超过此长度的命令将被拒绝，防止过长的恶意命令注入。
     * </p>
     */
    public static final int MAX_COMMAND_LENGTH = 1000;

    /**
     * 管道/连接符最大数量限制
     * <p>
     * 命令中允许的管道符（|）、逻辑与（&&）、分号（;）的最大数量。
     * 超过此数量将被视为潜在的命令注入攻击。
     * </p>
     */
    public static final int MAX_PIPE_CONNECTOR_COUNT = 3;

    /**
     * 命令执行超时时间（秒）
     * <p>
     * 单次命令执行的最大时间限制。
     * 超时后进程将被强制终止，防止长时间运行的恶意命令。
     * </p>
     */
    public static final long COMMAND_TIMEOUT_SECONDS = 120L;

    /**
     * 危险命令模式黑名单
     * <p>
     * 包含以下任一模式的命令将被拒绝执行：
     * </p>
     *
     * <h4>系统破坏类：</h4>
     * <ul>
     *   <li>{@code rm -rf /} - 危险的递归删除根目录</li>
     *   <li>{@code mkfs} - 格式化文件系统</li>
     *   <li>{@code dd if=} - 磁盘底层写入操作</li>
     * </ul>
     *
     * <h4>资源耗尽类：</h4>
     * <ul>
     *   <li>{@code :(){ :|:& };:} - Fork炸弹（Bash Fork Bomb）</li>
     * </ul>
     *
     * <h4>权限提升类：</h4>
     * <ul>
     *   <li>{@code chmod 777 /} - 修改系统目录权限</li>
     *   <li>{@code chown -R} - 批量修改文件所有者</li>
     *   <li>{@code sudo } - 使用超级用户权限</li>
     *   <li>{@code su } - 切换用户</li>
     *   <li>{@code passwd} - 修改密码</li>
     *   <li>{@code shadow} - 访问密码影子文件</li>
     * </ul>
     *
     * <h4>远程代码执行类：</h4>
     * <ul>
     *   <li>{@code curl | bash} - 从远程下载并执行脚本</li>
     *   <li>{@code wget | bash} - 从远程下载并执行脚本</li>
     * </ul>
     *
     * <h4>代码注入类：</h4>
     * <ul>
     *   <li>{@code eval $(} - 动态代码执行</li>
     *   <li>{@code $(command)} - 命令替换注入</li>
     *   <li>{@code `command`} - 反引号命令替换</li>
     * </ul>
     *
     * <h4>敏感路径访问类：</h4>
     * <ul>
     *   <li>{@code > /etc/} - 写入系统配置目录</li>
     *   <li>{@code /proc/} - 访问进程信息</li>
     *   <li>{@code /sys/} - 访问系统信息</li>
     * </ul>
     */
    public static final List<String> DANGEROUS_COMMAND_PATTERNS = Arrays.asList(
            "rm -rf /",
            "mkfs",
            "dd if=",
            ":(){ :|:& };:",
            "chmod 777 /",
            "chown -R",
            "> /etc/",
            "curl | bash",
            "wget | bash",
            "eval $(",
            "$(command)",
            "`command`",
            "sudo ",
            "su ",
            "passwd",
            "shadow",
            "/proc/",
            "/sys/"
    );

    /**
     * 安全命令前缀白名单
     * <p>
     * 仅允许以以下前缀开头的命令被执行。
     * 这些命令通常是安全的开发/构建/运行工具。
     * </p>
     *
     * <h4>Python生态：</h4>
     * <ul>
     *   <li>{@code python}, {@code python3} - Python解释器</li>
     *   <li>{@code pip install} - Python包安装</li>
     * </ul>
     *
     * <h4>Node.js生态：</h4>
     * <ul>
     *   <li>{@code node} - Node.js运行时</li>
     *   <li>{@code npm}, {@code npm install} - Node包管理器</li>
     *   <li>{@code yarn add} - Yarn包管理器</li>
     * </ul>
     *
     * <h4>Java生态：</h4>
     * <ul>
     *   <li>{@code java} - Java运行时</li>
     *   <li>{@code javac} - Java编译器</li>
     *   <li>{@code mvn} - Maven构建工具</li>
     *   <li>{@code gradle} - Gradle构建工具</li>
     * </ul>
     *
     * <h4>C/C++生态：</h4>
     * <ul>
     *   <li>{@code make} - Make构建工具</li>
     *   <li>{@code cmake} - CMake构建系统</li>
     *   <li>{@code gcc} - GCC编译器</li>
     *   <li>{@code g++} - G++编译器</li>
     * </ul>
     *
     * <h4>其他语言运行时：</h4>
     * <ul>
     *   <li>{@code go run} - Go语言运行</li>
     *   <li>{@code cargo run} - Rust/Cargo运行</li>
     *   <li>{@code dotnet} - .NET运行时</li>
     *   <li>{@code ruby} - Ruby解释器</li>
     *   <li>{@code php} - PHP解释器</li>
     * </ul>
     */
    public static final List<String> ALLOWED_COMMAND_PREFIXES = Arrays.asList(
            "python",
            "python3",
            "node",
            "npm",
            "npm install",
            "java",
            "javac",
            "mvn",
            "gradle",
            "make",
            "cmake",
            "gcc",
            "g++",
            "go run",
            "cargo run",
            "dotnet",
            "ruby",
            "php",
            "pip install",
            "yarn add"
    );

    /**
     * 命令格式正则表达式（允许的字符集）
     * <p>
     * 对于不在白名单中的命令，必须匹配此正则表达式才能通过校验。
     * 仅允许字母、数字、下划线、斜杠、连字符、空格等安全字符。
     * </p>
     */
    public static final String SAFE_COMMAND_PATTERN = "^[a-zA-Z0-9_./\\-\\s]+$";

    /**
     * Docker基础镜像名称
     * <p>
     * 用于创建临时执行容器的Docker镜像。
     * 建议使用轻量级的基础镜像以减少启动时间。
     * </p>
     */
    public static final String DOCKER_BASE_IMAGE = "alpine:latest";

    /**
     * Docker容器超时清理策略
     * <p>
     * 容器执行完成后是否自动清理容器资源。
     * {@code true} - 自动删除容器（--rm参数）
     * {@code false} - 保留容器用于调试
     * </p>
     */
    public static final boolean DOCKER_AUTO_REMOVE = true;
}