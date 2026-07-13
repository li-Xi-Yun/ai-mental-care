package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * Skill模块异常枚举
 * <p>用于定义Skill相关的所有业务异常</p>
 *
 * @author lixiyun
 * @since 2026-07-13 09:20
 */
public enum SkillExceptionEnum implements ErrorCode {

    // 状态码从3500开始

    SKILL_PATH_NOT_FOUND("Skill路径不存在", 3501),
    SKILL_PATH_UNSAFE("路径不安全，检测到潜在的路径穿透攻击", 3502),
    SKILL_FILE_NOT_FOUND("Skill文件不存在", 3503),
    SKILL_FOLDER_NOT_FOUND("Skill文件夹不存在", 3504),
    SKILL_FILE_READ_ERROR("文件读取失败", 3505),
    SKILL_FILE_NAME_INVALID("文件名不合法", 3506),
    SKILL_FOLDER_NAME_INVALID("文件夹名称不合法", 3507),
    SKILL_FILE_ALREADY_EXISTS("文件已存在", 3508),
    SKILL_FOLDER_ALREADY_EXISTS("文件夹已存在", 3509),
    SKILL_ROOT_CANNOT_DELETE("SKILL根目录不允许删除", 3510),
    SKILL_MD_FILE_OPERATION_ERROR("SKILL.md文件操作错误", 3511),
    SKILL_FILE_UPLOAD_SIZE_EXCEEDED("文件大小超出限制", 3512),
    SKILL_ZIP_FILE_PARSE_ERROR("压缩包解析失败", 3513),
    SKILL_DISTRIBUTED_LOCK_ACQUIRE_FAILED("分布式锁获取失败", 3514),
    SKILL_FOLDER_DELETE_ERROR("文件夹删除失败", 3515),
    SKILL_FILE_DELETE_ERROR("文件删除失败", 3516),
    SKILL_MULTIPLE_SKILL_MD_FILES("文件夹中存在多个SKILL.md文件", 3517),
    SKILL_FOLDER_UPLOAD_SIZE_EXCEEDED("压缩包大小超出限制", 3518),
    SKILL_FILE_CONTENT_UPDATE_ERROR("文件内容修改失败", 3519),
    SKILL_FILE_NOT_TEXT_FILE("只允许修改纯文本文件", 3520),
    SKILL_MD_METADATA_FORMAT_ERROR("SKILL.md元数据格式错误", 3521),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    SkillExceptionEnum(String msg, int code){
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