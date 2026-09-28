class ChatWebSocket {
  #logger = window.__logger.getLogger('ChatWebSocket');

  constructor() {
    this.client = null;
    this.connected = false;
    this.subscriptions = {};
    this.onConnected = null;
    this.onDisconnected = null;
    this.onError = null;
  }

  async connect(useNative = false) {
    let ticket = null;
    if (useNative) {
      try {
        ticket = await ChatAPI.getWsTicket();
        this.#logger.info('获取WebSocket一次性ticket成功');
      } catch (error) {
        throw new Error(`获取WebSocket连接凭证失败: ${error.message}`);
      }
    }

    return new Promise((resolve, reject) => {
      const webSocketUrl = useNative
        ? `${CONFIG.server.wsBinaryUrl}${CONFIG.websocket.nativeEndpoint}?wsTicket=${ticket}`
        : `${CONFIG.server.wsUrl}${CONFIG.websocket.endpoint}`;

      const factory = useNative
        ? () => new WebSocket(webSocketUrl)
        : () => new SockJS(webSocketUrl);

      this.client = new StompJs.Client({
        webSocketFactory: factory,
        connectHeaders: {
          token: CONFIG.auth.token
        },
        reconnectDelay: 0,
        debug: (str) => {
          this.#logger.debug('[STOMP]', str);
        },
        onConnect: (frame) => {
          this.connected = true;
          this.#logger.info('WebSocket连接成功');
          if (this.onConnected) this.onConnected(frame);
          resolve(frame);
        },
        onStompError: (frame) => {
          this.connected = false;
          this.#logger.error('WebSocket STOMP错误', frame);
          if (this.onError) this.onError(frame);
          reject(frame);
        },
        onWebSocketClose: (evt) => {
          this.connected = false;
          this.#logger.info('WebSocket连接关闭');
          if (this.onDisconnected) this.onDisconnected(evt);
        }
      });

      this.client.activate();
    });
  }

  subscribeTextReply(conversationId, onMessage) {
    const path = CONFIG.websocket.getTextReplyPath(conversationId);
    this.#logger.info('订阅文本回复:', path);

    if (this.subscriptions[path]) {
      this.subscriptions[path].unsubscribe();
    }

    this.subscriptions[path] = this.client.subscribe(path, (message) => {
      if (onMessage) onMessage(message.body);
    });

    return path;
  }

  subscribeConversationName(conversationId, onMessage) {
    const path = CONFIG.websocket.getConversationNamePath(conversationId);
    this.#logger.info('订阅会话名称:', path);

    if (this.subscriptions[path]) {
      this.subscriptions[path].unsubscribe();
    }

    this.subscriptions[path] = this.client.subscribe(path, (message) => {
      if (onMessage) onMessage(message.body);
    });

    return path;
  }

  subscribeAudioReply(conversationId, onMessage) {
    const path = CONFIG.websocket.getAudioReplyPath(conversationId);
    this.#logger.info('订阅音频文字流:', path);

    if (this.subscriptions[path]) {
      this.subscriptions[path].unsubscribe();
    }

    this.subscriptions[path] = this.client.subscribe(path, (message) => {
      if (onMessage) onMessage(message.body);
    });

    return path;
  }

  subscribeAudioBinary(conversationId, onMessage) {
    const path = CONFIG.websocket.getAudioBinaryPath(conversationId);
    this.#logger.info('订阅音频二进制流:', path);

    if (this.subscriptions[path]) {
      this.subscriptions[path].unsubscribe();
    }

    this.subscriptions[path] = this.client.subscribe(path, (message) => {
      if (onMessage) onMessage(message);
    });

    return path;
  }

  subscribeAudioTest(conversationId, onMessage) {
    const path = CONFIG.websocket.getAudioTestPath(conversationId);
    this.#logger.info('订阅测试音频流:', path);

    if (this.subscriptions[path]) {
      this.subscriptions[path].unsubscribe();
    }

    this.subscriptions[path] = this.client.subscribe(path, (message) => {
      if (onMessage) onMessage(message);
    });

    return path;
  }

  subscribeAsrIntermediate(conversationId, onMessage) {
    const path = CONFIG.websocket.getAsrIntermediatePath(conversationId);
    this.#logger.info('订阅ASR中间结果:', path);

    if (this.subscriptions[path]) {
      this.subscriptions[path].unsubscribe();
    }

    this.subscriptions[path] = this.client.subscribe(path, (message) => {
      if (onMessage) onMessage(message.body);
    });

    return path;
  }

  unsubscribeAll() {
    Object.values(this.subscriptions).forEach(sub => {
      if (sub) sub.unsubscribe();
    });
    this.subscriptions = {};
  }

  disconnect() {
    if (this.client && this.connected) {
      this.unsubscribeAll();
      this.client.deactivate();
      this.connected = false;
      this.#logger.info('WebSocket已断开');
    }
  }

  publish(destination, body) {
    if (!this.client || !this.connected) {
      this.#logger.error('WebSocket未连接，无法发送消息');
      return false;
    }
    this.client.publish({ destination, body: typeof body === 'string' ? body : JSON.stringify(body) });
    return true;
  }
}