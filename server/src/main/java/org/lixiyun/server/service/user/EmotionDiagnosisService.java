package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisFeedbackDTO;
import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisQueryDTO;
import org.lixiyun.pojo.vo.user.conversation.DiagnosisConversationVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionDiagnosisListVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionDiagnosisVO;

/**
 * 情感诊断书服务接口
 * <p>
 * 提供情感诊断书分页查询、详情查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-08-15 14:15
 */
public interface EmotionDiagnosisService {

    /**
     * 用户个人中心分页查询拥有诊断数据的会话列表
     * <p>
     * 按会话名称模糊匹配，按诊断更新时间逆序排列
     * </p>
     *
     * @param queryDTO 查询条件，包含分页参数和会话名称
     * @return 分页查询结果，包含拥有诊断数据的会话列表 VO
     */
    PageResult<DiagnosisConversationVO> listByUserCenter(EmotionDiagnosisQueryDTO queryDTO);

    /**
     * 按会话 ID 分页查询情感诊断书
     * <p>
     * 返回指定会话下的所有诊断书，按更新时间逆序排列
     * </p>
     *
     * @param conversationId 会话 ID
     * @param pageNum 当前页码
     * @param pageSize 每页数量
     * @return 分页查询结果，包含情感诊断书列表 VO
     */
    PageResult<EmotionDiagnosisListVO> listByConversationId(Long conversationId, Integer pageNum, Integer pageSize);

    /**
     * 获取诊断书详情
     * <p>
     * 查询指定诊断书的完整详情数据
     * </p>
     *
     * @param diagnosisId 诊断书 ID
     * @return 情感诊断书详情 VO
     */
    EmotionDiagnosisVO getDiagnosisDetail(Long diagnosisId);

    /**
     * 用户反馈诊断书
     * <p>
     * 用户对指定诊断书提交反馈信息，包括打分、认同度、采纳情况等，
     * 同时自动记录反馈时间
     * </p>
     *
     * @param diagnosisId 诊断书 ID
     * @param feedbackDTO 用户反馈 DTO
     */
    void updateFeedback(Long diagnosisId, EmotionDiagnosisFeedbackDTO feedbackDTO);

}