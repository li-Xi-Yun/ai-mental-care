<template>
  <div class="profile-page">
    <!-- ==================== 页头 ==================== -->
    <PageHeader title="个人资料" subtitle="管理个人资料与账号安全" />

    <!-- 顶部用户卡片 -->
    <section class="user-card">
      <div class="user-card__glow" aria-hidden="true"></div>
      <div class="user-card__avatar">
        <AvatarUpload
          :avatar-url="avatarUrl"
          :avatar-text="avatarText"
          :handle-upload="handleAvatarUpload"
          :before-upload="beforeAvatarUpload"
        />
      </div>
      <div class="user-card__info">
        <div class="user-card__name-row">
          <span class="user-card__name">{{ profile.username || "—" }}</span>
          <span class="status-badge" :class="statusMeta.cls">{{ statusMeta.label }}</span>
        </div>
        <div class="user-card__account">
          <el-icon :size="14"><User /></el-icon>
          <span>账号：{{ profile.loginAccount || "—" }}</span>
        </div>
        <div class="user-card__created">
          <el-icon :size="14"><Calendar /></el-icon>
          <span>创建时间：{{ formatTime(profile.createdTime) }}</span>
        </div>
        <div v-if="status === 2 && profile.banReason" class="user-card__ban">
          <el-icon :size="14"><WarningFilled /></el-icon>
          <span class="user-card__ban-reason">{{ profile.banReason }}</span>
        </div>
      </div>
      <el-button class="user-card__refresh" text size="small" :loading="loading" @click="loadProfile">
        刷新
      </el-button>
    </section>

    <!-- 基本信息 + 修改密码 -->
    <div class="form-grid">
      <BasicInfoForm
        :form="profileForm"
        :rules="profileRules"
        :saving="profileSaving"
        @save="handleSaveProfile"
        @reset="resetProfileForm"
      />
      <PasswordForm
        :form="pwdForm"
        :rules="pwdRules"
        :saving="pwdSaving"
        @save="handleChangePassword"
        @reset="resetPwdForm"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Calendar, User, WarningFilled } from "@element-plus/icons-vue";
import PageHeader from "@/admin/components/PageHeader.vue";
import { useProfile } from "./useProfile";
import AvatarUpload from "./components/AvatarUpload.vue";
import BasicInfoForm from "./components/BasicInfoForm.vue";
import PasswordForm from "./components/PasswordForm.vue";

const {
  // 资料
  loading,
  profile,
  status,
  statusMeta,
  avatarText,
  avatarUrl,
  formatTime,
  loadProfile,
  // 头像上传
  beforeAvatarUpload,
  handleAvatarUpload,
  // 基本信息
  profileForm,
  profileRules,
  profileSaving,
  handleSaveProfile,
  resetProfileForm,
  // 修改密码
  pwdForm,
  pwdRules,
  pwdSaving,
  handleChangePassword,
  resetPwdForm,
} = useProfile();
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ==================== 顶部用户卡片 ==================== */

.user-card {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 26px 28px;
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  box-shadow: var(--admin-shadow-card);
}

.user-card__glow {
  position: absolute;
  top: -60px;
  right: -40px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.12), transparent 70%);
  pointer-events: none;
}

.user-card__avatar {
  flex-shrink: 0;
  position: relative;
}

.user-card__info {
  flex: 1;
  min-width: 0;
}

.user-card__name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.user-card__name {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

/* 公共 .status-badge 基础样式已由 styles/index.css 提供，这里只保留页面状态配色 */

.status-badge.ok {
  color: #10b981;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

.status-badge.warn {
  color: #f59e0b;
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.status-badge.ban {
  color: #ef4444;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.status-badge.off {
  color: #94a3b8;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.user-card__account,
.user-card__created,
.user-card__ban {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}

.user-card__account .el-icon,
.user-card__created .el-icon,
.user-card__ban .el-icon {
  color: #94a3b8;
}

.user-card__ban {
  margin-top: 10px;
  padding: 6px 10px;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
}

.user-card__ban .el-icon {
  color: #ef4444;
}

.user-card__ban-reason {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card__refresh {
  flex-shrink: 0;
}

/* ==================== 表单卡片 ==================== */

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 20px;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .user-card {
    flex-direction: column;
    text-align: center;
  }

  .user-card__name-row {
    justify-content: center;
  }

  .user-card__account,
  .user-card__created,
  .user-card__ban {
    justify-content: center;
  }

  .user-card__refresh {
    display: none;
  }
}
</style>
