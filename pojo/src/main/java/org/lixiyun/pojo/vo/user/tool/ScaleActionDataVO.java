package org.lixiyun.pojo.vo.user.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 量表测评交互卡片数据VO（action_data字段，action_type=1）
 * <p>
 * 卡片渲染在对话模型的推荐语下方，仅包含量表基本信息。
 * 推荐理由由对话模型通过 ASSISTANT 消息自然输出，不在此处冗余存储。
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleActionDataVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 量表主表ID
     */
    private Long scaleId;

    /**
     * 量表名称
     */
    private String scaleName;

    /**
     * 题目总数
     */
    private Integer totalQuestions;

    /**
     * 用户本次测评记录ID（scale_user_record.id）
     * <p>用户进入答题页后由前端回写保存，默认 null；用于刷新页面后恢复作答上下文</p>
     */
    private Long recordId;

}