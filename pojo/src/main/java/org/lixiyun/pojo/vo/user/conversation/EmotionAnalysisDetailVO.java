package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-03-19 33:16
 */
@Data
@Builder
@Schema(description = "情绪分析详情（包含对话）")
public class EmotionAnalysisDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "思考内容")
    private String modelContent;

    @Schema(description = "模型内容")
    private String userContent;

    @Schema(description = "用户内容")
    private String thinkContent;

}
