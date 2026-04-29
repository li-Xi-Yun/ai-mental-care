package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2025-12-22 17:13
 */
public enum KnowledgeBaseExceptionEnum implements ErrorCode {


    // 状态码从1500开始


    KNOWLEDGE_BASE_NOT_EXIST("知识库不存在", 2001),
    KNOWLEDGE_BASE_FILE_NOT_EXIST("知识库文件不存在", 2002),
    KNOWLEDGE_BASE_FILE_NOT_PUBLIC("知识库文件未公开", 2003),
    KNOWLEDGE_BASE_NO_PERMISSION_DELETE("无权删除知识库", 2004),
    KNOWLEDGE_BASE_ACCESS_NOT_ALLOWED("该知识库不允许访问", 2005),
    KNOWLEDGE_BASE_NOT_PUBLIC("知识库未公开", 2006),
    KNOWLEDGE_BASE_ALREADY_QUOTE("知识库已添加", 2007),
    FILE_PARSE_ERROR("文件解析错误", 2008),
    CATEGORY_HAS_DATA("该分类下还存在数据，请先删除", 2009),










    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    KnowledgeBaseExceptionEnum(String msg, int code){
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
