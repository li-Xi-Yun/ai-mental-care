package org.lixiyun.server.ai.saver;

import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class GraphSaverConfig {

    @Bean
    public MysqlSaver mysqlSaver(DataSource dataSource) {
        return MysqlSaver.builder()
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public SaverConfig saverConfig(MysqlSaver mysqlSaver) {
        return SaverConfig.builder()
                .register(mysqlSaver)
                .build();
    }
}