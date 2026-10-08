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
 * 用户测评记录表(scale_user_record)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleUserRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 作答状态：未完成 */
    public static final int FINISH_STATUS_UNFINISHED = 0;
    /** 作答状态：已完成 */
    public static final int FINISH_STATUS_FINISHED = 1;
    /** 作答状态：中途终止 */
    public static final int FINISH_STATUS_TERMINATED = 2;

    /** 风险等级：无风险 */
    public static final int RISK_LEVEL_NONE = 0;
    /** 风险等级：低 */
    public static final int RISK_LEVEL_LOW = 1;
    /** 风险等级：中 */
    public static final int RISK_LEVEL_MEDIUM = 2;
    /** 风险等级：高（预警） */
    public static final int RISK_LEVEL_HIGH = 3;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 量表主表ID */
    private Long scaleId;

    /** 量表版本ID */
    private Long scaleVersionId;

    /** 本次测评使用的常模组ID */
    private Long normGroupId;

    /** 量表名称 */
    private String scaleName;

    /** 最终计算原始总分 */
    private BigDecimal totalScore;

    /** 标准分（如T分），由常模转换得出 */
    private BigDecimal standardScore;

    /** 百分等级快照 */
    private BigDecimal percentile;

    /** 本次测评结果描述 */
    private String resultText;

    /** 0无风险 1低 2中 3高（预警） */
    private Integer riskLevel;

    /** 0未完成 1已完成 2中途终止 */
    private Integer finishStatus;

    /** 开始作答时间 */
    private LocalDateTime startTime;

    /** 提交时间 */
    private LocalDateTime endTime;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 是否删除，0-否，1-是 */
    @TableLogic
    private Integer deleted;
    /**
     * 判断测评记录是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }

    /**
     * 判断当前作答状态是否为"未完成"
     *
     * @return true 表示作答状态为未完成，false 表示不是
     */
    public boolean isUnfinished() {
        return finishStatus != null && finishStatus == FINISH_STATUS_UNFINISHED;
    }

    /**
     * 判断当前作答状态是否为"已完成"
     *
     * @return true 表示作答状态为已完成，false 表示不是
     */
    public boolean isFinished() {
        return finishStatus != null && finishStatus == FINISH_STATUS_FINISHED;
    }

    /**
     * 判断当前作答状态是否为"中途终止"
     *
     * @return true 表示作答状态为中途终止，false 表示不是
     */
    public boolean isTerminated() {
        return finishStatus != null && finishStatus == FINISH_STATUS_TERMINATED;
    }

    /**
     * 判断当前风险等级是否为"无风险"
     *
     * @return true 表示风险等级为无风险，false 表示不是
     */
    public boolean isRiskNone() {
        return riskLevel != null && riskLevel == RISK_LEVEL_NONE;
    }

    /**
     * 判断当前风险等级是否为"低"
     *
     * @return true 表示风险等级为低，false 表示不是
     */
    public boolean isRiskLow() {
        return riskLevel != null && riskLevel == RISK_LEVEL_LOW;
    }

    /**
     * 判断当前风险等级是否为"中"
     *
     * @return true 表示风险等级为中，false 表示不是
     */
    public boolean isRiskMedium() {
        return riskLevel != null && riskLevel == RISK_LEVEL_MEDIUM;
    }

    /**
     * 判断当前风险等级是否为"高（预警）"
     *
     * @return true 表示风险等级为高（预警），false 表示不是
     */
    public boolean isRiskHigh() {
        return riskLevel != null && riskLevel == RISK_LEVEL_HIGH;
    }
}