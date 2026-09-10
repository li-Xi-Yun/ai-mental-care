<template>
  <div class="admin-manage-page">
    <div class="page-header">
      <h2 class="page-title">管理员管理</h2>
      <el-button type="primary" @click="showAddDialog = true">+ 新增管理员</el-button>
    </div>

    <el-table :data="adminList" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="role" label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.role === '超级管理员' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
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
      <el-pagination v-model:current-page="currentPage" :page-size="DEFAULT_PAGE_SIZE" :total="3" :layout="PAGINATION_LAYOUT" background />
    </div>

    <el-dialog v-model="showAddDialog" title="新增管理员" width="480">
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input placeholder="请输入用户名" /></el-form-item>
        <el-form-item label="姓名"><el-input placeholder="请输入姓名" /></el-form-item>
        <el-form-item label="手机号"><el-input placeholder="请输入手机号" /></el-form-item>
        <el-form-item label="密码"><el-input type="password" placeholder="请输入密码" /></el-form-item>
        <el-form-item label="角色">
          <el-select placeholder="请选择角色" style="width: 100%">
            <el-option label="普通管理员" value="normal" />
            <el-option label="超级管理员" value="super" />
          </el-select>
        </el-form-item>
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
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const currentPage = ref(1);
const showAddDialog = ref(false);

const adminList = ref([
  { id: 1, username: "superadmin", name: "张三", phone: "13800000001", role: "超级管理员", status: "启用", createdTime: "2026-01-01 00:00" },
  { id: 2, username: "admin01", name: "李四", phone: "13800000002", role: "普通管理员", status: "启用", createdTime: "2026-03-15 10:30" },
  { id: 3, username: "admin02", name: "王五", phone: "13800000003", role: "普通管理员", status: "禁用", createdTime: "2026-06-20 14:00" },
]);
</script>

<style scoped>
.admin-manage-page {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
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