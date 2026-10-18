<template>
  <section class="es-data-panel">
    <h4>{{ title }}</h4>
    <div v-if="entries.length" class="bar-list">
      <div class="bar-row" v-for="(e, i) in entries" :key="e.label" :class="{ 'is-selected': selectedLabel === e.label }" role="button" tabindex="0" @click="toggleEntry(e.label)" @keydown.enter="toggleEntry(e.label)">
        <span class="bar-name">{{ e.label }}</span>
        <div class="bar-track" :style="{ backgroundColor: trackColorOf(colorOf(e.label, i)) }">
          <div class="bar-fill" :style="fillStyleOf(e, i)"></div>
        </div>
        <span class="bar-value" :style="{ color: colorOf(e.label, i) }">{{ e.value }}</span>
      </div>
    </div>
    <div v-else style="font-size: 11px; color: #999; padding: 6px 0; text-align: center;">暂无数据</div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

const props = defineProps<{
  title: string;
  entries: { label: string; value: number }[];
  colorOf: (label: string, index: number) => string;
}>();

const selectedLabel = ref<string | null>(null);

function toggleEntry(label: string): void {
  selectedLabel.value = selectedLabel.value === label ? null : label;
}

/** 轨道浅色底映射 */
function trackColorOf(color: string): string {
  const map: Record<string, string> = {
    "#ff6b6b": "#fff0f0",
    "#fa8c16": "#fff8f0",
    "#4ecdc4": "#f0fff5",
    "#6c63ff": "#f5f0ff",
    "#52c41a": "#f0fff5",
  };
  const hit = map[color];
  if (hit) return hit;
  // 已是 8 位 hex（#RRGGBBAA）直接返回，否则追加 10% alpha
  return color.length === 9 ? color : `${color}1a`;
}

const maxVal = computed(() => {
  let m = 0;
  for (const e of props.entries) {
    if (e.value > m) m = e.value;
  }
  return m;
});

function barWidth(value: number): string {
  if (!maxVal.value) return "0%";
  const w = (value / maxVal.value) * 1.25 * 100;
  const clamped = Math.min(100, Math.max(0, w));
  return `${clamped}%`;
}

function fillStyleOf(e: { label: string; value: number }, i: number) {
  const color = props.colorOf(e.label, i);
  return {
    width: barWidth(e.value),
    background: `linear-gradient(90deg, ${color}, ${color}cc)`,
  };
}
</script>

<style scoped>
.bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 4px;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.15s, transform 0.15s;
}

.bar-row:hover,
.bar-row.is-selected {
  background: #f7f5ff;
  transform: translateX(2px);
}

.bar-name {
  min-width: 84px;
  max-width: 150px;
  flex: 0 1 auto;
  font-size: 11px;
  color: #666;
  overflow: visible;
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.35;
}

.bar-track {
  flex: 1;
  height: 18px;
  border-radius: 9px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 9px;
  transition: width 0.5s;
}

.bar-value {
  width: 48px;
  flex-shrink: 0;
  text-align: right;
  font-size: 11px;
  font-weight: 600;
}
</style>