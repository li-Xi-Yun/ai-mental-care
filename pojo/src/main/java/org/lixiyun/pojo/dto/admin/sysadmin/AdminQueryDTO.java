 package org.lixiyun.pojo.dto.admin.sysadmin;

 import io.swagger.v3.oas.annotations.media.Schema;
 import lombok.Data;
 import org.lixiyun.common.validation.annotation.NumberOfRanges;
 import org.lixiyun.pojo.dto.base.PageBaseDTO;

 import java.io.Serializable;

/**
 * 管理员查询DTO
 *
 * @author lixiyun
 * @since 2026-04-20
 */
@Data
@Schema(description = "管理员查询DTO")
public class AdminQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "查询关键词（支持用户名、账号名、手机号、邮箱）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String keyword;

    @NumberOfRanges(min = 0, max = 3)
    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否删除：0=未删除，1=已删除", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer deleted;
}