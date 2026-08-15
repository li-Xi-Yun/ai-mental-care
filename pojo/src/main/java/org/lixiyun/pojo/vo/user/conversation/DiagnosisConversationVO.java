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
@Schema(description = "拥有诊断数据的会话列表展示 VO")
public class DiagnosisConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话 ID")
    private Long conversationId;

    @Schema(description = "会话名称")
    private String conversationName;

    @Schema(description = "最新诊断书的轮次")
    private Integer latestRoundNum;

    @Schema(description = "该会话下的诊断数据量")
    private Integer diagnosisCount;

    @Schema(description = "诊断书最后更新时间")
    private LocalDateTime diagnosisUpdatedTime;

    @Schema(description = "会话创建时间")
    private LocalDateTime conversationCreatedTime;

}