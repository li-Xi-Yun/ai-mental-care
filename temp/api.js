class ChatAPI {
  static async _request(url, options = {}) {
    const headers = {
      'Content-Type': 'application/json',
      [CONFIG.auth.tokenHeader]: CONFIG.auth.token
    };

    const response = await fetch(url, { ...options, headers: { ...headers, ...options.headers } });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`HTTP ${response.status}: ${errorText}`);
    }

    const result = parseJSONWithBigInt(await response.text());

    if (result.code !== 200 && result.code !== 0) {
      throw new Error(result.msg || '请求失败');
    }

    return processIdFields(result.data);
  }

  static async sendMessage(message, conversationId = null) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.sendMessage}`;

    const body = { message };
    if (conversationId !== null) {
      body.conversationId = conversationId;
    }

    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify(body)
      });
    } catch (error) {
      console.error('发送消息失败:', error);
      throw error;
    }
  }

  static async getConversationList(pageNum = 1, pageSize = 20) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.conversationList}`;
    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify({ pageNum, pageSize })
      });
    } catch (error) {
      console.error('获取会话列表失败:', error);
      throw error;
    }
  }

  static async getDialogueMemory(conversationId, pageNum = 1, pageSize = 50) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.dialogueMemory}${conversationId}/memory`;
    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify({ pageNum, pageSize })
      });
    } catch (error) {
      console.error('获取对话记录失败:', error);
      throw error;
    }
  }

  static async getEmotionAnalysisList(conversationId, pageNum = 1, pageSize = 20) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.emotionAnalysisList}${conversationId}`;
    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify({ pageNum, pageSize })
      });
    } catch (error) {
      console.error('获取情绪分析列表失败:', error);
      throw error;
    }
  }

  static async getEmotionAnalysisDetail(analysisId) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.emotionAnalysisDetail}${analysisId}`;
    try {
      return await ChatAPI._request(url, { method: 'GET' });
    } catch (error) {
      console.error('获取情绪分析详情失败:', error);
      throw error;
    }
  }

  static async getEmotionDiagnosisList(conversationId, pageNum = 1, pageSize = 20) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.emotionDiagnosisList}${conversationId}`;
    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify({ pageNum, pageSize })
      });
    } catch (error) {
      console.error('获取诊断书列表失败:', error);
      throw error;
    }
  }

  static async getEmotionDiagnosisDetail(diagnosisId) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.emotionDiagnosisDetail}${diagnosisId}`;
    try {
      return await ChatAPI._request(url, { method: 'GET' });
    } catch (error) {
      console.error('获取诊断书详情失败:', error);
      throw error;
    }
  }

  static async sendTestMessage(conversationId, message) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.conversationTestSend}`;
    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify({ conversationId, message })
      });
    } catch (error) {
      console.error('发送测试消息失败:', error);
      throw error;
    }
  }
}