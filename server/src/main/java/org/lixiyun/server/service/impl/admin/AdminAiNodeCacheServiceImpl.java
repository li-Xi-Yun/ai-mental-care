package org.lixiyun.server.service.impl.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AiNodeConfigExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.vo.admin.config.AiNodeCacheVO;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.mapper.AiNodeConfigMapper;
import org.lixiyun.server.service.admin.AdminAiNodeCacheService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理员AI节点配置缓存服务实现类
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiNodeCacheServiceImpl implements AdminAiNodeCacheService {

    private final AiNodeConfigMapper aiNodeConfigMapper;
    private final AiNodeConfigManager aiNodeConfigManager;

    @Override
    public AiNodeCacheVO getCacheStatus() {
        log.info("AI节点配置缓存-获取状态");

        List<AiNodeConfig> allConfigs = aiNodeConfigManager.getAllConfigs();
        List<String> nodeKeys = allConfigs.stream().map(AiNodeConfig::getNodeKey).toList();

        log.debug("AI节点配置缓存-当前缓存节点：{}", nodeKeys);
        return AiNodeCacheVO.builder()
                .cacheSize(nodeKeys.size())
                .nodeKeys(nodeKeys)
                .build();
    }

    @Override
    public void refreshAll() {
        log.info("AI节点配置缓存-全量刷新");

        aiNodeConfigManager.refreshAll();

        List<AiNodeConfig> allConfigs = aiNodeConfigManager.getAllConfigs();
        log.info("AI节点配置缓存-全量刷新完成，缓存节点数：{}", allConfigs.size());
    }

    @Override
    public void refresh(Long id) {
        log.info("AI节点配置缓存-刷新单个节点，id：{}", id);

        AiNodeConfig config = aiNodeConfigMapper.selectById(id);
        if (config == null) {
            log.error("AI节点配置缓存-配置不存在，id：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_NOT_FOUND);
        }

        aiNodeConfigManager.refresh(config.getNodeKey());
        log.info("AI节点配置缓存-刷新单个节点成功，id：{}，nodeKey：{}", id, config.getNodeKey());
    }
}