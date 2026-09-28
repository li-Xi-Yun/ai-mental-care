/**
 * 音频WebSocket发送管理器
 * 负责通过STOMP协议发送音频消息和中断指令
 */
class AudioWebSocketSender {
  #logger = window.__logger.getLogger('AudioWebSocket');
  constructor(wsClient) {
    this.ws = wsClient;
  }

  sendAudioMessage(audioBase64, conversationId) {
    const body = {
      audioMessage: audioBase64,
      conversationId: conversationId || null
    };
    this.#logger.info('发送音频消息, conversationId:', conversationId, '数据大小:', audioBase64 ? audioBase64.length : 0);
    return this.ws.publish(CONFIG.websocket.stompSend.audioMessage, body);
  }

  sendInterrupt(conversationId) {
    if (!conversationId) {
      this.#logger.warn('无法中断：conversationId为空');
      return false;
    }
    const body = { conversationId: conversationId };
    this.#logger.info('发送中断指令, conversationId:', conversationId);
    return this.ws.publish(CONFIG.websocket.stompSend.audioInterrupt, body);
  }
}