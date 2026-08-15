package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.vo.user.conversation.ConversationMemoryVO;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.service.user.DialogueService;
import org.springframework.stereotype.Service;

/**
 * @author lixiyun
 * @since 2026-03-19 20:28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogueServiceImpl implements DialogueService {

    private final ConversationMemoryMapper conversationMemoryMapper;

    @Override
    public PageResult<ConversationMemoryVO> listMemory(Long conversationId, Integer pageNum, Integer pageSize) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // 构建分页对象
        Page<ConversationMemory> pageParam = new PageQuery(pageSize, pageNum).build();

        // 构建查询条件：用户 ID + 会话 ID + 未删除
        LambdaQueryWrapper<ConversationMemory> wrapper = new LambdaQueryWrapper<ConversationMemory>()
                .eq(ConversationMemory::getUserId, currentId)
                .eq(ConversationMemory::getConversationId, conversationId)
                .eq(ConversationMemory::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByDesc(ConversationMemory::getCreatedTime);

        // 执行分页查询
        Page<ConversationMemory> memoryPage = conversationMemoryMapper.selectPage(pageParam, wrapper);

        // 转换为 VO 并返回分页结果
        return PageResult.convert(memoryPage, ConversationMemoryVO.class);
    }

}
