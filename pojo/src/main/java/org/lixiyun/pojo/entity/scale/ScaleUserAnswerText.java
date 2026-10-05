package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户填空表(scale_user_answer_text)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleUserAnswerText implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 scale_user_answer.id */
    private Long answerId;

    /** 填空/简答答案文本 */
    private String answerText;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 是否删除，0-否，1-是 */
    @TableLogic
    private Integer deleted;
    /**
     * 判断填空答案是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}
