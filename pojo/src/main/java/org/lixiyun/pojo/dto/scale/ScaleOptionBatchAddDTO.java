package org.lixiyun.pojo.dto.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.pojo.dto.user.scale.ScaleOptionDTO;

import java.io.Serializable;
import java.util.List;

/**
 * 批量新增选项DTO
 * @author lixiyun
 * @since 2026-04-21
 */
@Schema(description = "批量新增选项DTO")
@Data
public class ScaleOptionBatchAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID")
    @NotNull(message = "题目ID不能为空", groups = AddGroup.class)
    private Long questionId;

    @Schema(description = "新增的选项列表")
    @NotEmpty(message = "新增的选项列表不能为空", groups = AddGroup.class)
    @Valid
    private List<ScaleOptionDTO> options;
}
