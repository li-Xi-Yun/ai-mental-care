package org.lixiyun.server.ai.model.factory;

import java.lang.annotation.*;

/**
 * @author lixiyun
 * @since 2026-08-15 17:42
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Deprecated
public @interface InjectChatModel {

    ChatModelType value();

}
