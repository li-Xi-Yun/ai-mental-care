package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleBranchRuleBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleBranchRuleDTO;
import org.lixiyun.pojo.entity.scale.ScaleBranchRule;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleBranchRuleVO;
import org.lixiyun.server.mapper.ScaleBranchRuleMapper;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.service.admin.AdminScaleBranchRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员量表跳题规则服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleBranchRuleServiceImpl implements AdminScaleBranchRuleService {

    private final ScaleBranchRuleMapper scaleBranchRuleMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleOptionMapper scaleOptionMapper;

    @Override
    public List<AdminScaleBranchRuleVO> listRules(Long scaleVersionId) {
        log.info("[AdminScaleBranchRuleServiceImpl-查询跳题规则列表]，版本ID：{}", scaleVersionId);

        List<ScaleBranchRule> rules = scaleBranchRuleMapper.selectList(new LambdaQueryWrapper<ScaleBranchRule>()
                .eq(ScaleBranchRule::getScaleVersionId, scaleVersionId));
        if (CollUtil.isEmpty(rules)) {
            return new ArrayList<>();
        }

        // 收集题目ID与触发选项ID
        Set<Long> questionIds = new HashSet<>();
        Set<Long> optionIds = new HashSet<>();
        for (ScaleBranchRule rule : rules) {
            if (rule.getSourceQuestionId() != null) {
                questionIds.add(rule.getSourceQuestionId());
            }
            if (rule.getTargetQuestionId() != null) {
                questionIds.add(rule.getTargetQuestionId());
            }
            if (rule.getSourceOptionId() != null) {
                optionIds.add(rule.getSourceOptionId());
            }
        }

        Map<Long, String> questionTitleMap = batchQuestionTitleMap(questionIds);
        Map<Long, String> optionTextMap = batchOptionTextMap(optionIds);

        List<AdminScaleBranchRuleVO> voList = rules.stream().map(rule -> AdminScaleBranchRuleVO.builder()
                        .id(rule.getId())
                        .scaleVersionId(rule.getScaleVersionId())
                        .sourceQuestionId(rule.getSourceQuestionId())
                        .sourceQuestionTitle(questionTitleMap.get(rule.getSourceQuestionId()))
                        .sourceOptionId(rule.getSourceOptionId())
                        .sourceOptionText(optionTextMap.get(rule.getSourceOptionId()))
                        .targetQuestionId(rule.getTargetQuestionId())
                        .targetQuestionTitle(questionTitleMap.get(rule.getTargetQuestionId()))
                        .createdTime(rule.getCreatedTime())
                        .updatedTime(rule.getUpdatedTime())
                        .build())
                .collect(Collectors.toList());

        log.debug("[AdminScaleBranchRuleServiceImpl-查询跳题规则列表]完成，规则数：{}", voList.size());
        return voList;
    }

    @Override
    public void createRule(AdminScaleBranchRuleDTO dto) {
        log.info("[AdminScaleBranchRuleServiceImpl-新增跳题规则]，参数：{}", dto);

        validateRule(dto.getScaleVersionId(), dto.getSourceQuestionId(), dto.getSourceOptionId(), dto.getTargetQuestionId());

        ScaleBranchRule entity = BeanUtil.copyProperties(dto, ScaleBranchRule.class);
        int row = scaleBranchRuleMapper.insert(entity);
        if (row == 0) {
            log.error("[AdminScaleBranchRuleServiceImpl-新增跳题规则]插入失败，参数：{}", dto);
            throw new BusinessException(ScaleExceptionEnum.SCALE_BRANCH_RULE_ADD_FAIL);
        }
        log.info("[AdminScaleBranchRuleServiceImpl-新增跳题规则]成功，规则ID：{}", entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveRules(AdminScaleBranchRuleBatchDTO dto) {
        Long scaleVersionId = dto.getScaleVersionId();
        List<AdminScaleBranchRuleDTO> rules = dto.getRules();
        log.info("[AdminScaleBranchRuleServiceImpl-批量保存跳题规则]，版本ID：{}，规则数：{}", scaleVersionId, CollUtil.size(rules));

        List<ScaleBranchRule> existing = scaleBranchRuleMapper.selectList(new LambdaQueryWrapper<ScaleBranchRule>()
                .eq(ScaleBranchRule::getScaleVersionId, scaleVersionId));
        Set<Long> existingIds = existing.stream().map(ScaleBranchRule::getId).collect(Collectors.toSet());
        Set<Long> submitIds = rules.stream().map(AdminScaleBranchRuleDTO::getId)
                .filter(ObjectUtil::isNotNull).collect(Collectors.toSet());

        // 新增：id 为空；更新：id 非空且在库中存在
        for (AdminScaleBranchRuleDTO rule : rules) {
            if (rule.getId() == null) {
                AdminScaleBranchRuleDTO newDto = BeanUtil.copyProperties(rule, AdminScaleBranchRuleDTO.class);
                newDto.setScaleVersionId(scaleVersionId);
                createRule(newDto);
            } else if (existingIds.contains(rule.getId())) {
                updateRule(rule.getId(), rule);
            }
        }

        // 删除库中存在但提交集合中已不存在的规则
        for (Long id : existingIds) {
            if (!submitIds.contains(id)) {
                scaleBranchRuleMapper.deleteById(id);
            }
        }
        log.info("[AdminScaleBranchRuleServiceImpl-批量保存跳题规则]完成，版本ID：{}", scaleVersionId);
    }

    @Override
    public void updateRule(Long ruleId, AdminScaleBranchRuleDTO dto) {
        log.info("[AdminScaleBranchRuleServiceImpl-修改跳题规则]，规则ID：{}", ruleId);

        ScaleBranchRule original = scaleBranchRuleMapper.selectById(ruleId);
        if (original == null) {
            log.error("[AdminScaleBranchRuleServiceImpl-修改跳题规则]规则不存在，规则ID：{}", ruleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_BRANCH_RULE_NOT_FOUND);
        }

        // 校验使用原规则的版本ID
        validateRule(original.getScaleVersionId(), dto.getSourceQuestionId(), dto.getSourceOptionId(), dto.getTargetQuestionId());

        ScaleBranchRule entity = BeanUtil.copyProperties(dto, ScaleBranchRule.class);
        entity.setId(ruleId);
        scaleBranchRuleMapper.updateById(entity);
        log.info("[AdminScaleBranchRuleServiceImpl-修改跳题规则]成功，规则ID：{}", ruleId);
    }

    @Override
    public void deleteRule(Long ruleId) {
        log.info("[AdminScaleBranchRuleServiceImpl-删除跳题规则]，规则ID：{}", ruleId);
        scaleBranchRuleMapper.deleteById(ruleId);
        log.info("[AdminScaleBranchRuleServiceImpl-删除跳题规则]成功，规则ID：{}", ruleId);
    }

    /**
     * 校验跳题规则合法性：触发题必须属于当前版本，选项须属于触发题，目标题须属于当前版本
     *
     * @param scaleVersionId   量表版本ID
     * @param sourceQuestionId 触发题ID
     * @param sourceOptionId   触发选项ID
     * @param targetQuestionId 跳转目标题ID
     */
    private void validateRule(Long scaleVersionId, Long sourceQuestionId, Long sourceOptionId, Long targetQuestionId) {
        // 触发题必须属于当前版本
        ScaleQuestion sourceQuestion = sourceQuestionId == null ? null : scaleQuestionMapper.selectById(sourceQuestionId);
        if (sourceQuestion == null || !scaleVersionId.equals(sourceQuestion.getScaleVersionId())) {
            log.error("[AdminScaleBranchRuleServiceImpl-校验跳题规则]触发题不属于当前版本，版本ID：{}，触发题ID：{}", scaleVersionId, sourceQuestionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_BRANCH_RULE_SOURCE_INVALID);
        }

        // 指定触发选项时，选项必须属于触发题
        if (sourceOptionId != null) {
            ScaleOption option = scaleOptionMapper.selectById(sourceOptionId);
            if (option == null || !sourceQuestionId.equals(option.getQuestionId())) {
                log.error("[AdminScaleBranchRuleServiceImpl-校验跳题规则]触发选项不属于触发题，题ID：{}，选项ID：{}", sourceQuestionId, sourceOptionId);
                throw new BusinessException(ScaleExceptionEnum.SCALE_BRANCH_RULE_SOURCE_INVALID);
            }
        }

        // 目标题非空时必须属于当前版本
        if (targetQuestionId != null) {
            ScaleQuestion targetQuestion = scaleQuestionMapper.selectById(targetQuestionId);
            if (targetQuestion == null || !scaleVersionId.equals(targetQuestion.getScaleVersionId())) {
                log.error("[AdminScaleBranchRuleServiceImpl-校验跳题规则]目标题不属于当前版本，版本ID：{}，目标题ID：{}", scaleVersionId, targetQuestionId);
                throw new BusinessException(ScaleExceptionEnum.SCALE_BRANCH_RULE_TARGET_INVALID);
            }
        }
    }

    /**
     * 批量解析题目ID对应的题干
     *
     * @param questionIds 题目ID集合
     * @return 题目ID -> 题干 映射
     */
    private Map<Long, String> batchQuestionTitleMap(Set<Long> questionIds) {
        if (CollUtil.isEmpty(questionIds)) {
            return new java.util.HashMap<>();
        }
        return scaleQuestionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(ScaleQuestion::getId, ScaleQuestion::getTitle, (a, b) -> a));
    }

    /**
     * 批量解析选项ID对应的选项文本
     *
     * @param optionIds 选项ID集合
     * @return 选项ID -> 选项文本 映射
     */
    private Map<Long, String> batchOptionTextMap(Set<Long> optionIds) {
        if (CollUtil.isEmpty(optionIds)) {
            return new java.util.HashMap<>();
        }
        return scaleOptionMapper.selectBatchIds(optionIds).stream()
                .collect(Collectors.toMap(ScaleOption::getId, ScaleOption::getOptionText, (a, b) -> a));
    }
}
