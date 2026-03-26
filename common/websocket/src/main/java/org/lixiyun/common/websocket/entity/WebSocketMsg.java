package org.lixiyun.common.websocket.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-03-24 12:45
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMsg<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    // 注释的字段是用于消息可靠性处理，暂不实现

    private Long msgId;  // 消息ID

    private String msgType; // 类型标识

    private T data; // 业务内容（不同类型传不同对象）

    private byte[] binaryData; // 二进制数据，文本消息不用

//    private LocalDateTime createTime;  // 发送时间

}
