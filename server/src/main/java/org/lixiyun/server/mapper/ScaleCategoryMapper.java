package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.ScaleCategory;

/**
 * 量表类别(ScaleCategory)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
public interface ScaleCategoryMapper extends BaseMapper<ScaleCategory> {

    /**
     * 更新量表类别的使用次数
     * @param scaleCategoryId 量表类别ID
     * @param useCount 增加的使用次数（可正可负）
     */
    @Update("UPDATE ai_mental_care.scale_category SET use_count = use_count + #{useCount} WHERE id = #{scaleCategoryId}")
    void updateUserCount(Long scaleCategoryId, int useCount);
}
