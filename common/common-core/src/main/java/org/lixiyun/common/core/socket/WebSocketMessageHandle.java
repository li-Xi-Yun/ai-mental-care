package org.lixiyun.common.core.socket;

/**
 * @author lixiyun
 * @since 2026-03-10 22:40
 */
public interface WebSocketMessageHandle {

    /**
     * 处理各种WebSocket消息，所有实现类都需要自定义Bean名称，要使用枚举类中的名称
     * @param userId 用户名
     * @param data 消息对应的数据
     */
    void handle(Long userId, Object data);

    /**
     * 默认处理方法，根据前端传来的消息名称，调用对应的处理方法
     * @param userId 用户ID
     * @param data 消息对应数据
     * @param messageName 消息名称
     */
    static void handle(Long userId, Object data, String messageName){
//        WebSocketMessageFactory factory = SpringUtil.getBean(WebSocketMessageFactory.class);
//        factory.getMessageHandle(messageName).handle(userId, data);
    }
}
