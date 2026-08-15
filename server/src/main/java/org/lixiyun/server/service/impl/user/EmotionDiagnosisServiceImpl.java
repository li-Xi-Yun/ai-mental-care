package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisFeedbackDTO;
import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisQueryDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.pojo.vo.user.conversation.DiagnosisConversationVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionDiagnosisListVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionDiagnosisVO;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.lixiyun.server.service.user.EmotionDiagnosisService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 情感诊断书服务实现
 *
 * @author lixiyun
 * @since 2026-08-15 14:15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionDiagnosisServiceImpl implements EmotionDiagnosisService {

    private final EmotionDiagnosisMapper emotionDiagnosisMapper;
    private final ConversationMapper conversationMapper;

    @Override
    public PageResult<DiagnosisConversationVO> listByUserCenter(EmotionDiagnosisQueryDTO queryDTO) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();
        String conversationName = queryDTO.getConversationName();

        log.debug("情感诊断书Service-查询用户拥有诊断的会话列表: userId={}, conversationName={}", currentId, conversationName);

        PageHelper.startPage(pageNum, pageSize);
        List<DiagnosisConversationVO> conversationList = emotionDiagnosisMapper.selectDiagnosisConversationPage(currentId, conversationName);
        PageInfo<DiagnosisConversationVO> pageInfo = new PageInfo<>(conversationList);

        return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public PageResult<EmotionDiagnosisListVO> listByConversationId(Long conversationId, Integer pageNum, Integer pageSize) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        log.debug("情感诊断书Service-按会话ID查询诊断书: userId={}, conversationId={}", currentId, conversationId);

        // 查询会话信息，校验归属权并获取会话名称
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getUserId, currentId)
        );

        if (conversation == null) {
            log.error("情感诊断书Service-会话不存在或无权访问: conversationId={}, userId={}", conversationId, currentId);
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }

        // 构建分页对象，按更新时间逆序排序
        Page<EmotionDiagnosis> pageParam = new PageQuery(pageSize, pageNum).build();

        // 查询该会话下的诊断书列表
        Page<EmotionDiagnosis> diagnosisPage = emotionDiagnosisMapper.selectPage(pageParam, new LambdaQueryWrapper<EmotionDiagnosis>()
                .eq(EmotionDiagnosis::getConversationId, conversationId)
                .eq(EmotionDiagnosis::getUserId, currentId)
                .orderByDesc(EmotionDiagnosis::getCreatedTime)
        );

        // 转换为 VO 并填充会话名称
        return PageResult.convert(diagnosisPage, EmotionDiagnosisListVO.class);
    }

    @Override
    public EmotionDiagnosisVO getDiagnosisDetail(Long diagnosisId) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        log.debug("情感诊断书Service-获取诊断书详情: userId={}, diagnosisId={}", currentId, diagnosisId);

        EmotionDiagnosis diagnosis = emotionDiagnosisMapper.selectOne(new LambdaQueryWrapper<EmotionDiagnosis>()
                .eq(EmotionDiagnosis::getId, diagnosisId)
                .eq(EmotionDiagnosis::getUserId, currentId));

        if (diagnosis == null) {
            log.error("情感诊断书Service-诊断书不存在或无权查看: diagnosisId={}, userId={}", diagnosisId, currentId);
            throw new BusinessException(ConversationExceptionEnum.EMOTION_DIAGNOSIS_NOT_EXIST);
        }

        return BeanUtil.copyProperties(diagnosis, EmotionDiagnosisVO.class);
    }

    @Override
    public void updateFeedback(Long diagnosisId, EmotionDiagnosisFeedbackDTO feedbackDTO) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        Integer diagnosisScore = feedbackDTO.getDiagnosisScore();
        String feedbackContent = feedbackDTO.getFeedbackContent();
        Integer agreeRiskJudge = feedbackDTO.getAgreeRiskJudge();
        Integer agreeSuggestionSelf = feedbackDTO.getAgreeSuggestionSelf();
        Integer agreeSuggestionSocial = feedbackDTO.getAgreeSuggestionSocial();
        Integer agreeSuggestionProfessional = feedbackDTO.getAgreeSuggestionProfessional();
        Integer useSuggestion = feedbackDTO.getUseSuggestion();

        log.debug("情感诊断书Service-用户反馈诊断书: userId={}, diagnosisId={}", currentId, diagnosisId);

        LambdaUpdateWrapper<EmotionDiagnosis> updateWrapper = new LambdaUpdateWrapper<EmotionDiagnosis>()
                .eq(EmotionDiagnosis::getId, diagnosisId)
                .eq(EmotionDiagnosis::getUserId, currentId)
                .set(diagnosisScore != null, EmotionDiagnosis::getDiagnosisScore, diagnosisScore)
                .set(feedbackContent != null, EmotionDiagnosis::getFeedbackContent, feedbackContent)
                .set(agreeRiskJudge != null, EmotionDiagnosis::getAgreeRiskJudge, agreeRiskJudge)
                .set(agreeSuggestionSelf != null, EmotionDiagnosis::getAgreeSuggestionSelf, agreeSuggestionSelf)
                .set(agreeSuggestionSocial != null, EmotionDiagnosis::getAgreeSuggestionSocial, agreeSuggestionSocial)
                .set(agreeSuggestionProfessional != null, EmotionDiagnosis::getAgreeSuggestionProfessional, agreeSuggestionProfessional)
                .set(useSuggestion != null, EmotionDiagnosis::getUseSuggestion, useSuggestion)
                .set(EmotionDiagnosis::getFeedbackTime, LocalDateTime.now());

        int updateCount = emotionDiagnosisMapper.update(null, updateWrapper);
        if (updateCount == 0) {
            log.error("情感诊断书Service-诊断书不存在或无权操作: diagnosisId={}, userId={}", diagnosisId, currentId);
            throw new BusinessException(ConversationExceptionEnum.EMOTION_DIAGNOSIS_NOT_EXIST);
        }

        log.info("情感诊断书Service-用户反馈诊断书成功: diagnosisId={}", diagnosisId);
    }

}