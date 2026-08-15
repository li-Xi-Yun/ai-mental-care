package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 症状标准术语字典
 * 对应标准症状词典中的单条术语记录，作为归一化映射的目标基准
 *
 * @author lixiyun
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0：禁用 */
    public static final int STATUS_DISABLED = 0;
    /** 1：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 1：轻度 */
    public static final int DEFAULT_SEVERITY = 1;
    /** 2：中度 */
    public static final int MEDIUM_SEVERITY = 2;
    /** 3：重度 */
    public static final int HEAVY_SEVERITY = 3;

    /**
     * 标准术语ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标准症状名称
     */
    private String symptomTerm;

    /**
     * 症状大类：情绪症状/躯体症状/认知症状/行为症状
     */
    private String symptomCategory;

    /**
     * 同义口语词数组，例：["睡不着","躺床上翻来覆去睡不着"]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> synonymWords;

    /**
     * 默认严重程度：1=轻度 2=中度 3=重度
     */
    private String severityDefault;

    /**
     * 状态 0：禁用 1：启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}