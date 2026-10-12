<template>
  <div class="user-manage-page">
    <PageHeader title="用户管理" subtitle="管理系统用户账号、状态与角色权限" />

    <!-- ==================== 统计条 ==================== -->
    <section class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><UserFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.total }}</div>
          <div class="stat-label">用户总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheckFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.normal }}</div>
          <div class="stat-label">正常</div>
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
          placeholder="搜索用户名 / 登录账号 / 手机号 / 邮箱"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="searchForm.status"
          placeholder="全部状态"
          clearable
          style="width: 130px"
          @change="handleSearch"
        >
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
        <el-button type="danger" plain :disabled="!selectedRows.length" @click="openBatchBan">
          <el-icon class="btn-icon"><CircleCloseFilled /></el-icon>批量封禁{{ selectedRows.length ? `（${selectedRows.length}）` : "" }}
        </el-button>
      </div>
    </section>

    <!-- ==================== 表格卡片 ==================== -->
    <section class="content-card">
      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="user-table"
        @row-click="handleRowClick"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="用户ID" width="92" />
        <el-table-column label="用户名" min-width="170">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="avatar-wrap">
                <img v-if="row.avatar" :src="row.avatar" class="avatar-img" alt="" />
                <span v-else class="avatar-letter" :style="avatarStyle(row.gender)">{{ avatarText(row) }}</span>
              </div>
              <div class="user-info">
                <div class="user-name">{{ row.username || "—" }}</div>
                <div class="user-account">{{ row.loginAccount || "" }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">
            <span class="mobile-text">{{ mobileOf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="性别" width="86">
          <template #default="{ row }">
            <span class="gender-tag" :style="genderTagStyle(row.gender)">
              <span class="gender-dot" :style="{ background: genderMetaOf(row.gender).color }" />
              {{ genderLabel(row.gender) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="status-badge" :style="statusBadgeStyle(row.status)">
              <span class="status-dot" :style="{ background: statusMetaDot(row.status) }" />
              {{ statusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button type="primary" text size="small" @click.stop="openDetail(row)">详情</el-button>
              <el-button type="primary" text size="small" :disabled="row.status === 3" @click.stop="openAssignDialog(row)">
                编辑角色
              </el-button>
              <el-button
                v-if="row.status === 2"
                type="success"
                text
                size="small"
                @click.stop="handleEnable(row)"
              >
                启用
              </el-button>
              <el-button
                v-else
                type="danger"
                text
                size="small"
                :disabled="row.status === 3"
                @click.stop="openBanDialog(row)"
              >
                封禁
              </el-button>
              <el-button type="warning" text size="small" @click.stop="openPermDrawer(row)">
                临时权限
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" :image-size="80" />
        </template>
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

    <!-- ==================== 用户详情抽屉 ==================== -->
    <UserDetailDrawer
      v-model="detailVisible"
      :loading="detailLoading"
      :target="detailTarget"
      :detail="detail"
      :status-label="statusLabel"
      :status-badge-style="statusBadgeStyle"
      :gender-label="genderLabel"
      :avatar-large-text="avatarLargeText"
      :avatar-style="avatarStyle"
      :format-time="formatTime"
    />

    <!-- ==================== 角色分配弹窗 ==================== -->
    <AssignRoleDialog
      v-model="assignVisible"
      :target="assignTarget"
      :loading="roleLoading"
      :submitting="assignSubmitting"
      :role-options="roleOptions"
      :selected-role-ids="selectedRoleIds"
      @update:selected-role-ids="selectedRoleIds = $event"
      @submit="submitAssign"
    />

    <!-- ==================== 封禁弹窗 ==================== -->
    <BanDialog
      v-model="banVisible"
      :title="banTitle"
      :submitting="banSubmitting"
      :disable-past-date="disablePastDate"
      @submit="submitBan"
    />

    <!-- ==================== 临时权限抽屉 ==================== -->
    <PermDrawer
      v-model="permVisible"
      :loading="permLoading"
      :target="permTarget"
      :perm-list="permList"
      :perm-codes="permCodes"
      :perm-status-text="permStatusText"
      :perm-tag-type="permTagType"
      :perm-timeline-type="permTimelineType"
      :format-time="formatTime"
    />
  </div>
</template>

<script setup lang="ts">
import {
  CircleCheckFilled,
  CircleCloseFilled,
  RefreshRight,
  RemoveFilled,
  Search,
  UserFilled,
} from "@element-plus/icons-vue";
import PageHeader from "@/admin/components/PageHeader.vue";
import { USER_STATUS_META } from "@/admin/constants/user";
import { useUserList } from "./useUserList";
import UserDetailDrawer from "./components/UserDetailDrawer.vue";
import AssignRoleDialog from "./components/AssignRoleDialog.vue";
import BanDialog from "./components/BanDialog.vue";
import PermDrawer from "./components/PermDrawer.vue";

const {
  // 工具
  statusLabel,
  statusBadgeStyle,
  genderLabel,
  genderMetaOf,
  genderTagStyle,
  avatarText,
  avatarLargeText,
  avatarStyle,
  permCodes,
  permStatusText,
  permTagType,
  permTimelineType,
  disablePastDate,
  formatTime,
  // 列表
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  searchForm,
  stats,
  selectedRows,
  handleSelectionChange,
  mobileOf,
  handleSearch,
  handleReset,
  // 封禁
  banVisible,
  banTitle,
  banSubmitting,
  openBanDialog,
  openBatchBan,
  submitBan,
  handleEnable,
  // 角色
  assignVisible,
  assignSubmitting,
  assignTarget,
  roleLoading,
  roleOptions,
  selectedRoleIds,
  openAssignDialog,
  submitAssign,
  // 详情
  detailVisible,
  detailLoading,
  detailTarget,
  detail,
  openDetail,
  handleRowClick,
  // 临时权限
  permVisible,
  permLoading,
  permTarget,
  permList,
  openPermDrawer,
  loadData,
  STATUS_OPTIONS,
  PAGINATION_LAYOUT,
} = useUserList();

/** 状态圆点颜色（供表格内 status-dot 使用） */
function statusMetaDot(status?: number): string {
  return USER_STATUS_META[status ?? 3]?.color ?? "#94a3b8";
}
</script>

<style scoped>
.user-manage-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: #1e293b;
}

/* 页面特有样式（公共 stat-grid / stat-card / toolbar / status-badge / 表格样式已迁至 styles/index.css） */

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-wrap {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  position: relative;
}

.avatar-wrap.avatar-lg {
  width: 64px;
  height: 64px;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-letter {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.5px;
  user-select: none;
}

.user-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.user-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-account {
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mobile-text {
  font-size: 12.5px;
  color: #475569;
  font-variant-numeric: tabular-nums;
}

.gender-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.gender-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

/* 整行可点进详情 */
:deep(.el-table__row) {
  cursor: pointer;
}
</style>