package org.lixiyun.server.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AiNodeConfigExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.admin.config.AiNodePromptDTO;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.config.AiNodeConfigHistory;
import org.lixiyun.pojo.vo.admin.config.AiNodePromptVO;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.mapper.AiNodeConfigHistoryMapper;
import org.lixiyun.server.mapper.AiNodeConfigMapper;
import org.lixiyun.server.service.admin.AdminAiNodePromptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 管理员AI节点提示词服务实现类
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiNodePromptServiceImpl implements AdminAiNodePromptService {

    private final AiNodeConfigMapper aiNodeConfigMapper;
    private final AiNodeConfigHistoryMapper aiNodeConfigHistoryMapper;
    private final AiNodeConfigManager aiNodeConfigManager;

    @Override
    public AiNodePromptVO getPrompt(Long id) {
        log.info("AI节点提示词-获取，id：{}", id);

        AiNodeConfig config = aiNodeConfigMapper.selectById(id);
        if (config == null) {
            log.error("AI节点提示词-配置不存在，id：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_NOT_FOUND);
        }

        log.debug("AI节点提示词-获取成功，nodeKey：{}，prompt长度：{}", config.getNodeKey(), config.getSystemPrompt().length());
        return AiNodePromptVO.builder()
                .nodeKey(config.getNodeKey())
                .systemPrompt(config.getSystemPrompt())
                .version(config.getVersion())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePrompt(Long id, AiNodePromptDTO dto) {
        String systemPrompt = dto.getSystemPrompt();
        Integer version = dto.getVersion();
        log.info("AI节点提示词-修改，id：{}，version：{}，prompt长度：{}", id, version, systemPrompt.length());

        AiNodeConfig oldConfig = aiNodeConfigMapper.selectById(id);
        if (oldConfig == null) {
            log.error("AI节点提示词-配置不存在，id：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_NOT_FOUND);
        }

        log.debug("AI节点提示词-修改前，nodeKey：{}，oldVersion：{}", oldConfig.getNodeKey(), oldConfig.getVersion());

        int row = aiNodeConfigMapper.update(null, new LambdaUpdateWrapper<AiNodeConfig>()
                .eq(AiNodeConfig::getId, id)
                .eq(AiNodeConfig::getVersion, version)
                .set(AiNodeConfig::getSystemPrompt, systemPrompt)
                .set(AiNodeConfig::getVersion, version + 1)
        );

        if (row == 0) {
            log.error("AI节点提示词-更新失败（版本冲突），id：{}，version：{}", id, version);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_VERSION_CONFLICT);
        }

        recordPromptHistory(oldConfig, systemPrompt, version);
        aiNodeConfigManager.refresh(oldConfig.getNodeKey());
        log.info("AI节点提示词-修改成功，id：{}，nodeKey：{}", id, oldConfig.getNodeKey());
    }

    /**
     * 记录提示词变更历史
     *
     * @param oldConfig    修改前的配置
     * @param newPrompt    修改后的提示词
     * @param oldVersion   修改前的版本号
     */
    private void recordPromptHistory(AiNodeConfig oldConfig, String newPrompt, Integer oldVersion) {
        log.debug("AI节点提示词-记录变更历史，configId：{}", oldConfig.getId());

        Map<String, Object> paramsSnapshot = Map.ofEntries(
                Map.entry("maxToken", oldConfig.getMaxToken()),
                Map.entry("temperature", oldConfig.getTemperature()),
                Map.entry("topP", oldConfig.getTopP()),
                Map.entry("topK", oldConfig.getTopK() != null ? oldConfig.getTopK() : "null"),
                Map.entry("frequencyPenalty", oldConfig.getFrequencyPenalty() != null ? oldConfig.getFrequencyPenalty() : "null"),
                Map.entry("presencePenalty", oldConfig.getPresencePenalty() != null ? oldConfig.getPresencePenalty() : "null"),
                Map.entry("repeatPenalty", oldConfig.getRepeatPenalty() != null ? oldConfig.getRepeatPenalty() : "null"),
                Map.entry("seed", oldConfig.getSeed() != null ? oldConfig.getSeed() : "null"),
                Map.entry("retryMaxAttempts", oldConfig.getRetryMaxAttempts()),
                Map.entry("retryDelay", oldConfig.getRetryDelay()),
                Map.entry("retryMultiplier", oldConfig.getRetryMultiplier())
        );

        AiNodeConfigHistory history = AiNodeConfigHistory.builder()
                .configId(oldConfig.getId())
                .nodeKey(oldConfig.getNodeKey())
                .oldSystemPrompt(oldConfig.getSystemPrompt())
                .newSystemPrompt(newPrompt)
                .oldModelType(oldConfig.getModelType())
                .newModelType(oldConfig.getModelType())
                .oldParams(paramsSnapshot)
                .newParams(paramsSnapshot)
                .oldVersion(oldVersion)
                .newVersion(oldVersion + 1)
                .changeSummary("修改系统提示词")
                .build();

        aiNodeConfigHistoryMapper.insert(history);
        log.debug("AI节点提示词-变更历史记录成功，historyId：{}", history.getId());
    }
}