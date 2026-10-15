package org.lixiyun.pojo.entity.quota;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI对话配额-消费与变更明细审计表(quota_usage_log)实体类
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "quota_usage_log")
public class QuotaUsageLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动作：CONSUME 扣减 */
    public static final int ACTION_CONSUME = 1;
    /** 动作：ROLLBACK 回滚 */
    public static final int ACTION_ROLLBACK = 2;
    /** 动作：ADMIN_ADJUST 管理员调整 */
    public static final int ACTION_ADMIN_ADJUST = 3;
    /** 动作：RESET 周期重置 */
    public static final int ACTION_RESET = 4;

    /** 备注：超限拒绝 */
    public static final String REMARK_QUOTA_REJECTED = "QUOTA_REJECTED";

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D */
    private Integer resourceType;

    /** 动作：1=CONSUME扣减 / 2=ROLLBACK回滚 / 3=ADMIN_ADJUST管理员调整 / 4=RESET周期重置 */
    private Integer action;

    /** 变化量：CONSUME=+1，ROLLBACK=-1，RESET=0，ADMIN_ADJUST=(after-before) */
    private Integer delta;

    /** 动作前 used_count */
    private Integer beforeUsed;

    /** 动作后 used_count */
    private Integer afterUsed;

    /** 操作人ID（管理员调整时必填，系统自动扣减为 NULL） */
    private Long operatorId;

    /** 关联会话ID（可选，便于核对具体一次对话） */
    private Long conversationId;

    /** 备注（如 QUOTA_REJECTED、调整说明） */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdTime;

}