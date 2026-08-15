package org.lixiyun.server.service.impl.common;

import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.properties.WeatherProperties;
import org.lixiyun.common.core.service.WeatherService;
import org.lixiyun.common.core.utils.OkHttpUtil;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private final WeatherProperties weatherProperties;

    @Override
    public String getCityWeather(String province, String city) {
        log.info("调用天气API查询对应城市的天气，省份：{}，城市：{}", province, city);

        // 构建 OkHttpUtil，将 apiUrl 作为 apiHost
        OkHttpUtil okHttpUtil = new OkHttpUtil(weatherProperties.getApiUrl(), null, null, null);

        // 构建查询参数
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("id", weatherProperties.getId());
        queryParams.put("key", weatherProperties.getKey());
        queryParams.put("sheng", URLEncoder.encode(province, StandardCharsets.UTF_8));
        queryParams.put("place", URLEncoder.encode(city, StandardCharsets.UTF_8));

        try {
            // 发送 GET 请求，url 传空字符串（apiHost 已包含完整地址）
            JSONObject result = okHttpUtil.get("", queryParams);
            if (result != null) {
                String response = result.toJSONString();
                log.info("API响应：{}", response);
                return response;
            }
        } catch (Exception e) {
            log.error("天气API请求异常：{}", e.getMessage(), e);
        }
        return "查询失败";
    }
}
