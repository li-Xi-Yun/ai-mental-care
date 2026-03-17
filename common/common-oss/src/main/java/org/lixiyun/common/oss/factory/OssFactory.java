package org.lixiyun.common.oss.factory;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.constant.CacheNames;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.common.oss.constant.OssConstant;
import org.lixiyun.common.oss.core.OssClient;
import org.lixiyun.common.oss.exception.OssException;
import org.lixiyun.common.oss.properties.OssProperties;
import org.lixiyun.common.redis.utils.CacheUtils;
import org.lixiyun.common.redis.utils.RedisUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件上传Factory
 *
 * @author lixiyun
 */
@Slf4j
public class OssFactory {

    private static final Map<String, OssClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取默认实例
     */
    public static OssClient instance() {
        // 获取redis 默认类型
        String configKey = RedisUtils.getCacheObject(OssConstant.DEFAULT_CONFIG_KEY);
        if (StrUtil.isEmpty(configKey)) {
            throw new OssException("文件存储服务类型无法找到!");
        }
        return instance(configKey);
    }

    /**
     * 根据类型获取实例
     */
    public static OssClient instance(String configKey) {
        String json = CacheUtils.get(CacheNames.SYS_OSS_CONFIG, configKey);
        if (json == null) {
            throw new OssException("系统异常, '" + configKey + "'配置信息不存在!");
        }
        OssProperties properties = JsonUtils.parseObject(json, OssProperties.class);
        OssClient client = CLIENT_CACHE.get(configKey);
        if (client == null) {
            CLIENT_CACHE.put(configKey, new OssClient(configKey, properties));
            log.info("创建OSS实例 key => {}", configKey);
            return CLIENT_CACHE.get(configKey);
        }
        // 配置不相同则重新构建
        if (!client.checkPropertiesSame(properties)) {
            CLIENT_CACHE.put(configKey, new OssClient(configKey, properties));
            log.info("重载OSS实例 key => {}", configKey);
            return CLIENT_CACHE.get(configKey);
        }
        return client;
    }

}
