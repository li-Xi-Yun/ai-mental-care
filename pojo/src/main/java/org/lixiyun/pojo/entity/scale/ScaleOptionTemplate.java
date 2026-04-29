package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 题目选项模板表(ScaleOptionTemplate)实体类
 *
 * @author lixiyun
 * @since 2026-04-20 21:25:48
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleOptionTemplate implements Serializable {

    private static final long serialVersionUID = 874569401508533201L;

    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 量表id
     */
    private Long scaleId;

    /**
     * 选项描述
     */
    private String optionText;

    /**
     * 该选项对应的原始分数
     */
    private Integer score;

    /**
     * 选项显示顺序
     */
    private Integer sort;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 创建人用户ID
     */
    private Long createdBy;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 最后更新人用户ID
     */
    private Long updatedBy;

    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;

}

