<template>
  <section class="es-data-panel">
    <h4><span class="es-icon">🎭</span>情绪占比分析</h4>
    <div v-if="emotionSegments.length || hasRatioData" class="emotion-wrap">
      <div v-if="emotionSegments.length" class="ring-box">
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
      <div v-else class="ring-empty">暂无核心情绪分布</div>
      <div class="ratio-legend">
        <div class="ratio-legend-item" v-for="r in ratioSummary" :key="r.label" :class="{ 'is-selected': selectedRatio === r.label }" role="button" tabindex="0" @click="toggleRatio(r.label)" @keydown.enter="toggleRatio(r.label)">
          <span class="ratio-dot" :style="{ backgroundColor: r.color }"></span>
          <span class="ratio-name">{{ r.label }}</span>
          <span class="ratio-pct">{{ r.value != null ? `${(r.value * 100).toFixed(0)}%` : "--" }}</span>
        </div>
      </div>
    </div>
    <div v-else class="no-emotion">暂无情绪占比数据</div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
const props = defineProps<{
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

const selectedRatio = ref<string | null>(null);

function toggleRatio(label: string): void {
  selectedRatio.value = selectedRatio.value === label ? null : label;
}

const hasRatioData = computed(() => props.ratioSummary.some((item) => item.value != null));
</script>

<style scoped>
.emotion-wrap {
  display: flex;
  gap: 24px;
  align-items: center;
}

.ring-box {
  position: relative;
  width: 140px;
  height: 140px;
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
  font-size: 10px;
  color: #999;
}

.ring-center-value {
  max-width: 82px;
  font-size: clamp(11px, 2.8vw, 15px);
  font-weight: 700;
  color: #1a1a2e;
  margin-top: 2px;
  text-align: center;
  overflow-wrap: anywhere;
}

.ring-center-sub {
  font-size: 12px;
  font-weight: 600;
  color: #6c63ff;
  margin-top: 2px;
}

.ratio-legend {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ratio-legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #555;
  padding: 3px 4px;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.ratio-legend-item:hover,
.ratio-legend-item.is-selected {
  background: #f7f5ff;
}

.ratio-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex-shrink: 0;
}

.ratio-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ratio-pct {
  font-size: 12px;
  font-weight: 600;
  color: #333;
}

.ring-empty,
.no-emotion {
  flex: 1;
  color: #999;
  font-size: 12px;
  text-align: center;
  padding: 20px 0;
}

.no-emotion { display: block; }
</style>