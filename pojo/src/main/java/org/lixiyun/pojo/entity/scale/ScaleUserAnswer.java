package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户答题基础表(scale_user_answer)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleUserAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的测评记录ID */
    private Long recordId;

    /** 题目ID */
    private Long questionId;

    /** 题干快照 */
    private String questionTitle;

    /** 题目类型快照：1=单选 2=多选 3=填空 */
    private Integer questionType;

    /** 0未作答 1已作答 */
    private Integer answerStatus;

    /** 本题耗时，秒 */
    private Integer spendSeconds;

    /** 该选项得分快照 */
    private BigDecimal originalScore;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 是否删除，0-否，1-是 */
    @TableLogic
    private Integer deleted;
    /**
     * 判断答题明细是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}
