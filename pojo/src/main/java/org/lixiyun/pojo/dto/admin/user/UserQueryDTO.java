package org.lixiyun.pojo.dto.admin.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * 用户查询DTO
 *
 * @author lixiyun
 * @since 2026-04-20
 */
@Schema(description = "用户查询DTO")
@Data
public class UserQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "页码不能为空")
    @NumberOfRanges
    private Integer pageNum;

    @Schema(description = "每页数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "每页数量不能为空")
    @NumberOfRanges
    private Integer pageSize;

    @Schema(description = "用户名（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String username;

    @Schema(description = "用户ID（精确匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long userId;

    @NumberOfRanges(min = 0, max = 3)
    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;
}
