package org.lixiyun.pojo.dto.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * 临时权限查询DTO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Schema(description = "临时权限用户分页查询DTO")
public class SysTempPermissionQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "人员ID，可选")
    private Long personId;

    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销")
    private Integer status;
}