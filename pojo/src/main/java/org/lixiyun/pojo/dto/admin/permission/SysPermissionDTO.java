package org.lixiyun.pojo.dto.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;

/**
 * 权限DTO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Schema(description = "权限DTO")
public class SysPermissionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "权限标识符不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Length(min = 2, max = 50, message = "权限标识符长度为2-50个字符", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "权限标识符（如：video:add、user:list）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String perms;

    @NotBlank(message = "权限名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Length(min = 2, max = 20, message = "权限名称长度为2-20个字符", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "权限名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "权限分组名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Length(min = 2, max = 20, message = "权限分组名称长度为2-20个字符", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "权限分组名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String groupName;

    @Length(max = 200, message = "备注长度不能超过200个字符", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "备注信息", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String remark;
}