<template>
  <section class="panel">
    <div class="panel-title"><span class="panel-icon">🎭</span>情绪占比分析</div>
    <div v-if="emotionSegments.length" class="emotion-wrap">
      <div class="ring-box">
        <svg viewBox="0 0 100 100" class="ring-svg">
          <circle
            cx="50"
            cy="50"
            :r="ringRadius"
            fill="none"
            stroke="#f0f0f7"
            stroke-width="12"
          />
          <circle
            v-for="(seg, i) in emotionSegments"
            :key="seg.label"
            cx="50"
            cy="50"
            :r="ringRadius"
            fill="none"
            :stroke="emotionColor(seg.label, i)"
            stroke-width="12"
            :stroke-dasharray="`${(seg.percent / 100) * ringCircumference} ${ringCircumference}`"
            :stroke-dashoffset="`${-(seg.start / 100) * ringCircumference}`"
            stroke-linecap="butt"
          />
        </svg>
        <div class="ring-center">
          <span class="ring-center-label">核心情绪</span>
          <span class="ring-center-value">{{ coreEmotionLabel ?? "--" }}</span>
          <span class="ring-center-sub">{{ coreEmotionShare }}%</span>
        </div>
      </div>
      <div class="legend">
        <div class="legend-item" v-for="(e, i) in emotionEntries.slice(0, 5)" :key="e.label">
          <span class="legend-dot" :style="{ backgroundColor: emotionColor(e.label, i) }"></span>
          <span class="legend-name">{{ e.label }}</span>
          <span class="legend-pct">{{ toPercent(e.value).toFixed(0) }}%</span>
        </div>
      </div>
    </div>
    <div v-else class="no-emotion">暂无情绪占比数据</div>
    <div v-if="ratioSummary.length" class="ratio-summary">
      <span class="ratio-item" v-for="r in ratioSummary" :key="r.label">
        <i class="ratio-dot" :style="{ backgroundColor: r.color }"></i>
        {{ r.label }}
        {{ r.value != null ? `${(r.value * 100).toFixed(0)}%` : "--" }}
      </span>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  emotionSegments: { label: string; percent: number; start: number }[];
  coreEmotionLabel?: string;
  coreEmotionShare: string;
  emotionEntries: { label: string; value: number }[];
  ratioSummary: { label: string; value: number | null; color: string }[];
  emotionColor: (label: string, index: number) => string;
  toPercent: (v?: number | null) => number;
  ringRadius: number;
  ringCircumference: number;
}>();
</script>

<style scoped>
.panel {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  padding: 20px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 16px;
}

.panel-icon {
  font-size: 16px;
}

.emotion-wrap {
  display: flex;
  gap: 28px;
  align-items: center;
}

.ring-box {
  position: relative;
  width: 150px;
  height: 150px;
  flex-shrink: 0;
}

.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-center-label {
  font-size: 11px;
  color: #b0b6c4;
}

.ring-center-value {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
  margin-top: 2px;
}

.ring-center-sub {
  font-size: 13px;
  font-weight: 600;
  color: #6c63ff;
  margin-top: 2px;
}

.legend {
  flex: 1;
  min-width: 0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  font-size: 13px;
  color: #555;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.legend-name {
  flex: 1;
}

.legend-pct {
  font-weight: 600;
  color: #333;
}

.no-emotion {
  color: #999;
  font-size: 13px;
  text-align: center;
  padding: 20px 0;
}

.ratio-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #e6e8f0;
}

.ratio-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #666;
}

.ratio-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
</style>