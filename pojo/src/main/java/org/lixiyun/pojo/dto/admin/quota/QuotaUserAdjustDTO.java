package org.lixiyun.pojo.dto.admin.quota;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * AI对话配额-单用户配额调整DTO
 * <p>用于管理员调整指定用户某资源的：个性化上限 / 已用次数 / 不限量豁免</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Data
@Schema(description = "AI对话配额-单用户配额调整DTO")
public class QuotaUserAdjustDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "资源类型不能为空")
    @NumberOfRanges(min = 1, max = 4)
    @Schema(description = "资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer resourceType;

    @Schema(description = "个性化上限，NULL=跟随全局默认（传 null 表示清除个性化）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "10")
    private Integer customLimit;

    @NotNull(message = "已用次数不能为空")
    @NumberOfRanges(min = 0, max = 99999)
    @Schema(description = "已用次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer used;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "不限量（豁免）：1=该用户此资源不限额、不计数，0=正常限流；NULL=不修改豁免状态", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "0")
    private Integer unlimited;
}