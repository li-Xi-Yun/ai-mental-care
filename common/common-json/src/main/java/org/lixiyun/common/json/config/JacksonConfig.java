package org.lixiyun.common.json.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.json.handler.BigNumberSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * jackson 配置
 *
 * @author lixiyun
 */
@Slf4j
@AutoConfiguration(before = JacksonAutoConfiguration.class)
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizer() {
        return builder -> {
            // 全局配置序列化返回 JSON 处理
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            // 通过BigNumberSerializer将这些大数字转换为字符串形式传输
            // 防止JavaScript中大数字精度丢失的问题
            javaTimeModule.addSerializer(Long.class, BigNumberSerializer.INSTANCE);
            javaTimeModule.addSerializer(Long.TYPE, BigNumberSerializer.INSTANCE);
            javaTimeModule.addSerializer(BigInteger.class, BigNumberSerializer.INSTANCE);
            // 将BigDecimal类型的数据序列化为字符串
            javaTimeModule.addSerializer(BigDecimal.class, ToStringSerializer.instance);
            // 配置了LocalDateTime类型的序列化和反序列化规则，将其格式化为"yyyy-MM-dd HH:mm:ss"格式的字符串
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
            builder.modules(javaTimeModule);
            // 设置Jackson使用系统默认时区进行时间处理
            builder.timeZone(TimeZone.getDefault());

            // 自定义大数模块：独立注册，确保优先级
            SimpleModule bigNumberModule = new SimpleModule("BigNumberModule");
            // 注册Long/long类型序列化器（核心：覆盖默认序列化）
            bigNumberModule.addSerializer(Long.class, BigNumberSerializer.INSTANCE);
            bigNumberModule.addSerializer(Long.TYPE, BigNumberSerializer.INSTANCE);
            // 注册BigInteger序列化器
            bigNumberModule.addSerializer(BigInteger.class, BigNumberSerializer.INSTANCE);
            // 注册BigDecimal序列化器（直接转字符串，避免精度丢失）
            bigNumberModule.addSerializer(BigDecimal.class, ToStringSerializer.instance);
            builder.modules(bigNumberModule);

            log.info("初始化 jackson 配置");
        };
    }

}
