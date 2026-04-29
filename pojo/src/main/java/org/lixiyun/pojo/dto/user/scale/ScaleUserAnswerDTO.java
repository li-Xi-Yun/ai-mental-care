package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户测评答题DTO
 * 用于用户提交测评答案
 * @author lixiyun
 * @since 2026-04-17
 */
@Schema(description = "用户测评答题DTO")
@Data
public class ScaleUserAnswerDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表ID")
    @NotNull(message = "量表ID不能为空")
    private Long scaleId;

    @Schema(description = "答题列表")
    @NotEmpty(message = "答题列表不能为空")
    private List<AnswerItemDTO> answers;

}
