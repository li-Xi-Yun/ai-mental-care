package org.lixiyun.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.TimeZone;

@EnableRetry // 开启重试功能
@EnableScheduling // 开启定时任务功能
@EnableTransactionManagement
@MapperScan("org.lixiyun.server.mapper")
@SpringBootApplication(scanBasePackages = {"org.lixiyun"})
public class ServerApplication {

    public static void main(String[] args) {
        // 统一时区基准为 UTC：与 web.timeZone=UTC、JDBC 会话时区（application.yaml 的 sessionVariables）
        // 保持一致，避免 LocalDateTime.now() 写入本地时间导致的时间偏移。
        // 注意：此项会使日志时间戳也变为 UTC（比东八区少 8 小时）。
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ConfigurableApplicationContext context = SpringApplication.run(ServerApplication.class, args);
        // 从上下文获取环境参数
        Environment env = context.getEnvironment();
        String[] activeProfiles = env.getActiveProfiles();
        String serverPort = env.getProperty("server.port");
        String applicationName = env.getProperty("spring.application.name");
        System.out.println("启动成功, 环境：" + activeProfiles[0] + ", 服务端口：" + serverPort + ", 服务名称：" + applicationName);
    }
}