package org.lixiyun.pojo.vo.admin.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@Schema(description = "管理员登录结果")
public class AdminLoginResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "登录后的token信息")
    private String token;

    @Schema(description = "管理员角色信息")
    private List<String> roles;

    @Schema(description = "管理员简要信息")
    private AdminLoginUserInfoVO adminInfo;
}
