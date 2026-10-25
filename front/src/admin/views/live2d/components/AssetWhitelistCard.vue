<template>
  <div class="info-card">
    <div class="whist-card-head">
      <span class="kind-dot" :class="kind"></span>
      <span class="whist-title">{{ title }}</span>
      <span class="whist-count">{{ entries.length }}</span>
      <button class="whist-edit" title="编辑中文含义" @click="emit('edit')">
        <el-icon :size="13"><Edit /></el-icon>编辑标签
      </button>
    </div>
    <div v-if="entries.length" class="asset-grid">
      <div
        v-for="[id, label] in entries"
        :key="id"
        class="asset-tile"
        :title="label ? `${id}（${label}）` : id"
      >
        <span class="asset-id">{{ id }}</span>
        <span v-if="label" class="asset-label">{{ label }}</span>
        <span v-else class="asset-label is-missing">未填</span>
      </div>
    </div>
    <div v-else class="value-empty">{{ emptyText }}</div>
  </div>
</template>

<script setup lang="ts">
import { Edit } from "@element-plus/icons-vue";

/**
 * Live2D 白名单展示卡片（宫格布局）
 *
 * 每个资产一个小圆角卡片：上方等宽 ascii 资产 id、下方中文含义。
 * 未填中文的资产显示浅灰「未填」占位；表情（绿点）/动作（紫点）用 kind 圆点区分。
 * 「编辑标签」统一交给父组件（AssetLabelEditor 一次编辑表情+动作两套白名单）。
 */
defineProps<{
  /** 卡片标题，如「表情白名单」 */
  title: string;
  /** 资产类型：expression / motion（决定标题前的圆点配色） */
  kind: "expression" | "motion";
  /** 白名单 entries：资产 id → 中文含义（value 空串 = 未填） */
  entries: [string, string][];
  /** 空态文案，如「暂无表情资产」 */
  emptyText: string;
}>();

const emit = defineEmits<{
  (e: "edit"): void;
}>();
</script>

<style scoped>
.info-card {
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 16px 18px;
  box-shadow: var(--admin-shadow-card);
}

/* ---------- 卡片头 ---------- */
.whist-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.kind-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.kind-dot.expression {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.kind-dot.motion {
  background: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.whist-title {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.whist-count {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 999px;
  padding: 1px 8px;
}

.whist-edit {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  font-size: 12px;
  color: #6c63ff;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 6px;
}

.whist-edit:hover {
  background: #f0eeff;
}

/* ---------- 宫格 ---------- */
.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 8px;
}

.asset-tile {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px 10px;
  min-width: 0;
  transition: border-color 0.15s, background 0.15s;
}

.asset-tile:hover {
  border-color: #c7ccf7;
  background: #f5f6ff;
}

.asset-id {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-label {
  font-size: 12.5px;
  color: #475569;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-label.is-missing {
  color: #cbd5e1;
  font-style: italic;
}

.value-empty {
  font-size: 12px;
  color: #cbd5e1;
}
</style>