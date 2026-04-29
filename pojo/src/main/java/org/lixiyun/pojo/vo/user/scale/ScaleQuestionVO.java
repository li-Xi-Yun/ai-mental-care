package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 17:19
 */
@Data
@Schema(description = "量表题目VO")
public class ScaleQuestionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID")
    private Long id;

    @Schema(description = "题目内容")

    private String title;

    @Schema(description = "题目显示顺序")
    private Integer sort;

    @Schema(description = "计分类型 1=正向 2=反向")
    private Integer scoreType;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "创建人")
    private Long createdBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "更新人")
    private Long updatedBy;

    @Schema(description = "选项列表")
    private List<ScaleOptionVO> optionList;

}
