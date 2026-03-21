package org.lixiyun.common.core.utils;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Session 工具封装类
 * 支持：1. Session 基础操作 2. 单个数据独立过期时间控制 3. 过期自动清理
 * 适配：统一使用 ServletUtils.getSession() 获取 Session，无需手动传入
 * 适用场景：单服务（Tomcat）环境，分布式场景建议结合 Redis
 *
 * @author 编程助手
 */
@Slf4j
public class SessionUtil {

    // ====================== 静态常量 ======================
    /**
     * Session 默认过期时间：60分钟（秒）
     */
    public static final int DEFAULT_SESSION_TIMEOUT = 3600;

    // ====================== 内部类：带过期时间的属性封装 ======================
    /**
     * 封装带独立过期时间的 Session 属性
     * 实现 Serializable 保证 Session 序列化（如 Tomcat 集群复制场景）
     */
    @Data
    @AllArgsConstructor
    public static class ExpirableAttribute<T> implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 实际存储的值
         */
        private T value;

        /**
         * 过期时间戳（毫秒）：System.currentTimeMillis() + 过期时长
         */
        private long expireTime;

        /**
         * 判断当前属性是否过期
         * @return true=已过期，false=未过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        /**
         * 静态工厂方法：创建带过期时间的属性
         * @param value 存储值（支持任意序列化对象）
         * @param expireTime 过期时长
         * @param timeUnit 时间单位
         * @return 带过期时间的属性对象
         */
        public static <T> ExpirableAttribute<T> of(T value, long expireTime, TimeUnit timeUnit) {
            Assert.notNull(value, "属性值不能为null");
            Assert.notNull(timeUnit, "时间单位不能为null");
            long expireTimestamp = System.currentTimeMillis() + timeUnit.toMillis(expireTime);
            return new ExpirableAttribute<>(value, expireTimestamp);
        }

        /**
         * 重载：默认以秒为单位设置过期时间
         */
        public static <T> ExpirableAttribute<T> of(T value, int expireSeconds) {
            return of(value, expireSeconds, TimeUnit.SECONDS);
        }

        /**
         * 获取属性剩余存活时间（毫秒）
         * @return 剩余毫秒数（≤0 表示已过期）
         */
        public long getRemainingTimeMillis() {
            long remaining = expireTime - System.currentTimeMillis();
            return Math.max(remaining, 0);
        }

        /**
         * 重置属性过期时间（覆盖为新的过期时长）
         * @param expireTime 新的过期时长
         * @param timeUnit 时间单位
         */
        public void refreshExpireTime(long expireTime, TimeUnit timeUnit) {
            Assert.notNull(timeUnit, "时间单位不能为null");
            if (expireTime < 0) {
                throw new IllegalArgumentException("过期时长不能为负数");
            }
            this.expireTime = System.currentTimeMillis() + timeUnit.toMillis(expireTime);
            log.debug("属性过期时间已重置：新过期时间戳={}", this.expireTime);
        }

