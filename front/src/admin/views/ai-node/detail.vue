<template>
  <div class="ai-node-detail-page">
    <!-- 加载态 -->
    <div v-if="pageLoading" class="page-state">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 失败态 -->
    <div v-else-if="pageError || !detail" class="page-state">
      <el-empty description="节点详情加载失败">
        <el-button type="primary" @click="handleReload">重新加载</el-button>
      </el-empty>
    </div>

    <template v-else>
      <!-- 顶部概览 -->
      <OverviewCard
        :node-name="form.nodeName"
        :enabled-tag-type="enabledTagType"
        :enabled-label="enabledLabel"
        :version="detail.version"
        :node-key="detail.nodeKey"
        :node-group="form.nodeGroup"
        :model-label="currentModelLabel"
        :sort="form.sort"
        :created-time="detail.createdTime"
        :updated-time="detail.updatedTime"
        :loading="pageLoading"
        :saving="savingConfig"
        @reload="handleReload"
        @save="handleSaveConfig"
      />

      <!-- 基本信息 + 模型设置 -->
      <div class="info-grid">
        <InfoFormCard :form="form" :node-key="detail.nodeKey" />
        <ModelFormCard
          :form="form"
          :model-type-options="modelTypeOptions"
          :model-name-label="modelNameLabel"
          :model-name-value="modelNameValue"
          @update:model-name-value="modelNameValue = $event"
        />
      </div>

      <!-- 生成参数 + 重试策略 -->
      <ParamsFormCard :form="form" />

      <!-- System Prompt -->
      <PromptCard
        v-model="promptText"
        :version="promptVersion"
        :saving="savingPrompt"
        @cancel="loadPrompt"
        @save="handleSavePrompt"
      />

      <!-- 变更历史 / 缓存管理 -->
      <HistoryCacheTabs
        v-model:active-tab="activeTab"
        v-model:history-keyword="historyKeyword"
        :history-total="historyTotal"
        :history-loading="historyLoading"
        :history-list="historyList"
        :history-page="historyPage"
        :history-page-size="historyPageSize"
        :pagination-layout="PAGINATION_LAYOUT"
        :cache-loading="cacheLoading"
        :cache-mutating="cacheMutating"
        :cache-status="cacheStatus"
        :current-node-key="detail.nodeKey"
        :has-prompt-change="hasPromptChange"
        :model-changed="modelChanged"
        :model-type-label="modelTypeLabel"
        @search-history="handleHistorySearch"
        @change-history-page="loadHistory"
        @view-detail="openHistoryDetail"
        @rollback="handleRollback"
        @refresh-all-cache="handleRefreshAllCache"
        @refresh-node-cache="handleRefreshNodeCache"
        @reload-cache="loadCacheStatus"
      />

      <!-- 变更历史详情弹窗 -->
      <HistoryDetailDialog
        v-model="historyDialogVisible"
        :loading="historyDetailLoading"
        :detail="historyDetail"
        :version-range="versionRange"
        :model-changed="modelChanged"
        :model-type-label="modelTypeLabel"
        @closed="historyDetail = null"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { useAiNodeDetail } from "./useAiNodeDetail";
import OverviewCard from "./components/OverviewCard.vue";
import InfoFormCard from "./components/InfoFormCard.vue";
import ModelFormCard from "./components/ModelFormCard.vue";
import ParamsFormCard from "./components/ParamsFormCard.vue";
import PromptCard from "./components/PromptCard.vue";
import HistoryCacheTabs from "./components/HistoryCacheTabs.vue";
import HistoryDetailDialog from "./components/HistoryDetailDialog.vue";

const {
  modelTypeOptions,
  pageLoading,
  pageError,
  detail,
  savingConfig,
  savingPrompt,
  activeTab,
  form,
  promptText,
  promptVersion,
  cacheStatus,
  cacheLoading,
  cacheMutating,
  historyList,
  historyTotal,
  historyPage,
  historyLoading,
  historyKeyword,
  historyDialogVisible,
  historyDetailLoading,
  historyDetail,
  enabledTagType,
  enabledLabel,
  modelNameLabel,
  modelNameValue,
  currentModelLabel,
  versionRange,
  modelTypeLabel,
  modelChanged,
  hasPromptChange,
  loadPrompt,
  loadHistory,
  loadCacheStatus,
  handleReload,
  handleSaveConfig,
  handleSavePrompt,
  handleHistorySearch,
  openHistoryDetail,
  handleRollback,
  handleRefreshAllCache,
  handleRefreshNodeCache,
  PAGINATION_LAYOUT,
  DEFAULT_PAGE_SIZE,
} = useAiNodeDetail();

const historyPageSize = DEFAULT_PAGE_SIZE;
</script>

<style scoped>
.ai-node-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: #1e293b;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

/* ---------- 页面状态 ---------- */
.page-state {
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 40px 24px;
  box-shadow: var(--admin-shadow-card);
}
</style>