package org.lixiyun.server.service.impl.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.QuotaExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.quota.QuotaConfig;
import org.lixiyun.pojo.entity.quota.QuotaUsageLog;
import org.lixiyun.pojo.entity.quota.QuotaUser;
import org.lixiyun.server.infrastructure.ai.QuotaConfigManager;
import org.lixiyun.server.mapper.QuotaUsageLogMapper;
import org.lixiyun.server.mapper.QuotaUserMapper;
import org.lixiyun.server.service.ai.QuotaService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI对话配额消费引擎服务实现类
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final QuotaUserMapper quotaUserMapper;
    private final QuotaUsageLogMapper quotaUsageLogMapper;
    private final QuotaConfigManager quotaConfigManager;

    @Override
    public boolean tryConsume(Long userId, Integer resourceType) {
        log.debug("[AI配额-扣减] 开始扣减，userId：{}，resourceType：{}", userId, resourceType);

        QuotaConfig config = quotaConfigManager.getConfig(resourceType);
        if (config == null) {
            log.warn("[AI配额-扣减] 资源未配置，放行兜底，userId：{}，resourceType：{}", userId, resourceType);
            return true;
        }

        if (config.isDisabled()) {
            log.warn("[AI配额-扣减] 资源已停用，userId：{}，resourceType：{}", userId, resourceType);
            throw new BusinessException(QuotaExceptionEnum.RESOURCE_DISABLED.getCode(),
                    "「" + QuotaConfig.resourceName(resourceType) + "」已停用，请联系管理员");
        }
        if (!config.isEnabled()) {
            log.debug("[AI配额-扣减] 限流已关闭，直接放行，userId：{}，resourceType：{}", userId, resourceType);
            return true;
        }

        int rows = consumeUpdate(userId, resourceType, config);
        if (rows == 1) {
            log.info("[AI配额-扣减] 扣减成功，userId：{}，resourceType：{}", userId, resourceType);
            recordConsume(userId, resourceType, 1);
            return true;
        }

        QuotaUser quotaUser = findUserQuota(userId, resourceType);
        if (quotaUser == null) {
            insertLazyQuota(userId, resourceType, config);
            rows = consumeUpdate(userId, resourceType, config);
            if (rows == 1) {
                log.info("[AI配额-扣减] 懒创建后扣减成功，userId：{}，resourceType：{}", userId, resourceType);
                recordConsume(userId, resourceType, 1);
                return true;
            }
        } else if (quotaUser.isUnlimited()) {
            log.info("[AI配额-扣减] 用户豁免放行，不计数不写明细，userId：{}，resourceType：{}", userId, resourceType);
            return true;
        }

        log.info("[AI配额-扣减] 超限被拒，userId：{}，resourceType：{}", userId, resourceType);
        recordConsumeRejected(userId, resourceType);
        return false;
    }

    @Override
    public void rollbackConsume(Long userId, Integer resourceType) {
        log.info("[AI配额-回滚] 开始回滚，userId：{}，resourceType：{}", userId, resourceType);

        LambdaUpdateWrapper<QuotaUser> wrapper = new LambdaUpdateWrapper<QuotaUser>()
                .eq(QuotaUser::getUserId, userId)
                .eq(QuotaUser::getResourceType, resourceType)
                .gt(QuotaUser::getUsedCount, 0)
                .setSql("used_count = used_count - 1");

        QuotaConfig config = quotaConfigManager.getConfig(resourceType);
        if (config != null && config.isMonthly()) {
            wrapper.ge(QuotaUser::getCycleStartTime, QuotaConfig.currentPeriodStart());
        }

        int rows = quotaUserMapper.update(null, wrapper);
        if (rows == 1) {
            recordRollback(userId, resourceType);
            log.info("[AI配额-回滚] 回滚成功，userId：{}，resourceType：{}", userId, resourceType);
        } else {
            log.debug("[AI配额-回滚] 无需回滚，userId：{}，resourceType：{}", userId, resourceType);
        }
    }

    @Override
    public String getRemainingDesc(Integer resourceType) {
        return QuotaConfig.resourceName(resourceType) + "次数已达上限，如需继续使用请联系管理员";
    }

    /**
     * 执行一次条件扣减。条件 UPDATE 使用数据库行锁保证并发下只有未超限请求成功。
     *
     * @param userId       用户ID
     * @param resourceType 资源类型
     * @param config       资源全局配置
     * @return 受影响行数，1 表示扣减成功
     */
    private int consumeUpdate(Long userId, Integer resourceType, QuotaConfig config) {
        LambdaUpdateWrapper<QuotaUser> wrapper = new LambdaUpdateWrapper<QuotaUser>()
                .eq(QuotaUser::getUserId, userId)
                .eq(QuotaUser::getResourceType, resourceType)
                .eq(QuotaUser::getUnlimited, QuotaUser.UNLIMITED_NO);

        if (config.isMonthly()) {
            LocalDateTime periodStart = QuotaConfig.currentPeriodStart();
            wrapper.setSql("used_count = IF(cycle_start_time < {0}, 1, used_count + 1)", periodStart)
                    .setSql("cycle_start_time = IF(cycle_start_time < {0}, {1}, cycle_start_time)", periodStart, periodStart)
                    .apply("(cycle_start_time < {0} OR used_count < COALESCE(custom_limit, {1}))",
                            periodStart, config.getDefaultLimit());
        } else {
            wrapper.setSql("used_count = used_count + 1")
                    .apply("used_count < COALESCE(custom_limit, {0})", config.getDefaultLimit());
        }
        return quotaUserMapper.update(null, wrapper);
    }

    /**
     * 懒创建用户配额行，并用唯一键处理并发首用。
     *
     * @param userId       用户ID
     * @param resourceType 资源类型
     * @param config       资源全局配置
     */
    private void insertLazyQuota(Long userId, Integer resourceType, QuotaConfig config) {
        QuotaUser quotaUser = QuotaUser.builder()
                .userId(userId)
                .resourceType(resourceType)
                .unlimited(QuotaUser.UNLIMITED_NO)
                .usedCount(0)
                .cycleStartTime(config.isMonthly() ? QuotaConfig.currentPeriodStart() : null)
                .build();
        try {
            quotaUserMapper.insert(quotaUser);
        } catch (DuplicateKeyException e) {
            log.debug("[AI配额-扣减] 并发懒创建去重，userId：{}，resourceType：{}", userId, resourceType);
        }
    }

    /**
     * 查询用户指定资源的配额行。
     *
     * @param userId       用户ID
     * @param resourceType 资源类型
     * @return 用户配额行，不存在返回 {@code null}
     */
    private QuotaUser findUserQuota(Long userId, Integer resourceType) {
        return quotaUserMapper.selectOne(new LambdaQueryWrapper<QuotaUser>()
                .eq(QuotaUser::getUserId, userId)
                .eq(QuotaUser::getResourceType, resourceType));
    }

    /**
     * 记录消费成功审计明细（best-effort，失败仅告警不影响主流程）。
     *
     * @param userId       用户ID
     * @param resourceType 资源类型
     * @param delta        使用量变化值
     */
    private void recordConsume(Long userId, Integer resourceType, int delta) {
        try {
            QuotaUser current = findUserQuota(userId, resourceType);
            int after = current == null ? 0 : current.usedCountOrDefault();
            quotaUsageLogMapper.insert(QuotaUsageLog.builder()
                    .userId(userId)
                    .resourceType(resourceType)
                    .action(QuotaUsageLog.ACTION_CONSUME)
                    .delta(delta)
                    .beforeUsed(Math.max(after - delta, 0))
                    .afterUsed(after)
                    .build());
        } catch (Exception e) {
            log.warn("[AI配额-审计] CONSUME 明细写入失败（best-effort），userId：{}，resourceType：{}，原因：{}",
                    userId, resourceType, e.getMessage());
        }
    }

    /**
     * 记录消费拒绝审计明细（best-effort，失败仅告警不影响主流程）。
     *
     * @param userId       用户ID
     * @param resourceType 资源类型
     */
    private void recordConsumeRejected(Long userId, Integer resourceType) {
        try {
            QuotaUser current = findUserQuota(userId, resourceType);
            int used = current == null ? 0 : current.usedCountOrDefault();
            quotaUsageLogMapper.insert(QuotaUsageLog.builder()
                    .userId(userId)
                    .resourceType(resourceType)
                    .action(QuotaUsageLog.ACTION_CONSUME)
                    .delta(0)
                    .beforeUsed(used)
                    .afterUsed(used)
                    .remark(QuotaUsageLog.REMARK_QUOTA_REJECTED)
                    .build());
        } catch (Exception e) {
            log.warn("[AI配额-审计] 拒绝明细写入失败（best-effort），userId：{}，resourceType：{}，原因：{}",
                    userId, resourceType, e.getMessage());
        }
    }

    /**
     * 记录消费回滚审计明细（best-effort，失败仅告警不影响主流程）。
     *
     * @param userId       用户ID
     * @param resourceType 资源类型
     */
    private void recordRollback(Long userId, Integer resourceType) {
        try {
            QuotaUser current = findUserQuota(userId, resourceType);
            int after = current == null ? 0 : current.usedCountOrDefault();
            quotaUsageLogMapper.insert(QuotaUsageLog.builder()
                    .userId(userId)
                    .resourceType(resourceType)
                    .action(QuotaUsageLog.ACTION_ROLLBACK)
                    .delta(-1)
                    .beforeUsed(after + 1)
                    .afterUsed(after)
                    .build());
        } catch (Exception e) {
            log.warn("[AI配额-审计] ROLLBACK 明细写入失败（best-effort），userId：{}，resourceType：{}，原因：{}",
                    userId, resourceType, e.getMessage());
        }
    }
}