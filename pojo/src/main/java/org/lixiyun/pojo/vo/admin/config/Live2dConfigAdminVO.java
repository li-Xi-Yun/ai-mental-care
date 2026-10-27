package org.lixiyun.pojo.vo.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Live2D 模型配置管理端 VO（列表/详情）
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Schema(description = "Live2D模型配置管理端VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Live2dConfigAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "自增主键ID", example = "1")
    private Long id;

    @Schema(description = "Live2D模型名称", example = "hiyori")
    private String modelName;

    @Schema(description = "模型别名（中文展示名称，可空）", example = "小阳")
    private String aliasName;

    @Schema(description = ".model3.json 服务器相对路径，前端加载入口（不带前导斜杠）", example = "static-resources/models/hiyori/hiyori.model3.json")
    private String modelUrl;

    @Schema(description = "模型包根目录相对路径", example = "static-resources/models/hiyori/")
    private String modelRelativePath;

    @Schema(description = "模型封面图路径", example = "static-resources/models/hiyori/hiyori.png")
    private String coverImagePath;

    @Schema(description = "是否启用：0-禁用 1-启用（全局唯一启用的互斥）", example = "1")
    private Integer enabled;

    @Schema(description = "排序值，越大越靠前", example = "1")
    private Integer sort;

    @Schema(description = "语义解析说明，发给大模型的提示词", example = "F01=平静，F02=关切，F03=鼓励")
    private String semanticPrompt;

    @Schema(description = "动作固定词汇Map：key=动作id（白名单固定），value=中文含义", example = "{\"haru_g_idle\":\"待机\"}")
    private Map<String, String> motionList;

    @Schema(description = "表情固定词汇Map：key=表情id（白名单固定），value=中文含义", example = "{\"F01\":\"平静\"}")
    private Map<String, String> expressionList;

    @Schema(description = "待机表情id列表（白名单子集；空数组/0 表示未启用待机配置）", example = "[\"F01\",\"F05\"]")
    private List<String> idleExpressionList;

    @Schema(description = "待机动作id列表（白名单子集；空数组/0 表示未启用待机配置）", example = "[\"haru_g_idle\"]")
    private List<String> idleMotionList;

    @Schema(description = "待机操作间隔（秒）；0=不启用", example = "10")
    private Integer idleIntervalSeconds;

    @Schema(description = "是否启用pixi-live2d-display自带自动待机(模型Idle组)：1-是(库自动播)，0-否(按后台待机设置；未设置则静止)", example = "1")
    private Integer idleAutoPlay;

    @Schema(description = "备注", example = "默认虚拟形象")
    private String remark;

    @Schema(description = "创建时间", example = "2026-10-14 12:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间", example = "2026-10-14 12:00:00")
    private LocalDateTime updatedTime;
}