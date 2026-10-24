package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * Live2D 模型配置异常枚举
 *
 * @author lixiyun
 * @since 2026-10-14
 */
public enum Live2dConfigExceptionEnum implements ErrorCode {

    // 状态码从2700开始（2600 为 AiNodeConfig，2800 为 InputAdapter，互不冲突）

    LIVE2D_CONFIG_NOT_FOUND("Live2D模型配置不存在", 3200),
    LIVE2D_CONFIG_ADD_FAIL("Live2D模型配置新增失败", 3201),
    LIVE2D_CONFIG_UPDATE_FAIL("Live2D模型配置更新失败", 3202),
    LIVE2D_CONFIG_DELETE_FAIL("Live2D模型配置删除失败", 3203),
    LIVE2D_CONFIG_ENABLED_FAIL("Live2D模型配置启用/禁用失败", 3204),
    LIVE2D_MODEL_NAME_INVALID("模型名称不合法，仅支持字母/数字/下划线/中划线", 3205),
    LIVE2D_MODEL_NAME_EXISTS("模型名称已存在", 3206),
    LIVE2D_UPLOAD_ZIP_INVALID("上传文件不是有效的ZIP压缩包或已损坏", 3207),
    LIVE2D_UPLOAD_ZIP_SIZE_EXCEEDED("模型包大小超出限制", 3208),
    LIVE2D_MODEL_FILE_NOT_FOUND("模型包中未找到 .model3.json 入口文件", 3209),
    LIVE2D_UPLOAD_MODEL_DIR_EXISTS("同名模型目录已存在，请先删除已创建的模型目录或更换模型名称", 3210),
    LIVE2D_MODEL_ASSET_PARSE_FAIL("模型资产（exp3/motion3）解析失败", 3211),
    LIVE2D_MODEL_PATH_INVALID("模型文件路径不合法", 3212),
    LIVE2D_MODEL_SIGN_INVALID("模型文件签名无效或已过期", 3213),
    LIVE2D_MODEL_ASSET_NOT_FOUND("模型包中未找到指定的表情/动作清单文件", 3214),
    LIVE2D_FEATURE_NOT_ENABLED("Live2D虚拟形象功能尚未启用，请不要选择该输出模式", 3215),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    Live2dConfigExceptionEnum(String msg, int code) {
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