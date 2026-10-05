package org.lixiyun.pojo.dto.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * 角色查询DTO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Schema(description = "角色查询DTO")
public class SysRoleQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色名称（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;

    @Schema(description = "角色标识（精确匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String roleKey;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "角色状态：0=正常，1=停用", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;
}