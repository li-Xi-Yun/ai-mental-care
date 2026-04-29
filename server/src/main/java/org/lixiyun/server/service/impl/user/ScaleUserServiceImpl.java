package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.constant.DeleteConstant;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.user.scale.AnswerItemDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleUserAnswerDTO;
import org.lixiyun.pojo.entity.scale.*;
import org.lixiyun.pojo.vo.user.scale.ScaleUserAnswerVO;
import org.lixiyun.pojo.vo.user.scale.ScaleUserRecordVO;
import org.lixiyun.server.mapper.*;
import org.lixiyun.server.service.user.ScaleUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author lixiyun
 * @since 2026-04-17 13:49
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleUserServiceImpl implements ScaleUserService {

    private final ScaleMapper scaleMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleResultRuleMapper scaleResultRuleMapper;
    private final ScaleUserRecordMapper scaleUserRecordMapper;
    private final ScaleUserAnswerMapper scaleUserAnswerMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAnswer(ScaleUserAnswerDTO answerDTO) {
        Long scaleId = answerDTO.getScaleId();
        List<AnswerItemDTO> answers = answerDTO.getAnswers();

        log.debug("开始处理用户测评，量表ID: {}, 答题数量: {}", scaleId, answers.size());

        Scale scale = scaleMapper.selectById(scaleId);
        if (scale == null || scale.deleteFlat()) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }

        if (!scale.isEnabled()) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_DISABLED);
        }

        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        ScaleUserRecord record = ScaleUserRecord.builder()
                .userId(userId)
                .scaleId(scaleId)
                .build();
        scaleUserRecordMapper.insertOneWithScaleName(record);
        Long recordId = record.getId();



        // 获取题目列表
        List<ScaleQuestion> questions = scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleId, scaleId)
                .eq(ScaleQuestion::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        Map<Long, ScaleQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(ScaleQuestion::getId, question -> question));
        List<Long> questionIds = questions.stream()
                .map(ScaleQuestion::getId)
                .collect(Collectors.toList());

        // 获取选项列表
        List<ScaleOption> options = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                .in(ScaleOption::getQuestionId, questionIds)
                .eq(ScaleOption::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        Map<Long, ScaleOption> optionMap = options.stream()
                .collect(Collectors.toMap(ScaleOption::getId, option -> option));

        // 处理用户答案
        List<ScaleUserAnswer> answerEntities = answers.stream()
                .map(answerItem -> {
                    Long questionId = answerItem.getQuestionId();
                    Long optionId = answerItem.getOptionId();

                    ScaleQuestion question = questionMap.get(questionId);
                    if (question == null) {
                        throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_NOT_FOUND);
                    }

                    ScaleOption option = optionMap.get(optionId);
                    if (option == null) {
                        throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_NOT_FOUND);
                    }

                    if (!question.getScaleId().equals(scaleId)) {
                        throw new BusinessException(ScaleExceptionEnum.SCALE_QUESTION_OPTION_MISMATCH);
                    }

                    Integer originalScore = option.getScore();
                    Integer scoreType = question.getScoreType();
                    Integer finalScore = (scoreType != null && scoreType.equals(ScaleQuestion.SCORE_TYPE_REVERSE)) ? -originalScore : originalScore;

                    return ScaleUserAnswer.builder()
                            .recordId(recordId)
                            .questionId(questionId)
                            .optionId(optionId)
                            .sort(question.getSort())
                            .questionTitle(question.getTitle())
                            .optionText(option.getOptionText())
                            .originalScore(finalScore)
                            .build();
                })
                .collect(Collectors.toList());
        scaleUserAnswerMapper.insert(answerEntities);

        // 计算总分
        int totalScore = answerEntities.stream()
                .mapToInt(ScaleUserAnswer::getOriginalScore)
                .sum();

        // 获取测评结果文本
        ScaleResultRule rule = scaleResultRuleMapper.selectOne(new LambdaQueryWrapper<ScaleResultRule>()
                .eq(ScaleResultRule::getScaleId, scaleId)
                .le(ScaleResultRule::getMinScore, totalScore)
                .ge(ScaleResultRule::getMaxScore, totalScore)
                .eq(ScaleResultRule::getDeleted, DeleteConstant.DELETE_FLAG_NO));

        record.setTotalScore(totalScore);
        record.setResultText(rule.getResultText());
        scaleUserRecordMapper.updateById(record);

        log.info("用户测评提交成功，recordId: {}, userId: {}, scaleId: {}, totalScore: {}, resultText: {}",
                recordId, userId, scaleId, totalScore, rule.getResultText());
    }

    @Override
    public PageResult<ScaleUserRecordVO> listRecords(Integer pageNum, Integer pageSize) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        Page<ScaleUserRecord> pageParam = new PageQuery(pageSize, pageNum).build();

        Page<ScaleUserRecord> recordPage = scaleUserRecordMapper.selectPage(pageParam, new LambdaQueryWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getUserId, userId)
                .eq(ScaleUserRecord::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByDesc(ScaleUserRecord::getCreatedTime));

        return PageResult.convert(recordPage, ScaleUserRecordVO.class);
    }

    @Override
    public PageResult<ScaleUserAnswerVO> listAnswerDetails(Long recordId, Integer pageNum, Integer pageSize) {
        Page<ScaleUserAnswer> pageParam = new PageQuery(pageSize, pageNum).build();

        Page<ScaleUserAnswer> answerPage = scaleUserAnswerMapper.selectPage(pageParam, new LambdaQueryWrapper<ScaleUserAnswer>()
                .eq(ScaleUserAnswer::getRecordId, recordId)
                .eq(ScaleUserAnswer::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByAsc(ScaleUserAnswer::getSort));

        return PageResult.convert(answerPage, ScaleUserAnswerVO.class);
    }
}
