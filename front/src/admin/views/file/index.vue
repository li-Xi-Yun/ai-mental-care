<template>
  <div class="admin-file-page">
    <div class="search-bar">
      <el-input v-model="searchKeyword" placeholder="搜索文件名" style="width: 200px" clearable />
      <el-select v-model="searchCategory" placeholder="分类" style="width: 120px" clearable>
        <el-option label="资料" value="material" />
        <el-option label="图表" value="chart" />
        <el-option label="数据" value="data" />
        <el-option label="音频" value="audio" />
      </el-select>
      <el-select v-model="searchFileType" placeholder="文件类型" style="width: 120px" clearable>
        <el-option label="PDF" value="pdf" />
        <el-option label="PNG" value="png" />
        <el-option label="Excel" value="xlsx" />
        <el-option label="Word" value="docx" />
        <el-option label="MP3" value="mp3" />
      </el-select>
      <el-button @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="handleUpload">📤 上传文件</el-button>
    </div>

    <el-table :data="fileList" stripe style="width: 100%">
      <el-table-column type="selection" width="50" />
      <el-table-column label="文件名" min-width="200">
        <template #default="{ row }">
          <span class="file-name">
            <span class="file-icon">{{ row.icon }}</span>
            {{ row.name }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="100">
        <template #default="{ row }">
          <el-tag :type="row.categoryType" size="small">{{ row.category }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="fileType" label="文件类型" width="100" />
      <el-table-column prop="size" label="大小" width="100" />
      <el-table-column prop="uploadTime" label="上传时间" width="120" />
      <el-table-column prop="uploader" label="上传者" width="100" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="handleDownload(row)">下载</el-button>
          <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="10"
        :total="48"
        layout="total, prev, pager, next"
        background
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { FILE_CATEGORIES, FILE_TYPES, DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const router = useRouter();

const searchKeyword = ref("");
const searchCategory = ref("");
const searchFileType = ref("");
const currentPage = ref(1);

const fileList = ref([
  { id: 1, name: "心理健康指南.pdf", icon: "📄", category: "资料", categoryType: "info" as const, fileType: "PDF", size: "2.3 MB", uploadTime: "2026-09-01", uploader: "admin" },
  { id: 2, name: "情绪图谱.png", icon: "🖼️", category: "图表", categoryType: "warning" as const, fileType: "PNG", size: "1.1 MB", uploadTime: "2026-08-28", uploader: "admin" },
  { id: 3, name: "量表数据.xlsx", icon: "📊", category: "数据", categoryType: "success" as const, fileType: "Excel", size: "0.8 MB", uploadTime: "2026-08-25", uploader: "admin" },
  { id: 4, name: "咨询记录.docx", icon: "📝", category: "资料", categoryType: "info" as const, fileType: "Word", size: "0.5 MB", uploadTime: "2026-08-20", uploader: "admin" },
  { id: 5, name: "冥想引导.mp3", icon: "🎵", category: "音频", categoryType: "warning" as const, fileType: "MP3", size: "5.2 MB", uploadTime: "2026-08-15", uploader: "admin" },
  { id: 6, name: "焦虑评估报告.pdf", icon: "📄", category: "资料", categoryType: "info" as const, fileType: "PDF", size: "1.8 MB", uploadTime: "2026-08-10", uploader: "admin" },
]);

function handleSearch() {
  console.log("查询", searchKeyword.value, searchCategory.value, searchFileType.value);
}

function handleReset() {
  searchKeyword.value = "";
  searchCategory.value = "";
  searchFileType.value = "";
}

function handleUpload() {
  console.log("上传文件");
}

function handleDownload(row: any) {
  console.log("下载", row.name);
}

function handleDelete(row: any) {
  console.log("删除", row.name);
}
</script>

<style scoped>
.admin-file-page {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.file-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.file-icon {
  font-size: 14px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>