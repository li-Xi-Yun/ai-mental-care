package org.lixiyun.pojo.vo.admin.quota;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI对话配额-用户级配额VO
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Schema(description = "AI对话配额-用户级配额VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuotaUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D", example = "1")
    private Integer resourceType;

    @Schema(description = "资源名称", example = "AI文本对话")
    private String resourceName;

    @Schema(description = "个性化上限，NULL=跟随全局默认", example = "10")
    private Integer customLimit;

    @Schema(description = "不限量（豁免）：1=该用户此资源不限额、不计数，0=正常限流", example = "0")
    private Integer unlimited;

    @Schema(description = "资源可用性：1=启用，0=禁用", example = "1")
    private Integer available;

    @Schema(description = "已用次数", example = "3")
    private Integer usedCount;

    @Schema(description = "生效上限（unlimited=1 或 available=0 或 enabled=0 时为 NULL）", example = "8")
    private Integer effectiveLimit;

    @Schema(description = "剩余次数（unlimited=1 或不可用时为 NULL，前端显示「不限量/已停用」）", example = "5")
    private Integer remaining;
}