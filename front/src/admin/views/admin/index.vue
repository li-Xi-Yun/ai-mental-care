<template>
  <div class="admin-manage-page">
    <PageHeader title="管理员管理" subtitle="管理后台管理员账号、状态与角色分配">
      <el-button type="primary" @click="openAdd">
        <el-icon class="btn-icon"><Plus /></el-icon>新增管理员
      </el-button>
    </PageHeader>

    <!-- ==================== 统计卡 ==================== -->
    <section class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><UserFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.total }}</div>
          <div class="stat-label">管理员总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheckFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.normal }}</div>
          <div class="stat-label">正常启用</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-red">
          <el-icon :size="22"><CircleCloseFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.ban }}</div>
          <div class="stat-label">封禁中</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-gray">
          <el-icon :size="22"><RemoveFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.logout }}</div>
          <div class="stat-label">已注销</div>
        </div>
      </div>
    </section>

    <!-- ==================== 筛选卡 ==================== -->
    <section class="filter-card">
      <!-- 搜索条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索用户名 / 手机号 / 邮箱"
          clearable
          style="width: 250px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
      </div>
    </section>

    <!-- ==================== 表格卡片 ==================== -->
    <section class="content-card">
      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" class="admin-table" @row-click="handleRowClick">
        <el-table-column prop="id" label="ID" width="76" />
        <el-table-column label="用户名" min-width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="user-name">{{ row.username }}</span>
              <span v-if="isSelf(row)" class="me-tag">我</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" width="140">
          <template #default="{ row }">
            <span class="muted-text">{{ row.mobile || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="muted-text">{{ row.email || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <div class="role-tags">
              <template v-for="role in rowRoles(row).slice(0, 2)" :key="role.id">
                <span class="role-tag" :style="roleStyle(role)">{{ role.name || role.roleKey }}</span>
              </template>
              <span v-if="rowRoles(row).length > 2" class="role-more">+{{ rowRoles(row).length - 2 }}</span>
              <span v-if="!rowRoles(row).length" class="role-empty">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-badge" :style="statusBadgeStyle(row.status)">
                <span class="status-dot" :style="{ background: STATUS_META[row.status]?.color ?? '#94a3b8' }" />
                {{ statusLabel(row.status) }}
              </span>
              <span @click.stop>
                <el-switch
                  :model-value="row.status === 0"
                  :disabled="isSelf(row) || row.status === 3"
                  size="small"
                  @change="(val: string | number | boolean) => handleStatusToggle(row, val)"
                />
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button type="primary" text size="small" @click.stop="openEdit(row)">编辑</el-button>
              <el-button type="warning" text size="small" @click.stop="handleResetPwd(row)">重置密码</el-button>
              <el-button type="primary" text size="small" :disabled="isSelf(row)" @click.stop="openAssignRoles(row)">分配角色</el-button>
              <el-button type="danger" text size="small" :disabled="isSelf(row)" @click.stop="handleDelete(row)">注销</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="page-total">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="loadData"
        />
      </div>
    </section>

    <!-- ==================== 新增 / 编辑弹窗 ==================== -->
    <AdminFormDialog
      v-model="dialogVisible"
      :is-edit="isEdit"
      :target="editTarget"
      :submitting="submitting"
      @submit="submitAdminForm"
    />

    <!-- ==================== 封禁弹窗 ==================== -->
    <BanDialog v-model="banVisible" :target="banTarget" :submitting="banSubmitting" @submit="submitBan" />

    <!-- ==================== 分配角色弹窗 ==================== -->
    <AssignRoleDialog
      v-model="assignVisible"
      :target="assignTarget"
      :loading="roleLoading"
      :submitting="assignSubmitting"
      :role-options="roleOptions"
      :selected-role-ids="selectedRoleIds"
      @update:selected-role-ids="selectedRoleIds = $event"
      @submit="handleAssignSubmit"
    />

    <!-- ==================== 管理员详情弹窗 ==================== -->
    <AdminDetailDialog
      v-model="detailVisible"
      :row="detailRow"
      :detail="detailData"
      :loading="detailLoading"
    />
  </div>
</template>

<script setup lang="ts">
import {
  CircleCheckFilled,
  CircleCloseFilled,
  Plus,
  RefreshRight,
  RemoveFilled,
  Search,
  UserFilled,
} from "@element-plus/icons-vue";
import PageHeader from "@/admin/components/PageHeader.vue";
import { useAdminList } from "./useAdminList";
import AdminFormDialog from "./components/AdminFormDialog.vue";
import AdminDetailDialog from "./components/AdminDetailDialog.vue";
import BanDialog from "./components/BanDialog.vue";
import AssignRoleDialog from "./components/AssignRoleDialog.vue";

const {
  // 常量
  STATUS_META,
  STATUS_OPTIONS,
  PAGINATION_LAYOUT,
  // 展示工具
  statusBadgeStyle,
  statusLabel,
  roleStyle,
  formatTime,
  isSelf,
  rowRoles,
  // 列表
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  searchForm,
  stats,
  loadData,
  handleSearch,
  handleReset,
  // 新增 / 编辑
  dialogVisible,
  isEdit,
  editTarget,
  submitting,
  openAdd,
  openEdit,
  submitAdminForm,
  // 状态切换 / 封禁
  banVisible,
  banTarget,
  banSubmitting,
  handleStatusToggle,
  submitBan,
  // 重置密码 / 注销
  handleResetPwd,
  handleDelete,
  // 角色分配
  assignVisible,
  assignTarget,
  roleLoading,
  roleOptions,
  selectedRoleIds,
  assignSubmitting,
  openAssignRoles,
  handleAssignSubmit,
  // 详情
  detailVisible,
  detailLoading,
  detailRow,
  detailData,
  handleRowClick,
} = useAdminList();
</script>

<style scoped>
.admin-manage-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: #1e293b;
}

/* 页面特有样式（公共 stat-grid / stat-card / toolbar / status-badge / 表格样式已迁至 styles/index.css） */

.user-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-name {
  font-weight: 600;
  color: #0f172a;
}

.me-tag {
  font-size: 10px;
  font-weight: 600;
  color: #6366f1;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  border-radius: 6px;
  padding: 1px 6px;
  line-height: 1.4;
}

.muted-text {
  font-size: 12px;
  color: #475569;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.role-tag {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 11.5px;
  line-height: 1.5;
  white-space: nowrap;
}

.role-more {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 22px;
}

.role-empty {
  font-size: 12px;
  color: #cbd5e1;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

/* 整行可点进详情 */
.admin-table :deep(.el-table__row) {
  cursor: pointer;
}
</style>
