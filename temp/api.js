class ChatAPI {
  static #logger = window.__logger.getLogger('ChatAPI');

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

  static async getWsTicket() {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.getWsTicket}`;
    return await ChatAPI._request(url, { method: 'GET' });
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
      ChatAPI.#logger.error('发送消息失败:', error);
      throw error;
    }
  }

  // ==================== 适配器 + 管道方案：生命周期 ====================

  /**
   * 初始化对话生命周期
   *
   * <p>会话ID为空时后端自动创建新会话；非空时校验归属后绑定适配器与管道。
   * 返回值包含 conversationId 与输入/输出端点路径。</p>
   *
   * @param inputTypes 输入类型集合（TEXT / AUDIO）
   * @param outputTypes 输出类型集合（TEXT / AUDIO）
   * @param conversationId 会话ID（可选，为空自动创建）
   * @returns {Promise<{conversationId: string, inputEndpoints: [], outputEndpoints: []}>}
   */
  static async initLifecycle(inputTypes, outputTypes, conversationId = null) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.lifecycleInit}`;

    const body = {
      inputTypes: inputTypes || ['TEXT'],
      outputTypes: outputTypes || ['TEXT']
    };
    if (conversationId !== null && conversationId !== undefined && conversationId !== '') {
      body.conversationId = conversationId;
    }

    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify(body)
      });
    } catch (error) {
      ChatAPI.#logger.error('初始化对话生命周期失败:', error);
      throw error;
    }
  }

  /**
   * 销毁对话生命周期
   *
   * <p>释放输入适配器与输出管道资源，取消时间轮任务，清理空会话。</p>
   *
   * @param conversationId 会话ID
   */
  static async endLifecycle(conversationId) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.lifecycleEnd}/${conversationId}`;
    try {
      return await ChatAPI._request(url, { method: 'DELETE' });
    } catch (error) {
      ChatAPI.#logger.error('销毁对话生命周期失败:', error);
      throw error;
    }
  }

  // ==================== 旧链路（测试/回退参考，新流程不再使用） ====================

  static async getConversationList(pageNum = 1, pageSize = 20) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.conversationList}`;
    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify({ pageNum, pageSize })
      });
    } catch (error) {
      ChatAPI.#logger.error('获取会话列表失败:', error);
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
      ChatAPI.#logger.error('获取对话记录失败:', error);
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
      ChatAPI.#logger.error('获取情绪分析列表失败:', error);
      throw error;
    }
  }

  static async getEmotionAnalysisDetail(analysisId) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.emotionAnalysisDetail}${analysisId}`;
    try {
      return await ChatAPI._request(url, { method: 'GET' });
    } catch (error) {
      ChatAPI.#logger.error('获取情绪分析详情失败:', error);
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
      ChatAPI.#logger.error('获取诊断书列表失败:', error);
      throw error;
    }
  }

  static async getEmotionDiagnosisDetail(diagnosisId) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.emotionDiagnosisDetail}${diagnosisId}`;
    try {
      return await ChatAPI._request(url, { method: 'GET' });
    } catch (error) {
      ChatAPI.#logger.error('获取诊断书详情失败:', error);
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
      ChatAPI.#logger.error('发送测试消息失败:', error);
      throw error;
    }
  }

  static async initAudioSession(conversationId = null) {
    let url = `${CONFIG.server.baseUrl}${CONFIG.api.audioInit}`;
    if (conversationId !== null) {
      url += `?conversationId=${conversationId}`;
    }
    try {
      return await ChatAPI._request(url, { method: 'POST' });
    } catch (error) {
      ChatAPI.#logger.error('初始化语音会话失败:', error);
      throw error;
    }
  }

  static async endAudioSession(conversationId) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.audioEnd}/${conversationId}/end`;
    try {
      return await ChatAPI._request(url, { method: 'DELETE' });
    } catch (error) {
      ChatAPI.#logger.error('结束语音会话失败:', error);
      throw error;
    }
  }

  static async audioStreamTest(conversationId, message) {
    const url = `${CONFIG.server.baseUrl}${CONFIG.api.audioTestStream}`;
    const body = { conversationId };
    if (message !== null && message !== undefined && message !== '') {
      body.message = message;
    }
    try {
      return await ChatAPI._request(url, {
        method: 'POST',
        body: JSON.stringify(body)
      });
    } catch (error) {
      ChatAPI.#logger.error('语音测试流失败:', error);
      throw error;
    }
  }
}