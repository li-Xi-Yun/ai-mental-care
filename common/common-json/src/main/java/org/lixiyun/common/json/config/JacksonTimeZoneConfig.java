package org.lixiyun.common.json.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.lixiyun.common.core.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 全局Jackson时区序列化统一配置
 * 统一规则：业务LocalDateTime为项目配置本地时区，序列化输出UTC毫秒戳
 * 依赖项目统一DateUtils做时区转换，支持yml动态配置time-zone
 */
@Configuration
public class JacksonTimeZoneConfig {

    @Autowired
    private DateUtils dateUtils;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer timeZoneJacksonCustomizer() {
        return builder -> {
            // 1. 先注册原生JavaTimeModule，支持所有Java8时间基础类型
            JavaTimeModule javaTimeModule = new JavaTimeModule();

            // 2. 自定义时区转换模块，覆盖LocalDateTime序列化规则
            SimpleModule customTimeModule = new SimpleModule("CustomUtcLocalDateTimeModule_v1");

            // 序列化：本地时区LocalDateTime → UTC毫秒数字
            customTimeModule.addSerializer(LocalDateTime.class, new StdSerializer<>(LocalDateTime.class) {
                @Override
                public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                    Instant utcInstant = dateUtils.toUtcZoned(value).toInstant();
                    gen.writeNumber(utcInstant.toEpochMilli());
                }
            });

            // 反序列化：UTC毫秒数字/Jackson默认数组 → 项目配置本地时区LocalDateTime
            customTimeModule.addDeserializer(LocalDateTime.class, new StdDeserializer<>(LocalDateTime.class) {
                @Override
                public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    JsonToken token = p.currentToken();
                    // 数字类型：标准UTC毫秒戳
                    if (token == JsonToken.VALUE_NUMBER_INT) {
                        long utcMilli = p.getLongValue();
                        Instant utcInstant = Instant.ofEpochMilli(utcMilli);
                        return LocalDateTime.ofInstant(utcInstant, dateUtils.getLocalZoneId());
                    }
                    // 空字符串兼容处理，返回null
                    if (token == JsonToken.VALUE_STRING) {
                        String text = p.getText().trim();
                        if (text.isEmpty()) {
                            return null;
                        }
                        return (LocalDateTime) ctxt.handleWeirdStringValue(
                                LocalDateTime.class, text, "时间字段必须传入UTC毫秒数字，不支持日期字符串");
                    }
                    // Jackson默认数组格式兼容：[年, 月, 日, 时, 分, 秒, 纳秒]
                    // 当数据源使用标准JavaTimeModule反序列化时产生此格式（如MysqlSaver的checkpoint）
                    if (token == JsonToken.START_ARRAY) {
                        int year = p.nextIntValue(-1);
                        int month = p.nextIntValue(-1);
                        int day = p.nextIntValue(-1);
                        int hour = p.nextIntValue(-1);
                        int minute = p.nextIntValue(-1);
                        int second = p.nextIntValue(-1);
                        int nano = p.nextIntValue(-1);
                        p.nextToken();
                        if (nano > 0) {
                            return LocalDateTime.of(year, month, day, hour, minute, second, nano);
                        }
                        return LocalDateTime.of(year, month, day, hour, minute, second);
                    }
                    // 非法类型统一抛Jackson标准异常
                    return (LocalDateTime) ctxt.handleUnexpectedToken(LocalDateTime.class, p);
                }
            });

            // 注册顺序：先原生时间模块，再自定义模块，保证自定义规则覆盖默认规则
            builder.modules(javaTimeModule, customTimeModule);
        };
    }
}