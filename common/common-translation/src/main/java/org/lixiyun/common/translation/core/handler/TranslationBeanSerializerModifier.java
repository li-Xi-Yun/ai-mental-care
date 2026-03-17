package org.lixiyun.common.translation.core.handler;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * Bean 序列化修改器 解决 Null 被单独处理问题
 *
 * @author lixiyun
 */
public class TranslationBeanSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            // 如果序列化器为 TranslationHandler 的话 将 Null 值也交给他处理
            if (writer.getSerializer() instanceof TranslationHandler serializer) {
                // 默认情况下，Jackson对于null值的处理是：
                // 直接输出null或者根据配置忽略该字段
                // 不会调用自定义的TranslationHandler
                writer.assignNullSerializer(serializer);
            }
        }
        return beanProperties;
    }

}
