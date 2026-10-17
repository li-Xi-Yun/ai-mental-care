<template>
  <div class="quota-user-page">
    <PageHeader title="用户限流管理" subtitle="查看并调整单个用户的 AI 资源配额（跟随全局 / 自定义 / 不限额）" />

    <!-- ==================== 筛选卡 ==================== -->
    <section class="filter-card">
      <div class="toolbar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索用户名 / 登录账号"
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
          <el-option v-for="s in USER_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
      </div>
    </section>

    <!-- ==================== 表格卡片 ==================== -->
    <section class="content-card">
      <el-table v-loading="loading" :data="tableData" row-key="id" class="quota-user-table" @row-click="openQuota($event as SysUserPageVO)">
        <el-table-column prop="id" label="用户ID" width="100" />
        <el-table-column label="用户名" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="avatar-wrap">
                <img v-if="row.avatar" :src="row.avatar" class="avatar-img" alt="" />
                <span v-else class="avatar-letter" :style="avatarStyle(row as SysUserPageVO)">{{ avatarText(row as SysUserPageVO) }}</span>
              </div>
              <div class="user-info">
                <div class="user-name">{{ row.username || "—" }}</div>
                <div class="user-account">{{ row.loginAccount || "" }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="status-badge" :style="statusBadgeStyle(row.status)">
              <span class="status-dot" :style="statusDotStyle(row.status)" />
              {{ statusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button type="primary" text size="small" @click.stop="openQuota(row as SysUserPageVO)">
                <el-icon><Tickets /></el-icon>配额
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

    <!-- ==================== 配额总览弹窗 ==================== -->
    <QuotaDialog
      v-model="quotaVisible"
      v-model:loading="quotaLoading"
      :target="quotaTarget"
      @closed="handleQuotaClosed"
    />
  </div>
</template>

<script setup lang="ts">
import PageHeader from "@/admin/components/PageHeader.vue";
import { useQuotaUserList } from "./useQuotaUserList";
import QuotaDialog from "./components/QuotaDialog.vue";
import type { SysUserPageVO } from "@/admin/api/admin/user-manage";

const {
  // 展示工具
  statusLabel,
  statusBadgeStyle,
  statusDotStyle,
  avatarText,
  avatarStyle,
  formatTime,
  // 列表
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  searchForm,
  loadData,
  handleSearch,
  handleReset,
  // 配额弹窗
  quotaVisible,
  quotaLoading,
  quotaTarget,
  openQuota,
  USER_STATUS_OPTIONS,
  PAGINATION_LAYOUT,
} = useQuotaUserList();

/** 弹窗关闭后不保留上一用户的本次配额数据（内部 @closed 自会重置） */
function handleQuotaClosed() {}
</script>

<style scoped>
.quota-user-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: #1e293b;
}

/* 页面特有样式（公共 filter-card / toolbar / content-card / status-badge / op-cell / pagination-bar 已迁至 styles/index.css） */

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

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}
</style>