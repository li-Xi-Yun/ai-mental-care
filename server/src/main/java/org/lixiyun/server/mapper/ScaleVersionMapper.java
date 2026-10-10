package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.ScaleVersion;

import java.util.List;

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

    /**
     * 不过滤软删行，统计指定量表下指定版本号的数量（含已删除记录）
     * <p>
     * 因为 {@link com.baomidou.mybatisplus.annotation.TableLogic} 会自动拼接 deleted=0，
     * 而数据库唯一索引 (scale_id, version_no) 覆盖所有行（含软删行），
     * 创建/复制版本时需要此方法做预检，避免唯一索引冲突。
     *
     * @param scaleId   量表ID
     * @param versionNo 版本号
     * @return 版本数量（含已删除）
     */
    @Select("SELECT COUNT(*) FROM scale_version WHERE scale_id = #{scaleId} AND version_no = #{versionNo}")
    Long countByScaleIdAndVersionNoIncludingDeleted(@Param("scaleId") Long scaleId,
                                                     @Param("versionNo") String versionNo);

    /**
     * 不过滤软删行，查询指定量表下指定版本号的版本列表（含已删除记录）
     *
     * @param scaleId   量表ID
     * @param versionNo 版本号
     * @return 版本列表（含已删除）
     */
    @Select("SELECT * FROM scale_version WHERE scale_id = #{scaleId} AND version_no = #{versionNo}")
    List<ScaleVersion> selectByScaleIdAndVersionNoIncludingDeleted(@Param("scaleId") Long scaleId,
                                                                    @Param("versionNo") String versionNo);

}