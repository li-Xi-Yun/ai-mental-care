<template>
  <el-dialog
    :model-value="modelValue"
    :title="detailTitle"
    width="640px"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="detail-meta">
      <span>创建时间：{{ formatTime(activeItem?.createdTime) }}</span>
      <span>字符数：{{ activeItem?.content.length ?? 0 }}</span>
      <span>L1 / L2：{{ padIdx(activeItem?.chunkLevel1Idx) }} / {{ padIdx(activeItem?.chunkLevel2Idx) }}</span>
    </div>
    <div class="detail-content">{{ activeItem?.content }}</div>
  </el-dialog>
</template>

<script setup lang="ts">
import type { FileVectorVO } from "@/admin/api/file/vector";

defineProps<{
  modelValue: boolean;
  detailTitle: string;
  activeItem: FileVectorVO | null;
  formatTime: (v?: string) => string;
  padIdx: (idx?: number) => string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
}>();
</script>

<style scoped>
.detail-meta {
  display: flex;
  gap: 20px;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--admin-border);
  font-size: 12.5px;
  color: var(--admin-text-secondary);
}

.detail-content {
  padding: 14px;
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-control);
  background: var(--admin-bg);
  font-size: 13.5px;
  line-height: 1.9;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
