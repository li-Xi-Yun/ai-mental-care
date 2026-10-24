<template>
  <div class="live2d-page">
    <PageHeader title="Live2D模型" subtitle="上传模型包、配置语义提示词与白名单，启用后用户端虚拟形象自动生效">
      <el-button type="primary" @click="openUploadDialog">
        <el-icon class="btn-icon"><Upload /></el-icon>上传模型
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">🧍</div>
        <div class="stat-body">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">模型总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">✅</div>
        <div class="stat-body">
          <div class="stat-num">{{ enabledCount }}</div>
          <div class="stat-label">启用模型</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-amber">💬</div>
        <div class="stat-body">
          <div class="stat-num">1</div>
          <div class="stat-label">全局唯一启用</div>
        </div>
      </div>
    </div>

    <div class="filter-card">
      <div class="toolbar">
        <el-input
          v-model="searchForm.modelName"
          placeholder="搜索模型名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select v-model="searchForm.enabled" placeholder="启用状态" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>

        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
      </div>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="tableData" row-key="id" class="live2d-table">
        <el-table-column label="封面" width="64" align="center">
          <template #default="{ row }">
            <div v-if="row.coverImagePath" class="cover-thumb">
              <img :src="resolveResourceUrl(row.coverImagePath)" alt="" />
            </div>
          </template>
        </el-table-column>

        <el-table-column label="模型信息" min-width="220">
          <template #default="{ row }">
            <div class="model-cell">
              <span
                class="model-name"
                :class="{ 'is-link': row.id != null }"
                @click="row.id != null && handleViewDetail(row)"
              >
                {{ row.modelName || "-" }}
              </span>
              <code class="model-url">{{ row.modelUrl || "-" }}</code>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="模型别名" min-width="140">
          <template #default="{ row }">
            <span class="alias-text">{{ row.aliasName || "—" }}</span>
          </template>
        </el-table-column>

        <el-table-column label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.enabled === 1 ? "启用" : "禁用" }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.updatedTime, FORMAT_DATETIME) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button text size="small" class="op-btn op-view" @click="handleViewDetail(row)">
                详情
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无模型数据，请点击右上角「上传模型」" :image-size="80" />
        </template>
      </el-table>

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

    <Live2dUploadDialog
      v-model="uploadVisible"
      :submitting="uploadSubmitting"
      :form="uploadForm"
      :file-list="uploadFileList"
      @change="handleUploadChange"
      @submit="handleUploadSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { Search, Upload, RefreshRight } from "@element-plus/icons-vue";
import { FORMAT_DATETIME } from "@/shared/utils/dayjs";
import { formatTime } from "@/admin/composables/useTimeFormat";
import { resolveResourceUrl } from "@/shared/utils/resource";
import PageHeader from "@/admin/components/PageHeader.vue";
import { useLive2dList } from "./useLive2dList";
import Live2dUploadDialog from "./components/Live2dUploadDialog.vue";

const {
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  enabledCount,
  searchForm,
  handleSearch,
  handleReset,
  loadData,

  uploadVisible,
  uploadSubmitting,
  uploadFileList,
  uploadForm,
  openUploadDialog,
  handleUploadChange,
  handleUploadSubmit,

  handleViewDetail,

  PAGINATION_LAYOUT,
} = useLive2dList();
</script>

<style scoped>
.live2d-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 统计卡仅需覆盖默认 4 列 → 3 列，卡片本体沿用全局 .stat-card/.stat-icon-* 样式 */
.stat-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.model-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

/* ---------- 封面缩略图（固定尺寸不撑高行） ---------- */
.cover-thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  overflow: hidden;
  margin: 0 auto;
  flex-shrink: 0;
}

.cover-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.model-name {
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.alias-text {
  font-size: 13px;
  color: #475569;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.model-name.is-link {
  cursor: pointer;
  transition: color 0.15s;
}

.model-name.is-link:hover {
  color: #6c63ff;
}

.model-url {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 11px;
  color: #64748b;
  background: #f8fafc;
  border-radius: 4px;
  padding: 1px 6px;
  align-self: flex-start;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.time-text {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  color: #94a3b8;
}
</style>
