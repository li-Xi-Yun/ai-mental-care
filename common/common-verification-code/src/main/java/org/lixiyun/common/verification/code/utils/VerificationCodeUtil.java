package org.lixiyun.common.verification.code.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.extra.spring.SpringUtil;
import com.pig4cloud.captcha.*;
import com.pig4cloud.captcha.base.Captcha;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.mail.utils.SendSimpleEmailUtil;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.common.verification.code.properties.VerificationCodeProperties;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class VerificationCodeUtil {

    private static final VerificationCodeProperties verificationCodeProperties = SpringUtil.getBean(VerificationCodeProperties.class);

    public static final String VERIFICATION_CODE_KEY = "verification-code:";

    /**
     * 判断验证码是否正确
     * @param code 用户输入的验证码
     * @param key 验证码对应存放在Redis中的键
     */
    public static void judgmentCode(String code, String key){
        // 获取Redis中的验证码答案
        String value = RedisUtils.getCacheObject(VERIFICATION_CODE_KEY + key);

        // 验证码校验
        if (value == null || !value.equals(code)) {
            throw new BusinessException(AuthenticationExceptionEnum.CODE_ERROR);
        }

        RedisUtils.deleteObject(key);
    }

    /**
     * 校验邮箱验证码
     * @param emailCode 用户输入的邮箱验证码
     * @param email 邮箱号
     */
    public static void judgmentEmailCode(String emailCode, String email){
        judgmentCode(emailCode, email);
    }

    public static void sentVerificationCodeEmail(String email, String from) {
        String value = UUID.randomUUID().toString();
        System.out.println(value);
        SendSimpleEmailUtil.sendHtmlEmail(email, "注册验证码", "<h1>您的验证码为：" + value + "</h1>", from);
        RedisUtils.setCacheObject(VerificationCodeUtil.VERIFICATION_CODE_KEY + email, value, 3, TimeUnit.MINUTES);
    }

    /**
     * 生成验证码图片
     * <p>Map的key分别为"image"和"key"，后一个key需要发送给前端，在校验时由前端发送，后端根据这个key进行校验</p>
     * @return 一个Map集合，包含验证码图片的Base64编码字符串和存放在Redis中验证码的答案对应的键
     */
    public static Map<String, Object> getVerificationCodePhone() {
        Random random = new Random();
        int i = random.nextInt(3);
        Captcha captcha = null;
        try {
            captcha = switch (i){
                case 0 -> getPngCaptcha();
                case 1 -> getGifCaptcha();
                case 2 -> getArithmeticCaptcha();
//                case 3 -> getChineseCaptcha();
//                case 4 -> getGifChineseCaptcha();
                default -> null;
            };
        } catch (IOException | FontFormatException e) {
            throw new RuntimeException(e);
        }
        String key = storageVerificationCode(captcha);
        Map<String, Object> map = new HashMap<>();
        map.put("key", key);
        // 用途：可以直接将 base64 字符串插入到 HTML 的 <img src="..." /> 中显示图片
        map.put("image", captcha.toBase64());
        return map;
    }

    private static String storageVerificationCode(Captcha captcha){
        UUID uuid = UUID.randomUUID();
        String key = VERIFICATION_CODE_KEY + uuid;
        // 获取对应的答案
        String value = captcha.text();

        System.out.println("答案是：" + value);

        // 存储到redis中
        RedisUtils.setCacheObject(key, value, verificationCodeProperties.getExpireTime(), TimeUnit.SECONDS);
        return uuid.toString();
    }


    private static SpecCaptcha getPngCaptcha() throws IOException, FontFormatException {
        SpecCaptcha captcha = new SpecCaptcha(verificationCodeProperties.getWidth(), verificationCodeProperties.getHeight(), verificationCodeProperties.getCodeCount());
        Random random = new Random();
        int i = random.nextInt(6) + 1;
        captcha.setCharType(i);
        int t = random.nextInt(9) + 1;
        captcha.setFont(t);
        return captcha;
    }

    private static GifCaptcha getGifCaptcha() throws IOException, FontFormatException {
        GifCaptcha captcha = new GifCaptcha(verificationCodeProperties.getWidth(), verificationCodeProperties.getHeight(), verificationCodeProperties.getCodeCount());
        Random random = new Random();
        int i = random.nextInt(6) + 1;
        captcha.setCharType(i);
        int t = random.nextInt(9) + 1;
        captcha.setFont(t);
        return captcha;
    }

    private static ChineseCaptcha getChineseCaptcha() {
        return new ChineseCaptcha(verificationCodeProperties.getWidth(), verificationCodeProperties.getHeight(), verificationCodeProperties.getCodeCount());
    }

    private static ChineseGifCaptcha getGifChineseCaptcha() {
        return new ChineseGifCaptcha(verificationCodeProperties.getWidth(), verificationCodeProperties.getHeight(), verificationCodeProperties.getCodeCount());
    }

    private static ArithmeticCaptcha getArithmeticCaptcha() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(verificationCodeProperties.getWidth(), verificationCodeProperties.getHeight(), verificationCodeProperties.getCodeCount());
        captcha.setLen(verificationCodeProperties.getNumLen());  // 几位数运算，默认是两位
        captcha.getArithmeticString();  // 获取运算的公式：3+2=?
        captcha.supportAlgorithmSign(4); // 可设置支持的算法：2 表示只生成带加减法的公式
        captcha.setDifficulty(verificationCodeProperties.getNumMax()); // 设置计算难度，参与计算的每一个整数的最大值
        return captcha;
    }


}
