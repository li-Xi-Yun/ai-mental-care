<template>
  <header class="report-header">
    <div class="hero">
      <div class="hero-info">
        <h1 class="hero-title">诊断详情</h1>
        <p class="hero-sub">
          {{ roundNumText }} · 创建 {{ formatDateTime(diagnosis?.createdTime) }} · 更新
          {{ formatDateTime(diagnosis?.updatedTime) }}
        </p>
      </div>
      <div class="core-badge">
        <span class="core-badge-dot"></span>
        <div class="core-badge-text">
          <span class="core-badge-label">核心情绪</span>
          <strong class="core-badge-value">{{ diagnosis?.coreEmotionLabel ?? "--" }}</strong>
        </div>
        <div class="core-badge-meta">
          <div>置信度 {{ formatPercent(diagnosis?.coreEmotionConfAvg) }}</div>
          <div>强度 {{ formatPercent(diagnosis?.coreEmotionIntensityScore) }}</div>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import type { EmotionDiagnosisVO } from "@/portal/api/conversation/emotion-diagnosis";

defineProps<{
  diagnosis: EmotionDiagnosisVO | null;
  roundNumText: string;
  formatDateTime: (v?: string) => string;
  formatPercent: (v?: number | null, digits?: number) => string;
}>();
</script>

<style scoped>
.report-header {
  margin-bottom: 16px;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
  background: linear-gradient(135deg, #f0eeff 0%, #f7f6ff 60%, #ffffff 100%);
  border: 1px solid #e6e3ff;
  border-radius: 16px;
}

.hero-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.hero-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: #999;
}

.core-badge {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-radius: 999px;
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  color: #fff;
  box-shadow: 0 4px 14px rgba(108, 99, 255, 0.35);
  flex-shrink: 0;
}

.core-badge-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.25);
  animation: pulse 2s infinite;
  flex-shrink: 0;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

.core-badge-label {
  display: block;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
}

.core-badge-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
}

.core-badge-meta {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  text-align: right;
  line-height: 1.6;
  flex-shrink: 0;
}
</style>