package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.ScaleCategory;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleCategoryVO;

/**
 * 量表类别 数据访问层
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleCategoryMapper extends BaseMapper<ScaleCategory> {

    /**
     * 分页查询量表类别（支持已删除数据筛选，使用原生 SQL）
     *
     * @param page         分页参数
     * @param categoryName 类别名称，模糊匹配
     * @param deletedFlag  删除状态：0-未删除 1-已删除，为空查全部
     * @return 类别分页结果
     */
    Page<AdminScaleCategoryVO> pageCategories(Page<AdminScaleCategoryVO> page,
                                              @Param("categoryName") String categoryName,
                                              @Param("deletedFlag") Integer deletedFlag);

    /**
     * 恢复已删除的量表类别
     *
     * @param id 类别ID
     * @return 影响行数
     */
    @Update("UPDATE scale_category SET deleted = 0, updated_time = NOW() WHERE id = #{id}")
    int restoreCategory(@Param("id") Long id);

}