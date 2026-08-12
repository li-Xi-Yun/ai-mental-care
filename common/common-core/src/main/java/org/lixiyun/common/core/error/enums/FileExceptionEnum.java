package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2026-04-28 14:56
 */
public enum FileExceptionEnum implements ErrorCode {

    // 状态码从1800开始

    FILE_NOT_FOUND("文件不存在", 1801),
    FILE_READ_ERROR("文件读取失败", 1802),
    FILE_PARSE_ERROR("文件解析错误", 1803),
    NETWORK_TIMEOUT("网络超时", 1804),
    FILE_SIZE_EXCEEDED("文件大小超出限制", 1805),
    UNSUPPORTED_FILE_TYPE("不支持的文件类型", 1806),
    FILE_CATEGORY_NOT_FOUND("文件分类不存在", 1807),
    FILE_CATEGORY_HAS_FILES("文件分类下存在文件，不允许删除文件分类", 1808),
    FILE_CATEGORY_NAME_EXISTS("文件分类名称已存在", 1809),
    FILE_CATEGORY_NOT_ALLOWED_CHILD("文件分类下不允许存在子分类", 1810),
    FILE_CATEGORY_NOT_ALLOWED_UPDATE("文件分类下不允许更新", 1811),
    FILE_PARAMS_ERROR("RAG参数错误", 1812),
    FILE_VECTOR_UPDATE_ERROR("文件向量更新错误", 1814),
    FILE_NAME_INVALID("文件名错误", 1815),
    FOLDER_NAME_INVALID("文件夹名称错误", 1816),
    FILE_WRITE_ERROR("文件写入错误", 1817),
    FILE_KNOWLEDGE_TYPE_NONE("向量加载时，文件知识类型不允许为空", 1818),
    FILE_KNOWLEDGE_TYPE_UPDATE_NEED_DELETE_VECTOR("请先删除文件向量，才能更新知识类型", 1819),




    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    FileExceptionEnum(String msg, int code){
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