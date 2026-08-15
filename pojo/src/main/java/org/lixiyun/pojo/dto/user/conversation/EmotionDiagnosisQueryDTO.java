package org.lixiyun.pojo.dto.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "情感诊断书分页查询 DTO（用户个人中心）")
public class EmotionDiagnosisQueryDTO extends PageBaseDTO {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话名称（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String conversationName;

}