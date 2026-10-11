<template>
  <el-drawer :model-value="modelValue" :title="`用户详情 · ${target?.username ?? ''}`" size="540px" @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading" class="drawer-body">
      <template v-if="detail">
        <div class="detail-head">
          <div class="avatar-wrap avatar-lg">
            <img v-if="detail.avatar" :src="detail.avatar" class="avatar-img" alt="" />
            <span v-else class="avatar-letter" :style="avatarStyle(detail.gender)">{{ avatarLargeText(detail) }}</span>
          </div>
          <div class="detail-head-info">
            <div class="detail-name">{{ detail.username || "—" }}</div>
            <div class="detail-account">{{ detail.loginAccount || "" }}</div>
            <span class="status-badge" :style="statusBadgeStyle(detail.status ?? 3)">
              <span class="status-dot" :style="{ background: STATUS_META[detail.status ?? 3]?.color ?? '#94a3b8' }" />
              {{ statusLabel(detail.status) }}
            </span>
          </div>
        </div>

        <el-descriptions :column="1" border size="small" class="detail-desc">
          <el-descriptions-item label="用户ID">{{ detail.id }} </el-descriptions-item>
          <el-descriptions-item label="登录账号">{{ detail.loginAccount || "—" }}</el-descriptions-item>
          <el-descriptions-item label="手机号">
            <span class="desc-highlight">{{ detail.mobile || "—" }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.email || "—" }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ genderLabel(detail.gender) }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatTime(detail.createdTime) }}</el-descriptions-item>
          <el-descriptions-item label="最后更新">{{ formatTime(detail.updatedTime) }}</el-descriptions-item>
          <el-descriptions-item label="个人简介">
            <span class="desc-intro">{{ detail.introduction || "—" }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <div class="detail-section-title">
            <el-icon :size="15"><WarningFilled /></el-icon>
            封禁记录
          </div>
          <div v-if="detail.status === 2" class="ban-card">
            <div class="ban-item">
              <span class="ban-label">封禁理由</span>
              <span class="ban-value">{{ detail.banReason || "—" }}</span>
            </div>
            <div class="ban-item">
              <span class="ban-label">封禁时间</span>
              <span class="ban-value">{{ formatTime(detail.banTime) }}</span>
            </div>
            <div class="ban-item">
              <span class="ban-label">解封时间</span>
              <span class="ban-value ban-permanent">{{ detail.banEndTime ? formatTime(detail.banEndTime) : "永久封禁" }}</span>
            </div>
          </div>
          <div v-else class="ban-empty">
            <el-icon :size="16"><CircleCheckFilled /></el-icon>
            <span>该账号当前未被封禁</span>
          </div>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { CircleCheckFilled, WarningFilled } from "@element-plus/icons-vue";
import type { SysUserDetailVO } from "@/admin/api/admin/user-manage";
import { USER_STATUS_META } from "@/admin/constants/user";

defineProps<{
  modelValue: boolean;
  loading: boolean;
  target?: { username?: string } | null;
  detail?: SysUserDetailVO | null;
  statusLabel: (v?: number) => string;
  statusBadgeStyle: (v?: number) => Record<string, string>;
  genderLabel: (v?: number) => string;
  avatarLargeText: (d: SysUserDetailVO) => string;
  avatarStyle: (g?: number) => Record<string, string>;
  formatTime: (v?: string) => string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
}>();

const STATUS_META = USER_STATUS_META;
</script>

<style scoped>
.drawer-body {
  min-height: 200px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0 18px;
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

.detail-head-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.detail-name {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.detail-account {
  font-size: 12.5px;
  color: #94a3b8;
}

.detail-desc {
  margin-bottom: 20px;
}

.detail-desc :deep(.el-descriptions__label) {
  color: #64748b;
  font-weight: 500;
  width: 96px;
}

.desc-highlight {
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.desc-intro {
  color: #475569;
  line-height: 1.6;
  white-space: pre-wrap;
}

.detail-section {
  margin-top: 6px;
}

.detail-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 10px;
}

.detail-section-title .el-icon {
  color: #ef4444;
}

.ban-card {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ban-item {
  display: flex;
  gap: 10px;
  font-size: 13px;
  line-height: 1.6;
}

.ban-label {
  color: #9f1239;
  font-weight: 500;
  flex-shrink: 0;
  width: 60px;
}

.ban-value {
  color: #7f1d1d;
  word-break: break-all;
}

.ban-permanent {
  font-weight: 600;
}

.ban-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 10px;
}

.ban-empty .el-icon {
  color: #22c55e;
}
</style>