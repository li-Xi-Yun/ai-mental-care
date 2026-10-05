package org.lixiyun.pojo.vo.admin.symptom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 症状词典下拉选项VO（按分类分组）
 * <p>用于前端按症状大类分组的级联/分组选择框</p>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Schema(description = "症状词典下拉选项VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SymptomDictOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "症状大类：情绪症状/躯体症状/认知症状/行为症状", example = "情绪症状")
    private String symptomCategory;

    @Schema(description = "该分类下的启用症状列表")
    private List<SymptomDictOptionItemVO> terms;
}