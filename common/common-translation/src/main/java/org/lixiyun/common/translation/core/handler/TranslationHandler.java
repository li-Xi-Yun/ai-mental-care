package org.lixiyun.common.translation.core.handler;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.utils.reflect.ReflectUtils;
import org.lixiyun.common.translation.annotation.Translation;
import org.lixiyun.common.translation.core.TranslationInterface;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译处理器
 *
 * @author lixiyun
 */
@Slf4j
public class TranslationHandler extends JsonSerializer<Object> implements ContextualSerializer {

    /**
     * 全局翻译实现类映射器
     */
    public static final Map<String, TranslationInterface<?>> TRANSLATION_MAPPER = new ConcurrentHashMap<>();

    private Translation translation;

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        TranslationInterface<?> trans = TRANSLATION_MAPPER.get(translation.type());
        if (ObjectUtil.isNotNull(trans)) {
            // 如果映射字段不为空 则取映射字段的值
            if (StrUtil.isNotBlank(translation.mapper())) {
                value = ReflectUtils.invokeGetter(gen.getCurrentValue(), translation.mapper());
            }
            // 如果为 null 直接写出
            if (ObjectUtil.isNull(value)) {
                gen.writeNull();
                return;
            }
            Object result = trans.translation(value, translation.other());
            gen.writeObject(result);
        } else {
            gen.writeObject(value);
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        // 先调用这个方法后，再调用 serialize 方法
        Translation translation = property.getAnnotation(Translation.class);
        if (Objects.nonNull(translation)) {
            this.translation = translation;
            // 返回当前的 TranslationHandler 实例
            return this;
        }
        // 返回值决定了字段将使用哪个序列化器进行处理。
        return prov.findValueSerializer(property.getType(), property);
    }
}
