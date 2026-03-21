package org.lixiyun.server.service.impl;

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
import org.lixiyun.pojo.entity.*;
import org.lixiyun.pojo.vo.conversation.ConversationMemoryVO;
import org.lixiyun.server.constant.CommonConstant;
import org.lixiyun.server.mapper.*;
import org.lixiyun.server.service.DialogueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author lixiyun
 * @since 2026-03-19 20:28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogueServiceImpl implements DialogueService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final GraphCheckpointMapper graphCheckpointMapper;
    private final EmotionAnalysisMapper emotionAnalysisMapper;
    private final EmotionDiagnosisMapper emotionDiagnosisMapper;

    @Override
    public PageResult<ConversationMemoryVO> listMemory(Long conversationId, Integer pageNum, Integer pageSize) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // 构建分页对象
        Page<ConversationMemory> pageParam = new PageQuery(pageSize, pageNum).build();

        // 构建查询条件：用户 ID + 会话 ID + 未删除
        LambdaQueryWrapper<ConversationMemory> wrapper = new LambdaQueryWrapper<ConversationMemory>()
                .eq(ConversationMemory::getUserId, currentId)
                .eq(ConversationMemory::getConversationId, conversationId)
                .eq(ConversationMemory::getDeleted, CommonConstant.DELETE_FLAG_NO)
                .orderByDesc(ConversationMemory::getCreatedTime);

        // 执行分页查询
        Page<ConversationMemory> memoryPage = conversationMemoryMapper.selectPage(pageParam, wrapper);

        // 转换为 VO 并返回分页结果
        return PageResult.convert(memoryPage, ConversationMemoryVO.class);
    }

    @Override
    @Transactional
    public void deleteConversationMemory(Long conversationId, Integer roundNum) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // 1. 删除对话表数据，where 用户 ID、对话 ID、删除状态
        int removed = conversationMemoryMapper.delete(new LambdaQueryWrapper<ConversationMemory>()
                .eq(ConversationMemory::getConversationId, conversationId)
                .eq(ConversationMemory::getRoundNum, roundNum)
                .eq(ConversationMemory::getUserId, currentId)
                .eq(ConversationMemory::getDeleted, CommonConstant.DELETE_FLAG_NO));

        // 判断是否有删除
        if (removed <= 0) {
            log.error("删除对话失败：对话不存在或无权删除，dialogueId: {}", conversationId);
            throw new BusinessException(ConversationExceptionEnum.DIALOGUE_NOT_EXIST);
        }

        // 2. 删除该对话的所有检查点数据，where 会话 ID、删除状态
        graphCheckpointMapper.delete(new LambdaQueryWrapper<GraphCheckpoint>()
                .eq(GraphCheckpoint::getConversationId, conversationId)
                .eq(GraphCheckpoint::getRoundNum, roundNum)
                .eq(GraphCheckpoint::getDeleted, CommonConstant.DELETE_FLAG_NO));

        // 3. 删除该对话的情绪分析数据，where 会话 ID、删除状态
        emotionAnalysisMapper.delete(new LambdaQueryWrapper<EmotionAnalysis>()
                .eq(EmotionAnalysis::getConversationId, conversationId)
                .eq(EmotionAnalysis::getRoundNum, roundNum)
                .eq(EmotionAnalysis::getDeleted, CommonConstant.DELETE_FLAG_NO));

        // 4. 删除该对话的情绪诊断数据，where 会话 ID、删除状态
        emotionDiagnosisMapper.delete(new LambdaQueryWrapper<EmotionDiagnosis>()
                .eq(EmotionDiagnosis::getConversationId, conversationId)
                .eq(EmotionDiagnosis::getRoundNum, roundNum)
                .eq(EmotionDiagnosis::getDeleted, CommonConstant.DELETE_FLAG_NO));

        // 5. 修改对话表当前轮次，where ID、删除状态
        conversationMapper.update(new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .set(Conversation::getCurrentRound, roundNum - 1));

        log.info("对话级联删除成功，conversationId: {}", conversationId);
    }
}
