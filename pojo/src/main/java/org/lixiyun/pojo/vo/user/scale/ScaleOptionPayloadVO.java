package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 选项载荷 VO（作答下发选项，不含 score）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选项载荷 VO")
public class ScaleOptionPayloadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "选项ID", example = "501")
    private Long optionId;

    @Schema(description = "选项描述", example = "没有或很少时间")
    private String optionText;

    @Schema(description = "选项显示顺序", example = "1")
    private Integer sort;

}