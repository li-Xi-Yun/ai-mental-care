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
 * 用户选项表，包含单选与多选(scale_user_answer_option)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleUserAnswerOption implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 scale_user_answer.id */
    private Long answerId;

    /** 选中选项ID */
    private Long optionId;

    /** 选项文本快照 */
    private String optionText;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 是否删除，0-否，1-是 */
    @TableLogic
    private Integer deleted;
    /**
     * 判断选中选项是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}
