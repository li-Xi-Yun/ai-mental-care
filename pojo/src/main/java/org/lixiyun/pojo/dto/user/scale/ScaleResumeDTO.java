package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 续答测评 DTO
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Data
@Schema(description = "续答测评 DTO")
public class ScaleResumeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "测评记录ID不能为空")
    @Schema(description = "测评记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "789")
    private Long recordId;

}
