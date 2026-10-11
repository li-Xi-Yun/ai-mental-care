<template>
  <div class="skill-page">
    <!-- ==================== 页头 ==================== -->
    <PageHeader title="Skill管理" subtitle="管理对话增强与诊断辅助 Skill">
      <el-button type="primary" @click="showAddDialog = true">
        <el-icon class="btn-icon"><Plus /></el-icon>新增Skill
      </el-button>
    </PageHeader>

    <!-- ==================== 统计卡 ==================== -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><Grid /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ totalCount }}</div>
          <div class="stat-label">Skill 总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheck /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ enabledCount }}</div>
          <div class="stat-label">启用数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-gray">
          <el-icon :size="22"><CircleClose /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ disabledCount }}</div>
          <div class="stat-label">禁用数</div>
        </div>
      </div>
    </div>

    <!-- ==================== 主卡片 ==================== -->
    <div class="content-card">
      <el-table :data="skillList" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="Skill名称" width="180" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="108" align="center">
          <template #default>
            <div class="op-cell">
              <el-button type="primary" text size="small">编辑</el-button>
              <el-button type="danger" text size="small">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination v-model:current-page="currentPage" :page-size="DEFAULT_PAGE_SIZE" :total="4" :layout="PAGINATION_LAYOUT" background />
      </div>
    </div>

    <SkillFormDialog v-model="showAddDialog" />
  </div>
</template>

<script setup lang="ts">
import { CircleCheck, CircleClose, Grid, Plus } from "@element-plus/icons-vue";
import PageHeader from "@/admin/components/PageHeader.vue";
import { useSkillList } from "./useSkillList";
import SkillFormDialog from "./components/SkillFormDialog.vue";

const {
  currentPage,
  showAddDialog,
  skillList,
  totalCount,
  enabledCount,
  disabledCount,
  DEFAULT_PAGE_SIZE,
  PAGINATION_LAYOUT,
} = useSkillList();
</script>

<style scoped>
/* 主题变量由 body.admin-mode 全局提供，页面根仅保留 20px 间距布局 */
.skill-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: #1e293b;
}

/* 页面共 3 张统计卡，覆写全局 .stat-grid 的 4 列布局 */
.skill-page .stat-grid {
  grid-template-columns: repeat(3, 1fr);
}

/* 公共 .pagination-bar（display/flex/间距）已由 admin/styles/index.css 提供，仅保留页面特有的右对齐差异 */
.pagination-bar {
  justify-content: flex-end;
}
</style>
