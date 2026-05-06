package org.lixiyun.pojo.dto.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件查询DTO
 *
 * @author lixiyun
 * @since 2026-05-01
 */
@Schema(description = "文件查询DTO")
@Data
public class FileQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "页码不能为空")
    @NumberOfRanges
    private Integer pageNum;

    @Schema(description = "每页数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "每页数量不能为空")
    @NumberOfRanges
    private Integer pageSize;

    @Schema(description = "分类ID，没有查所有文件信息", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long categoryId;

    @Schema(description = "文件名（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String fileName;

    @NumberOfRanges(min = 0, max = 3)
    @Schema(description = "文件状态：0-待解析，1-解析中，2-解析失败，3-解析完成", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;

    @Schema(description = "开始时间（查询创建时间大于等于该时间的文件）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDateTime startTime;

    @Schema(description = "结束时间（查询创建时间小于等于该时间的文件）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDateTime endTime;

}
