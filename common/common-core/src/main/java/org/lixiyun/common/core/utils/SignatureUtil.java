package org.lixiyun.common.core.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 签名工具类（HMAC-SHA256）
 * <p>
 * 用于资源下载 URL 的短期签名：对「资源路径 + 过期时间戳」做 HMAC-SHA256 摘要，
 * 下载接口在收到请求时重新计算比对，防止裸链访问、URL 篡改与越权读取。
 * </p>
 *
 * <p><b>设计说明</b>：签名绑定资源相对路径（content）与有效期（expires），因此
 * ① URL 泄露后仍会在过期时间点失效；② 篡改 content / expires 会导致重算签名不匹配；
 * ③ content 需配合下载接口内层再校验（normalize 后必须落在资源根目录内）防止目录穿越。</p>
 *
 * <p><b>通用性</b>：纯静态工具，不持有任何配置/密钥状态。密钥（secret）由调用方
 * 通过方法参数传入，因此<b>不绑定任何业务命名</b>（如 live2d），可被任意资源鉴权场景复用。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * long expires = System.currentTimeMillis() / 1000 + 300;   // 5 分钟有效
 * String path  = "live2d-models/hiyori/hiyori.model3.json";
 * String secret = "...";                                     // 由调用方配置持有
 * String sign  = SignatureUtil.sign(path, expires, secret);
 * // 生成 URL: /user/live2d-model/file?path=...&expires=...&sign=...
 *
 * boolean valid = SignatureUtil.verify(requestPath, requestExpires, requestSign, secret);
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Slf4j
public class SignatureUtil {

    private SignatureUtil() {
    }

    /**
     * 对资源路径 + 过期时间戳计算 HMAC-SHA256 签名
     *
     * @param content 资源相对路径（须为下载接口内层 also 校验的路径，如 live2d-models/xxx/model3.json）
     * @param expires 过期时间戳（Unix 秒）
     * @param secret  签名密钥（HMAC 使用），调用方持有
     * @return URL 安全的 Base64 签名
     */
    public static String sign(String content, long expires, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = content + "&" + expires;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            log.error("[签名工具] 签名计算失败", e);
            throw new IllegalStateException("签名计算失败", e);
        }
    }

    /**
     * 验证签名是否有效（含过期校验 + 常量时间比对）
     *
     * @param content 资源相对路径
     * @param expires 请求携带的过期时间戳（Unix 秒）
     * @param sign    请求携带的签名
     * @param secret  签名密钥（HMAC 使用），调用方持有
     * @return true-签名有效且未过期
     */
    public static boolean verify(String content, long expires, String sign, String secret) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        // 过期校验：过期时间必须在当前时间之后
        long now = System.currentTimeMillis() / 1000;
        if (expires <= now) {
            log.debug("[签名工具] 签名已过期，expires：{}，now：{}", expires, now);
            return false;
        }
        String expected = sign(content, expires, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8));
    }
}