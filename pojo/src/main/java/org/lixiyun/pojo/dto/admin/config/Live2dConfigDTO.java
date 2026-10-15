package org.lixiyun.pojo.dto.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;

/**
 * Live2D模型配置新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Data
@Schema(description = "Live2D模型配置新增/修改DTO")
public class Live2dConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "模型名称不能为空", groups = {AddGroup.class})
    @Schema(description = "模型名称（上传时已确定，一般不修改）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "hiyori")
    private String modelName;

    @Schema(description = ".model3.json 服务器相对路径，前端加载入口（一般由上传接口解析生成，不带前导斜杠）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "static-resources/models/hiyori/hiyori.model3.json")
    private String modelUrl;

    @Schema(description = "模型包根目录相对路径（.moc3/.exp3/.motion3/纹理所在目录）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "static-resources/models/hiyori/")
    private String modelRelativePath;

    @Schema(description = "模型封面图路径（管理端列表 + 用户端预览共用）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "static-resources/models/hiyori/hiyori.png")
    private String coverImagePath;

    @Schema(description = "语义解析说明，发给大模型的提示词（LLM 依据它从固定词中选表情/动作；可选，未填则保持原值）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "F01=平静，F02=关切，F03=鼓励")
    private String semanticPrompt;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否启用：0-禁用 1-启用（全局唯一启用互斥，一般走启用接口切换）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "0")
    private Integer enabled;

    @Schema(description = "排序值，越大越靠前", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer sort;

    @Schema(description = "备注信息", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "默认虚拟形象")
    private String remark;

    @NotNull(message = "记录ID不能为空", groups = {UpdateGroup.class})
    @Schema(description = "记录ID（更新时必传）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;
}