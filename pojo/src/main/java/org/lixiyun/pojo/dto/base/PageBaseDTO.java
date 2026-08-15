package org.lixiyun.pojo.dto.base;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

@Data
@Schema(description = "分页基础DTO")
public class PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "页码不能为空")
    @NumberOfRanges
    @Schema(description = "当前页码", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer pageNum;

    @NotNull(message = "每页数量不能为空")
    @NumberOfRanges
    @Schema(description = "每页数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer pageSize;

}