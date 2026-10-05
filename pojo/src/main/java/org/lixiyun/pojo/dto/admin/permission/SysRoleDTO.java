package org.lixiyun.pojo.dto.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;

/**
 * 角色DTO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Schema(description = "角色DTO")
public class SysRoleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "角色标识不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Length(min = 2, max = 20, message = "角色标识长度为2-20个字符", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "角色标识（如admin/user/VIP），唯一且非空", requiredMode = Schema.RequiredMode.REQUIRED)
    private String roleKey;

    @NotBlank(message = "角色名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Length(min = 2, max = 20, message = "角色名称长度为2-20个字符", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Length(max = 200, message = "备注长度不能超过200个字符", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "备注信息", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String remark;

}