        /**
         * 延长属性过期时间（在原有剩余时间基础上增加）
         * @param extendTime 延长的时长
         * @param timeUnit 时间单位
         */
        public void extendExpireTime(long extendTime, TimeUnit timeUnit) {
            Assert.notNull(timeUnit, "时间单位不能为null");
            if (extendTime < 0) {
                throw new IllegalArgumentException("延长时长不能为负数");
            }
            // 已过期则从当前时间开始延长，未过期则在原有过期时间基础上延长
            long newExpireTime = Math.max(this.expireTime, System.currentTimeMillis())
                    + timeUnit.toMillis(extendTime);
            this.expireTime = newExpireTime;
            log.debug("属性过期时间已延长：新过期时间戳={}", this.expireTime);
        }

    }

    // ====================== 基础 Session 操作（统一使用 ServletUtils 获取 Session） ======================

    /**
     * 设置 Session 整体过期时间
     * @param timeout 过期时长（秒）
     */
    public static void setSessionTimeout(int timeout) {
        HttpSession session = ServletUtils.getSession();
        Assert.notNull(session, "HttpSession 不能为null");

        if (timeout < 0) {
            log.warn("Session过期时间不能为负数，已重置为默认值：{}秒", DEFAULT_SESSION_TIMEOUT);
            timeout = DEFAULT_SESSION_TIMEOUT;
        }
        session.setMaxInactiveInterval(timeout);
        log.debug("Session[{}]过期时间已设置为：{}秒", session.getId(), timeout);
    }

    /**
     * 获取 Session 剩余过期时间（秒）
     * @return 剩余秒数（≤0 表示已过期）
     */
    public static int getSessionRemainingSeconds() {
        HttpSession session = ServletUtils.getSession();
        if (session == null) {
            log.warn("Session不存在，剩余过期时间返回0");
            return 0;
        }

        int maxInactiveSeconds = session.getMaxInactiveInterval();
        long elapsedSeconds = (System.currentTimeMillis() - session.getLastAccessedTime()) / 1000;
        int remainingSeconds = maxInactiveSeconds - (int) elapsedSeconds;
        return Math.max(remainingSeconds, 0);
    }

    /**
     * 销毁 Session（清除所有数据）
     */
    public static void invalidateSession() {
        HttpSession session = ServletUtils.getSession();
        if (session != null) {
            try {
                session.invalidate();
                log.debug("Session[{}]已销毁", session.getId());
            } catch (IllegalStateException e) {
                log.warn("Session[{}]已失效，无需重复销毁", session.getId(), e);
            }
        } else {
            log.warn("Session不存在，无需销毁");
        }
    }

    // ====================== 普通属性操作（无独立过期） ======================

    /**
     * 存入普通 Session 属性（无独立过期，随Session整体过期）
     * @param key 属性名
     * @param value 属性值
     */
    public static void setAttribute(String key, Object value) {
        HttpSession session = ServletUtils.getSession();
        Assert.notNull(session, "HttpSession 不能为null");
        Assert.hasText(key, "属性名不能为空");

        session.setAttribute(key, value);
        log.debug("Session[{}]存入普通属性：{}={}", session.getId(), key, value);
    }

    /**
     * 获取普通 Session 属性
     * @param key 属性名
     * @return 属性值（不存在返回null）
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAttribute(String key) {
        HttpSession session = ServletUtils.getSession();
        if (session == null || !hasText(key)) {
            return null;
        }
        return (T) session.getAttribute(key);
    }

    /**
     * 移除普通 Session 属性
     * @param key 属性名
     */
    public static void removeAttribute(String key) {
        HttpSession session = ServletUtils.getSession();
        if (session != null && hasText(key)) {
            session.removeAttribute(key);
            log.debug("Session[{}]移除属性：{}", session.getId(), key);
        }
    }

    // ====================== 带独立过期时间的属性操作 ======================

    /**
     * 存入带独立过期时间的 Session 属性
     * @param key 属性名
     * @param value 属性值
     * @param expireSeconds 该属性独立过期时间（秒）
     */
    public static <T> void setExpirableAttribute(String key, T value, int expireSeconds) {
        HttpSession session = ServletUtils.getSession();
        Assert.notNull(session, "HttpSession 不能为null");
        Assert.hasText(key, "属性名不能为空");

        ExpirableAttribute<T> expirableAttr = ExpirableAttribute.of(value, expireSeconds);
        session.setAttribute(key, expirableAttr);
        log.debug("Session[{}]存入带过期属性：{}，过期时间：{}秒", session.getId(), key, expireSeconds);
    }

    /**
     * 存入带独立过期时间的 Session 属性（自定义时间单位）
     * @param key 属性名
     * @param value 属性值
     * @param expireTime 过期时长
     * @param timeUnit 时间单位
     */
    public static <T> void setExpirableAttribute(String key, T value, long expireTime, TimeUnit timeUnit) {
        HttpSession session = ServletUtils.getSession();
        Assert.notNull(session, "HttpSession 不能为null");
        Assert.hasText(key, "属性名不能为空");

        ExpirableAttribute<T> expirableAttr = ExpirableAttribute.of(value, expireTime, timeUnit);
        session.setAttribute(key, expirableAttr);
        log.debug("Session[{}]存入带过期属性：{}，过期时间：{} {}", session.getId(), key, expireTime, timeUnit);
    }

    /**
     * 获取带独立过期时间的 Session 属性（自动校验过期）
     * @param key 属性名
     * @return 未过期返回属性值，已过期/不存在返回null（并自动移除过期属性）
     */
    @SuppressWarnings("unchecked")
    public static <T> T getExpirableAttribute(String key) {
        HttpSession session = ServletUtils.getSession();
        if (session == null || !hasText(key)) {
            return null;
        }

        Object attrObj = session.getAttribute(key);
        // 不是带过期时间的属性，直接返回null
        if (!(attrObj instanceof ExpirableAttribute)) {
            log.warn("Session[{}]属性{}不是带过期时间的类型", session.getId(), key);
            return null;
        }

        ExpirableAttribute<T> expirableAttr = (ExpirableAttribute<T>) attrObj;
        // 校验是否过期
        if (expirableAttr.isExpired()) {
            removeAttribute(key);
            log.debug("Session[{}]属性{}已过期，已自动移除", session.getId(), key);
            return null;
        }

        return expirableAttr.getValue();
    }

    /**
     * 批量清理 Session 中所有过期的独立属性
     */
    public static void cleanExpiredAttributes() {
        HttpSession session = ServletUtils.getSession();
        if (session == null) {
            log.warn("Session不存在，无需清理过期属性");
            return;
        }

        try {
            session.getAttributeNames().asIterator().forEachRemaining(key -> {
                Object attrObj = session.getAttribute(key);
                if (attrObj instanceof ExpirableAttribute && ((ExpirableAttribute<?>) attrObj).isExpired()) {
                    removeAttribute(key);
                    log.debug("Session[{}]清理过期属性：{}", session.getId(), key);
                }
            });
            log.debug("Session[{}]过期属性清理完成", session.getId());
        } catch (IllegalStateException e) {
            log.warn("Session[{}]已失效，无法清理过期属性", session.getId(), e);
        }
    }

    // ====================== 获取独立过期属性剩余存活时间 ======================

    /**
     * 获取带独立过期时间的属性剩余存活时间（秒）
     * @param key 属性名
     * @return 剩余秒数（≤0 表示已过期/不存在）
     */
    public static long getExpirableAttributeRemainingSeconds(String key) {
        return getExpirableAttributeRemainingTime(key, TimeUnit.SECONDS);
    }

    /**
     * 获取带独立过期时间的属性剩余存活时间（自定义单位）
     * @param key 属性名
     * @param timeUnit 时间单位（秒/分钟/小时等）
     * @return 剩余时间（≤0 表示已过期/不存在）
     */
    public static long getExpirableAttributeRemainingTime(String key, TimeUnit timeUnit) {
        Assert.hasText(key, "属性名不能为空");
        Assert.notNull(timeUnit, "时间单位不能为null");

        HttpSession session = ServletUtils.getSession();
        if (session == null) {
            log.warn("Session不存在，属性{}剩余时间返回0", key);
            return 0;
        }

        Object attrObj = session.getAttribute(key);
        // 不是带过期时间的属性，返回0
        if (!(attrObj instanceof ExpirableAttribute)) {
            log.warn("Session[{}]属性{}不是带过期时间的类型，剩余时间返回0", session.getId(), key);
            return 0;
        }

        ExpirableAttribute<?> expirableAttr = (ExpirableAttribute<?>) attrObj;
        // 获取剩余毫秒数，转换为指定单位
        long remainingMillis = expirableAttr.getRemainingTimeMillis();
        long remainingTime = timeUnit.convert(remainingMillis, TimeUnit.MILLISECONDS);

        log.debug("Session[{}]属性{}剩余存活时间：{} {}（原始：{}毫秒）",
                session.getId(), key, remainingTime, timeUnit, remainingMillis);
        return remainingTime;
    }

    // ====================== 刷新独立过期属性的公开方法 ======================

    /**
     * 刷新独立过期属性的过期时间（重置为指定秒数）
     * @param key 属性名
     * @param newExpireSeconds 新的过期时长（秒）
     * @return true=刷新成功，false=属性不存在/不是独立过期类型/已过期
     */
    public static boolean refreshExpirableAttribute(String key, long newExpireSeconds) {
        return refreshExpirableAttribute(key, newExpireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 刷新独立过期属性的过期时间（重置为指定时长，自定义单位）
     * @param key 属性名
     * @param newExpireTime 新的过期时长
     * @param timeUnit 时间单位
     * @return true=刷新成功，false=刷新失败
     */
    public static boolean refreshExpirableAttribute(String key, long newExpireTime, TimeUnit timeUnit) {
        Assert.hasText(key, "属性名不能为空");
        Assert.notNull(timeUnit, "时间单位不能为null");
        if (newExpireTime < 0) {
            log.warn("属性{}刷新失败：过期时长不能为负数", key);
            return false;
        }

        HttpSession session = ServletUtils.getSession();
        if (session == null) {
            log.warn("属性{}刷新失败：Session不存在", key);
            return false;
        }

        Object attrObj = session.getAttribute(key);
        // 不是独立过期属性，刷新失败
        if (!(attrObj instanceof ExpirableAttribute)) {
            log.warn("Session[{}]属性{}刷新失败：不是带过期时间的类型", session.getId(), key);
            return false;
        }

        ExpirableAttribute<?> expirableAttr = (ExpirableAttribute<?>) attrObj;
        // 已过期的属性直接返回失败（建议重新存入）
        if (expirableAttr.isExpired()) {
            log.warn("Session[{}]属性{}刷新失败：属性已过期", session.getId(), key);
            return false;
        }

        // 重置过期时间
        expirableAttr.refreshExpireTime(newExpireTime, timeUnit);
        // 重新存入Session（触发更新）
        session.setAttribute(key, expirableAttr);
        log.debug("Session[{}]属性{}刷新成功：新过期时长={} {}",
                session.getId(), key, newExpireTime, timeUnit);
        return true;
    }

    /**
     * 延长独立过期属性的过期时间（在原有剩余时间基础上增加）
     * @param key 属性名
     * @param extendSeconds 延长的时长（秒）
     * @return true=延长成功，false=延长失败
     */
    public static boolean extendExpirableAttribute(String key, int extendSeconds) {
        return extendExpirableAttribute(key, extendSeconds, TimeUnit.SECONDS);
    }

    /**
     * 延长独立过期属性的过期时间（自定义单位）
     * @param key 属性名
     * @param extendTime 延长的时长
     * @param timeUnit 时间单位
     * @return true=延长成功，false=延长失败
     */
    public static boolean extendExpirableAttribute(String key, long extendTime, TimeUnit timeUnit) {
        Assert.hasText(key, "属性名不能为空");
        Assert.notNull(timeUnit, "时间单位不能为null");
        if (extendTime < 0) {
            log.warn("属性{}延长失败：延长时长不能为负数", key);
            return false;
        }

        HttpSession session = ServletUtils.getSession();
        if (session == null) {
            log.warn("属性{}延长失败：Session不存在", key);
            return false;
        }

        Object attrObj = session.getAttribute(key);
        if (!(attrObj instanceof ExpirableAttribute)) {
            log.warn("Session[{}]属性{}延长失败：不是带过期时间的类型", session.getId(), key);
            return false;
        }

        ExpirableAttribute<?> expirableAttr = (ExpirableAttribute<?>) attrObj;
        // 已过期则从当前时间开始延长
        expirableAttr.extendExpireTime(extendTime, timeUnit);
        session.setAttribute(key, expirableAttr);
        log.debug("Session[{}]属性{}延长成功：延长时长={} {}",
                session.getId(), key, extendTime, timeUnit);
        return true;
    }

    // ====================== 私有工具方法 ======================

    /**
     * 字符串非空判断（兼容空字符串）
     */
    private static boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }
}