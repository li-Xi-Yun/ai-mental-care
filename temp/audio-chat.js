(function () {
  const chatContainer = document.getElementById('chatContainer');
  const recordBtn = document.getElementById('recordBtn');
  const recordBtnText = document.getElementById('recordBtnText');
  const recordStatus = document.getElementById('recordStatus');
  const volumeBar = document.getElementById('volumeBar');
  const wsStatus = document.getElementById('wsStatus');
  const interruptBtn = document.getElementById('interruptBtn');
  const textInput = document.getElementById('textInput');
  const sendTextBtn = document.getElementById('sendTextBtn');
  const autoTestToggle = document.getElementById('autoTestToggle');
  const simulatedVoiceToggle = document.getElementById('simulatedVoiceToggle');

  const ws = new ChatWebSocket();
  AppState.ws = ws;
  const audioSender = new AudioWebSocketSender(ws);
  const recorder = new AudioRecorder();

  let isRecording = false;
  let currentAsrText = '';

  let audioContext = null;
  let nextPlayTime = 0;
  const PCM_SAMPLE_RATE = 24000;

  let autoTestTimer = null;

  function getAudioContext() {
    if (!audioContext) {
      audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: PCM_SAMPLE_RATE });
    }
    if (audioContext.state === 'suspended') {
      audioContext.resume();
    }
    return audioContext;
  }

  function playPcmAudioChunk(uint8Data) {
    if (!uint8Data || uint8Data.byteLength < 2) return;

    try {
      const ctx = getAudioContext();
      const byteOffset = uint8Data.byteOffset || 0;
      const int16Length = Math.floor(uint8Data.byteLength / 2);
      const int16Data = new Int16Array(uint8Data.buffer, byteOffset, int16Length);
      const float32Data = new Float32Array(int16Data.length);
      for (let i = 0; i < int16Data.length; i++) {
        float32Data[i] = int16Data[i] / 32768.0;
      }

      const buffer = ctx.createBuffer(1, float32Data.length, ctx.sampleRate);
      buffer.getChannelData(0).set(float32Data);

      const source = ctx.createBufferSource();
      source.buffer = buffer;
      source.connect(ctx.destination);

      const now = ctx.currentTime;
      if (nextPlayTime < now) {
        nextPlayTime = now;
      }
      source.start(nextPlayTime);
      nextPlayTime += buffer.duration;
    } catch (e) {
      console.warn('播放音频chunk失败:', e);
    }
  }

  function resetAudioPlayback() {
    nextPlayTime = 0;
  }

  function speakSimulatedVoice(text) {
    if (!text || !window.speechSynthesis) return;

    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'zh-CN';
    utterance.rate = 1.0;
    utterance.pitch = 1.0;
    utterance.volume = 0.8;

    const voices = window.speechSynthesis.getVoices();
    const zhVoice = voices.find(v => v.lang.startsWith('zh'));
    if (zhVoice) {
      utterance.voice = zhVoice;
    }

    window.speechSynthesis.speak(utterance);
  }

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
      recordStatus.textContent = AppState.autoTestEnabled ? '自动测试中...' : '点击开始录音';
      interruptBtn.style.display = AppState.conversationId && !AppState.autoTestEnabled ? 'inline-block' : 'none';
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

  function enableInputs() {
    recordBtn.disabled = false;
    textInput.disabled = false;
    sendTextBtn.disabled = false;
    autoTestToggle.disabled = false;
    simulatedVoiceToggle.disabled = false;
  }

  async function ensureConversation() {
    if (!AppState.conversationId) {
      try {
        addSystemMessage('正在初始化语音会话...');
        const data = await ChatAPI.initAudioSession(null);
        AppState.conversationId = String(data.conversationId);
        addSystemMessage(`语音会话已创建，ID: ${AppState.conversationId}`);
        startAudioSubscriptions();
        interruptBtn.style.display = 'inline-block';
      } catch (error) {
        addSystemMessage(`初始化失败: ${error.message}`);
        throw error;
      }
    }
  }

  async function toggleRecording() {
    if (AppState.autoTestEnabled) {
      addSystemMessage('自动测试模式下不支持手动录音');
      return;
    }
    if (isRecording) {
      await stopRecording();
    } else {
      await startRecording();
    }
  }

  async function startRecording() {
    try {
      await ensureConversation();
    } catch (error) {
      return;
    }

    try {
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

  async function sendTextMessage() {
    const text = textInput.value.trim();
    if (!text) return;

    if (AppState.autoTestEnabled) {
      addSystemMessage('自动测试模式下不支持手动发送文本');
      return;
    }

    try {
      await ensureConversation();
    } catch (error) {
      return;
    }

    textInput.value = '';
    textInput.disabled = true;
    sendTextBtn.disabled = true;

    addMessage(text, 'user');
    recordStatus.textContent = '等待AI回复...';

    try {
      await ChatAPI.sendMessage(text, AppState.conversationId);
    } catch (error) {
      addSystemMessage(`发送文本消息失败: ${error.message}`);
      recordStatus.textContent = '发送失败';
      textInput.disabled = false;
      sendTextBtn.disabled = false;
    }
  }

  function startAudioSubscriptions() {
    if (!AppState.conversationId) return;

    ws.subscribeAudioReply(AppState.conversationId, onAudioReply);
    ws.subscribeAudioBinary(AppState.conversationId, onAudioBinary);
    ws.subscribeAudioTest(AppState.conversationId, onAudioTestBinary);
    ws.subscribeAsrIntermediate(AppState.conversationId, onAsrIntermediate);
    ws.subscribeConversationName(AppState.conversationId, onConversationName);
  }

  function onAsrIntermediate(text) {
    currentAsrText = text;
    addAsrMessage(text);
    recordStatus.textContent = '识别中...';
  }

  function onAudioReply(text) {
    if (text === '[TTS_COMPLETE]') {
      handleTtsComplete();
      return;
    }

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

    if (!AppState.autoTestEnabled) {
      recordStatus.textContent = 'AI回复中...';
    }
  }

  function onAudioBinary(message) {
    if (message && message._binaryBody && message._binaryBody.length > 0) {
      playPcmAudioChunk(message._binaryBody);
    }
  }

  function onAudioTestBinary(message) {
    if (message && message._binaryBody && message._binaryBody.length > 0) {
      playPcmAudioChunk(message._binaryBody);
    }
  }

  function onConversationName(name) {
    addSystemMessage(`会话名称: ${name}`);
  }

  function handleTtsComplete() {
    if (AppState.currentAiMessageEl) {
      AppState.currentAiMessageEl.dataset.complete = 'true';
    }
    AppState.currentAiMessageEl = null;

    if (!AppState.autoTestEnabled || !AppState.autoTestRunning) {
      if (!AppState.autoTestEnabled) {
        recordStatus.textContent = '点击开始录音';
        textInput.disabled = false;
        sendTextBtn.disabled = false;
      }
      return;
    }

    recordStatus.textContent = '自动测试: 准备下一轮...';

    if (autoTestTimer) {
      clearTimeout(autoTestTimer);
    }
    autoTestTimer = setTimeout(() => {
      executeAutoTestRound();
    }, 2500);
  }

  async function executeAutoTestRound() {
    if (!AppState.autoTestEnabled || !AppState.conversationId) {
      AppState.autoTestRunning = false;
      return;
    }

    AppState.autoTestRunning = true;
    resetAudioPlayback();

    try {
      recordStatus.textContent = '自动测试: 请求中...';
      const data = await ChatAPI.audioStreamTest(AppState.conversationId, null);

      if (data && data.assistantMessage) {
        addMessage(data.assistantMessage, 'ai');

        if (AppState.playSimulatedVoice) {
          speakSimulatedVoice(data.assistantMessage);
        }
      }

      recordStatus.textContent = '自动测试: AI生成中...';
    } catch (error) {
      addSystemMessage(`自动测试请求失败: ${error.message}`);
      AppState.autoTestRunning = false;
      recordStatus.textContent = '自动测试出错，已停止';
      autoTestToggle.checked = false;
      AppState.autoTestEnabled = false;
      setRecordButtonState(false);
    }
  }

  function startAutoTest() {
    if (!AppState.conversationId) {
      addSystemMessage('请先开始对话');
      autoTestToggle.checked = false;
      AppState.autoTestEnabled = false;
      return;
    }

    AppState.autoTestEnabled = true;
    AppState.autoTestRunning = false;
    addSystemMessage('自动测试已开启');
    recordStatus.textContent = '自动测试: 启动中...';

    isRecording = false;
    setRecordButtonState(false);
    textInput.value = '';
    textInput.disabled = true;
    sendTextBtn.disabled = true;

    if (autoTestTimer) {
      clearTimeout(autoTestTimer);
    }
    autoTestTimer = setTimeout(() => {
      executeAutoTestRound();
    }, 1000);
  }

  function stopAutoTest() {
    AppState.autoTestEnabled = false;
    AppState.autoTestRunning = false;

    if (autoTestTimer) {
      clearTimeout(autoTestTimer);
      autoTestTimer = null;
    }

    window.speechSynthesis && window.speechSynthesis.cancel();
    resetAudioPlayback();
    addSystemMessage('自动测试已关闭');
    recordStatus.textContent = '点击开始录音';
    textInput.disabled = false;
    sendTextBtn.disabled = false;
    setRecordButtonState(false);
  }

  autoTestToggle.addEventListener('change', () => {
    if (autoTestToggle.checked) {
      startAutoTest();
    } else {
      stopAutoTest();
    }
  });

  simulatedVoiceToggle.addEventListener('change', () => {
    AppState.playSimulatedVoice = simulatedVoiceToggle.checked;
    if (!simulatedVoiceToggle.checked) {
      window.speechSynthesis && window.speechSynthesis.cancel();
    }
  });

  async function handleInterrupt() {
    if (!AppState.conversationId) return;

    if (AppState.autoTestEnabled) {
      stopAutoTest();
      autoTestToggle.checked = false;
    }

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
      textInput.disabled = false;
      sendTextBtn.disabled = false;
      resetAudioPlayback();
    } catch (error) {
      addSystemMessage(`中断失败: ${error.message}`);
    } finally {
      interruptBtn.disabled = false;
    }
  }

  ws.onConnected = () => {
    wsStatus.textContent = '已连接';
    wsStatus.classList.add('connected');
    enableInputs();
    addSystemMessage('WebSocket连接成功，可以开始语音对话');
  };

  ws.onDisconnected = () => {
    wsStatus.textContent = '已断开';
    wsStatus.classList.remove('connected');
    recordBtn.disabled = true;
    textInput.disabled = true;
    sendTextBtn.disabled = true;
    autoTestToggle.disabled = true;
    simulatedVoiceToggle.disabled = true;
  };

  ws.onError = (error) => {
    wsStatus.textContent = '连接错误';
    wsStatus.classList.remove('connected');
    recordBtn.disabled = true;
    textInput.disabled = true;
    sendTextBtn.disabled = true;
    autoTestToggle.disabled = true;
    simulatedVoiceToggle.disabled = true;
  };

  recordBtn.addEventListener('click', toggleRecording);
  interruptBtn.addEventListener('click', handleInterrupt);

  document.addEventListener('keydown', (e) => {
    if (e.key === ' ' && e.target === document.body) {
      e.preventDefault();
      toggleRecording();
    }
    if (e.key === 'Enter' && document.activeElement === textInput) {
      e.preventDefault();
      sendTextMessage();
    }
  });

  sendTextBtn.addEventListener('click', sendTextMessage);

  AppState.currentAiText = '';
  AppState.currentAiMessageEl = null;
  AppState.fn.addMessage = addMessage;
  AppState.fn.addSystemMessage = addSystemMessage;
  AppState.fn.scrollToBottom = scrollToBottom;
})();