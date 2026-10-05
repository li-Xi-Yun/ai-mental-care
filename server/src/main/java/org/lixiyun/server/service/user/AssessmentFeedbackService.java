package org.lixiyun.server.service.user;

import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisFeedbackDTO;
import org.lixiyun.pojo.vo.user.conversation.AssessmentFeedbackVO;

/**
 * 诊断反馈服务接口
 * <p>提供诊断反馈的查询、提交/更新、删除等功能。</p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AssessmentFeedbackService {

    /**
     * 按诊断书 ID 查询反馈信息
     * <p>校验诊断书归属后，查询当前用户对指定诊断书的反馈记录；不存在时返回 null。</p>
     *
     * @param diagnosisId 诊断书 ID
     * @return 反馈信息 VO；未反馈时返回 null
     */
    AssessmentFeedbackVO getByDiagnosisId(Long diagnosisId);

    /**
     * 提交或更新诊断反馈（按诊断书+用户维度 upsert）
     * <p>校验诊断书归属后，若已存在反馈记录则更新字段，否则新增记录。</p>
     *
     * @param diagnosisId 诊断书 ID
     * @param dto         反馈提交 DTO
     */
    void saveOrUpdate(Long diagnosisId, EmotionDiagnosisFeedbackDTO dto);

    /**
     * 删除指定诊断书的反馈信息（逻辑删除）
     *
     * @param diagnosisId 诊断书 ID
     */
    void deleteByDiagnosisId(Long diagnosisId);
}