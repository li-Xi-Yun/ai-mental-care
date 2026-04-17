package org.lixiyun.pojo.dto.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-04-17 14:12
 */
@Schema(description = "答题项")
@Data
public class AnswerItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID")
    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    @Schema(description = "选项ID")
    @NotNull(message = "选项ID不能为空")
    private Long optionId;
}
