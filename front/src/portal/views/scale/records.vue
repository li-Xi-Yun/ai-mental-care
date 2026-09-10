<template>
  <div class="scale-records-page">
    <div class="breadcrumb">
      <router-link to="/scale" class="breadcrumb-link">量表测评</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">测评记录</span>
    </div>

    <h2 class="page-title">我的测评记录</h2>

    <el-table :data="records" stripe style="width: 100%">
      <el-table-column prop="id" label="记录ID" width="80" />
      <el-table-column prop="scaleName" label="量表名称" />
      <el-table-column prop="totalScore" label="总分" width="80" />
      <el-table-column prop="level" label="等级" width="100">
        <template #default="{ row }">
          <el-tag :type="row.levelType" size="small">{{ row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="completedTime" label="完成时间" width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="handleViewDetail(row)">查看详情 →</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination v-model:current-page="currentPage" :page-size="DEFAULT_PAGE_SIZE" :total="4" :layout="PAGINATION_LAYOUT" background />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const router = useRouter();
const currentPage = ref(1);

const records = ref([
  { id: 4, scaleName: "SCL-90 症状自评量表", totalScore: 156, level: "轻度", levelType: "warning", completedTime: "2026-09-08 10:30" },
  { id: 3, scaleName: "SAS 焦虑自评量表", totalScore: 52, level: "中度", levelType: "danger", completedTime: "2026-09-05 14:20" },
  { id: 2, scaleName: "SDS 抑郁自评量表", totalScore: 41, level: "正常", levelType: "success", completedTime: "2026-09-01 09:15" },
  { id: 1, scaleName: "PSQI 匹兹堡睡眠质量指数", totalScore: 8, level: "轻度", levelType: "warning", completedTime: "2026-08-28 16:00" },
]);

function handleViewDetail(row: any) {
  router.push(`/scale/records/${row.id}`);
}
</script>

<style scoped>
.scale-records-page {
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