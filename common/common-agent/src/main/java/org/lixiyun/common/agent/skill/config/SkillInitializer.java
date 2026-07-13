package org.lixiyun.common.agent.skill.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.skill.utils.SkillUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Skill全局影子表初始化器
 * <p>
 * 项目启动时自动执行，判断Redis中是否已存在全局影子表，
 * 若不存在则触发全局影子表的构建和持久化。
 * 确保服务启动后即可正常使用Skill相关功能。
 * </p>
 *
 * <h3>初始化流程（符合图片要求）：</h3>
 * <pre>{@code
 * 项目初始化
 *     ↓
 * 判断缓存是否存在全局影子表
 *     ↓
 * ├─ 存在 → 跳过（直接使用已有数据）
 * └─ 不存在 → 全局影子表加载
 *              ├─ 争抢分布式锁
 *              ├─ 深度遍历Skill文件树
 *              ├─ 构建全局影子表
 *              └─ 持久化到Redis
 * }</pre>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li><b>首次部署</b>：服务首次启动时自动构建全局影子表</li>
 *   <li><b>重启恢复</b>：Redis数据丢失后重启可自动重建</li>
 *   <li><b>多实例部署</b>：通过分布式锁确保只有一个实例执行构建</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "skill.auto-init.enabled", havingValue = "true", matchIfMissing = true)
public class SkillInitializer {

    /**
     * 项目初始化时加载全局影子表
     * <p>
     * 使用 {@link PostConstruct} 注解确保在Spring容器完成依赖注入后立即执行。
     * 该方法会检查Redis中是否已存在全局影子表：
     * <ul>
     *   <li>如果已存在 → 直接跳过，避免重复构建</li>
     *   <li>如果不存在 → 触发完整的构建流程（分布式锁+深度遍历+Redis持久化）</li>
     * </ul>
     * </p>
     *
     * @see SkillUtil#loadGlobalShadowTable() 核心加载逻辑
     */
    @PostConstruct
    public void init() {
        log.info("========== 开始Skill全局影子表初始化 ==========");
        long startTime = System.currentTimeMillis();

        try {
            boolean isEmpty = SkillUtil.isGlobalShadowTableEmpty();

            if (isEmpty) {
                log.info("【Skill项目初始化】Redis中不存在全局影子表，开始构建...");
                SkillUtil.loadGlobalShadowTable();
                log.info("【Skill项目初始化】全局影子表构建完成");
            } else {
                log.info("【Skill项目初始化】Redis中已存在全局影子表，跳过构建");
            }

            boolean success = !SkillUtil.isGlobalShadowTableEmpty();
            if (success) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("========== Skill全局影子表初始化成功，耗时：{}ms ==========", duration);
            } else {
                log.error("========== Skill全局影子表初始化失败 ==========");
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("========== Skill全局影子表初始化异常，耗时：{}ms ==========", duration, e);
        }
    }
}