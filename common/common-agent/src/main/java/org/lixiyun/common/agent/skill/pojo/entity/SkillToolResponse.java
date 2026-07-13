package org.lixiyun.common.agent.skill.pojo.entity;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Skill工具统一响应封装类
 * <p>
 * 为所有AI模型可调用的Skill工具提供统一的响应格式，
 * 简化返回值结构，提供清晰的语义化字段，
 * 并内置AI友好的错误恢复建议机制。
 * </p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li><b>统一性</b>：所有工具方法返回相同格式的响应</li>
 *   <li><b>简洁性</b>：减少冗余字段，核心信息一目了然</li>
 *   <li><b>AI友好</b>：提供明确的成功/失败状态和操作建议</li>
 *   <li><b>可扩展</b>：通过data字段承载具体业务数据</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 成功响应
 * SkillToolResponse.success(skillsData);
 *
 * // 带消息的成功响应
 * SkillToolResponse.success("加载完成", skillsData);
 *
 * // 错误响应（自动包含AI建议）
 * SkillToolResponse.error("SKILL_NOT_FOUND", "Skill不存在");
 *
 * // Context_ID无效的特殊错误（引导AI重新加载）
 * SkillToolResponse.contextIdInvalid("getSkillContent");
 * }</pre>
 *
 * @param <T> 响应数据的类型
 * @author lixiyun
 * @since 2026-07-12
 */
@Data
public class SkillToolResponse<T> {

    /** 是否成功 */
    private boolean success;

    /** 状态码 */
    private String code;

    /** 消息描述 */
    private String message;

    /** 具体业务数据 */
    private T data;

    /** 给AI模型的操作建议（仅在错误时有效） */
    private String suggestion;

    /** 推荐的下一步操作（仅在错误时有效） */
    private String recommendedAction;

    /**
     * 私有构造函数，强制使用工厂方法创建实例
     */
    private SkillToolResponse() {
    }

    /**
     * 创建成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return 成功响应实例
     */
    public static <T> SkillToolResponse<T> success() {
        return success(null);
    }

    /**
     * 创建成功响应（带数据）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应实例
     */
    public static <T> SkillToolResponse<T> success(T data) {
        return success("操作成功", data);
    }

    /**
     * 创建成功响应（带消息和数据）
     *
     * @param message 成功消息
     * @param data    业务数据
     * @param <T>     数据类型
     * @return 成功响应实例
     */
    public static <T> SkillToolResponse<T> success(String message, T data) {
        SkillToolResponse<T> response = new SkillToolResponse<>();
        response.success = true;
        response.code = "200";
        response.message = message;
        response.data = data;
        return response;
    }

    /**
     * 创建错误响应
     * <p>
     * 通用的错误响应，适用于大多数异常情况。
     * 自动生成通用的建议信息。
     * </p>
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param <T>       数据类型
     * @return 错误响应实例
     */
    public static <T> SkillToolResponse<T> error(String errorCode, String message) {
        return error(errorCode, message, null, null);
    }

    /**
     * 创建错误响应（带建议）
     *
     * @param errorCode         错误码
     * @param message           错误消息
     * @param suggestion        给AI的建议
     * @param recommendedAction 推荐的下一步操作
     * @param <T>               数据类型
     * @return 错误响应实例
     */
    public static <T> SkillToolResponse<T> error(String errorCode, String message,
                                                  String suggestion, String recommendedAction) {
        SkillToolResponse<T> response = new SkillToolResponse<>();
        response.success = false;
        response.code = errorCode;
        response.message = message;
        response.data = null;
        response.suggestion = suggestion;
        response.recommendedAction = recommendedAction;
        return response;
    }

    /**
     * 创建Context_ID无效的特殊错误响应
     * <p>
     * 当context_id无法找到对应的数据时使用此方法。
     * 会自动生成标准的错误信息和AI操作建议，
     * 引导模型重新调用loadFirstLevelSkills从头查询。
     * </p>
     *
     * @param failedOperation 失败的操作名称（如"getSkillContent"、"loadChildrenSkills"）
     * @param <T>             数据类型
     * @return Context_ID无效的错误响应
     */
    public static <T> SkillToolResponse<T> contextIdInvalid(String failedOperation) {
        return error(
                "CONTEXT_ID_INVALID",
                "无法找到有效的Skill数据，context_id可能已失效或不正确",
                "请检查传入的context_id参数是否正确。" +
                        "如果确认参数无误但仍出现此错误，建议重新调用loadFirstLevelSkills工具从头开始查询最新的Skill列表和context_id",
                "RELOAD_SKILLS"
        );
    }

    /**
     * 创建跳过操作的响应（用于文件不存在等情况）
     * <p>
     * 当遇到非致命性错误（如补充资料文件不存在）时使用。
     * 告知AI可以安全地跳过当前操作继续执行。
     * </p>
     *
     * @param message  跳过原因说明
     * @param suggestion 给AI的建议
     * @param <T>      数据类型
     * @return 跳过响应实例
     */
    public static <T> SkillToolResponse<T> skip(String message, String suggestion) {
        SkillToolResponse<T> response = new SkillToolResponse<>();
        response.success = true;
        response.code = "SKIP";
        response.message = message;
        response.data = null;
        response.suggestion = suggestion;
        response.recommendedAction = "SKIP_AND_CONTINUE";
        return response;
    }

    /**
     * 将响应转换为Map（用于JSON序列化）
     * <p>
     * 由于Spring AI工具方法需要返回Map类型，
     * 提供此方法方便转换。
     * </p>
     *
     * @return Map形式的响应数据
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("code", code);
        map.put("message", message);

        if (data != null) {
            map.put("data", data);
        }

        if (!success) {
            if (suggestion != null) {
                map.put("suggestion", suggestion);
            }
            if (recommendedAction != null) {
                map.put("recommendedAction", recommendedAction);
            }
        } else if ("SKIP".equals(code)) {
            if (suggestion != null) {
                map.put("suggestion", suggestion);
            }
            if (recommendedAction != null) {
                map.put("recommendedAction", recommendedAction);
            }
        }

        return map;
    }

    /**
     * 判断是否为Context_ID无效错误
     *
     * @return true-是Context_ID无效错误
     */
    public boolean isContextIdInvalid() {
        return !success && "CONTEXT_ID_INVALID".equals(code);
    }

    /**
     * 判断是否为跳过操作
     *
     * @return true-应该跳过当前操作
     */
    public boolean isSkipOperation() {
        return success && "SKIP".equals(code);
    }
}