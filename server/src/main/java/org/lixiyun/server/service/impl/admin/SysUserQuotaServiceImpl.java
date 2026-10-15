package org.lixiyun.server.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.QuotaExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.admin.quota.QuotaUserAdjustDTO;
import org.lixiyun.pojo.entity.quota.QuotaConfig;
import org.lixiyun.pojo.entity.quota.QuotaUsageLog;
import org.lixiyun.pojo.entity.quota.QuotaUser;
import org.lixiyun.pojo.vo.admin.quota.QuotaUserVO;
import org.lixiyun.server.infrastructure.ai.QuotaConfigManager;
import org.lixiyun.server.mapper.QuotaUsageLogMapper;
import org.lixiyun.server.mapper.QuotaUserMapper;
import org.lixiyun.server.service.admin.SysUserQuotaService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员 AI 对话配额用户调整服务实现类
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserQuotaServiceImpl implements SysUserQuotaService {

    private final QuotaUserMapper quotaUserMapper;
    private final QuotaUsageLogMapper quotaUsageLogMapper;
    private final QuotaConfigManager quotaConfigManager;

    @Override
    public List<QuotaUserVO> listUserQuota(Long userId) {
        List<QuotaUser> userQuotas = quotaUserMapper.selectList(new LambdaQueryWrapper<QuotaUser>()
                .eq(QuotaUser::getUserId, userId));
        Map<Integer, QuotaUser> userQuotaMap = userQuotas.stream()
                .filter(item -> item.getResourceType() != null)
                .collect(Collectors.toMap(QuotaUser::getResourceType, Function.identity(), (left, right) -> left));

        return quotaConfigManager.getAllConfigs().stream()
                .sorted(Comparator.comparing(QuotaConfig::getResourceType))
                .map(config -> buildUserQuotaVO(config, userQuotaMap.get(config.getResourceType())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserQuota(Long userId, Integer resourceType, QuotaUserAdjustDTO dto) {
        log.info("[AI配额-用户调整] 开始调整，userId：{}，resourceType：{}，dto：{}", userId, resourceType, dto);

        if (dto.getResourceType() != null && !resourceType.equals(dto.getResourceType())) {
            log.error("[AI配额-用户调整] 路径资源类型与请求体不一致，userId：{}，resourceType：{}，dtoResourceType：{}",
                    userId, resourceType, dto.getResourceType());
            throw new BusinessException(QuotaExceptionEnum.QUOTA_UPDATE_FAILED.getCode(), "资源类型参数不一致");
        }

        QuotaConfig config = quotaConfigManager.getConfig(resourceType);
        if (config == null) {
            log.error("[AI配额-用户调整] 配置不存在，resourceType：{}", resourceType);
            throw new BusinessException(QuotaExceptionEnum.QUOTA_CONFIG_NOT_FOUND);
        }

        QuotaUser oldUser = findUserQuota(userId, resourceType);
        int oldUsed = oldUser == null ? 0 : oldUser.usedCountOrDefault();
        int oldUnlimited = oldUser == null ? QuotaUser.UNLIMITED_NO : oldUser.getUnlimited() == null ? QuotaUser.UNLIMITED_NO : oldUser.getUnlimited();
        int effectiveUnlimited = dto.getUnlimited() == null ? oldUnlimited : dto.getUnlimited();
        Integer effectiveCustomLimit = dto.getCustomLimit();
        int effectiveUsed = dto.getUsed();

        if (effectiveUnlimited == QuotaUser.UNLIMITED_YES && oldUser != null) {
            effectiveCustomLimit = oldUser.getCustomLimit();
            effectiveUsed = oldUsed;
        }
        if (effectiveCustomLimit != null && effectiveUsed > effectiveCustomLimit) {
            log.error("[AI配额-用户调整] 已用次数超过个性化上限，userId：{}，resourceType：{}，used：{}，customLimit：{}",
                    userId, resourceType, effectiveUsed, effectiveCustomLimit);
            throw new BusinessException(QuotaExceptionEnum.QUOTA_USED_EXCEEDS_LIMIT);
        }

        QuotaUser savedUser;
        if (oldUser == null) {
            savedUser = QuotaUser.builder()
                    .userId(userId)
                    .resourceType(resourceType)
                    .customLimit(effectiveCustomLimit)
                    .unlimited(effectiveUnlimited)
                    .usedCount(effectiveUnlimited == QuotaUser.UNLIMITED_YES ? 0 : effectiveUsed)
                    .cycleStartTime(config.isMonthly() ? QuotaConfig.currentPeriodStart() : null)
                    .build();
            try {
                int row = quotaUserMapper.insert(savedUser);
                if (row == 0) {
                    throw new BusinessException(QuotaExceptionEnum.QUOTA_UPDATE_FAILED);
                }
            } catch (DuplicateKeyException e) {
                log.debug("[AI配额-用户调整] 并发插入冲突，改为更新，userId：{}，resourceType：{}", userId, resourceType);
                oldUser = findUserQuota(userId, resourceType);
                if (oldUser == null || updateExistingQuota(oldUser, effectiveCustomLimit, effectiveUnlimited, effectiveUsed) == 0) {
                    throw new BusinessException(QuotaExceptionEnum.QUOTA_UPDATE_FAILED);
                }
                savedUser = findUserQuota(userId, resourceType);
            }
        } else {
            updateExistingQuota(oldUser, effectiveCustomLimit, effectiveUnlimited, effectiveUsed);
            savedUser = findUserQuota(userId, resourceType);
        }

        recordAdminAdjust(userId, resourceType, oldUsed,
                savedUser == null ? effectiveUsed : savedUser.usedCountOrDefault(),
                oldUnlimited, effectiveUnlimited);
        log.info("[AI配额-用户调整] 调整成功，userId：{}，resourceType：{}", userId, resourceType);
    }

    /**
     * 更新已有用户配额（使用显式 SET 允许 custom_limit 置 NULL，清除个性化上限）
     *
     * @param oldUser   已存在的用户配额行
     * @param customLimit 个性化上限，NULL=清除个性化
     * @param unlimited 不限量豁免
     * @param used      目标已用次数
     * @return 影响行数
     */
    private int updateExistingQuota(QuotaUser oldUser, Integer customLimit, Integer unlimited, Integer used) {
        LambdaUpdateWrapper<QuotaUser> wrapper = new LambdaUpdateWrapper<QuotaUser>()
                .eq(QuotaUser::getId, oldUser.getId())
                .set(QuotaUser::getCustomLimit, customLimit)
                .set(QuotaUser::getUnlimited, unlimited)
                .set(QuotaUser::getUsedCount, unlimited == QuotaUser.UNLIMITED_YES ? oldUser.getUsedCount() : used)
                .set(QuotaUser::getUpdatedBy, UserInfoThreadLocalUtil.getCurrentId().orElse(null));
        return quotaUserMapper.update(null, wrapper);
    }

    /**
     * 查询用户指定资源的配额行
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
     * 按全局配置基底合并用户配额行，构建用户配额VO
     *
     * @param config 全局配置 {@link QuotaConfig}
     * @param user   用户配额行 {@link QuotaUser}，可空
     * @return 用户配额VO
     */
    private QuotaUserVO buildUserQuotaVO(QuotaConfig config, QuotaUser user) {
        int usedCount = user == null ? 0 : user.usedCountOrDefault();
        int unlimited = user == null ? QuotaUser.UNLIMITED_NO : user.getUnlimited() == null ? QuotaUser.UNLIMITED_NO : user.getUnlimited();
        Integer customLimit = user == null ? null : user.getCustomLimit();
        boolean available = config.isAvailable();
        boolean enabled = config.isEnabled();
        Integer effectiveLimit = null;
        Integer remaining = null;

        if (unlimited != QuotaUser.UNLIMITED_YES && available && enabled) {
            effectiveLimit = customLimit == null ? config.getDefaultLimit() : customLimit;
            if (effectiveLimit != null) {
                remaining = Math.max(effectiveLimit - usedCount, 0);
            }
        }

        return QuotaUserVO.builder()
                .resourceType(config.getResourceType())
                .resourceName(QuotaConfig.resourceName(config.getResourceType()))
                .customLimit(customLimit)
                .unlimited(unlimited)
                .available(available ? QuotaConfig.AVAILABLE_YES : QuotaConfig.AVAILABLE_NO)
                .usedCount(usedCount)
                .effectiveLimit(effectiveLimit)
                .remaining(remaining)
                .build();
    }

    /**
     * 记录管理员调整审计明细（best-effort，失败仅告警不影响主流程）
     *
     * @param userId          用户ID
     * @param resourceType    资源类型
     * @param before          调整前已用次数
     * @param after           调整后已用次数
     * @param beforeUnlimited 调整前不限量状态
     * @param afterUnlimited  调整后不限量状态
     */
    private void recordAdminAdjust(Long userId, Integer resourceType, int before, int after,
                                   int beforeUnlimited, int afterUnlimited) {
        try {
            quotaUsageLogMapper.insert(QuotaUsageLog.builder()
                    .userId(userId)
                    .resourceType(resourceType)
                    .action(QuotaUsageLog.ACTION_ADMIN_ADJUST)
                    .delta(after - before)
                    .beforeUsed(before)
                    .afterUsed(after)
                    .operatorId(UserInfoThreadLocalUtil.getCurrentId().orElse(null))
                    .remark("管理员调整配额，不限量状态：" + beforeUnlimited + "→" + afterUnlimited)
                    .build());
        } catch (Exception e) {
            log.warn("[AI配额-审计] ADMIN_ADJUST 明细写入失败（best-effort），userId：{}，resourceType：{}，原因：{}",
                    userId, resourceType, e.getMessage());
        }
    }
}