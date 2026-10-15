package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.config.Live2dConfig;
import org.lixiyun.pojo.vo.user.live2d.Live2dConfigVO;
import org.lixiyun.server.infrastructure.live2d.ModelFileSigner;
import org.lixiyun.server.mapper.Live2dConfigMapper;
import org.lixiyun.server.service.user.Live2dConfigService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户端 Live2D 模型配置服务实现类
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Live2dConfigServiceImpl implements Live2dConfigService {

    private final Live2dConfigMapper live2dConfigMapper;
    private final ModelFileSigner modelFileSigner;

    @Override
    public Live2dConfigVO getEnabledConfig() {
        // 与 AvatarNode.loadAllowList 查询条件完全一致：enabled=1 且 sort 升序取第一条
        Live2dConfig config = live2dConfigMapper.selectOne(new LambdaQueryWrapper<Live2dConfig>()
                .eq(Live2dConfig::getEnabled, Live2dConfig.ENABLED_YES)
                .orderByAsc(Live2dConfig::getSort)
                .last("limit 1"));
        if (config == null) {
            log.debug("[Live2D模型配置] 当前无启用模型，用户端返回 null");
            return null;
        }
        return toVO(config);
    }

    @Override
    public List<Live2dConfigVO> listAvailable() {
        List<Live2dConfig> configs = live2dConfigMapper.selectList(new LambdaQueryWrapper<Live2dConfig>()
                .orderByDesc(Live2dConfig::getSort)
        );
        return configs.stream().map(this::toVO).toList();
    }

    @Override
    public String signDownloadUrl(String path) {
        // 委托 ModelFileSigner 签发：内部做路径前缀校验（防穿越）+ HMAC 签名 + 有效期
        return modelFileSigner.signDownloadUrl(path);
    }

    @Override
    public ModelFileSigner.ModelFileBytes downloadModelFile(String path, long expires, String sign) {
        return modelFileSigner.load(path, expires, sign);
    }

    /**
     * 实体转用户端 VO
     */
    private Live2dConfigVO toVO(Live2dConfig config) {
        return Live2dConfigVO.builder()
                .modelId(config.getId())
                .modelName(config.getModelName())
                .modelUrl(config.getModelUrl())
                .coverImagePath(config.getCoverImagePath())
                .expressionList(config.getExpressionList())
                .motionList(config.getMotionList())
                .build();
    }
}