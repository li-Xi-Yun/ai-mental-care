package org.lixiyun.pojo.dto.admin.symptom;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;
import java.util.List;

/**
 * 症状词典DTO（新增/修改共用）
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Schema(description = "症状词典DTO")
@Data
public class SymptomDictDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标准症状名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标准症状名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private String symptomTerm;

    @Schema(description = "症状大类：情绪症状/躯体症状/认知症状/行为症状", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "症状大类不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private String symptomCategory;

    @NotEmpty(message = "同义口语词数组不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "同义口语词数组，例：[\"睡不着\",\"躺床上翻来覆去睡不着\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> synonymWords;

    @Schema(description = "默认严重程度：1=轻度 2=中度 3=重度", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String severityDefault;

    @Schema(description = "状态 0：禁用 1：启用", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;
}