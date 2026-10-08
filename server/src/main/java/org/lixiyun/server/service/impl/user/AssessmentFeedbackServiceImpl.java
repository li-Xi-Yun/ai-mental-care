package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisFeedbackDTO;
import org.lixiyun.pojo.entity.emotion.AssessmentFeedback;
import org.lixiyun.pojo.entity.emotion.EmotionDiagnosis;
import org.lixiyun.pojo.vo.user.conversation.AssessmentFeedbackVO;
import org.lixiyun.server.mapper.AssessmentFeedbackMapper;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.lixiyun.server.service.user.AssessmentFeedbackService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 诊断反馈服务实现
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentFeedbackServiceImpl implements AssessmentFeedbackService {

    private final AssessmentFeedbackMapper assessmentFeedbackMapper;
    private final EmotionDiagnosisMapper emotionDiagnosisMapper;

    @Override
    public AssessmentFeedbackVO getByDiagnosisId(Long diagnosisId) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.debug("诊断反馈Service-查询反馈，diagnosisId={}，userId={}", diagnosisId, currentId);

        // 校验诊断书存在且归属当前用户
        validateDiagnosisOwnership(diagnosisId, currentId);

        AssessmentFeedback feedback = assessmentFeedbackMapper.selectOne(new LambdaQueryWrapper<AssessmentFeedback>()
                .eq(AssessmentFeedback::getDiagnosisId, diagnosisId)
                .eq(AssessmentFeedback::getUserId, currentId)
        );
        if (feedback == null) {
            log.info("诊断反馈Service-暂无反馈记录，diagnosisId={}", diagnosisId);
            return null;
        }
        return BeanUtil.copyProperties(feedback, AssessmentFeedbackVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(Long diagnosisId, EmotionDiagnosisFeedbackDTO dto) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("诊断反馈Service-提交反馈，diagnosisId={}，userId={}，打分={}", diagnosisId, currentId, dto.getDiagnosisScore());

        // 校验诊断书存在且归属当前用户
        validateDiagnosisOwnership(diagnosisId, currentId);

        AssessmentFeedback existing = assessmentFeedbackMapper.selectOne(new LambdaQueryWrapper<AssessmentFeedback>()
                .eq(AssessmentFeedback::getDiagnosisId, diagnosisId)
                .eq(AssessmentFeedback::getUserId, currentId));

        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            // 更新已有反馈（只更新非空字段）
            LambdaUpdateWrapper<AssessmentFeedback> updateWrapper = new LambdaUpdateWrapper<AssessmentFeedback>()
                    .eq(AssessmentFeedback::getId, existing.getId())
                    .set(dto.getDiagnosisScore() != null, AssessmentFeedback::getDiagnosisScore, dto.getDiagnosisScore())
                    .set(dto.getFeedbackContent() != null, AssessmentFeedback::getFeedbackContent, dto.getFeedbackContent())
                    .set(dto.getAgreeRiskJudge() != null, AssessmentFeedback::getAgreeRiskJudge, dto.getAgreeRiskJudge())
                    .set(dto.getAgreeSuggestionSelf() != null, AssessmentFeedback::getAgreeSuggestionSelf, dto.getAgreeSuggestionSelf())
                    .set(dto.getAgreeSuggestionSocial() != null, AssessmentFeedback::getAgreeSuggestionSocial, dto.getAgreeSuggestionSocial())
                    .set(dto.getAgreeSuggestionProfessional() != null, AssessmentFeedback::getAgreeSuggestionProfessional, dto.getAgreeSuggestionProfessional())
                    .set(dto.getUseSuggestion() != null, AssessmentFeedback::getUseSuggestion, dto.getUseSuggestion())
                    .set(AssessmentFeedback::getFeedbackTime, now);
            assessmentFeedbackMapper.update(null, updateWrapper);
            log.info("诊断反馈Service-更新反馈成功，diagnosisId={}，feedbackId={}", diagnosisId, existing.getId());
        } else {
            // 新增反馈记录
            AssessmentFeedback feedback = AssessmentFeedback.builder()
                    .userId(currentId)
                    .diagnosisId(diagnosisId)
                    .diagnosisScore(dto.getDiagnosisScore())
                    .feedbackContent(dto.getFeedbackContent())
                    .agreeRiskJudge(dto.getAgreeRiskJudge())
                    .agreeSuggestionSelf(dto.getAgreeSuggestionSelf())
                    .agreeSuggestionSocial(dto.getAgreeSuggestionSocial())
                    .agreeSuggestionProfessional(dto.getAgreeSuggestionProfessional())
                    .useSuggestion(dto.getUseSuggestion())
                    .feedbackTime(now)
                    .createdTime(now)
                    .updatedTime(now)
                    .deleted(0)
                    .build();
            assessmentFeedbackMapper.insert(feedback);
            log.info("诊断反馈Service-新增反馈成功，diagnosisId={}，feedbackId={}", diagnosisId, feedback.getId());
        }
    }

    @Override
    public void deleteByDiagnosisId(Long diagnosisId) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.debug("诊断反馈Service-删除反馈，diagnosisId={}，userId={}", diagnosisId, currentId);

        // 校验诊断书存在且归属当前用户
        validateDiagnosisOwnership(diagnosisId, currentId);

        int deleted = assessmentFeedbackMapper.delete(new LambdaUpdateWrapper<AssessmentFeedback>()
                .eq(AssessmentFeedback::getDiagnosisId, diagnosisId)
                .eq(AssessmentFeedback::getUserId, currentId));
        if (deleted == 0) {
            log.warn("诊断反馈Service-反馈记录不存在或已删除，diagnosisId={}", diagnosisId);
        }
    }

    /**
     * 校验诊断书存在且归属当前用户
     *
     * @param diagnosisId 诊断书 ID
     * @param userId      当前用户 ID
     */
    private void validateDiagnosisOwnership(Long diagnosisId, Long userId) {
        EmotionDiagnosis diagnosis = emotionDiagnosisMapper.selectOne(new LambdaQueryWrapper<EmotionDiagnosis>()
                .eq(EmotionDiagnosis::getId, diagnosisId)
                .eq(EmotionDiagnosis::getUserId, userId));
        if (diagnosis == null) {
            log.error("诊断反馈Service-诊断书不存在或无权访问，diagnosisId={}，userId={}", diagnosisId, userId);
            throw new BusinessException(ConversationExceptionEnum.EMOTION_DIAGNOSIS_NOT_EXIST);
        }
    }
}