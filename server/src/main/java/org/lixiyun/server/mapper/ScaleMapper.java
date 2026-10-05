package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleVO;

/**
 * 量表主表 数据访问层
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleMapper extends BaseMapper<Scale> {

    /**
     * 分页查询量表主表（支持已删除数据筛选与当前版本统计，使用原生 SQL）
     *
     * @param page            分页参数
     * @param scaleName       量表名称，模糊匹配
     * @param scaleCategoryId 量表分类ID
     * @param status          状态：0-禁用 1-启用
     * @param deletedFlag     删除状态：0-未删除 1-已删除，为空查全部
     * @return 量表分页结果
     */
    Page<AdminScaleVO> pageScales(Page<AdminScaleVO> page,
                                  @Param("scaleName") String scaleName,
                                  @Param("scaleCategoryId") Long scaleCategoryId,
                                  @Param("status") Integer status,
                                  @Param("deletedFlag") Integer deletedFlag);

    /**
     * 恢复已删除的量表主表
     *
     * @param id 量表ID
     * @return 影响行数
     */
    @Update("UPDATE scale SET deleted = 0, updated_time = NOW() WHERE id = #{id}")
    int restoreScale(@Param("id") Long id);

}