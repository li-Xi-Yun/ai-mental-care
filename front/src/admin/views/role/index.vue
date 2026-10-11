<template>
  <div class="role-page">
    <PageHeader title="角色管理" subtitle="维护角色及其权限分配">
      <el-button type="primary" @click="openDialog()">
        <el-icon class="btn-icon"><Plus /></el-icon>新增角色
      </el-button>
    </PageHeader>

    <!-- 统计卡：三次轻量查询取总数 -->
    <StatGrid :columns="3">
      <StatCard :icon="Avatar" tone="indigo" :num="stats.total" label="角色总数" />
      <StatCard :icon="CircleCheck" tone="green" :num="stats.normal" label="正常角色" />
      <StatCard :icon="CircleClose" tone="gray" :num="stats.disabled" label="已停用" />
    </StatGrid>

    <div class="filter-card">
      <div class="toolbar">
        <el-input v-model="query.name" placeholder="角色名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        <el-input v-model="query.roleKey" placeholder="角色标识" clearable style="width: 180px" @keyup.enter="handleSearch" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option v-for="s in ROLE_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <div class="content-card">
      <!-- 行点击查看详情；操作列按钮 @click.stop 阻断 -->
      <el-table v-loading="loading" :data="roleList" stripe class="role-table" @row-click="openDetailDialog">
        <el-table-column label="角色" min-width="190">
          <template #default="{ row }">
            <div class="rp-role-cell">
              <span class="rp-role-badge" :style="{ background: hashColorFor(row.roleKey, 2).gradient }">
                <el-icon :size="16"><Avatar /></el-icon>
              </span>
              <span class="rp-role-name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色标识" min-width="180">
          <template #default="{ row }">
            <div class="rp-rolekey-cell">
              <span class="rp-rolekey-dot" :style="{ background: hashColorFor(row.roleKey, 2).gradient }" />
              <code class="rp-role-key">{{ row.roleKey }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <StatusBadge
              :label="roleStatus.labelOf(row.status)"
              :color="roleStatus.itemOf(row.status).color"
              :bg="roleStatus.itemOf(row.status).bg"
              :border="roleStatus.itemOf(row.status).border"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="168" align="center" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <button class="op-icon-btn op-grant" type="button" title="分配权限" @click.stop="openPermissionDialog(row)">
                <el-icon :size="15"><Key /></el-icon>
              </button>
              <button class="op-icon-btn op-edit" type="button" title="编辑" @click.stop="openDialog(row)">
                <el-icon :size="15"><Edit /></el-icon>
              </button>
              <button
                v-if="row.status === 0"
                class="op-icon-btn op-disable"
                type="button"
                title="停用"
                @click.stop="handleToggleStatus(row, 1)"
              >
                <el-icon :size="15"><CircleClose /></el-icon>
              </button>
              <button
                v-else
                class="op-icon-btn op-enable"
                type="button"
                title="启用"
                @click.stop="handleToggleStatus(row, 0)"
              >
                <el-icon :size="15"><CircleCheck /></el-icon>
              </button>
              <button class="op-icon-btn op-del" type="button" title="删除" @click.stop="handleDelete(row)">
                <el-icon :size="15"><Delete /></el-icon>
              </button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <span class="page-total">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="fetchRoles"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 新增/编辑角色弹窗 -->
    <RoleFormDialog v-model="dialogVisible" :target="formTarget" :submitting="submitting" @submit="handleSubmit" />

    <!-- 角色详情弹窗 -->
    <RoleDetailDialog v-model="detailVisible" :role="detailRole" :detail="detailData" :loading="detailLoading" />

    <!-- 权限分配弹窗 -->
    <PermAssignDialog
      v-model="permDialogVisible"
      :role-name="currentRole?.name ?? ''"
      :role-key="currentRole?.roleKey ?? ''"
      :loading="permLoading"
      :submitting="permSubmitting"
      :groups="permGroups"
      :selected-ids="selectedPermIds"
      @toggle="togglePermission"
      @submit="handleSavePermissions"
      @closed="closePermissionDialog"
    />
  </div>
</template>

<script setup lang="ts">
import { Avatar, CircleCheck, CircleClose, Delete, Edit, Key, Plus, Search } from "@element-plus/icons-vue";
import { PageHeader, StatCard, StatGrid, StatusBadge } from "@/admin/components";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { useRoleList } from "./useRoleList";
import RoleFormDialog from "./components/RoleFormDialog.vue";
import RoleDetailDialog from "./components/RoleDetailDialog.vue";
import PermAssignDialog from "./components/PermAssignDialog.vue";

const {
  // 列表
  loading,
  roleList,
  total,
  query,
  fetchRoles,
  handleSearch,
  handleReset,
  handleSizeChange,
  formatTime,
  handleToggleStatus,
  handleDelete,
  roleStatus,
  // 统计卡
  stats,
  // 新增 / 编辑
  dialogVisible,
  submitting,
  formTarget,
  openDialog,
  handleSubmit,
  // 详情
  detailVisible,
  detailLoading,
  detailRole,
  detailData,
  openDetailDialog,
  // 权限分配
  permDialogVisible,
  permLoading,
  permSubmitting,
  currentRole,
  permGroups,
  selectedPermIds,
  openPermissionDialog,
  togglePermission,
  handleSavePermissions,
  closePermissionDialog,
  // 常量
  ROLE_STATUS_OPTIONS,
  PAGINATION_LAYOUT,
} = useRoleList();
</script>

<style scoped>
/* 页面根：透明容器 + 20px 间距（筛选/表格各自成卡） */
.role-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
/* 角色列：渐变徽章 + 名称 */
.rp-role-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rp-role-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 8px;
  color: #fff;
}
.rp-role-name {
  font-weight: 500;
  color: #334155;
}
/* 角色标识列：渐变小圆点 + 灰底代码胶囊 */
.rp-rolekey-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.rp-rolekey-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
}
.rp-role-key {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  color: #475569;
  background: #f1f5f9;
  padding: 1px 8px;
  border-radius: 6px;
}
/* 整行可点进详情 */
.role-table :deep(.el-table__row) {
  cursor: pointer;
}
</style>
