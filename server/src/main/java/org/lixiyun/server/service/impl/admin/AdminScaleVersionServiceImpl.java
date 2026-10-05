package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleVersionCopyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleVersionDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleBranchRule;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleNorm;
import org.lixiyun.pojo.entity.scale.ScaleNormGroup;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleOptionTemplateGroup;
import org.lixiyun.pojo.entity.scale.ScaleOptionTemplateItem;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.entity.scale.ScaleResultRule;
import org.lixiyun.pojo.entity.scale.ScaleVersion;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleVersionVO;
import org.lixiyun.server.mapper.ScaleBranchRuleMapper;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleNormGroupMapper;
import org.lixiyun.server.mapper.ScaleNormMapper;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleOptionTemplateGroupMapper;
import org.lixiyun.server.mapper.ScaleOptionTemplateItemMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.mapper.ScaleResultRuleMapper;
import org.lixiyun.server.mapper.ScaleVersionMapper;
import org.lixiyun.server.service.admin.AdminScaleVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理员量表版本服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleVersionServiceImpl implements AdminScaleVersionService {

    private final ScaleVersionMapper scaleVersionMapper;
    private final ScaleMapper scaleMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleOptionTemplateGroupMapper scaleOptionTemplateGroupMapper;
    private final ScaleOptionTemplateItemMapper scaleOptionTemplateItemMapper;
    private final ScaleBranchRuleMapper scaleBranchRuleMapper;
    private final ScaleResultRuleMapper scaleResultRuleMapper;
    private final ScaleNormGroupMapper scaleNormGroupMapper;
    private final ScaleNormMapper scaleNormMapper;
    private final ScaleAuditNameHelper scaleAuditNameHelper;

    @Override
    public List<AdminScaleVersionVO> listVersions(Long scaleId) {
        log.info("查询量表版本列表，量表ID：{}", scaleId);
        List<ScaleVersion> versions = scaleVersionMapper.selectList(new LambdaQueryWrapper<ScaleVersion>()
                .eq(ScaleVersion::getScaleId, scaleId)
                .orderByAsc(ScaleVersion::getId));
        if (CollUtil.isEmpty(versions)) {
            return Collections.emptyList();
        }
        Scale scale = scaleMapper.selectById(scaleId);
        Long currentVersionId = scale != null ? scale.getCurrentVersionId() : null;
        List<Long> versionIds = StreamUtils.toList(versions, ScaleVersion::getId);
        Map<Long, Long> questionCountMap = CollUtil.isEmpty(versionIds) ? Collections.emptyMap()
                : scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                        .in(ScaleQuestion::getScaleVersionId, versionIds))
                .stream().collect(Collectors.groupingBy(ScaleQuestion::getScaleVersionId, Collectors.counting()));
        Map<Long, Long> dimensionCountMap = CollUtil.isEmpty(versionIds) ? Collections.emptyMap()
                : scaleDimensionMapper.selectList(new LambdaQueryWrapper<ScaleDimension>()
                        .in(ScaleDimension::getScaleVersionId, versionIds))
                .stream().collect(Collectors.groupingBy(ScaleDimension::getScaleVersionId, Collectors.counting()));
        List<AdminScaleVersionVO> versionVOs = StreamUtils.toList(versions, version -> {
            AdminScaleVersionVO vo = buildBaseVersionVO(version, currentVersionId);
            vo.setQuestionCount(questionCountMap.getOrDefault(version.getId(), 0L).intValue());
            vo.setDimensionCount(dimensionCountMap.getOrDefault(version.getId(), 0L).intValue());
            return vo;
        });
        fillVersionAuditNames(versionVOs, versions);
        return versionVOs;
    }

    @Override
    public AdminScaleVersionVO getVersionDetail(Long versionId) {
        log.info("查询版本详情，版本ID：{}", versionId);
        ScaleVersion version = scaleVersionMapper.selectById(versionId);
        if (version == null) {
            log.warn("量表版本不存在，版本ID：{}", versionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
        }
        Scale scale = scaleMapper.selectById(version.getScaleId());
        AdminScaleVersionVO vo = buildBaseVersionVO(version, scale != null ? scale.getCurrentVersionId() : null);
        vo.setQuestionCount(scaleQuestionMapper.selectCount(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, versionId)).intValue());
        vo.setDimensionCount(scaleDimensionMapper.selectCount(new LambdaQueryWrapper<ScaleDimension>()
                .eq(ScaleDimension::getScaleVersionId, versionId)).intValue());
        fillVersionAuditNames(Collections.singletonList(vo), Collections.singletonList(version));
        return vo;
    }

    @Override
    public void createVersion(AdminScaleVersionDTO dto) {
        log.info("新增量表版本，量表ID：{}，版本号：{}", dto.getScaleId(), dto.getVersionNo());
        Long scaleId = dto.getScaleId();
        String versionNo = dto.getVersionNo();
        Long count = scaleVersionMapper.selectCount(new LambdaQueryWrapper<ScaleVersion>()
                .eq(ScaleVersion::getScaleId, scaleId)
                .eq(ScaleVersion::getVersionNo, versionNo));
        if (count > 0) {
            log.warn("该量表下版本号已存在，量表ID：{}，版本号：{}", scaleId, versionNo);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NO_EXISTS);
        }
        Scale scale = scaleMapper.selectById(scaleId);
        if (scale == null) {
            log.warn("量表不存在，量表ID：{}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        ScaleVersion version = BeanUtil.copyProperties(dto, ScaleVersion.class);
        version.setId(null);
        int row = scaleVersionMapper.insert(version);
        if (row == 0) {
            log.error("量表版本新增失败，量表ID：{}，版本号：{}", scaleId, versionNo);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_ADD_FAIL);
        }
        log.info("量表版本新增成功，版本ID：{}", version.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyVersion(AdminScaleVersionCopyDTO dto) {
        Long sourceVersionId = dto.getSourceVersionId();
        Long targetScaleId = dto.getTargetScaleId();
        String versionNo = dto.getVersionNo();
        log.info("复制量表版本，源版本ID：{}，目标量表ID：{}，版本号：{}", sourceVersionId, targetScaleId, versionNo);

        ScaleVersion sourceVersion = scaleVersionMapper.selectById(sourceVersionId);
        if (sourceVersion == null) {
            log.warn("源版本不存在，版本ID：{}", sourceVersionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
        }
        Scale targetScale = scaleMapper.selectById(targetScaleId);
        if (targetScale == null) {
            log.warn("目标量表不存在，量表ID：{}", targetScaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        Long dupCount = scaleVersionMapper.selectCount(new LambdaQueryWrapper<ScaleVersion>()
                .eq(ScaleVersion::getScaleId, targetScaleId)
                .eq(ScaleVersion::getVersionNo, versionNo));
        if (dupCount > 0) {
            log.warn("目标量表下版本号已存在，量表ID：{}，版本号：{}", targetScaleId, versionNo);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NO_EXISTS);
        }

        ScaleVersion newVersion = new ScaleVersion();
        newVersion.setScaleId(targetScaleId);
        newVersion.setVersionNo(versionNo);
        newVersion.setDescription(sourceVersion.getDescription());
        newVersion.setCopyrightInfo(sourceVersion.getCopyrightInfo());
        int row = scaleVersionMapper.insert(newVersion);
        if (row == 0) {
            log.error("量表版本新增失败，量表ID：{}，版本号：{}", targetScaleId, versionNo);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_ADD_FAIL);
        }
        log.debug("新版本创建成功，版本ID：{}", newVersion.getId());

        try {
            Map<Long, Long> dimensionIdMapping = copyDimensions(sourceVersionId, newVersion.getId());
            Map<Long, Long> questionIdMapping = copyQuestions(sourceVersionId, newVersion.getId(), dimensionIdMapping);
            Map<Long, Long> optionIdMapping = copyOptions(sourceVersionId, questionIdMapping);
            copyTemplateGroups(sourceVersionId, newVersion.getId());
            copyResultRules(sourceVersionId, newVersion.getId(), dimensionIdMapping);
            copyBranchRules(sourceVersionId, newVersion.getId(), questionIdMapping, optionIdMapping);
            copyNormGroups(sourceVersionId, newVersion.getId(), dimensionIdMapping);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("复制量表版本失败，源版本ID：{}，目标量表ID：{}", sourceVersionId, targetScaleId, e);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_COPY_FAIL);
        }
        log.info("量表版本复制成功，源版本ID：{}，新版本ID：{}", sourceVersionId, newVersion.getId());
    }

    @Override
    public void updateVersion(Long versionId, AdminScaleVersionDTO dto) {
        log.info("修改量表版本，版本ID：{}，参数：{}", versionId, dto.getVersionNo());
        Long scaleId = dto.getScaleId();
        if (scaleId == null) {
            ScaleVersion existing = scaleVersionMapper.selectById(versionId);
            if (existing == null) {
                log.warn("量表版本不存在，版本ID：{}", versionId);
                throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
            }
            scaleId = existing.getScaleId();
        }
        Long count = scaleVersionMapper.selectCount(new LambdaQueryWrapper<ScaleVersion>()
                .eq(ScaleVersion::getScaleId, scaleId)
                .eq(ScaleVersion::getVersionNo, dto.getVersionNo())
                .ne(ScaleVersion::getId, versionId));
        if (count > 0) {
            log.warn("该量表下版本号已存在，量表ID：{}，版本号：{}", scaleId, dto.getVersionNo());
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NO_EXISTS);
        }
        ScaleVersion version = BeanUtil.copyProperties(dto, ScaleVersion.class);
        version.setId(versionId);
        int row = scaleVersionMapper.updateById(version);
        if (row == 0) {
            log.warn("量表版本不存在，版本ID：{}", versionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
        }
        log.info("量表版本修改成功，版本ID：{}", versionId);
    }

    @Override
    public void publishVersion(Long versionId) {
        log.info("发布量表版本，版本ID：{}", versionId);
        ScaleVersion version = scaleVersionMapper.selectById(versionId);
        if (version == null) {
            log.warn("量表版本不存在，版本ID：{}", versionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
        }
        Long questionCount = scaleQuestionMapper.selectCount(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, versionId));
        if (questionCount <= 0) {
            log.warn("版本下无题目，暂不能发布，版本ID：{}", versionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_PUBLISH_EMPTY);
        }
        Scale scale = scaleMapper.selectById(version.getScaleId());
        if (scale == null) {
            log.warn("量表不存在，量表ID：{}", version.getScaleId());
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        scale.setCurrentVersionId(versionId);
        scaleMapper.updateById(scale);
        log.info("量表版本发布成功，版本ID：{}，量表ID：{}", versionId, version.getScaleId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(Long versionId) {
        log.info("删除量表版本，版本ID：{}", versionId);
        ScaleVersion version = scaleVersionMapper.selectById(versionId);
        if (version == null) {
            log.warn("量表版本不存在，版本ID：{}", versionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
        }
        Scale scale = scaleMapper.selectById(version.getScaleId());
        if (scale != null && Objects.equals(versionId, scale.getCurrentVersionId())) {
            log.warn("当前生效版本不允许删除，版本ID：{}", versionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_CURRENT_DELETE_FORBIDDEN);
        }
        deleteVersionContents(versionId);
        scaleVersionMapper.deleteById(versionId);
        log.info("量表版本删除成功，版本ID：{}", versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreVersion(Long versionId) {
        log.info("恢复量表版本，版本ID：{}", versionId);
        int row = scaleVersionMapper.restoreVersion(versionId);
        if (row == 0) {
            log.warn("量表版本不存在，版本ID：{}", versionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
        }
        scaleDimensionMapper.restoreByVersionId(versionId);
        scaleQuestionMapper.restoreByVersionId(versionId);
        scaleOptionMapper.restoreByVersionId(versionId);
        scaleOptionTemplateGroupMapper.restoreByVersionId(versionId);
        scaleOptionTemplateItemMapper.restoreByVersionId(versionId);
        scaleBranchRuleMapper.restoreByVersionId(versionId);
        scaleResultRuleMapper.restoreByVersionId(versionId);
        scaleNormGroupMapper.restoreByVersionId(versionId);
        scaleNormMapper.restoreByVersionId(versionId);
        log.info("量表版本恢复成功，版本ID：{}", versionId);
    }

    /**
     * 组装版本基础VO（不含统计信息）
     *
     * @param version           版本实体
     * @param currentVersionId  当前生效版本ID
     * @return 版本VO
     */
    private AdminScaleVersionVO buildBaseVersionVO(ScaleVersion version, Long currentVersionId) {
        return AdminScaleVersionVO.builder()
                .id(version.getId())
                .scaleId(version.getScaleId())
                .versionNo(version.getVersionNo())
                .description(version.getDescription())
                .copyrightInfo(version.getCopyrightInfo())
                .isCurrent(Objects.equals(version.getId(), currentVersionId))
                .createdTime(version.getCreatedTime())
                .updatedTime(version.getUpdatedTime())
                .build();
    }

    /**
     * 批量填充版本VO的创建人/更新人姓名
     *
     * @param versionVOs 版本VO列表
     * @param versions   版本实体列表
     */
    private void fillVersionAuditNames(List<AdminScaleVersionVO> versionVOs, List<ScaleVersion> versions) {
        if (CollUtil.isEmpty(versionVOs) || CollUtil.isEmpty(versions)) {
            return;
        }
        List<Long> personIds = new ArrayList<>();
        versions.forEach(version -> {
            if (version.getCreatedBy() != null) {
                personIds.add(version.getCreatedBy());
            }
            if (version.getUpdatedBy() != null) {
                personIds.add(version.getUpdatedBy());
            }
        });
        if (CollUtil.isEmpty(personIds)) {
            return;
        }
        Map<Long, String> nameMap = scaleAuditNameHelper.resolveNames(personIds);
        for (int i = 0; i < versionVOs.size(); i++) {
            ScaleVersion version = versions.get(i);
            AdminScaleVersionVO vo = versionVOs.get(i);
            if (version.getCreatedBy() != null) {
                vo.setCreatedByName(nameMap.get(version.getCreatedBy()));
            }
            if (version.getUpdatedBy() != null) {
                vo.setUpdatedByName(nameMap.get(version.getUpdatedBy()));
            }
        }
    }

    /**
     * 级联逻辑删除版本全部内容（题目/选项/维度/模板/规则/常模等）
     *
     * @param versionId 版本ID
     */
    private void deleteVersionContents(Long versionId) {
        List<Long> questionIds = StreamUtils.toList(scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, versionId)), ScaleQuestion::getId);
        List<Long> templateGroupIds = StreamUtils.toList(scaleOptionTemplateGroupMapper.selectList(new LambdaQueryWrapper<ScaleOptionTemplateGroup>()
                .eq(ScaleOptionTemplateGroup::getScaleVersionId, versionId)), ScaleOptionTemplateGroup::getId);
        List<Long> normGroupIds = StreamUtils.toList(scaleNormGroupMapper.selectList(new LambdaQueryWrapper<ScaleNormGroup>()
                .eq(ScaleNormGroup::getScaleVersionId, versionId)), ScaleNormGroup::getId);

        scaleDimensionMapper.delete(new LambdaQueryWrapper<ScaleDimension>().eq(ScaleDimension::getScaleVersionId, versionId));
        scaleQuestionMapper.delete(new LambdaQueryWrapper<ScaleQuestion>().eq(ScaleQuestion::getScaleVersionId, versionId));
        if (CollUtil.isNotEmpty(questionIds)) {
            scaleOptionMapper.delete(new LambdaQueryWrapper<ScaleOption>().in(ScaleOption::getQuestionId, questionIds));
        }
        scaleOptionTemplateGroupMapper.delete(new LambdaQueryWrapper<ScaleOptionTemplateGroup>().eq(ScaleOptionTemplateGroup::getScaleVersionId, versionId));
        if (CollUtil.isNotEmpty(templateGroupIds)) {
            scaleOptionTemplateItemMapper.delete(new LambdaQueryWrapper<ScaleOptionTemplateItem>().in(ScaleOptionTemplateItem::getTemplateGroupId, templateGroupIds));
        }
        scaleBranchRuleMapper.delete(new LambdaQueryWrapper<ScaleBranchRule>().eq(ScaleBranchRule::getScaleVersionId, versionId));
        scaleResultRuleMapper.delete(new LambdaQueryWrapper<ScaleResultRule>().eq(ScaleResultRule::getScaleVersionId, versionId));
        scaleNormGroupMapper.delete(new LambdaQueryWrapper<ScaleNormGroup>().eq(ScaleNormGroup::getScaleVersionId, versionId));
        if (CollUtil.isNotEmpty(normGroupIds)) {
            scaleNormMapper.delete(new LambdaQueryWrapper<ScaleNorm>().in(ScaleNorm::getNormGroupId, normGroupIds));
        }
    }

    /**
     * 复制源版本的维度到新版本
     *
     * @param sourceVersionId 源版本ID
     * @param targetVersionId 目标版本ID
     * @return 源维度ID -> 新维度ID 映射
     */
    private Map<Long, Long> copyDimensions(Long sourceVersionId, Long targetVersionId) {
        List<ScaleDimension> dimensions = scaleDimensionMapper.selectList(new LambdaQueryWrapper<ScaleDimension>()
                .eq(ScaleDimension::getScaleVersionId, sourceVersionId));
        if (CollUtil.isEmpty(dimensions)) {
            return Collections.emptyMap();
        }
        Map<Long, Long> idMapping = new HashMap<>();
        for (ScaleDimension dimension : dimensions) {
            ScaleDimension newDimension = BeanUtil.copyProperties(dimension, ScaleDimension.class);
            newDimension.setId(null);
            newDimension.setScaleVersionId(targetVersionId);
            scaleDimensionMapper.insert(newDimension);
            idMapping.put(dimension.getId(), newDimension.getId());
        }
        return idMapping;
    }

    /**
     * 复制源版本的题目到新版本
     *
     * @param sourceVersionId    源版本ID
     * @param targetVersionId    目标版本ID
     * @param dimensionIdMapping 源维度ID -> 新维度ID 映射
     * @return 源题目ID -> 新题目ID 映射
     */
    private Map<Long, Long> copyQuestions(Long sourceVersionId, Long targetVersionId, Map<Long, Long> dimensionIdMapping) {
        List<ScaleQuestion> questions = scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, sourceVersionId));
        if (CollUtil.isEmpty(questions)) {
            return Collections.emptyMap();
        }
        Map<Long, Long> idMapping = new HashMap<>();
        for (ScaleQuestion question : questions) {
            ScaleQuestion newQuestion = BeanUtil.copyProperties(question, ScaleQuestion.class);
            newQuestion.setId(null);
            newQuestion.setScaleVersionId(targetVersionId);
            if (question.getDimensionId() != null) {
                newQuestion.setDimensionId(dimensionIdMapping.get(question.getDimensionId()));
            }
            scaleQuestionMapper.insert(newQuestion);
            idMapping.put(question.getId(), newQuestion.getId());
        }
        return idMapping;
    }

    /**
     * 复制源版本全部题目的选项到新版本
     *
     * @param sourceVersionId    源版本ID
     * @param questionIdMapping  源题目ID -> 新题目ID 映射
     * @return 源选项ID -> 新选项ID 映射
     */
    private Map<Long, Long> copyOptions(Long sourceVersionId, Map<Long, Long> questionIdMapping) {
        List<ScaleOption> sourceOptions = CollUtil.isEmpty(questionIdMapping) ? Collections.emptyList()
                : scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                        .in(ScaleOption::getQuestionId, questionIdMapping.keySet()));
        if (CollUtil.isEmpty(sourceOptions)) {
            return Collections.emptyMap();
        }
        List<ScaleOption> newOptions = new ArrayList<>();
        for (ScaleOption sourceOption : sourceOptions) {
            ScaleOption newOption = BeanUtil.copyProperties(sourceOption, ScaleOption.class);
            newOption.setId(null);
            newOption.setQuestionId(questionIdMapping.get(sourceOption.getQuestionId()));
            newOptions.add(newOption);
        }
        Db.saveBatch(newOptions);
        Map<Long, Long> idMapping = new HashMap<>();
        for (int i = 0; i < sourceOptions.size(); i++) {
            idMapping.put(sourceOptions.get(i).getId(), newOptions.get(i).getId());
        }
        return idMapping;
    }

    /**
     * 复制源版本的选项模板组及明细到新版本
     *
     * @param sourceVersionId 源版本ID
     * @param targetVersionId 目标版本ID
     */
    private void copyTemplateGroups(Long sourceVersionId, Long targetVersionId) {
        List<ScaleOptionTemplateGroup> groups = scaleOptionTemplateGroupMapper.selectList(new LambdaQueryWrapper<ScaleOptionTemplateGroup>()
                .eq(ScaleOptionTemplateGroup::getScaleVersionId, sourceVersionId));
        if (CollUtil.isEmpty(groups)) {
            return;
        }
        Map<Long, Long> idMapping = new HashMap<>();
        for (ScaleOptionTemplateGroup group : groups) {
            ScaleOptionTemplateGroup newGroup = BeanUtil.copyProperties(group, ScaleOptionTemplateGroup.class);
            newGroup.setId(null);
            newGroup.setScaleVersionId(targetVersionId);
            scaleOptionTemplateGroupMapper.insert(newGroup);
            idMapping.put(group.getId(), newGroup.getId());
        }
        List<ScaleOptionTemplateItem> items = scaleOptionTemplateItemMapper.selectList(new LambdaQueryWrapper<ScaleOptionTemplateItem>()
                .in(ScaleOptionTemplateItem::getTemplateGroupId, idMapping.keySet()));
        if (CollUtil.isEmpty(items)) {
            return;
        }
        List<ScaleOptionTemplateItem> newItems = new ArrayList<>();
        for (ScaleOptionTemplateItem item : items) {
            ScaleOptionTemplateItem newItem = BeanUtil.copyProperties(item, ScaleOptionTemplateItem.class);
            newItem.setId(null);
            newItem.setTemplateGroupId(idMapping.get(item.getTemplateGroupId()));
            newItems.add(newItem);
        }
        Db.saveBatch(newItems);
    }

    /**
     * 复制源版本的结果规则到新版本
     *
     * @param sourceVersionId    源版本ID
     * @param targetVersionId    目标版本ID
     * @param dimensionIdMapping 源维度ID -> 新维度ID 映射
     */
    private void copyResultRules(Long sourceVersionId, Long targetVersionId, Map<Long, Long> dimensionIdMapping) {
        List<ScaleResultRule> rules = scaleResultRuleMapper.selectList(new LambdaQueryWrapper<ScaleResultRule>()
                .eq(ScaleResultRule::getScaleVersionId, sourceVersionId));
        if (CollUtil.isEmpty(rules)) {
            return;
        }
        List<ScaleResultRule> newRules = new ArrayList<>();
        for (ScaleResultRule rule : rules) {
            ScaleResultRule newRule = BeanUtil.copyProperties(rule, ScaleResultRule.class);
            newRule.setId(null);
            newRule.setScaleVersionId(targetVersionId);
            if (rule.getDimensionId() != null) {
                newRule.setDimensionId(dimensionIdMapping.get(rule.getDimensionId()));
            }
            newRules.add(newRule);
        }
        Db.saveBatch(newRules);
    }

    /**
     * 复制源版本的跳题规则到新版本
     *
     * @param sourceVersionId   源版本ID
     * @param targetVersionId   目标版本ID
     * @param questionIdMapping 源题目ID -> 新题目ID 映射
     * @param optionIdMapping   源选项ID -> 新选项ID 映射
     */
    private void copyBranchRules(Long sourceVersionId, Long targetVersionId, Map<Long, Long> questionIdMapping, Map<Long, Long> optionIdMapping) {
        List<ScaleBranchRule> rules = scaleBranchRuleMapper.selectList(new LambdaQueryWrapper<ScaleBranchRule>()
                .eq(ScaleBranchRule::getScaleVersionId, sourceVersionId));
        if (CollUtil.isEmpty(rules)) {
            return;
        }
        List<ScaleBranchRule> newRules = new ArrayList<>();
        for (ScaleBranchRule rule : rules) {
            ScaleBranchRule newRule = BeanUtil.copyProperties(rule, ScaleBranchRule.class);
            newRule.setId(null);
            newRule.setScaleVersionId(targetVersionId);
            newRule.setSourceQuestionId(questionIdMapping.get(rule.getSourceQuestionId()));
            newRule.setTargetQuestionId(questionIdMapping.get(rule.getTargetQuestionId()));
            if (rule.getSourceOptionId() != null) {
                newRule.setSourceOptionId(optionIdMapping.get(rule.getSourceOptionId()));
            }
            newRules.add(newRule);
        }
        Db.saveBatch(newRules);
    }

    /**
     * 复制源版本的常模组及明细到新版本
     *
     * @param sourceVersionId    源版本ID
     * @param targetVersionId    目标版本ID
     * @param dimensionIdMapping 源维度ID -> 新维度ID 映射
     */
    private void copyNormGroups(Long sourceVersionId, Long targetVersionId, Map<Long, Long> dimensionIdMapping) {
        List<ScaleNormGroup> groups = scaleNormGroupMapper.selectList(new LambdaQueryWrapper<ScaleNormGroup>()
                .eq(ScaleNormGroup::getScaleVersionId, sourceVersionId));
        if (CollUtil.isEmpty(groups)) {
            return;
        }
        Map<Long, Long> idMapping = new HashMap<>();
        for (ScaleNormGroup group : groups) {
            ScaleNormGroup newGroup = BeanUtil.copyProperties(group, ScaleNormGroup.class);
            newGroup.setId(null);
            newGroup.setScaleVersionId(targetVersionId);
            if (group.getDimensionId() != null) {
                newGroup.setDimensionId(dimensionIdMapping.get(group.getDimensionId()));
            }
            scaleNormGroupMapper.insert(newGroup);
            idMapping.put(group.getId(), newGroup.getId());
        }
        List<ScaleNorm> norms = scaleNormMapper.selectList(new LambdaQueryWrapper<ScaleNorm>()
                .in(ScaleNorm::getNormGroupId, idMapping.keySet()));
        if (CollUtil.isEmpty(norms)) {
            return;
        }
        List<ScaleNorm> newNorms = new ArrayList<>();
        for (ScaleNorm norm : norms) {
            ScaleNorm newNorm = BeanUtil.copyProperties(norm, ScaleNorm.class);
            newNorm.setId(null);
            newNorm.setNormGroupId(idMapping.get(norm.getNormGroupId()));
            if (norm.getDimensionId() != null) {
                newNorm.setDimensionId(dimensionIdMapping.get(norm.getDimensionId()));
            }
            newNorms.add(newNorm);
        }
        Db.saveBatch(newNorms);
    }
}
