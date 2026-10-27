package org.lixiyun.pojo.vo.user.live2d;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Live2D 模型用户端 VO（当前启用模型配置）
 * <p>会话初始化时服务端据此返回，前端按 {@code modelUrl} 动态加载模型，按 {@code expressionList}/{@code motionList} 白名单校验收到的资产 id</p>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Schema(description = "Live2D模型用户端配置VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Live2dConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模型配置ID", example = "1")
    private Long modelId;

    @Schema(description = "Live2D模型名称", example = "hiyori")
    private String modelName;

    @Schema(description = ".model3.json 服务器相对路径，前端加载入口（不带前导斜杠，前端 resolveResourceUrl 拼接）", example = "static-resources/models/hiyori/hiyori.model3.json")
    private String modelUrl;

    @Schema(description = "模型封面图路径", example = "static-resources/models/hiyori/hiyori.png")
    private String coverImagePath;

    @Schema(description = "表情固定词汇白名单", example = "[\"F01\",\"F02\",\"F03\"]")
    private List<String> expressionList;

    @Schema(description = "动作固定词汇白名单", example = "[\"haru_g_idle\",\"haru_g_m01\"]")
    private List<String> motionList;

    @Schema(description = "待机表情id列表（白名单子集；空数组/0 表示未启用待机配置）", example = "[\"F01\",\"F05\"]")
    private List<String> idleExpressionList;

    @Schema(description = "待机动作id列表（白名单子集；空数组/0 表示未启用待机配置）", example = "[\"haru_g_idle\"]")
    private List<String> idleMotionList;

    @Schema(description = "待机操作间隔（秒）；0=不启用", example = "10")
    private Integer idleIntervalSeconds;

    @Schema(description = "是否启用pixi-live2d-display自带自动待机(模型Idle组)：1-是(库自动播)，0-否(按后台待机设置；未设置则静止)", example = "1")
    private Integer idleAutoPlay;
}