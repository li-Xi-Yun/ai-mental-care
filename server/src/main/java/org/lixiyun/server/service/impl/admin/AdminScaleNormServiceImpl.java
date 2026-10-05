package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormDeleteDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormGroupDTO;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleNorm;
import org.lixiyun.pojo.entity.scale.ScaleNormGroup;
import org.lixiyun.pojo.entity.scale.ScaleUserRecord;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleNormGroupVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleNormVO;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleNormGroupMapper;
import org.lixiyun.server.mapper.ScaleNormMapper;
import org.lixiyun.server.mapper.ScaleUserRecordMapper;
import org.lixiyun.server.service.admin.AdminScaleNormService;
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
 * 管理员量表常模服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleNormServiceImpl implements AdminScaleNormService {

    private final ScaleNormGroupMapper scaleNormGroupMapper;
    private final ScaleNormMapper scaleNormMapper;
    private final ScaleUserRecordMapper scaleUserRecordMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;

    @Override
    public List<AdminScaleNormGroupVO> listNormGroups(Long scaleVersionId) {
        log.info("[AdminScaleNormServiceImpl-查询常模组列表]，版本ID：{}", scaleVersionId);

        List<ScaleNormGroup> groups = scaleNormGroupMapper.selectList(new LambdaQueryWrapper<ScaleNormGroup>()
                .eq(ScaleNormGroup::getScaleVersionId, scaleVersionId));
        if (CollUtil.isEmpty(groups)) {
            return new ArrayList<>();
        }

        Set<Long> groupIds = groups.stream().map(ScaleNormGroup::getId).collect(Collectors.toSet());
        Map<Long, Long> normCountMap = new HashMap<>();
        if (CollUtil.isNotEmpty(groupIds)) {
            List<ScaleNorm> norms = scaleNormMapper.selectList(new LambdaQueryWrapper<ScaleNorm>()
                    .in(CollUtil.isNotEmpty(groupIds), ScaleNorm::getNormGroupId, groupIds));
            normCountMap.putAll(norms.stream()
                    .collect(Collectors.groupingBy(ScaleNorm::getNormGroupId, Collectors.counting())));
        }

        Set<Long> dimensionIds = groups.stream().map(ScaleNormGroup::getDimensionId)
                .filter(ObjectUtil::isNotNull).collect(Collectors.toSet());
        Map<Long, String> dimensionNameMap = new HashMap<>();
        if (CollUtil.isNotEmpty(dimensionIds)) {
            dimensionNameMap.putAll(scaleDimensionMapper.selectBatchIds(dimensionIds).stream()
                    .collect(Collectors.toMap(ScaleDimension::getId, ScaleDimension::getDimName, (a, b) -> a)));
        }

        List<AdminScaleNormGroupVO> voList = groups.stream().map(group -> {
            AdminScaleNormGroupVO vo = BeanUtil.copyProperties(group, AdminScaleNormGroupVO.class);
            vo.setDimensionName(dimensionNameMap.get(group.getDimensionId()));
            Long count = normCountMap.get(group.getId());
            vo.setNormCount(count == null ? 0 : count.intValue());
            return vo;
        }).collect(Collectors.toList());

        log.debug("[AdminScaleNormServiceImpl-查询常模组列表]完成，常模组数：{}", voList.size());
        return voList;
    }

    @Override
    public AdminScaleNormGroupVO getNormGroupDetail(Long groupId) {
        log.info("[AdminScaleNormServiceImpl-查询常模组详情]，常模组ID：{}", groupId);

        ScaleNormGroup group = scaleNormGroupMapper.selectById(groupId);
        if (group == null) {
            log.error("[AdminScaleNormServiceImpl-查询常模组详情]常模组不存在，常模组ID：{}", groupId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_GROUP_NOT_FOUND);
        }

        List<ScaleNorm> norms = scaleNormMapper.selectList(new LambdaQueryWrapper<ScaleNorm>()
                .eq(ScaleNorm::getNormGroupId, groupId)
                .orderByAsc(ScaleNorm::getRawScore));

        AdminScaleNormGroupVO vo = BeanUtil.copyProperties(group, AdminScaleNormGroupVO.class);
        if (group.getDimensionId() != null) {
            ScaleDimension dimension = scaleDimensionMapper.selectById(group.getDimensionId());
            if (dimension != null) {
                vo.setDimensionName(dimension.getDimName());
            }
        }
        vo.setNormCount(norms.size());
        log.debug("[AdminScaleNormServiceImpl-查询常模组详情]完成，常模明细数：{}", norms.size());
        return vo;
    }

    @Override
    public void createNormGroup(AdminScaleNormGroupDTO dto) {
        log.info("[AdminScaleNormServiceImpl-新增常模组]，参数：{}", dto);

        ScaleNormGroup entity = BeanUtil.copyProperties(dto, ScaleNormGroup.class);
        int row = scaleNormGroupMapper.insert(entity);
        if (row == 0) {
            log.error("[AdminScaleNormServiceImpl-新增常模组]插入失败，参数：{}", dto);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_GROUP_ADD_FAIL);
        }
        log.info("[AdminScaleNormServiceImpl-新增常模组]成功，常模组ID：{}", entity.getId());
    }

    @Override
    public void updateNormGroup(Long groupId, AdminScaleNormGroupDTO dto) {
        log.info("[AdminScaleNormServiceImpl-修改常模组]，常模组ID：{}", groupId);

        ScaleNormGroup entity = BeanUtil.copyProperties(dto, ScaleNormGroup.class);
        entity.setId(groupId);
        int row = scaleNormGroupMapper.updateById(entity);
        if (row == 0) {
            log.error("[AdminScaleNormServiceImpl-修改常模组]常模组不存在或更新失败，常模组ID：{}", groupId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_GROUP_NOT_FOUND);
        }
        log.info("[AdminScaleNormServiceImpl-修改常模组]成功，常模组ID：{}", groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNormGroup(Long groupId) {
        log.info("[AdminScaleNormServiceImpl-删除常模组]，常模组ID：{}", groupId);

        Long usedCount = scaleUserRecordMapper.selectCount(new LambdaQueryWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getNormGroupId, groupId));
        if (usedCount != null && usedCount > 0) {
            log.error("[AdminScaleNormServiceImpl-删除常模组]常模组已关联测评记录，不允许删除，常模组ID：{}", groupId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_GROUP_FORBIDDEN_DELETE);
        }

        scaleNormMapper.delete(new LambdaQueryWrapper<ScaleNorm>().eq(ScaleNorm::getNormGroupId, groupId));
        int row = scaleNormGroupMapper.deleteById(groupId);
        if (row == 0) {
            log.error("[AdminScaleNormServiceImpl-删除常模组]删除失败，常模组ID：{}", groupId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_GROUP_DELETE_FAIL);
        }
        log.info("[AdminScaleNormServiceImpl-删除常模组]成功，常模组ID：{}", groupId);
    }

    @Override
    public List<AdminScaleNormVO> listNorms(Long normGroupId) {
        log.info("[AdminScaleNormServiceImpl-查询常模明细列表]，常模组ID：{}", normGroupId);

        List<ScaleNorm> norms = scaleNormMapper.selectList(new LambdaQueryWrapper<ScaleNorm>()
                .eq(ScaleNorm::getNormGroupId, normGroupId)
                .orderByAsc(ScaleNorm::getRawScore));
        if (CollUtil.isEmpty(norms)) {
            return new ArrayList<>();
        }

        List<AdminScaleNormVO> voList = norms.stream()
                .map(norm -> BeanUtil.copyProperties(norm, AdminScaleNormVO.class))
                .collect(Collectors.toList());
        log.debug("[AdminScaleNormServiceImpl-查询常模明细列表]完成，明细数：{}", voList.size());
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveNorms(AdminScaleNormBatchDTO dto) {
        Long normGroupId = dto.getNormGroupId();
        List<AdminScaleNormDTO> items = dto.getItems();
        log.info("[AdminScaleNormServiceImpl-批量保存常模明细]，常模组ID：{}，明细数：{}", normGroupId, CollUtil.size(items));

        List<ScaleNorm> existing = scaleNormMapper.selectList(new LambdaQueryWrapper<ScaleNorm>()
                .eq(ScaleNorm::getNormGroupId, normGroupId));
        Set<Long> existingIds = existing.stream().map(ScaleNorm::getId).collect(Collectors.toSet());
        Set<Long> submitIds = items.stream().map(AdminScaleNormDTO::getId)
                .filter(ObjectUtil::isNotNull).collect(Collectors.toSet());

        for (AdminScaleNormDTO item : items) {
            if (item.getId() != null && existingIds.contains(item.getId())) {
                ScaleNorm entity = BeanUtil.copyProperties(item, ScaleNorm.class);
                entity.setId(item.getId());
                if (scaleNormMapper.updateById(entity) == 0) {
                    log.error("[AdminScaleNormServiceImpl-批量保存常模明细]更新失败，明细ID：{}", item.getId());
                    throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_DETAIL_NOT_FOUND);
                }
            } else if (item.getId() == null) {
                if (scaleNormRawExists(normGroupId, item.getDimensionId(), item.getRawScore(), null)) {
                    log.error("[AdminScaleNormServiceImpl-批量保存常模明细]原始分已存在，原始分：{}", item.getRawScore());
                    throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_RAW_DUPLICATE);
                }
                ScaleNorm entity = BeanUtil.copyProperties(item, ScaleNorm.class);
                entity.setNormGroupId(normGroupId);
                if (scaleNormMapper.insert(entity) == 0) {
                    log.error("[AdminScaleNormServiceImpl-批量保存常模明细]新增失败，参数：{}", item);
                    throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_ADD_FAIL);
                }
            }
        }

        // 删除库中存在但提交集合中已不存在的明细
        for (Long id : existingIds) {
            if (!submitIds.contains(id)) {
                scaleNormMapper.deleteById(id);
            }
        }
        log.info("[AdminScaleNormServiceImpl-批量保存常模明细]完成，常模组ID：{}", normGroupId);
    }

    @Override
    public void updateNorm(Long normId, AdminScaleNormDTO dto) {
        log.info("[AdminScaleNormServiceImpl-修改常模明细]，明细ID：{}", normId);

        if (scaleNormRawExists(dto.getNormGroupId(), dto.getDimensionId(), dto.getRawScore(), normId)) {
            log.error("[AdminScaleNormServiceImpl-修改常模明细]原始分已存在，原始分：{}", dto.getRawScore());
            throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_RAW_DUPLICATE);
        }

        ScaleNorm entity = BeanUtil.copyProperties(dto, ScaleNorm.class);
        entity.setId(normId);
        if (scaleNormMapper.updateById(entity) == 0) {
            log.error("[AdminScaleNormServiceImpl-修改常模明细]常模明细不存在，明细ID：{}", normId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_DETAIL_NOT_FOUND);
        }
        log.info("[AdminScaleNormServiceImpl-修改常模明细]成功，明细ID：{}", normId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteNorms(AdminScaleNormDeleteDTO dto) {
        Long normGroupId = dto.getNormGroupId();
        List<Long> normIds = dto.getNormIds();
        log.info("[AdminScaleNormServiceImpl-批量删除常模明细]，常模组ID：{}，明细ID：{}", normGroupId, normIds);

        ScaleNormGroup group = scaleNormGroupMapper.selectById(normGroupId);
        if (group == null) {
            log.error("[AdminScaleNormServiceImpl-批量删除常模明细]常模组不存在，常模组ID：{}", normGroupId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NORM_GROUP_NOT_FOUND);
        }

        scaleNormMapper.deleteByIds(normIds);
        log.info("[AdminScaleNormServiceImpl-批量删除常模明细]成功，常模组ID：{}，删除数量：{}", normGroupId, CollUtil.size(normIds));
    }

    /**
     * 校验同一常模组下同一维度同一原始分是否已存在
     *
     * @param normGroupId 常模组ID
     * @param dimensionId 维度ID
     * @param rawScore    原始分
     * @param excludeId   需排除的明细ID（自身），可为空
     * @return true表示已存在
     */
    private boolean scaleNormRawExists(Long normGroupId, Long dimensionId, BigDecimal rawScore, Long excludeId) {
        Long count = scaleNormMapper.selectCount(new LambdaQueryWrapper<ScaleNorm>()
                .eq(ScaleNorm::getNormGroupId, normGroupId)
                .eq(dimensionId != null, ScaleNorm::getDimensionId, dimensionId)
                .isNull(dimensionId == null, ScaleNorm::getDimensionId)
                .eq(ScaleNorm::getRawScore, rawScore)
                .ne(excludeId != null, ScaleNorm::getId, excludeId));
        return count != null && count > 0;
    }
}