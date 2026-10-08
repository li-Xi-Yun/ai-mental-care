package org.lixiyun.common.agent.tool.tools;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tool.Tools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 本地时间工具类
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Slf4j
@Component(LocalTimeTool.NAME)
public class LocalTimeTool implements Tools {

    public static final String NAME = "localTimeTool";

    public static final String GET_CURRENT_TIME = "getCurrentTime";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");

    @Tool(name = GET_CURRENT_TIME,
            description = "获取当前系统本地时间，返回包含日期、星期和时间的完整时间信息，用于需要知道当前时间的对话场景")
    public String getCurrentTime() {
        log.info("[本地时间工具] 查询当前系统时间");

        LocalDateTime now = LocalDateTime.now();
        String dateTime = now.format(FORMATTER);
        String weekDay = getChineseWeekDay(now.getDayOfWeek().getValue());

        String result = "当前时间是：" + dateTime + "，" + weekDay;
        log.info("[本地时间工具] 当前时间：{}", result);
        return result;
    }

    private String getChineseWeekDay(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> "";
        };
    }
}