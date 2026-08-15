package org.lixiyun.pojo.dto.admin.symptom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * 症状词典分页查询DTO
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Data
@Schema(description = "症状词典分页查询DTO")
public class AdminSymptomDictQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "症状名称（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String symptomTerm;

    @Schema(description = "症状大类", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String symptomCategory;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "状态：0=禁用，1=启用", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;
}