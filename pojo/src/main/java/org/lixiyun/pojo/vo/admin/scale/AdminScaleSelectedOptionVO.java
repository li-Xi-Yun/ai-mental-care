package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户选中选项VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户选中选项VO")
public class AdminScaleSelectedOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "选中选项ID", example = "1")
    private Long optionId;

    @Schema(description = "选项文本快照", example = "是")
    private String optionText;
}