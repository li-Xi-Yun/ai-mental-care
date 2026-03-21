package org.lixiyun.server.constant;

/**
 * @author lixiyun
 * @since 2026-03-16 18:05
 */
public interface GraphConstant {

    String STREAM_FLAT = "stream_flat";  // 专门用于存储节点输出的流式结果到OverAllState中的，以便整合到图级别中，返回到前端

    String STREAM_RESULT = "stream_result";  // 专门用于存储节点流式输出的完整结果，以便在后续存入OverAllState（检查点）与上下文信息（DB）中

    String CONVERSATION_MESSAGES = "conversation_messages";  // 这里存储对话的上下文信息，一个ArrayList<Message>，目前只存储用户输出与最终模型回答用户的流式输出

    String MESSAGES = "messages";  // 这里是OverAllState中的上下文信息，一个ArrayList<Message>

    String USER_ID = "user_id";

    String USER_INPUT = "user_input";  // 用户输入，这个只用于上下文存储，不参与模型调用，模型调用对应的时OverAllState中的常量属性：DEFAULT_INPUT_KEY

    String CONVERSATION_FIRST = "conversation_first";  // 这个是标明是否是会话的第一次对话，是，会在后续进行会话名称的创建

    String CURRENT_ROUND = "current_round";  // 这个是标明当前对话的轮次
}
