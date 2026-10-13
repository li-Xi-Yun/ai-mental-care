<template>
  <div class="vector-page">
    <!-- 文件信息卡 -->
    <VectorInfoCard
      :file-name="fileName"
      :vector-status="vectorStatus"
      :suffix-text="suffixText"
      :file-size-text="fileSizeText"
      :total="total"
    />

    <!-- 统计 + 工具栏 -->
    <VectorOverviewCard
      :total="total"
      :chunk-count-l1="chunkCountL1"
      :vector-status="vectorStatus"
      :status-tone="statusTone"
      :status-icon="statusIcon"
      :is-parsing="isParsing"
      :elapsed="elapsed"
      :format-elapsed="formatElapsed"
      :submitting="submitting"
      :deleting="deleting"
      :can-load="canLoad"
      :load-blocked-reason="loadBlockedReason"
      :can-delete="canDelete"
      :load-label="loadLabel"
      @load="handleLoad"
      @interrupt="handleInterrupt"
      @delete-all="handleDeleteAll"
    />

    <!-- 向量片段列表 -->
    <VectorChunkList
      v-model:page="page"
      :vector-list="vectorList"
      :total="total"
      :page-size="pageSize"
      :list-loading="listLoading"
      :is-parsing="isParsing"
      :empty-text="emptyText"
      :pagination-layout="PAGINATION_LAYOUT"
      :format-time="formatTime"
      :pad-idx="padIdx"
      @page-change="handlePageChange"
      @refresh="loadVectors"
      @view="openDetail"
    />

    <!-- 完整内容弹窗 -->
    <VectorDetailDialog
      v-model="detailVisible"
      :detail-title="detailTitle"
      :active-item="activeItem"
      :format-time="formatTime"
      :pad-idx="padIdx"
    />
  </div>
</template>

<script setup lang="ts">
import { useFileVector } from "./useFileVector";
import VectorInfoCard from "./components/VectorInfoCard.vue";
import VectorOverviewCard from "./components/VectorOverviewCard.vue";
import VectorChunkList from "./components/VectorChunkList.vue";
import VectorDetailDialog from "./components/VectorDetailDialog.vue";

const {
  // 文件信息
  fileName,
  suffixText,
  fileSizeText,
  vectorStatus,
  // 统计 / 工具栏
  total,
  chunkCountL1,
  statusTone,
  statusIcon,
  isParsing,
  canLoad,
  loadBlockedReason,
  canDelete,
  loadLabel,
  submitting,
  deleting,
  elapsed,
  formatElapsed,
  handleLoad,
  handleInterrupt,
  handleDeleteAll,
  // 片段列表
  vectorList,
  listLoading,
  page,
  pageSize,
  emptyText,
  loadVectors,
  handlePageChange,
  PAGINATION_LAYOUT,
  // 详情弹窗
  detailVisible,
  detailTitle,
  activeItem,
  formatTime,
  padIdx,
  openDetail,
} = useFileVector();
</script>

<style scoped>
/* 页面局部变量统一映射到 admin token（供 file/vector 子组件消费） */
.vector-page {
  --primary: var(--admin-primary);
  --primary-hover: var(--admin-primary-hover);
  --primary-bg: var(--admin-primary-soft);
  --text-main: var(--admin-text-primary);
  --text-sub: var(--admin-text-secondary);
  --text-muted: var(--admin-text-muted);
  --border: var(--admin-border);
  --bg-soft: var(--admin-bg);
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
