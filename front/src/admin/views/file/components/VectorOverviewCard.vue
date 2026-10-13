<template>
  <div class="overview-card">
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon indigo"><el-icon :size="20"><Document /></el-icon></div>
        <div class="stat-info">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">向量片段总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple"><el-icon :size="20"><CollectionTag /></el-icon></div>
        <div class="stat-info">
          <div class="stat-num">{{ chunkCountL1 }}</div>
          <div class="stat-label">一级分块 L1</div>
        </div>
      </div>
      <div class="stat-card" :class="statusTone">
        <div class="stat-icon">
          <el-icon :size="20" :class="{ 'is-loading': isParsing }"><component :is="statusIcon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-num is-text">{{ vectorStatus.label }}</div>
          <div class="stat-label">加载状态</div>
        </div>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <span v-if="isParsing" class="toolbar-hint">
          <el-icon class="is-loading" :size="14"><Loading /></el-icon>
          向量解析进行中，可随时中断
        </span>
        <span v-else-if="loadBlockedReason" class="toolbar-hint toolbar-hint-warn">
          <el-icon :size="14"><Warning /></el-icon>
          {{ loadBlockedReason }}
        </span>
        <span v-else class="toolbar-hint">文件将按层级切分为向量片段，供 AI 知识库检索</span>
      </div>
      <div class="toolbar-right">
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="submitting"
          :disabled="!canLoad"
          @click="emit('load')"
        >
          {{ loadLabel }}
        </el-button>
        <el-button type="warning" plain :icon="VideoPause" :disabled="!isParsing" @click="emit('interrupt')">
          中断加载
        </el-button>
        <el-button
          type="danger"
          plain
          :icon="Delete"
          :disabled="!canDelete"
          :loading="deleting"
          @click="emit('deleteAll')"
        >
          删除全部向量
        </el-button>
      </div>
    </div>

    <!-- 解析进度条 -->
    <div v-if="isParsing" class="parsing-banner">
      <div class="parsing-top">
        <span class="parsing-title">正在向量化解析中</span>
        <span class="parsing-sub">已耗时 {{ formatElapsed(elapsed) }} · 完成后将自动刷新片段列表</span>
      </div>
      <el-progress
        class="parsing-progress"
        :percentage="100"
        :indeterminate="true"
        :duration="2"
        :stroke-width="4"
        :show-text="false"
        color="#6366f1"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from "vue";
import { CollectionTag, Delete, Document, Loading, Refresh, VideoPause, Warning } from "@element-plus/icons-vue";
import type { VectorStatusMeta } from "../useFileVector";

defineProps<{
  total: number;
  chunkCountL1: number;
  vectorStatus: VectorStatusMeta;
  statusTone: string;
  statusIcon: Component;
  isParsing: boolean;
  elapsed: number;
  formatElapsed: (sec: number) => string;
  submitting: boolean;
  deleting: boolean;
  canLoad: boolean;
  canDelete: boolean;
  loadLabel: string;
  /** 无法向量化的原因（如知识类型为「无」），非空时展示警告提示 */
  loadBlockedReason?: string;
}>();

const emit = defineEmits<{
  (e: "load"): void;
  (e: "interrupt"): void;
  (e: "deleteAll"): void;
}>();
</script>

<style scoped>
.overview-card {
  padding: 20px;
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  box-shadow: var(--admin-shadow-card);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--admin-border);
  border-radius: 12px;
  background: var(--bg-soft);
  transition: border-color 0.2s ease;
}

.stat-card:hover {
  border-color: #e0e7ff;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-icon.indigo {
  background: var(--primary-bg);
  color: var(--primary);
}

.stat-icon.purple {
  background: #f5f3ff;
  color: #8b5cf6;
}

.stat-card.tone-success .stat-icon {
  background: #f0fdf4;
  color: #22c55e;
}

.stat-card.tone-danger .stat-icon {
  background: #fef2f2;
  color: #ef4444;
}

.stat-card.tone-warning .stat-icon {
  background: #fffbeb;
  color: #f59e0b;
}

.stat-card.tone-primary .stat-icon {
  background: var(--primary-bg);
  color: var(--primary);
}

.stat-card.tone-default .stat-icon {
  background: #f1f5f9;
  color: var(--text-sub);
}

.stat-info {
  min-width: 0;
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.stat-num.is-text {
  font-size: 16px;
  line-height: 24px;
}

.stat-label {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-muted);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--admin-border);
}

.toolbar-left {
  min-width: 0;
}

.toolbar-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-muted);
}

.toolbar-hint-warn {
  color: #d97706;
}

.toolbar-right {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.parsing-banner {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 10px;
  background: linear-gradient(90deg, #eef2ff, #f5f3ff);
  border: 1px solid #e0e7ff;
}

.parsing-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.parsing-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #3730a3;
}

.parsing-sub {
  font-size: 12px;
  color: #6b7280;
}

.parsing-progress {
  margin-top: 10px;
}
</style>
