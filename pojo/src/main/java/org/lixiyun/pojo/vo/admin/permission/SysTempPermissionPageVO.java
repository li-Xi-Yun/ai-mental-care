package org.lixiyun.pojo.vo.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "临时权限人员分页VO")
public class SysTempPermissionPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "人员ID")
    private Long personId;

    @Schema(description = "人员名称")
    private String personUsername;
}
