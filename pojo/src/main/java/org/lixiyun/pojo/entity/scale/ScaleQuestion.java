package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表题目表(scale_question)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目类型：单选 */
    public static final int QUESTION_TYPE_SINGLE = 1;
    /** 题目类型：多选 */
    public static final int QUESTION_TYPE_MULTIPLE = 2;
    /** 题目类型：填空 */
    public static final int QUESTION_TYPE_FILL = 3;

    /** 计分方式：正向计分 */
    public static final int SCORE_TYPE_FORWARD = 1;
    /** 计分方式：反向计分 */
    public static final int SCORE_TYPE_REVERSE = 2;
    /** 计分方式：不计分 */
    public static final int SCORE_TYPE_NONE = 0;

    /** 是否必答：是 */
    public static final int REQUIRED_YES = 1;
    /** 是否必答：否 */
    public static final int REQUIRED_NO = 0;

    /** 答题状态：未作答 */
    public static final int ANSWER_STATUS_UNDONE = 0;
    /** 答题状态：已作答 */
    public static final int ANSWER_STATUS_DONE = 1;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 量表版本ID */
    private Long scaleVersionId;

    /** 所属维度ID，筛选题可以为空 */
    private Long dimensionId;

    /** 题干 */
    private String title;

    /** 1单选 2多选 3填空 */
    private Integer questionType;

    /** 题目顺序 */
    private Integer sort;

    /** 1正向计分 2反向计分 0不计分 */
    private Integer scoreType;

    /** 是否必答：0-否 1-是 */
    private Integer required;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 创建人用户ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;

    /** 最后更新人用户ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 是否删除，0-否，1-是 */
    @TableLogic
    private Integer deleted;
    /**
     * 判断题目是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}
