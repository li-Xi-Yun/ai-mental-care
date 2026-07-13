package org.lixiyun.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.skill.service.SkillConversationService;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.server.mapper.ConversationMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-07-12 14:33
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillConversationServiceImpl implements SkillConversationService {

    private final ConversationMapper conversationMapper;

    @Override
    public int updateConversationSessionMapping(String conversationId, Map<String, String> sessionMapping) {
        return conversationMapper.update(null,
                new LambdaUpdateWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .set(Conversation::getSessionMapping, sessionMapping)
        );
    }
}
