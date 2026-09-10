<template>
  <div class="admin-user-page">
    <div class="search-bar">
      <el-input v-model="searchKeyword" placeholder="搜索用户名/手机号" style="width: 200px" clearable />
      <el-select v-model="searchStatus" placeholder="状态" style="width: 120px" clearable>
        <el-option label="正常" value="normal" />
        <el-option label="禁用" value="disabled" />
      </el-select>
      <el-button @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="handleAdd">+ 新增用户</el-button>
    </div>

    <el-table :data="userList" stripe style="width: 100%">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="id" label="用户ID" width="100" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="registerTime" label="注册时间" width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'normal' ? 'success' : 'danger'" size="small">
            {{ row.status === "normal" ? "正常" : "禁用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button
            :type="row.status === 'normal' ? 'danger' : 'success'"
            text
            size="small"
            @click="handleToggleStatus(row)"
          >
            {{ row.status === "normal" ? "禁用" : "启用" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="10"
        :total="156"
        layout="total, prev, pager, next"
        background
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const searchKeyword = ref("");
const searchStatus = ref("");
const currentPage = ref(1);

const userList = ref([
  { id: 1001, username: "zhangxm", phone: "138****5678", registerTime: "2026-08-15", status: "normal" },
  { id: 1002, username: "lisi", phone: "139****1234", registerTime: "2026-08-20", status: "normal" },
  { id: 1003, username: "wangwu", phone: "137****9876", registerTime: "2026-09-01", status: "disabled" },
  { id: 1004, username: "zhaoliu", phone: "136****5432", registerTime: "2026-09-03", status: "normal" },
  { id: 1005, username: "sunqi", phone: "135****8765", registerTime: "2026-09-05", status: "normal" },
]);

function handleSearch() {
  console.log("查询", searchKeyword.value, searchStatus.value);
}

function handleReset() {
  searchKeyword.value = "";
  searchStatus.value = "";
}

function handleAdd() {
  console.log("新增用户");
}

function handleEdit(row: any) {
  console.log("编辑", row);
}

function handleToggleStatus(row: any) {
  console.log("切换状态", row);
}
</script>

<style scoped>
.admin-user-page {
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

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>