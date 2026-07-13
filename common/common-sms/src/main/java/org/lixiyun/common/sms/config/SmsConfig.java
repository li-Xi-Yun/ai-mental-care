package org.lixiyun.common.sms.config;

import org.lixiyun.common.sms.config.properties.SmsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 短信配置类
 *
 * @author lixiyun
 * @version 4.2.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

//    @Configuration
//    @ConditionalOnProperty(value = "sms.enabled", havingValue = "true")
//    @ConditionalOnClass(com.aliyun.dysmsapi20170525.Client.class)
//    static class AliyunSmsConfig {
//
//        @Bean
//        public SmsTemplate aliyunSmsTemplate(SmsProperties smsProperties) {
//            return new AliyunSmsTemplate(smsProperties);
//        }
//
//    }
//
//    @Configuration
//    @ConditionalOnProperty(value = "sms.enabled", havingValue = "true")
//    @ConditionalOnClass(com.tencentcloudapi.sms.v20190711.SmsClient.class)
//    static class TencentSmsConfig {
//
//        @Bean
//        public SmsTemplate tencentSmsTemplate(SmsProperties smsProperties) {
//            return new TencentSmsTemplate(smsProperties);
//        }
//
//    }

}
