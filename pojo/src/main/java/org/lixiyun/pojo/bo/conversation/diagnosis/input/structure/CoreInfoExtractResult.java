package org.lixiyun.pojo.bo.conversation.diagnosis.input.structure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模块2-核心信息提取结果
 * 包含用户核心诉求、关键事件时间线、症状表述原文列表、背景信息总结等核心信息
 * @author lixiyun
 * @since 2026-08-10 14:11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreInfoExtractResult {

    /**
     * 核心诉求
     * 用户本次咨询的核心问题与需求描述
     */
    private String coreAppeal;

    /**
     * 关键事件时间线
     * 按对话轮次排序的关键应激事件列表
     */
    private List<KeyEventItem> keyEventTimeline;

    /**
     * 症状表述原文列表
     * 用户提到的所有症状原始表述，附带轮次，可直接传入模块4做归一化处理
     */
    private List<SymptomRawItem> symptomOriginalList;

    /**
     * 背景信息总结
     * 社会支持、生活环境等用户背景描述
     */
    private String backgroundSummary;

    public static String getPrompt() {
        return "coreAppeal：核心诉求，用户本次咨询的核心问题与需求描述，" +
                "keyEventTimeline：" + KeyEventItem.getPrompt() + "，" +
                "symptomOriginalList：" + SymptomRawItem.getPrompt() + "，" +
                "backgroundSummary：背景信息总结，社会支持、生活环境等用户背景描述";
    }
}