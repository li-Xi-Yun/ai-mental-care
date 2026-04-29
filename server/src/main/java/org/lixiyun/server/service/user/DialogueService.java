package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.user.conversation.ConversationMemoryVO;

/**
 * 对话服务接口
 * <p>
 * 提供对话记忆列表查询、对话记忆删除等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-03-19 20:27
 */
public interface DialogueService {

    /**
     * 分页查询对话记忆列表
     * <p>
     * 按创建时间逆序排列，返回指定会话下的所有对话记录
     * </p>
     *
     * @param conversationId 会话 ID
     * @param pageNum 当前页码
     * @param pageSize 每页数量
     * @return 分页查询结果，包含对话记忆 VO 列表
     */
    PageResult<ConversationMemoryVO> listMemory(Long conversationId, Integer pageNum, Integer pageSize);

    /**
     * 删除对话记忆（级联删除相关数据）
     * <p>
     * 删除指定轮次的对话及其相关的所有数据，包括检查点、情绪分析、情绪诊断等
     * </p>
     *
     * @param dialogueId 对话 ID
     * @param roundNum 轮次
     */
    /**
     * 删除对话记忆（级联删除相关数据）
     * @param conversationId 会话ID
     * @param roundNum 轮次
     */
    void deleteConversationMemory(Long conversationId, Integer roundNum);
}
