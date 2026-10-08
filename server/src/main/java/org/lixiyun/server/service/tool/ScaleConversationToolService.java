package org.lixiyun.server.service.tool;

import org.lixiyun.pojo.dto.user.tool.ScaleConversationBindRecordDTO;
import org.lixiyun.pojo.dto.user.tool.ScaleConversationCompleteDTO;

/**
 * AI对话量表工具服务接口
 * <p>
 * 承接对话量表卡片的人工交互：保存用户测评记录ID、接收用户作答完成状态。
 * 与对话模型侧的 {@code ScaleConversationTools}（@Tool 工具类）互补：
 * 工具类负责创建待处理记录，本服务负责处理用户侧的点击回执。
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-07
 */
public interface ScaleConversationToolService {

    /**
     * 保存用户测评记录ID
     * <p>
     * 校验待处理记录归属当前用户且会话匹配后，把 recordId 写入 action_data 中持久化，
     * 保证用户刷新页面后仍可通过待处理记录恢复作答上下文。
     * </p>
     *
     * @param dto 保存请求（工具ID、会话ID、测评记录ID）
     */
    void bindRecord(ScaleConversationBindRecordDTO dto);

    /**
     * 接收用户点击是否答完题目
     * <p>
     * 已作答（answered=1）：校验测评记录已完成提交，读取库内答题明细调用分析节点生成分析文本，
     * 写入 tool_result 并把状态置为待对话注入；未作答（answered=0）：直接取消本次待处理交互。
     * </p>
     *
     * @param dto 提交请求（工具ID、会话ID、测评记录ID、是否作答）
     */
    void completeAnswer(ScaleConversationCompleteDTO dto);

}
