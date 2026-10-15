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

}