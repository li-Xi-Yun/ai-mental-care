(function () {
  const emotionToggleBtn = document.getElementById('emotionToggleBtn');
  const sidebarOverlay = document.getElementById('sidebarOverlay');
  const emotionSidebar = document.getElementById('emotionSidebar');
  const sidebarCloseBtn = document.getElementById('sidebarCloseBtn');
  const sidebarContent = document.getElementById('sidebarContent');
  const diagnosisPageBtn = document.getElementById('diagnosisPageBtn');
  const sidebarPagination = document.getElementById('sidebarPagination');
  const sidebarPrevBtn = document.getElementById('sidebarPrevBtn');
  const sidebarNextBtn = document.getElementById('sidebarNextBtn');
  const sidebarPageInfo = document.getElementById('sidebarPageInfo');

  function openSidebar() {
    AppState.sidebarOpen = true;
    emotionSidebar.classList.add('open');
    sidebarOverlay.classList.add('active');
    if (AppState.conversationId) {
      AppState.sidebarPageNum = 1;
      loadEmotionAnalysis();
    }
  }

  function closeSidebar() {
    AppState.sidebarOpen = false;
    emotionSidebar.classList.remove('open');
    sidebarOverlay.classList.remove('active');
  }

  emotionToggleBtn.addEventListener('click', () => {
    if (AppState.sidebarOpen) {
      closeSidebar();
    } else {
      openSidebar();
    }
  });

  sidebarCloseBtn.addEventListener('click', closeSidebar);
  sidebarOverlay.addEventListener('click', closeSidebar);

  diagnosisPageBtn.addEventListener('click', () => {
    if (!AppState.conversationId) return;
    const url = `diagnosis.html?conversationId=${AppState.conversationId}&token=${encodeURIComponent(CONFIG.auth.token)}&host=${CONFIG.server.host}&port=${CONFIG.server.port}`;
    window.open(url, '_blank');
  });

  sidebarPrevBtn.addEventListener('click', () => {
    if (AppState.sidebarPageNum > 1) {
      AppState.sidebarPageNum--;
      loadEmotionAnalysis();
    }
  });

  sidebarNextBtn.addEventListener('click', () => {
    if (AppState.sidebarPageNum < AppState.sidebarTotalPages) {
      AppState.sidebarPageNum++;
      loadEmotionAnalysis();
    }
  });

  async function loadEmotionAnalysis() {
    if (!AppState.conversationId) {
      sidebarContent.innerHTML = '<div class="sidebar-empty">暂无会话，请先开始对话</div>';
      sidebarPagination.style.display = 'none';
      return;
    }

    sidebarContent.innerHTML = '<div class="sidebar-loading">加载中...</div>';
    sidebarPagination.style.display = 'none';

    try {
      const data = await ChatAPI.getEmotionAnalysisList(AppState.conversationId, AppState.sidebarPageNum, AppState.sidebarPageSize);
      const list = data.records || [];
      const total = data.total || 0;
      AppState.sidebarTotalPages = Math.max(1, Math.ceil(total / AppState.sidebarPageSize));

      if (list.length === 0) {
        sidebarContent.innerHTML = '<div class="sidebar-empty">暂无情绪分析数据</div>';
        return;
      }

      sidebarContent.innerHTML = '';
      list.forEach(item => {
        sidebarContent.appendChild(createAnalysisCard(item));
      });

      sidebarPageInfo.textContent = `${AppState.sidebarPageNum} / ${AppState.sidebarTotalPages}`;
      sidebarPrevBtn.disabled = AppState.sidebarPageNum <= 1;
      sidebarNextBtn.disabled = AppState.sidebarPageNum >= AppState.sidebarTotalPages;
      sidebarPagination.style.display = 'flex';
    } catch (error) {
      sidebarContent.innerHTML = `<div class="sidebar-error">加载失败: ${error.message}</div>`;
    }
  }

  function createAnalysisCard(item) {
    const card = document.createElement('div');
    card.className = 'analysis-card';

    const header = document.createElement('div');
    header.className = 'analysis-card-header';

    const round = document.createElement('span');
    round.className = 'analysis-round';
    round.textContent = `第 ${item.roundNum} 轮`;

    const time = document.createElement('span');
    time.className = 'analysis-time';
    time.textContent = item.createdTime ? formatTime(item.createdTime) : '';

    header.appendChild(round);
    header.appendChild(time);
    card.appendChild(header);

    const labelRow = document.createElement('div');
    labelRow.className = 'analysis-label-row';

    if (item.emotionLabel) {
      const tag = document.createElement('span');
      tag.className = `emotion-tag ${getEmotionTagClass(item.emotionLabel)}`;
      tag.textContent = item.emotionLabel;
      labelRow.appendChild(tag);
    }
    if (item.emotionSubLabel) {
      const subTag = document.createElement('span');
      subTag.className = `emotion-tag ${getEmotionTagClass(item.emotionSubLabel)}`;
      subTag.style.opacity = '0.8';
      subTag.textContent = item.emotionSubLabel;
      labelRow.appendChild(subTag);
    }
    if (item.emotionTrend) {
      const trendTag = document.createElement('span');
      trendTag.className = 'emotion-tag neutral';
      trendTag.textContent = `趋势: ${item.emotionTrend}`;
      labelRow.appendChild(trendTag);
    }
    card.appendChild(labelRow);

    const metrics = document.createElement('div');
    metrics.className = 'analysis-metrics';
    metrics.innerHTML = `
      <span>置信度: ${item.emotionConfidence != null ? (item.emotionConfidence * 100).toFixed(1) + '%' : '-'}</span>
      <span>强度: ${item.emotionIntensity != null ? (item.emotionIntensity * 100).toFixed(1) + '%' : '-'}</span>
      <span>P(愉悦): ${item.pScore != null ? item.pScore.toFixed(2) : '-'}</span>
      <span>A(唤醒): ${item.aScore != null ? item.aScore.toFixed(2) : '-'}</span>
      <span>D(支配): ${item.dScore != null ? item.dScore.toFixed(2) : '-'}</span>
      <span>正向: ${item.positiveEmotionRatio != null ? (item.positiveEmotionRatio * 100).toFixed(1) + '%' : '-'}</span>
      <span>负向: ${item.negativeEmotionRatio != null ? (item.negativeEmotionRatio * 100).toFixed(1) + '%' : '-'}</span>
      <span>中性: ${item.neutralEmotionRatio != null ? (item.neutralEmotionRatio * 100).toFixed(1) + '%' : '-'}</span>
    `;
    card.appendChild(metrics);

    if (item.analysisContent) {
      const contentPreview = document.createElement('div');
      contentPreview.style.cssText = 'margin-top:8px;font-size:12px;color:#888;line-height:1.5;max-height:60px;overflow:hidden;text-overflow:ellipsis;';
      contentPreview.textContent = item.analysisContent;
      card.appendChild(contentPreview);
    }

    const detailBtn = document.createElement('button');
    detailBtn.className = 'analysis-detail-btn';
    detailBtn.textContent = '查看详情';
    card.appendChild(detailBtn);

    let detailLoaded = false;
    detailBtn.addEventListener('click', async () => {
      const existingPanel = card.querySelector('.analysis-detail-panel');
      if (existingPanel) {
        existingPanel.remove();
        detailBtn.textContent = '查看详情';
        return;
      }

      if (!detailLoaded) {
        detailBtn.textContent = '加载中...';
        detailBtn.disabled = true;
        try {
          const detail = await ChatAPI.getEmotionAnalysisDetail(item.id);
          detailLoaded = true;
          showDetailPanel(card, detail, detailBtn);
        } catch (error) {
          const panel = document.createElement('div');
          panel.className = 'analysis-detail-panel';
          panel.style.color = '#ff4d4f';
          panel.textContent = `加载详情失败: ${error.message}`;
          card.appendChild(panel);
        } finally {
          detailBtn.disabled = false;
        }
      } else {
        showDetailPanel(card, null, detailBtn);
      }
    });

    return card;
  }

  function showDetailPanel(card, detail, btn) {
    const panel = document.createElement('div');
    panel.className = 'analysis-detail-panel';
    let text = '';
    if (detail) {
      if (detail.thinkContent) text += `【思考过程】\n${detail.thinkContent}\n\n`;
      if (detail.modelContent) text += `【模型分析】\n${detail.modelContent}\n\n`;
      if (detail.userContent) text += `【用户输入】\n${detail.userContent}`;
    }
    panel.textContent = text || '无详情数据';
    card.appendChild(panel);
    btn.textContent = '收起详情';
  }

  AppState.fn.openSidebar = openSidebar;
  AppState.fn.closeSidebar = closeSidebar;
  AppState.fn.loadEmotionAnalysis = loadEmotionAnalysis;
})();