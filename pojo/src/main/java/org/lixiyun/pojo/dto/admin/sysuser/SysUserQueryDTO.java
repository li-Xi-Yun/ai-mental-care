package org.lixiyun.pojo.dto.admin.sysuser;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * 系统用户查询DTO
 *
 * @author lixiyun
 * @since 2026-07-30
 */
@Schema(description = "系统用户查询DTO")
@Data
public class SysUserQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "查询关键词（支持用户名、登录账号名、手机号、邮箱）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String keyword;

    @NumberOfRanges(min = 0, max = 3)
    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;
}