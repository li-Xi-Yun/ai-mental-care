package org.lixiyun.common.agent.tool.tools;

import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tool.Tools;
import org.lixiyun.common.agent.tool.pojo.WeatherApiResponse;
import org.lixiyun.common.agent.tool.properties.WeatherProperties;
import org.lixiyun.common.core.utils.OkHttpUtil;
import org.lixiyun.common.json.utils.JsonUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天气工具类
 *
 * @author lixiyun
 * @date 2026/10/7 18:57
 */
@Slf4j
@Component(WeatherTool.NAME)
@RequiredArgsConstructor
public class WeatherTool implements Tools {

    public static final String NAME = "weatherTool";

    public static final String GET_CITY_WEATHER = "getCityWeather";

    private final WeatherProperties weatherProperties;

    @Tool(name = GET_CITY_WEATHER,
            description = "根据省份和城市名称查询该地区的天气预警信息，返回该地区当前的天气预警详情（如暴雨、大风、雷电等预警的等级和生效时间）")
    public String getCityWeather(
            @ToolParam(description = "省份名称，不带后缀") String province,
            @ToolParam(description = "城市名称，不带后缀") String city) {
        log.info("调用天气API查询对应城市的天气，省份：{}，城市：{}", province, city);

        OkHttpUtil okHttpUtil = new OkHttpUtil(weatherProperties.getApiUrl(), null, null, null);

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("id", weatherProperties.getId());
        queryParams.put("key", weatherProperties.getKey());
        queryParams.put("sheng", URLEncoder.encode(province, StandardCharsets.UTF_8));
        queryParams.put("place", URLEncoder.encode(city, StandardCharsets.UTF_8));

        try {
            JSONObject result = okHttpUtil.get("", queryParams);
            if (result == null || result.isEmpty()) {
                log.warn("天气API返回空结果，省份：{}，城市：{}", province, city);
                return "未查询到" + province + city + "的天气预警信息。";
            }

            String jsonStr = result.toJSONString();
            log.info("天气API响应：{}", jsonStr);

            WeatherApiResponse response = JsonUtils.parseObject(jsonStr, WeatherApiResponse.class);
            if (response == null) {
                log.error("天气API响应解析失败，jsonStr={}", jsonStr);
                return "天气数据解析失败，请稍后重试。";
            }

            String promptText = response.toPromptText();
            log.info("天气API查询成功，省份：{}，城市：{}", province, city);
            return promptText;
        } catch (Exception e) {
            log.error("天气API请求异常，省份：{}，城市：{}，异常：{}", province, city, e.getMessage(), e);
            return "查询" + province + city + "天气失败：" + e.getMessage();
        }
    }
}