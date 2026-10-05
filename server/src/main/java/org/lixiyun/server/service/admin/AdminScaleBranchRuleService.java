package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.scale.AdminScaleBranchRuleBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleBranchRuleDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleBranchRuleVO;

import java.util.List;

/**
 * 管理员量表跳题规则服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleBranchRuleService {

    /**
     * 查询版本跳题规则列表
     *
     * @param scaleVersionId 量表版本ID
     * @return 跳题规则列表
     */
    List<AdminScaleBranchRuleVO> listRules(Long scaleVersionId);

    /**
     * 新增单条跳题规则
     *
     * @param dto 跳题规则DTO {@link AdminScaleBranchRuleDTO}
     */
    void createRule(AdminScaleBranchRuleDTO dto);

    /**
     * 批量保存跳题规则（整个版本规则集合整体提交，服务端做 diff 更新）
     *
     * @param dto 批量保存DTO {@link AdminScaleBranchRuleBatchDTO}
     */
    void batchSaveRules(AdminScaleBranchRuleBatchDTO dto);

    /**
     * 修改单条跳题规则
     *
     * @param ruleId 规则ID
     * @param dto    跳题规则DTO {@link AdminScaleBranchRuleDTO}
     */
    void updateRule(Long ruleId, AdminScaleBranchRuleDTO dto);

    /**
     * 删除单条跳题规则
     *
     * @param ruleId 规则ID
     */
    void deleteRule(Long ruleId);
}