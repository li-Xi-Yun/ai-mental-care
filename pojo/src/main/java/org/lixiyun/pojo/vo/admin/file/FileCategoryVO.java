package org.lixiyun.pojo.vo.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件分类VO
 *
 * @author lixiyun
 * @since 2026-05-01
 */
@Schema(description = "文件分类VO")
@Data
public class FileCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "父级类目ID，0=一级分类")
    private Long parentId;

    @Schema(description = "人员ID，关联用户/管理员表")
    private Long personId;

    @Schema(description = "类目名称")
    private String categoryName;

    @Schema(description = "是否默认分类：0-否，1-是")
    private Integer defaultType;

    @Schema(description = "该分类下的文件数量")
    private Integer fileCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "更新人ID")
    private Long updatedBy;

    @Schema(description = "子分类列表")
    private List<FileCategoryVO> children;
}
