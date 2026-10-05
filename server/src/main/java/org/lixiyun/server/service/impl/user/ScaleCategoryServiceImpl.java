package org.lixiyun.server.service.impl.user;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleCategory;
import org.lixiyun.pojo.vo.user.scale.ScaleCategoryVO;
import org.lixiyun.server.mapper.ScaleCategoryMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.service.user.ScaleCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 量表类别前台服务实现
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleCategoryServiceImpl implements ScaleCategoryService {

    private final ScaleCategoryMapper scaleCategoryMapper;
    private final ScaleMapper scaleMapper;

    @Override
    public List<ScaleCategoryVO> listCategories() {
        log.info("[量表类别-列表]，查询量表类别列表");
        List<ScaleCategory> categories = scaleCategoryMapper.selectList(new LambdaQueryWrapper<ScaleCategory>()
                .orderByAsc(ScaleCategory::getSort)
                .orderByAsc(ScaleCategory::getId));
        if (CollUtil.isEmpty(categories)) {
            log.debug("[量表类别-列表]，无可用类别数据");
            return new ArrayList<>();
        }

        // 查询全部启用且存在当前版本的未删除量表，按类别 ID 聚合计数
        List<Scale> enabledScales = scaleMapper.selectList(new LambdaQueryWrapper<Scale>()
                .eq(Scale::getStatus, Scale.STATUS_ENABLE)
                .isNotNull(Scale::getCurrentVersionId));
        Map<Long, Long> scaleCountByCategory = new HashMap<>();
        if (CollUtil.isNotEmpty(enabledScales)) {
            scaleCountByCategory = enabledScales.stream()
                    .filter(scale -> scale.getScaleCategoryId() != null)
                    .collect(Collectors.groupingBy(Scale::getScaleCategoryId, Collectors.counting()));
        }

        List<ScaleCategoryVO> voList = new ArrayList<>();
        for (ScaleCategory category : categories) {
            Long scaleCount = scaleCountByCategory.getOrDefault(category.getId(), 0L);
            ScaleCategoryVO vo = ScaleCategoryVO.builder()
                    .id(category.getId())
                    .categoryName(category.getCategoryName())
                    .sort(category.getSort())
                    .scaleCount(scaleCount)
                    .build();
            voList.add(vo);
        }
        log.info("[量表类别-列表]，返回类别数量：{}", voList.size());
        return voList;
    }
}