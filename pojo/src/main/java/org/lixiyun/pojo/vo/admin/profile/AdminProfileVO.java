package org.lixiyun.pojo.vo.admin.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-04-18 21:34
 */
@Data
@Schema(description = "管理员个人资料")
public class AdminProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "管理员ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "手机号码")
    private String mobile;

    @Schema(description = "邮箱地址")
    private String email;

}
