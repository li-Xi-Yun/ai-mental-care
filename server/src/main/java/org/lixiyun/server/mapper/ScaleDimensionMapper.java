package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.ScaleDimension;

/**
 * 量表维度 数据访问层
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleDimensionMapper extends BaseMapper<ScaleDimension> {

    /**
     * 级联恢复指定版本下的维度
     *
     * @param versionId 版本ID
     * @return 影响行数
     */
    @Update("UPDATE scale_dimension SET deleted = 0, updated_time = NOW() WHERE scale_version_id = #{versionId}")
    int restoreByVersionId(@Param("versionId") Long versionId);

    /**
     * 级联恢复指定量表全部版本下的维度
     *
     * @param scaleId 量表ID
     * @return 影响行数
     */
    @Update("UPDATE scale_dimension SET deleted = 0, updated_time = NOW() " +
            "WHERE scale_version_id IN (SELECT id FROM scale_version WHERE scale_id = #{scaleId})")
    int restoreByScaleId(@Param("scaleId") Long scaleId);

}