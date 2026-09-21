package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估反馈表(AssessmentFeedback)实体类
 *
 * @author lixiyun
 * @since 2026-09-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "assessment_feedback", autoResultMap = true)
public class AssessmentFeedback implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 不认同 */
    public static final int AGREE_NO = 0;
    /** 认同 */
    public static final int AGREE_YES = 1;

    /** 未尝试采纳建议 */
    public static final int USE_SUGGESTION_NONE = 0;
    /** 尝试部分建议 */
    public static final int USE_SUGGESTION_PART = 1;
    /** 全部尝试建议 */
    public static final int USE_SUGGESTION_ALL = 2;

    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（冗余存储，便于单独查询）
     */
    private Long userId;

    /**
     * 诊断ID
     */
    private Long diagnosisId;

    /**
     * 用户对本次诊断打分 1~5分，NULL代表未评分
     */
    private Integer diagnosisScore;

    /**
     * 用户文字反馈、吐槽、补充意见
     */
    private String feedbackContent;

    /**
     * 是否认同风险评估：0-不认同 1-认同 NULL未反馈
     */
    private Integer agreeRiskJudge;

    /**
     * 是否认同给出的自助调节建议：0-不认同 1-认同 NULL未反馈
     */
    private Integer agreeSuggestionSelf;

    /**
     * 是否认同给出的社会支持建议：0-不认同 1-认同 NULL未反馈
     */
    private Integer agreeSuggestionSocial;

    /**
     * 是否认同给出的专业干预建议：0-不认同 1-认同 NULL未反馈
     */
    private Integer agreeSuggestionProfessional;

    /**
     * 是否尝试采纳建议：0-没有 1-尝试部分 2-全部尝试 NULL未反馈
     */
    private Integer useSuggestion;

    /**
     * 用户提交反馈时间
     */
    private LocalDateTime feedbackTime;

    /**
     * 记录创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;

}