package org.lixiyun.server.service.impl.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.user.scale.ScaleAnswerItemDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleResumeDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleStartDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleSubmitDTO;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleBranchRule;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleNorm;
import org.lixiyun.pojo.entity.scale.ScaleNormGroup;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.entity.scale.ScaleResultRule;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswer;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswerOption;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswerText;
import org.lixiyun.pojo.entity.scale.ScaleUserDimScore;
import org.lixiyun.pojo.entity.scale.ScaleUserRecord;
import org.lixiyun.pojo.entity.scale.ScaleVersion;
import org.lixiyun.pojo.vo.user.scale.ScaleBranchRulePayloadVO;
import org.lixiyun.pojo.vo.user.scale.ScaleDimensionResultVO;
import org.lixiyun.pojo.vo.user.scale.ScaleOptionPayloadVO;
import org.lixiyun.pojo.vo.user.scale.ScaleQuestionPayloadVO;
import org.lixiyun.pojo.vo.user.scale.ScaleStartVO;
import org.lixiyun.pojo.vo.user.scale.ScaleSubmitResultVO;
import org.lixiyun.pojo.vo.user.scale.ScaleUnfinishedVO;
import org.lixiyun.server.mapper.ScaleBranchRuleMapper;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleNormGroupMapper;
import org.lixiyun.server.mapper.ScaleNormMapper;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.mapper.ScaleResultRuleMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerOptionMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerTextMapper;
import org.lixiyun.server.mapper.ScaleUserDimScoreMapper;
import org.lixiyun.server.mapper.ScaleUserRecordMapper;
import org.lixiyun.server.mapper.ScaleVersionMapper;
import org.lixiyun.server.mapper.UserMapper;
import org.lixiyun.server.service.user.ScaleAssessmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 量表测评前台服务实现
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleAssessmentServiceImpl implements ScaleAssessmentService {

    private final ScaleMapper scaleMapper;
    private final ScaleVersionMapper scaleVersionMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleBranchRuleMapper scaleBranchRuleMapper;
    private final ScaleResultRuleMapper scaleResultRuleMapper;
    private final ScaleNormGroupMapper scaleNormGroupMapper;
    private final ScaleNormMapper scaleNormMapper;
    private final ScaleUserRecordMapper scaleUserRecordMapper;
    private final ScaleUserAnswerMapper scaleUserAnswerMapper;
    private final ScaleUserAnswerOptionMapper scaleUserAnswerOptionMapper;
    private final ScaleUserAnswerTextMapper scaleUserAnswerTextMapper;
    private final ScaleUserDimScoreMapper scaleUserDimScoreMapper;
    private final UserMapper userMapper;

    /** 结果规则命中结果载体 */
    private record ResultRuleMatch(String text, Integer riskLevel) {

        static ResultRuleMatch none() {
            return new ResultRuleMatch(null, ScaleUserRecord.RISK_LEVEL_NONE);
        }
    }

    /** 常模换算结果载体 */
    private record NormResult(Long normGroupId, BigDecimal standardScore, BigDecimal percentile) {

        static NormResult none() {
            return new NormResult(null, null, null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScaleStartVO start(ScaleStartDTO dto) {
        Long scaleId = dto.getScaleId();
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[测评-开始]，参数：scaleId={}，userId={}", scaleId, userId);

        Scale scale = scaleMapper.selectById(scaleId);
        if (scale == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        if (scale.getStatus() == null || scale.getStatus().intValue() != Scale.STATUS_ENABLE) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_DISABLED);
        }
        if (scale.getCurrentVersionId() == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_NO_CURRENT_VERSION);
        }
        ScaleVersion version = scaleVersionMapper.selectById(scale.getCurrentVersionId());
        if (version == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
        }

        // 冷却与重复作答限制
        checkCoolingAndRepeat(scale, userId);

        // 将本人该量表所有未完成记录置为中途终止
        LocalDateTime now = LocalDateTime.now();
        scaleUserRecordMapper.update(null, new LambdaUpdateWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getUserId, userId)
                .eq(ScaleUserRecord::getScaleId, scaleId)
                .eq(ScaleUserRecord::getFinishStatus, ScaleUserRecord.FINISH_STATUS_UNFINISHED)
                .set(ScaleUserRecord::getFinishStatus, ScaleUserRecord.FINISH_STATUS_TERMINATED)
                .set(ScaleUserRecord::getEndTime, now));

        // 新建测评记录，锁定当前版本
        ScaleUserRecord record = ScaleUserRecord.builder()
                .userId(userId)
                .scaleId(scaleId)
                .scaleVersionId(version.getId())
                .scaleName(scale.getScaleName())
                .finishStatus(ScaleUserRecord.FINISH_STATUS_UNFINISHED)
                .startTime(now)
                .build();
        scaleUserRecordMapper.insert(record);
        log.info("[测评-开始]，测评记录创建成功，recordId={}", record.getId());

        // 按记录锁定版本装配整卷下发
        ScaleStartVO vo = buildStartVO(record, scale);
        log.debug("[测评-开始]，下发题目数：{}，跳题规则数：{}", vo.getTotalQuestionCount(),
                vo.getBranchRules() == null ? 0 : vo.getBranchRules().size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScaleUnfinishedVO getUnfinished(Long scaleId) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[测评-查询未完成]，参数：scaleId={}，userId={}", scaleId, userId);

        ScaleUserRecord record = scaleUserRecordMapper.selectOne(new LambdaQueryWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getUserId, userId)
                .eq(ScaleUserRecord::getScaleId, scaleId)
                .eq(ScaleUserRecord::getFinishStatus, ScaleUserRecord.FINISH_STATUS_UNFINISHED)
                .orderByDesc(ScaleUserRecord::getId)
                .last("LIMIT 1"));
        if (record == null) {
            return ScaleUnfinishedVO.builder().hasUnfinished(false).build();
        }
        return ScaleUnfinishedVO.builder()
                .hasUnfinished(true)
                .recordId(record.getId())
                .questionCount(scaleQuestionMapper.selectCount(new LambdaQueryWrapper<ScaleQuestion>()
                        .eq(ScaleQuestion::getScaleVersionId, record.getScaleVersionId())).intValue())
                .startTime(record.getStartTime())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScaleStartVO resume(ScaleResumeDTO dto) {
        Long recordId = dto.getRecordId();
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[测评-续答]，参数：recordId={}，userId={}", recordId, userId);

        ScaleUserRecord record = getOwnedRecord(recordId, userId);
        Integer finishStatus = record.getFinishStatus();
        if (finishStatus == null || finishStatus.intValue() != ScaleUserRecord.FINISH_STATUS_UNFINISHED) {
            if (finishStatus != null && finishStatus.intValue() == ScaleUserRecord.FINISH_STATUS_FINISHED) {
                throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_FINISHED);
            }
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_TERMINATED);
        }

        Scale scale = scaleMapper.selectById(record.getScaleId());
        if (scale == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }
        if (scale.getStatus() == null || scale.getStatus().intValue() != Scale.STATUS_ENABLE) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_DISABLED);
        }

        ScaleStartVO vo = buildStartVO(record, scale);
        log.info("[测评-续答]，下发整卷，recordId={}，题目数：{}", recordId, vo.getTotalQuestionCount());
        return vo;
    }

    /**
     * 按测评记录锁定的版本装配整卷下发 VO（不新建记录、不修改任何数据）
     * <p>开始测评与续答共用：start 在建记录后调用，resume 直接对已有记录调用。</p>
     *
     * @param record 测评记录（需已设置 scaleVersionId / scaleName）
     * @param scale  量表主表实体
     * @return 整卷下发 VO
     */
    private ScaleStartVO buildStartVO(ScaleUserRecord record, Scale scale) {
        Long versionId = record.getScaleVersionId();
        ScaleVersion version = versionId == null ? null : scaleVersionMapper.selectById(versionId);

        // 加载整卷题目与选项
        List<ScaleQuestion> questions = scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, versionId)
                .orderByAsc(ScaleQuestion::getSort)
                .orderByAsc(ScaleQuestion::getId));
        List<Long> questionIds = questions.stream().map(ScaleQuestion::getId).toList();
        Map<Long, List<ScaleOption>> optionsByQuestion = new HashMap<>();
        if (CollUtil.isNotEmpty(questionIds)) {
            List<ScaleOption> options = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                    .in(ScaleOption::getQuestionId, questionIds)
                    .orderByAsc(ScaleOption::getSort)
                    .orderByAsc(ScaleOption::getId));
            if (CollUtil.isNotEmpty(options)) {
                optionsByQuestion = options.stream().collect(Collectors.groupingBy(ScaleOption::getQuestionId));
            }
        }

        // 精简跳题规则
        List<ScaleBranchRule> branchRules = scaleBranchRuleMapper.selectList(new LambdaQueryWrapper<ScaleBranchRule>()
                .eq(ScaleBranchRule::getScaleVersionId, versionId)
                .orderByAsc(ScaleBranchRule::getId));

        List<ScaleQuestionPayloadVO> questionPayloads = new ArrayList<>();
        for (ScaleQuestion question : questions) {
            List<ScaleOption> questionOptions = optionsByQuestion.getOrDefault(question.getId(), new ArrayList<>());
            List<ScaleOptionPayloadVO> optionPayloads = questionOptions.stream()
                    .map(option -> ScaleOptionPayloadVO.builder()
                            .optionId(option.getId())
                            .optionText(option.getOptionText())
                            .sort(option.getSort())
                            .build())
                    .toList();
            questionPayloads.add(ScaleQuestionPayloadVO.builder()
                    .questionId(question.getId())
                    .title(question.getTitle())
                    .questionType(question.getQuestionType())
                    .required(question.getRequired())
                    .sort(question.getSort())
                    .dimensionId(question.getDimensionId())
                    .options(optionPayloads)
                    .build());
        }
        List<ScaleBranchRulePayloadVO> branchRulePayloads = branchRules.stream()
                .map(rule -> ScaleBranchRulePayloadVO.builder()
                        .sourceQuestionId(rule.getSourceQuestionId())
                        .sourceOptionId(rule.getSourceOptionId())
                        .targetQuestionId(rule.getTargetQuestionId())
                        .build())
                .toList();

        return ScaleStartVO.builder()
                .recordId(record.getId())
                .scaleId(scale.getId())
                .scaleVersionId(versionId)
                .scaleName(record.getScaleName())
                .versionNo(version == null ? null : version.getVersionNo())
                .description(version == null ? null : version.getDescription())
                .copyrightInfo(version == null ? null : version.getCopyrightInfo())
                .timeLimit(scale.getTimeLimit())
                .anonymous(scale.getAnonymous())
                .totalQuestionCount(questions.size())
                .questions(questionPayloads)
                .branchRules(branchRulePayloads)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScaleSubmitResultVO submit(ScaleSubmitDTO dto) {
        Long recordId = dto.getRecordId();
        List<ScaleAnswerItemDTO> answers = dto.getAnswers();
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[测评-提交]，参数：recordId={}，userId={}，答案数量={}", recordId, userId,
                answers == null ? 0 : answers.size());
        if (CollUtil.isEmpty(answers)) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_ANSWER_EMPTY);
        }

        ScaleUserRecord record = getOwnedRecord(recordId, userId);
        Integer finishStatus = record.getFinishStatus();
        if (finishStatus != null && finishStatus.intValue() == ScaleUserRecord.FINISH_STATUS_FINISHED) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_FINISHED);
        }
        if (finishStatus != null && finishStatus.intValue() == ScaleUserRecord.FINISH_STATUS_TERMINATED) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_TERMINATED);
        }

        LocalDateTime now = LocalDateTime.now();

        // 超时校验
        Scale scale = scaleMapper.selectById(record.getScaleId());
        if (scale != null && scale.getTimeLimit() != null && scale.getTimeLimit() > 0
                && record.getStartTime() != null
                && Duration.between(record.getStartTime(), now).toSeconds() > scale.getTimeLimit()) {
            scaleUserRecordMapper.update(null, new LambdaUpdateWrapper<ScaleUserRecord>()
                    .eq(ScaleUserRecord::getId, recordId)
                    .set(ScaleUserRecord::getFinishStatus, ScaleUserRecord.FINISH_STATUS_TERMINATED)
                    .set(ScaleUserRecord::getEndTime, now));
            log.info("[测评-提交]，作答超时，记录已终止，recordId={}", recordId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_TIME_LIMIT_EXCEEDED);
        }

        // 同一题目多次提交时按前一条为准，避免重复落库
        Map<Long, ScaleAnswerItemDTO> answerMap = new LinkedHashMap<>();
        for (ScaleAnswerItemDTO answer : answers) {
            answerMap.putIfAbsent(answer.getQuestionId(), answer);
        }
        List<ScaleAnswerItemDTO> uniqueAnswers = new ArrayList<>(answerMap.values());

        Long versionId = record.getScaleVersionId();
        List<ScaleQuestion> questions = scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, versionId)
                .orderByAsc(ScaleQuestion::getSort)
                .orderByAsc(ScaleQuestion::getId));
        List<Long> questionIds = questions.stream().map(ScaleQuestion::getId).toList();
        Map<Long, List<ScaleOption>> optionsByQuestion = new HashMap<>();
        if (CollUtil.isNotEmpty(questionIds)) {
            List<ScaleOption> options = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                    .in(ScaleOption::getQuestionId, questionIds)
                    .orderByAsc(ScaleOption::getSort)
                    .orderByAsc(ScaleOption::getId));
            if (CollUtil.isNotEmpty(options)) {
                optionsByQuestion = options.stream().collect(Collectors.groupingBy(ScaleOption::getQuestionId));
            }
        }
        List<ScaleBranchRule> branchRules = scaleBranchRuleMapper.selectList(new LambdaQueryWrapper<ScaleBranchRule>()
                .eq(ScaleBranchRule::getScaleVersionId, versionId)
                .orderByAsc(ScaleBranchRule::getId));
        List<ScaleResultRule> resultRules = scaleResultRuleMapper.selectList(new LambdaQueryWrapper<ScaleResultRule>()
                .eq(ScaleResultRule::getScaleVersionId, versionId)
                .orderByAsc(ScaleResultRule::getSort)
                .orderByAsc(ScaleResultRule::getId));
        List<ScaleNormGroup> normGroups = scaleNormGroupMapper.selectList(new LambdaQueryWrapper<ScaleNormGroup>()
                .eq(ScaleNormGroup::getScaleVersionId, versionId)
                .orderByAsc(ScaleNormGroup::getSort)
                .orderByAsc(ScaleNormGroup::getId));

        // 唯一化答案后执行合法性校验
        validateRequiredQuestions(questions, uniqueAnswers);
        validateAnswerPath(questions, optionsByQuestion, branchRules, uniqueAnswers);
        validateAnswerOptions(questions, optionsByQuestion, uniqueAnswers);

        Map<Long, ScaleQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(ScaleQuestion::getId, Function.identity()));
        Map<Long, ScaleOption> optionById = optionsByQuestion.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(ScaleOption::getId, Function.identity()));

        // 逐题计分
        Map<Long, BigDecimal> originalScoreByQuestion = calculateScore(questions, optionsByQuestion, uniqueAnswers);

        // 逐题落库（回填自增ID）
        List<ScaleUserAnswerOption> optionSnapshots = new ArrayList<>();
        List<ScaleUserAnswerText> textSnapshots = new ArrayList<>();
        for (ScaleAnswerItemDTO answer : uniqueAnswers) {
            ScaleQuestion question = questionById.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }
            ScaleUserAnswer userAnswer = ScaleUserAnswer.builder()
                    .recordId(recordId)
                    .questionId(question.getId())
                    .questionTitle(question.getTitle())
                    .questionType(question.getQuestionType())
                    .answerStatus(answer.getAnswerStatus())
                    .spendSeconds(answer.getSpendSeconds())
                    .originalScore(originalScoreByQuestion.getOrDefault(question.getId(), BigDecimal.ZERO))
                    .build();
            scaleUserAnswerMapper.insert(userAnswer);
            Long answerId = userAnswer.getId();

            if (CollUtil.isNotEmpty(answer.getOptionIds())) {
                for (Long optionId : answer.getOptionIds()) {
                    ScaleOption option = optionById.get(optionId);
                    optionSnapshots.add(ScaleUserAnswerOption.builder()
                            .answerId(answerId)
                            .optionId(optionId)
                            .optionText(option == null ? null : option.getOptionText())
                            .build());
                }
            }
            if (StrUtil.isNotBlank(answer.getAnswerText())) {
                textSnapshots.add(ScaleUserAnswerText.builder()
                        .answerId(answerId)
                        .answerText(answer.getAnswerText())
                        .build());
            }
        }
        if (CollUtil.isNotEmpty(optionSnapshots)) {
            Db.saveBatch(optionSnapshots);
        }
        if (CollUtil.isNotEmpty(textSnapshots)) {
            Db.saveBatch(textSnapshots);
        }
        log.debug("[测评-提交]，逐题答案落库完成，答题记录数：{}", originalScoreByQuestion.size());

        // 维度得分汇总与结果规则匹配
        BigDecimal totalScore = BigDecimal.ZERO;
        Map<Long, BigDecimal> scoreByDimension = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : originalScoreByQuestion.entrySet()) {
            BigDecimal score = entry.getValue();
            totalScore = totalScore.add(score);
            ScaleQuestion question = questionById.get(entry.getKey());
            if (question != null && question.getDimensionId() != null) {
                scoreByDimension.merge(question.getDimensionId(), score, BigDecimal::add);
            }
        }

        List<ScaleUserDimScore> dimScoreList = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : scoreByDimension.entrySet()) {
            ResultRuleMatch dimMatch = matchResultRule(resultRules, entry.getKey(), entry.getValue(), false);
            dimScoreList.add(ScaleUserDimScore.builder()
                    .recordId(recordId)
                    .dimensionId(entry.getKey())
                    .dimScore(entry.getValue())
                    .dimResult(dimMatch.text())
                    .riskLevel(dimMatch.riskLevel())
                    .build());
        }
        if (CollUtil.isNotEmpty(dimScoreList)) {
            Db.saveBatch(dimScoreList);
        }

        ResultRuleMatch totalMatch = matchResultRule(resultRules, null, totalScore, true);
        String resultText = totalMatch.text();
        Integer riskLevel = totalMatch.riskLevel();

        // 总分常模换算
        NormResult normResult = convertNorm(normGroups, scaleNormMapper, userId, totalScore, null);

        // 更新测评记录
        record.setTotalScore(totalScore);
        record.setStandardScore(normResult.standardScore());
        record.setPercentile(normResult.percentile());
        record.setResultText(resultText);
        record.setRiskLevel(riskLevel);
        record.setNormGroupId(normResult.normGroupId());
        record.setFinishStatus(ScaleUserRecord.FINISH_STATUS_FINISHED);
        record.setEndTime(now);
        scaleUserRecordMapper.updateById(record);
        log.info("[测评-提交]，测评结果落库完成，recordId={}，totalScore={}", recordId, totalScore);

        // 组装维度结果
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

        return ScaleSubmitResultVO.builder()
                .recordId(recordId)
                .finishStatus(ScaleUserRecord.FINISH_STATUS_FINISHED)
                .totalScore(totalScore)
                .standardScore(normResult.standardScore())
                .percentile(normResult.percentile())
                .riskLevel(riskLevel)
                .resultText(resultText)
                .needFollowUp(riskLevel != null && riskLevel.intValue() == ScaleUserRecord.RISK_LEVEL_HIGH)
                .dimensionResults(dimensionResults)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long recordId) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[测评-终止]，参数：recordId={}，userId={}", recordId, userId);
        ScaleUserRecord record = getOwnedRecord(recordId, userId);
        Integer finishStatus = record.getFinishStatus();
        if (finishStatus != null && finishStatus.intValue() == ScaleUserRecord.FINISH_STATUS_FINISHED) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_FINISHED);
        }
        if (finishStatus != null && finishStatus.intValue() == ScaleUserRecord.FINISH_STATUS_TERMINATED) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_TERMINATED);
        }
        scaleUserRecordMapper.update(null, new LambdaUpdateWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getId, recordId)
                .set(ScaleUserRecord::getFinishStatus, ScaleUserRecord.FINISH_STATUS_TERMINATED)
                .set(ScaleUserRecord::getEndTime, LocalDateTime.now()));
        log.info("[测评-终止]，测评记录已终止，recordId={}", recordId);
    }

    /**
     * 校验量表是否处于冷却期或不允许重复作答，命中直接抛出业务异常
     *
     * @param scale  量表实体
     * @param userId 当前用户 ID
     */
    private void checkCoolingAndRepeat(Scale scale, Long userId) {
        ScaleUserRecord lastFinished = scaleUserRecordMapper.selectOne(new LambdaQueryWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getUserId, userId)
                .eq(ScaleUserRecord::getScaleId, scale.getId())
                .eq(ScaleUserRecord::getFinishStatus, ScaleUserRecord.FINISH_STATUS_FINISHED)
                .orderByDesc(ScaleUserRecord::getEndTime)
                .last("LIMIT 1"));
        if (lastFinished == null) {
            return;
        }
        // 不允许重复作答且已有完成记录（优先于冷却判断，与作答前检查接口保持一致）
        Integer allowRepeat = scale.getAllowRepeat();
        if (allowRepeat == null || allowRepeat == 0) {
            log.info("[测评-开始]，不允许重复作答，scaleId={}，userId={}", scale.getId(), userId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_REPEAT_LIMITED);
        }
        // 冷却期判定
        if (scale.getCoolMinutes() != null && scale.getCoolMinutes() > 0
                && lastFinished.getEndTime() != null
                && lastFinished.getEndTime().plusMinutes(scale.getCoolMinutes()).isAfter(LocalDateTime.now())) {
            log.info("[测评-开始]，处于冷却期，scaleId={}，userId={}", scale.getId(), userId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_COOLING);
        }
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

    /**
     * 校验作答路径合法性：跳题规则隐藏的题目不允许提交作答
     *
     * @param questions         版本题目列表
     * @param optionsByQuestion 题目 ID -> 选项列表 映射
     * @param branchRules       跳题规则列表
     * @param answers           提交答案列表
     */
    private void validateAnswerPath(List<ScaleQuestion> questions,
                                    Map<Long, List<ScaleOption>> optionsByQuestion,
                                    List<ScaleBranchRule> branchRules,
                                    List<ScaleAnswerItemDTO> answers) {
        if (CollUtil.isEmpty(branchRules)) {
            return;
        }
        Map<Long, Integer> sortByQuestion = questions.stream()
                .collect(Collectors.toMap(ScaleQuestion::getId, ScaleQuestion::getSort, (a, b) -> a));
        Map<Long, Set<Long>> selectedOptionIdsByQuestion = new HashMap<>();
        for (ScaleAnswerItemDTO answer : answers) {
            if (Integer.valueOf(ScaleQuestion.ANSWER_STATUS_DONE).equals(answer.getAnswerStatus())) {
                Set<Long> optionIdSet = CollUtil.isNotEmpty(answer.getOptionIds())
                        ? new HashSet<>(answer.getOptionIds())
                        : new HashSet<>();
                selectedOptionIdsByQuestion.put(answer.getQuestionId(), optionIdSet);
            }
        }

        Set<Long> hiddenQuestionIds = new HashSet<>();
        for (ScaleBranchRule rule : branchRules) {
            Set<Long> selectedOptions = selectedOptionIdsByQuestion.get(rule.getSourceQuestionId());
            if (selectedOptions == null || selectedOptions.isEmpty()) {
                continue;
            }
            if (rule.getSourceOptionId() != null && !selectedOptions.contains(rule.getSourceOptionId())) {
                continue;
            }
            Integer sourceSort = sortByQuestion.get(rule.getSourceQuestionId());
            if (sourceSort == null) {
                continue;
            }
            if (rule.getTargetQuestionId() != null) {
                Integer targetSort = sortByQuestion.get(rule.getTargetQuestionId());
                if (targetSort != null) {
                    for (ScaleQuestion question : questions) {
                        if (question.getSort() != null && question.getSort() > sourceSort
                                && question.getSort() < targetSort) {
                            hiddenQuestionIds.add(question.getId());
                        }
                    }
                }
            } else {
                for (ScaleQuestion question : questions) {
                    if (question.getSort() != null && question.getSort() > sourceSort) {
                        hiddenQuestionIds.add(question.getId());
                    }
                }
            }
        }
        if (hiddenQuestionIds.isEmpty()) {
            return;
        }
        for (ScaleAnswerItemDTO answer : answers) {
            if (Integer.valueOf(ScaleQuestion.ANSWER_STATUS_DONE).equals(answer.getAnswerStatus())
                    && hiddenQuestionIds.contains(answer.getQuestionId())) {
                log.warn("[测评-提交]，作答路径不合法：题目被跳题规则隐藏，questionId={}", answer.getQuestionId());
                throw new BusinessException(ScaleExceptionEnum.SCALE_ANSWER_PATH_INVALID);
            }
        }
    }

    /**
     * 校验必答题是否全部作答：required=1 的题目必须存在 answerStatus=1 的提交项
     *
     * @param questions 版本题目列表
     * @param answers   提交答案列表
     */
    private void validateRequiredQuestions(List<ScaleQuestion> questions, List<ScaleAnswerItemDTO> answers) {
        if (CollUtil.isEmpty(questions) || CollUtil.isEmpty(answers)) {
            return;
        }
        Set<Long> answeredQuestionIds = answers.stream()
                .filter(answer -> Integer.valueOf(ScaleQuestion.ANSWER_STATUS_DONE).equals(answer.getAnswerStatus()))
                .map(ScaleAnswerItemDTO::getQuestionId)
                .collect(Collectors.toSet());
        for (ScaleQuestion question : questions) {
            if (Integer.valueOf(ScaleQuestion.REQUIRED_YES).equals(question.getRequired())
                    && !answeredQuestionIds.contains(question.getId())) {
                log.warn("[测评-提交]，存在必答题未作答，questionId={}", question.getId());
                throw new BusinessException(ScaleExceptionEnum.SCALE_REQUIRED_UNANSWERED);
            }
        }
    }

    /**
     * 校验答案选项合法性：选中选项必须属于对应题目；填空题不允许绑定选项且必须填写文本
     *
     * @param questions         版本题目列表
     * @param optionsByQuestion 题目 ID -> 选项列表 映射
     * @param answers           提交答案列表
     */
    private void validateAnswerOptions(List<ScaleQuestion> questions,
                                       Map<Long, List<ScaleOption>> optionsByQuestion,
                                       List<ScaleAnswerItemDTO> answers) {
        Map<Long, ScaleQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(ScaleQuestion::getId, Function.identity()));
        Map<Long, Set<Long>> validOptionIdsByQuestion = new HashMap<>();
        optionsByQuestion.forEach((questionId, options) -> validOptionIdsByQuestion.put(questionId,
                options.stream().map(ScaleOption::getId).collect(Collectors.toSet())));

        for (ScaleAnswerItemDTO answer : answers) {
            if (!Integer.valueOf(ScaleQuestion.ANSWER_STATUS_DONE).equals(answer.getAnswerStatus())) {
                continue;
            }
            ScaleQuestion question = questionById.get(answer.getQuestionId());
            if (question == null) {
                log.warn("[测评-提交]，提交的题目不属于当前版本，questionId={}", answer.getQuestionId());
                throw new BusinessException(ScaleExceptionEnum.SCALE_ANSWER_OPTION_INVALID);
            }
            List<Long> optionIds = answer.getOptionIds();
            boolean isFill = question.getQuestionType() != null
                    && question.getQuestionType().intValue() == ScaleQuestion.QUESTION_TYPE_FILL;
            if (!isFill) {
                Set<Long> validIds = validOptionIdsByQuestion.getOrDefault(answer.getQuestionId(), new HashSet<>());
                if (CollUtil.isEmpty(optionIds) || !validIds.containsAll(optionIds)) {
                    log.warn("[测评-提交]，选项不属于该题目，questionId={}", answer.getQuestionId());
                    throw new BusinessException(ScaleExceptionEnum.SCALE_ANSWER_OPTION_INVALID);
                }
                // 多选不允许提交重复选项，避免重复计分
                if (new HashSet<>(optionIds).size() != optionIds.size()) {
                    log.warn("[测评-提交]，选项存在重复，questionId={}", answer.getQuestionId());
                    throw new BusinessException(ScaleExceptionEnum.SCALE_ANSWER_OPTION_INVALID);
                }
            } else {
                if (CollUtil.isNotEmpty(optionIds) || StrUtil.isBlank(answer.getAnswerText())) {
                    log.warn("[测评-提交]，填空题选项或文本不合法，questionId={}", answer.getQuestionId());
                    throw new BusinessException(ScaleExceptionEnum.SCALE_ANSWER_OPTION_INVALID);
                }
            }
        }
    }

    /**
     * 逐题计算原始分：未作答与填空题为 0 分，反题按 「选中数*最大选项分-原始分」 计分
     *
     * @param questions         版本题目列表
     * @param optionsByQuestion 题目 ID -> 选项列表 映射
     * @param answers           提交答案列表
     * @return 题目 ID -> 该题原始分 映射
     */
    private Map<Long, BigDecimal> calculateScore(List<ScaleQuestion> questions,
                                                 Map<Long, List<ScaleOption>> optionsByQuestion,
                                                 List<ScaleAnswerItemDTO> answers) {
        Map<Long, Integer> answerStatusByQuestion = answers.stream()
                .collect(Collectors.toMap(ScaleAnswerItemDTO::getQuestionId,
                        item -> item.getAnswerStatus() == null ? ScaleQuestion.ANSWER_STATUS_UNDONE : item.getAnswerStatus(),
                        (a, b) -> a));
        Map<Long, List<Long>> optionIdsByQuestion = answers.stream()
                .collect(Collectors.toMap(ScaleAnswerItemDTO::getQuestionId,
                        item -> item.getOptionIds() == null ? new ArrayList<>() : item.getOptionIds(),
                        (a, b) -> a));
        Map<Long, ScaleOption> optionById = optionsByQuestion.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(ScaleOption::getId, Function.identity()));

        Map<Long, BigDecimal> originalScoreByQuestion = new HashMap<>();
        for (ScaleQuestion question : questions) {
            BigDecimal originalScore = BigDecimal.ZERO;
            Integer answerStatus = answerStatusByQuestion.get(question.getId());
            boolean isFill = question.getQuestionType() != null
                    && question.getQuestionType().intValue() == ScaleQuestion.QUESTION_TYPE_FILL;
            if (Integer.valueOf(ScaleQuestion.ANSWER_STATUS_DONE).equals(answerStatus) && !isFill) {
                List<Long> selectedIds = optionIdsByQuestion.getOrDefault(question.getId(), new ArrayList<>());
                BigDecimal raw = BigDecimal.ZERO;
                for (Long optionId : selectedIds) {
                    ScaleOption option = optionById.get(optionId);
                    if (option != null && option.getScore() != null) {
                        raw = raw.add(option.getScore());
                    }
                }
                boolean reverse = question.getScoreType() != null
                        && question.getScoreType().intValue() == ScaleQuestion.SCORE_TYPE_REVERSE;
                if (reverse) {
                    BigDecimal maxScore = BigDecimal.ZERO;
                    for (ScaleOption option : optionsByQuestion.getOrDefault(question.getId(), new ArrayList<>())) {
                        if (option.getScore() != null && option.getScore().compareTo(maxScore) > 0) {
                            maxScore = option.getScore();
                        }
                    }
                    originalScore = BigDecimal.valueOf(selectedIds.size()).multiply(maxScore).subtract(raw);
                } else {
                    originalScore = raw;
                }
            }
            originalScoreByQuestion.put(question.getId(), originalScore);
        }
        return originalScoreByQuestion;
    }

    /**
     * 匹配结果规则：返回该维度（null 表示总分）下第一个分数区间命中的规则结果
     *
     * @param resultRules 版本结果规则列表
     * @param dimensionId 维度 ID，null 表示总分规则
     * @param score       得分
     * @param isTotal     是否为总分规则匹配
     * @return 命中的结果文本与风险等级，未命中返回 {null, 0}
     */
    private ResultRuleMatch matchResultRule(List<ScaleResultRule> resultRules, Long dimensionId, BigDecimal score, boolean isTotal) {
        if (CollUtil.isEmpty(resultRules)) {
            return ResultRuleMatch.none();
        }
        List<ScaleResultRule> rules = resultRules.stream()
                .filter(rule -> isTotal
                        ? rule.getDimensionId() == null
                        : Objects.equals(rule.getDimensionId(), dimensionId))
                .sorted(Comparator.comparing(ScaleResultRule::getSort, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (ScaleResultRule rule : rules) {
            boolean inRange = (rule.getMinScore() == null || score.compareTo(rule.getMinScore()) >= 0)
                    && (rule.getMaxScore() == null || score.compareTo(rule.getMaxScore()) <= 0);
            if (inRange) {
                return new ResultRuleMatch(rule.getResultText(), rule.getRiskLevel());
            }
        }
        return ResultRuleMatch.none();
    }

    /**
     * 常模换算：匹配用户常模组，公式法或查表法换算标准分与百分等级
     *
     * @param normGroups  版本常模组列表
     * @param normMapper  常模明细 Mapper
     * @param userId      用户 ID
     * @param totalScore  原始总分
     * @param dimensionId 维度 ID，null 表示总分常模
     * @return 常模换算结果（未命中时字段为 null）
     */
    private NormResult convertNorm(List<ScaleNormGroup> normGroups, ScaleNormMapper normMapper,
                                   Long userId, BigDecimal totalScore, Long dimensionId) {
        if (totalScore == null || CollUtil.isEmpty(normGroups)) {
            return NormResult.none();
        }
        List<ScaleNormGroup> targetGroups = normGroups.stream()
                .filter(group -> Objects.equals(group.getDimensionId(), dimensionId))
                .sorted(Comparator.comparing(ScaleNormGroup::getSort, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (CollUtil.isEmpty(targetGroups)) {
            return NormResult.none();
        }

        Integer gender = null;
        User user = userMapper.selectById(userId);
        if (user != null) {
            gender = user.getGender();
        }
        // 优先「性别匹配或性别不限」，其次取排序最靠前
        Integer finalGender = gender;
        ScaleNormGroup matched = targetGroups.stream()
                .filter(group -> group.getGender() == null
                        || (finalGender != null && group.getGender().intValue() == finalGender.intValue()))
                .min(Comparator.comparing(ScaleNormGroup::getSort, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (matched == null) {
            return NormResult.none();
        }

        BigDecimal standardScore = null;
        BigDecimal percentile = null;
        Integer normType = matched.getNormType();
        if (normType != null && normType.intValue() == ScaleNormGroup.NORM_TYPE_FORMULA) {
            BigDecimal mean = matched.getMean();
            BigDecimal sd = matched.getSd();
            if (mean != null && sd != null && sd.compareTo(BigDecimal.ZERO) > 0) {
                double z = (totalScore.doubleValue() - mean.doubleValue()) / sd.doubleValue();
                double t = 50 + 10 * z;
                double pct = Math.max(0, Math.min(100, normalCdf(z) * 100));
                standardScore = BigDecimal.valueOf(t).setScale(2, RoundingMode.HALF_UP);
                percentile = BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP);
            }
        } else if (normType != null && normType.intValue() == ScaleNormGroup.NORM_TYPE_TABLE) {
            LambdaQueryWrapper<ScaleNorm> wrapper = new LambdaQueryWrapper<ScaleNorm>()
                    .eq(ScaleNorm::getNormGroupId, matched.getId())
                    .eq(ScaleNorm::getRawScore, totalScore);
            if (dimensionId == null) {
                wrapper.isNull(ScaleNorm::getDimensionId);
            } else {
                wrapper.eq(ScaleNorm::getDimensionId, dimensionId);
            }
            wrapper.orderByAsc(ScaleNorm::getId).last("LIMIT 1");
            ScaleNorm norm = normMapper.selectOne(wrapper);
            if (norm != null) {
                standardScore = norm.getTScore();
                percentile = norm.getPercentile();
            }
        }
        return new NormResult(matched.getId(), standardScore, percentile);
    }

    /**
     * 标准正态分布累积概率近似（Abramowitz &amp; Stegun 7.1.26）
     *
     * @param x 标准正态分位点
     * @return 累积概率 Φ(x)
     */
    private double normalCdf(double x) {
        boolean negative = x < 0;
        double abs = Math.abs(x);
        double b1 = 0.319381530;
        double b2 = -0.356563782;
        double b3 = 1.781477937;
        double b4 = -1.821255978;
        double b5 = 1.330274429;
        double p = 0.2316419;
        double t = 1 / (1 + p * abs);
        double cdf = 1 - 0.39894228040143268 * Math.exp(-abs * abs / 2) * t
                * (b1 + t * (b2 + t * (b3 + t * (b4 + t * b5))));
        return negative ? 1 - cdf : cdf;
    }
}