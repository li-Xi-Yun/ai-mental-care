package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.user.conversation.EmotionAnalysisDetailVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionAnalysisVO;

/**
 * 情绪分析服务接口
 * <p>
 * 提供情绪诊断、情绪分析列表查询、情绪分析详情查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-03-19 20:27
 */
public interface EmotionAnalysisService {

    /**
     * 分页查询情绪分析列表
     * <p>
     * 按创建时间逆序排列，返回指定会话下的所有情绪分析记录
     * </p>
     *
     * @param conversationId 会话 ID
     * @param pageNum 当前页码
     * @param pageSize 每页数量
     * @return 分页查询结果，包含情绪分析 VO 列表
     */
    PageResult<EmotionAnalysisVO> listEmotionAnalysis(Long conversationId, Integer pageNum, Integer pageSize);

    /**
     * 获取情绪分析详情（包含对话内容）
     * <p>
     * 查询指定情绪分析记录的详细信息，包括用户消息、助手回复和情绪分析内容
     * </p>
     *
     * @param analysisId 情绪分析 ID
     * @return 情绪分析详情 VO 对象，包含用户内容、模型回复和思考内容
     */
    EmotionAnalysisDetailVO getEmotionAnalysisDetail(Long analysisId);
}
