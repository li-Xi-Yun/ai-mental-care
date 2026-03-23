package org.lixiyun.server.service;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.conversation.ConversationInfoDTO;
import org.lixiyun.pojo.vo.conversation.ConversationVO;

/**
 * 会话服务接口
 * <p>
 * 提供会话列表查询、会话信息更新、会话删除等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-03-19 20:27
 */
public interface ConversationService {

    /**
     * 分页查询会话列表
     * <p>
     * 按更新时间逆序排列，返回当前登录用户的所有会话
     * </p>
     *
     * @param pageNum 当前页码
     * @param pageSize 每页数量
     * @return 分页查询结果，包含会话 VO 列表
     */
    PageResult<ConversationVO> listDisplay(Integer pageNum, Integer pageSize);

    /**
     * 更新会话信息
     * <p>
     * 修改指定会话的名称、上下文概括等信息
     * </p>
     *
     * @param conversationId 会话 ID
     * @param conversationInfoDTO 会话信息 DTO，包含需要更新的字段
     */
    void updateConversationInfo(Long conversationId, ConversationInfoDTO conversationInfoDTO);

    /**
     * 删除会话（级联删除相关数据）
     * <p>
     * 删除指定会话及其相关的所有数据，包括检查点、情绪分析、情绪诊断等
     * </p>
     *
     * @param conversationId 会话 ID
     */
    void deleteConversation(Long conversationId);

    /**
     * 初始化会话名称
     * <p>
     * 根据用户输入和模型输出，生成会话名称
     * </p>
     *
     * @param conversationId 会话 ID
     * @param userInput 用户输入
     * @param modelOutput 模型输出
     * @return 生成的会话名称
     */
    String initializeConversationName(Long conversationId, String userInput, String modelOutput);
}
