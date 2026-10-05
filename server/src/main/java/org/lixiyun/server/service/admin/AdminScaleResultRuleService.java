package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.scale.AdminScaleResultRuleBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleResultRuleDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleResultRuleVO;

import java.util.List;

/**
 * 管理员量表结果规则服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleResultRuleService {

    /**
     * 查询版本结果规则列表（按维度分组，dimensionId 为 NULL 的为总分规则）
     *
     * @param scaleVersionId 量表版本ID
     * @return 结果规则列表
     */
    List<AdminScaleResultRuleVO> listRules(Long scaleVersionId);

    /**
     * 新增单条结果规则
     *
     * @param dto 结果规则DTO {@link AdminScaleResultRuleDTO}
     */
    void createRule(AdminScaleResultRuleDTO dto);

    /**
     * 批量保存结果规则（整体维护一个维度的区间集）
     *
     * @param dto 批量保存DTO {@link AdminScaleResultRuleBatchDTO}
     */
    void batchSaveRules(AdminScaleResultRuleBatchDTO dto);

    /**
     * 修改单条结果规则
     *
     * @param ruleId 规则ID
     * @param dto    结果规则DTO {@link AdminScaleResultRuleDTO}
     */
    void updateRule(Long ruleId, AdminScaleResultRuleDTO dto);

    /**
     * 删除单条结果规则
     *
     * @param ruleId 规则ID
     */
    void deleteRule(Long ruleId);
}