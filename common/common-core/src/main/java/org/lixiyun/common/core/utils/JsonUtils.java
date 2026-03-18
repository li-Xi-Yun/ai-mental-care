package org.lixiyun.common.core.utils;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = SpringUtil.getBean(ObjectMapper.class);

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    public static String toJsonString(Object object) throws JsonProcessingException {
        if (ObjectUtil.isNull(object)) {
            return null;
        }
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    public static <T> T parseObject(String text, Class<T> clazz) throws JsonProcessingException {
        if (StrUtil.isEmpty(text)) {
            return null;
        }
        return OBJECT_MAPPER.readValue(text, clazz);
    }
}