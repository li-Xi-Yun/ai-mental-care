package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;
import org.lixiyun.pojo.entity.scale.Scale;

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
}
