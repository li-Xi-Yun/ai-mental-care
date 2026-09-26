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

  // 测试音频收集器：用于缓存从测试接口WS收到的PCM音频chunk
  let testAudioCollector = null;

  // AI回复完成检测：debounce定时器，无新数据到达则判定AI回复结束
  let aiReplyCompleteTimer = null;
  const AI_REPLY_IDLE_MS = 2000;

  // ==================== 音频播放相关 ====================

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

  // ==================== PCM → WAV → Base64 转换 ====================

  function writeString(view, offset, str) {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i));
    }
  }

  function addWavHeader(pcmData, sampleRate) {
    const numChannels = 1;
    const bitsPerSample = 16;
    const byteRate = sampleRate * numChannels * bitsPerSample / 8;
    const blockAlign = numChannels * bitsPerSample / 8;
    const dataSize = pcmData.length;
    const headerSize = 44;
    const totalSize = headerSize + dataSize;

    const buffer = new ArrayBuffer(totalSize);
    const view = new DataView(buffer);

    writeString(view, 0, 'RIFF');
    view.setUint32(4, totalSize - 8, true);
    writeString(view, 8, 'WAVE');

    writeString(view, 12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, numChannels, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, byteRate, true);
    view.setUint16(32, blockAlign, true);
    view.setUint16(34, bitsPerSample, true);

    writeString(view, 36, 'data');
    view.setUint32(40, dataSize, true);

    const uint8View = new Uint8Array(buffer);
    uint8View.set(pcmData, headerSize);

    return uint8View;
  }

  function arrayBufferToBase64(buffer) {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  function assemblePcmToWavBase64(pcmChunks, sampleRate) {
    let totalLength = 0;
    pcmChunks.forEach(function (chunk) { totalLength += chunk.length; });

    const pcmData = new Uint8Array(totalLength);
    let offset = 0;
    pcmChunks.forEach(function (chunk) {
      pcmData.set(chunk, offset);
      offset += chunk.length;
    });

    const wavData = addWavHeader(pcmData, sampleRate);
    return arrayBufferToBase64(wavData);
  }

  // ==================== 测试音频收集器 ====================

  function createTestAudioCollector() {
    return new Promise(function (resolve, reject) {
      testAudioCollector = {
        chunks: [],
        resolve: resolve,
        reject: reject
      };
      setTimeout(function () {
        if (testAudioCollector) {
          var err = new Error('语音合成超时');
          testAudioCollector.reject(err);
          testAudioCollector = null;
        }
      }, 60000);
    });
  }

  // ==================== AI回复完成检测 ====================

  function resetAiReplyCompleteTimer() {
    if (aiReplyCompleteTimer) {
      clearTimeout(aiReplyCompleteTimer);
    }
    aiReplyCompleteTimer = setTimeout(function () {
      handleAiReplyComplete();
    }, AI_REPLY_IDLE_MS);
  }

  function handleAiReplyComplete() {
    aiReplyCompleteTimer = null;
    if (AppState.currentAiMessageEl) {
      AppState.currentAiMessageEl.dataset.complete = 'true';
    }
    AppState.currentAiMessageEl = null;

    if (AppState.autoTestEnabled && AppState.autoTestRunning) {
      recordStatus.textContent = '自动测试: 准备下一轮...';
      if (autoTestTimer) {
        clearTimeout(autoTestTimer);
      }
      autoTestTimer = setTimeout(function () {
        executeAutoTestRound();
      }, 2500);
    } else {
      recordStatus.textContent = '点击开始录音';
      textInput.disabled = false;
      sendTextBtn.disabled = false;
    }
  }

  function cancelAiReplyCompleteTimer() {
    if (aiReplyCompleteTimer) {
      clearTimeout(aiReplyCompleteTimer);
      aiReplyCompleteTimer = null;
    }
  }

  // ==================== 消息展示 ====================

  function addMessage(text, type) {
    const el = document.createElement('div');
    el.className = 'message ' + type;
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
      recordBtnText.textContent = '\u{1F3A4}';
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

  // ==================== 会话初始化 ====================

  async function ensureConversation() {
    if (!AppState.conversationId) {
      try {
        addSystemMessage('正在初始化语音会话...');
        const data = await ChatAPI.initAudioSession(null);
        AppState.conversationId = String(data.conversationId);
        addSystemMessage('语音会话已创建，ID: ' + AppState.conversationId);
        startAudioSubscriptions();
        interruptBtn.style.display = 'inline-block';
      } catch (error) {
        addSystemMessage('初始化失败: ' + error.message);
        throw error;
      }
    }
  }

  // ==================== 录音相关 ====================

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
      recorder.onStateChange = function (state) {
        setRecordButtonState(state);
      };

      recorder.onDataAvailable = function (chunk) {
      };

      await recorder.start();
      isRecording = true;
      setRecordButtonState(true);
      recordStatus.textContent = '录音中...';
    } catch (error) {
      addSystemMessage('录音启动失败: ' + error.message);
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

      // 直接发送到 AudioController 进行语音对话
      audioSender.sendAudioMessage(audioBase64, AppState.conversationId);
      recordStatus.textContent = '等待识别结果...';
    } catch (error) {
      addSystemMessage('发送语音失败: ' + error.message);
      recordStatus.textContent = '发送失败';
    }
  }

  // ==================== 文本发送（新流程：文字→测试接口TTS→收集音频→发AudioController） ====================

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
    recordStatus.textContent = '正在合成语音...';

    // 创建收集器，在调用API之前（WS音频chunk可能在HTTP返回前就开始到达）
    const collectPromise = createTestAudioCollector();

    try {
      resetAudioPlayback();

      // 调用测试接口 将文字转为语音
      const data = await ChatAPI.audioStreamTest(AppState.conversationId, text);
      console.log('文字转语音API返回:', data);

      // 等待WS音频chunk全部收集完成（由[TTS_COMPLETE]信号触发resolve）
      const chunks = await collectPromise;

      if (!chunks || chunks.length === 0) {
        throw new Error('语音合成为空');
      }

      // 将PCM chunk拼成WAV 再转base64
      const audioBase64 = assemblePcmToWavBase64(chunks, PCM_SAMPLE_RATE);

      // 如果开启了模拟语音 用浏览器TTS朗读原文本
      if (AppState.playSimulatedVoice) {
        speakSimulatedVoice(text);
      }

      // 发送到 AudioController 走语音对话流水线
      audioSender.sendAudioMessage(audioBase64, AppState.conversationId);
      recordStatus.textContent = '等待AI回复...';

    } catch (error) {
      addSystemMessage('发送失败: ' + error.message);
      recordStatus.textContent = '发送失败';
      textInput.disabled = false;
      sendTextBtn.disabled = false;
      testAudioCollector = null;
      cancelAiReplyCompleteTimer();
    }
  }

  // ==================== WebSocket 订阅 ====================

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

  // ==================== WebSocket 消息处理 ====================

  function onAudioReply(text) {
    // 来自测试控制器的 TTS完成信号 → 解析收集器Promise
    if (text === '[TTS_COMPLETE]') {
      if (testAudioCollector) {
        const collector = testAudioCollector;
        testAudioCollector = null;
        collector.resolve(collector.chunks);
      }
      return;
    }

    // 来自 AudioController 的 AI回复文字流
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

    // 收到新文字chunk 重置AI回复完成计时器
    resetAiReplyCompleteTimer();

    if (!AppState.autoTestEnabled) {
      recordStatus.textContent = 'AI回复中...';
    }
  }

  function onAudioBinary(message) {
    if (message && message._binaryBody && message._binaryBody.length > 0) {
      playPcmAudioChunk(message._binaryBody);
      // 收到新音频chunk 也重置AI回复完成计时器
      resetAiReplyCompleteTimer();
    }
  }

  function onAudioTestBinary(message) {
    if (message && message._binaryBody && message._binaryBody.length > 0) {
      const chunkCopy = new Uint8Array(message._binaryBody);
      // 如果收集器活跃 缓存chunk
      if (testAudioCollector) {
        testAudioCollector.chunks.push(chunkCopy);
      }
      // 实时播放
      playPcmAudioChunk(message._binaryBody);
    }
  }

  function onConversationName(name) {
    addSystemMessage('会话名称: ' + name);
  }

  // ==================== 自动测试 ====================

  async function executeAutoTestRound() {
    if (!AppState.autoTestEnabled || !AppState.conversationId) {
      AppState.autoTestRunning = false;
      return;
    }

    AppState.autoTestRunning = true;
    resetAudioPlayback();

    // 创建收集器（在API调用之前）
    const collectPromise = createTestAudioCollector();

    try {
      recordStatus.textContent = '自动测试: 请求中...';

      // 调用测试接口 message=null → LLM扮演用户生成文本 → TTS合成语音
      const data = await ChatAPI.audioStreamTest(AppState.conversationId, null);

      // 展示LLM生成的用户说的话
      if (data && data.assistantMessage) {
        addMessage(data.assistantMessage, 'ai');
      }

      // 等待WS音频chunk收集完成
      const chunks = await collectPromise;

      if (!chunks || chunks.length === 0) {
        throw new Error('自动测试语音合成为空');
      }

      // 拼成WAV → base64
      const audioBase64 = assemblePcmToWavBase64(chunks, PCM_SAMPLE_RATE);

      // 模拟语音开关开启时 浏览器朗读生成的文本
      if (AppState.playSimulatedVoice && data && data.assistantMessage) {
        speakSimulatedVoice(data.assistantMessage);
      }

      // 发送到 AudioController 走语音对话流水线
      audioSender.sendAudioMessage(audioBase64, AppState.conversationId);
      recordStatus.textContent = '自动测试: AI回复中...';

    } catch (error) {
      addSystemMessage('自动测试请求失败: ' + error.message);
      AppState.autoTestRunning = false;
      recordStatus.textContent = '自动测试出错，已停止';
      autoTestToggle.checked = false;
      AppState.autoTestEnabled = false;
      setRecordButtonState(false);
      testAudioCollector = null;
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
    resetAudioPlayback();

    if (autoTestTimer) {
      clearTimeout(autoTestTimer);
    }
    autoTestTimer = setTimeout(function () {
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

    testAudioCollector = null;
    cancelAiReplyCompleteTimer();

    window.speechSynthesis && window.speechSynthesis.cancel();
    resetAudioPlayback();
    addSystemMessage('自动测试已关闭');
    recordStatus.textContent = '点击开始录音';
    textInput.disabled = false;
    sendTextBtn.disabled = false;
    setRecordButtonState(false);
  }

  autoTestToggle.addEventListener('change', function () {
    if (autoTestToggle.checked) {
      startAutoTest();
    } else {
      stopAutoTest();
    }
  });

  simulatedVoiceToggle.addEventListener('change', function () {
    AppState.playSimulatedVoice = simulatedVoiceToggle.checked;
    if (!simulatedVoiceToggle.checked) {
      window.speechSynthesis && window.speechSynthesis.cancel();
    }
  });

  // ==================== 中断处理 ====================

  async function handleInterrupt() {
    if (!AppState.conversationId) return;

    if (AppState.autoTestEnabled) {
      stopAutoTest();
      autoTestToggle.checked = false;
    }

    // 清理收集器
    testAudioCollector = null;
    cancelAiReplyCompleteTimer();

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
      addSystemMessage('中断失败: ' + error.message);
    } finally {
      interruptBtn.disabled = false;
    }
  }

  // ==================== WebSocket 连接状态回调 ====================

  ws.onConnected = function () {
    wsStatus.textContent = '已连接';
    wsStatus.classList.add('connected');
    enableInputs();
    addSystemMessage('WebSocket连接成功，可以开始语音对话');
  };

  ws.onDisconnected = function () {
    wsStatus.textContent = '已断开';
    wsStatus.classList.remove('connected');
    recordBtn.disabled = true;
    textInput.disabled = true;
    sendTextBtn.disabled = true;
    autoTestToggle.disabled = true;
    simulatedVoiceToggle.disabled = true;
  };

  ws.onError = function (error) {
    wsStatus.textContent = '连接错误';
    wsStatus.classList.remove('connected');
    recordBtn.disabled = true;
    textInput.disabled = true;
    sendTextBtn.disabled = true;
    autoTestToggle.disabled = true;
    simulatedVoiceToggle.disabled = true;
  };

  // ==================== 事件绑定 ====================

  recordBtn.addEventListener('click', toggleRecording);
  interruptBtn.addEventListener('click', handleInterrupt);

  document.addEventListener('keydown', function (e) {
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

  // ==================== AppState 扩展 ====================

  AppState.currentAiText = '';
  AppState.currentAiMessageEl = null;
  AppState.fn.addMessage = addMessage;
  AppState.fn.addSystemMessage = addSystemMessage;
  AppState.fn.scrollToBottom = scrollToBottom;
})();