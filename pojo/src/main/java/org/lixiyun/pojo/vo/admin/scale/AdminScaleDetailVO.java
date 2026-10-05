package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 量表详情VO（在 {@link AdminScaleVO} 基础上增加版本列表）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "量表详情VO")
public class AdminScaleDetailVO extends AdminScaleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表全部版本列表")
    private List<AdminScaleVersionVO> versions;
}