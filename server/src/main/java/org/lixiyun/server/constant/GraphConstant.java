package org.lixiyun.server.constant;

/**
 * @author lixiyun
 * @since 2026-03-16 18:05
 */
public interface GraphConstant {

    // ============================================这里是放入 config 的 context 对话级临时数据==================================================

    String AUDIO_DATA_EMOTION_RECOGNITION = "audio_data_emotion_recognition"; // 用于格式化情绪分析节点的额外输入，语音语气情绪分析，放入config的context中进行对话级的保存

    String AUDIO_TEXT_DATA = "audio_text_data"; // 存储文本模型生成的文本回复信息，用于后续的文本转语音，放入config的context中进行对话级的保存

    String INVALID_CONVERSATION_INFO = "invalid_conversation_info"; // 判断当前会话是否无效，用于检查点数据的清除或是会话上下文信息的清除，存放一个boolean变量

    String RAG_RESULT = "rag_result"; // 存储RAG节点的输出结果，一个String类型

    String RAG_INTERRUPTED = "rag_interrupted";  // 用于中断本次RAG流程，存放一个boolean变量，为true则中断RAG流程

    String DIAGNOSIS_INTERRUPTED = "diagnosis_interrupted";  // 用于中断本次诊断流程，存放一个boolean变量，为true则中断诊断流程


    // ============================================这里是放入 config 的 metadata 对话级元数据==================================================

    String CURRENT_ROUND = "current_round";  // 这个是标明当前对话的轮次

    String CONVERSATION_MESSAGES = "conversation_messages";  // 这里存储对话的上下文信息，一个ArrayList<Message>，目前只存储用户输出与最终模型回答用户的流式输出

    String USER_ID = "user_id";

    String FINAL_ANSWER_STREAM = "final_answer_stream"; // 用于给予FinalAnswerNode是否开启流式输出的标识，放于config的metadata中进行会话级的保存，存在则不开启流式输出

    String AUDIO_DATA = "audio_data"; // 用于存储用户输入的音频数据，放于config的metadata中进行会话级的保存

    String AUDIO_FLUX = "audio_flux"; // 用于发送 文本转语音模型 的 流式音频数据 到前端，在config存储的是一个 Flux<byte[]> 变量




    // ============================================这里是放入 state 的会话级数据==================================================

    String STREAM_FLAT = "stream_flat";  // 专门用于存储节点输出的流式结果到OverAllState中的，以便整合到图级别中，返回到前端，临时性数据标识

    String STREAM_RESULT = "stream_result";  // 专门用于存储节点流式输出的完整结果，以便在后续存入OverAllState（检查点）与上下文信息（DB）中

    String MESSAGES = "messages";  // 这里是OverAllState中的上下文信息，一个ArrayList<Message>

    String INPUT = "input";  // 用户输入,模型调用对应OverAllState中的常量属性：DEFAULT_INPUT_KEY


//    String USER_INPUT = "user_input";  // 用户输入，这个只用于上下文存储，不参与模型调用，模型调用对应的时OverAllState中的常量属性：DEFAULT_INPUT_KEY

//    String CONVERSATION_FIRST = "conversation_first";  // 这个是标明是否是会话的第一次对话，是，会在后续进行会话名称的创建

//    String END_NAME = "__END__";

//    String START_NAME = "__START__";

}