package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.scale.ScaleResultRule;

/**
 * 量表结果规则表(ScaleResultRule)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
public interface ScaleResultRuleMapper extends BaseMapper<ScaleResultRule> {

    /**
     * 物理删除规则数据
     * @param ruleId 规则ID
     */
    @Delete("delete from ai_mental_care.scale_result_rule where id = #{ruleId}")
    void deletePhysics(@Param("ruleId") Long ruleId);

    /**
     * 恢复逻辑删除的规则数据
     * @param scaleId 量表ID
     * @param deleteFlag 删除状态
     */
    @Delete("update ai_mental_care.scale_result_rule set deleted = #{deleteFlag} where scale_id = #{scaleId}")
    void retractDelete(@Param("scaleId") Long scaleId, @Param("deleteFlag") int deleteFlag);
}
