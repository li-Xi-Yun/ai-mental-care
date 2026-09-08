package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.vo.admin.config.AiNodeCacheVO;

/**
 * 管理员AI节点配置缓存服务接口
 *
 * @author lixiyun
 * @since 2026-09-07
 */
public interface AdminAiNodeCacheService {

    /**
     * 获取缓存状态
     *
     * @return 缓存状态信息
     */
    AiNodeCacheVO getCacheStatus();

    /**
     * 全量刷新缓存
     */
    void refreshAll();

    /**
     * 刷新单个节点缓存
     *
     * @param id 节点配置主键ID
     */
    void refresh(Long id);
}