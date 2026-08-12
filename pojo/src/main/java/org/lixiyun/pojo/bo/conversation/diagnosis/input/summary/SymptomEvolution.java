package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 历史诊断摘要-症状演变
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomEvolution implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 持续症状
     * key:症状标签 value:全局历史总出现次数
     */
    private Map<String, Integer> persistentSymptomMap;

    /**
     * 新增症状（观察期存在，基准期无）
     * key:症状标签 value:观察期内出现次数
     */
    private Map<String, Integer> newSymptomMap;

    /**
     * 缓解症状（基准持续，观察期消失）
     * key:症状标签 value:基准期内出现次数
     */
    private Map<String, Integer> relievedSymptomMap;

    public static String getPrompt() {
        return "persistentSymptomMap：持续症状（key:症状标签，value:全局历史总出现次数），" +
                "newSymptomMap：新增症状（key:症状标签，value:观察期内出现次数），" +
                "relievedSymptomMap：缓解症状（key:症状标签，value:基准期内出现次数）";
    }
}