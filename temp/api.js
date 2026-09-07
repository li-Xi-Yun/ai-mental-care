class ChatAPI {
  static async sendMessage(message, conversationId = null) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.sendMessage}`;

    const body = { message };
    if (conversationId !== null) {
      body.conversationId = conversationId;
    }

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [CONFIG.auth.tokenHeader]: CONFIG.auth.token
        },
        body: JSON.stringify(body)
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`HTTP ${response.status}: ${errorText}`);
      }

      const result = await response.json();

      if (result.code !== 200 && result.code !== 0) {
        throw new Error(result.msg || '请求失败');
      }

      return result.data;
    } catch (error) {
      console.error('发送消息失败:', error);
      throw error;
    }
  }
}