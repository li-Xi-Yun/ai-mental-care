package org.lixiyun.pojo.vo.admin.quota;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI对话配额-全局默认配置VO
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Schema(description = "AI对话配额-全局默认配置VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuotaConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D", example = "1")
    private Integer resourceType;

    @Schema(description = "资源名称", example = "AI文本对话")
    private String resourceName;

    @Schema(description = "全局默认次数上限", example = "8")
    private Integer defaultLimit;

    @Schema(description = "资源可用性：1=启用，0=禁用", example = "1")
    private Integer available;

    @Schema(description = "是否启用限流：1启用，0不启用(不限额)", example = "1")
    private Integer enabled;

    @Schema(description = "重置周期：0=NONE终身累计，1=MONTHLY按自然月重置", example = "0")
    private Integer cycleType;

    @Schema(description = "备注", example = "AI文本对话默认限流")
    private String remark;
}