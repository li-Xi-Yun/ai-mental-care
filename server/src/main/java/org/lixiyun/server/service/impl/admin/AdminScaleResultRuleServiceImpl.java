package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleResultRuleBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleResultRuleDTO;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleResultRule;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleResultRuleVO;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleResultRuleMapper;
import org.lixiyun.server.service.admin.AdminScaleResultRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员量表结果规则服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleResultRuleServiceImpl implements AdminScaleResultRuleService {

    private final ScaleResultRuleMapper scaleResultRuleMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;

    @Override
    public List<AdminScaleResultRuleVO> listRules(Long scaleVersionId) {
        log.info("[AdminScaleResultRuleServiceImpl-查询结果规则列表]，版本ID：{}", scaleVersionId);

        List<ScaleResultRule> rules = scaleResultRuleMapper.selectList(new LambdaQueryWrapper<ScaleResultRule>()
                .eq(ScaleResultRule::getScaleVersionId, scaleVersionId));
        if (CollUtil.isEmpty(rules)) {
            return new ArrayList<>();
        }

        Set<Long> dimensionIds = rules.stream().map(ScaleResultRule::getDimensionId)
                .filter(ObjectUtil::isNotNull).collect(Collectors.toSet());
        Map<Long, String> dimensionNameMap = new HashMap<>();
        if (CollUtil.isNotEmpty(dimensionIds)) {
            dimensionNameMap.putAll(scaleDimensionMapper.selectBatchIds(dimensionIds).stream()
                    .collect(Collectors.toMap(ScaleDimension::getId, ScaleDimension::getDimName, (a, b) -> a)));
        }

        List<AdminScaleResultRuleVO> voList = rules.stream().map(rule -> AdminScaleResultRuleVO.builder()
                        .id(rule.getId())
                        .scaleVersionId(rule.getScaleVersionId())
                        .dimensionId(rule.getDimensionId())
                        .dimensionName(dimensionNameMap.get(rule.getDimensionId()))
                        .minScore(rule.getMinScore())
                        .maxScore(rule.getMaxScore())
                        .resultText(rule.getResultText())
                        .riskLevel(rule.getRiskLevel())
                        .sort(rule.getSort())
                        .createdTime(rule.getCreatedTime())
                        .updatedTime(rule.getUpdatedTime())
                        .build())
                .collect(Collectors.toList());

        log.debug("[AdminScaleResultRuleServiceImpl-查询结果规则列表]完成，规则数：{}", voList.size());
        return voList;
    }

    @Override
    public void createRule(AdminScaleResultRuleDTO dto) {
        log.info("[AdminScaleResultRuleServiceImpl-新增结果规则]，参数：{}", dto);

        validateRule(dto.getScaleVersionId(), dto.getDimensionId(), dto.getMinScore(), dto.getMaxScore(), null);

        ScaleResultRule entity = BeanUtil.copyProperties(dto, ScaleResultRule.class);
        int row = scaleResultRuleMapper.insert(entity);
        if (row == 0) {
            log.error("[AdminScaleResultRuleServiceImpl-新增结果规则]插入失败，参数：{}", dto);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RESULT_RULE_ADD_FAIL);
        }
        log.info("[AdminScaleResultRuleServiceImpl-新增结果规则]成功，规则ID：{}", entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveRules(AdminScaleResultRuleBatchDTO dto) {
        Long scaleVersionId = dto.getScaleVersionId();
        List<AdminScaleResultRuleDTO> rules = dto.getRules();
        log.info("[AdminScaleResultRuleServiceImpl-批量保存结果规则]，版本ID：{}，规则数：{}", scaleVersionId, CollUtil.size(rules));

        // 整体校验提交集合区间不重叠（按维度分组）
        validateBatchRules(rules);

        List<ScaleResultRule> existing = scaleResultRuleMapper.selectList(new LambdaQueryWrapper<ScaleResultRule>()
                .eq(ScaleResultRule::getScaleVersionId, scaleVersionId));
        Set<Long> existingIds = existing.stream().map(ScaleResultRule::getId).collect(Collectors.toSet());
        Set<Long> submitIds = rules.stream().map(AdminScaleResultRuleDTO::getId)
                .filter(ObjectUtil::isNotNull).collect(Collectors.toSet());

        // 新增：id 为空；更新：id 非空且在库中存在
        for (AdminScaleResultRuleDTO rule : rules) {
            if (rule.getId() == null) {
                AdminScaleResultRuleDTO newDto = BeanUtil.copyProperties(rule, AdminScaleResultRuleDTO.class);
                newDto.setScaleVersionId(scaleVersionId);
                createRule(newDto);
            } else if (existingIds.contains(rule.getId())) {
                updateRule(rule.getId(), rule);
            }
        }

        // 删除库中存在但提交集合中已不存在的规则
        for (Long id : existingIds) {
            if (!submitIds.contains(id)) {
                scaleResultRuleMapper.deleteById(id);
            }
        }
        log.info("[AdminScaleResultRuleServiceImpl-批量保存结果规则]完成，版本ID：{}", scaleVersionId);
    }

    @Override
    public void updateRule(Long ruleId, AdminScaleResultRuleDTO dto) {
        log.info("[AdminScaleResultRuleServiceImpl-修改结果规则]，规则ID：{}", ruleId);

        ScaleResultRule original = scaleResultRuleMapper.selectById(ruleId);
        if (original == null) {
            log.error("[AdminScaleResultRuleServiceImpl-修改结果规则]规则不存在，规则ID：{}", ruleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RESULT_RULE_NOT_FOUND);
        }

        // 校验区间合法性，重叠判断排除自身
        validateRule(original.getScaleVersionId(), dto.getDimensionId(), dto.getMinScore(), dto.getMaxScore(), ruleId);

        ScaleResultRule entity = BeanUtil.copyProperties(dto, ScaleResultRule.class);
        entity.setId(ruleId);
        scaleResultRuleMapper.updateById(entity);
        log.info("[AdminScaleResultRuleServiceImpl-修改结果规则]成功，规则ID：{}", ruleId);
    }

    @Override
    public void deleteRule(Long ruleId) {
        log.info("[AdminScaleResultRuleServiceImpl-删除结果规则]，规则ID：{}", ruleId);
        scaleResultRuleMapper.deleteById(ruleId);
        log.info("[AdminScaleResultRuleServiceImpl-删除结果规则]成功，规则ID：{}", ruleId);
    }

    /**
     * 校验结果规则合法性：区间最低分不能大于最高分，且同一版本同一维度下不允许区间重叠
     *
     * @param scaleVersionId 量表版本ID
     * @param dimensionId    维度ID
     * @param minScore       区间最低分
     * @param maxScore       区间最高分
     */
    private void validateRule(Long scaleVersionId, Long dimensionId, BigDecimal minScore, BigDecimal maxScore) {
        validateRule(scaleVersionId, dimensionId, minScore, maxScore, null);
    }

    /**
     * 校验结果规则合法性（更新场景排除自身）
     *
     * @param scaleVersionId 量表版本ID
     * @param dimensionId    维度ID
     * @param minScore       区间最低分
     * @param maxScore       区间最高分
     * @param excludeRuleId  需排除的规则ID（自身），可为空
     */
    private void validateRule(Long scaleVersionId, Long dimensionId, BigDecimal minScore, BigDecimal maxScore, Long excludeRuleId) {
        if (minScore == null || maxScore == null || minScore.compareTo(maxScore) > 0) {
            log.error("[AdminScaleResultRuleServiceImpl-校验结果规则]区间不合法，minScore：{}，maxScore：{}", minScore, maxScore);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RESULT_RULE_RANGE_INVALID);
        }

        LambdaQueryWrapper<ScaleResultRule> wrapper = new LambdaQueryWrapper<ScaleResultRule>()
                .eq(ScaleResultRule::getScaleVersionId, scaleVersionId);
        if (dimensionId == null) {
            wrapper.isNull(ScaleResultRule::getDimensionId);
        } else {
            wrapper.eq(ScaleResultRule::getDimensionId, dimensionId);
        }
        wrapper.ne(excludeRuleId != null, ScaleResultRule::getId, excludeRuleId);

        List<ScaleResultRule> existing = scaleResultRuleMapper.selectList(wrapper);
        for (ScaleResultRule rule : existing) {
            if (checkOverlap(rule.getMinScore(), rule.getMaxScore(), minScore, maxScore)) {
                log.error("[AdminScaleResultRuleServiceImpl-校验结果规则]同一维度下区间重叠，版本ID：{}，维度ID：{}", scaleVersionId, dimensionId);
                throw new BusinessException(ScaleExceptionEnum.SCALE_RESULT_RULE_OVERLAP);
            }
        }
    }

    /**
     * 校验批量提交的结果规则整体不重叠（按维度分组，dimensionId 为 NULL 的视为同一组）
     *
     * @param rules 提交的结果规则集合
     */
    private void validateBatchRules(List<AdminScaleResultRuleDTO> rules) {
        Map<Long, List<AdminScaleResultRuleDTO>> groups = new HashMap<>();
        for (AdminScaleResultRuleDTO rule : rules) {
            groups.computeIfAbsent(rule.getDimensionId(), k -> new ArrayList<>()).add(rule);
        }
        for (List<AdminScaleResultRuleDTO> group : groups.values()) {
            for (int i = 0; i < group.size(); i++) {
                AdminScaleResultRuleDTO a = group.get(i);
                for (int j = i + 1; j < group.size(); j++) {
                    AdminScaleResultRuleDTO b = group.get(j);
                    if (checkOverlap(a.getMinScore(), a.getMaxScore(), b.getMinScore(), b.getMaxScore())) {
                        log.error("[AdminScaleResultRuleServiceImpl-校验批量结果规则]提交区间重叠，a：{}，b：{}", a, b);
                        throw new BusinessException(ScaleExceptionEnum.SCALE_RESULT_RULE_OVERLAP);
                    }
                }
            }
        }
    }

    /**
     * 判断两个闭区间是否重叠（均包含边界）
     *
     * @param aMin 区间a最低分
     * @param aMax 区间a最高分
     * @param bMin 区间b最低分
     * @param bMax 区间b最高分
     * @return true表示重叠
     */
    private boolean checkOverlap(BigDecimal aMin, BigDecimal aMax, BigDecimal bMin, BigDecimal bMax) {
        if (aMin == null || aMax == null || bMin == null || bMax == null) {
            return false;
        }
        return aMax.compareTo(bMin) >= 0 && bMax.compareTo(aMin) >= 0;
    }
}