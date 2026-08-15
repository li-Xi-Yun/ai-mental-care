package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.admin.scale.ScaleOptionBatchAddDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleOptionDTO;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.service.admin.AdminScaleOptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lixiyun
 * @since 2026-04-15 16:27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleOptionServiceImpl implements AdminScaleOptionService {

    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddOptions(ScaleOptionBatchAddDTO addDTO) {
        Long questionId = addDTO.getQuestionId();
        List<ScaleOptionDTO> optionDTOs = addDTO.getOptions();

        // 验证题目是否存在
        ScaleQuestion question = scaleQuestionMapper.selectMyById(questionId);
        if (question == null) {
            log.error("题目不存在，questionId: {}", questionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_NOT_FOUND);
        }

        // 使用Stream将DTO转换为实体对象
        List<ScaleOption> options = optionDTOs.stream()
                .map(dto -> {
                    ScaleOption option = new ScaleOption();
                    BeanUtil.copyProperties(dto, option);
                    option.setQuestionId(questionId);
                    return option;
                })
                .collect(Collectors.toList());

        // 批量插入选项
        scaleOptionMapper.insert(options);

        log.info("批量新增选项成功，题目ID: {}, 新增数量: {}", questionId, options.size());
    }

    @Override
    public void deleteOptions(Long questionId, List<Long> optionIds) {
        // 物理删除
        scaleOptionMapper.deletePhysicsWithOptions(questionId, optionIds);
    }

    @Override
    public void updateOption(Long optionId, ScaleOptionDTO updateDTO) {
        ScaleOption scaleOption = new ScaleOption();
        BeanUtil.copyProperties(updateDTO, scaleOption);
        scaleOption.setId(optionId);
        scaleOptionMapper.updateById(scaleOption);
    }
}
