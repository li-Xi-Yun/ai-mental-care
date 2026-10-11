<template>
  <div class="answer-header">
    <div class="header-info">
      <h2 class="scale-title">{{ scaleName }}</h2>
      <div class="scale-meta">
        <span class="meta-item">共 {{ totalQuestionCount }} 题</span>
        <span class="meta-divider">·</span>
        <span class="meta-item">预计 {{ estimatedMinutes }} 分钟</span>
        <template v-if="versionNo">
          <span class="meta-divider">·</span>
          <span class="meta-item">版本 {{ versionNo }}</span>
        </template>
      </div>
      <p v-if="description" class="scale-desc">{{ description }}</p>
    </div>

    <div v-if="timeLimit > 0" class="timer-box" :class="{ 'is-warning': timeWarning }">
      <span class="timer-label">剩余时间</span>
      <span class="timer-value">{{ formattedTime }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface ScaleInfo {
  scaleName: string;
  totalQuestionCount: number;
  versionNo?: string;
  description?: string;
  timeLimit: number;
}

defineProps<ScaleInfo & {
  estimatedMinutes: number;
  timeWarning: boolean;
  formattedTime: string;
}>();
</script>

<style scoped>
.answer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.header-info {
  min-width: 0;
}

.scale-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.3;
  margin-bottom: 6px;
}

.scale-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #888;
}

.meta-item {
  white-space: nowrap;
}

.meta-divider {
  color: #d0d0d0;
}

.scale-desc {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  margin-top: 6px;
}

.timer-box {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  background: #f0eeff;
  border: 1px solid #d9d2ff;
  border-radius: 12px;
  padding: 10px 18px;
  min-width: 110px;
}

.timer-box.is-warning {
  background: #fff1f0;
  border-color: #ffa39e;
}

.timer-label {
  font-size: 11px;
  color: #6c63ff;
}

.timer-box.is-warning .timer-label {
  color: #ff4d4f;
}

.timer-value {
  font-size: 20px;
  font-weight: 700;
  color: #3f3d9e;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5px;
}

.timer-box.is-warning .timer-value {
  color: #ff4d4f;
}
</style>