package org.lixiyun.pojo.vo.admin.symptom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 症状词典下拉选项明细VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Schema(description = "症状词典下拉选项明细VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SymptomDictOptionItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标准术语ID", example = "1")
    private Long id;

    @Schema(description = "标准症状名称", example = "持续情绪低落")
    private String symptomTerm;

    @Schema(description = "默认严重程度：1=轻度 2=中度 3=重度", example = "2")
    private Integer severityDefault;

    @Schema(description = "同义口语词数组", example = "[\"抑郁心境\",\"心情低落\"]")
    private List<String> synonymWords;
}