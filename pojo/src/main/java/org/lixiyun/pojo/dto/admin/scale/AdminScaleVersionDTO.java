package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;

/**
 * 量表版本新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表版本新增/修改DTO")
public class AdminScaleVersionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本ID（更新时必填）", example = "1")
    private Long id;

    @NotNull(message = "量表ID不能为空", groups = {AddGroup.class})
    @Schema(description = "量表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleId;

    @NotBlank(message = "版本号不能为空", groups = {AddGroup.class})
    @Schema(description = "版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "v1.0")
    private String versionNo;

    @Schema(description = "量表说明、指导语", example = "请根据最近一周实际情况作答")
    private String description;

    @Schema(description = "量表版权/授权说明", example = "© 2026 某机构")
    private String copyrightInfo;
}