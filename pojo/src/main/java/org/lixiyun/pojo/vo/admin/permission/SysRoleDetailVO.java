package org.lixiyun.pojo.vo.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色详情VO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "角色详情VO")
public class SysRoleDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色标识")
    private String roleKey;

    @Schema(description = "角色名称")
    private String name;

    @Schema(description = "角色状态：0=正常，1=停用")
    private Integer status;

    @Schema(description = "备注信息")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "创建人名称")
    private String createdByName;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "更新人ID")
    private Long updatedBy;

    @Schema(description = "更新人名称")
    private String updatedByName;

}