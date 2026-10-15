package org.lixiyun.server.infrastructure.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.quota.QuotaConfig;
import org.lixiyun.server.mapper.QuotaConfigMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * AI对话配额-全局配置缓存管理器
 * <p>启动时全量预热到本地内存，请求时直接读内存（零延迟），管理员修改配置后主动刷新缓存。
 * {@link #getConfig(Integer)} 对未配置资源返回 {@code null}，由 {@code tryConsume} 走「放行兜底」。</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaConfigManager {

    private final QuotaConfigMapper quotaConfigMapper;

    private final ConcurrentMap<Integer, QuotaConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 应用启动后全量预热加载
     */
    @PostConstruct
    public void init() {
        log.info("[配额配置缓存管理器] 预热开始");
        refreshAll();
        log.info("[配额配置缓存管理器] 预热完成，加载资源类型数：{}，resourceType：{}", configCache.size(), configCache.keySet());
    }

    /**
     * 根据资源类型获取配置（仅读缓存，不触发数据库加载）
     * <p>适用于高频调用场景，缓存未命中返回 {@code null}，由调用方按「未配置=放行兜底」处理</p>
     *
     * @param resourceType 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D
     * @return 全局配置，不存在时返回 {@code null}
     */
    public QuotaConfig getConfig(Integer resourceType) {
        QuotaConfig config = configCache.get(resourceType);
        if (config != null) {
            log.debug("[配额配置缓存管理器] 配置命中缓存，resourceType：{}", resourceType);
        } else {
            log.debug("[配额配置缓存管理器] 配置未命中缓存，resourceType：{}", resourceType);
        }
        return config;
    }

    /**
     * 获取全部资源类型配置（从缓存读取）
     *
     * @return 全部配置列表
     */
    public List<QuotaConfig> getAllConfigs() {
        return List.copyOf(configCache.values());
    }

    /**
     * 刷新单个资源类型的配置缓存
     * <p>管理员修改配置后调用，从数据库重新加载并覆盖缓存</p>
     *
     * @param resourceType 资源类型
     */
    public void refresh(Integer resourceType) {
        QuotaConfig config = loadFromDb(resourceType);
        if (config != null) {
            configCache.put(resourceType, config);
            log.info("[配额配置缓存管理器] 配置刷新成功，resourceType：{}", resourceType);
        } else {
            configCache.remove(resourceType);
            log.warn("[配额配置缓存管理器] 配置刷新失败，数据库中无数据，已移除缓存，resourceType：{}", resourceType);
        }
    }

    /**
     * 全量刷新缓存
     * <p>启动预热或管理员批量变更时调用，清空后重新加载全部配置</p>
     */
    public void refreshAll() {
        List<QuotaConfig> allConfigs = quotaConfigMapper.selectList(null);
        configCache.clear();
        allConfigs.forEach(c -> configCache.put(c.getResourceType(), c));
        log.debug("[配额配置缓存管理器] 全量刷新完成，资源类型数：{}", configCache.size());
    }

    /**
     * 从数据库加载单个资源类型的配置
     *
     * @param resourceType 资源类型
     * @return 全局配置，不存在时返回 {@code null}
     */
    private QuotaConfig loadFromDb(Integer resourceType) {
        return quotaConfigMapper.selectOne(
                new LambdaQueryWrapper<QuotaConfig>()
                        .eq(QuotaConfig::getResourceType, resourceType)
        );
    }
}