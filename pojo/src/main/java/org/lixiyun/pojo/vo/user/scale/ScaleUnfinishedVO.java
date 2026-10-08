package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 查询未完成测评记录 VO（通用量表入口用）
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "查询未完成测评记录 VO")
public class ScaleUnfinishedVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否存在未完成的测评记录", example = "true")
    private Boolean hasUnfinished;

    @Schema(description = "未完成测评记录ID（hasUnfinished=true 时有效）", example = "789")
    private Long recordId;

    @Schema(description = "该量表总题数（hasUnfinished=true 时有效，用于前端弹窗文案）", example = "20")
    private Integer questionCount;

    @Schema(description = "上次开始作答时间（hasUnfinished=true 时有效）", example = "2026-10-07T10:00:00")
    private LocalDateTime startTime;

}
