package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 历史诊断摘要-干预历史
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 历史自助建议清单
     */
    private List<InterventionRecord> selfHelpSuggestions;

    /**
     * 历史社会支持建议清单
     */
    private List<InterventionRecord> socialSupportSuggestions;

    /**
     * 历史专业干预建议清单
     */
    private List<InterventionRecord> professionalSuggestions;

    /**
     * 历史最高建议优先级
     */
    private Integer highestSuggestionPriority;

    /**
     * 最近一次自助建议
     */
    private String latestSelfHelpSuggestion;

    /**
     * 最近一次社会支持建议
     */
    private String latestSocialSupportSuggestion;

    /**
     * 最近一次专业干预建议
     */
    private String latestProfessionalSuggestion;

    /**
     * 单条干预建议历史记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterventionRecord implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 建议文本（整条存入，不做拆分） */
        private String suggestionText;

        /** 是否认同该建议：true认同 false不认同 null无反馈 */
        private Boolean agreed;

        /** 是否尝试执行：0未尝试 1尝试部分 2全部尝试 null无反馈，映射use_suggestion */
        private Integer useStatus;

        /** 该建议的诊断时间 */
        private LocalDateTime diagnosisTime;

        public static String getPrompt() {
            return "suggestionText：建议文本，" +
                    "agreed：是否认同该建议（true认同/false不认同/null无反馈），" +
                    "useStatus：是否尝试执行（0未尝试/1尝试部分/2全部尝试/null无反馈），" +
                    "diagnosisTime：该建议的诊断时间";
        }
    }

    public static String getPrompt() {
        return "selfHelpSuggestions：" + InterventionRecord.getPrompt() + "，" +
                "socialSupportSuggestions：" + InterventionRecord.getPrompt() + "，" +
                "professionalSuggestions：" + InterventionRecord.getPrompt() + "，" +
                "highestSuggestionPriority：历史最高建议优先级，" +
                "latestSelfHelpSuggestion：最近一次自助建议，" +
                "latestSocialSupportSuggestion：最近一次社会支持建议，" +
                "latestProfessionalSuggestion：最近一次专业干预建议";
    }
}