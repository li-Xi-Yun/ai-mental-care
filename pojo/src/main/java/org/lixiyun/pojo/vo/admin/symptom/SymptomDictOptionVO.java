package org.lixiyun.pojo.vo.admin.symptom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 症状词典下拉选项VO（按分类分组）
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

    @Schema(description = "症状大类")
    private String symptomCategory;

}