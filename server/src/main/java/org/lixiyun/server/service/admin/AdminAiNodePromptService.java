package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.config.AiNodePromptDTO;
import org.lixiyun.pojo.vo.admin.config.AiNodePromptVO;

/**
 * 管理员AI节点提示词服务接口
 *
 * @author lixiyun
 * @since 2026-09-07
 */
public interface AdminAiNodePromptService {

    /**
     * 获取AI节点提示词
     *
     * @param id 节点配置主键ID
     * @return 提示词信息
     */
    AiNodePromptVO getPrompt(Long id);

    /**
     * 修改AI节点提示词
     *
     * @param id  节点配置主键ID
     * @param dto 提示词修改DTO {@link AiNodePromptDTO}
     */
    void updatePrompt(Long id, AiNodePromptDTO dto);
}