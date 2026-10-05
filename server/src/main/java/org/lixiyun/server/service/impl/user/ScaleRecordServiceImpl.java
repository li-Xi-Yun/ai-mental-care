package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswer;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswerOption;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswerText;
import org.lixiyun.pojo.entity.scale.ScaleUserDimScore;
import org.lixiyun.pojo.entity.scale.ScaleUserRecord;
import org.lixiyun.pojo.entity.scale.ScaleVersion;
import org.lixiyun.pojo.vo.user.scale.ScaleAnswerDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScaleDimensionResultVO;
import org.lixiyun.pojo.vo.user.scale.ScaleRecordDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScaleRecordVO;
import org.lixiyun.pojo.vo.user.scale.ScaleSelectedOptionVO;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerOptionMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerTextMapper;
import org.lixiyun.server.mapper.ScaleUserDimScoreMapper;
import org.lixiyun.server.mapper.ScaleUserRecordMapper;
import org.lixiyun.server.mapper.ScaleVersionMapper;
import org.lixiyun.server.service.user.ScaleRecordService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 测评记录前台服务实现
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleRecordServiceImpl implements ScaleRecordService {

    private final ScaleUserRecordMapper scaleUserRecordMapper;
    private final ScaleVersionMapper scaleVersionMapper;
    private final ScaleMapper scaleMapper;
    private final ScaleUserDimScoreMapper scaleUserDimScoreMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;
    private final ScaleUserAnswerMapper scaleUserAnswerMapper;
    private final ScaleUserAnswerOptionMapper scaleUserAnswerOptionMapper;
    private final ScaleUserAnswerTextMapper scaleUserAnswerTextMapper;

    @Override
    public PageResult<ScaleRecordVO> listRecords(Integer pageNum, Integer pageSize, Long scaleId, Integer finishStatus) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[测评记录-分页]，参数：pageNum={}, pageSize={}, scaleId={}, finishStatus={}",
                pageNum, pageSize, scaleId, finishStatus);
        LambdaQueryWrapper<ScaleUserRecord> wrapper = new LambdaQueryWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getUserId, userId)
                .eq(scaleId != null, ScaleUserRecord::getScaleId, scaleId)
                .eq(finishStatus != null, ScaleUserRecord::getFinishStatus, finishStatus)
                .orderByDesc(ScaleUserRecord::getStartTime)
                .orderByDesc(ScaleUserRecord::getId);
        Page<ScaleUserRecord> page = scaleUserRecordMapper.selectPage(new PageQuery(pageSize, pageNum).build(), wrapper);
        List<ScaleUserRecord> records = page.getRecords();
        if (CollUtil.isEmpty(records)) {
            log.debug("[测评记录-分页]，无查询数据");
            return new PageResult<>(page.getTotal(), new ArrayList<>());
        }
        List<ScaleRecordVO> voList = new ArrayList<>();
        for (ScaleUserRecord record : records) {
            ScaleRecordVO vo = BeanUtil.copyProperties(record, ScaleRecordVO.class);
            vo.setRecordId(record.getId());
            voList.add(vo);
        }
        log.debug("[测评记录-分页]，返回记录数：{}", voList.size());
        return new PageResult<>(page.getTotal(), voList);
    }

    @Override
    public ScaleRecordDetailVO getRecordDetail(Long recordId) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[测评记录-详情]，参数：recordId={}，userId={}", recordId, userId);
        ScaleUserRecord record = getOwnedRecord(recordId, userId);

        ScaleRecordDetailVO vo = BeanUtil.copyProperties(record, ScaleRecordDetailVO.class);
        vo.setRecordId(record.getId());

        ScaleVersion version = scaleVersionMapper.selectById(record.getScaleVersionId());
        if (version != null) {
            vo.setVersionNo(version.getVersionNo());
        }
        Scale scale = scaleMapper.selectById(record.getScaleId());
        if (scale != null) {
            vo.setAnonymous(scale.getAnonymous());
        }

        // 维度得分快照组装
        List<ScaleUserDimScore> dimScoreList = scaleUserDimScoreMapper.selectList(new LambdaQueryWrapper<ScaleUserDimScore>()
                .eq(ScaleUserDimScore::getRecordId, recordId));
        List<ScaleDimensionResultVO> dimensionResults = new ArrayList<>();
        if (CollUtil.isNotEmpty(dimScoreList)) {
            List<Long> dimensionIds = dimScoreList.stream().map(ScaleUserDimScore::getDimensionId).toList();
            Map<Long, ScaleDimension> dimensionMap = scaleDimensionMapper.selectBatchIds(dimensionIds).stream()
                    .collect(Collectors.toMap(ScaleDimension::getId, Function.identity()));
            for (ScaleUserDimScore dimScore : dimScoreList) {
                ScaleDimension dimension = dimensionMap.get(dimScore.getDimensionId());
                dimensionResults.add(ScaleDimensionResultVO.builder()
                        .dimensionId(dimScore.getDimensionId())
                        .dimName(dimension == null ? null : dimension.getDimName())
                        .dimDesc(dimension == null ? null : dimension.getDimDesc())
                        .dimScore(dimScore.getDimScore())
                        .dimResult(dimScore.getDimResult())
                        .riskLevel(dimScore.getRiskLevel())
                        .build());
            }
        }
        vo.setDimensionResults(dimensionResults);
        return vo;
    }

    @Override
    public List<ScaleAnswerDetailVO> listAnswerDetails(Long recordId) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[测评记录-答题明细]，参数：recordId={}，userId={}", recordId, userId);
        getOwnedRecord(recordId, userId);

        List<ScaleUserAnswer> answers = scaleUserAnswerMapper.selectList(new LambdaQueryWrapper<ScaleUserAnswer>()
                .eq(ScaleUserAnswer::getRecordId, recordId));
        if (CollUtil.isEmpty(answers)) {
            log.debug("[测评记录-答题明细]，无答题数据，recordId={}", recordId);
            return new ArrayList<>();
        }
        List<Long> answerIds = answers.stream().map(ScaleUserAnswer::getId).toList();

        Map<Long, List<ScaleUserAnswerOption>> optionsByAnswer = new HashMap<>();
        if (CollUtil.isNotEmpty(answerIds)) {
            List<ScaleUserAnswerOption> options = scaleUserAnswerOptionMapper.selectList(
                    new LambdaQueryWrapper<ScaleUserAnswerOption>()
                            .in(ScaleUserAnswerOption::getAnswerId, answerIds)
                            .orderByAsc(ScaleUserAnswerOption::getCreatedTime));
            if (CollUtil.isNotEmpty(options)) {
                optionsByAnswer = options.stream().collect(Collectors.groupingBy(ScaleUserAnswerOption::getAnswerId));
            }
        }
        Map<Long, ScaleUserAnswerText> textByAnswer = new HashMap<>();
        if (CollUtil.isNotEmpty(answerIds)) {
            List<ScaleUserAnswerText> texts = scaleUserAnswerTextMapper.selectList(new LambdaQueryWrapper<ScaleUserAnswerText>()
                    .in(ScaleUserAnswerText::getAnswerId, answerIds));
            if (CollUtil.isNotEmpty(texts)) {
                textByAnswer = texts.stream().collect(Collectors.toMap(ScaleUserAnswerText::getAnswerId, Function.identity(), (a, b) -> a));
            }
        }

        List<ScaleAnswerDetailVO> voList = new ArrayList<>();
        for (ScaleUserAnswer answer : answers) {
            ScaleUserAnswerText text = textByAnswer.get(answer.getId());
            List<ScaleUserAnswerOption> selectedOptions = optionsByAnswer.getOrDefault(answer.getId(), Collections.emptyList());
            List<ScaleSelectedOptionVO> selectedOptionVOs = selectedOptions.stream()
                    .map(option -> ScaleSelectedOptionVO.builder()
                            .optionId(option.getOptionId())
                            .optionText(option.getOptionText())
                            .build())
                    .toList();
            voList.add(ScaleAnswerDetailVO.builder()
                    .questionId(answer.getQuestionId())
                    .questionTitle(answer.getQuestionTitle())
                    .questionType(answer.getQuestionType())
                    .answerStatus(answer.getAnswerStatus())
                    .answerText(text == null ? null : text.getAnswerText())
                    .selectedOptions(selectedOptionVOs)
                    .createdTime(answer.getCreatedTime())
                    .build());
        }
        log.debug("[测评记录-答题明细]，返回记录数：{}，recordId={}", voList.size(), recordId);
        return voList;
    }

    /**
     * 校验测评记录存在且归属当前用户
     *
     * @param recordId 测评记录 ID
     * @param userId   当前用户 ID
     * @return 测评记录实体
     */
    private ScaleUserRecord getOwnedRecord(Long recordId, Long userId) {
        ScaleUserRecord record = scaleUserRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_FOUND);
        }
        if (record.getUserId() == null || !userId.equals(record.getUserId())) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_OWNED);
        }
        return record;
    }
}