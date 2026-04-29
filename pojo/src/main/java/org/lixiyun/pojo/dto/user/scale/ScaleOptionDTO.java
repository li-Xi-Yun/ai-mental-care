package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;

/**
 * 题目选项DTO
 * 对应数据库表：scale_option
 * 仅接收前端传递的核心业务字段
 * @author lixiyun
 * @since 2026-04-15 08:11
 */
@Schema(description = "题目选项DTO")
@Data
public class ScaleOptionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "选项描述")
    @NotBlank(message = "选项内容不能为空", groups = AddGroup.class)
    private String optionText;

    @Schema(description = "选项对应的分数")
    @NotNull(message = "选项分数不能为空", groups = AddGroup.class)
    private Integer score;

    @Schema(description = "选项显示顺序（前端传递）")
    @NotNull(message = "选项顺序不能为空", groups = AddGroup.class)
    private Integer sort;
}