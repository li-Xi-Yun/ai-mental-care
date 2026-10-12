<template>
  <div class="temp-permission-page">
    <!-- ==================== 页头 ==================== -->
    <PageHeader title="临时权限" subtitle="查看与授予前台用户、后台管理员的限时权限">
      <button
        type="button"
        class="op-icon-btn"
        :class="{ 'is-refreshing': loading }"
        title="刷新"
        :disabled="loading"
        @click="handleRefresh"
      >
        <el-icon><Refresh /></el-icon>
      </button>
      <el-button type="primary" @click="handleGrant">
        <el-icon class="btn-icon"><Key /></el-icon>授予临时权限
      </el-button>
    </PageHeader>

    <!-- ==================== 统计卡 ==================== -->
    <StatGrid :columns="2">
      <StatCard icon="User" tone="indigo" :num="`${stats.user} 人`" label="前台用户被授权人" />
      <StatCard icon="UserFilled" tone="green" :num="`${stats.admin} 人`" label="后台管理员被授权人" />
    </StatGrid>

    <!-- ==================== 筛选卡：类型切换 + 重置 ==================== -->
    <div class="filter-card tp-filter">
      <el-tabs v-model="activeTab" class="type-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="前台用户" name="user" />
        <el-tab-pane label="后台管理员" name="admin" />
      </el-tabs>
      <div class="tp-filter-right">
        <el-button text size="small" :icon="RefreshLeft" @click="handleReset">重置</el-button>
        <span class="tp-filter-count">共 <b>{{ total }}</b> 位被授权人</span>
      </div>
    </div>

    <!-- ==================== 主从布局 ==================== -->
    <div class="tp-master-detail">
      <!-- 左：被授权人 -->
      <section class="tp-card tp-person-card">
        <header class="tp-card-head">
          <el-icon class="tp-head-icon"><UserFilled /></el-icon>
          <span class="tp-head-title">被授权人</span>
          <span class="tp-head-sub">{{ total }} 人</span>
        </header>

        <div class="tp-person-body">
          <!-- 加载骨架屏 -->
          <div v-if="loading && !tableData.length" class="tp-skeleton">
            <div v-for="i in 5" :key="i" class="tp-skel-row">
              <span class="tp-skel-avatar" />
              <span class="tp-skel-lines">
                <span class="tp-skel-line" style="width: 72px" />
                <span class="tp-skel-line tp-skel-line-sm" style="width: 52px" />
              </span>
            </div>
          </div>

          <!-- 空态 -->
          <div v-else-if="!tableData.length" class="tp-empty">暂无被授权人</div>

          <!-- 被授权人行 -->
          <template v-else>
            <button
              v-for="row in tableData"
              :key="row.personId"
              type="button"
              class="tp-person-row"
              :class="{ active: row.personId === selectedPersonId }"
              @click="selectedPersonId = row.personId"
            >
              <span class="tp-avatar" :style="avatarStyle(row.personUsername)">{{ initialOf(row.personUsername) }}</span>
              <span class="tp-person-main">
                <span class="tp-person-name">{{ row.personUsername || "未知用户" }}</span>
                <span class="tp-person-sub">{{ row.permCount != null ? `${row.permCount} 条临时权限` : "—" }}</span>
              </span>
              <el-icon v-if="row.personId === selectedPersonId" class="tp-person-check"><Select /></el-icon>
            </button>
          </template>
        </div>

        <!-- 分页 -->
        <footer class="tp-card-foot">
          <span class="tp-page-info">第 {{ pageRange }} 条 / 共 {{ total }} 条</span>
          <div class="tp-page-btns">
            <el-button text size="small" :disabled="pageNum <= 1" @click="handlePrevPage">上一页</el-button>
            <el-button text size="small" :disabled="pageNum >= totalPages" @click="handleNextPage">下一页</el-button>
          </div>
        </footer>
      </section>

      <!-- 右：权限明细 -->
      <section class="tp-card tp-detail-card">
        <header class="tp-card-head">
          <el-icon class="tp-head-icon"><Key /></el-icon>
          <span class="tp-head-title">权限明细</span>
          <span class="tp-head-sub">{{ selectedPersonName ? `${selectedPersonName} · ${detailList.length} 条` : "请选择左侧被授权人" }}</span>
        </header>

        <el-table
          v-loading="detailLoading"
          :data="selectedPersonId === null ? [] : detailList"
          class="tp-table"
          size="small"
          style="width: 100%"
          @row-click="openDetail"
        >
          <el-table-column label="权限" min-width="200">
            <template #default="{ row }">
              <div class="tp-perm-cell">
                <el-icon class="tp-perm-icon"><Lock /></el-icon>
                <div class="tp-perm-main">
                  <div class="tp-perm-name">{{ row.permissionName || "—" }}</div>
                  <div class="tp-perm-code">
                    {{ formatPerms(row.perms) }}<template v-if="row.groupName"> · {{ row.groupName }}</template>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="生效时间" width="150">
            <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="过期时间" width="150">
            <template #default="{ row }">
              <span :class="{ 'tp-expire-amber': row.status === 2 }">{{ formatTime(row.expireTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <StatusBadge
                :label="statusMetaOf(row.status).label"
                :color="statusMetaOf(row.status).color"
                :bg="statusMetaOf(row.status).bg"
                :border="statusMetaOf(row.status).border"
              />
            </template>
          </el-table-column>
          <el-table-column label="授权原因" min-width="150">
            <template #default="{ row }">
              <span class="tp-reason" :title="row.grantReason || ''">{{ row.grantReason || "—" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="授权人" width="110">
            <template #default="{ row }">{{ row.grantUsername || "—" }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 0"
                text
                size="small"
                class="op-btn op-revoke"
                @click.stop="handleRevoke(row as PermItem)"
              >
                作废
              </el-button>
              <span v-else class="tp-muted">—</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="tp-detail-empty">
              <el-icon :size="20"><Lock /></el-icon>
              <p>{{ selectedPersonId === null ? "请选择左侧被授权人" : "该被授权人暂无临时权限记录" }}</p>
            </div>
          </template>
        </el-table>
      </section>
    </div>

    <!-- ==================== 授予临时权限弹窗 ==================== -->
    <GrantPermissionDialog
      v-model="grantVisible"
      :permission-tree="permissionTree"
      :perm-loading="permLoading"
      :submitting="grantSubmitting"
      @submit="handleGrantSubmit"
    />

    <!-- ==================== 权限明细详情弹窗（纯行数据，零请求） ==================== -->
    <TempPermissionDetailDialog v-model="detailVisible" :row="detailRow" />
  </div>
</template>

<script setup lang="ts">
import { Key, Lock, Refresh, RefreshLeft, Select, UserFilled } from "@element-plus/icons-vue";
import PageHeader from "@/admin/components/PageHeader.vue";
import StatCard from "@/admin/components/StatCard.vue";
import StatGrid from "@/admin/components/StatGrid.vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { useTempPermissionList } from "./useTempPermissionList";
import type { PermItem } from "./useTempPermissionList";
import GrantPermissionDialog from "./components/GrantPermissionDialog.vue";
import TempPermissionDetailDialog from "./components/TempPermissionDetailDialog.vue";

const {
  // 页面状态
  activeTab,
  loading,
  tableData,
  total,
  pageNum,
  totalPages,
  pageRange,
  selectedPersonId,
  selectedPersonName,
  stats,
  handleTabChange,
  handlePrevPage,
  handleNextPage,
  handleReset,
  handleRefresh,
  // 权限明细
  detailLoading,
  detailList,
  handleRevoke,
  detailVisible,
  detailRow,
  openDetail,
  // 授予权限弹窗
  grantVisible,
  grantSubmitting,
  permissionTree,
  permLoading,
  handleGrant,
  handleGrantSubmit,
  // 展示工具
  statusMetaOf,
  formatTime,
  formatPerms,
  avatarStyle,
  initialOf,
} = useTempPermissionList();
</script>

<style scoped>
/* 页面根：透明容器 + 20px 间距 */
.temp-permission-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: #1e293b;
}

/* ==================== 页头刷新按钮 ==================== */

.op-icon-btn.is-refreshing .el-icon {
  animation: tp-spin 1s linear infinite;
}

@keyframes tp-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ==================== 筛选卡：el-tabs 分段控件观感 ==================== */

.tp-filter {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.type-tabs {
  flex: 0 0 auto;
}

.type-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.type-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.type-tabs :deep(.el-tabs__nav) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 8px;
  background: #f1f5f9;
}

.type-tabs :deep(.el-tabs__item) {
  height: 30px;
  line-height: 30px;
  padding: 0 16px;
  font-size: 13px;
  color: #475569;
  border-radius: 6px;
  transition: background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}

.type-tabs :deep(.el-tabs__item:hover) {
  color: var(--admin-primary, #4f46e5);
}

.type-tabs :deep(.el-tabs__item.is-active) {
  background: #fff;
  color: var(--admin-primary, #4f46e5);
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.type-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.type-tabs :deep(.el-tabs__content) {
  display: none;
}

.tp-filter-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.tp-filter-count {
  font-size: 12px;
  color: #94a3b8;
}

.tp-filter-count b {
  color: #475569;
  font-weight: 600;
}

/* ==================== 主从布局 ==================== */

.tp-master-detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: 20px;
  align-items: start;
}

@media (max-width: 1024px) {
  .tp-master-detail {
    grid-template-columns: minmax(0, 1fr);
  }
}

.tp-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--admin-border, #e2e8f0);
  border-radius: 12px;
  box-shadow: var(--admin-shadow-card, 0 1px 2px rgba(16, 24, 40, 0.04));
  overflow: hidden;
}

.tp-person-card {
  min-height: 420px;
}

.tp-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  border-bottom: 1px solid #eef1f6;
}

.tp-head-icon {
  color: #6366f1;
  font-size: 15px;
}

.tp-head-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.tp-head-sub {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
}

/* ==================== 左卡：被授权人 ==================== */

.tp-person-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
}

.tp-person-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.tp-person-row:hover {
  background: #f8fafc;
}

.tp-person-row.active {
  background: var(--admin-primary-soft, #eef2ff);
  border-color: #c7d2fe;
}

.tp-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.15);
}

.tp-person-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tp-person-name {
  font-size: 13.5px;
  font-weight: 500;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tp-person-row.active .tp-person-name {
  color: #4338ca;
}

.tp-person-sub {
  font-size: 12px;
  color: #94a3b8;
}

.tp-person-check {
  color: #6366f1;
  flex-shrink: 0;
}

.tp-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: #94a3b8;
}

/* 加载骨架屏 */
.tp-skeleton {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tp-skel-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
}

.tp-skel-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f1f5f9;
  animation: tp-pulse 1.6s ease-in-out infinite;
  flex-shrink: 0;
}

.tp-skel-lines {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tp-skel-line {
  display: block;
  height: 10px;
  border-radius: 6px;
  background: #f1f5f9;
  animation: tp-pulse 1.6s ease-in-out infinite;
}

.tp-skel-line-sm {
  height: 8px;
}

@keyframes tp-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

/* 分页 */
.tp-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px solid #eef1f6;
}

.tp-page-info {
  font-size: 12px;
  color: #94a3b8;
}

.tp-page-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ==================== 右卡：权限明细 ==================== */

.tp-table :deep(th.el-table__cell) {
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

/* 整行可点进详情（仅右侧「权限明细」表） */
.tp-table :deep(.el-table__row) {
  cursor: pointer;
}

.tp-perm-cell {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.tp-perm-icon {
  margin-top: 2px;
  color: #818cf8;
  flex-shrink: 0;
}

.tp-perm-main {
  min-width: 0;
}

.tp-perm-name {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
}

.tp-perm-code {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tp-expire-amber {
  color: #d97706;
}

.tp-reason {
  display: block;
  max-width: 170px;
  font-size: 12.5px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tp-muted {
  font-size: 12px;
  color: #cbd5e1;
}

.tp-detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 0;
  font-size: 13px;
  color: #94a3b8;
}

.tp-detail-empty .el-icon {
  color: #cbd5e1;
}
</style>
