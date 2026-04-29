package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.dto.user.scale.ScaleResultRuleDTO;
import org.lixiyun.pojo.entity.scale.ScaleResultRule;
import org.lixiyun.pojo.vo.user.scale.ScaleResultRuleVO;
import org.lixiyun.server.mapper.ScaleResultRuleMapper;
import org.lixiyun.server.service.admin.AdminScaleRuleService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 15:35
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleRuleServiceImpl implements AdminScaleRuleService {

    private final ScaleResultRuleMapper scaleResultRuleMapper;

    @Override
    public void createRule(ScaleResultRuleDTO dto) {
        ScaleResultRule rule = BeanUtil.copyProperties(dto, ScaleResultRule.class);
        scaleResultRuleMapper.insert(rule);
        log.info("量表规则创建成功，ruleId: {}", rule.getId());
    }

    @Override
    public List<ScaleResultRuleVO> listRules(Long scaleId) {
        List<ScaleResultRule> rules = scaleResultRuleMapper.selectList(new LambdaQueryWrapper<ScaleResultRule>()
                .eq(ScaleResultRule::getScaleId, scaleId));
        return StreamUtils.toListVO(rules, ScaleResultRuleVO.class);
    }

    @Override
    public void updateRule(Long ruleId, ScaleResultRuleDTO dto) {
        ScaleResultRule rule = BeanUtil.copyProperties(dto, ScaleResultRule.class);
        rule.setId(ruleId);
        scaleResultRuleMapper.updateById(rule);
        log.info("量表规则修改成功，ruleId: {}", ruleId);
    }

    @Override
    public void deleteRule(Long ruleId) {
        scaleResultRuleMapper.deletePhysics(ruleId);
        log.info("量表规则物理删除成功，ruleId: {}", ruleId);
    }
}
