<template>
  <div class="card tabs-card">
    <el-tabs :model-value="activeTab" @update:model-value="emit('update:activeTab', String($event))">
      <!-- 变更历史 -->
      <el-tab-pane label="变更历史" name="history">
        <div class="tab-toolbar">
          <el-input
            :model-value="historyKeyword"
            placeholder="按变更摘要搜索"
            clearable
            style="width: 220px"
            @update:model-value="emit('update:historyKeyword', $event)"
            @keyup.enter="emit('searchHistory')"
            @clear="emit('searchHistory')"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="emit('searchHistory')">查询</el-button>
          <div class="toolbar-spacer"></div>
          <span class="muted">共 {{ historyTotal }} 条变更记录</span>
        </div>

        <el-table v-loading="historyLoading" :data="historyList" stripe class="history-table">
          <el-table-column prop="changeSummary" label="变更摘要" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="summary-text">{{ row.changeSummary || "-" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Prompt 变更（简要）" min-width="230">
            <template #default="{ row }">
              <div v-if="hasPromptChange(row)" class="prompt-diff">
                <div class="diff-row">
                  <span class="diff-badge">旧</span>
                  <span class="diff-text diff-old">{{ row.oldSystemPrompt || "（无）" }}</span>
                </div>
                <div class="diff-row">
                  <span class="diff-badge diff-new-badge">新</span>
                  <span class="diff-text diff-new">{{ row.newSystemPrompt || "（无）" }}</span>
                </div>
              </div>
              <span v-else class="muted">仅参数调整</span>
            </template>
          </el-table-column>
          <el-table-column label="模型变更" width="150">
            <template #default="{ row }">
              <template v-if="modelChanged(row)">
                <el-tag size="small" type="info" effect="plain">{{ modelTypeLabel(row.oldModelType) }}</el-tag>
                <span class="diff-arrow">→</span>
                <el-tag size="small" type="success" effect="plain">{{ modelTypeLabel(row.newModelType) }}</el-tag>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="90">
            <template #default="{ row }">
              <span v-if="row.oldVersion != null || row.newVersion != null">v{{ row.oldVersion ?? "?" }} → v{{ row.newVersion ?? "?" }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdByName" label="变更人" width="110">
            <template #default="{ row }">{{ row.createdByName || "-" }}</template>
          </el-table-column>
          <el-table-column prop="createdTime" label="变更时间" width="170" />
          <el-table-column label="操作" width="88" fixed="right">
            <template #default="{ row }">
              <div class="op-cell">
                <el-button type="primary" text size="small" @click="emit('viewDetail', row)">详情</el-button>
                <el-button type="warning" text size="small" @click="emit('rollback', row)">回滚</el-button>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无可展示的变更记录" :image-size="70" />
          </template>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            :current-page="historyPage"
            :page-size="historyPageSize"
            :total="historyTotal"
            :layout="paginationLayout"
            background
            @current-change="emit('changeHistoryPage', $event)"
          />
        </div>
      </el-tab-pane>

      <!-- 缓存管理 -->
      <el-tab-pane label="缓存管理" name="cache">
        <div v-loading="cacheLoading" class="cache-panel">
          <div class="cache-stats">
            <div class="stat-box">
              <div class="stat-value">{{ cacheStatus?.cacheSize ?? "-" }}</div>
              <div class="stat-label">已缓存节点数量</div>
            </div>
            <div class="stat-hint">
              <p>AI 节点配置以内存缓存服务线上调用：修改配置 / 提示词或执行回滚后，需刷新缓存使新配置生效。</p>
              <p class="sub">当前节点 key：<code class="meta-mono">{{ currentNodeKey || "-" }}</code></p>
            </div>
          </div>

          <div class="cache-keys">
            <span class="muted cache-keys-label">已缓存节点：</span>
            <div v-if="cacheStatus?.nodeKeys?.length" class="cache-key-tags">
              <el-tag
                v-for="k in cacheStatus.nodeKeys"
                :key="k"
                size="small"
                effect="light"
                :type="k === currentNodeKey ? 'success' : 'info'"
              >{{ k }}</el-tag>
            </div>
            <span v-else class="muted">缓存为空（首次调用或已清空）</span>
          </div>

          <div class="cache-actions">
            <el-button type="primary" plain :loading="cacheMutating" @click="emit('refreshAllCache')">
              <el-icon class="btn-icon"><Refresh /></el-icon>全量刷新缓存
            </el-button>
            <el-button type="primary" :loading="cacheMutating" @click="emit('refreshNodeCache')">
              <el-icon class="btn-icon"><RefreshRight /></el-icon>刷新当前节点
            </el-button>
            <el-button text @click="emit('reloadCache')">重查状态</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import type { AiNodeCacheVO } from "@/admin/api/ai-node/config";
import type { AiNodeConfigHistoryVO } from "@/admin/api/ai-node/history";

defineProps<{
  activeTab: string;
  historyKeyword: string;
  historyTotal: number;
  historyLoading: boolean;
  historyList: AiNodeConfigHistoryVO[];
  historyPage: number;
  historyPageSize: number;
  paginationLayout: string;
  cacheLoading: boolean;
  cacheMutating: boolean;
  cacheStatus: AiNodeCacheVO | null;
  currentNodeKey?: string | null;
  hasPromptChange: (row: AiNodeConfigHistoryVO) => boolean;
  modelChanged: (row: AiNodeConfigHistoryVO) => boolean;
  modelTypeLabel: (type: number | null | undefined) => string;
}>();

const emit = defineEmits<{
  (e: "update:activeTab", v: string): void;
  (e: "update:historyKeyword", v: string): void;
  (e: "searchHistory"): void;
  (e: "changeHistoryPage", page: number): void;
  (e: "viewDetail", row: AiNodeConfigHistoryVO): void;
  (e: "rollback", row: AiNodeConfigHistoryVO): void;
  (e: "refreshAllCache"): void;
  (e: "refreshNodeCache"): void;
  (e: "reloadCache"): void;
}>();
</script>

<style scoped>
.card {
  background: #ffffff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 20px 22px;
  box-shadow: var(--admin-shadow-card);
}

.tabs-card .el-tabs__item.is-active {
  color: #6366f1;
}

.tabs-card .el-tabs__item:hover {
  color: #4f46e5;
}

.tabs-card .el-tabs__active-bar {
  background-color: #6366f1;
}

.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.toolbar-spacer {
  flex: 1;
}

.muted {
  color: var(--admin-text-muted);
  font-size: 12px;
}

.summary-text {
  font-size: 13px;
  color: #334155;
}

.history-table {
  width: 100%;
}

/* 公共 .pagination-bar（display / 间距）已由 admin/styles/index.css 提供，仅保留页面特有的右对齐差异 */
.pagination-bar {
  justify-content: flex-end;
}

.prompt-diff {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.diff-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  line-height: 1.5;
  min-width: 0;
}

.diff-badge {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background: #fef2f2;
  color: #ef4444;
  margin-top: 1px;
}

.diff-new-badge {
  background: #f0fdf4;
  color: #16a34a;
}

.diff-text {
  display: inline-block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
  color: #475569;
}

.diff-new {
  color: #16a34a;
}

.diff-old {
  color: #ef4444;
}

.diff-arrow {
  color: var(--admin-text-muted);
  margin: 0 4px;
}

.cache-panel {
  padding: 4px 0;
}

.cache-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  margin-bottom: 16px;
}

.stat-box {
  min-width: 140px;
}

.stat-value {
  font-size: 34px;
  font-weight: 700;
  color: #6366f1;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--admin-text-muted);
  margin-top: 2px;
}

.stat-hint {
  flex: 1;
  min-width: 240px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.7;
}

.stat-hint p {
  margin: 0;
}

.stat-hint .sub {
  margin-top: 4px;
  color: var(--admin-text-muted);
}

.cache-keys {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 16px;
}

.cache-keys-label {
  flex-shrink: 0;
  line-height: 24px;
}

.cache-key-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.cache-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-mono {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  background: #f1f5f9;
  border-radius: 4px;
  padding: 1px 6px;
  color: #4f46e5;
}

.btn-icon {
  margin-right: 4px;
}
</style>