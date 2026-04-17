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
 * 用户答题明细表(ScaleUserAnswer)实体类
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleUserAnswer implements Serializable {
    
    private static final long serialVersionUID = -50944775420950510L;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 关联的测评记录ID
     */
    private Long recordId;
    
    /**
     * 题目ID
     */
    private Long questionId;
    
    /**
     * 用户选择的选项ID
     */
    private Long optionId;

    /**
     * 题目显示顺序(冗余存储)
     */
    private Integer sort;
    
    /**
     * 答题时的题目内容(冗余存储)
     */
    private String questionTitle;
    
    /**
     * 答题时的选项内容(冗余存储)
     */
    private String optionText;
    
    /**
     * 选项得分(冗余存储)
     */
    private Integer originalScore;
    
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

