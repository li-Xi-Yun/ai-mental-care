package org.lixiyun.pojo.entity.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI节点配置变更历史表(AiNodeConfigHistory)实体类
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_node_config_history", autoResultMap = true)
public class AiNodeConfigHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联ai_node_config.id */
    private Long configId;

    /** 节点唯一标识（冗余存储，便于查询） */
    private String nodeKey;

    /** 修改前的系统提示词 */
    private String oldSystemPrompt;

    /** 修改后的系统提示词 */
    private String newSystemPrompt;

    /** 修改前的模型类型 */
    private String oldModelType;

    /** 修改后的模型类型 */
    private String newModelType;

    /** 修改前的推理参数快照（temperature/topP/maxToken等） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> oldParams;

    /** 修改后的推理参数快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> newParams;

    /** 修改前的版本号 */
    private Integer oldVersion;

    /** 修改后的版本号 */
    private Integer newVersion;

    /** 变更摘要，如"修改了系统提示词"/"调整temperature从0.2到0.4" */
    private String changeSummary;

    /** 变更时间 */
    private LocalDateTime createdTime;

    /** 变更操作人用户ID */
    private Long createdBy;
}