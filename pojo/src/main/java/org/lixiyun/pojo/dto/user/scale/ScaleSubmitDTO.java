package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 用户提交测评答案DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户提交测评答案DTO")
public class ScaleSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "测评记录ID不能为空")
    @Schema(description = "测评记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long recordId;

    @NotEmpty(message = "答案列表不能为空")
    @Valid
    @Schema(description = "答题明细列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ScaleAnswerItemDTO> answers;

}