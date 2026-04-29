package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;

/**
 * 量表题目表(ScaleQuestion)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
public interface ScaleQuestionMapper extends BaseMapper<ScaleQuestion> {

    /**
     * 批量删除题目
     *
     * @param questionId 题目ID
     */
    @Delete("delete from ai_mental_care.scale_question where id = #{questionId}")
    void deletePhysics(@Param("questionId") Long questionId);

    /**
     * 批量恢复题目
     *
     * @param scaleId 量表ID
     * @param deleteFlag 删除状态
     */
    @Delete("update ai_mental_care.scale_question set deleted = #{deleteFlag} where scale_id = #{scaleId}")
    void retractDelete(@Param("scaleId") Long scaleId, @Param("deleteFlag") int deleteFlag);

    /**
     * 根据ID查询题目，无论是否删除
     *
     * @param questionId 题目ID
     * @return 题目
     */
    @Select("select * from ai_mental_care.scale_question where id = #{questionId}")
    ScaleQuestion selectMyById(@Param("questionId") Long questionId);
}
