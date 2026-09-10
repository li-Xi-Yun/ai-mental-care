<template>
  <div class="diagnosis-detail-page">
    <div class="breadcrumb">
      <router-link to="/diagnosis" class="breadcrumb-link">心理诊断</router-link>
      <span class="breadcrumb-sep">›</span>
      <router-link :to="`/diagnosis/${sessionId}`" class="breadcrumb-link">心情困扰与工作压力</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">诊断 #3</span>
    </div>

    <div class="detail-grid">
      <div class="detail-card">
        <div class="card-title">📋 基本信息</div>
        <div class="card-body">
          <div class="info-row"><span class="info-label">诊断编号：</span>#3</div>
          <div class="info-row"><span class="info-label">所属会话：</span>心情困扰与工作压力</div>
          <div class="info-row"><span class="info-label">对话轮次：</span>第6轮</div>
          <div class="info-row">
            <span class="info-label">核心情绪：</span>
            <el-tag type="warning" size="small">焦虑</el-tag>
            <span class="info-extra">（置信度0.85，强度0.78）</span>
          </div>
          <div class="info-row"><span class="info-label">情绪趋势：</span>↓ 下降（平稳3轮）</div>
          <div class="info-row"><span class="info-label">心理状态：</span>轻度焦虑状态</div>
        </div>
      </div>

      <div class="detail-card">
        <div class="card-title">🎭 情绪占比分析</div>
        <div class="card-body">
          <div class="emotion-chart-row">
            <div class="emotion-ring">
              <svg viewBox="0 0 100 100" class="ring-svg">
                <circle cx="50" cy="50" r="40" fill="none" stroke="#f0f0f0" stroke-width="8" />
                <circle cx="50" cy="50" r="40" fill="none" stroke="#ff6b6b" stroke-width="8" stroke-dasharray="113.10 138.23" stroke-dashoffset="0" />
                <circle cx="50" cy="50" r="40" fill="none" stroke="#fa8c16" stroke-width="8" stroke-dasharray="62.83 188.50" stroke-dashoffset="-113.10" />
                <circle cx="50" cy="50" r="40" fill="none" stroke="#6c63ff" stroke-width="8" stroke-dasharray="37.70 213.63" stroke-dashoffset="-175.93" />
                <circle cx="50" cy="50" r="40" fill="none" stroke="#52c41a" stroke-width="8" stroke-dasharray="37.70 213.63" stroke-dashoffset="-213.63" />
              </svg>
            </div>
            <div class="emotion-legend">
              <div class="legend-item"><span class="dot" style="background:#ff6b6b"></span>焦虑 45%</div>
              <div class="legend-item"><span class="dot" style="background:#fa8c16"></span>低落 25%</div>
              <div class="legend-item"><span class="dot" style="background:#6c63ff"></span>平静 15%</div>
              <div class="legend-item"><span class="dot" style="background:#52c41a"></span>开心 15%</div>
              <div class="legend-summary">负向70% | 正向15% | 中性15%</div>
            </div>
          </div>
        </div>
      </div>

      <div class="detail-card">
        <div class="card-title">⚡ 触发因素</div>
        <div class="card-body">
          <div class="info-row"><span class="info-label">核心场景：</span>工作压力</div>
          <div class="info-row">
            <span class="info-label">关键词：</span>
            <el-tag type="warning" size="small">截止日期</el-tag>
            <el-tag type="danger" size="small">失眠</el-tag>
            <el-tag type="warning" size="small">沟通不畅</el-tag>
          </div>
          <div class="info-row"><span class="info-label">首次触发轮次：</span>第2轮</div>
        </div>
      </div>

      <div class="detail-card">
        <div class="card-title">⚠️ 风险评估</div>
        <div class="card-body">
          <div class="info-row"><span class="info-label">情绪风险：</span><el-tag type="warning" size="small">中度</el-tag></div>
          <div class="info-row"><span class="info-label">自伤风险：</span><el-tag type="success" size="small">无</el-tag></div>
          <div class="info-row"><span class="info-label">自杀风险：</span><el-tag type="success" size="small">无</el-tag></div>
          <div class="info-row"><span class="info-label">社会功能：</span>轻度受损（工作领域）</div>
          <div class="info-row"><span class="info-label">危机预警：</span>未触发</div>
        </div>
      </div>

      <div class="detail-card">
        <div class="card-title">📊 PAD情绪维度</div>
        <div class="card-body">
          <div class="info-row">愉悦度P：<span class="pad-value" style="color:#6c63ff">-0.30</span>（标准差0.15）</div>
          <div class="info-row">唤醒度A：<span class="pad-value" style="color:#fa8c16">+0.60</span>（标准差0.20）</div>
          <div class="info-row">支配度D：<span class="pad-value" style="color:#52c41a">-0.20</span>（标准差0.10）</div>
          <div class="info-row extra">情绪稳定性：0.72 | 波动幅度：0.35</div>
        </div>
      </div>

      <div class="detail-card">
        <div class="card-title">💡 AI 诊断建议</div>
        <div class="card-body">
          <div class="suggestion-row"><b>自助调节：</b>每日10分钟深呼吸放松训练，保持规律作息</div>
          <div class="suggestion-row"><b>社会支持：</b>与信任的朋友或家人倾诉，寻求情感支持</div>
          <div class="suggestion-row"><b>专业干预：</b>如持续失眠超过2周，建议就医咨询</div>
          <div class="suggestion-row"><b>建议优先级：</b><el-tag type="warning" size="small">建议寻求支持</el-tag></div>
        </div>
      </div>
    </div>

    <div class="feedback-card">
      <div class="card-title">✍️ 我的反馈</div>
      <div class="feedback-grid">
        <div class="feedback-row"><span class="info-label">诊断评分：</span>⭐⭐⭐⭐ (4/5)</div>
        <div class="feedback-row"><span class="info-label">认同风险评估：</span>✅ 认同</div>
        <div class="feedback-row"><span class="info-label">认同自助建议：</span>✅ 认同</div>
        <div class="feedback-row"><span class="info-label">认同社会支持建议：</span>✅ 认同</div>
        <div class="feedback-row"><span class="info-label">认同专业干预建议：</span>❌ 不认同</div>
        <div class="feedback-row"><span class="info-label">建议采纳情况：</span>尝试部分</div>
      </div>
      <div class="feedback-text"><span class="info-label">文字反馈：</span>建议很实用，但我觉得暂时不需要专业干预</div>
      <el-button type="primary" size="small" style="margin-top: 10px">编辑反馈</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from "vue-router";

const route = useRoute();
const sessionId = route.params.sessionId as string;
</script>

<style scoped>
.diagnosis-detail-page {
  padding: 24px;
  max-width: 1000px;
  margin: 0 auto;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #999;
  margin-bottom: 16px;
}

.breadcrumb-link {
  color: #6c63ff;
  text-decoration: none;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.detail-card,
.feedback-card {
  background: #fff;
  border-radius: 10px;
  padding: 18px;
  border: 1px solid #f0f0f0;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.card-body {
  font-size: 13px;
  line-height: 1.8;
}

.info-row {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.info-label {
  color: #999;
  flex-shrink: 0;
}

.info-extra {
  font-size: 12px;
  color: #999;
}

.emotion-chart-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.emotion-ring {
  width: 80px;
  height: 80px;
  flex-shrink: 0;
}

.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.emotion-legend {
  font-size: 13px;
  line-height: 2;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-summary {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}

.pad-value {
  font-weight: 600;
}

.suggestion-row {
  margin-bottom: 6px;
  color: #555;
  line-height: 1.7;
}

.feedback-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  font-size: 13px;
}

.feedback-row {
  margin-bottom: 4px;
}

.feedback-text {
  font-size: 13px;
  margin-top: 8px;
}
</style>