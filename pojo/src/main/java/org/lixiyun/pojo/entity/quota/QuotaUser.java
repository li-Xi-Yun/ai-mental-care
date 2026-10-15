package org.lixiyun.pojo.entity.quota;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI对话配额-用户级使用量与个性化上限表(quota_user)实体类
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "quota_user")
public class QuotaUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 不限量开关：豁免（unlimited=1，该用户此资源不限额、不计数） */
    public static final int UNLIMITED_YES = 1;
    /** 不限量开关：正常限流（unlimited=0） */
    public static final int UNLIMITED_NO = 0;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D */
    private Integer resourceType;

    /** 个性化上限，NULL=跟随全局默认 */
    private Integer customLimit;

    /** 不限量（豁免）：1=该用户此资源不限额、不计数，0=正常限流 */
    private Integer unlimited;

    /** 已用次数（cycle_type=0 为终身累计，1=MONTHLY 为当周期内次数） */
    private Integer usedCount;

    /** 当前周期开始时间；cycle_type=1(MONTHLY) 时用于跨月判断重置，0(NONE) 时保持 NULL */
    private LocalDateTime cycleStartTime;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 最后更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 是否不限量豁免（unlimited=1）
     *
     * @return true=该用户此资源不限额、不计数
     */
    public boolean isUnlimited() {
        return unlimited != null && unlimited == UNLIMITED_YES;
    }

    /**
     * usedCount 空安全取值（用于 VO 构建等场景）
     *
     * @return 若字段为 null 则返回 0，否则返回实际值
     */
    public int usedCountOrDefault() {
        return usedCount == null ? 0 : usedCount;
    }

}