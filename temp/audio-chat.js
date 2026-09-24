(function () {
  const chatContainer = document.getElementById('chatContainer');
  const recordBtn = document.getElementById('recordBtn');
  const recordBtnText = document.getElementById('recordBtnText');
  const recordStatus = document.getElementById('recordStatus');
  const volumeBar = document.getElementById('volumeBar');
  const wsStatus = document.getElementById('wsStatus');
  const interruptBtn = document.getElementById('interruptBtn');

  const ws = new ChatWebSocket();
  AppState.ws = ws;
  const audioSender = new AudioWebSocketSender(ws);
  const recorder = new AudioRecorder();

  let isRecording = false;
  let currentAsrText = '';

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

  function addAsrMessage(text) {
    const existing = document.getElementById('currentAsrMessage');
    if (existing) {
      existing.textContent = text;
    } else {
      const el = document.createElement('div');
      el.className = 'message user asr-pending';
      el.id = 'currentAsrMessage';
      el.textContent = text;
      chatContainer.appendChild(el);
    }
    scrollToBottom();
  }

  function finalizeAsrMessage() {
    const el = document.getElementById('currentAsrMessage');
    if (el) {
      el.removeAttribute('id');
      el.classList.remove('asr-pending');
    }
  }

  function scrollToBottom() {
    chatContainer.scrollTop = chatContainer.scrollHeight;
  }

  function setRecordButtonState(recording) {
    if (recording) {
      recordBtn.classList.add('recording');
      recordBtnText.textContent = '停止';
      recordStatus.textContent = '录音中...';
      interruptBtn.style.display = 'none';
    } else {
      recordBtn.classList.remove('recording');
      recordBtnText.textContent = '🎤';
      recordStatus.textContent = '点击开始录音';
      interruptBtn.style.display = AppState.conversationId ? 'inline-block' : 'none';
    }
  }

  function updateVolume(volume) {
    volumeBar.style.width = volume + '%';
    if (volume > 70) {
      volumeBar.style.background = '#ff4d4f';
    } else if (volume > 30) {
      volumeBar.style.background = '#faad14';
    } else {
      volumeBar.style.background = '#52c41a';
    }
  }

  async function toggleRecording() {
    if (isRecording) {
      await stopRecording();
    } else {
      await startRecording();
    }
  }

  async function startRecording() {
    if (!AppState.conversationId) {
      try {
        addSystemMessage('正在初始化语音会话...');
        const data = await ChatAPI.initAudioSession(null);
        AppState.conversationId = String(data.conversationId);
        addSystemMessage(`语音会话已创建，ID: ${AppState.conversationId}`);
      } catch (error) {
        addSystemMessage(`初始化失败: ${error.message}`);
        return;
      }
    }

    try {
      startAudioSubscriptions();

      recorder.onVolumeChange = updateVolume;
      recorder.onStateChange = (state) => {
        setRecordButtonState(state);
      };

      recorder.onDataAvailable = (chunk) => {
      };

      await recorder.start();
      isRecording = true;
      setRecordButtonState(true);
      recordStatus.textContent = '录音中...';
    } catch (error) {
      addSystemMessage(`录音启动失败: ${error.message}`);
    }
  }

  async function stopRecording() {
    recordStatus.textContent = '处理中...';
    isRecording = false;
    setRecordButtonState(false);

    try {
      const audioBase64 = await recorder.stop();
      if (!audioBase64) {
        addSystemMessage('录音数据为空');
        return;
      }

      currentAsrText = '';
      finalizeAsrMessage();

      audioSender.sendAudioMessage(audioBase64, AppState.conversationId);
      recordStatus.textContent = '等待识别结果...';
    } catch (error) {
      addSystemMessage(`发送语音失败: ${error.message}`);
      recordStatus.textContent = '发送失败';
    }
  }

  function startAudioSubscriptions() {
    if (!AppState.conversationId) return;

    ws.subscribeAudioReply(AppState.conversationId, onAudioReply);
    ws.subscribeAudioBinary(AppState.conversationId, onAudioBinary);
    ws.subscribeAsrIntermediate(AppState.conversationId, onAsrIntermediate);
    ws.subscribeConversationName(AppState.conversationId, onConversationName);
  }

  function onAsrIntermediate(text) {
    currentAsrText = text;
    addAsrMessage(text);
    recordStatus.textContent = '识别中...';
  }

  function onAudioReply(text) {
    if (!AppState.currentAiMessageEl || AppState.currentAiMessageEl.dataset.complete === 'true') {
      finalizeAsrMessage();
      AppState.currentAiMessageEl = document.createElement('div');
      AppState.currentAiMessageEl.className = 'message ai';
      AppState.currentAiMessageEl.dataset.complete = 'false';
      AppState.currentAiText = '';
      chatContainer.appendChild(AppState.currentAiMessageEl);
    }

    AppState.currentAiText += text;
    AppState.currentAiMessageEl.textContent = AppState.currentAiText;
    scrollToBottom();
    recordStatus.textContent = 'AI回复中...';
  }

  function onAudioBinary(message) {
    if (message && message._binaryBody && message._binaryBody.length > 0) {
      console.log('收到音频二进制数据:', message._binaryBody.length, '字节');
    }
  }

  function onConversationName(name) {
    addSystemMessage(`会话名称: ${name}`);
  }

  async function handleInterrupt() {
    if (!AppState.conversationId) return;

    interruptBtn.disabled = true;
    try {
      audioSender.sendInterrupt(AppState.conversationId);
      addSystemMessage('已发送中断指令');
      if (AppState.currentAiMessageEl) {
        AppState.currentAiMessageEl.dataset.complete = 'true';
      }
      AppState.currentAiMessageEl = null;
      recordStatus.textContent = '已中断，可重新录音';
      isRecording = false;
      setRecordButtonState(false);
    } catch (error) {
      addSystemMessage(`中断失败: ${error.message}`);
    } finally {
      interruptBtn.disabled = false;
    }
  }

  ws.onConnected = () => {
    wsStatus.textContent = '已连接';
    wsStatus.classList.add('connected');
    recordBtn.disabled = false;
    addSystemMessage('WebSocket连接成功，可以开始语音对话');
  };

  ws.onDisconnected = () => {
    wsStatus.textContent = '已断开';
    wsStatus.classList.remove('connected');
    recordBtn.disabled = true;
  };

  ws.onError = (error) => {
    wsStatus.textContent = '连接错误';
    wsStatus.classList.remove('connected');
    recordBtn.disabled = true;
  };

  recordBtn.addEventListener('click', toggleRecording);
  interruptBtn.addEventListener('click', handleInterrupt);

  document.addEventListener('keydown', (e) => {
    if (e.key === ' ' && e.target === document.body) {
      e.preventDefault();
      toggleRecording();
    }
  });

  AppState.currentAiText = '';
  AppState.currentAiMessageEl = null;
  AppState.fn.addMessage = addMessage;
  AppState.fn.addSystemMessage = addSystemMessage;
  AppState.fn.scrollToBottom = scrollToBottom;
})();