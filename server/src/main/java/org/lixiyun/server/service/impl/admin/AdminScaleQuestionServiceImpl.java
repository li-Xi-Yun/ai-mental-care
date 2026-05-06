package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.user.scale.ScaleOptionDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleQuestionDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.vo.user.scale.ScaleOptionVO;
import org.lixiyun.pojo.vo.user.scale.ScaleQuestionVO;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.service.admin.AdminScaleQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author lixiyun
 * @since 2026-04-19 15:37
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleQuestionServiceImpl implements AdminScaleQuestionService {

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
                .map(option -> ScaleOptionVO.builder()
                        .optionText(option.getOptionText())
                        .score(option.getScore())
                        .sort(option.getSort())
                        .build())
                .toList();
        vo.setOptionList(optionDTOs);

        return vo;
    }

    @Override
    public PageResult<ScaleQuestionVO> listQuestionsWithOptions(Long scaleId, Integer pageNum, Integer pageSize) {
        // 检查量表是否存在
        Scale scale = scaleMapper.selectMyById(scaleId);
        if (scale == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }

        // 构建分页对象
        Page<ScaleQuestion> pageParam = new PageQuery(pageSize, pageNum).build();

        // 查询指定量表下的题目，按排序字段升序排列
        Page<ScaleQuestion> questionPage = scaleQuestionMapper.selectPage(pageParam, new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleId, scaleId)
                .eq(ScaleQuestion::getDeleted, scale.getDeleted())
                .orderByAsc(ScaleQuestion::getSort));

        // 获取当前页的所有题目ID
        List<Long> questionIds = questionPage.getRecords().stream()
                .map(ScaleQuestion::getId)
                .toList();

        // 批量查询这些题目的所有选项
        Map<Long, List<ScaleOption>> optionMap = Map.of();
        if (!questionIds.isEmpty()) {
            List<ScaleOption> allOptions = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                    .in(ScaleOption::getQuestionId, questionIds)
                    .eq(ScaleOption::getDeleted, scale.getDeleted())
                    .orderByAsc(ScaleOption::getSort));

            // 使用Stream将选项按题目ID分组
            optionMap = allOptions.stream()
                    .collect(Collectors.groupingBy(ScaleOption::getQuestionId));
        }

        // 使用Stream将题目和选项组合成VO
        final Map<Long, List<ScaleOption>> finalOptionMap = optionMap;
        List<ScaleQuestionVO> voList = questionPage.getRecords().stream()
                .map(question -> {
                    ScaleQuestionVO vo = BeanUtil.copyProperties(question, ScaleQuestionVO.class);
                    List<ScaleOption> options = finalOptionMap.getOrDefault(question.getId(), List.of());
                    List<ScaleOptionVO> optionVOs = options.stream()
                            .map(option -> ScaleOptionVO.builder()
                                    .id(option.getId())
                                    .optionText(option.getOptionText())
                                    .score(option.getScore())
                                    .sort(option.getSort())
                                    .build())
                            .toList();
                    vo.setOptionList(optionVOs);
                    return vo;
                })
                .toList();

        // 返回分页结果
        return new PageResult<>(questionPage.getTotal(), voList);
    }

    @Override
    @Transactional
    public void deleteWithOptions(Long questionId, Long scaleId) {
        // 删除题目
        scaleQuestionMapper.deletePhysics(questionId);
        // 级联删除选项
        scaleOptionMapper.deletePhysics(questionId);

        // 更新量表的题目数量
        scaleMapper.updateQuestionCount(scaleId, -1);

        log.info("题目及选项删除成功，题目ID: {}", questionId);
    }

    @Override
    @Transactional
    public void updateMetadata(Long questionId, ScaleQuestionDTO scaleQuestionDTO) {
        // 只更新题目元数据信息，不更新选项
        ScaleQuestion question = new ScaleQuestion();
        BeanUtil.copyProperties(scaleQuestionDTO, question);
        question.setId(questionId);
        // 确保不更新scaleId，如果DTO中有的话
        question.setScaleId(null); // 防止修改量表ID
        scaleQuestionMapper.updateById(question);

        // 级联删除选项
        scaleOptionMapper.deletePhysics(questionId);

        // 批量插入选项
        List<ScaleOptionDTO> optionDTOList = scaleQuestionDTO.getOptionList();
        List<ScaleOption> scaleOptionList = optionDTOList.stream().map(item -> {
            ScaleOption option = BeanUtil.copyProperties(item, ScaleOption.class);
            option.setQuestionId(questionId);
            return option;
        }).toList();

        scaleOptionMapper.insert(scaleOptionList);


        log.info("题目元数据修改成功，题目ID: {}", questionId);
    }

}
