package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AiNodeConfigExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.config.AiNodeHistoryQueryDTO;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.config.AiNodeConfigHistory;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigHistoryVO;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.mapper.AiNodeConfigHistoryMapper;
import org.lixiyun.server.mapper.AiNodeConfigMapper;
import org.lixiyun.server.service.admin.AdminAiNodeHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 管理员AI节点配置变更历史服务实现类
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiNodeHistoryServiceImpl implements AdminAiNodeHistoryService {

    private final AiNodeConfigMapper aiNodeConfigMapper;
    private final AiNodeConfigHistoryMapper aiNodeConfigHistoryMapper;
    private final AiNodeConfigManager aiNodeConfigManager;

    @Override
    public PageResult<AiNodeConfigHistoryVO> pageHistory(Long id, AiNodeHistoryQueryDTO queryDTO) {
        String nodeKey = queryDTO.getNodeKey();
        String changeSummary = queryDTO.getChangeSummary();
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();

        log.info("AI节点配置变更历史-分页查询，configId：{}，nodeKey：{}，changeSummary：{}", id, nodeKey, changeSummary);

        Page<AiNodeConfigHistory> page = new PageQuery(pageSize, pageNum).build();
        Page<AiNodeConfigHistory> result = aiNodeConfigHistoryMapper.selectPage(page, new LambdaQueryWrapper<AiNodeConfigHistory>()
                .eq(id != null, AiNodeConfigHistory::getConfigId, id)
                .like(nodeKey != null, AiNodeConfigHistory::getNodeKey, nodeKey)
                .like(changeSummary != null, AiNodeConfigHistory::getChangeSummary, changeSummary)
                .orderByDesc(AiNodeConfigHistory::getCreatedTime)
        );

        log.info("AI节点配置变更历史-分页查询完成，总数：{}", result.getTotal());
        return PageResult.convert(result, AiNodeConfigHistoryVO.class);
    }

    @Override
    public AiNodeConfigHistoryVO getHistoryDetail(Long historyId) {
        log.info("AI节点配置变更历史-获取详情，historyId：{}", historyId);

        AiNodeConfigHistory history = aiNodeConfigHistoryMapper.selectById(historyId);
        if (history == null) {
            log.error("AI节点配置变更历史-记录不存在，historyId：{}", historyId);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_HISTORY_NOT_FOUND);
        }

        log.debug("AI节点配置变更历史-获取详情成功，nodeKey：{}，oldVersion：{}，newVersion：{}",
                history.getNodeKey(), history.getOldVersion(), history.getNewVersion());
        return BeanUtil.copyProperties(history, AiNodeConfigHistoryVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, Long historyId) {
        log.info("AI节点配置变更历史-回滚，configId：{}，historyId：{}", id, historyId);

        AiNodeConfig currentConfig = aiNodeConfigMapper.selectById(id);
        if (currentConfig == null) {
            log.error("AI节点配置变更历史-配置不存在，configId：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_NOT_FOUND);
        }

        AiNodeConfigHistory history = aiNodeConfigHistoryMapper.selectById(historyId);
        if (history == null) {
            log.error("AI节点配置变更历史-历史记录不存在，historyId：{}", historyId);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_HISTORY_NOT_FOUND);
        }

        log.debug("AI节点配置变更历史-回滚前，nodeKey：{}，当前version：{}，目标oldVersion：{}",
                currentConfig.getNodeKey(), currentConfig.getVersion(), history.getOldVersion());

        int row = aiNodeConfigMapper.update(null, new LambdaUpdateWrapper<AiNodeConfig>()
                .eq(AiNodeConfig::getId, id)
                .set(AiNodeConfig::getSystemPrompt, history.getOldSystemPrompt())
                .set(AiNodeConfig::getModelType, history.getOldModelType())
                .set(AiNodeConfig::getVersion, currentConfig.getVersion() + 1)
        );

        if (row == 0) {
            log.error("AI节点配置变更历史-回滚失败，configId：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_ROLLBACK_FAIL);
        }

        recordRollbackHistory(currentConfig, history);
        aiNodeConfigManager.refresh(currentConfig.getNodeKey());
        log.info("AI节点配置变更历史-回滚成功，configId：{}，nodeKey：{}", id, currentConfig.getNodeKey());
    }

    /**
     * 记录回滚变更历史
     *
     * @param currentConfig 当前配置
     * @param history       目标回滚的历史记录
     */
    private void recordRollbackHistory(AiNodeConfig currentConfig, AiNodeConfigHistory history) {
        log.debug("AI节点配置变更历史-记录回滚历史，configId：{}", currentConfig.getId());

        Map<String, Object> currentParams = Map.ofEntries(
                Map.entry("maxToken", currentConfig.getMaxToken()),
                Map.entry("temperature", currentConfig.getTemperature()),
                Map.entry("topP", currentConfig.getTopP()),
                Map.entry("topK", currentConfig.getTopK() != null ? currentConfig.getTopK() : "null"),
                Map.entry("frequencyPenalty", currentConfig.getFrequencyPenalty() != null ? currentConfig.getFrequencyPenalty() : "null"),
                Map.entry("presencePenalty", currentConfig.getPresencePenalty() != null ? currentConfig.getPresencePenalty() : "null"),
                Map.entry("responseFormat", currentConfig.getResponseFormat() != null ? currentConfig.getResponseFormat() : "null"),
                Map.entry("stopSequences", currentConfig.getStopSequences() != null ? currentConfig.getStopSequences() : "null"),
                Map.entry("retryMaxAttempts", currentConfig.getRetryMaxAttempts()),
                Map.entry("retryDelay", currentConfig.getRetryDelay()),
                Map.entry("retryMultiplier", currentConfig.getRetryMultiplier())
        );

        AiNodeConfigHistory rollbackHistory = AiNodeConfigHistory.builder()
                .configId(currentConfig.getId())
                .nodeKey(currentConfig.getNodeKey())
                .oldSystemPrompt(currentConfig.getSystemPrompt())
                .newSystemPrompt(history.getOldSystemPrompt())
                .oldModelType(currentConfig.getModelType())
                .newModelType(history.getOldModelType())
                .oldParams(currentParams)
                .newParams(history.getOldParams())
                .oldVersion(currentConfig.getVersion())
                .newVersion(currentConfig.getVersion() + 1)
                .changeSummary("回滚到版本" + history.getOldVersion())
                .build();

        aiNodeConfigHistoryMapper.insert(rollbackHistory);
        log.debug("AI节点配置变更历史-回滚历史记录成功，historyId：{}", rollbackHistory.getId());
    }
}