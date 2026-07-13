package org.lixiyun.common.agent.skill.service;

import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-07-12 14:32
 */
public interface SkillConversationService {

    int updateConversationSessionMapping(String conversationId, Map<String, String> sessionMapping);

}
