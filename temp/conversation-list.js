(function () {
  const panelToggleBtn = document.getElementById('panelToggleBtn');
  const leftPanel = document.getElementById('leftPanel');
  const conversationListEl = document.getElementById('conversationList');
  const newConversationBtn = document.getElementById('newConversationBtn');
  const conversationSearch = document.getElementById('conversationSearch');
  const convPagination = document.getElementById('convPagination');
  const convPrevBtn = document.getElementById('convPrevBtn');
  const convNextBtn = document.getElementById('convNextBtn');
  const convPageInfo = document.getElementById('convPageInfo');

  panelToggleBtn.addEventListener('click', () => {
    leftPanel.classList.toggle('collapsed');
  });

  newConversationBtn.addEventListener('click', () => {
    AppState.conversationId = null;
    AppState.currentRound = 0;
    AppState.currentAiMessageEl = null;
    AppState.currentAiText = '';
    AppState.chatPageNum = 1;
    AppState.chatTotalPages = 1;
    AppState.chatLoadingOlder = false;
    AppState.chatAllLoaded = false;

    const chatContainer = document.getElementById('chatContainer');
    chatContainer.innerHTML = '';
    AppState.fn.addSystemMessage('已创建新会话，发送消息后将自动开始');

    document.getElementById('emotionToggleBtn').disabled = true;
    document.getElementById('diagnosisPageBtn').disabled = true;

    AppState.ws.unsubscribeAll();

    document.querySelectorAll('.conversation-item.active').forEach(el => el.classList.remove('active'));
  });

  conversationSearch.addEventListener('input', () => {
    AppState.convPageNum = 1;
    loadConversationList();
  });

  convPrevBtn.addEventListener('click', () => {
    if (AppState.convPageNum > 1) {
      AppState.convPageNum--;
      loadConversationList();
    }
  });

  convNextBtn.addEventListener('click', () => {
    if (AppState.convPageNum < AppState.convTotalPages) {
      AppState.convPageNum++;
      loadConversationList();
    }
  });

  async function loadConversationList() {
    conversationListEl.innerHTML = '<div class="sidebar-loading">加载中...</div>';
    convPagination.style.display = 'none';

    try {
      const data = await ChatAPI.getConversationList(AppState.convPageNum, AppState.convPageSize);
      const list = data.records || [];
      const total = data.total || 0;
      AppState.convTotalPages = Math.max(1, Math.ceil(total / AppState.convPageSize));
      AppState.convListData = list;

      if (list.length === 0) {
        conversationListEl.innerHTML = '<div class="sidebar-empty">暂无会话</div>';
        return;
      }

      const searchKeyword = conversationSearch.value.trim().toLowerCase();
      const filtered = searchKeyword
        ? list.filter(item => (item.name || '').toLowerCase().includes(searchKeyword))
        : list;

      if (filtered.length === 0) {
        conversationListEl.innerHTML = '<div class="sidebar-empty">无匹配会话</div>';
        convPagination.style.display = 'flex';
        convPageInfo.textContent = `${AppState.convPageNum} / ${AppState.convTotalPages}`;
        convPrevBtn.disabled = AppState.convPageNum <= 1;
        convNextBtn.disabled = AppState.convPageNum >= AppState.convTotalPages;
        return;
      }

      conversationListEl.innerHTML = '';
      filtered.forEach(item => {
        const el = document.createElement('div');
        el.className = 'conversation-item';
        if (AppState.conversationId && safeBigIntEqual(item.id, AppState.conversationId)) {
          el.classList.add('active');
        }

        const nameEl = document.createElement('div');
        nameEl.className = 'conversation-item-name';
        nameEl.textContent = item.name || `会话 ${String(item.id)}`;

        const timeEl = document.createElement('div');
        timeEl.className = 'conversation-item-time';
        timeEl.textContent = item.updatedTime ? formatTime(item.updatedTime) : '';

        el.appendChild(nameEl);
        el.appendChild(timeEl);

        el.addEventListener('click', () => switchConversation(item));
        conversationListEl.appendChild(el);
      });

      convPageInfo.textContent = `${AppState.convPageNum} / ${AppState.convTotalPages}`;
      convPrevBtn.disabled = AppState.convPageNum <= 1;
      convNextBtn.disabled = AppState.convPageNum >= AppState.convTotalPages;
      convPagination.style.display = 'flex';
    } catch (error) {
      conversationListEl.innerHTML = `<div class="sidebar-error">加载失败: ${error.message}</div>`;
    }
  }

  async function switchConversation(item) {
    if (AppState.isSending) return;

    const targetId = String(item.id);
    if (AppState.conversationId && safeBigIntEqual(AppState.conversationId, targetId)) return;

    AppState.conversationId = targetId;
    AppState.currentRound = 0;
    AppState.currentAiMessageEl = null;
    AppState.currentAiText = '';

    document.querySelectorAll('.conversation-item.active').forEach(el => el.classList.remove('active'));
    const items = conversationListEl.querySelectorAll('.conversation-item');
    items.forEach(el => {
      const nameText = el.querySelector('.conversation-item-name').textContent;
      if (nameText === (item.name || `会话 ${String(item.id)}`)) {
        el.classList.add('active');
      }
    });

    document.getElementById('emotionToggleBtn').disabled = false;
    document.getElementById('diagnosisPageBtn').disabled = false;

    AppState.ws.unsubscribeAll();
    AppState.ws.subscribeTextReply(AppState.conversationId, AppState.fn.onTextReply);
    AppState.ws.subscribeConversationName(AppState.conversationId, AppState.fn.onConversationName);

    const chatContainer = document.getElementById('chatContainer');
    chatContainer.innerHTML = '<div class="sidebar-loading">加载对话记录中...</div>';
    AppState.chatLoadingOlder = false;
    AppState.chatAllLoaded = false;
    AppState.chatPageNum = 1;

    try {
      const data = await ChatAPI.getDialogueMemory(AppState.conversationId, AppState.chatPageNum, AppState.chatPageSize);
      const list = data.records || [];
      const total = data.total || 0;
      AppState.chatTotalPages = Math.max(1, Math.ceil(total / AppState.chatPageSize));

      chatContainer.innerHTML = '';

      if (list.length === 0) {
        AppState.fn.addSystemMessage('该会话暂无对话记录');
        AppState.chatAllLoaded = true;
      } else {
        const reversedList = [...list].reverse();
        let lastRound = 0;
        reversedList.forEach(msg => {
          const type = (msg.type || '').toUpperCase();
          if (type === 'USER') {
            AppState.fn.addMessage(msg.content, 'user');
          } else if (type === 'ASSISTANT') {
            AppState.fn.addMessage(msg.content, 'ai');
          } else if (type === 'SYSTEM' || type === 'THINKING') {
            AppState.fn.addSystemMessage(msg.content);
          } else {
            AppState.fn.addSystemMessage(`[${type}] ${msg.content}`);
          }
          if (msg.roundNum && msg.roundNum > lastRound) {
            lastRound = msg.roundNum;
          }
        });
        AppState.currentRound = lastRound;
        AppState.fn.scrollToBottom();

        if (list.length < AppState.chatPageSize || AppState.chatPageNum >= AppState.chatTotalPages) {
          AppState.chatAllLoaded = true;
        }

        AppState.fn.addSystemMessage(`已切换到会话: ${item.name || String(item.id)}，共 ${lastRound} 轮`);
      }
    } catch (error) {
      chatContainer.innerHTML = '';
      AppState.fn.addSystemMessage(`切换会话失败: ${error.message}`);
    }
  }

  AppState.fn.loadConversationList = loadConversationList;
  AppState.fn.switchConversation = switchConversation;
})();