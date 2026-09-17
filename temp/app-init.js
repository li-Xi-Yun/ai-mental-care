(async function init() {
  AppState.fn.addSystemMessage('正在连接WebSocket...');
  try {
    await AppState.ws.connect();
    AppState.fn.addSystemMessage('WebSocket连接成功，可以开始对话');
  } catch (error) {
    AppState.fn.addSystemMessage(`WebSocket连接失败: ${error.message}`);
  }
  AppState.fn.loadConversationList();
})();