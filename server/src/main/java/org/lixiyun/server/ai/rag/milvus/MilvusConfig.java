package org.lixiyun.server.ai.rag.milvus;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.rag.milvus.properties.MilvusProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author lixiyun
 * @since 2026-05-03 16:54
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MilvusConfig {

    private final MilvusProperties milvusProperties;

    @Bean
    public MilvusClientV2 milvusClientV2() {
        String host = milvusProperties.getHost();
        int port = milvusProperties.getPort();
        String username = milvusProperties.getUsername();
        String password = milvusProperties.getPassword();

        log.info("Initializing MilvusClientV2 with host={} port={}", host, port);

        return new MilvusClientV2(
                ConnectConfig.builder()
//                        .uri("http://" + host + ":" + port)
                        .uri("grpc://" + host + ":" + port)
                        .token(username + ":" + password)
                        .build()
        );
    }
}
