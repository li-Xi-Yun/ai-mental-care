<template>
  <div class="admin-scale-page">
    <!-- ==================== 页头 ==================== -->
    <PageHeader title="量表管理" subtitle="管理心理量表、版本与分类">
      <el-button type="primary" @click="handleAdd">
        <el-icon class="btn-icon"><Plus /></el-icon>新增量表
      </el-button>
    </PageHeader>

    <!-- ==================== 统计卡 ==================== -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><Document /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">量表总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheck /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ enabledCount }}</div>
          <div class="stat-label">启用量表</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-purple">
          <el-icon :size="22"><FolderOpened /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ categoryCount }}</div>
          <div class="stat-label">分类数量</div>
        </div>
      </div>
    </div>

    <!-- ==================== 筛选卡 ==================== -->
    <div class="filter-card">
      <!-- 搜索条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.scaleName"
          placeholder="搜索量表名称"
          clearable
          style="width: 210px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="searchForm.scaleCategoryId"
          placeholder="量表分类"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option v-for="c in categoryList" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
        <el-select
          v-model="searchForm.status"
          placeholder="启用状态"
          clearable
          style="width: 130px"
          @change="handleSearch"
        >
          <el-option v-for="opt in SCALE_ENABLE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select
          v-model="searchForm.deletedFlag"
          placeholder="删除状态"
          clearable
          style="width: 130px"
          @change="handleSearch"
        >
          <el-option v-for="opt in SCALE_DELETED_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" plain @click="openCategoryDialog()">
          <el-icon class="btn-icon"><FolderOpened /></el-icon>分类管理
        </el-button>
      </div>
    </div>

    <!-- ==================== 主卡片 ==================== -->
    <div class="content-card">
      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" class="scale-table" row-key="id">
        <el-table-column prop="id" label="ID" width="72" align="center" />
        <el-table-column label="量表名称" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="scale-name-cell">
              <span class="scale-name" @click="goDetail(row)">{{ row.scaleName }}</span>
              <el-tag v-if="row.currentVersionNo" size="small" class="ver-tag" effect="plain">{{ row.currentVersionNo }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="130">
          <template #default="{ row }">
            <span v-if="row.scaleCategoryName" class="cat-tag" :style="categoryStyle(row.scaleCategoryName)">
              <span class="cat-dot" :style="{ background: categoryStyle(row.scaleCategoryName).color }" />
              {{ row.scaleCategoryName }}
            </span>
            <span v-else class="cat-empty">未分类</span>
          </template>
        </el-table-column>
        <el-table-column label="题目数" width="90" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ row.questionCount ?? "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="维度数" width="90" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ row.dimensionCount ?? "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="96" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="row.deletedFlag === 1"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="(val: number | string | boolean) => handleStatusToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="136">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="148" align="center" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button text size="small" class="op-btn op-detail" @click="goDetail(row)">详情</el-button>
              <el-button v-if="row.deletedFlag !== 1" text size="small" class="op-btn op-edit" @click="handleEdit(row)">编辑</el-button>
              <el-button v-if="row.deletedFlag !== 1" text size="small" class="op-btn op-del" @click="handleDelete(row)">删除</el-button>
              <el-button v-else text size="small" class="op-btn op-restore" @click="handleRestore(row)">恢复</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空态 -->
      <el-empty v-if="!loading && !tableData.length" :image-size="100" description="暂无量表数据，点击右上角「新增量表」开始创建" />

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="page-total">共 {{ total }} 条</span>
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

    <!-- ==================== 新增 / 编辑量表弹窗 ==================== -->
    <ScaleFormDialog
      v-model="scaleDialogVisible"
      :title="scaleDialogTitle"
      :target="scaleTarget"
      :submitting="scaleSubmitting"
      :category-options="categoryList"
      @submit="handleScaleSubmit"
    />

    <!-- ==================== 分类管理弹窗 ==================== -->
    <CategoryManageDialog
      v-model="categoryDialogVisible"
      :categories="categoryList"
      :loading="categoryLoading"
      :submitting="categorySubmitting"
      @add-category="openCategoryEdit()"
      @edit-category="openCategoryEdit"
      @move-category="handleMoveSort"
      @delete-category="handleCategoryDelete"
    />

    <!-- 分类编辑/新增子弹窗 -->
    <CategoryEditDialog
      v-model="categoryEditVisible"
      :title="categoryEditTitle"
      :target="categoryEditTarget"
      :submitting="categorySubmitting"
      @submit="handleCategorySubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { CircleCheck, Document, FolderOpened, Plus, RefreshRight, Search } from "@element-plus/icons-vue";
import { SCALE_DELETED_OPTIONS, SCALE_ENABLE_OPTIONS } from "@/admin/constants/scale";
import PageHeader from "@/admin/components/PageHeader.vue";
import { useScaleList } from "./useScaleList";
import ScaleFormDialog from "./components/ScaleFormDialog.vue";
import CategoryManageDialog from "./components/CategoryManageDialog.vue";
import CategoryEditDialog from "./components/CategoryEditDialog.vue";

const {
  // 工具
  categoryStyle,
  formatTime,
  // 列表
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  searchForm,
  categoryList,
  enabledCount,
  categoryCount,
  loadData,
  handleSearch,
  handleReset,
  // 详情 / 状态
  goDetail,
  handleStatusToggle,
  // 新增 / 编辑量表
  scaleDialogVisible,
  scaleSubmitting,
  scaleDialogTitle,
  scaleTarget,
  handleAdd,
  handleEdit,
  handleScaleSubmit,
  // 删除 / 恢复
  handleDelete,
  handleRestore,
  // 分类管理
  categoryDialogVisible,
  categoryLoading,
  categorySubmitting,
  openCategoryDialog,
  categoryEditVisible,
  categoryEditTitle,
  categoryEditTarget,
  openCategoryEdit,
  handleCategorySubmit,
  handleMoveSort,
  handleCategoryDelete,
  // 常量
  PAGINATION_LAYOUT,
} = useScaleList();
</script>

<style scoped>
.admin-scale-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 页面共 3 张统计卡，覆写全局 .stat-grid 的 4 列布局 */
.admin-scale-page .stat-grid {
  grid-template-columns: repeat(3, 1fr);
}

/* ==================== 表格 ==================== */

.scale-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scale-name {
  font-weight: 600;
  color: #0f172a;
  cursor: pointer;
  transition: color 0.2s ease;
}

.scale-name:hover {
  color: var(--admin-primary);
}

.ver-tag {
  background: #eef2ff;
  border-color: #e0e7ff;
  color: #4f46e5;
  font-variant-numeric: tabular-nums;
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

.cat-empty {
  font-size: 12px;
  color: #cbd5e1;
}

.num-text {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}
</style>
