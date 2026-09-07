package org.lixiyun.server.infrastructure.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.mapper.AiNodeConfigMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * AI节点配置缓存管理器
 * <p>启动时全量预热到本地内存，请求时直接读内存（零延迟），前端修改后主动刷新缓存</p>
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNodeConfigManager {

    private final AiNodeConfigMapper aiNodeConfigMapper;

    private final ConcurrentMap<String, AiNodeConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 应用启动后全量预热加载
     */
    @PostConstruct
    public void init() {
        log.info("AI节点配置缓存预热开始");
        refreshAll();
        log.info("AI节点配置缓存预热完成，加载节点数：{}", configCache.size());
    }

    /**
     * 根据节点唯一标识获取配置
     * <p>优先从本地内存读取，缓存未命中时回源查库并回填缓存</p>
     *
     * @param nodeKey 节点唯一标识，如psychologicalState/riskAssessment
     * @return 节点配置，不存在时返回{@code null}
     */
    public AiNodeConfig getConfig(String nodeKey) {
        AiNodeConfig config = configCache.get(nodeKey);
        if (config != null) {
            return config;
        }

        log.warn("AI节点配置未命中缓存，nodeKey：{}，回源加载", nodeKey);
        config = loadFromDb(nodeKey);
        if (config != null) {
            configCache.put(nodeKey, config);
        }
        return config;
    }

    /**
     * 获取全部节点配置（从缓存读取）
     *
     * @return 全部节点配置列表
     */
    public List<AiNodeConfig> getAllConfigs() {
        return List.copyOf(configCache.values());
    }

    /**
     * 刷新单个节点的缓存
     * <p>前端修改配置后调用，从数据库重新加载并覆盖缓存</p>
     *
     * @param nodeKey 节点唯一标识
     */
    public void refresh(String nodeKey) {
        AiNodeConfig config = loadFromDb(nodeKey);
        if (config != null) {
            configCache.put(nodeKey, config);
            log.info("AI节点配置缓存刷新成功，nodeKey：{}，version：{}", nodeKey, config.getVersion());
        } else {
            configCache.remove(nodeKey);
            log.warn("AI节点配置缓存刷新失败，数据库中无数据，已移除缓存，nodeKey：{}", nodeKey);
        }
    }

    /**
     * 全量刷新缓存
     * <p>批量导入或启动预热时调用，清空后重新加载全部配置</p>
     */
    public void refreshAll() {
        List<AiNodeConfig> allConfigs = aiNodeConfigMapper.selectList(
                new LambdaQueryWrapper<AiNodeConfig>()
                        .eq(AiNodeConfig::getEnabled, AiNodeConfig.ENABLED_YES)
        );

        configCache.clear();
        allConfigs.forEach(c -> configCache.put(c.getNodeKey(), c));

        log.debug("AI节点配置全量刷新完成，节点数：{}", configCache.size());
    }

    /**
     * 从数据库加载单个节点配置
     *
     * @param nodeKey 节点唯一标识
     * @return 节点配置，不存在时返回{@code null}
     */
    private AiNodeConfig loadFromDb(String nodeKey) {
        return aiNodeConfigMapper.selectOne(
                new LambdaQueryWrapper<AiNodeConfig>()
                        .eq(AiNodeConfig::getNodeKey, nodeKey)
                        .eq(AiNodeConfig::getEnabled, AiNodeConfig.ENABLED_YES)
        );
    }
}