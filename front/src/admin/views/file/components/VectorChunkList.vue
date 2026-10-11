<template>
  <div class="list-section">
    <div class="section-head">
      <div class="section-title-wrap">
        <h3 class="section-title">向量片段列表</h3>
        <span v-if="total > 0" class="section-count">共 {{ total }} 条</span>
      </div>
      <el-button :icon="Refresh" circle plain size="small" title="刷新列表" @click="emit('refresh')" />
    </div>

    <div v-if="isParsing && total === 0" class="skeleton-list">
      <div v-for="n in 3" :key="n" class="skeleton-card">
        <div class="skeleton-badges">
          <span class="sk-badge" />
          <span class="sk-badge" />
        </div>
        <div class="skeleton-lines">
          <div class="sk-line" style="width: 92%" />
          <div class="sk-line" style="width: 74%" />
          <div class="sk-line" style="width: 40%" />
        </div>
      </div>
    </div>

    <el-empty v-else-if="!listLoading && total === 0" :description="emptyText" :image-size="120" />

    <div v-else class="chunk-list" v-loading="listLoading">
      <div v-for="item in vectorList" :key="item.id" class="chunk-card">
        <div class="chunk-left">
          <span class="chunk-badge l1">L1 · {{ padIdx(item.chunkLevel1Idx) }}</span>
          <span class="chunk-badge l2">L2 · {{ padIdx(item.chunkLevel2Idx) }}</span>
        </div>
        <div class="chunk-main">
          <p class="chunk-content">{{ item.content }}</p>
          <div class="chunk-foot">
            <span class="chunk-char">{{ item.content.length }} 字</span>
            <span class="chunk-time">
              <el-icon :size="12"><Clock /></el-icon>
              {{ formatTime(item.createdTime) }}
            </span>
            <el-button text type="primary" size="small" :icon="View" class="chunk-view" @click="emit('view', item)">
              查看完整内容
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pagination-bar">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        :layout="paginationLayout"
        background
        @update:current-page="emit('update:page', $event)"
        @current-change="emit('pageChange', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Clock, Refresh, View } from "@element-plus/icons-vue";
import type { FileVectorVO } from "@/admin/api/file/vector";

defineProps<{
  vectorList: FileVectorVO[];
  total: number;
  page: number;
  pageSize: number;
  listLoading: boolean;
  isParsing: boolean;
  emptyText: string;
  paginationLayout: string;
  formatTime: (v?: string) => string;
  padIdx: (idx?: number) => string;
}>();

const emit = defineEmits<{
  (e: "update:page", v: number): void;
  (e: "pageChange", v: number): void;
  (e: "refresh"): void;
  (e: "view", item: FileVectorVO): void;
}>();
</script>

<style scoped>
.list-section {
  padding: 20px;
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  box-shadow: var(--admin-shadow-card);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.section-count {
  font-size: 12px;
  color: var(--text-muted);
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 160px;
}

.chunk-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--admin-border);
  border-radius: 12px;
  background: #fff;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.chunk-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.08);
  transform: translateY(-1px);
}

.chunk-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 76px;
  flex-shrink: 0;
}

.chunk-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.chunk-badge.l1 {
  background: var(--primary-bg);
  color: var(--primary);
}

.chunk-badge.l2 {
  background: #f5f3ff;
  color: #8b5cf6;
}

.chunk-main {
  min-width: 0;
  flex: 1;
}

.chunk-content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  word-break: break-word;
}

.chunk-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.chunk-char {
  flex-shrink: 0;
}

.chunk-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.chunk-view {
  margin-left: auto;
}

/* ==================== 骨架屏 ==================== */

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--admin-border);
  border-radius: 12px;
  background: #fff;
}

.skeleton-badges {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 76px;
  flex-shrink: 0;
}

.sk-badge {
  height: 22px;
  border-radius: 6px;
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 4px;
}

.sk-line {
  height: 12px;
  border-radius: 6px;
}

.sk-badge,
.sk-line {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 37%, #f1f5f9 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

@keyframes skeleton-loading {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
