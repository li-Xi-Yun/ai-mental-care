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
 * 用户测评记录表(ScaleUserRecord)实体类
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleUserRecord implements Serializable {
    
    private static final long serialVersionUID = 648552664660012737L;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 量表id
     */
    private Long scaleId;

    /**
     * 量表名称(冗余存储)
     */
    private String scaleName;
    
    /**
     * 最终计算总分
     */
    private Integer totalScore;
    
    /**
     * 本次测评结果描述
     */
    private String resultText;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;

}
