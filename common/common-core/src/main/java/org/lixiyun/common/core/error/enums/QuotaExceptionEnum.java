package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * AI对话配额异常枚举
 *
 * @author lixiyun
 * @since 2026-10-15
 */
public enum QuotaExceptionEnum implements ErrorCode {

    // 状态码从1500开始

    QUOTA_EXCEEDED("次数已达上限，如需继续使用请联系管理员", 1500),
    QUOTA_CONFIG_NOT_FOUND("对话配额配置不存在", 1501),
    QUOTA_USED_EXCEEDS_LIMIT("已用次数不能大于单次限额", 1502),
    QUOTA_UPDATE_FAILED("对话配额更新失败", 1503),
    RESOURCE_DISABLED("资源已停用，请联系管理员", 1504),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    QuotaExceptionEnum(String msg, int code) {
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