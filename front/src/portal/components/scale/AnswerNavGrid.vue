<template>
  <div class="nav-grid">
    <div class="nav-grid-title">
      <span>答题导航</span>
      <span class="nav-grid-hint">{{ answeredCount }} / {{ totalCount }} 已答</span>
    </div>
    <div class="nav-grid-body">
      <button
        v-for="(cell, index) in cells"
        :key="cell.questionId"
        class="nav-cell"
        :class="cell.classes"
        :title="cell.title"
        @click="$emit('jump', index)"
      >{{ index + 1 }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
interface NavCell {
  questionId: number;
  title: string;
  classes: Record<string, boolean>;
}

defineProps<{
  answeredCount: number;
  totalCount: number;
  cells: NavCell[];
}>();

defineEmits<{
  (e: "jump", index: number): void;
}>();
</script>

<style scoped>
.nav-grid {
  margin-top: 26px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 16px 18px;
}

.nav-grid-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 13px;
  color: #666;
}

.nav-grid-hint {
  font-size: 12px;
  color: #6c63ff;
  font-weight: 600;
}

.nav-grid-body {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.nav-cell {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1.5px solid #e8e8e8;
  background: #fff;
  font-size: 13px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: inherit;
}

.nav-cell:hover {
  border-color: #6c63ff;
  background: #f8f7ff;
}

.nav-cell.done {
  background: #f0eeff;
  border-color: #6c63ff;
  color: #6c63ff;
}

.nav-cell.required-missing {
  border-color: #ffccc7;
  background: #fff1f0;
  color: #ff4d4f;
}

.nav-cell.active {
  border-color: #6c63ff;
  background: #6c63ff;
  color: #fff;
  box-shadow: 0 3px 10px rgba(108, 99, 255, 0.3);
}
</style>