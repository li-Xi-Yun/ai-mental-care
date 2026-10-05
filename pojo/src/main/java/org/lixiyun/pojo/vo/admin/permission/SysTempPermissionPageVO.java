package org.lixiyun.pojo.vo.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 临时权限记录分页VO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "临时权限记录分页VO")
public class SysTempPermissionPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long personId;

    @Schema(description = "临时权限记录列表")
    private List<TempPermissionVO> itemList;

}