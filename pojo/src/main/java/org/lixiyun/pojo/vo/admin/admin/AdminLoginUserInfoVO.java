package org.lixiyun.pojo.vo.admin.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
@Schema(description = "管理员登录用户简要信息")
public class AdminLoginUserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "管理员ID")
    private Long id;

    @Schema(description = "账号名")
    private String loginAccount;

    @Schema(description = "用户名")
    private String username;
}
