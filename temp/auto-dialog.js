(function () {
  const STREAM_COMPLETE_DELAY = 2000;

  let autoDialogEnabled = false;
  let streamCompleteTimer = null;
  let autoDialogBusy = false;

  const autoDialogBtn = document.getElementById('autoDialogBtn');
  const messageInput = document.getElementById('messageInput');
  const sendBtn = document.getElementById('sendBtn');

  AppState.fn.onStreamChunk = function (chunk) {
    if (!autoDialogEnabled) return;
    clearTimeout(streamCompleteTimer);
    streamCompleteTimer = setTimeout(onStreamComplete, STREAM_COMPLETE_DELAY);
  };

  function onStreamComplete() {
    if (!autoDialogEnabled || autoDialogBusy) return;
    if (!AppState.conversationId || !AppState.currentAiText) return;

    runAutoDialogCycle();
  }

  async function runAutoDialogCycle() {
    if (autoDialogBusy) return;
    autoDialogBusy = true;

    let shouldContinue = false;

    try {
      const aiReply = AppState.currentAiText;
      if (!aiReply || !autoDialogEnabled) return;

      AppState.fn.addSystemMessage('[自动对话] 获取模拟用户回复...');

      const result = await ChatAPI.sendTestMessage(AppState.conversationId, aiReply);
      const simulatedMessage = result.assistantMessage;

      if (!simulatedMessage || !autoDialogEnabled) {
        AppState.fn.addSystemMessage('[自动对话] 未获取到模拟消息，停止');
        return;
      }

      AppState.fn.addSystemMessage('[自动对话] 模拟用户: ' + simulatedMessage);

      messageInput.value = simulatedMessage;
      await AppState.fn.sendMessage();
      shouldContinue = true;

    } catch (error) {
      AppState.fn.addSystemMessage('[自动对话] 错误: ' + error.message);
    } finally {
      autoDialogBusy = false;
      if (autoDialogEnabled) {
        disableManualInput();
      }
      if (shouldContinue && autoDialogEnabled) {
        clearTimeout(streamCompleteTimer);
        streamCompleteTimer = setTimeout(onStreamComplete, STREAM_COMPLETE_DELAY);
      }
    }
  }

  function disableManualInput() {
    messageInput.disabled = true;
    sendBtn.disabled = true;
  }

  function enableManualInput() {
    messageInput.disabled = false;
    if (AppState.ws && AppState.ws.connected) {
      sendBtn.disabled = false;
    }
  }

  autoDialogBtn.addEventListener('click', function () {
    autoDialogEnabled = !autoDialogEnabled;

    if (autoDialogEnabled) {
      autoDialogBtn.textContent = '停止自动对话';
      autoDialogBtn.classList.add('active');
      disableManualInput();

      if (!AppState.conversationId) {
        AppState.fn.addSystemMessage('[自动对话] 已开启，发送消息后自动执行');
        return;
      }

      AppState.fn.addSystemMessage('[自动对话] 已开启');

      if (AppState.currentAiText) {
        clearTimeout(streamCompleteTimer);
        streamCompleteTimer = setTimeout(onStreamComplete, STREAM_COMPLETE_DELAY);
      }

    } else {
      autoDialogBtn.textContent = '自动对话';
      autoDialogBtn.classList.remove('active');
      enableManualInput();
      clearTimeout(streamCompleteTimer);
      autoDialogBusy = false;
      AppState.fn.addSystemMessage('[自动对话] 已关闭');
    }
  });
})();