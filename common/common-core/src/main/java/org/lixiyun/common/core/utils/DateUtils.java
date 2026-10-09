package org.lixiyun.common.core.utils;

import lombok.RequiredArgsConstructor;
import org.lixiyun.common.core.properties.WebProperties;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * 时间工具类
 *
 * @author lixiyun
 */
@Component
@RequiredArgsConstructor
public class DateUtils extends org.apache.commons.lang3.time.DateUtils {

    private final WebProperties webProperties;

    public static final String YYYY = "yyyy";

    public static final String YYYY_MM = "yyyy-MM";

    public static final String YYYY_MM_DD = "yyyy-MM-dd";

    public static final String YYYYMMDDHHMMSS = "yyyyMMddHHmmss";

    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private static final String[] PARSE_PATTERNS = {
        "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM",
        "yyyy/MM/dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM",
        "yyyy.MM.dd", "yyyy.MM.dd HH:mm:ss", "yyyy.MM.dd HH:mm", "yyyy.MM"};

    /**
     * 获取服务器启动时间
     */
    public static Date getServerStartDate() {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        return new Date(time);
    }

    /**
     * 获取配置的本地时区ZoneId
     * 优先使用WebProperties中配置的时区，未配置则使用系统默认时区
     *
     * @return 本地时区ZoneId
     */
    public ZoneId getLocalZoneId() {
        String tz = webProperties.getTimeZone();
        return (tz != null && !tz.isEmpty()) ? ZoneId.of(tz) : ZoneId.systemDefault();
    }

    /**
     * 将本地时区的LocalDateTime转换为UTC的LocalDateTime
     *
     * @param localDateTime 本地时区的时间
     * @return UTC时间
     */
    public LocalDateTime toUtc(LocalDateTime localDateTime) {
        return localDateTime.atZone(getLocalZoneId())
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * 将UTC的LocalDateTime转换为本地时区的LocalDateTime
     *
     * @param utcDateTime UTC时间
     * @return 本地时区的时间
     */
    public LocalDateTime fromUtc(LocalDateTime utcDateTime) {
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(getLocalZoneId())
                .toLocalDateTime();
    }

    /**
     * 将本地时区的Date转换为UTC的Date
     * 将Date视为本地时区的时间点，转换为其在UTC时区对应的同一瞬间
     *
     * @param localDate 本地时区的日期
     * @return UTC时区的日期
     */
    public Date toUtcDate(Date localDate) {
        ZonedDateTime localZdt = localDate.toInstant().atZone(getLocalZoneId());
        ZonedDateTime utcZdt = localZdt.withZoneSameInstant(ZoneOffset.UTC);
        return Date.from(utcZdt.toInstant());
    }

    /**
     * 将UTC的Date转换为本地时区的Date
     * 将Date视为UTC时区的时间点，转换为其在本地时区对应的同一瞬间
     *
     * @param utcDate UTC时区的日期
     * @return 本地时区的日期
     */
    public Date fromUtcDate(Date utcDate) {
        ZonedDateTime utcZdt = utcDate.toInstant().atZone(ZoneOffset.UTC);
        ZonedDateTime localZdt = utcZdt.withZoneSameInstant(getLocalZoneId());
        return Date.from(localZdt.toInstant());
    }

    /**
     * 将本地时区的LocalDateTime转换为UTC的ZonedDateTime
     *
     * @param localDateTime 本地时区的时间
     * @return UTC时区的ZonedDateTime
     */
    public ZonedDateTime toUtcZoned(LocalDateTime localDateTime) {
        return localDateTime.atZone(getLocalZoneId())
                .withZoneSameInstant(ZoneOffset.UTC);
    }

    /**
     * 将UTC的LocalDateTime转换为本地时区的ZonedDateTime
     *
     * @param utcDateTime UTC时间
     * @return 本地时区的ZonedDateTime
     */
    public ZonedDateTime fromUtcZoned(LocalDateTime utcDateTime) {
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(getLocalZoneId());
    }

    // ==================== 相对时间文本 ====================

    /**
     * 计算指定时间相对于当前时间的相对时间文本
     *
     * @param dateTime 指定时间
     * @return 相对时间文本，如"刚刚"、"3分钟前"、"2小时前"、"5天前"等；null或未来时间返回空字符串
     */
    public static String toRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        long seconds = Duration.between(dateTime, LocalDateTime.now()).getSeconds();
        if (seconds < 0) {
            return "";
        }
        if (seconds < 60) {
            return toSecondsAgo(seconds);
        }
        if (seconds < 3600) {
            return toMinutesAgo(seconds);
        }
        if (seconds < 86400) {
            return toHoursAgo(seconds);
        }
        if (seconds < 2592000L) {
            return toDaysAgo(seconds);
        }
        int months = (int) (seconds / 2592000L);
        if (months < 12) {
            return toMonthsAgo(months);
        }
        return toYearsAgo(months / 12);
    }

    /**
     * 秒级相对时间文本
     *
     * @param seconds 时间差（秒）
     * @return "刚刚"
     */
    private static String toSecondsAgo(long seconds) {
        return "刚刚";
    }

    /**
     * 分钟级相对时间文本
     *
     * @param seconds 时间差（秒）
     * @return 如"3分钟前"
     */
    private static String toMinutesAgo(long seconds) {
        return (seconds / 60) + "分钟前";
    }

    /**
     * 小时级相对时间文本
     *
     * @param seconds 时间差（秒）
     * @return 如"2小时前"
     */
    private static String toHoursAgo(long seconds) {
        return (seconds / 3600) + "小时前";
    }

    /**
     * 天级相对时间文本
     *
     * @param seconds 时间差（秒）
     * @return 如"5天前"
     */
    private static String toDaysAgo(long seconds) {
        return (seconds / 86400) + "天前";
    }

    /**
     * 月级相对时间文本
     *
     * @param months 月数
     * @return 如"3个月前"
     */
    private static String toMonthsAgo(int months) {
        return months + "个月前";
    }

    /**
     * 年级相对时间文本
     *
     * @param years 年数
     * @return 如"2年前"
     */
    private static String toYearsAgo(int years) {
        return years + "年前";
    }

}