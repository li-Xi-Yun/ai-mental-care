package org.lixiyun.pojo.vo.admin.symptom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 症状词典VO
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Schema(description = "症状词典VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SymptomDictVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标准术语ID")
    private Long id;

    @Schema(description = "标准症状名称")
    private String symptomTerm;

    @Schema(description = "症状大类：情绪症状/躯体症状/认知症状/行为症状")
    private String symptomCategory;

    @Schema(description = "同义口语词数组")
    private List<String> synonymWords;

    @Schema(description = "默认严重程度：1=轻度 2=中度 3=重度")
    private String severityDefault;

    @Schema(description = "状态 0：禁用 1：启用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}