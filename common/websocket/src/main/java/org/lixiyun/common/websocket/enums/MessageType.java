package org.lixiyun.common.websocket.enums;

import lombok.Getter;

/**
 * @author lixiyun
 * @since 2026-03-10 20:49
 */
public enum MessageType {

    // 基本上这里的所有实例都需要有一个对应的处理器：WebSocketMessageHandle接口
    // 如果需要切换实例中的名称，则需要修改对应的处理器的Bean名称
    // 有些可以不要，比如发送到前端的标识

    ERROR("error"),
    CLOSE("close"),


    PING("ping"),
    PONG("pong"),
    TEXT("text"),
    BINARY("binary"),

    ACK("ack"),
    NACK("nAck"),


    AUDIO_START("audioStart"),
    AUDIO_DATA("audioData"),
    AUDIO_MODEL("audioModel"),
    AUDIO_STOP("audioStop"),

    AUDIO_CONCURRENT_ERROR("audioConcurrentError"),
    AUDIO_STREAM_RESULT("audioStreamResult"),
    AUDIO_STREAM_FINISH("audioStreamFinish"),
    AUDIO_STREAM_INTERRUPT("audioStreamInterrupt"),







    ;

    @Getter
    final String name;

    MessageType(String name) {
        this.name = name;
    }

}
