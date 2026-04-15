package org.lixiyun.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.constant.DeleteConstant;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.scale.ScaleOptionDTO;
import org.lixiyun.pojo.dto.scale.ScaleQuestionDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.vo.scale.ScaleOptionVO;
import org.lixiyun.pojo.vo.scale.ScaleQuestionVO;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.service.ScaleQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 16:27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleQuestionServiceImpl implements ScaleQuestionService {

    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleMapper scaleMapper;

    @Override
    @Transactional
    public void createWithOptions(ScaleQuestionDTO scaleQuestionDTO) {
        // 插入题目
        ScaleQuestion question = new ScaleQuestion();
        BeanUtil.copyProperties(scaleQuestionDTO, question);
        scaleQuestionMapper.insert(question);
        Long questionId = question.getId();

        // 插入选项
        List<ScaleOptionDTO> optionList = scaleQuestionDTO.getOptionList();
        List<ScaleOption> options = optionList.stream()
            .map(optionDTO -> {
                ScaleOption option = BeanUtil.copyProperties(optionDTO, ScaleOption.class);
                option.setQuestionId(questionId);
                return option;
            })
            .toList();
        scaleOptionMapper.insert(options);

        // 更新量表的题目数量
        scaleMapper.updateQuestionCount(scaleQuestionDTO.getScaleId(), 1);

        log.info("题目及选项创建成功，题目ID: {}", questionId);
    }

    @Override
    public ScaleQuestionVO getWithOptions(Long questionId) {
        // 获取题目
        ScaleQuestion question = scaleQuestionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_NOT_FOUND);
        }

        // 检查量表是否启用且未删除
        Scale scale = scaleMapper.selectById(question.getScaleId());
        if (scale == null || scale.getStatus() == Scale.STATUS_DISABLE || scale.getDeleted() == DeleteConstant.DELETE_FLAG_YES) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }

        // 获取选项
        List<ScaleOption> options = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                .eq(ScaleOption::getQuestionId, questionId)
                .orderByAsc(ScaleOption::getSort));

        // 转换为VO
        ScaleQuestionVO vo = BeanUtil.copyProperties(question, ScaleQuestionVO.class);
        List<ScaleOptionVO> optionDTOs = options.stream()
                .map(option -> BeanUtil.copyProperties(option, ScaleOptionVO.class))
                .toList();
        vo.setOptionList(optionDTOs);

        return vo;
    }

    @Override
    @Transactional
    public void deleteWithOptions(Long questionId, Long scaleId) {
        // 级联删除选项
        scaleOptionMapper.delete(new LambdaQueryWrapper<ScaleOption>()
                .eq(ScaleOption::getQuestionId, questionId));
        // 删除题目
        scaleQuestionMapper.deleteById(questionId);

        // 更新量表的题目数量
        scaleMapper.updateQuestionCount(scaleId, -1);

        log.info("题目及选项删除成功，题目ID: {}", questionId);
    }

    @Override
    public void updateMetadata(Long questionId, ScaleQuestionDTO scaleQuestionDTO) {
        // 只更新题目元数据信息，不更新选项
        ScaleQuestion question = new ScaleQuestion();
        BeanUtil.copyProperties(scaleQuestionDTO, question);
        question.setId(questionId);
        // 确保不更新scaleId，如果DTO中有的话
        question.setScaleId(null); // 防止修改量表ID
        scaleQuestionMapper.updateById(question);

        log.info("题目元数据修改成功，题目ID: {}", questionId);
    }
}
