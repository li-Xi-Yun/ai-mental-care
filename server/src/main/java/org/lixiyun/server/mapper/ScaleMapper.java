package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.Scale;

import java.util.List;

/**
 * 量表主表(Scale)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:15
 */
public interface ScaleMapper extends BaseMapper<Scale> {

    /**
     * 更新量表的题目数量
     * @param scaleId 量表ID
     * @param updateCount 增加的题目数量（可正可负）
     */
    @Update("UPDATE ai_mental_care.scale SET question_count = question_count + #{updateCount} WHERE id = #{scaleId}")
    void updateQuestionCount(Long scaleId, int updateCount);

    /**
     * 查询当前用户有权限的量表列表
     * @param scaleName 量表名称，可模糊匹配
     * @param isDeleted 是否已删除，0-否，1-是
     * @param status 状态：1=启用 0=禁用
     * @return 当前用户有权限的量表列表
     */
    List<Scale> selectMyPage(@Param("scaleName") String scaleName, @Param("isDeleted") Integer isDeleted, @Param("status") Integer status);

    /**
     * 用于管理员查询量表数据
     * @param scaleId 量表ID
     * @return 量表数据 {@link Scale}
     */
    @Select("select * from ai_mental_care.scale where id = #{scaleId}")
    Scale selectMyById(@Param("scaleId") Long scaleId);

    /**
     * 更新量表删除状态
     * @param scale 量表数据
     */
    @Update("UPDATE ai_mental_care.scale SET deleted = #{deleted} WHERE id = #{id}")
    void updateMyDeleteFlat(Scale scale);
}
