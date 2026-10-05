package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleDimensionDTO;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleDimensionVO;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.service.admin.AdminScaleDimensionService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员量表维度服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleDimensionServiceImpl implements AdminScaleDimensionService {

    private final ScaleDimensionMapper scaleDimensionMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;

    @Override
    public List<AdminScaleDimensionVO> listDimensions(Long scaleVersionId) {
        log.info("查询版本维度列表，版本ID：{}", scaleVersionId);
        List<ScaleDimension> dimensions = scaleDimensionMapper.selectList(new LambdaQueryWrapper<ScaleDimension>()
                .eq(ScaleDimension::getScaleVersionId, scaleVersionId)
                .orderByAsc(ScaleDimension::getSort));
        if (CollUtil.isEmpty(dimensions)) {
            return Collections.emptyList();
        }
        List<Long> dimensionIds = StreamUtils.toList(dimensions, ScaleDimension::getId);
        Map<Long, Long> questionCountMap = CollUtil.isEmpty(dimensionIds) ? Collections.emptyMap()
                : scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                        .in(ScaleQuestion::getDimensionId, dimensionIds))
                .stream().collect(Collectors.groupingBy(ScaleQuestion::getDimensionId, Collectors.counting()));
        return StreamUtils.toList(dimensions, dimension -> {
            AdminScaleDimensionVO vo = BeanUtil.copyProperties(dimension, AdminScaleDimensionVO.class);
            vo.setQuestionCount(questionCountMap.getOrDefault(dimension.getId(), 0L).intValue());
            return vo;
        });
    }

    @Override
    public void createDimension(AdminScaleDimensionDTO dto) {
        log.info("新增维度，版本ID：{}，参数：{}", dto.getScaleVersionId(), dto.getDimName());
        Long count = scaleDimensionMapper.selectCount(new LambdaQueryWrapper<ScaleDimension>()
                .eq(ScaleDimension::getScaleVersionId, dto.getScaleVersionId())
                .eq(ScaleDimension::getDimCode, dto.getDimCode()));
        if (count > 0) {
            log.warn("维度编码已存在，版本ID：{}，编码：{}", dto.getScaleVersionId(), dto.getDimCode());
            throw new BusinessException(ScaleExceptionEnum.SCALE_DIMENSION_CODE_EXISTS);
        }
        ScaleDimension dimension = BeanUtil.copyProperties(dto, ScaleDimension.class);
        dimension.setId(null);
        int row = scaleDimensionMapper.insert(dimension);
        if (row == 0) {
            log.error("量表维度新增失败：{}", dto.getDimName());
            throw new BusinessException(ScaleExceptionEnum.SCALE_DIMENSION_ADD_FAIL);
        }
        log.info("量表维度新增成功，维度ID：{}", dimension.getId());
    }

    @Override
    public void updateDimension(Long dimensionId, AdminScaleDimensionDTO dto) {
        log.info("修改维度，维度ID：{}，参数：{}", dimensionId, dto.getDimName());
        ScaleDimension dimension = BeanUtil.copyProperties(dto, ScaleDimension.class);
        dimension.setId(dimensionId);
        int row = scaleDimensionMapper.updateById(dimension);
        if (row == 0) {
            log.warn("量表维度不存在，维度ID：{}", dimensionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_DIMENSION_NOT_FOUND);
        }
        log.info("量表维度修改成功，维度ID：{}", dimensionId);
    }

    @Override
    public void updateDimensionSort(Long dimensionId, Integer sort) {
        log.info("调整维度排序，维度ID：{}，排序：{}", dimensionId, sort);
        scaleDimensionMapper.update(null, new LambdaUpdateWrapper<ScaleDimension>()
                .eq(ScaleDimension::getId, dimensionId)
                .set(ScaleDimension::getSort, sort));
    }

    @Override
    public void deleteDimension(Long dimensionId) {
        log.info("删除维度，维度ID：{}", dimensionId);
        Long questionCount = scaleQuestionMapper.selectCount(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getDimensionId, dimensionId));
        if (questionCount > 0) {
            log.warn("维度下存在题目，无法删除，维度ID：{}", dimensionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_DIMENSION_IN_USE);
        }
        scaleDimensionMapper.deleteById(dimensionId);
        log.info("维度删除成功，维度ID：{}", dimensionId);
    }
}
