package org.lixiyun.common.verification.code.properties;

import lombok.Data;
import org.lixiyun.common.core.factory.YmlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "verification.code.properties")
//@ConditionalOnProperty(prefix = "verification.code", name = "enable", havingValue = "true")
@PropertySource(value = "classpath:common-verification-code.yml", factory = YmlPropertySourceFactory.class)
public class VerificationCodeProperties {

    // 设置验证码图片的宽度
    private int width;

    // 设置验证码图片的高度
    private int height;

    // 设置验证码字符个数（不包括算术验证码）
    private int codeCount;

    // 设置算术验证码的运算位数为3位（如：123+456=?）
    private int numLen;

    // 设置算术验证码中参与计算的每个数字的最大值
    private int numMax;

    // 设置验证码过期时间(秒)
    private int expireTime;

}