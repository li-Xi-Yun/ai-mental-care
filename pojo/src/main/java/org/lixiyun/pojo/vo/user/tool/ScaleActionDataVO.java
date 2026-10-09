package org.lixiyun.pojo.vo.user.tool;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 量表测评交互卡片数据VO（action_data字段，action_type=1）
 * <p>
 * 卡片渲染在对话模型的推荐语下方，仅包含量表基本信息。
 * 推荐理由由对话模型通过 ASSISTANT 消息自然输出，不在此处冗余存储。
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleActionDataVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "量表主表ID")
    private Long scaleId;

    @Schema(description = "量表名称")
    private String scaleName;

    @Schema(description = "题目总数")
    private Integer totalQuestions;

    @Schema(description = "用户本次测评记录ID（scale_user_record.id）")
    private Long recordId;

}