package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * AI节点配置异常枚举
 *
 * @author lixiyun
 * @since 2026-09-07
 */
public enum AiNodeConfigExceptionEnum implements ErrorCode {

    // 状态码从2600开始

    AI_NODE_CONFIG_NOT_FOUND("AI节点配置不存在", 2600),
    AI_NODE_CONFIG_NODE_KEY_EXISTS("节点唯一标识已存在", 2601),
    AI_NODE_CONFIG_ADD_FAIL("AI节点配置新增失败", 2602),
    AI_NODE_CONFIG_UPDATE_FAIL("AI节点配置更新失败", 2603),
    AI_NODE_CONFIG_DELETE_FAIL("AI节点配置删除失败", 2604),
    AI_NODE_CONFIG_VERSION_CONFLICT("配置版本冲突，请刷新后重试", 2605),
    AI_NODE_CONFIG_HISTORY_NOT_FOUND("变更历史记录不存在", 2606),
    AI_NODE_CONFIG_BATCH_ENABLED_FAIL("批量启用/禁用失败", 2607),
    AI_NODE_PROMPT_UPDATE_FAIL("AI节点提示词更新失败", 2608),
    AI_NODE_CONFIG_ROLLBACK_FAIL("AI节点配置回滚失败", 2609),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    AiNodeConfigExceptionEnum(String msg, int code) {
        this.msg = msg;
        this.code = code;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setCode(int code) {
        this.code = code;
    }
}