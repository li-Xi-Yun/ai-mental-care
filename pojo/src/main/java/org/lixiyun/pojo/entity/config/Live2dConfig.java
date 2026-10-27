package org.lixiyun.pojo.entity.config;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Live2D模型配置表(Live2dConfig)实体类
 *
 * @author lixiyun
 * @since 2026-10-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "live2d_config", autoResultMap = true)
public class Live2dConfig implements Serializable {

    public static final String NAME = "live2dConfig";

    private static final long serialVersionUID = 1L;

    /** 启用 */
    public static final int ENABLED_YES = 1;
    /** 禁用 */
    public static final int ENABLED_NO = 0;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Live2D模型名称 */
    private String modelName;

    /** 模型别名（中文展示名称，可空） */
    private String aliasName;

    /** .model3.json 服务器相对路径，前端加载入口 */
    private String modelUrl;

    /** 模型包根目录相对路径（.moc3/.exp3/.motion3/纹理所在目录） */
    private String modelRelativePath;

    /** 模型封面图路径 */
    private String coverImagePath;

    /** 是否启用：0-禁用，1-启用（全局唯一启用的互斥） */
    private Integer enabled;

    /** 排序值，越大越靠前 */
    private Integer sort;

    /** 语义解析说明，发给大模型的提示词 */
    private String semanticPrompt;

    /**
     * 动作固定词汇 Map：key=动作 id（白名单，上传解析生成，不可编辑删除），value=中文含义（后台可编辑，可为空串）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> motionList;

    /**
     * 表情固定词汇 Map：key=表情 id（白名单，上传解析生成，不可编辑删除），value=中文含义（后台可编辑，可为空串）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> expressionList;

    /**
     * 待机表情 id 列表（可空；须为 expressionList 白名单的子集，后台编辑时过滤）。未配置（空列表）时前端回退现有待机行为。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> idleExpressionList;

    /**
     * 待机动作 id 列表（可空；须为 motionList 白名单的子集，后台编辑时过滤）。未配置（空列表）时前端回退现有待机行为。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> idleMotionList;

    /** 待机操作间隔（秒）；<=0 或待机列表为空时待机配置不启用，前端回退现有待机行为 */
    private Integer idleIntervalSeconds;

    /**
     * 是否启用 pixi-live2d-display 自带的自动待机（模型自带 Idle 分组）：1-是（库自动播 Idle 组），
     * 0-否（禁用库自动 Idle，仅按 {@link #idleExpressionList}/{@link #idleMotionList}/{@link #idleIntervalSeconds}
     * 后台待机设置播放；后台未设置则静止）
     */
    private Integer idleAutoPlay;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 修改人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 是否删除，0-否，1-是 */
    @TableLogic
    private Integer deleted;

    public boolean isEnabledFlag() {
        return enabled != null && enabled == ENABLED_YES;
    }

    public boolean isDeletedFlag() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}