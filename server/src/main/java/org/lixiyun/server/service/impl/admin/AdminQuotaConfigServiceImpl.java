package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.QuotaExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.admin.quota.QuotaConfigUpdateDTO;
import org.lixiyun.pojo.entity.quota.QuotaConfig;
import org.lixiyun.pojo.vo.admin.quota.QuotaConfigVO;
import org.lixiyun.server.infrastructure.ai.QuotaConfigManager;
import org.lixiyun.server.mapper.QuotaConfigMapper;
import org.lixiyun.server.service.admin.AdminQuotaConfigService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 管理员 AI 对话配额全局配置服务实现类
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminQuotaConfigServiceImpl implements AdminQuotaConfigService {

    private final QuotaConfigMapper quotaConfigMapper;
    private final QuotaConfigManager quotaConfigManager;

    @Override
    public List<QuotaConfigVO> listConfig() {
        return quotaConfigManager.getAllConfigs().stream()
                .sorted(Comparator.comparing(QuotaConfig::getResourceType))
                .map(this::buildVO)
                .toList();
    }

    @Override
    public void updateConfig(Integer resourceType, QuotaConfigUpdateDTO dto) {
        log.info("[配额配置] 修改全局配置，resourceType：{}，dto：{}", resourceType, dto);

        QuotaConfig oldConfig = quotaConfigMapper.selectOne(new LambdaQueryWrapper<QuotaConfig>()
                .eq(QuotaConfig::getResourceType, resourceType));
        if (oldConfig == null) {
            log.error("[配额配置] 配置不存在，resourceType：{}", resourceType);
            throw new BusinessException(QuotaExceptionEnum.QUOTA_CONFIG_NOT_FOUND);
        }

        QuotaConfig newConfig = new QuotaConfig();
        BeanUtil.copyProperties(dto, newConfig, CopyOptions.create().setIgnoreNullValue(true));
        newConfig.setId(oldConfig.getId());
        int row = quotaConfigMapper.updateById(newConfig);
        if (row == 0) {
            log.error("[配额配置] 更新失败，resourceType：{}", resourceType);
            throw new BusinessException(QuotaExceptionEnum.QUOTA_UPDATE_FAILED);
        }

        quotaConfigManager.refresh(resourceType);
        log.info("[配额配置] 修改成功，resourceType：{}", resourceType);
    }

    /**
     * 将全局配置实体转换为VO（展示用）
     *
     * @param config 全局配置实体 {@link QuotaConfig}
     * @return 全局配置VO
     */
    private QuotaConfigVO buildVO(QuotaConfig config) {
        return QuotaConfigVO.builder()
                .resourceType(config.getResourceType())
                .resourceName(QuotaConfig.resourceName(config.getResourceType()))
                .defaultLimit(config.getDefaultLimit())
                .available(config.getAvailable())
                .enabled(config.getEnabled())
                .cycleType(config.getCycleType())
                .remark(config.getRemark())
                .build();
    }
}