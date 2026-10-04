(function () {
  const chatContainer = document.getElementById('chatContainer');
  const messageInput = document.getElementById('messageInput');
  const sendBtn = document.getElementById('sendBtn');
  const wsStatus = document.getElementById('wsStatus');

  const ws = new ChatWebSocket();
  AppState.ws = ws;

  function addMessage(text, type) {
    const el = document.createElement('div');
    el.className = `message ${type}`;
    el.textContent = text;
    chatContainer.appendChild(el);
    scrollToBottom();
    return el;
  }

  function addSystemMessage(text) {
    const el = document.createElement('div');
    el.className = 'message system';
    el.textContent = text;
    chatContainer.appendChild(el);
    scrollToBottom();
  }

  function scrollToBottom() {
    chatContainer.scrollTop = chatContainer.scrollHeight;
  }

  /**
   * 确保当前会话已完成生命周期初始化（适配器 + 管道绑定）
   *
   * <p>新流程：文本消息统一走输入适配器（/adapter/text/send），
   * 发送前必须先通过 lifecycle/init 创建/绑定会话，否则适配器不存在。</p>
   */
  async function ensureTextConversation() {
    if (AppState.conversationId && AppState.lifecycleInitialized) return;

    // 同一会话进行模式切换（如语音 → 文本）：先销毁旧生命周期绑定，再按文本模式重新初始化
    if (AppState.conversationId && AppState.lifecycleBoundId
        && safeBigIntEqual(AppState.conversationId, AppState.lifecycleBoundId)) {
      try {
        await ChatAPI.endLifecycle(AppState.conversationId);
        AppState.fn.addSystemMessage('已释放旧会话资源');
      } catch (e) {
        AppState.fn.addSystemMessage('释放旧会话资源失败: ' + e.message);
      }
      AppState.lifecycleInitialized = false;
      AppState.lifecycleBoundId = null;
    }

    const data = await ChatAPI.initLifecycle(['TEXT'], ['TEXT'], AppState.conversationId || null);
    const newlyCreated = !AppState.conversationId;
    AppState.conversationId = String(data.conversationId);
    AppState.lifecycleInitialized = true;
    AppState.lifecycleBoundId = AppState.conversationId;

    if (newlyCreated) {
      AppState.fn.addSystemMessage(`新会话已创建，ID: ${AppState.conversationId}`);

      ws.subscribeTextReply(AppState.conversationId, onTextReply);
      ws.subscribeConversationName(AppState.conversationId, onConversationName);

      document.getElementById('emotionToggleBtn').disabled = false;
      document.getElementById('diagnosisPageBtn').disabled = false;

      AppState.fn.loadConversationList();
    }
  }

  function prependMessage(msg) {
    const type = (msg.type || '').toUpperCase();
    const el = document.createElement('div');
    if (type === 'USER') {
      el.className = 'message user';
      el.textContent = msg.content;
    } else if (type === 'ASSISTANT') {
      el.className = 'message ai';
      el.textContent = msg.content;
    } else if (type === 'SYSTEM' || type === 'THINKING') {
      el.className = 'message system';
      el.textContent = msg.content;
    } else {
      el.className = 'message system';
      el.textContent = `[${type}] ${msg.content}`;
    }
    chatContainer.insertBefore(el, chatContainer.firstChild);
  }

  async function loadOlderMessages() {
    if (AppState.chatLoadingOlder || AppState.chatAllLoaded) return;
    AppState.chatLoadingOlder = true;

    const loadingEl = document.createElement('div');
    loadingEl.className = 'message system';
    loadingEl.textContent = '加载更早的消息...';
    chatContainer.insertBefore(loadingEl, chatContainer.firstChild);

    AppState.chatPageNum++;
    try {
      const data = await ChatAPI.getDialogueMemory(AppState.conversationId, AppState.chatPageNum, AppState.chatPageSize);
      const list = data.records || [];

      if (chatContainer.contains(loadingEl)) {
        chatContainer.removeChild(loadingEl);
      }

      if (list.length === 0) {
        AppState.chatAllLoaded = true;
        AppState.chatPageNum--;
        return;
      }

      const prevScrollHeight = chatContainer.scrollHeight;

      for (let i = list.length - 1; i >= 0; i--) {
        prependMessage(list[i]);
      }

      const newScrollHeight = chatContainer.scrollHeight;
      chatContainer.scrollTop += (newScrollHeight - prevScrollHeight);

      if (list.length < AppState.chatPageSize || AppState.chatPageNum >= AppState.chatTotalPages) {
        AppState.chatAllLoaded = true;
      }
    } catch (error) {
      if (chatContainer.contains(loadingEl)) {
        chatContainer.removeChild(loadingEl);
      }
      AppState.chatPageNum--;
    } finally {
      AppState.chatLoadingOlder = false;
    }
  }

  chatContainer.addEventListener('scroll', () => {
    if (chatContainer.scrollTop < 50 && !AppState.chatLoadingOlder && !AppState.chatAllLoaded) {
      loadOlderMessages();
    }
  });

  async function sendMessage() {
    const text = messageInput.value.trim();
    if (!text || AppState.isSending) return;

    AppState.isSending = true;
    sendBtn.disabled = true;
    messageInput.value = '';

    addMessage(text, 'user');

    try {
      // 新流程：发送前确保会话已完成生命周期初始化（创建/绑定适配器与管道）
      await ensureTextConversation();

      const data = await ChatAPI.sendMessage(text, AppState.conversationId);

      if (!AppState.conversationId) {
        AppState.conversationId = String(data.conversationId);
        AppState.currentRound = data.currentRound;
        addSystemMessage(`新会话已创建，ID: ${AppState.conversationId}，轮次: ${AppState.currentRound}`);

        ws.subscribeTextReply(AppState.conversationId, onTextReply);
        ws.subscribeConversationName(AppState.conversationId, onConversationName);

        document.getElementById('emotionToggleBtn').disabled = false;
        document.getElementById('diagnosisPageBtn').disabled = false;

        AppState.fn.loadConversationList();
      } else {
        AppState.currentRound = data.currentRound;
        addSystemMessage(`轮次: ${AppState.currentRound}`);
      }

      AppState.currentAiText = '';
      AppState.currentAiMessageEl = document.createElement('div');
      AppState.currentAiMessageEl.className = 'message ai';
      AppState.currentAiMessageEl.innerHTML = '<span class="typing-indicator">AI正在思考</span>';
      chatContainer.appendChild(AppState.currentAiMessageEl);
      scrollToBottom();

    } catch (error) {
      addSystemMessage(`发送失败: ${error.message}`);
    } finally {
      AppState.isSending = false;
      sendBtn.disabled = false;
    }
  }

  function onTextReply(chunk) {
    if (!AppState.currentAiMessageEl) return;

    AppState.currentAiText += chunk;
    AppState.currentAiMessageEl.textContent = AppState.currentAiText;
    scrollToBottom();
    if (AppState.fn.onStreamChunk) AppState.fn.onStreamChunk(chunk);
  }

  function onConversationName(name) {
    addSystemMessage(`会话名称: ${name}`);
    AppState.fn.loadConversationList();
  }

  sendBtn.addEventListener('click', sendMessage);

  messageInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  });

  ws.onConnected = () => {
    wsStatus.textContent = '已连接';
    wsStatus.classList.add('connected');
    sendBtn.disabled = false;
  };

  ws.onDisconnected = () => {
    wsStatus.textContent = '已断开';
    wsStatus.classList.remove('connected');
    sendBtn.disabled = true;
  };

  ws.onError = (error) => {
    wsStatus.textContent = '连接错误';
    wsStatus.classList.remove('connected');
    sendBtn.disabled = true;
  };

  AppState.fn.addMessage = addMessage;
  AppState.fn.addSystemMessage = addSystemMessage;
  AppState.fn.scrollToBottom = scrollToBottom;
  AppState.fn.sendMessage = sendMessage;
  AppState.fn.onTextReply = onTextReply;
  AppState.fn.onConversationName = onConversationName;
})();