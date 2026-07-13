package org.lixiyun.common.core.service;

public interface WeatherService {

    /**
     * 根据省份和城市获取天气信息
     * @param province 省份
     * @param city 城市
     * @return
     */
    String getCityWeather(String province, String city);

}