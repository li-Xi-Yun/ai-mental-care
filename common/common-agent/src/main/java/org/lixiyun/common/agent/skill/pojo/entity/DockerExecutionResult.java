package org.lixiyun.common.agent.skill.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Docker容器执行结果实体类
 * <p>
 * 封装终端命令在Docker容器中执行的完整结果信息，
 * 替代原来的Map&lt;String, Object&gt;结构，
 * 提供类型安全和IDE支持。
 * </p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li><b>类型安全</b>：所有字段都有明确的类型定义</li>
 *   <li><b>不可变性</b>：通过@Builder构建后不可修改</li>
 *   <li><b>可序列化</b>：支持JSON序列化用于API响应</li>
 *   <li><b>语义清晰</b>：字段名自解释，无需注释</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>Docker容器命令执行结果</li>
 *   <li>AI工具调用返回值</li>
 *   <li>日志记录和监控</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerExecutionResult {

    /**
     * 执行是否成功
     * <p>
     * 基于退出码判断：exitCode == 0 表示成功
     * </p>
     */
    private boolean success;

    /**
     * 进程退出码
     * <p>
     * 0 表示成功，非零表示失败（具体含义取决于命令）
     * -1 通常表示异常（超时、中断等）
     * </p>
     */
    private int exitCode;

    /**
     * 标准输出内容（stdout）
     * <p>
     * 命令正常输出的内容，可能包含多行文本
     * </p>
     */
    private String stdout;

    /**
     * 错误输出内容（stderr）
     * <p>
     * 命令的错误输出和警告信息
     * </p>
     */
    private String stderr;

    /**
     * 错误消息（可选）
     * <p>
     * 当执行失败时的错误描述。
     * 成功时此字段为null。
     * </p>
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     * <p>
     * 从启动进程到获取结果的耗时统计
     * </p>
     */
    private long executionTime;

    /**
     * Skill名称
     * <p>
     * 执行命令所在的Skill目录名
     * </p>
     */
    private String skillName;

    /**
     * Docker容器名称
     * <p>
     * 执行命令的临时容器唯一标识
     * 格式：skill-exec-{contextId}-{timestamp}
     * </p>
     */
    private String containerName;

    /**
     * 执行环境标识
     * <p>
     * 标识命令在何种环境中执行：
     * - DOCKER_CONTAINER：Docker容器环境
     * - HOST_DIRECT：主机直接执行（已废弃）
     * </p>
     */
    private String executionEnvironment;

    /**
     * 转换为Map格式（兼容旧代码）
     * <p>
     * 用于与现有的Map-based API保持兼容。
     * 新代码建议直接使用实体类。
     * </p>
     *
     * @return Map格式的执行结果
     */
    public Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("success", success);
        map.put("exitCode", exitCode);
        map.put("stdout", stdout != null ? stdout : "");
        map.put("stderr", stderr != null ? stderr : "");
        map.put("executionTime", executionTime);
        map.put("skillName", skillName);
        map.put("containerName", containerName);
        map.put("executionEnvironment", executionEnvironment);

        if (errorMessage != null) {
            map.put("errorMessage", errorMessage);
        }

        return map;
    }

    /**
     * 创建成功的执行结果
     *
     * @param exitCode       退出码（应为0）
     * @param stdout         标准输出
     * @param stderr         错误输出
     * @param executionTime  执行耗时
     * @param skillName      Skill名称
     * @param containerName  容器名称
     * @return 成功的执行结果实例
     */
    public static DockerExecutionResult success(int exitCode, String stdout, String stderr,
                                                long executionTime, String skillName,
                                                String containerName) {
        return DockerExecutionResult.builder()
                .success(true)
                .exitCode(exitCode)
                .stdout(stdout)
                .stderr(stderr)
                .executionTime(executionTime)
                .skillName(skillName)
                .containerName(containerName)
                .executionEnvironment("DOCKER_CONTAINER")
                .build();
    }

    /**
     * 创建失败的执行结果
     *
     * @param exitCode       退出码（通常为-1）
     * @param errorMessage   错误消息
     * @param stdout         标准输出（可能为空）
     * @param stderr         错误输出
     * @param executionTime  执行耗时
     * @param skillName      Skill名称
     * @param containerName  容器名称
     * @return 失败的执行结果实例
     */
    public static DockerExecutionResult failure(int exitCode, String errorMessage,
                                                 String stdout, String stderr,
                                                 long executionTime, String skillName,
                                                 String containerName) {
        return DockerExecutionResult.builder()
                .success(false)
                .exitCode(exitCode)
                .stdout(stdout != null ? stdout : "")
                .stderr(stderr != null ? stderr : "")
                .errorMessage(errorMessage)
                .executionTime(executionTime)
                .skillName(skillName)
                .containerName(containerName)
                .executionEnvironment("DOCKER_CONTAINER")
                .build();
    }
}