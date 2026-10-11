<template>
  <div class="ai-node-page">
    <!-- ==================== 页头 ==================== -->
    <PageHeader title="AI节点配置" subtitle="配置 AI 节点的模型、提示词与生成参数">
      <el-button type="primary" @click="handleAdd">
        <el-icon class="btn-icon"><Plus /></el-icon>新增节点
      </el-button>
    </PageHeader>

    <!-- ==================== 统计卡 ==================== -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">🧠</div>
        <div class="stat-body">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">节点总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">✅</div>
        <div class="stat-body">
          <div class="stat-num">{{ enabledCount }}</div>
          <div class="stat-label">启用节点</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-amber">🗂️</div>
        <div class="stat-body">
          <div class="stat-num">{{ groupOptions.length }}</div>
          <div class="stat-label">节点分组</div>
        </div>
      </div>
    </div>

    <!-- ==================== 筛选卡 ==================== -->
    <div class="filter-card">
      <!-- 搜索筛选条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索节点标识 / 名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select v-model="searchForm.nodeGroup" placeholder="节点分组" clearable filterable style="width: 150px" @change="handleSearch">
          <el-option v-for="g in groupOptions" :key="g.nodeGroup" :label="g.nodeGroup" :value="g.nodeGroup ?? ''" />
        </el-select>

        <el-select v-model="searchForm.modelType" placeholder="模型类型" clearable style="width: 150px" @change="handleSearch">
          <el-option v-for="m in MODEL_TYPE_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>

        <el-select v-model="searchForm.enabled" placeholder="启用状态" clearable style="width: 120px" @change="handleSearch">
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

        <el-button :disabled="!selectedRows.length" @click="handleBatchToggle(true)">
          批量启用
        </el-button>
        <el-button :disabled="!selectedRows.length" @click="handleBatchToggle(false)">
          批量禁用
        </el-button>
      </div>
    </div>

    <!-- ==================== 主卡片 ==================== -->
    <div class="content-card">
      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="ai-node-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column label="节点信息" min-width="220">
          <template #default="{ row }">
            <div class="node-cell">
              <span class="node-name">{{ row.nodeName || "-" }}</span>
              <code class="node-key">{{ row.nodeKey || "-" }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分组" width="130">
          <template #default="{ row }">
            <span class="group-tag" :style="groupStyle(row.nodeGroup)">
              <span class="group-dot" :style="{ background: groupStyle(row.nodeGroup).color }" />
              {{ row.nodeGroup || "未分组" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="模型" width="180">
          <template #default="{ row }">
            <div class="model-cell">
              <span class="model-type-badge" :style="modelTypeStyle(row.modelType)">
                {{ modelTypeLabel(row.modelType) }}
              </span>
              <span class="model-name">{{ row.modelName || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="96" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="(val: string | number | boolean) => handleStatusToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <span class="ver-tag">v{{ row.version ?? "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.updatedTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.remark" class="remark-text">{{ row.remark }}</span>
            <span v-else class="remark-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="148" align="center" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button text size="small" class="op-btn op-edit" @click="handleViewDetail(row)">详情</el-button>
              <el-button text size="small" class="op-btn op-edit" @click="handleEdit(row)">编辑</el-button>
              <el-button text size="small" class="op-btn op-del" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无节点数据" :image-size="80" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span v-if="selectedRows.length" class="selected-info">已选 {{ selectedRows.length }} 项</span>
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
    <AiNodeFormDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :is-edit="isEdit"
      :submitting="submitting"
      :form="form"
      :group-options="groupOptions"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { Search, Plus, RefreshRight } from "@element-plus/icons-vue";
import PageHeader from "@/admin/components/PageHeader.vue";
import { useAiNodeList } from "./useAiNodeList";
import AiNodeFormDialog from "./components/AiNodeFormDialog.vue";

const {
  // 展示工具
  modelTypeLabel,
  modelTypeStyle,
  groupStyle,
  formatTime,
  // 列表
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  selectedRows,
  searchForm,
  groupOptions,
  enabledCount,
  handleSelectionChange,
  loadData,
  handleSearch,
  handleReset,
  // 启停用
  handleStatusToggle,
  handleBatchToggle,
  // 新增 / 编辑 / 删除 / 详情
  handleAdd,
  handleEdit,
  handleViewDetail,
  handleDelete,
  handleSubmit,
  // 弹窗状态
  dialogVisible,
  dialogTitle,
  isEdit,
  submitting,
  form,
  // 常量
  MODEL_TYPE_OPTIONS,
  PAGINATION_LAYOUT,
} = useAiNodeList();
</script>

<style scoped>
.ai-node-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ==================== 统计卡 ==================== */

/* 本页仅 3 张统计卡，覆盖全局 4 列布局 */
.stat-grid {
  grid-template-columns: repeat(3, 1fr);
}

/* 本页 emoji 图标需显式字号（全局 .stat-icon 未定义 font-size） */
.stat-icon {
  font-size: 22px;
}

/* ==================== 表格 ==================== */

.node-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.node-name {
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-key {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 11.5px;
  color: #6366f1;
  background: #eef2ff;
  border-radius: 4px;
  padding: 1px 6px;
  align-self: flex-start;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-tag {
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

.group-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.model-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.model-type-badge {
  flex-shrink: 0;
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
}

.model-name {
  font-size: 12px;
  color: #475569;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ver-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11.5px;
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.remark-text {
  font-size: 12px;
  color: #475569;
}

.remark-empty {
  color: #cbd5e1;
}

/* ==================== 分页（公共 pagination-bar / page-total 已迁至 styles/index.css） ==================== */

.selected-info {
  font-size: 12px;
  color: #6366f1;
  font-weight: 500;
}
</style>
