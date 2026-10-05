package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
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
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.entity.scale.ScaleUserRecord;
import org.lixiyun.pojo.entity.scale.ScaleVersion;
import org.lixiyun.pojo.vo.user.scale.ScaleDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScalePrecheckVO;
import org.lixiyun.pojo.vo.user.scale.ScaleQuestionTypeStatVO;
import org.lixiyun.pojo.vo.user.scale.ScaleVO;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.mapper.ScaleUserRecordMapper;
import org.lixiyun.server.mapper.ScaleVersionMapper;
import org.lixiyun.server.service.user.ScaleService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 量表前台服务实现
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleServiceImpl implements ScaleService {

    private final ScaleMapper scaleMapper;
    private final ScaleVersionMapper scaleVersionMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;
    private final ScaleUserRecordMapper scaleUserRecordMapper;

    @Override
    public PageResult<ScaleVO> listScales(Integer pageNum, Integer pageSize, Long scaleCategoryId, String keyword) {
        log.info("[量表-分页列表]，参数：pageNum={}, pageSize={}, scaleCategoryId={}, keyword={}",
                pageNum, pageSize, scaleCategoryId, keyword);
        LambdaQueryWrapper<Scale> wrapper = new LambdaQueryWrapper<Scale>()
                .eq(Scale::getStatus, Scale.STATUS_ENABLE)
                .isNotNull(Scale::getCurrentVersionId)
                .eq(scaleCategoryId != null, Scale::getScaleCategoryId, scaleCategoryId)
                .like(StrUtil.isNotBlank(keyword), Scale::getScaleName, keyword)
                .orderByDesc(Scale::getCreatedTime);
        Page<Scale> page = scaleMapper.selectPage(new PageQuery(pageSize, pageNum).build(), wrapper);
        List<Scale> records = page.getRecords();
        if (CollUtil.isEmpty(records)) {
            log.debug("[量表-分页列表]，查询无数据");
            return new PageResult<>(page.getTotal(), new ArrayList<>());
        }

        List<Long> versionIds = records.stream()
                .map(Scale::getCurrentVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 当前版本映射：versionId -> scaleVersion
        Map<Long, ScaleVersion> versionMap = new HashMap<>();
        if (CollUtil.isNotEmpty(versionIds)) {
            scaleVersionMapper.selectBatchIds(versionIds).forEach(version -> versionMap.put(version.getId(), version));
        }

        // 各版本题目数统计：scaleVersionId -> 题目数
        Map<Long, Long> questionCountByVersion = new HashMap<>();
        if (CollUtil.isNotEmpty(versionIds)) {
            List<ScaleQuestion> allQuestions = scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                    .in(ScaleQuestion::getScaleVersionId, versionIds));
            if (CollUtil.isNotEmpty(allQuestions)) {
                questionCountByVersion = allQuestions.stream()
                        .collect(Collectors.groupingBy(ScaleQuestion::getScaleVersionId, Collectors.counting()));
            }
        }

        List<ScaleVO> voList = new ArrayList<>();
        for (Scale scale : records) {
            ScaleVO vo = BeanUtil.copyProperties(scale, ScaleVO.class);
            ScaleVersion version = versionMap.get(scale.getCurrentVersionId());
            if (version != null) {
                vo.setVersionNo(version.getVersionNo());
                vo.setDescription(version.getDescription());
            }
            long questionCount = questionCountByVersion.getOrDefault(scale.getCurrentVersionId(), 0L);
            vo.setQuestionCount((int) questionCount);
            vo.setEstimatedMinutes(Math.max(1, (int) ((questionCount + 1) / 2)));
            voList.add(vo);
        }
        log.debug("[量表-分页列表]，返回记录数：{}", voList.size());
        return new PageResult<>(page.getTotal(), voList);
    }

    @Override
    public ScaleDetailVO getScaleDetail(Long scaleId) {
        log.info("[量表-详情]，参数：scaleId={}", scaleId);
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
        Long versionId = scale.getCurrentVersionId();
        ScaleVersion version = scaleVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_VERSION_NOT_FOUND);
        }

        // 维度构成
        List<ScaleDimension> dimensions = scaleDimensionMapper.selectList(new LambdaQueryWrapper<ScaleDimension>()
                .eq(ScaleDimension::getScaleVersionId, versionId)
                .orderByAsc(ScaleDimension::getSort)
                .orderByAsc(ScaleDimension::getId));
        List<String> dimNames = new ArrayList<>();
        if (CollUtil.isNotEmpty(dimensions)) {
            dimNames = dimensions.stream().map(ScaleDimension::getDimName).toList();
        }

        // 题型统计
        List<ScaleQuestion> questions = scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, versionId));
        List<ScaleQuestionTypeStatVO> questionTypeStats = new ArrayList<>();
        if (CollUtil.isNotEmpty(questions)) {
            Map<Integer, Long> countByType = questions.stream()
                    .collect(Collectors.groupingBy(ScaleQuestion::getQuestionType, Collectors.counting()));
            List<Integer> types = Arrays.asList(ScaleQuestion.QUESTION_TYPE_SINGLE,
                    ScaleQuestion.QUESTION_TYPE_MULTIPLE,
                    ScaleQuestion.QUESTION_TYPE_FILL);
            for (Integer type : types) {
                long count = countByType.getOrDefault(type, 0L);
                questionTypeStats.add(ScaleQuestionTypeStatVO.builder()
                        .questionType(type)
                        .questionTypeLabel(questionTypeLabel(type))
                        .count(count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count)
                        .build());
            }
        }

        ScaleDetailVO vo = new ScaleDetailVO();
        BeanUtil.copyProperties(scale, vo);
        vo.setVersionNo(version.getVersionNo());
        vo.setDescription(version.getDescription());
        vo.setDimensions(dimNames);
        vo.setQuestionTypes(questionTypeStats);
        return vo;
    }

    @Override
    public ScalePrecheckVO precheck(Long scaleId) {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[量表-作答前检查]，参数：scaleId={}，userId={}", scaleId, userId);
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

        // 最近一条已完成记录
        ScaleUserRecord lastFinished = scaleUserRecordMapper.selectOne(new LambdaQueryWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getUserId, userId)
                .eq(ScaleUserRecord::getScaleId, scaleId)
                .eq(ScaleUserRecord::getFinishStatus, ScaleUserRecord.FINISH_STATUS_FINISHED)
                .orderByDesc(ScaleUserRecord::getEndTime)
                .last("LIMIT 1"));

        // 重复作答限制：不允许重复且已完成过
        Integer allowRepeat = scale.getAllowRepeat();
        if (allowRepeat != null && allowRepeat == 0 && lastFinished != null) {
            log.info("[量表-作答前检查]，不允许重复作答，scaleId={}，userId={}", scaleId, userId);
            return ScalePrecheckVO.builder()
                    .allowed(false)
                    .reason("REPEAT_LIMITED")
                    .coolRemainMinutes(0)
                    .build();
        }

        // 冷却期校验
        if (lastFinished != null && lastFinished.getEndTime() != null
                && scale.getCoolMinutes() != null && scale.getCoolMinutes() > 0) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime coolEnd = lastFinished.getEndTime().plusMinutes(scale.getCoolMinutes());
            if (coolEnd.isAfter(now)) {
                long millis = Duration.between(now, coolEnd).toMillis();
                int remainMinutes = (int) Math.max(1, (millis + 59999) / 60000);
                log.info("[量表-作答前检查]，处于冷却期，剩余分钟：{}，scaleId={}，userId={}", remainMinutes, scaleId, userId);
                return ScalePrecheckVO.builder()
                        .allowed(false)
                        .reason("COOLING")
                        .coolRemainMinutes(remainMinutes)
                        .build();
            }
        }

        return ScalePrecheckVO.builder()
                .allowed(true)
                .reason(null)
                .coolRemainMinutes(0)
                .build();
    }

    /**
     * 题型数字转中文名称
     *
     * @param questionType 题型：1-单选 2-多选 3-填空
     * @return 中文题型名称，未知类型返回空串
     */
    private String questionTypeLabel(Integer questionType) {
        if (questionType == null) {
            return "";
        }
        return switch (questionType.intValue()) {
            case ScaleQuestion.QUESTION_TYPE_SINGLE -> "单选";
            case ScaleQuestion.QUESTION_TYPE_MULTIPLE -> "多选";
            case ScaleQuestion.QUESTION_TYPE_FILL -> "填空";
            default -> "";
        };
    }
}