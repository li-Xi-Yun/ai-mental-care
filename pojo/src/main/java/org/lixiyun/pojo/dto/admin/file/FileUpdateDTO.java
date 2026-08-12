package org.lixiyun.pojo.dto.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * 文件信息修改DTO
 *
 * @author lixiyun
 * @since 2026-05-01
 */
@Schema(description = "文件信息修改DTO")
@Data
public class FileUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文件ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long id;

    @Schema(description = "文件名（可选）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String fileName;

    @NumberOfRanges
    @Schema(description = "分类ID（可选）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long categoryId;

    @NumberOfRanges(max = 1, min = 0)
    @Schema(description = "是否启用向量检索，0-否，1-是", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer vectorStatus;

    @NumberOfRanges(min = 0, max = 3)
    @Schema(description = "向量库知识类型：0-无，1-症状库，2-诊断标准库，3-干预方案库", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer knowledgeType;

}
