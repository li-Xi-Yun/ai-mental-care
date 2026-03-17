package org.lixiyun.pojo.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "登录结果")
public class LoginResultVO implements java.io.Serializable{

    private static final long serialVersionUID = 1L;

    @Schema(description = "登录后的token信息")
    private String token;

    @Schema(description = "该管理员的角色信息")
    private List<String> roles;

    @Schema(description = "该管理员的权限信息")
    private List<String> permissions;

}