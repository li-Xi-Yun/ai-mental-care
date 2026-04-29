package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.user.scale.ScaleResultRuleDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleResultRuleVO;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 15:35
 */
public interface AdminScaleRuleService {

    /**
     * 创建一个新的结果规则。
     *
     * @param dto 包含要创建的规则信息的DTO
     */
    void createRule(ScaleResultRuleDTO dto);

    /**
     * 检索给定量表的结果规则列表。
     *
     * @param scaleId 量表的ID
     * @return 表示规则的ScaleResultRuleVO对象列表
     */
    List<ScaleResultRuleVO> listRules(Long scaleId);

    /**
     * 更新现有的结果规则。
     *
     * @param ruleId  要更新的规则的ID
     * @param dto 包含更新的规则信息的DTO
     */
    void updateRule(Long ruleId, ScaleResultRuleDTO dto);

    /**
     * 根据ID删除结果规则。
     *
     * @param ruleId 要删除的规则的ID
     */
    void deleteRule(Long ruleId);
}
