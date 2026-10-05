package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户开始测评DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Schema(description = "用户开始测评DTO")
public class ScaleStartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "量表ID不能为空")
    @Schema(description = "量表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleId;
}