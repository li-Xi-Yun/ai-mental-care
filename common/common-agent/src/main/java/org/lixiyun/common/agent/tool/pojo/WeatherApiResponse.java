package org.lixiyun.common.agent.tool.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 天气API响应实体（气象预警查询）
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Data
public class WeatherApiResponse {

    private Integer code;
    private String msg;
    private Integer page;
    private List<CityData> data;

    /**
     * 城市天气数据
     */
    @Data
    public static class CityData {
        /** 省级行政区，不带后缀 */
        private String sheng;
        /** 市级行政区，不带后缀 */
        private String shi;
        /** 地点 */
        private String name;
        /** 省级行政区，带后缀 */
        private String sheng2;
        /** 市级行政区，带后缀 */
        private String shi2;
        /** 区县级行政区或以下 */
        private String qu2;
        /** 区划代码 */
        private String code5;
        /** 预警数据集 */
        private List<Alarm> alarm;
    }

    /**
     * 预警信息
     */
    @Data
    public static class Alarm {
        /** 预警编号 */
        private String id;
        /** 预警标题 */
        private String title;
        /** 预警天气类别 */
        @JsonProperty("signaltype")
        private String signalType;
        /** 预警等级 */
        @JsonProperty("signallevel")
        private String signalLevel;
        /** 预警生效时间 */
        private String effective;
        /** 事件类型代码 */
        private String eventType;
        /** 预警等级（英文） */
        private String severity;
        /** 预警类型代码 */
        private String type;
    }

    /**
     * 生成供大模型阅读的自然语言提示词
     */
    public String toPromptText() {
        if (code == null || code != 200) {
            String errorMsg = msg != null ? msg : "未知错误";
            return "天气查询失败：" + errorMsg;
        }

        if (data == null || data.isEmpty()) {
            return "未查询到该地区的天气预警信息。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("天气预警查询结果，共查到").append(data.size()).append("个地区：\n");

        for (int i = 0; i < data.size(); i++) {
            CityData city = data.get(i);
            sb.append(i + 1).append(". ");

            String areaName = buildAreaName(city);
            sb.append(areaName);
            if (city.getCode5() != null) {
                sb.append("（区划代码：").append(city.getCode5()).append("）");
            }
            sb.append("\n");

            if (city.getAlarm() != null && !city.getAlarm().isEmpty()) {
                sb.append("   预警信息（共").append(city.getAlarm().size()).append("条）：\n");
                for (Alarm alarm : city.getAlarm()) {
                    sb.append("   - ");
                    if (alarm.getSignalType() != null && alarm.getSignalLevel() != null) {
                        sb.append("【").append(alarm.getSignalType())
                                .append(alarm.getSignalLevel()).append("预警】");
                    }
                    if (alarm.getTitle() != null) {
                        sb.append(alarm.getTitle());
                    }
                    if (alarm.getEffective() != null) {
                        sb.append("（生效时间：").append(alarm.getEffective()).append("）");
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("   暂无预警信息。\n");
            }
        }

        return sb.toString();
    }

    private String buildAreaName(CityData city) {
        String area = "";
        if (city.getSheng2() != null) {
            area += city.getSheng2();
        }
        if (city.getShi2() != null) {
            area += city.getShi2();
        }
        if (city.getQu2() != null) {
            area += city.getQu2();
        }
        if (area.isEmpty() && city.getName() != null) {
            area = city.getName();
        }
        return area;
    }
}