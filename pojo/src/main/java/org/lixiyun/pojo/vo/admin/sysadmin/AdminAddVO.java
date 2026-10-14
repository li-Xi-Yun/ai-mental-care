package org.lixiyun.pojo.vo.admin.sysadmin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员新增结果VO
 *
 * <p>仅在创建管理员的响应中一次性返回，供创建者（超级管理员）将账号转交新管理员。
 * 不包含任何密码信息。</p>
 *
 * @author lixiyun
 * @since 2026-10-13
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "管理员新增结果VO")
public class AdminAddVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "管理员ID")
    private Long id;

    @Schema(description = "登录账号（系统自动生成，仅创建响应一次性返回）")
    private String loginAccount;

    @Schema(description = "管理员用户名")
    private String username;
}
