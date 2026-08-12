package org.lixiyun.pojo.bo.conversation.diagnosis.input.structure;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 症状原文项（结构与模块4入参完全对齐）
 * @author lixiyun
 * @since 2026-08-10 14:11
 */
@Data
@Builder
public class SymptomRawItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 症状出现的对话轮次号
     */
    private Integer roundNum;

    /**
     * 用户症状原始表述文本
     */
    private String originalText;

    public static String getPrompt() {
        return "roundNum：症状出现的对话轮次号，" +
                "originalText：用户症状原始表述文本";
    }
}