package org.lixiyun.pojo.vo.admin.sysadmin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 角色VO
 *
 * @author lixiyun
 * @since 2026-07-30
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "角色VO")
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名(如admin/user/VIP),唯一且非空")
    private String roleKey;

    @Schema(description = "角色名称")
    private String name;

    @Schema(description = "角色状态：0=正常，1=停用")
    private Integer status;

    @Schema(description = "备注信息")
    private String remark;
}