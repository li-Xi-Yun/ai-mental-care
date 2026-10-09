package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.user.conversation.ConversationRoundVO;

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
     * 按轮次聚合返回：每条记录为一个轮次（含该轮的全部消息与关联的待处理人工交互），
     * 轮次按创建时间逆序排列。
     * </p>
     *
     * @param conversationId 会话 ID
     * @param pageNum 当前页码
     * @param pageSize 每页数量
     * @return 分页查询结果，按轮次聚合的对话记录
     */
    PageResult<ConversationRoundVO> listMemory(Long conversationId, Integer pageNum, Integer pageSize);

}