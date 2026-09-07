class ChatWebSocket {
  constructor() {
    this.client = null;
    this.connected = false;
    this.subscriptions = {};
    this.onConnected = null;
    this.onDisconnected = null;
    this.onError = null;
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.client = new StompJs.Client({
        webSocketFactory: () => new SockJS(`${CONFIG.server.wsUrl}${CONFIG.websocket.endpoint}`),
        connectHeaders: {
          token: CONFIG.auth.token
        },
        reconnectDelay: 0,
        debug: (str) => {
          console.log('[STOMP]', str);
        },
        onConnect: (frame) => {
          this.connected = true;
          console.log('WebSocket连接成功:', frame);
          if (this.onConnected) this.onConnected(frame);
          resolve(frame);
        },
        onStompError: (frame) => {
          this.connected = false;
          console.error('WebSocket STOMP错误:', frame);
          if (this.onError) this.onError(frame);
          reject(frame);
        },
        onWebSocketClose: (evt) => {
          this.connected = false;
          console.log('WebSocket连接关闭:', evt);
          if (this.onDisconnected) this.onDisconnected(evt);
        }
      });

      this.client.activate();
    });
  }

  subscribeTextReply(conversationId, onMessage) {
    const path = CONFIG.websocket.getTextReplyPath(conversationId);
    console.log('订阅文本回复:', path);

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
    console.log('订阅会话名称:', path);

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
      console.log('WebSocket已断开');
    }
  }
}