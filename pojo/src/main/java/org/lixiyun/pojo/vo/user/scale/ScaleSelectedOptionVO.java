package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 选中选项快照 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选中选项快照 VO")
public class ScaleSelectedOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "选项ID", example = "501")
    private Long optionId;

    @Schema(description = "选项文本快照", example = "没有或很少时间")
    private String optionText;

}