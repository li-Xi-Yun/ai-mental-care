package org.lixiyun.pojo.dto.admin.quota;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * AI对话配额-全局默认配置更新DTO
 * <p>全字段可空（局部更新）：仅更新传入字段，未传字段保持原值</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Data
@Schema(description = "AI对话配额-全局默认配置更新DTO")
public class QuotaConfigUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NumberOfRanges(min = 0, max = 99999)
    @Schema(description = "全局默认次数上限", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "8")
    private Integer defaultLimit;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否启用限流：1启用，0不启用(不限额)", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer enabled;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "资源可用性：1=启用（可用，进入限流判断），0=禁用（整资源不可用，请求被拒）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer available;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "重置周期：0=NONE终身累计不重置，1=MONTHLY按自然月重置", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "0")
    private Integer cycleType;

    @Schema(description = "备注", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "AI文本对话默认限流")
    private String remark;
}