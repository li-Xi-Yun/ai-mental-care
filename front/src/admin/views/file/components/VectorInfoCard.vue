<template>
  <div class="file-card">
    <div class="file-icon-box">
      <el-icon :size="26"><Document /></el-icon>
    </div>
    <div class="file-meta">
      <div class="file-name-row">
        <span class="file-name">{{ fileName }}</span>
        <el-tag size="small" effect="light" :type="vectorStatus.tagType">{{ vectorStatus.label }}</el-tag>
      </div>
      <div class="file-sub">
        <span>{{ suffixText }}</span>
        <span class="dot">·</span>
        <span>{{ fileSizeText }}</span>
        <span class="dot">·</span>
        <span>向量片段 {{ total }} 个</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Document } from "@element-plus/icons-vue";
import type { VectorStatusMeta } from "../useFileVector";

defineProps<{
  fileName: string;
  vectorStatus: VectorStatusMeta;
  suffixText: string;
  fileSizeText: string;
  total: number;
}>();
</script>

<style scoped>
.file-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  box-shadow: var(--admin-shadow-card);
}

.file-icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 12px;
  color: #fff;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--primary), #8b5cf6);
  box-shadow: 0 6px 14px rgba(99, 102, 241, 0.35);
}

.file-meta {
  min-width: 0;
}

.file-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-sub);
}

.file-sub .dot {
  color: #cbd5e1;
}
</style>
