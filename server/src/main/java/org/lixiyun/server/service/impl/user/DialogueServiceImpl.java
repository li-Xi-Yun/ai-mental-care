package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.ConversationPendingAction;
import org.lixiyun.pojo.vo.user.conversation.ConversationMemoryVO;
import org.lixiyun.pojo.vo.user.conversation.ConversationPendingActionVO;
import org.lixiyun.pojo.vo.user.conversation.ConversationRoundVO;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.mapper.ConversationPendingActionMapper;
import org.lixiyun.server.service.user.DialogueService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author lixiyun
 * @since 2026-03-19 20:28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogueServiceImpl implements DialogueService {

    private final ConversationMemoryMapper conversationMemoryMapper;
    private final ConversationPendingActionMapper pendingActionMapper;

    @Override
    public PageResult<ConversationRoundVO> listMemory(Long conversationId, Integer pageNum, Integer pageSize) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("[对话服务-分页查询对话记录]: userId={}，conversationId={}，pageNum={}，pageSize={}",
                currentId, conversationId, pageNum, pageSize);

        // 1. 以轮次为分页单元：按 roundNum 去重分页，轮次倒序（最新在前）
        Page<ConversationMemory> roundPage = conversationMemoryMapper.selectPage(
                new PageQuery(pageSize, pageNum).build(),
                new LambdaQueryWrapper<ConversationMemory>()
                        .select(ConversationMemory::getRoundNum)
                        .eq(ConversationMemory::getUserId, currentId)
                        .eq(ConversationMemory::getConversationId, conversationId)
                        .eq(ConversationMemory::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                        .groupBy(ConversationMemory::getRoundNum)
                        .orderByDesc(ConversationMemory::getRoundNum)
        );

        List<ConversationMemory> roundRows = roundPage.getRecords();
        if (CollUtil.isEmpty(roundRows)) {
            log.debug("[对话服务-分页查询对话记录]: 无数据，会话ID：{}", conversationId);
            return new PageResult<>(roundPage.getTotal(), new ArrayList<>());
        }
        List<Integer> roundNums = roundRows.stream()
                .map(ConversationMemory::getRoundNum)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (roundNums.isEmpty()) {
            log.debug("[对话服务-分页查询对话记录]: 各轮次轮次号为空，会话ID：{}", conversationId);
            return new PageResult<>(roundPage.getTotal(), new ArrayList<>());
        }
        log.debug("[对话服务-分页查询对话记录]: 当前页轮次数：{}，总数：{}", roundNums.size(), roundPage.getTotal());

        // 2. 查询当前页各轮次下的全部消息（按轮次、id 正序）
        List<ConversationMemory> messages = conversationMemoryMapper.selectList(
                new LambdaQueryWrapper<ConversationMemory>()
                        .eq(ConversationMemory::getUserId, currentId)
                        .eq(ConversationMemory::getConversationId, conversationId)
                        .in(ConversationMemory::getRoundNum, roundNums)
                        .orderByAsc(ConversationMemory::getRoundNum)
                        .orderByAsc(ConversationMemory::getId));
        Map<Integer, List<ConversationMemory>> messagesByRound = messages.stream()
                .collect(Collectors.groupingBy(ConversationMemory::getRoundNum,
                        Collectors.toList()));

        // 3. 仅查询当前页各轮次对应的待处理交互（工具信息），不含 tool_result
        List<ConversationPendingAction> pendingActions = pendingActionMapper.selectList(
                new LambdaQueryWrapper<ConversationPendingAction>()
                        .eq(ConversationPendingAction::getConversationId, conversationId)
                        .in(ConversationPendingAction::getRoundNum, roundNums)
        );
        Map<Integer, List<ConversationPendingActionVO>> pendingByRound = new HashMap<>();
        for (ConversationPendingAction action : pendingActions) {
            pendingByRound.computeIfAbsent(action.getRoundNum(), k -> new ArrayList<>())
                    .add(buildPendingActionVO(action));
        }
        log.debug("[对话服务-分页查询对话记录]: 待处理交互数：{}", pendingActions.size());

        // 4. 组装轮次 VO
        List<ConversationRoundVO> voList = new ArrayList<>(roundNums.size());
        for (Integer round : roundNums) {
            List<ConversationMemoryVO> memoryVOs = messagesByRound.getOrDefault(round, List.of())
                    .stream()
                    .map(mem -> BeanUtil.copyProperties(mem, ConversationMemoryVO.class))
                    .toList();
            voList.add(ConversationRoundVO.builder()
                    .roundNum(round)
                    .messages(memoryVOs)
                    .pendingActions(pendingByRound.getOrDefault(round, List.of()))
                    .build());
        }
        log.info("[对话服务-分页查询对话记录]: 完成，返回轮次数：{}，总数：{}", voList.size(), roundPage.getTotal());
        return new PageResult<>(roundPage.getTotal(), voList);
    }

    /**
     * 构建待处理交互 VO：action_data 解析为结构化 JSON 对象（前端无需再处理字符串）
     *
     * @param action 待处理交互记录
     * @return 待处理交互 VO
     */
    private ConversationPendingActionVO buildPendingActionVO(ConversationPendingAction action) {
        if (action == null) {
            return null;
        }
        return ConversationPendingActionVO.builder()
                .id(action.getId())
                .actionType(action.getActionType())
                .roundNum(action.getRoundNum())
                .actionData(action.getActionData() == null ? null : JsonUtils.parseObject(action.getActionData(), Object.class))
                .status(action.getStatus())
                .expireTime(action.getExpireTime())
                .completedTime(action.getCompletedTime())
                .createdTime(action.getCreatedTime())
                .build();
    }

}
