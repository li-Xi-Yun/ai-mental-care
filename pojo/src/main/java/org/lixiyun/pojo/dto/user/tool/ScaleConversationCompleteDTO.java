package org.lixiyun.pojo.dto.user.tool;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * AI对话量表-用户作答完成提交 DTO
 * <p>
 * 用户在对话量表卡片点击"已完成/不作答"后提交：
 * 后端按工具ID取回待处理记录，校验归属后读取测评记录与答题明细（明细取库，不由前端传），
 * 已作答时调用分析节点生成分析文本并写回 tool_result，未作答时直接取消本次交互。
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "AI对话量表-用户作答完成提交 DTO")
public class ScaleConversationCompleteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "工具ID不能为空")
    @Schema(description = "工具ID（createScalePendingAction 返回的 toolId，即 conversation_pending_action.id）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
    private Long toolId;

    @NotNull(message = "会话ID不能为空")
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
    private Long conversationId;

    @Schema(description = "测评记录ID（scale_user_record.id），未作答时可为空；为空时后端回退取 action_data 中已保存的记录ID",
            example = "789")
    private Long recordId;

    @NotNull(message = "是否作答不能为空")
    @NumberOfRanges(min = 0, max = 1, message = "是否作答只能是0-未作答或1-已作答")
    @Schema(description = "用户是否作答：0-未作答（放弃） 1-已作答（完成）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer answered;

}
