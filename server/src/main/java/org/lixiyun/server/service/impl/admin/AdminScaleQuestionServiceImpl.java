package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionItemDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQuestionDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQuestionSortItemDTO;
import org.lixiyun.pojo.entity.scale.ScaleBranchRule;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleOptionVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleQuestionVO;
import org.lixiyun.server.mapper.ScaleBranchRuleMapper;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.service.admin.AdminScaleQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理员量表题目服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleQuestionServiceImpl implements AdminScaleQuestionService {

    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;
    private final ScaleBranchRuleMapper scaleBranchRuleMapper;

    @Override
    public List<AdminScaleQuestionVO> listQuestions(Long scaleVersionId) {
        log.info("查询版本题目列表，版本ID：{}", scaleVersionId);
        List<ScaleQuestion> questions = scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, scaleVersionId)
                .orderByAsc(ScaleQuestion::getSort)
                .orderByAsc(ScaleQuestion::getId));
        if (CollUtil.isEmpty(questions)) {
            return Collections.emptyList();
        }
        List<Long> questionIds = StreamUtils.toList(questions, ScaleQuestion::getId);
        List<ScaleOption> options = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                .in(ScaleOption::getQuestionId, questionIds)
                .orderByAsc(ScaleOption::getSort)
                .orderByAsc(ScaleOption::getId));
        Map<Long, List<ScaleOption>> optionMap = StreamUtils.groupByKey(options, ScaleOption::getQuestionId);
        List<Long> dimensionIds = questions.stream().map(ScaleQuestion::getDimensionId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, ScaleDimension> dimensionMap = CollUtil.isEmpty(dimensionIds) ? Collections.emptyMap()
                : StreamUtils.toIdentityMap(scaleDimensionMapper.selectBatchIds(dimensionIds), ScaleDimension::getId);
        return StreamUtils.toList(questions, question -> {
            AdminScaleQuestionVO vo = BeanUtil.copyProperties(question, AdminScaleQuestionVO.class);
            ScaleDimension dimension = dimensionMap.get(question.getDimensionId());
            if (dimension != null) {
                vo.setDimensionName(dimension.getDimName());
            }
            List<ScaleOption> questionOptions = optionMap.get(question.getId());
            if (CollUtil.isNotEmpty(questionOptions)) {
                vo.setOptions(StreamUtils.toListVO(questionOptions, AdminScaleOptionVO.class));
            }
            return vo;
        });
    }

    @Override
    public AdminScaleQuestionVO getQuestionDetail(Long questionId) {
        log.info("查询题目详情，题目ID：{}", questionId);
        ScaleQuestion question = scaleQuestionMapper.selectById(questionId);
        if (question == null) {
            log.warn("量表题目不存在，题目ID：{}", questionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_NOT_FOUND);
        }
        AdminScaleQuestionVO vo = BeanUtil.copyProperties(question, AdminScaleQuestionVO.class);
        if (question.getDimensionId() != null) {
            ScaleDimension dimension = scaleDimensionMapper.selectById(question.getDimensionId());
            if (dimension != null) {
                vo.setDimensionName(dimension.getDimName());
            }
        }
        List<ScaleOption> options = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                .eq(ScaleOption::getQuestionId, questionId)
                .orderByAsc(ScaleOption::getSort)
                .orderByAsc(ScaleOption::getId));
        if (CollUtil.isNotEmpty(options)) {
            vo.setOptions(StreamUtils.toListVO(options, AdminScaleOptionVO.class));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createQuestion(AdminScaleQuestionDTO dto) {
        log.info("新增题目，版本ID：{}，参数：{}", dto.getScaleVersionId(), dto.getTitle());
        ScaleQuestion question = BeanUtil.copyProperties(dto, ScaleQuestion.class);
        question.setId(null);
        int row = scaleQuestionMapper.insert(question);
        if (row == 0) {
            log.error("量表题目新增失败：{}", dto.getTitle());
            throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_ADD_FAIL);
        }
        List<AdminScaleOptionItemDTO> options = dto.getOptions();
        if (CollUtil.isNotEmpty(options) && !Objects.equals(dto.getQuestionType(), ScaleQuestion.QUESTION_TYPE_FILL)) {
            Db.saveBatch(buildOptions(options, question.getId()));
        }
        log.info("量表题目新增成功，题目ID：{}", question.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestion(Long questionId, AdminScaleQuestionDTO dto) {
        log.info("修改题目，题目ID：{}，参数：{}", questionId, dto.getTitle());
        ScaleQuestion question = BeanUtil.copyProperties(dto, ScaleQuestion.class);
        question.setId(questionId);
        int row = scaleQuestionMapper.updateById(question);
        if (row == 0) {
            log.warn("量表题目不存在，题目ID：{}", questionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_NOT_FOUND);
        }
        List<AdminScaleOptionItemDTO> options = dto.getOptions();
        if (options != null) {
            scaleOptionMapper.delete(new LambdaQueryWrapper<ScaleOption>().eq(ScaleOption::getQuestionId, questionId));
            if (CollUtil.isNotEmpty(options)) {
                Db.saveBatch(buildOptions(options, questionId));
            }
        }
        log.info("量表题目修改成功，题目ID：{}", questionId);
    }

    @Override
    public void updateQuestionSort(List<AdminScaleQuestionSortItemDTO> sortItems) {
        log.info("批量调整题目顺序，参数：{}", sortItems);
        if (CollUtil.isEmpty(sortItems)) {
            return;
        }
        List<ScaleQuestion> questions = StreamUtils.toList(sortItems, item -> {
            ScaleQuestion question = new ScaleQuestion();
            question.setId(item.getQuestionId());
            question.setSort(item.getSort());
            return question;
        });
        Db.updateBatchById(questions);
    }

    @Override
    public void updateQuestionDimension(Long questionId, Long dimensionId) {
        log.info("调整题目所属维度，题目ID：{}，维度ID：{}", questionId, dimensionId);
        scaleQuestionMapper.update(null, new LambdaUpdateWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getId, questionId)
                .set(ScaleQuestion::getDimensionId, dimensionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyQuestion(Long questionId, Long targetScaleVersionId) {
        log.info("复制题目，题目ID：{}，目标版本ID：{}", questionId, targetScaleVersionId);
        ScaleQuestion source = scaleQuestionMapper.selectById(questionId);
        if (source == null) {
            log.warn("量表题目不存在，题目ID：{}", questionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_NOT_FOUND);
        }
        ScaleQuestion newQuestion = BeanUtil.copyProperties(source, ScaleQuestion.class);
        newQuestion.setId(null);
        newQuestion.setScaleVersionId(targetScaleVersionId);
        int row = scaleQuestionMapper.insert(newQuestion);
        if (row == 0) {
            log.error("复制题目失败，题目ID：{}", questionId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_COPY_FAIL);
        }
        List<ScaleOption> options = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                .eq(ScaleOption::getQuestionId, questionId)
                .orderByAsc(ScaleOption::getSort)
                .orderByAsc(ScaleOption::getId));
        if (CollUtil.isNotEmpty(options)) {
            List<ScaleOption> newOptions = new ArrayList<>();
            options.forEach(option -> {
                ScaleOption newOption = BeanUtil.copyProperties(option, ScaleOption.class);
                newOption.setId(null);
                newOption.setQuestionId(newQuestion.getId());
                newOptions.add(newOption);
            });
            Db.saveBatch(newOptions);
        }
        log.info("题目复制成功，题目ID：{}，新题目ID：{}", questionId, newQuestion.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long questionId) {
        log.info("删除题目，题目ID：{}", questionId);
        scaleOptionMapper.delete(new LambdaQueryWrapper<ScaleOption>().eq(ScaleOption::getQuestionId, questionId));
        scaleBranchRuleMapper.delete(new LambdaQueryWrapper<ScaleBranchRule>().eq(ScaleBranchRule::getSourceQuestionId, questionId));
        scaleQuestionMapper.deleteById(questionId);
        log.info("题目删除成功，题目ID：{}", questionId);
    }

    /**
     * 将选项项DTO列表转换为选项实体列表并绑定题目ID
     *
     * @param items      选项项DTO列表
     * @param questionId 题目ID
     * @return 选项实体列表
     */
    private List<ScaleOption> buildOptions(List<AdminScaleOptionItemDTO> items, Long questionId) {
        return StreamUtils.toList(items, item -> {
            ScaleOption option = BeanUtil.copyProperties(item, ScaleOption.class);
            option.setQuestionId(questionId);
            return option;
        });
    }
}