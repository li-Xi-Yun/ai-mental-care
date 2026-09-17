package org.lixiyun.server.ai.model.factory;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author lixiyun
 * @since 2026-08-15 17:42
 */
@Component
@RequiredArgsConstructor
@Deprecated
public class ChatModelInjectProcessor implements InstantiationAwareBeanPostProcessor {

    private final ChatModelFactory chatModelFactory;

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
        Field[] fields = bean.getClass().getDeclaredFields();
        for (Field field : fields) {
            InjectChatModel anno = field.getAnnotation(InjectChatModel.class);
            if (anno == null || !ChatModel.class.isAssignableFrom(field.getType())) {
                continue;
            }

            ChatModelType modelType = anno.value();
            String methodName = modelType.getterMethod;
            try {
                Method getter = ChatModelFactory.class.getDeclaredMethod(methodName);
                ChatModel chatModel = (ChatModel) getter.invoke(chatModelFactory);

                field.setAccessible(true);
                field.set(bean, chatModel);
            } catch (Exception e) {
                throw new RuntimeException("ChatModel注入失败，类型：" + modelType + "，预期方法：" + methodName, e);
            }
        }
        return pvs;
    }
}
