<template>
  <div class="symptom-page">
    <!-- ==================== 页头 ==================== -->
    <PageHeader title="症状字典" subtitle="维护症状术语、分类与严重等级">
      <el-button type="primary" @click="handleAdd">
        <el-icon class="btn-icon"><Plus /></el-icon>新增症状
      </el-button>
    </PageHeader>

    <!-- ==================== 统计卡 ==================== -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">💊</div>
        <div class="stat-body">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">症状总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-amber">🗂️</div>
        <div class="stat-body">
          <div class="stat-num">{{ categoryOptions.length }}</div>
          <div class="stat-label">症状大类</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">✅</div>
        <div class="stat-body">
          <div class="stat-num">{{ enabledCount }}</div>
          <div class="stat-label">启用症状</div>
        </div>
      </div>
    </div>

    <!-- ==================== 筛选卡 ==================== -->
    <div class="filter-card">
      <!-- 搜索条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.symptomTerm"
          placeholder="搜索症状术语 / 同义词"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="searchForm.symptomCategory" placeholder="症状大类" clearable style="width: 140px" @change="handleSearch">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
        <el-button :danger="true" :disabled="!selectedIds.length" @click="handleBatchDelete">批量删除</el-button>
      </div>
    </div>

    <!-- ==================== 主卡片 ==================== -->
    <div class="content-card">
      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="symptom-table"
        @selection-change="handleSelectionChange"
        @row-click="handleRowClick"
      >
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column label="症状术语" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="term-name">{{ row.symptomTerm }}</span>
          </template>
        </el-table-column>
        <el-table-column label="症状大类" width="120">
          <template #default="{ row }">
            <span class="cat-tag" :style="categoryStyle(row.symptomCategory)">
              <span class="cat-dot" :style="{ background: categoryStyle(row.symptomCategory).color }" />
              {{ row.symptomCategory }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="同义词" min-width="200">
          <template #default="{ row }">
            <div class="syn-list">
              <span v-for="(w, i) in row.synonymWords" :key="i" class="syn-chip">{{ w }}</span>
              <span v-if="!row.synonymWords?.length" class="syn-empty">暂无同义词</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="默认严重等级" width="150" align="center">
          <template #default="{ row }">
            <div class="sev-cell">
              <span class="sev-bars">
                <span
                  v-for="n in SYMPTOM_MAX_SEVERITY"
                  :key="n"
                  class="sev-bar"
                  :class="{ on: n <= (row.severityDefault ?? 0) }"
                  :style="n <= (row.severityDefault ?? 0) ? { background: levelColor(row.severityDefault) } : {}"
                />
              </span>
              <span class="sev-text" :style="{ color: levelColor(row.severityDefault) }">
                {{ severityText(row.severityDefault) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="86" align="center">
          <template #default="{ row }">
            <span @click.stop>
              <el-switch
                :model-value="row.status === 1"
                @change="(val: string | number | boolean) => handleStatusToggle(row as SymptomRow, val)"
              />
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="130">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="108" align="center" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button text size="small" class="op-btn op-edit" @click.stop="handleEdit(row as SymptomRow)">编辑</el-button>
              <el-button text size="small" class="op-btn op-del" @click.stop="handleDelete(row as SymptomRow)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="selected-info" v-if="selectedIds.length">已选 {{ selectedIds.length }} 项</span>
        <span v-else class="page-total">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- ==================== 新增 / 编辑弹窗 ==================== -->
    <SymptomFormDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :submitting="submitting"
      :target="dialogTarget"
      :category-options="categoryOptions"
      @submit="handleSubmit"
    />

    <!-- ==================== 详情弹窗 ==================== -->
    <SymptomDetailDialog
      v-model="detailVisible"
      :row="detailRow"
      :detail="detailData"
      :loading="detailLoading"
    />
  </div>
</template>

<script setup lang="ts">
import { Search, Plus, RefreshRight } from "@element-plus/icons-vue";
import PageHeader from "@/admin/components/PageHeader.vue";
import { useSymptomList } from "./useSymptomList";
import type { SymptomRow } from "./useSymptomList";
import SymptomFormDialog from "./components/SymptomFormDialog.vue";
import SymptomDetailDialog from "./components/SymptomDetailDialog.vue";

const {
  // 展示工具
  formatTime,
  categoryStyle,
  levelColor,
  severityText,
  // 列表
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  searchForm,
  categoryOptions,
  enabledCount,
  selectedIds,
  handleSelectionChange,
  loadData,
  handleSearch,
  handleReset,
  handleStatusToggle,
  // 弹窗
  dialogVisible,
  dialogTitle,
  dialogTarget,
  submitting,
  handleAdd,
  handleEdit,
  handleSubmit,
  // 详情
  detailVisible,
  detailLoading,
  detailRow,
  detailData,
  openDetailDialog,
  // 删除
  handleDelete,
  handleBatchDelete,
  // 常量
  SYMPTOM_MAX_SEVERITY,
  PAGINATION_LAYOUT,
} = useSymptomList();

/** 行点击查看详情（忽略选择列） */
function handleRowClick(row: SymptomRow, column?: { type?: string } | null) {
  if (column?.type === "selection") return;
  void openDetailDialog(row);
}
</script>

<style scoped>
.symptom-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 本页统计卡为 3 列，覆盖全局 4 列栅格 */
.stat-grid {
  grid-template-columns: repeat(3, 1fr);
}

/* 本页统计图标为 emoji，需显式字号（全局 .stat-icon 未定义 font-size） */
.stat-icon {
  font-size: 22px;
}

/* ==================== 表格 ==================== */

/* 整行可点进详情 */
.symptom-table :deep(.el-table__row) {
  cursor: pointer;
}

.term-name {
  font-weight: 600;
  color: #0f172a;
}

.cat-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.cat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.syn-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
}

.syn-chip {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 6px;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 11.5px;
  border: 1px solid #e0e7ff;
  line-height: 1.5;
}

.syn-empty {
  font-size: 12px;
  color: #cbd5e1;
}

.sev-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sev-bars {
  display: flex;
  gap: 3px;
}

.sev-bar {
  width: 16px;
  height: 6px;
  border-radius: 3px;
  background: #e2e8f0;
  transition: background 0.2s ease;
}

.sev-bar.on {
  background: #6366f1;
}

.sev-text {
  font-size: 11.5px;
  font-weight: 600;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

/* ==================== 分页 ==================== */

.selected-info {
  font-size: 12px;
  color: #6366f1;
  font-weight: 500;
}
</style>
