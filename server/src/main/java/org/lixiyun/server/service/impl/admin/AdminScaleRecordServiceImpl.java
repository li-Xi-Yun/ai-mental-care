package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleRecordQueryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleRiskStatisticsQueryDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswer;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswerOption;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswerText;
import org.lixiyun.pojo.entity.scale.ScaleUserDimScore;
import org.lixiyun.pojo.entity.scale.ScaleUserRecord;
import org.lixiyun.pojo.entity.scale.ScaleVersion;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleAnswerDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleDimScoreVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRecordDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRecordVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRiskStatisticsVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleSelectedOptionVO;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerOptionMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerTextMapper;
import org.lixiyun.server.mapper.ScaleUserDimScoreMapper;
import org.lixiyun.server.mapper.ScaleUserRecordMapper;
import org.lixiyun.server.mapper.ScaleVersionMapper;
import org.lixiyun.server.service.admin.AdminScaleRecordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员量表测评记录监控服务实现类（敏感数据，须严格权限与审计）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleRecordServiceImpl implements AdminScaleRecordService {

    private final ScaleUserRecordMapper scaleUserRecordMapper;
    private final ScaleUserDimScoreMapper scaleUserDimScoreMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;
    private final ScaleUserAnswerMapper scaleUserAnswerMapper;
    private final ScaleUserAnswerOptionMapper scaleUserAnswerOptionMapper;
    private final ScaleUserAnswerTextMapper scaleUserAnswerTextMapper;
    private final ScaleMapper scaleMapper;
    private final ScaleVersionMapper scaleVersionMapper;
    private final ScaleAuditNameHelper scaleAuditNameHelper;

    @Override
    public PageResult<AdminScaleRecordVO> pageRecords(AdminScaleRecordQueryDTO queryDTO) {
        Long userId = queryDTO.getUserId();
        Long scaleId = queryDTO.getScaleId();
        String scaleName = queryDTO.getScaleName();
        Integer riskLevel = queryDTO.getRiskLevel();
        Integer finishStatus = queryDTO.getFinishStatus();
        LocalDateTime startTime = queryDTO.getStartTime();
        LocalDateTime endTime = queryDTO.getEndTime();
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();
        log.info("[AdminScaleRecordServiceImpl-分页查询测评记录]，userId：{}，scaleId：{}，scaleName：{}，riskLevel：{}，finishStatus：{}",
                userId, scaleId, scaleName, riskLevel, finishStatus);

        Page<ScaleUserRecord> page = new PageQuery(pageSize, pageNum).build();
        Page<ScaleUserRecord> result = scaleUserRecordMapper.selectPage(page, new LambdaQueryWrapper<ScaleUserRecord>()
                .eq(userId != null, ScaleUserRecord::getUserId, userId)
                .eq(scaleId != null, ScaleUserRecord::getScaleId, scaleId)
                .like(ObjectUtil.isNotEmpty(scaleName), ScaleUserRecord::getScaleName, scaleName)
                .eq(riskLevel != null, ScaleUserRecord::getRiskLevel, riskLevel)
                .eq(finishStatus != null, ScaleUserRecord::getFinishStatus, finishStatus)
                .ge(startTime != null, ScaleUserRecord::getStartTime, startTime)
                .le(endTime != null, ScaleUserRecord::getStartTime, endTime)
                .orderByDesc(ScaleUserRecord::getStartTime)
                .orderByDesc(ScaleUserRecord::getId));

        List<ScaleUserRecord> records = result.getRecords();
        if (CollUtil.isEmpty(records)) {
            return new PageResult<>(result.getTotal(), new ArrayList<>());
        }

        // 组装VO并批量解析用户名与版本号
        List<AdminScaleRecordVO> voList = records.stream()
                .map(record -> BeanUtil.copyProperties(record, AdminScaleRecordVO.class))
                .toList();

        Set<Long> userIds = records.stream().map(ScaleUserRecord::getUserId)
                .filter(ObjectUtil::isNotNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = scaleAuditNameHelper.resolveNames(userIds);

        Set<Long> scaleVersionIds = records.stream().map(ScaleUserRecord::getScaleVersionId)
                .filter(ObjectUtil::isNotNull).collect(Collectors.toSet());
        Map<Long, String> versionNoMap = new HashMap<>();
        if (CollUtil.isNotEmpty(scaleVersionIds)) {
            versionNoMap = scaleVersionMapper.selectBatchIds(scaleVersionIds).stream()
                    .collect(Collectors.toMap(ScaleVersion::getId, ScaleVersion::getVersionNo, (a, b) -> a));
        }

        for (int i = 0; i < records.size(); i++) {
            ScaleUserRecord record = records.get(i);
            AdminScaleRecordVO vo = voList.get(i);
            vo.setRecordId(record.getId());
            vo.setUserName(userNameMap.get(record.getUserId()));
            vo.setVersionNo(versionNoMap.get(record.getScaleVersionId()));
        }

        log.debug("[AdminScaleRecordServiceImpl-分页查询测评记录]完成，总数：{}", result.getTotal());
        return new PageResult<>(result.getTotal(), voList);
    }

    @Override
    public AdminScaleRecordDetailVO getRecordDetail(Long recordId) {
        log.info("[AdminScaleRecordServiceImpl-查询测评记录详情]，记录ID：{}", recordId);

        ScaleUserRecord record = scaleUserRecordMapper.selectById(recordId);
        if (record == null) {
            log.error("[AdminScaleRecordServiceImpl-查询测评记录详情]测评记录不存在，记录ID：{}", recordId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_FOUND);
        }

        AdminScaleRecordDetailVO detail = BeanUtil.copyProperties(record, AdminScaleRecordDetailVO.class);
        detail.setRecordId(record.getId());
        detail.setUserName(scaleAuditNameHelper.resolveNames(
                java.util.Collections.singleton(record.getUserId())).get(record.getUserId()));
        if (record.getScaleVersionId() != null) {
            ScaleVersion version = scaleVersionMapper.selectById(record.getScaleVersionId());
            detail.setVersionNo(version == null ? null : version.getVersionNo());
        }

        // 维度得分与维度名称
        List<ScaleUserDimScore> dimScores = scaleUserDimScoreMapper.selectList(new LambdaQueryWrapper<ScaleUserDimScore>()
                .eq(ScaleUserDimScore::getRecordId, recordId));
        if (CollUtil.isNotEmpty(dimScores)) {
            Set<Long> dimensionIds = dimScores.stream().map(ScaleUserDimScore::getDimensionId)
                    .filter(ObjectUtil::isNotNull).collect(Collectors.toSet());
            Map<Long, String> dimensionNameMap = new HashMap<>();
            if (CollUtil.isNotEmpty(dimensionIds)) {
                dimensionNameMap.putAll(scaleDimensionMapper.selectBatchIds(dimensionIds).stream()
                        .collect(Collectors.toMap(ScaleDimension::getId, ScaleDimension::getDimName, (a, b) -> a)));
            }
            List<AdminScaleDimScoreVO> dmList = dimScores.stream().map(dim -> AdminScaleDimScoreVO.builder()
                            .dimensionId(dim.getDimensionId())
                            .dimensionName(dimensionNameMap.get(dim.getDimensionId()))
                            .dimScore(dim.getDimScore())
                            .dimResult(dim.getDimResult())
                            .riskLevel(dim.getRiskLevel())
                            .build())
                    .collect(Collectors.toList());
            detail.setDimensionResults(dmList);
        }

        log.debug("[AdminScaleRecordServiceImpl-查询测评记录详情]完成，记录ID：{}", recordId);
        return detail;
    }

    @Override
    public List<AdminScaleAnswerDetailVO> listAnswerDetails(Long recordId) {
        log.info("[AdminScaleRecordServiceImpl-查询答题明细]，记录ID：{}", recordId);

        ScaleUserRecord record = scaleUserRecordMapper.selectById(recordId);
        if (record == null) {
            log.error("[AdminScaleRecordServiceImpl-查询答题明细]测评记录不存在，记录ID：{}", recordId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_FOUND);
        }

        List<ScaleUserAnswer> answers = scaleUserAnswerMapper.selectList(new LambdaQueryWrapper<ScaleUserAnswer>()
                .eq(ScaleUserAnswer::getRecordId, recordId));
        if (CollUtil.isEmpty(answers)) {
            return new ArrayList<>();
        }

        Set<Long> answerIds = answers.stream().map(ScaleUserAnswer::getId).collect(Collectors.toSet());
        Map<Long, List<ScaleUserAnswerOption>> optionGroupMap = new HashMap<>();
        if (CollUtil.isNotEmpty(answerIds)) {
            optionGroupMap.putAll(scaleUserAnswerOptionMapper.selectList(new LambdaQueryWrapper<ScaleUserAnswerOption>()
                            .in(CollUtil.isNotEmpty(answerIds), ScaleUserAnswerOption::getAnswerId, answerIds))
                    .stream().collect(Collectors.groupingBy(ScaleUserAnswerOption::getAnswerId)));
        }
        Map<Long, String> textMap = new HashMap<>();
        if (CollUtil.isNotEmpty(answerIds)) {
            Map<Long, String> textMapFinal = textMap;
            scaleUserAnswerTextMapper.selectList(new LambdaQueryWrapper<ScaleUserAnswerText>()
                            .in(CollUtil.isNotEmpty(answerIds), ScaleUserAnswerText::getAnswerId, answerIds))
                    .forEach(text -> textMapFinal.putIfAbsent(text.getAnswerId(), text.getAnswerText()));
        }

        List<AdminScaleAnswerDetailVO> voList = answers.stream().map(answer -> {
            List<ScaleUserAnswerOption> options = optionGroupMap.getOrDefault(answer.getId(), new ArrayList<>());
            List<AdminScaleSelectedOptionVO> selectedOptions = options.stream()
                    .map(option -> AdminScaleSelectedOptionVO.builder()
                            .optionId(option.getOptionId())
                            .optionText(option.getOptionText())
                            .build())
                    .collect(Collectors.toList());
            return AdminScaleAnswerDetailVO.builder()
                    .questionId(answer.getQuestionId())
                    .questionTitle(answer.getQuestionTitle())
                    .questionType(answer.getQuestionType())
                    .answerStatus(answer.getAnswerStatus())
                    .answerText(textMap.get(answer.getId()))
                    .selectedOptions(selectedOptions)
                    .createdTime(answer.getCreatedTime())
                    .build();
        }).collect(Collectors.toList());

        log.debug("[AdminScaleRecordServiceImpl-查询答题明细]完成，答题数：{}", voList.size());
        return voList;
    }

    @Override
    public AdminScaleRiskStatisticsVO riskStatistics(AdminScaleRiskStatisticsQueryDTO queryDTO) {
        Long scaleId = queryDTO.getScaleId();
        LocalDateTime startTime = queryDTO.getStartTime();
        LocalDateTime endTime = queryDTO.getEndTime();
        log.info("[AdminScaleRecordServiceImpl-风险预警统计]，scaleId：{}，startTime：{}，endTime：{}", scaleId, startTime, endTime);

        List<ScaleUserRecord> records = scaleUserRecordMapper.selectList(new LambdaQueryWrapper<ScaleUserRecord>()
                .eq(ScaleUserRecord::getFinishStatus, ScaleUserRecord.FINISH_STATUS_FINISHED)
                .eq(scaleId != null, ScaleUserRecord::getScaleId, scaleId)
                .ge(startTime != null, ScaleUserRecord::getStartTime, startTime)
                .le(endTime != null, ScaleUserRecord::getStartTime, endTime));

        long total = records.size();
        long noRisk = records.stream().filter(r -> r.getRiskLevel() != null
                && r.getRiskLevel() == ScaleUserRecord.RISK_LEVEL_NONE).count();
        long lowRisk = records.stream().filter(r -> r.getRiskLevel() != null
                && r.getRiskLevel() == ScaleUserRecord.RISK_LEVEL_LOW).count();
        long mediumRisk = records.stream().filter(r -> r.getRiskLevel() != null
                && r.getRiskLevel() == ScaleUserRecord.RISK_LEVEL_MEDIUM).count();
        long highRisk = records.stream().filter(r -> r.getRiskLevel() != null
                && r.getRiskLevel() == ScaleUserRecord.RISK_LEVEL_HIGH).count();

        String scaleName = null;
        if (scaleId != null) {
            Scale scale = scaleMapper.selectById(scaleId);
            scaleName = scale == null ? null : scale.getScaleName();
        }

        BigDecimal highRiskRate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(highRisk).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        AdminScaleRiskStatisticsVO vo = AdminScaleRiskStatisticsVO.builder()
                .scaleId(scaleId)
                .scaleName(scaleName)
                .totalCount(total)
                .noRiskCount(noRisk)
                .lowRiskCount(lowRisk)
                .mediumRiskCount(mediumRisk)
                .highRiskCount(highRisk)
                .highRiskRate(highRiskRate)
                .build();

        log.debug("[AdminScaleRecordServiceImpl-风险预警统计]完成，total：{}，highRisk：{}", total, highRisk);
        return vo;
    }
}