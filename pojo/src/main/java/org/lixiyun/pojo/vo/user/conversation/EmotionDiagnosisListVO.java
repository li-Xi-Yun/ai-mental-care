package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "情感诊断书列表展示 VO")
public class EmotionDiagnosisListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "诊断书主键 ID")
    private Long id;

    @Schema(description = "会话 ID")
    private Long conversationId;

    @Schema(description = "该诊断数据创建或更新时的轮次")
    private Integer roundNum;

    @Schema(description = "记录创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updatedTime;

}