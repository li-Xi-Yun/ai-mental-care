package org.lixiyun.pojo.vo.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 角色简单信息VO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "角色简单信息VO")
public class SysRoleSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名称")
    private String name;
}