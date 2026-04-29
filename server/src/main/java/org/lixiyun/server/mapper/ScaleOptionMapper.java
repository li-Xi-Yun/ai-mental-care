package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.scale.ScaleOption;

import java.util.List;

/**
 * 题目选项表(ScaleOption)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
public interface ScaleOptionMapper extends BaseMapper<ScaleOption> {

    /**
     * 批量逻辑删除选项
     *
     * @param questionId 问题ID
     * @param optionIds  选项ID列表
     */
    void deletePhysicsWithOptions(@Param("questionId") Long questionId, @Param("optionIds") List<Long> optionIds);

    /**
     * 批量逻辑删除选项
     *
     * @param questionId 问题ID
     */
    @Delete("delete from ai_mental_care.scale_option where question_id = #{questionId}")
    void deletePhysics(@Param("questionId") Long questionId);

    /**
     * 批量恢复选项
     *
     * @param scaleId    量表ID
     * @param deleteFlag 删除状态
     */
    void retractDelete(@Param("scaleId") Long scaleId, @Param("deleteFlag") int deleteFlag);
}
