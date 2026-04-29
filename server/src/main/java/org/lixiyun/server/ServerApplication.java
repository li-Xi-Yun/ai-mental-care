package org.lixiyun.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableRetry // 开启重试功能
@EnableTransactionManagement
@MapperScan("org.lixiyun.server.mapper")
@SpringBootApplication(scanBasePackages = {"org.lixiyun"})
public class ServerApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ServerApplication.class, args);
        // 从上下文获取环境参数
        Environment env = context.getEnvironment();
        String[] activeProfiles = env.getActiveProfiles();
        String serverPort = env.getProperty("server.port");
        String applicationName = env.getProperty("spring.application.name");
        System.out.println("启动成功, 环境：" + activeProfiles[0] + ", 服务端口：" + serverPort + ", 服务名称：" + applicationName);
    }
}
