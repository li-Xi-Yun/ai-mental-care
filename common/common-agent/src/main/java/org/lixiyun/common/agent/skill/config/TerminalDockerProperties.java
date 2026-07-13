package org.lixiyun.common.agent.skill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 终端命令Docker执行配置属性
 * <p>
 * 用于配置Docker容器的运行参数，
 * 包括资源限制、网络隔离、超时控制等。
 * 所有配置项均可通过application.yml动态调整。
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>{@code
 * skill:
 *   terminal:
 *     docker:
 *       base-image: alpine:latest
 *       memory-limit: 512m
 *       cpu-limit: 1
 *       timeout-seconds: 120
 *       network-mode: none
 *       read-only-fs: true
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Data
@Component
@ConfigurationProperties(prefix = "skill.terminal.docker")
public class TerminalDockerProperties {

    /**
     * Docker基础镜像名称
     * <p>
     * 用于创建临时执行容器的基础镜像。
     * 推荐使用轻量级镜像以减少启动时间。
     * </p>
     *
     * <p>常用选项：</p>
     * <ul>
     *   <li><b>alpine:latest</b> - 最小化Linux发行版（~5MB）</li>
     *   <li><b>python:3.11-slim</b> - Python环境（~150MB）</li>
     *   <li><b>node:20-alpine</b> - Node.js环境（~180MB）</li>
     * </ul>
     */
    private String baseImage;

    /**
     * 内存限制（如：512m, 1g, 2g）
     * <p>
     * Docker容器的最大内存使用量。
     * 设置为0表示不限制（不推荐）。
     * </p>
     */
    private String memoryLimit;

    /**
     * CPU限制（核数）
     * <p>
     * Docker容器可使用的CPU核心数。
     * 可以是小数，如0.5表示半个核心。
     * </p>
     */
    private double cpuLimit;

    /**
     * 内存交换空间限制（如：512m, 0表示禁用swap）
     * <p>
     * 通常设置为与memory-limit相同以禁用交换内存。
     * 禁用swap可以防止进程因内存不足而使用磁盘导致性能下降。
     * </p>
     */
    private String memorySwap;

    /**
     * 命令执行超时时间（秒）
     * <p>
     * 单次命令的最大执行时间。
     * 超时后容器将被强制终止。
     * </p>
     */
    private long timeoutSeconds;

    /**
     * 网络模式
     * <p>
     * Docker容器的网络隔离模式：
     * </p>
     * <ul>
     *   <li><b>none</b> - 无网络访问（最安全）</li>
     *   <li><b>bridge</b> - 默认桥接网络</li>
     *   <li><b>host</b> - 使用主机网络（不推荐）</li>
     * </ul>
     */
    private String networkMode;

    /**
     * 是否启用只读根文件系统
     * <p>
     * 启用后容器的根文件系统为只读，
     * 只有显式挂载的卷才能写入。
     * 可以防止恶意程序修改系统文件。
     * </p>
     */
    private boolean readOnlyFs;

    /**
     * 容器内工作目录
     * <p>
     * Skill目录在容器内的挂载路径。
     * 命令将在此目录下执行。
     * </p>
     */
    private String workspaceDir;

    /**
     * 容器清理超时时间（秒）
     * <p>
     * 超时容器停止和删除操作的超时时间。
     * 超时后将强制终止。
     * </p>
     */
    private long cleanupTimeoutSeconds;

    /**
     * 容器名前缀
     * <p>
     * 自动生成的容器名将此前缀作为开头。
     * </p>
     */
    private String containerNamePrefix = "skill-exec-";

    // ==================== 分布式锁配置 ====================

    /**
     * 分布式锁Key前缀
     * <p>
     * 用于生成唯一的锁标识。
     * 最终锁Key格式：{lock-key-prefix}:{shadowKey标准化路径}
     * </p>
     */
    private String lockKeyPrefix = "skill:terminal:lock:";

    /**
     * 分布式锁最大重试次数
     * <p>
     * 获取锁失败后的最大重试次数。
     * 超过此次数将放弃并返回失败。
     * </p>
     */
    private int lockMaxRetries = 30;

    /**
     * 分布式锁重试间隔（毫秒）
     * <p>
     * 每次重试之间的等待时间。
     * </p>
     */
    private long lockRetryIntervalMs = 1000L;

    /**
     * 分布式锁持有时间（秒）
     * <p>
     * 成功获取锁后的持有时间。
     * 超时后锁自动释放，防止死锁。
     * 应该大于命令的最大执行时间。
     * </p>
     */
    private long lockLeaseTimeSeconds = 300L;
}