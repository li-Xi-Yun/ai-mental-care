package org.lixiyun.pojo.vo.user.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户信息VO")
public class UserSimpleInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "用户ID")
        private Long id;

        @Schema(description = "用户名")
        private String username;

        @Schema(description = "用户头像URL")
        private String avatar;

        @Schema(description = "用户简介")
        private String introduction;

        @Schema(description = "用户性别: 0-女 1-男, 2-未知")
        private Integer gender;

        @Schema(description = "用户状态: 0-禁用 1-启用")
        private Integer status;
}