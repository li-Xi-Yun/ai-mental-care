<template>
  <div class="file-vector-page">
    <div class="breadcrumb">
      <router-link to="/admin/file" class="breadcrumb-link">文件管理</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">向量管理 - {{ fileName }}</span>
    </div>

    <div class="page-header">
      <h2 class="page-title">{{ fileName }} — 向量管理</h2>
      <el-button type="primary" @click="showAddDialog = true">+ 新增向量</el-button>
    </div>

    <el-table :data="vectorList" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="content" label="内容片段" min-width="300" show-overflow-tooltip />
      <el-table-column prop="dimension" label="维度" width="80" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '已索引' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default>
          <el-button type="primary" text size="small">编辑</el-button>
          <el-button type="danger" text size="small">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination v-model:current-page="currentPage" :page-size="DEFAULT_PAGE_SIZE" :total="5" :layout="PAGINATION_LAYOUT" background />
    </div>

    <el-dialog v-model="showAddDialog" title="新增向量" width="560">
      <el-form label-width="80px">
        <el-form-item label="内容片段"><el-input type="textarea" :rows="4" placeholder="请输入内容片段" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="showAddDialog = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute } from "vue-router";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const route = useRoute();
const fileId = route.params.fileId as string;
const fileName = ref("心理健康指南.pdf");
const currentPage = ref(1);
const showAddDialog = ref(false);

const vectorList = ref([
  { id: 1, content: "焦虑是一种常见的情绪反应，表现为对未来不确定性的过度担忧...", dimension: 1536, status: "已索引", createdTime: "2026-09-01 10:00" },
  { id: 2, content: "抑郁症状包括持续的情绪低落、兴趣减退、精力不足等...", dimension: 1536, status: "已索引", createdTime: "2026-09-01 10:01" },
  { id: 3, content: "认知行为疗法（CBT）是一种有效的心理治疗方法...", dimension: 1536, status: "已索引", createdTime: "2026-09-01 10:02" },
  { id: 4, content: "正念冥想可以帮助缓解焦虑和压力，改善情绪调节能力...", dimension: 1536, status: "未索引", createdTime: "2026-09-01 10:03" },
  { id: 5, content: "睡眠卫生习惯对心理健康至关重要，建议保持规律作息...", dimension: 1536, status: "已索引", createdTime: "2026-09-01 10:04" },
]);
</script>

<style scoped>
.file-vector-page {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #999;
  margin-bottom: 16px;
}

.breadcrumb-link {
  color: #6c63ff;
  text-decoration: none;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>