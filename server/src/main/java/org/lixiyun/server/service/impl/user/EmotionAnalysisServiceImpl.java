package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.pojo.vo.user.conversation.EmotionAnalysisDetailVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionAnalysisVO;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.mapper.EmotionAnalysisMapper;
import org.lixiyun.server.service.user.EmotionAnalysisService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-03-19 20:28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnalysisServiceImpl implements EmotionAnalysisService {

    private final EmotionAnalysisMapper emotionAnalysisMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;

    @Override
    public PageResult<EmotionAnalysisVO> listEmotionAnalysis(Long conversationId, Integer pageNum, Integer pageSize) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // 构建分页对象
        Page<EmotionAnalysis> pageParam = new PageQuery(pageSize, pageNum).build();

        // DB 查询情绪分析表数据，where 用户 ID、会话 ID、删除状态，按创建时间逆序排序
        LambdaQueryWrapper<EmotionAnalysis> wrapper = new LambdaQueryWrapper<EmotionAnalysis>()
                .eq(EmotionAnalysis::getUserId, currentId)
                .eq(EmotionAnalysis::getConversationId, conversationId)
                .orderByDesc(EmotionAnalysis::getCreatedTime);

        // 执行分页查询
        Page<EmotionAnalysis> analysisPage = emotionAnalysisMapper.selectPage(pageParam, wrapper);

        // 转换为 VO 并返回分页结果
        return PageResult.convert(analysisPage, EmotionAnalysisVO.class);
    }

    @Override
    public EmotionAnalysisDetailVO getEmotionAnalysisDetail(Long analysisId) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // DB 查询对应的情绪分析表，where 分析 ID、用户 ID、删除状态
        EmotionAnalysis analysis = emotionAnalysisMapper.selectOne(new LambdaQueryWrapper<EmotionAnalysis>()
                .eq(EmotionAnalysis::getId, analysisId)
                .eq(EmotionAnalysis::getUserId, currentId));

        if (analysis == null) {
            log.error("情绪分析记录不存在或无权查看，analysisId: {}", analysisId);
            throw new BusinessException(ConversationExceptionEnum.EMOTION_ANALYSIS_NOT_EXIST);
        }

        // DB 查询对应的聊天信息表，where 会话 ID、轮次、删除状态
        List<ConversationMemory> messageList = conversationMemoryMapper.selectList(new LambdaQueryWrapper<ConversationMemory>()
                .eq(ConversationMemory::getConversationId, analysis.getConversationId())
                .eq(ConversationMemory::getRoundNum, analysis.getRoundNum())
                .orderByAsc(ConversationMemory::getCreatedTime)
        );

        StringBuilder userString = new StringBuilder();
        StringBuilder assistantString = new StringBuilder();
        StringBuilder thinkString = new StringBuilder();

        for (ConversationMemory message : messageList) {
            if(message != null && message.getType().equals(MessageType.USER.getName())){
                userString.append(message.getContent()).append("\n");
            } else if(message != null && message.getType().equals(MessageType.ASSISTANT.getName())){
                assistantString.append(message.getContent()).append("\n");
            } else if(message != null && message.getType().equals(MessageType.THINKING.getName())){
                thinkString.append(message.getContent()).append("\n");
            }
        }

        return EmotionAnalysisDetailVO.builder()
                .userContent(userString.toString())
                .modelContent(assistantString.toString())
                .thinkContent(thinkString.toString())
                .build();
    }
}
