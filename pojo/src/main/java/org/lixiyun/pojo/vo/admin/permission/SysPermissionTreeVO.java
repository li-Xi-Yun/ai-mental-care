package org.lixiyun.pojo.vo.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 权限树形VO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "权限树形VO")
public class SysPermissionTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "权限分组名称")
    private String groupName;

    @Schema(description = "该分组下的权限列表")
    private List<PermissionItem> permissions;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "权限项")
    public static class PermissionItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "权限ID")
        private Long id;

        @Schema(description = "权限标识符")
        private String perms;

        @Schema(description = "权限名称")
        private String name;

        @Schema(description = "状态：0=正常，1=停用")
        private Integer status;
    }
}