package org.lixiyun.pojo.entity.quota;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI对话配额-全局默认配置表(quota_config)实体类
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "quota_config")
public class QuotaConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资源类型：TEXT */
    public static final int RESOURCE_TYPE_TEXT = 1;
    /** 资源类型：ASR */
    public static final int RESOURCE_TYPE_ASR = 2;
    /** 资源类型：TTS */
    public static final int RESOURCE_TYPE_TTS = 3;
    /** 资源类型：AVATAR_2D */
    public static final int RESOURCE_TYPE_AVATAR_2D = 4;

    /** 重置周期：NONE 终身累计不重置 */
    public static final int CYCLE_TYPE_NONE = 0;
    /** 重置周期：MONTHLY 按自然月重置 */
    public static final int CYCLE_TYPE_MONTHLY = 1;

    /** 限流开关：启用（enabled=1） */
    public static final int ENABLED_YES = 1;
    /** 限流开关：不启用（enabled=0，不限额） */
    public static final int ENABLED_NO = 0;

    /** 可用性开关：可用（available=1，进入限流判断） */
    public static final int AVAILABLE_YES = 1;
    /** 可用性开关：禁用（available=0，整资源不可用，请求被拒） */
    public static final int AVAILABLE_NO = 0;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D */
    private Integer resourceType;

    /** 全局默认次数上限 */
    private Integer defaultLimit;

    /** 是否启用限流：1启用，0不启用(不限额) */
    private Integer enabled;

    /** 资源可用性：1=启用（可用，进入限流判断），0=禁用（整资源不可用，请求被拒） */
    private Integer available;

    /** 重置周期：0=NONE终身累计不重置(当前默认)，1=MONTHLY按自然月重置(预留，套餐化时启用) */
    private Integer cycleType;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 最后更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 资源是否被禁用（available=0）
     *
     * @return true=已禁用，请求应被拒
     */
    public boolean isDisabled() {
        return available != null && available == AVAILABLE_NO;
    }

    /**
     * 资源是否可用（available 缺省视为可用）
     * <p>命名避开 JavaBeans 属性 getter 冲突（字段 available + getAvailable 已存在），
     * 否则 MyBatis Reflector 会因 {@code getAvailable()}/{@code isAvailable()} 返回类型不一致
     * 抛出 “Illegal overloaded getter method with ambiguous type”。</p>
     *
     * @return true=可用
     */
    public boolean isAvailableFlag() {
        return available == null || available == AVAILABLE_YES;
    }

    /**
     * 是否启用限流（enabled 缺省视为启用）
     * <p>命名避开 JavaBeans 属性 getter 冲突（字段 enabled + getEnabled 已存在）。</p>
     *
     * @return true=启用限流
     */
    public boolean isEnabledFlag() {
        return enabled == null || enabled == ENABLED_YES;
    }

    /**
     * 是否按自然月重置周期（cycleType=MONTHLY）
     *
     * @return true=MONTHLY
     */
    public boolean isMonthly() {
        return cycleType != null && cycleType == CYCLE_TYPE_MONTHLY;
    }

    /**
     * 资源类型 → 显示名
     *
     * @param resourceType 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D
     * @return 资源显示名，未知资源返回「未知资源」
     */
    public static String resourceName(Integer resourceType) {
        if (resourceType == null) {
            return "未知资源";
        }
        return switch (resourceType) {
            case RESOURCE_TYPE_TEXT -> "AI文本对话";
            case RESOURCE_TYPE_ASR -> "AI语音识别";
            case RESOURCE_TYPE_TTS -> "AI语音合成";
            case RESOURCE_TYPE_AVATAR_2D -> "AI 2D 形象播报";
            default -> "未知资源";
        };
    }

    /**
     * 当前月度周期起点（当月 1 日 00:00:00）
     * <p>供 MONTHLY 重置周期下计算 {@code quota_user.cycle_start_time} 与跨月判断使用。</p>
     *
     * @return 当月 1 日 00:00:00
     */
    public static LocalDateTime currentPeriodStart() {
        LocalDateTime now = LocalDateTime.now();
        return now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

}