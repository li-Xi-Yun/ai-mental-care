package org.lixiyun.common.redis.config;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.redis.handler.KeyPrefixHandler;
import org.lixiyun.common.redis.manager.PlusSpringCacheManager;
import org.lixiyun.common.redis.properties.RedissonProperties;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

/**
 * redis配置
 *
 * @author lixiyun
 */
@Slf4j
@AutoConfiguration
@EnableCaching
@EnableConfigurationProperties(RedissonProperties.class)
public class RedisConfig {

    @Autowired
    private RedissonProperties redissonProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer() {
        return config -> {
            config.setThreads(redissonProperties.getThreads())
                .setNettyThreads(redissonProperties.getNettyThreads())
                .setCodec(new JsonJacksonCodec(objectMapper));

            RedissonProperties.SingleServerConfig singleServerConfig = redissonProperties.getSingleServerConfig();
            if (ObjectUtil.isNotNull(singleServerConfig)) {
                // 使用单机模式
                config.useSingleServer()
                    // 设置 Redis 键的前缀处理器，用于给所有 Redis 键添加统一前缀
                    .setNameMapper(new KeyPrefixHandler(redissonProperties.getKeyPrefix()))
                    // 设置命令执行的超时时间（毫秒）
                    .setTimeout(singleServerConfig.getTimeout())
                    // 设置客户端名称，用于标识不同的客户端
                    .setClientName(singleServerConfig.getClientName())
                    // 设置连接空闲超时时间（毫秒）
                    .setIdleConnectionTimeout(singleServerConfig.getIdleConnectionTimeout())
                    // 设置发布和订阅连接池大小
                    .setSubscriptionConnectionPoolSize(singleServerConfig.getSubscriptionConnectionPoolSize())
                    // 设置连接池最小空闲连接数
                    .setConnectionMinimumIdleSize(singleServerConfig.getConnectionMinimumIdleSize())
                    // 设置连接池最大连接数
                    .setConnectionPoolSize(singleServerConfig.getConnectionPoolSize());
            }

            // 集群配置方式 参考下方注释
            RedissonProperties.ClusterServersConfig clusterServersConfig = redissonProperties.getClusterServersConfig();
            if (ObjectUtil.isNotNull(clusterServersConfig)) {
                config.useClusterServers()
                    //设置redis key前缀
                    .setNameMapper(new KeyPrefixHandler(redissonProperties.getKeyPrefix()))
                    .setTimeout(clusterServersConfig.getTimeout())
                    .setClientName(clusterServersConfig.getClientName())
                    .setIdleConnectionTimeout(clusterServersConfig.getIdleConnectionTimeout())
                    .setSubscriptionConnectionPoolSize(clusterServersConfig.getSubscriptionConnectionPoolSize())
                    // 设置主节点最小空闲连接数
                    .setMasterConnectionMinimumIdleSize(clusterServersConfig.getMasterConnectionMinimumIdleSize())
                    // 设置主节点连接池大小
                    .setMasterConnectionPoolSize(clusterServersConfig.getMasterConnectionPoolSize())
                    // 设置从节点最小空闲连接数
                    .setSlaveConnectionMinimumIdleSize(clusterServersConfig.getSlaveConnectionMinimumIdleSize())
                    // 设置从节点连接池大小
                    .setSlaveConnectionPoolSize(clusterServersConfig.getSlaveConnectionPoolSize())
                    // 设置读取模式（SLAVE/MASTER）
                    .setReadMode(clusterServersConfig.getReadMode())
                    // 设置订阅模式（MASTER/SLAVE）
                    .setSubscriptionMode(clusterServersConfig.getSubscriptionMode());
            }
            log.info("初始化 redis 配置");
        };
    }

    /**
     * 自定义缓存管理器 整合spring-cache
     */
    @Bean
    public CacheManager cacheManager() {
        return new PlusSpringCacheManager();
    }

}
