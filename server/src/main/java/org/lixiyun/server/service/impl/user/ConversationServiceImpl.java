package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.user.conversation.ConversationInfoDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.pojo.vo.user.conversation.ConversationVO;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.mapper.EmotionAnalysisMapper;
import org.lixiyun.server.service.user.ConversationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author lixiyun
 * @since 2026-03-19 20:28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final EmotionAnalysisMapper emotionAnalysisMapper;

    @Override
    public PageResult<ConversationVO> listDisplay(Integer pageNum, Integer pageSize) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // 构建分页对象，按更新时间逆序排序
        Page<Conversation> pageParam = new PageQuery(pageSize, pageNum).build();

        // 构建查询条件：用户 ID + 未删除
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, currentId)
                .orderByDesc(Conversation::getUpdatedTime);

        // 执行分页查询
        Page<Conversation> conversationPage = conversationMapper.selectPage(pageParam, wrapper);

        // 转换为 VO 并返回分页结果
        return PageResult.convert(conversationPage, ConversationVO.class);
    }

    @Override
    public void updateConversationInfo(Long conversationId, ConversationInfoDTO conversationInfoDTO) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        Conversation conversation = BeanUtil.copyProperties(conversationInfoDTO, Conversation.class);
        conversationMapper.update(conversation, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getUserId, currentId));
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        int delete = conversationMapper.delete(new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getUserId, currentId)
                .eq(Conversation::getId, conversationId));

        if (delete == 0) {
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_EXIST);
        }

        // 级联删除所有相关数据
        conversationMemoryMapper.delete(new LambdaUpdateWrapper<ConversationMemory>()
                .eq(ConversationMemory::getConversationId, conversationId));
        emotionAnalysisMapper.delete(new LambdaUpdateWrapper<EmotionAnalysis>()
                .eq(EmotionAnalysis::getConversationId, conversationId));
    }
}
