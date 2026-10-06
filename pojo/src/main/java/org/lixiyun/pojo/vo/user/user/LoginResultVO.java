package org.lixiyun.pojo.vo.user.user;

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

    @Schema(defaultValue = "对应的请求头名称")
    private String headerName;

    @Schema(description = "该登录账号的角色信息")
    private List<String> roles;

    @Schema(description = "登录账号的简要信息")
    private LoginUserInfoVO userInfo;

}