package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQueryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleStatusDTO;
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
import org.lixiyun.pojo.vo.admin.scale.AdminScaleDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleVO;
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
import org.lixiyun.server.service.admin.AdminScaleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理员量表主表服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleServiceImpl implements AdminScaleService {

    private final ScaleMapper scaleMapper;
    private final ScaleVersionMapper scaleVersionMapper;
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
    @Transactional(rollbackFor = Exception.class)
    public void createScale(AdminScaleDTO dto) {
        log.info("新增量表，参数：{}", dto.getScaleName());
        String scaleName = dto.getScaleName();
        Long count = scaleMapper.selectCount(new LambdaQueryWrapper<Scale>()
                .eq(Scale::getScaleName, scaleName));
        if (count > 0) {
            log.warn("量表名称已存在：{}", scaleName);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NAME_EXISTS);
        }
        Scale scale = BeanUtil.copyProperties(dto, Scale.class);
        scale.setId(null);
        scale.setStatus(ObjectUtil.defaultIfNull(scale.getStatus(), Scale.STATUS_ENABLE));
        scale.setAllowRepeat(ObjectUtil.defaultIfNull(scale.getAllowRepeat(), 1));
        int row = scaleMapper.insert(scale);
        if (row == 0) {
            log.error("量表新增失败：{}", scaleName);
            throw new BusinessException(ScaleExceptionEnum.SCALE_ADD_FAIL);
        }
        log.debug("量表新增成功，量表ID：{}", scale.getId());
        if (Boolean.TRUE.equals(dto.getCreateFirstVersion())) {
            createFirstVersion(scale, dto);
        }
        log.info("量表新增完成，量表ID：{}", scale.getId());
    }

    @Override
    public PageResult<AdminScaleVO> pageScales(AdminScaleQueryDTO queryDTO) {
        log.info("量表分页查询，参数：{}", queryDTO);
        String scaleName = queryDTO.getScaleName();
        Long scaleCategoryId = queryDTO.getScaleCategoryId();
        Integer status = queryDTO.getStatus();
        Integer deletedFlag = queryDTO.getDeletedFlag();
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();

        Page<AdminScaleVO> page = scaleMapper.pageScales(new Page<>(pageNum, pageSize), scaleName, scaleCategoryId, status, deletedFlag);
        fillAuditNames(page.getRecords());
        log.debug("量表分页查询完成，总数：{}", page.getTotal());
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    public AdminScaleDetailVO getScaleDetail(Long scaleId) {
        log.info("量表详情查询，量表ID：{}", scaleId);
        Scale scale = scaleMapper.selectById(scaleId);
        if (scale == null) {
            log.warn("量表不存在，量表ID：{}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        AdminScaleDetailVO detailVO = BeanUtil.copyProperties(scale, AdminScaleDetailVO.class);
        List<ScaleVersion> versions = scaleVersionMapper.selectList(new LambdaQueryWrapper<ScaleVersion>()
                .eq(ScaleVersion::getScaleId, scaleId)
                .orderByAsc(ScaleVersion::getId));
        List<AdminScaleVersionVO> versionVOs = CollUtil.isEmpty(versions) ? Collections.emptyList()
                : StreamUtils.toList(versions, version -> toVersionVO(version, scale.getCurrentVersionId()));
        detailVO.setVersions(versionVOs);
        fillScaleAuditName(detailVO, scale);
        fillVersionAuditNames(versionVOs, versions);
        return detailVO;
    }

    @Override
    public void updateScale(Long scaleId, AdminScaleDTO dto) {
        log.info("修改量表，量表ID：{}，参数：{}", scaleId, dto.getScaleName());
        Scale scale = BeanUtil.copyProperties(dto, Scale.class);
        scale.setId(scaleId);
        int row = scaleMapper.updateById(scale);
        if (row == 0) {
            log.warn("量表不存在，量表ID：{}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        log.info("量表修改成功，量表ID：{}", scaleId);
    }

    @Override
    public void updateScaleStatus(Long scaleId, AdminScaleStatusDTO statusDTO) {
        log.info("修改量表状态，量表ID：{}，状态：{}", scaleId, statusDTO.getStatus());
        scaleMapper.update(null, new LambdaUpdateWrapper<Scale>()
                .eq(Scale::getId, scaleId)
                .set(Scale::getStatus, statusDTO.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScale(Long scaleId) {
        log.info("删除量表，量表ID：{}", scaleId);
        Scale scale = scaleMapper.selectById(scaleId);
        if (scale == null) {
            log.warn("量表不存在，量表ID：{}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        List<ScaleVersion> versions = scaleVersionMapper.selectList(new LambdaQueryWrapper<ScaleVersion>()
                .eq(ScaleVersion::getScaleId, scaleId));
        List<Long> versionIds = StreamUtils.toList(versions, ScaleVersion::getId);
        List<Long> questionIds = CollUtil.isEmpty(versionIds) ? Collections.emptyList()
                : StreamUtils.toList(scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                        .in(ScaleQuestion::getScaleVersionId, versionIds)), ScaleQuestion::getId);
        List<Long> templateGroupIds = CollUtil.isEmpty(versionIds) ? Collections.emptyList()
                : StreamUtils.toList(scaleOptionTemplateGroupMapper.selectList(new LambdaQueryWrapper<ScaleOptionTemplateGroup>()
                        .in(ScaleOptionTemplateGroup::getScaleVersionId, versionIds)), ScaleOptionTemplateGroup::getId);
        List<Long> normGroupIds = CollUtil.isEmpty(versionIds) ? Collections.emptyList()
                : StreamUtils.toList(scaleNormGroupMapper.selectList(new LambdaQueryWrapper<ScaleNormGroup>()
                        .in(ScaleNormGroup::getScaleVersionId, versionIds)), ScaleNormGroup::getId);

        scaleVersionMapper.delete(new LambdaQueryWrapper<ScaleVersion>().eq(ScaleVersion::getScaleId, scaleId));
        if (CollUtil.isNotEmpty(versionIds)) {
            scaleDimensionMapper.delete(new LambdaQueryWrapper<ScaleDimension>().in(ScaleDimension::getScaleVersionId, versionIds));
            scaleQuestionMapper.delete(new LambdaQueryWrapper<ScaleQuestion>().in(ScaleQuestion::getScaleVersionId, versionIds));
            if (CollUtil.isNotEmpty(questionIds)) {
                scaleOptionMapper.delete(new LambdaQueryWrapper<ScaleOption>().in(ScaleOption::getQuestionId, questionIds));
            }
            scaleOptionTemplateGroupMapper.delete(new LambdaQueryWrapper<ScaleOptionTemplateGroup>().in(ScaleOptionTemplateGroup::getScaleVersionId, versionIds));
            if (CollUtil.isNotEmpty(templateGroupIds)) {
                scaleOptionTemplateItemMapper.delete(new LambdaQueryWrapper<ScaleOptionTemplateItem>().in(ScaleOptionTemplateItem::getTemplateGroupId, templateGroupIds));
            }
            scaleBranchRuleMapper.delete(new LambdaQueryWrapper<ScaleBranchRule>().in(ScaleBranchRule::getScaleVersionId, versionIds));
            scaleResultRuleMapper.delete(new LambdaQueryWrapper<ScaleResultRule>().in(ScaleResultRule::getScaleVersionId, versionIds));
            scaleNormGroupMapper.delete(new LambdaQueryWrapper<ScaleNormGroup>().in(ScaleNormGroup::getScaleVersionId, versionIds));
            if (CollUtil.isNotEmpty(normGroupIds)) {
                scaleNormMapper.delete(new LambdaQueryWrapper<ScaleNorm>().in(ScaleNorm::getNormGroupId, normGroupIds));
            }
        }
        scaleMapper.deleteById(scaleId);
        log.info("量表删除成功，量表ID：{}", scaleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreScale(Long scaleId) {
        log.info("恢复量表，量表ID：{}", scaleId);
        int row = scaleMapper.restoreScale(scaleId);
        if (row == 0) {
            log.warn("量表不存在，量表ID：{}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        scaleVersionMapper.restoreVersionsByScaleId(scaleId);
        scaleDimensionMapper.restoreByScaleId(scaleId);
        scaleQuestionMapper.restoreByScaleId(scaleId);
        scaleOptionMapper.restoreByScaleId(scaleId);
        scaleOptionTemplateGroupMapper.restoreByScaleId(scaleId);
        scaleOptionTemplateItemMapper.restoreByScaleId(scaleId);
        scaleBranchRuleMapper.restoreByScaleId(scaleId);
        scaleResultRuleMapper.restoreByScaleId(scaleId);
        scaleNormGroupMapper.restoreByScaleId(scaleId);
        scaleNormMapper.restoreByScaleId(scaleId);
        log.info("量表恢复成功，量表ID：{}", scaleId);
    }

    /**
     * 创建并发布量表首个版本
     *
     * @param scale 量表实体
     * @param dto   量表DTO
     */
    private void createFirstVersion(Scale scale, AdminScaleDTO dto) {
        String versionNo = StrUtil.isBlank(dto.getVersionNo()) ? "v1.0" : dto.getVersionNo();
        Long count = scaleVersionMapper.selectCount(new LambdaQueryWrapper<ScaleVersion>()
                .eq(ScaleVersion::getScaleId, scale.getId())
                .eq(ScaleVersion::getVersionNo, versionNo));
        if (count > 0) {
            log.warn("该量表下版本号已存在，量表ID：{}，版本号：{}", scale.getId(), versionNo);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NO_EXISTS);
        }
        ScaleVersion version = new ScaleVersion();
        version.setScaleId(scale.getId());
        version.setVersionNo(versionNo);
        version.setDescription(dto.getDescription());
        version.setCopyrightInfo(dto.getCopyrightInfo());
        int row = scaleVersionMapper.insert(version);
        if (row == 0) {
            log.error("量表版本新增失败，量表ID：{}，版本号：{}", scale.getId(), versionNo);
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_ADD_FAIL);
        }
        scale.setCurrentVersionId(version.getId());
        scaleMapper.updateById(scale);
        log.debug("量表首个版本创建并发布成功，量表ID：{}，版本ID：{}", scale.getId(), version.getId());
    }

    /**
     * 组装版本VO（含题目数/维度数统计与是否当前生效）
     *
     * @param version           版本实体
     * @param currentVersionId  当前生效版本ID
     * @return 版本VO
     */
    private AdminScaleVersionVO toVersionVO(ScaleVersion version, Long currentVersionId) {
        AdminScaleVersionVO vo = AdminScaleVersionVO.builder()
                .id(version.getId())
                .scaleId(version.getScaleId())
                .versionNo(version.getVersionNo())
                .description(version.getDescription())
                .copyrightInfo(version.getCopyrightInfo())
                .isCurrent(Objects.equals(version.getId(), currentVersionId))
                .createdTime(version.getCreatedTime())
                .updatedTime(version.getUpdatedTime())
                .build();
        vo.setQuestionCount(scaleQuestionMapper.selectCount(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, version.getId())).intValue());
        vo.setDimensionCount(scaleDimensionMapper.selectCount(new LambdaQueryWrapper<ScaleDimension>()
                .eq(ScaleDimension::getScaleVersionId, version.getId())).intValue());
        return vo;
    }

    /**
     * 批量填充量表VO的创建人/更新人姓名
     *
     * @param records 量表VO列表
     */
    private void fillAuditNames(List<AdminScaleVO> records) {
        if (CollUtil.isEmpty(records)) {
            return;
        }
        List<Long> scaleIds = StreamUtils.toList(records, AdminScaleVO::getId);
        if (CollUtil.isEmpty(scaleIds)) {
            return;
        }
        List<Scale> scales = scaleMapper.selectBatchIds(scaleIds);
        if (CollUtil.isEmpty(scales)) {
            return;
        }
        Map<Long, Scale> scaleMap = StreamUtils.toIdentityMap(scales, Scale::getId);
        List<Long> personIds = new ArrayList<>();
        scales.forEach(scale -> {
            if (scale.getCreatedBy() != null) {
                personIds.add(scale.getCreatedBy());
            }
            if (scale.getUpdatedBy() != null) {
                personIds.add(scale.getUpdatedBy());
            }
        });
        Map<Long, String> nameMap = scaleAuditNameHelper.resolveNames(personIds);
        records.forEach(vo -> {
            Scale scale = scaleMap.get(vo.getId());
            if (scale != null) {
                if (scale.getCreatedBy() != null) {
                    vo.setCreatedByName(nameMap.get(scale.getCreatedBy()));
                }
                if (scale.getUpdatedBy() != null) {
                    vo.setUpdatedByName(nameMap.get(scale.getUpdatedBy()));
                }
            }
        });
    }

    /**
     * 填充量表详情VO的创建人/更新人姓名
     *
     * @param detailVO 量表详情VO
     * @param scale    量表实体
     */
    private void fillScaleAuditName(AdminScaleDetailVO detailVO, Scale scale) {
        List<Long> personIds = new ArrayList<>();
        if (scale.getCreatedBy() != null) {
            personIds.add(scale.getCreatedBy());
        }
        if (scale.getUpdatedBy() != null) {
            personIds.add(scale.getUpdatedBy());
        }
        if (CollUtil.isEmpty(personIds)) {
            return;
        }
        Map<Long, String> nameMap = scaleAuditNameHelper.resolveNames(personIds);
        if (scale.getCreatedBy() != null) {
            detailVO.setCreatedByName(nameMap.get(scale.getCreatedBy()));
        }
        if (scale.getUpdatedBy() != null) {
            detailVO.setUpdatedByName(nameMap.get(scale.getUpdatedBy()));
        }
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
}
