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
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionDeleteDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionSortItemDTO;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.service.admin.AdminScaleOptionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 管理员量表选项服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleOptionServiceImpl implements AdminScaleOptionService {

    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;

    @Override
    public void batchAddOptions(AdminScaleOptionBatchDTO dto) {
        log.info("为题目批量新增选项，题目ID：{}", dto.getQuestionId());
        Long questionId = dto.getQuestionId();
        ScaleQuestion question = scaleQuestionMapper.selectById(questionId);
        if (question == null) {
            log.warn("量表题目不存在，题目ID：{}", questionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_NOT_FOUND);
        }
        List<ScaleOption> options = StreamUtils.toList(dto.getOptions(), item -> {
            ScaleOption option = BeanUtil.copyProperties(item, ScaleOption.class);
            option.setQuestionId(questionId);
            return option;
        });
        if (!Db.saveBatch(options)) {
            log.error("选项新增失败，题目ID：{}", questionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_ADD_FAIL);
        }
        log.info("选项批量新增成功，题目ID：{}，数量：{}", questionId, options.size());
    }

    @Override
    public void updateOption(Long optionId, AdminScaleOptionDTO dto) {
        log.info("修改选项，选项ID：{}，参数：{}", optionId, dto.getOptionText());
        ScaleOption option = BeanUtil.copyProperties(dto, ScaleOption.class);
        option.setId(optionId);
        int row = scaleOptionMapper.updateById(option);
        if (row == 0) {
            log.warn("量表选项不存在，选项ID：{}", optionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_NOT_FOUND);
        }
        log.info("选项修改成功，选项ID：{}", optionId);
    }

    @Override
    public void updateOptionSort(List<AdminScaleOptionSortItemDTO> sortItems) {
        log.info("批量调整选项顺序，参数：{}", sortItems);
        if (CollUtil.isEmpty(sortItems)) {
            return;
        }
        List<ScaleOption> options = StreamUtils.toList(sortItems, item -> {
            ScaleOption option = new ScaleOption();
            option.setId(item.getOptionId());
            option.setSort(item.getSort());
            return option;
        });
        Db.updateBatchById(options);
    }

    @Override
    public void batchDeleteOptions(AdminScaleOptionDeleteDTO dto) {
        log.info("批量删除选项，题目ID：{}，选项ID：{}", dto.getQuestionId(), dto.getOptionIds());
        List<Long> optionIds = dto.getOptionIds();
        List<ScaleOption> options = scaleOptionMapper.selectBatchIds(optionIds);
        if (CollUtil.isEmpty(options) || options.size() != optionIds.size()) {
            log.warn("批量删除选项校验失败：选项不存在，题目ID：{}", dto.getQuestionId());
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_NOT_FOUND);
        }
        for (ScaleOption option : options) {
            if (!Objects.equals(option.getQuestionId(), dto.getQuestionId())) {
                log.warn("批量删除选项校验失败：选项不属于该题目，题目ID：{}，选项ID：{}", dto.getQuestionId(), option.getId());
                throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_NOT_FOUND);
            }
        }
        scaleOptionMapper.deleteByIds(optionIds);
        log.info("选项批量删除成功，题目ID：{}，数量：{}", dto.getQuestionId(), optionIds.size());
    }
}