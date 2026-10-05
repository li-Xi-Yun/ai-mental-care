package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.ScaleVersion;

/**
 * 量表版本 数据访问层
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleVersionMapper extends BaseMapper<ScaleVersion> {

    /**
     * 恢复已删除的量表版本
     *
     * @param id 版本ID
     * @return 影响行数
     */
    @Update("UPDATE scale_version SET deleted = 0, updated_time = NOW() WHERE id = #{id}")
    int restoreVersion(@Param("id") Long id);

    /**
     * 级联恢复指定量表下全部已删除的版本
     *
     * @param scaleId 量表ID
     * @return 影响行数
     */
    @Update("UPDATE scale_version SET deleted = 0, updated_time = NOW() WHERE scale_id = #{scaleId}")
    int restoreVersionsByScaleId(@Param("scaleId") Long scaleId);

}