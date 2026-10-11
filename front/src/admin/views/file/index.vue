<template>
  <div class="admin-file-page">
    <!-- 页头 -->
    <PageHeader title="文件管理" subtitle="管理文件分类、上传与向量化">
      <el-button type="primary" @click="openUploadDialog">
        <el-icon class="btn-icon"><Upload /></el-icon>上传文件
      </el-button>
    </PageHeader>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo"><el-icon :size="22"><Files /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.totalFiles }}</div>
          <div class="stat-label">文件总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-purple"><el-icon :size="22"><Monitor /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">{{ formatSize(stats.totalSize) }}</div>
          <div class="stat-label">占用空间</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green"><el-icon :size="22"><DataAnalysis /></el-icon></div>
        <div class="stat-body">
          <div class="stat-num">
            {{ stats.vectorizedFiles }}
            <span v-if="stats.totalFiles" class="stat-sub">/ {{ stats.totalFiles }}</span>
          </div>
          <div class="stat-label">已向量化</div>
        </div>
      </div>
    </div>

    <div class="file-layout">
      <!-- 左侧：分类树 -->
      <CategoryTreePanel
        :category-tree="categoryTree"
        :category-loading="categoryLoading"
        :active-category-id="searchCategoryId"
        :total-files="stats.totalFiles"
        :category-tree-props="categoryTreeProps"
        @select="handleSelectCategory"
        @manage="openCategoryManage"
        @add="openCategoryEdit()"
      />

      <!-- 右侧：筛选卡 + 内容卡 -->
      <div class="content-panel">
        <!-- 搜索条 -->
        <div class="filter-card">
          <div class="toolbar">
            <el-input
              v-model="searchForm.fileName"
              placeholder="搜索文件名"
              clearable
              style="width: 170px"
              @keyup.enter="handleSearch"
            >
              <template #prefix><el-icon :size="14"><Search /></el-icon></template>
            </el-input>
            <el-tree-select
              v-model="searchCategoryId"
              :data="categoryTree"
              :props="categoryTreeProps"
              node-key="id"
              check-strictly
              clearable
              placeholder="全部分类"
              style="width: 140px"
            />
            <el-select v-model="searchForm.fileSuffix" placeholder="文件类型" clearable filterable style="width: 120px">
              <el-option v-for="s in COMMON_FILE_SUFFIXES" :key="s" :label="s.toUpperCase()" :value="s" />
            </el-select>
            <el-select v-model="searchForm.status" placeholder="解析状态" clearable style="width: 120px">
              <el-option
                v-for="s in STATUS_MAP"
                :key="s.value"
                :label="s.label"
                :value="s.value"
              />
            </el-select>
            <el-select v-model="searchForm.vectorStatus" placeholder="向量状态" clearable style="width: 120px">
              <el-option label="已启用" :value="1" />
              <el-option label="未启用" :value="0" />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>

        <!-- 文件表格 -->
        <section class="content-card">
          <el-table v-loading="tableLoading" :data="fileList" class="file-table" style="width: 100%">
            <el-table-column label="文件名" min-width="152">
              <template #default="{ row }">
                <div class="file-name-cell">
                  <span class="file-type-icon" :style="{ background: fileMeta(row).bg, color: fileMeta(row).color }">
                    <el-icon :size="16"><component :is="fileMeta(row).icon" /></el-icon>
                  </span>
                  <span class="file-name-text" :title="row.originalName" @click="handlePreview(row)">
                    {{ row.originalName }}
                  </span>
                  <span v-if="row.vectorStatus === 1" class="vector-flag">向量</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="分类" width="92">
              <template #default="{ row }">
                <el-tag size="small" :type="categoryTagType(row.categoryName)" effect="light" round>
                  {{ row.categoryName || "未分类" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="56">
              <template #default="{ row }">
                <span class="suffix-text">{{ (row.fileSuffix || "-").toUpperCase() }}</span>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="76">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="解析状态" width="96">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.status === 2 && row.failReason"
                  :content="`失败原因：${row.failReason}`"
                  placement="top"
                >
                  <span class="status-badge" :class="statusMeta(row.status).cls">
                    <span class="status-dot" />
                    {{ statusMeta(row.status).label }}
                  </span>
                </el-tooltip>
                <span v-else class="status-badge" :class="statusMeta(row.status).cls">
                  <span class="status-dot" />
                  {{ statusMeta(row.status).label }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="向量状态" width="84">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.knowledgeType"
                  :content="`知识类型：${KNOWLEDGE_TYPE_MAP[row.knowledgeType as number] ?? '未知'}`"
                  placement="top"
                >
                  <span class="status-badge" :class="row.vectorStatus === 1 ? 'ok' : 'off'">
                    <span class="status-dot" />
                    {{ row.vectorStatus === 1 ? "已启用" : "未启用" }}
                  </span>
                </el-tooltip>
                <span v-else class="status-badge" :class="row.vectorStatus === 1 ? 'ok' : 'off'">
                  <span class="status-dot" />
                  {{ row.vectorStatus === 1 ? "已启用" : "未启用" }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="上传人" width="64" prop="creatorName" show-overflow-tooltip>
              <template #default="{ row }">{{ row.creatorName || "-" }}</template>
            </el-table-column>
            <el-table-column label="上传时间" width="92">
              <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="216" fixed="right">
              <template #default="{ row }">
                <div class="op-cell">
                  <el-button text size="small" type="primary" @click="handlePreview(row)">预览</el-button>
                  <el-button text size="small" @click="handleDownload(row)">下载</el-button>
                  <el-button text size="small" @click="openEditDialog(row)">编辑</el-button>
                  <el-button text size="small" type="primary" @click="goVector(row)">向量</el-button>
                  <el-button text size="small" type="danger" @click="handleDelete(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无文件数据" :image-size="80" />
            </template>
          </el-table>

          <!-- 分页 -->
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadFiles"
              @size-change="handleSizeChange"
            />
          </div>
        </section>
      </div>
    </div>

    <!-- 上传弹窗 -->
    <FileUploadDialog
      v-model="uploadVisible"
      v-model:category-id="uploadCategoryId"
      v-model:file-list="uploadFileList"
      :category-tree="categoryTree"
      :category-tree-props="categoryTreeProps"
      :before-upload="beforeUpload"
      :upload-request="handleUploadRequest"
    />

    <!-- 编辑弹窗 -->
    <FileEditDialog
      v-model="editVisible"
      :form="editForm"
      :saving="editSaving"
      :category-tree="categoryTree"
      :category-tree-props="categoryTreeProps"
      @submit="handleSaveEdit"
    />

    <!-- 分类管理弹窗 -->
    <CategoryManageDialog
      v-model="categoryManageVisible"
      :category-tree="categoryTree"
      :category-tree-props="categoryTreeProps"
      @add="openCategoryEdit()"
      @edit="openCategoryEdit"
      @remove="handleDeleteCategory"
    />

    <!-- 分类新增/编辑弹窗 -->
    <CategoryEditDialog
      v-model="categoryEditVisible"
      v-model:name="categoryForm.name"
      v-model:parent-id="categoryParentId"
      :is-editing="!!editingCategoryId"
      :saving="categorySaving"
      :category-tree="categoryTree"
      :category-tree-props="categoryTreeProps"
      @submit="handleSaveCategory"
    />

    <!-- 文件预览弹窗 -->
    <FilePreviewDialog
      v-model="previewVisible"
      :row="previewRow"
      :loading="previewLoading"
      :image-url="previewImageUrl"
      :text="previewText"
      :is-image="isImagePreview"
      :is-text="isTextPreview"
      @download="handleDownload"
      @closed="clearPreview"
    />
  </div>
</template>

<script setup lang="ts">
import { DataAnalysis, Files, Monitor, Search, Upload } from "@element-plus/icons-vue";
import PageHeader from "@/admin/components/PageHeader.vue";
import { useFileList } from "./useFileList";
import CategoryTreePanel from "./components/CategoryTreePanel.vue";
import FileUploadDialog from "./components/FileUploadDialog.vue";
import FileEditDialog from "./components/FileEditDialog.vue";
import CategoryManageDialog from "./components/CategoryManageDialog.vue";
import CategoryEditDialog from "./components/CategoryEditDialog.vue";
import FilePreviewDialog from "./components/FilePreviewDialog.vue";

const {
  // 工具
  fileMeta,
  formatSize,
  formatTime,
  categoryTagType,
  statusMeta,
  // 列表
  page,
  pageSize,
  total,
  tableLoading,
  fileList,
  loadFiles,
  handleSizeChange,
  // 搜索
  searchForm,
  searchCategoryId,
  handleSearch,
  handleReset,
  handleSelectCategory,
  // 分类
  categoryLoading,
  categoryTree,
  categoryTreeProps,
  openCategoryManage,
  openCategoryEdit,
  handleDeleteCategory,
  categoryManageVisible,
  categoryEditVisible,
  categorySaving,
  editingCategoryId,
  categoryParentId,
  categoryForm,
  handleSaveCategory,
  // 统计
  stats,
  // 上传
  uploadVisible,
  uploadCategoryId,
  uploadFileList,
  openUploadDialog,
  beforeUpload,
  handleUploadRequest,
  // 编辑
  editVisible,
  editSaving,
  editForm,
  openEditDialog,
  handleSaveEdit,
  // 预览 / 下载 / 删除 / 跳转
  previewVisible,
  previewLoading,
  previewRow,
  previewImageUrl,
  previewText,
  isImagePreview,
  isTextPreview,
  handlePreview,
  clearPreview,
  handleDownload,
  handleDelete,
  goVector,
  // 常量
  STATUS_MAP,
  KNOWLEDGE_TYPE_MAP,
  COMMON_FILE_SUFFIXES,
} = useFileList();
</script>

<style scoped>
.admin-file-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 页面共 3 张统计卡，覆写全局 .stat-grid 的 4 列布局 */
.admin-file-page .stat-grid {
  grid-template-columns: repeat(3, 1fr);
}

/* 统计卡「/ 总数」次要数值（页面特有） */
.stat-sub {
  font-size: 13px;
  font-weight: 500;
  color: var(--admin-text-muted);
  margin-left: 2px;
}

/* ===== 主布局 ===== */
.file-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* 右侧列：筛选卡 + 内容卡（卡片外观由全局 .filter-card / .content-card 提供） */
.content-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ===== 表格 ===== */
.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-type-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-name-text {
  cursor: pointer;
  color: #334155;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.file-name-text:hover {
  color: var(--admin-primary);
}

.vector-flag {
  font-size: 10px;
  color: #8b5cf6;
  background: #f5f3ff;
  border-radius: 4px;
  padding: 1px 5px;
  flex-shrink: 0;
}

.suffix-text {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

/* 分类列 el-tag 定宽截断（列宽压缩后长分类名省略显示） */
.file-table :deep(.el-tag) {
  max-width: 76px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-table :deep(.el-tag .el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 状态徽章（全局 pill 形态：999px 圆角 + 1px 边框 + 圆点，配色沿用页面语义色） ===== */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
  white-space: nowrap;
  cursor: default;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  background: currentColor;
}

.status-badge.ok {
  background: #f0fdf4;
  color: #16a34a;
  border-color: #bbf7d0;
}

.status-badge.warn {
  background: #fffbeb;
  color: #d97706;
  border-color: #fde68a;
}

.status-badge.err {
  background: #fef2f2;
  color: #dc2626;
  border-color: #fecaca;
}

.status-badge.info {
  background: #eff6ff;
  color: #2563eb;
  border-color: #bfdbfe;
}

.status-badge.off {
  background: #f1f5f9;
  color: #94a3b8;
  border-color: #e2e8f0;
}

/* ===== 分页 ===== */
/* 公共 .pagination-bar（display / 间距）已由 admin/styles/index.css 提供，仅保留页面特有的右对齐差异 */
.pagination-bar {
  justify-content: flex-end;
}
</style>
