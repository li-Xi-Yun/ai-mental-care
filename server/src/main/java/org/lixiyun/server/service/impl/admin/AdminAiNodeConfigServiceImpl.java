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
import org.lixiyun.pojo.dto.admin.config.AdminAiNodeConfigQueryDTO;
import org.lixiyun.pojo.dto.admin.config.AiNodeConfigBatchEnabledDTO;
import org.lixiyun.pojo.dto.admin.config.AiNodeConfigDTO;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.config.AiNodeConfigHistory;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigSimpleVO;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigVO;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.mapper.AiNodeConfigHistoryMapper;
import org.lixiyun.server.mapper.AiNodeConfigMapper;
import org.lixiyun.server.service.admin.AdminAiNodeConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 管理员AI节点配置服务实现类
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiNodeConfigServiceImpl implements AdminAiNodeConfigService {

    private final AiNodeConfigMapper aiNodeConfigMapper;
    private final AiNodeConfigHistoryMapper aiNodeConfigHistoryMapper;
    private final AiNodeConfigManager aiNodeConfigManager;

    @Override
    public PageResult<AiNodeConfigSimpleVO> pageAiNodeConfig(AdminAiNodeConfigQueryDTO queryDTO) {
        String nodeKey = queryDTO.getNodeKey();
        String nodeName = queryDTO.getNodeName();
        String nodeGroup = queryDTO.getNodeGroup();
        Integer modelType = queryDTO.getModelType();
        Integer enabled = queryDTO.getEnabled();
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();

        log.info("AI节点配置-分页查询，nodeKey：{}，nodeName：{}，nodeGroup：{}，modelType：{}，enabled：{}",
                nodeKey, nodeName, nodeGroup, modelType, enabled);

        Page<AiNodeConfig> page = new PageQuery(pageSize, pageNum).build();
        Page<AiNodeConfig> result = aiNodeConfigMapper.selectPage(page, new LambdaQueryWrapper<AiNodeConfig>()
                .like(nodeKey != null, AiNodeConfig::getNodeKey, nodeKey)
                .like(nodeName != null, AiNodeConfig::getNodeName, nodeName)
                .eq(nodeGroup != null, AiNodeConfig::getNodeGroup, nodeGroup)
                .eq(modelType != null, AiNodeConfig::getModelType, modelType)
                .eq(enabled != null, AiNodeConfig::getEnabled, enabled)
                .orderByAsc(AiNodeConfig::getSort)
                .orderByDesc(AiNodeConfig::getUpdatedTime)
        );

        log.info("AI节点配置-分页查询完成，总数：{}", result.getTotal());
        return PageResult.convert(result, AiNodeConfigSimpleVO.class);
    }

    @Override
    public AiNodeConfigVO getAiNodeConfigDetail(Long id) {
        log.info("AI节点配置-获取详情，id：{}", id);

        AiNodeConfig config = aiNodeConfigMapper.selectById(id);
        if (config == null) {
            log.error("AI节点配置-记录不存在，id：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_NOT_FOUND);
        }

        log.debug("AI节点配置-获取详情成功，nodeKey：{}，version：{}", config.getNodeKey(), config.getVersion());
        return BeanUtil.copyProperties(config, AiNodeConfigVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAiNodeConfig(AiNodeConfigDTO dto) {
        String nodeKey = dto.getNodeKey();
        log.info("AI节点配置-新增，nodeKey：{}，nodeName：{}", nodeKey, dto.getNodeName());

        Long count = aiNodeConfigMapper.selectCount(new LambdaQueryWrapper<AiNodeConfig>()
                .eq(AiNodeConfig::getNodeKey, nodeKey)
        );
        if (count > 0) {
            log.error("AI节点配置-节点唯一标识已存在，nodeKey：{}", nodeKey);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_NODE_KEY_EXISTS);
        }

        AiNodeConfig config = BeanUtil.copyProperties(dto, AiNodeConfig.class);

        int insert = aiNodeConfigMapper.insert(config);
        if (insert == 0) {
            log.error("AI节点配置-新增失败，nodeKey：{}", nodeKey);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_ADD_FAIL);
        }

        aiNodeConfigManager.refresh(nodeKey);
        log.info("AI节点配置-新增成功，id：{}，nodeKey：{}", config.getId(), nodeKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAiNodeConfig(Long id, AiNodeConfigDTO dto) {
        log.info("AI节点配置-修改，id：{}", id);

        AiNodeConfig oldConfig = aiNodeConfigMapper.selectById(id);
        if (oldConfig == null) {
            log.error("AI节点配置-记录不存在，id：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_NOT_FOUND);
        }

        log.debug("AI节点配置-修改前，nodeKey：{}，version：{}", oldConfig.getNodeKey(), oldConfig.getVersion());

        AiNodeConfig newConfig = BeanUtil.copyProperties(dto, AiNodeConfig.class);
        newConfig.setId(id);
        newConfig.setVersion(oldConfig.getVersion() + 1);

        int row = aiNodeConfigMapper.updateById(newConfig);
        if (row == 0) {
            log.error("AI节点配置-更新失败（版本冲突），id：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_VERSION_CONFLICT);
        }

        recordHistory(oldConfig, dto, "修改节点配置");
        aiNodeConfigManager.refresh(oldConfig.getNodeKey());
        log.info("AI节点配置-修改成功，id：{}，nodeKey：{}", id, oldConfig.getNodeKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAiNodeConfig(Long id) {
        log.info("AI节点配置-删除，id：{}", id);

        AiNodeConfig config = aiNodeConfigMapper.selectById(id);
        if (config == null) {
            log.error("AI节点配置-记录不存在，id：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_NOT_FOUND);
        }

        int row = aiNodeConfigMapper.deleteById(id);
        if (row == 0) {
            log.error("AI节点配置-删除失败，id：{}", id);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_DELETE_FAIL);
        }

        aiNodeConfigManager.refresh(config.getNodeKey());
        log.info("AI节点配置-删除成功，id：{}，nodeKey：{}", id, config.getNodeKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateEnabled(AiNodeConfigBatchEnabledDTO dto) {
        List<String> nodeKeys = dto.getNodeKeys();
        Integer enabled = dto.getEnabled();
        log.info("AI节点配置-批量启用/禁用，nodeKeys：{}，enabled：{}", nodeKeys, enabled);

        List<AiNodeConfig> configs = aiNodeConfigMapper.selectList(new LambdaQueryWrapper<AiNodeConfig>()
                .in(AiNodeConfig::getNodeKey, nodeKeys)
        );

        if (configs.isEmpty()) {
            log.error("AI节点配置-批量启用/禁用失败，未找到匹配记录，nodeKeys：{}", nodeKeys);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_BATCH_ENABLED_FAIL);
        }

        List<Long> ids = configs.stream().map(AiNodeConfig::getId).toList();
        int row = aiNodeConfigMapper.update(null, new LambdaUpdateWrapper<AiNodeConfig>()
                .in(AiNodeConfig::getId, ids)
                .set(AiNodeConfig::getEnabled, enabled)
        );

        if (row == 0) {
            log.error("AI节点配置-批量启用/禁用失败，nodeKeys：{}", nodeKeys);
            throw new BusinessException(AiNodeConfigExceptionEnum.AI_NODE_CONFIG_BATCH_ENABLED_FAIL);
        }

        configs.forEach(c -> aiNodeConfigManager.refresh(c.getNodeKey()));
        log.info("AI节点配置-批量启用/禁用成功，更新数量：{}", row);
    }

    @Override
    public List<String> listNodeKeys() {
        log.info("AI节点配置-获取全部节点唯一标识列表");

        List<AiNodeConfig> configs = aiNodeConfigMapper.selectList(new LambdaQueryWrapper<AiNodeConfig>()
                .select(AiNodeConfig::getNodeKey)
                .orderByAsc(AiNodeConfig::getSort)
        );

        List<String> nodeKeys = configs.stream().map(AiNodeConfig::getNodeKey).toList();
        log.info("AI节点配置-获取全部节点唯一标识列表完成，数量：{}", nodeKeys.size());
        return nodeKeys;
    }

    /**
     * 记录配置变更历史
     *
     * @param oldConfig 修改前的配置
     * @param dto       修改后的配置DTO
     * @param summary   变更摘要
     */
    private void recordHistory(AiNodeConfig oldConfig, AiNodeConfigDTO dto, String summary) {
        log.debug("AI节点配置-记录变更历史，configId：{}，summary：{}", oldConfig.getId(), summary);

        Map<String, Object> oldParams = buildParamsSnapshot(oldConfig);
        Map<String, Object> newParams = buildParamsSnapshotFromDto(dto);

        AiNodeConfigHistory history = AiNodeConfigHistory.builder()
                .configId(oldConfig.getId())
                .nodeKey(oldConfig.getNodeKey())
                .oldSystemPrompt(oldConfig.getSystemPrompt())
                .newSystemPrompt(dto.getSystemPrompt())
                .oldModelType(oldConfig.getModelType())
                .newModelType(dto.getModelType())
                .oldParams(oldParams)
                .newParams(newParams)
                .oldVersion(oldConfig.getVersion())
                .newVersion(oldConfig.getVersion() + 1)
                .changeSummary(summary)
                .build();

        aiNodeConfigHistoryMapper.insert(history);
        log.debug("AI节点配置-变更历史记录成功，historyId：{}", history.getId());
    }

    /**
     * 从实体构建推理参数快照
     */
    private Map<String, Object> buildParamsSnapshot(AiNodeConfig config) {
        return Map.ofEntries(
                Map.entry("maxToken", config.getMaxToken()),
                Map.entry("temperature", config.getTemperature()),
                Map.entry("topP", config.getTopP()),
                Map.entry("topK", config.getTopK() != null ? config.getTopK() : "null"),
                Map.entry("frequencyPenalty", config.getFrequencyPenalty() != null ? config.getFrequencyPenalty() : "null"),
                Map.entry("presencePenalty", config.getPresencePenalty() != null ? config.getPresencePenalty() : "null"),
                Map.entry("repeatPenalty", config.getRepeatPenalty() != null ? config.getRepeatPenalty() : "null"),
                Map.entry("seed", config.getSeed() != null ? config.getSeed() : "null"),
                Map.entry("retryMaxAttempts", config.getRetryMaxAttempts()),
                Map.entry("retryDelay", config.getRetryDelay()),
                Map.entry("retryMultiplier", config.getRetryMultiplier())
        );
    }

    /**
     * 从DTO构建推理参数快照
     */
    private Map<String, Object> buildParamsSnapshotFromDto(AiNodeConfigDTO dto) {
        return Map.ofEntries(
                Map.entry("maxToken", dto.getMaxToken()),
                Map.entry("temperature", dto.getTemperature()),
                Map.entry("topP", dto.getTopP()),
                Map.entry("topK", dto.getTopK() != null ? dto.getTopK() : "null"),
                Map.entry("frequencyPenalty", dto.getFrequencyPenalty() != null ? dto.getFrequencyPenalty() : "null"),
                Map.entry("presencePenalty", dto.getPresencePenalty() != null ? dto.getPresencePenalty() : "null"),
                Map.entry("repeatPenalty", dto.getRepeatPenalty() != null ? dto.getRepeatPenalty() : "null"),
                Map.entry("seed", dto.getSeed() != null ? dto.getSeed() : "null"),
                Map.entry("retryMaxAttempts", dto.getRetryMaxAttempts()),
                Map.entry("retryDelay", dto.getRetryDelay()),
                Map.entry("retryMultiplier", dto.getRetryMultiplier())
        );
    }
}