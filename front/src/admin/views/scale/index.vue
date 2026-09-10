<template>
  <div class="admin-scale-page">
    <div class="search-bar">
      <el-input v-model="searchKeyword" placeholder="搜索量表名称" style="width: 200px" clearable />
      <el-button @click="handleSearch">查询</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="handleAdd">+ 新增量表</el-button>
    </div>

    <el-table :data="scaleList" stripe style="width: 100%">
      <el-table-column prop="id" label="量表ID" width="100" />
      <el-table-column prop="name" label="量表名称" min-width="200" />
      <el-table-column label="分类" width="100">
        <template #default="{ row }">
          <el-tag :type="row.categoryType" size="small">{{ row.category }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="questionCount" label="题目数" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'enabled' ? 'success' : 'danger'" size="small">
            {{ row.status === "enabled" ? "启用" : "禁用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="info" text size="small" @click="handleQuestions(row)">题目</el-button>
          <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const router = useRouter();

const searchKeyword = ref("");

const scaleList = ref([
  { id: "S001", name: "SAS 焦虑自评量表", category: "焦虑", categoryType: "danger" as const, questionCount: 20, status: "enabled" },
  { id: "S002", name: "SDS 抑郁自评量表", category: "抑郁", categoryType: "info" as const, questionCount: 20, status: "enabled" },
  { id: "S003", name: "PSQI 睡眠质量", category: "睡眠", categoryType: "warning" as const, questionCount: 19, status: "enabled" },
  { id: "S004", name: "PSS 压力知觉量表", category: "压力", categoryType: "warning" as const, questionCount: 10, status: "enabled" },
  { id: "S005", name: "GAD-7 广泛性焦虑", category: "焦虑", categoryType: "danger" as const, questionCount: 7, status: "enabled" },
  { id: "S006", name: "PHQ-9 抑郁症筛查", category: "抑郁", categoryType: "info" as const, questionCount: 9, status: "disabled" },
]);

function handleSearch() {
  console.log("查询", searchKeyword.value);
}

function handleAdd() {
  console.log("新增量表");
}

function handleEdit(row: any) {
  console.log("编辑", row);
}

function handleQuestions(row: any) {
  router.push(`/admin/scale/${row.id}`);
}

function handleDelete(row: any) {
  console.log("删除", row);
}
</script>

<style scoped>
.admin-scale-page {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
</style>