package org.lixiyun.pojo.dto.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "会话元数据修改请求")
public class ConversationInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "会话名称不能为空")
    @Schema(description = "会话名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

}
