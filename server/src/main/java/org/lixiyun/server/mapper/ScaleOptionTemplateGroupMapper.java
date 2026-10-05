package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.ScaleOptionTemplateGroup;

/**
 * 题目选项模板组 数据访问层
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleOptionTemplateGroupMapper extends BaseMapper<ScaleOptionTemplateGroup> {

    /**
     * 级联恢复指定版本下的选项模板组
     *
     * @param versionId 版本ID
     * @return 影响行数
     */
    @Update("UPDATE scale_option_template_group SET deleted = 0, updated_time = NOW() " +
            "WHERE scale_version_id = #{versionId}")
    int restoreByVersionId(@Param("versionId") Long versionId);

    /**
     * 级联恢复指定量表全部版本下的选项模板组
     *
     * @param scaleId 量表ID
     * @return 影响行数
     */
    @Update("UPDATE scale_option_template_group SET deleted = 0, updated_time = NOW() " +
            "WHERE scale_version_id IN (SELECT id FROM scale_version WHERE scale_id = #{scaleId})")
    int restoreByScaleId(@Param("scaleId") Long scaleId);

}