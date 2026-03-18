package org.lixiyun.server.constant;

/**
 * @author lixiyun
 * @since 2026-03-16 18:05
 */
public interface GraphConstant {

    String STREAM_RESULT = "stream_result";

    String CONVERSATION_MESSAGES = "conversation_messages";

    String MESSAGES = "messages";

    String USER_ID = "user_id";

    String USER_INPUT = "user_input";  // 用户输入，这个只用于上下文存储，不参与模型调用，模型调用对应的时OverAllState中的常量属性：DEFAULT_INPUT_KEY

    String CONVERSATION_FIRST = "conversation_first";
}
