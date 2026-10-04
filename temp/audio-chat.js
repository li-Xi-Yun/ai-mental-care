(function () {
  const logger = window.__logger.getLogger('AudioChat');
  const chatContainer = document.getElementById('chatContainer');
  const recordBtn = document.getElementById('recordBtn');
  const recordBtnText = document.getElementById('recordBtnText');
  const recordStatus = document.getElementById('recordStatus');
  const volumeBar = document.getElementById('volumeBar');
  const wsStatus = document.getElementById('wsStatus');
  const textInput = document.getElementById('textInput');
  const sendTextBtn = document.getElementById('sendTextBtn');
  const autoTestToggle = document.getElementById('autoTestToggle');
  const simulatedVoiceToggle = document.getElementById('simulatedVoiceToggle');

  const ws = new ChatWebSocket();
  AppState.ws = ws;
  const audioSender = new AudioWebSocketSender(ws);

  let isRecording = false;
  let currentAsrText = '';

  // ==================== VAD 连续语音输入 ====================
  // 语音输入采用 VAD（语音活动检测）驱动：持续采集麦克风 → 检测到说话自动逐帧发送音频，
  // 检测到静音自动发送 vad-stop 结束当前语句，全程无需按钮控制"停止说话"。
  const VAD = {
    enabled: false,
    stream: null,
    audioContext: null,
    source: null,
    scriptNode: null,
    analyser: null,
    targetSampleRate: 16000,
    energyThreshold: 0.012,       // RMS 能量阈值：高于视为语音
    silenceHoldFrames: 15,        // 连续静音帧数达到该值 → 判定本次说话结束（每帧约 90ms）
    inSpeech: false,
    silenceFrames: 0,
    onVolumeChange: null,
    onSpeechStateChange: null
  };

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
      logger.warn('播放音频chunk失败', e);
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

  // ==================== PCM → Base64 转换 ====================

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
      recordStatus.textContent = '点击开启语音输入';
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
      recordStatus.textContent = '连续聆听中...';
    } else {
      recordBtn.classList.remove('recording');
      recordBtnText.textContent = '\u{1F3A4}';
      recordStatus.textContent = AppState.autoTestEnabled ? '自动测试中...' : '点击开启语音输入';
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

  // ==================== 会话初始化（适配器 + 管道方案） ====================

  // 从 URL 读取跳转带入的会话信息（支持同一会话文本 ↔ 语音模式切换）
  (function () {
    const params = new URLSearchParams(window.location.search);
    const cid = params.get('conversationId');
    if (cid) {
      AppState.conversationId = cid;
      // 文本页跳转时若带 lifecycleBound=1，表示该会话已有旧绑定，模式切换需先 end 再 init
      AppState.lifecycleBoundId = params.get('lifecycleBound') === '1' ? cid : null;
      AppState.lifecycleInitialized = false;
    }
  })();

  async function ensureConversation() {
    // 会话已绑定生命周期则复用
    if (AppState.conversationId && AppState.lifecycleInitialized) return;

    // 同一会话进行模式切换（如从文本切到语音）：先调用 endLifecycle 销毁旧绑定，再按当前模式初始化
    if (AppState.conversationId && AppState.lifecycleBoundId
        && safeBigIntEqual(AppState.conversationId, AppState.lifecycleBoundId)) {
      try {
        await ChatAPI.endLifecycle(AppState.conversationId);
        addSystemMessage('已释放旧会话资源');
      } catch (e) {
        addSystemMessage('释放旧会话资源失败: ' + e.message);
      }
      AppState.lifecycleInitialized = false;
      AppState.lifecycleBoundId = null;
    }

    try {
      addSystemMessage('正在初始化语音会话...');
      // 注册文本+音频输入适配器，文本+音频输出（文字走 /text/reply，语音走 /audio/binary）
      const data = await ChatAPI.initLifecycle(['TEXT', 'AUDIO'], ['TEXT', 'AUDIO'], AppState.conversationId || null);
      AppState.conversationId = String(data.conversationId);
      AppState.lifecycleInitialized = true;
      AppState.lifecycleBoundId = AppState.conversationId;
      addSystemMessage('语音会话已初始化，ID: ' + AppState.conversationId);
      startAudioSubscriptions();
    } catch (error) {
      addSystemMessage('初始化失败: ' + error.message);
      throw error;
    }
  }

  // ==================== VAD 连续语音输入 ====================

  /**
   * 开启/关闭连续语音输入（主开关）
   *
   * <p>开启后持续采集麦克风，由 VAD 判断说话/静音，无需每句话按按钮。</p>
   */
  async function toggleListening() {
    if (AppState.autoTestEnabled) {
      addSystemMessage('自动测试模式下不支持语音输入');
      return;
    }
    if (VAD.enabled) {
      stopListening();
    } else {
      await startListening();
    }
  }

  async function startListening() {
    try {
      await ensureConversation();
    } catch (error) {
      return;
    }

    try {
      VAD.onVolumeChange = updateVolume;
      VAD.onSpeechStateChange = function (speaking) {
        if (speaking) {
          recordStatus.textContent = '识别中...';
        }
      };
      await startContinuousCapture();
      VAD.enabled = true;
      isRecording = true;
      setRecordButtonState(true);
      addSystemMessage('已开启连续语音输入：说话自动识别，静音自动结束一句话');
    } catch (error) {
      addSystemMessage('开启语音输入失败: ' + error.message);
    }
  }

  function stopListening() {
    stopContinuousCapture();
    VAD.enabled = false;
    isRecording = false;
    setRecordButtonState(false);
  }

  /**
   * 启动麦克风连续采集（Web Audio API，无外部依赖）
   *
   * <p>ScriptProcessorNode 每帧（约 90ms）产出 16kHz PCM 数据，
   * 送入 {@link handleVadFrame} 做 VAD 判断并逐帧发送。</p>
   */
  async function startContinuousCapture() {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    VAD.stream = stream;

    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    VAD.audioContext = ctx;
    const source = ctx.createMediaStreamSource(stream);
    VAD.source = source;

    // 音量表（音量条显示）
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 256;
    source.connect(analyser);
    VAD.analyser = analyser;

    // PCM 采集节点
    const scriptNode = ctx.createScriptProcessor(4096, 1, 1);
    source.connect(scriptNode);
    scriptNode.connect(ctx.destination);
    VAD.scriptNode = scriptNode;

    const ratio = ctx.sampleRate / VAD.targetSampleRate;

    scriptNode.onaudioprocess = (e) => {
      const input = e.inputBuffer.getChannelData(0);
      const outLen = Math.floor(input.length / ratio);
      const pcm16 = new Int16Array(outLen);
      let energy = 0;
      for (let i = 0; i < outLen; i++) {
        const s = input[Math.floor(i * ratio)];
        pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        energy += s * s;
      }
      const rms = Math.sqrt(energy / outLen);

      handleVadFrame(pcm16, rms);

      if (VAD.onVolumeChange) {
        const freq = new Uint8Array(analyser.frequencyBinCount);
        analyser.getByteFrequencyData(freq);
        let sum = 0;
        for (let i = 0; i < freq.length; i++) sum += freq[i];
        VAD.onVolumeChange(Math.min(100, Math.round((sum / freq.length / 128) * 100)));
      }
    };
  }

  /**
   * VAD 帧处理：说话 → 逐帧发音频；静音持续达到阈值 → 发 vad-stop
   *
   * @param pcm16 16kHz PCM 帧（Int16Array）
   * @param rms   当前帧 RMS 能量
   */
  function handleVadFrame(pcm16, rms) {
    const isVoice = rms >= VAD.energyThreshold;

    if (isVoice) {
      VAD.silenceFrames = 0;
      if (!VAD.inSpeech) {
        VAD.inSpeech = true;
        recordStatus.textContent = '识别中...';
        if (VAD.onSpeechStateChange) VAD.onSpeechStateChange(true);
      }
      // 说话中：逐帧发送音频到适配器层（ASR），目标 /app/audio/frame
      audioSender.sendAudioMessage(arrayBufferToBase64(pcm16.buffer), AppState.conversationId);
    } else {
      if (VAD.inSpeech) {
        VAD.silenceFrames++;
        if (VAD.silenceFrames >= VAD.silenceHoldFrames) {
          // 静音持续达到阈值 → 本次说话结束，通知 ASR 结束音频流
          VAD.inSpeech = false;
          VAD.silenceFrames = 0;
          audioSender.sendStopSpeaking(AppState.conversationId);
          recordStatus.textContent = '识别中...（可继续说话）';
          if (VAD.onSpeechStateChange) VAD.onSpeechStateChange(false);
        }
      }
    }
  }

  /**
   * 停止连续采集并释放资源；若仍在说话中，补发 vad-stop 收尾
   */
  function stopContinuousCapture() {
    if (VAD.scriptNode) { try { VAD.scriptNode.disconnect(); } catch (e) {} VAD.scriptNode = null; }
    if (VAD.source) { try { VAD.source.disconnect(); } catch (e) {} VAD.source = null; }
    if (VAD.analyser) { try { VAD.analyser.disconnect(); } catch (e) {} VAD.analyser = null; }
    if (VAD.audioContext) { try { VAD.audioContext.close(); } catch (e) {} VAD.audioContext = null; }
    if (VAD.stream) { VAD.stream.getTracks().forEach(t => t.stop()); VAD.stream = null; }

    if (VAD.inSpeech) {
      VAD.inSpeech = false;
      VAD.silenceFrames = 0;
      audioSender.sendStopSpeaking(AppState.conversationId);
    }
  }

  // ==================== 文本发送（适配器 + 管道方案：走文本适配器接口） ====================

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
      // 文本消息走输入适配器：持久化并触发 AI 对话流程（输出含 TEXT 字幕 + AUDIO 语音）
      const data = await ChatAPI.sendMessage(text, AppState.conversationId);
      logger.info('文本消息发送成功:', data);

      if (!AppState.currentAiMessageEl || AppState.currentAiMessageEl.dataset.complete === 'true') {
        AppState.currentAiMessageEl = document.createElement('div');
        AppState.currentAiMessageEl.className = 'message ai';
        AppState.currentAiMessageEl.dataset.complete = 'false';
        AppState.currentAiText = '';
        chatContainer.appendChild(AppState.currentAiMessageEl);
      }
    } catch (error) {
      addSystemMessage('发送失败: ' + error.message);
      recordStatus.textContent = '发送失败';
      textInput.disabled = false;
      sendTextBtn.disabled = false;
      cancelAiReplyCompleteTimer();
    }
  }

  // ==================== WebSocket 订阅 ====================

  function startAudioSubscriptions() {
    if (!AppState.conversationId) return;

    // 文字字幕流（outputTypes 含 TEXT 时由 /text/reply 推送）
    ws.subscribeTextReply(AppState.conversationId, onAiTextReply);
    // 音频二进制流（含 AUDIO 时由 /audio/binary 推送）
    ws.subscribeAudioBinary(AppState.conversationId, onAudioBinary);
    // 测试接口的 TTS 完成信号（仅自动测试/文本转语音临时链路使用）
    ws.subscribeAudioReply(AppState.conversationId, onAudioReply);
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
    // 仅处理来自测试接口的 TTS完成信号 → 解析收集器Promise（自动测试链路沿用）
    if (text === '[TTS_COMPLETE]') {
      if (testAudioCollector) {
        const collector = testAudioCollector;
        testAudioCollector = null;
        collector.resolve(collector.chunks);
      }
      return;
    }
  }

  function onAiTextReply(text) {
    // 来自适配器/管道方案的 AI 回复文字流（/text/reply）
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

      // 发送音频帧到适配器层（ASR），随后通知ASR结束音频流
      audioSender.sendAudioMessage(audioBase64, AppState.conversationId);
      audioSender.sendStopSpeaking(AppState.conversationId);
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

    // 自动测试与连续语音输入互斥
    stopListening();

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
    recordStatus.textContent = '点击开启语音输入';
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

  // ==================== 事件绑定 ====================

  recordBtn.addEventListener('click', toggleListening);

  document.addEventListener('keydown', function (e) {
    if (e.key === ' ' && e.target === document.body) {
      e.preventDefault();
      toggleListening();
    }
    if (e.key === 'Enter' && document.activeElement === textInput) {
      e.preventDefault();
      sendTextMessage();
    }
  });

  sendTextBtn.addEventListener('click', sendTextMessage);

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
    stopListening();
    recordBtn.disabled = true;
    textInput.disabled = true;
    sendTextBtn.disabled = true;
    autoTestToggle.disabled = true;
    simulatedVoiceToggle.disabled = true;
  };

  ws.onError = function (error) {
    wsStatus.textContent = '连接错误';
    wsStatus.classList.remove('connected');
    stopListening();
    recordBtn.disabled = true;
    textInput.disabled = true;
    sendTextBtn.disabled = true;
    autoTestToggle.disabled = true;
    simulatedVoiceToggle.disabled = true;
  };

  // ==================== AppState 扩展 ====================

  AppState.currentAiText = '';
  AppState.currentAiMessageEl = null;
  AppState.fn.addMessage = addMessage;
  AppState.fn.addSystemMessage = addSystemMessage;
  AppState.fn.scrollToBottom = scrollToBottom;

  // ==================== 退出语音对话：释放生命周期资源 ====================

  /**
   * 点击"返回"退出语音对话时，先调用 endLifecycle 释放当前会话的适配器/管道资源，
   * 再跳转回文本对话页（若当前会话为文本页跳入的模式切换会话，释放后文本页会重新初始化）。
   */
  (function () {
    const backBtn = document.getElementById('backBtn');
    if (!backBtn) return;
    const targetHref = backBtn.getAttribute('href') || 'index.html';

    backBtn.addEventListener('click', function (e) {
      e.preventDefault();
      stopListening();

      const cid = AppState.conversationId;
      const bound = AppState.lifecycleBoundId;
      const release = (cid && bound && safeBigIntEqual(cid, bound))
        ? ChatAPI.endLifecycle(cid).catch(function () {})
        : Promise.resolve();

      release.then(function () {
        AppState.lifecycleInitialized = false;
        AppState.lifecycleBoundId = null;
        window.location.href = targetHref;
      });
    });
  })();
})();