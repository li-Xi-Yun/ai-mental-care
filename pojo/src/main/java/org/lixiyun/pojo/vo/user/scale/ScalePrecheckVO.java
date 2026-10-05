package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 作答前检查VO（前台）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@Schema(description = "作答前检查VO")
public class ScalePrecheckVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否允许开始作答", example = "true")
    private Boolean allowed;

    @Schema(description = "不允许时的原因枚举：SCALE_DISABLED/NO_VERSION/COOLING/REPEAT_LIMITED/UNFINISHED_EXISTS", example = "COOLING")
    private String reason;

    @Schema(description = "若处于冷却期，剩余需等待分钟数（否则为0）", example = "15")
    private Integer coolRemainMinutes;
}