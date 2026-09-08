package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.config.AiNodeHistoryQueryDTO;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigHistoryVO;

/**
 * 管理员AI节点配置变更历史服务接口
 *
 * @author lixiyun
 * @since 2026-09-07
 */
public interface AdminAiNodeHistoryService {

    /**
     * 分页查询AI节点配置变更历史
     *
     * @param id       节点配置主键ID
     * @param queryDTO 查询条件 {@link AiNodeHistoryQueryDTO}
     * @return 分页结果
     */
    PageResult<AiNodeConfigHistoryVO> pageHistory(Long id, AiNodeHistoryQueryDTO queryDTO);

    /**
     * 获取变更历史详情
     *
     * @param historyId 历史记录ID
     * @return 变更历史详情
     */
    AiNodeConfigHistoryVO getHistoryDetail(Long historyId);

    /**
     * 回滚到指定历史版本
     *
     * @param id        节点配置主键ID
     * @param historyId 目标历史记录ID
     */
    void rollback(Long id, Long historyId);
}