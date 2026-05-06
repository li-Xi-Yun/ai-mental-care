package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.file.InfraFileCategory;

import java.util.List;

/**
 * 文件分类表(InfraFileCategory)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-05-01 00:00:00
 */
public interface InfraFileCategoryMapper extends BaseMapper<InfraFileCategory> {

    /**
     * 批量删除分类
     *
     * @param ids 分类ID列表
     * @return 影响行数
     */
    int deleteBatchByIds(@Param("ids") List<Long> ids);

    /**
     * 更新当前分类下的文件数量
     *
     * @param categoryId 分类ID
     * @param updateCount 更新数量
     */
    @Update("update ai_mental_care.infra_file_category set file_count = file_count + #{updateCount} where id = #{categoryId}")
    void updateMyFileCount(Long categoryId, int updateCount);
}
