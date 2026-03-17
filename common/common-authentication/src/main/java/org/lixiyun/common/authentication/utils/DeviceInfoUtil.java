package org.lixiyun.common.authentication.utils;


import jakarta.servlet.http.HttpServletRequest;
import org.lixiyun.common.core.utils.ServletUtils;

public class DeviceInfoUtil {

    public static String getOperatingSystem() {
        HttpServletRequest request = ServletUtils.getRequest();
        // 获取操作系统信息
        String userAgent = request.getHeader("User-Agent");
        String os = "Unknown";
        if (userAgent.toLowerCase().contains("windows")) {
            os = "Windows";
        } else if (userAgent.toLowerCase().contains("mac")) {
            os = "Mac";
        } else if (userAgent.toLowerCase().contains("linux")) {
            os = "Linux";
        } else if (userAgent.toLowerCase().contains("android")) {
            os = "Android";
        } else if (userAgent.toLowerCase().contains("iphone")) {
            os = "iOS";
        }
        return os;
    }

    public static String getBrowser(){
        HttpServletRequest request = ServletUtils.getRequest();
        String userAgent = request.getHeader("User-Agent");
        // 获取浏览器信息
        String browser = "Unknown";
        if (userAgent.toLowerCase().contains("msie")) {
            browser = "Internet Explorer";
        } else if (userAgent.toLowerCase().contains("firefox")) {
            browser = "Firefox";
        } else if (userAgent.toLowerCase().contains("chrome")) {
            browser = "Chrome";
        } else if (userAgent.toLowerCase().contains("safari")) {
            browser = "Safari";
        }
        return browser;
    }

    public static Integer getDeviceType() {
        HttpServletRequest request = ServletUtils.getRequest();
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            return -1; // 无法判断
        }

        // 统一转为小写以提高匹配准确性
        String lowerCaseUserAgent = userAgent.toLowerCase();

        if (lowerCaseUserAgent.contains("mobile") ||
                lowerCaseUserAgent.contains("android") ||
                lowerCaseUserAgent.contains("iphone")) {
            return 0; // 手机
        } else if (lowerCaseUserAgent.contains("ipad") ||
                lowerCaseUserAgent.contains("tablet") ||
                (lowerCaseUserAgent.contains("android") && !lowerCaseUserAgent.contains("mobile"))) { // 注意一些Android平板可能不包含"Mobile"
            return 2; // 平板
        } else if (lowerCaseUserAgent.contains("windows nt") ||
                lowerCaseUserAgent.contains("macintosh") ||
                lowerCaseUserAgent.contains("linux")) {
            return 1; // 电脑
        } else {
            return -1; // 未知设备
        }
    }

}