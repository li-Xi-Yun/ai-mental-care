package org.lixiyun.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.dto.scale.ScaleOptionDTO;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.service.ScaleOptionService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 16:27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleOptionServiceImpl implements ScaleOptionService {

    private final ScaleOptionMapper scaleOptionMapper;


    @Override
    public void deleteOptions(Long questionId, List<Long> optionIds) {
        scaleOptionMapper.delete(new LambdaQueryWrapper<ScaleOption>()
                .eq(ScaleOption::getQuestionId, questionId)
                .in(ScaleOption::getId, optionIds));
    }

    @Override
    public void updateOption(Long optionId, ScaleOptionDTO updateDTO) {
        ScaleOption scaleOption = new ScaleOption();
        BeanUtil.copyProperties(updateDTO, scaleOption);
        scaleOption.setId(optionId);
        scaleOptionMapper.updateById(scaleOption);
    }
}
