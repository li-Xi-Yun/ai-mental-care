/**
 * 音频WebSocket发送管理器
 * 负责通过STOMP协议发送音频帧与VAD停止信令（适配器 + 管道方案）
 */
class AudioWebSocketSender {
  #logger = window.__logger.getLogger('AudioWebSocket');
  constructor(wsClient) {
    this.ws = wsClient;
  }

  /**
   * 发送音频帧数据
   *
   * <p>目的地：{@code /app/audio/frame} → 适配器层 {@code AdapterController @MessageMapping("/audio/frame")}。</p>
   */
  sendAudioMessage(audioBase64, conversationId) {
    const body = {
      audioMessage: audioBase64,
      conversationId: conversationId || null
    };
    this.#logger.info('发送音频帧, conversationId:', conversationId, '数据大小:', audioBase64 ? audioBase64.length : 0);
    return this.ws.publish(CONFIG.websocket.stompSend.audioMessage, body);
  }

  /**
   * 发送VAD停止信令（前端VAD检测到静音）
   *
   * <p>目的地：{@code /app/audio/vad-stop} → 适配器层 {@code AdapterController @MessageMapping("/audio/vad-stop")}。</p>
   */
  sendStopSpeaking(conversationId) {
    if (!conversationId) {
      this.#logger.warn('无法发送停止说话信号：conversationId为空');
      return false;
    }
    const body = { conversationId: conversationId };
    this.#logger.info('发送VAD停止信号, conversationId:', conversationId);
    return this.ws.publish(CONFIG.websocket.stompSend.audioVadStop, body);
  }
}