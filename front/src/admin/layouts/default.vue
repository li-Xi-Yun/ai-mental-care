<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-aside">
      <div class="admin-logo">AI Mental Care 管理后台</div>
      <el-menu router default-active="/admin">
        <el-menu-item index="/admin/user">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/file">
          <el-icon><Document /></el-icon>
          <span>文件管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/scale">
          <el-icon><DataAnalysis /></el-icon>
          <span>量表管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/symptom">
          <el-icon><FirstAidKit /></el-icon>
          <span>症状字典</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <span>管理后台</span>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { User, Document, DataAnalysis, FirstAidKit } from "@element-plus/icons-vue";
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
.admin-aside {
  border-right: 1px solid #e5e7eb;
}
.admin-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #e5e7eb;
}
.admin-header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #e5e7eb;
}
</style>