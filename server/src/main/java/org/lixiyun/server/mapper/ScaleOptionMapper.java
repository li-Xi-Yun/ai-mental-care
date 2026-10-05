package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.ScaleOption;

/**
 * 题目选项 数据访问层
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleOptionMapper extends BaseMapper<ScaleOption> {

    /**
     * 级联恢复指定版本下的选项
     *
     * @param versionId 版本ID
     * @return 影响行数
     */
    @Update("UPDATE scale_option SET deleted = 0, updated_time = NOW() " +
            "WHERE question_id IN (SELECT id FROM scale_question WHERE scale_version_id = #{versionId})")
    int restoreByVersionId(@Param("versionId") Long versionId);

    /**
     * 级联恢复指定量表全部版本下的选项
     *
     * @param scaleId 量表ID
     * @return 影响行数
     */
    @Update("UPDATE scale_option SET deleted = 0, updated_time = NOW() " +
            "WHERE question_id IN (SELECT id FROM scale_question " +
            "WHERE scale_version_id IN (SELECT id FROM scale_version WHERE scale_id = #{scaleId}))")
    int restoreByScaleId(@Param("scaleId") Long scaleId);

}