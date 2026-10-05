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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 常模转换明细表(scale_norm)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleNorm implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 常模组ID，关联 scale_norm_group */
    private Long normGroupId;

    /** 维度ID（冗余，方便直接查询），NULL=总分 */
    private Long dimensionId;

    /** 原始分 */
    private BigDecimal rawScore;

    /** T分：均值50 标准差10 */
    private BigDecimal tScore;

    /** Z分：均值0  标准差1 */
    private BigDecimal zScore;

    /** 百分等级 0.00~100.00 */
    private BigDecimal percentile;

    /** 标准九 1~9 */
    private Integer stanine;

    /** 离差智商：均值100 标准差15 */
    private BigDecimal diq;

    /** 等级标签：0-极低/1-偏低/2-正常/3-偏高/4-极高 */
    private Integer levelLabel;

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

    /** 是否删除，0=否 1=是 */
    @TableLogic
    private Integer deleted;
    /**
     * 判断常模明细是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}
