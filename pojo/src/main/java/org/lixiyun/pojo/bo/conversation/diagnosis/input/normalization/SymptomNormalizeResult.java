package org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 症状归一化处理结果
 * 模块4核心输出对象
 *
 * @author lixiyun
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomNormalizeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标准化标签列表（核心输出）
     * 按出现次数降序排列，高频核心症状在前
     */
    private List<SymptomTerm> termList;

    /**
     * 术语-原文映射表
     * key：标准术语ID（对应SymptomDict.termId），使用String类型避免Jackson序列化Map数值型Key时的类型推断异常
     * value：该术语匹配到的所有用户原始表述列表
     * 用于溯源验证，保证归一化结果可回溯
     */
    private Map<String, List<SymptomOriginalItem>> termOriginalMapping;

    public static String getPrompt() {
        return "termList：" + SymptomTerm.getPrompt() + "，" +
                "termOriginalMapping：术语-原文映射表（key:标准术语ID，value:" + SymptomOriginalItem.getPrompt() + "）";
    }
}