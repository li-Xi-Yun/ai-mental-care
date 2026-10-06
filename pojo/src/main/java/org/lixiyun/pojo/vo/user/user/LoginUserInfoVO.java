package org.lixiyun.pojo.vo.user.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录结果中附带的用户简要信息
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@Schema(description = "登录用户简要信息")
public class LoginUserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "账号名")
    private String loginAccount;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户头像URL，仅用户端返回")
    private String avatar;
}