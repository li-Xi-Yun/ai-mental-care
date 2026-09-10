<template>
  <div class="admin-symptom-page">
    <div class="search-bar">
      <el-input v-model="searchKeyword" placeholder="搜索症状名称" style="width: 200px" clearable />
      <el-button @click="handleSearch">查询</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="handleAdd">+ 新增症状</el-button>
    </div>

    <el-table :data="symptomList" stripe style="width: 100%">
      <el-table-column prop="id" label="症状ID" width="100" />
      <el-table-column prop="name" label="症状名称" min-width="160" />
      <el-table-column label="分类" width="100">
        <template #default="{ row }">
          <el-tag :type="row.categoryType" size="small">{{ row.category }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="严重等级" width="120">
        <template #default="{ row }">
          <el-rate v-model="row.severity" disabled size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="relatedScale" label="关联量表" width="140" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { SYMPTOM_MAX_SEVERITY } from "@/shared/api/config";

const searchKeyword = ref("");

const symptomList = ref([
  { id: "SY001", name: "入睡困难", category: "睡眠", categoryType: "warning" as const, severity: 3, relatedScale: "PSQI" },
  { id: "SY002", name: "持续焦虑", category: "焦虑", categoryType: "danger" as const, severity: 4, relatedScale: "SAS" },
  { id: "SY003", name: "情绪低落", category: "抑郁", categoryType: "info" as const, severity: 3, relatedScale: "SDS" },
  { id: "SY004", name: "社交回避", category: "焦虑", categoryType: "danger" as const, severity: 3, relatedScale: "GAD-7" },
  { id: "SY005", name: "早醒", category: "睡眠", categoryType: "warning" as const, severity: 2, relatedScale: "PSQI" },
  { id: "SY006", name: "兴趣丧失", category: "抑郁", categoryType: "info" as const, severity: 4, relatedScale: "PHQ-9" },
]);

function handleSearch() {
  console.log("查询", searchKeyword.value);
}

function handleAdd() {
  console.log("新增症状");
}

function handleEdit(row: any) {
  console.log("编辑", row);
}

function handleDelete(row: any) {
  console.log("删除", row);
}
</script>

<style scoped>
.admin-symptom-page {
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