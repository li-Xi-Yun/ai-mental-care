<template>
  <div class="diagnosis-list-page">
    <div class="breadcrumb">
      <router-link to="/diagnosis" class="breadcrumb-link">心理诊断</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">{{ sessionName }}</span>
    </div>

    <h2 class="page-title">{{ sessionName }} — 诊断记录</h2>

    <el-table :data="diagnosisList" stripe style="width: 100%">
      <el-table-column prop="code" label="诊断编号" width="120" />
      <el-table-column prop="round" label="轮次" width="100" />
      <el-table-column prop="createdTime" label="创建时间" width="180" />
      <el-table-column prop="updatedTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="handleViewDetail(row)">查看详情 →</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination v-model:current-page="currentPage" :page-size="DEFAULT_PAGE_SIZE" :total="3" :layout="PAGINATION_LAYOUT" background />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const route = useRoute();
const router = useRouter();
const sessionId = route.params.sessionId as string;
const sessionName = ref("心情困扰与工作压力");
const currentPage = ref(1);

const diagnosisList = ref([
  { id: "3", code: "#3", round: "第6轮", createdTime: "2026-09-08 14:30", updatedTime: "2026-09-08 14:30" },
  { id: "2", code: "#2", round: "第4轮", createdTime: "2026-09-06 09:15", updatedTime: "2026-09-06 09:15" },
  { id: "1", code: "#1", round: "第2轮", createdTime: "2026-09-03 20:00", updatedTime: "2026-09-03 20:00" },
]);

function handleViewDetail(row: any) {
  router.push(`/diagnosis/${sessionId}/${row.id}`);
}
</script>

<style scoped>
.diagnosis-list-page {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
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

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 16px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>