package org.lixiyun.pojo.vo.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户测评记录VO
 * @author lixiyun
 * @since 2026-04-17
 */
@Schema(description = "用户测评记录VO")
@Data
public class ScaleUserRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "测评记录ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "量表ID")
    private Long scaleId;

    @Schema(description = "量表名称")
    private String scaleName;

    @Schema(description = "最终计算总分")
    private Integer totalScore;

    @Schema(description = "本次测评结果描述")
    private String resultText;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
