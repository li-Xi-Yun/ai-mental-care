<template>
  <div class="profile-page">
    <div class="profile-sidebar">
      <div class="sidebar-avatar">
        <el-avatar :size="64" class="avatar-icon">{{ avatarText }}</el-avatar>
      </div>
      <div class="sidebar-name">张小明</div>
      <div class="sidebar-email">xiaoming@example.com</div>
      <div class="sidebar-menu">
        <div
          v-for="item in menuItems"
          :key="item.key"
          class="menu-item"
          :class="{ active: activeMenu === item.key }"
          @click="activeMenu = item.key"
        >
          {{ item.label }}
        </div>
      </div>
    </div>

    <div class="profile-content">
      <div v-if="activeMenu === 'info'" class="content-card">
        <div class="content-title">基本信息</div>
        <el-form :model="profileForm" label-width="80px" label-position="left">
          <el-form-item label="用户名">
            <el-input v-model="profileForm.username" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="profileForm.phone" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profileForm.email" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSave">保存修改</el-button>
            <el-button @click="handleCancel">取消</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-if="activeMenu === 'password'" class="content-card">
        <div class="content-title">修改密码</div>
        <el-form :model="passwordForm" label-width="80px" label-position="left">
          <el-form-item label="当前密码">
            <el-input v-model="passwordForm.current" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPwd" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="passwordForm.confirm" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleChangePassword">确认修改</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-if="activeMenu === 'report'" class="content-card">
        <div class="content-title">健康报告</div>
        <div class="report-placeholder">
          <el-empty description="暂无健康报告，完成更多测评后将生成综合报告" />
        </div>
      </div>

      <div v-if="activeMenu === 'settings'" class="content-card">
        <div class="content-title">偏好设置</div>
        <el-form label-width="100px" label-position="left">
          <el-form-item label="消息通知">
            <el-switch v-model="settings.notification" />
          </el-form-item>
          <el-form-item label="诊断提醒">
            <el-switch v-model="settings.diagnosisReminder" />
          </el-form-item>
          <el-form-item label="语言">
            <el-select v-model="settings.language" style="width: 200px">
              <el-option label="简体中文" value="zh-CN" />
              <el-option label="English" value="en" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { PROFILE_MENU_ITEMS } from "@/shared/api/config";

const activeMenu = ref("info");
const avatarText = "张";

const menuItems = PROFILE_MENU_ITEMS;

const profileForm = reactive({
  username: "zhangxiaoming",
  nickname: "张小明",
  phone: "138****5678",
  email: "xiaoming@example.com",
});

const passwordForm = reactive({
  current: "",
  newPwd: "",
  confirm: "",
});

const settings = reactive({
  notification: true,
  diagnosisReminder: true,
  language: "zh-CN",
});

function handleSave() {
  console.log("保存修改", profileForm);
}

function handleCancel() {
  console.log("取消");
}

function handleChangePassword() {
  console.log("修改密码");
}
</script>

<style scoped>
.profile-page {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 20px;
}

.profile-sidebar {
  background: #fff;
  border-radius: 10px;
  padding: 24px 16px;
  text-align: center;
  border: 1px solid #f0f0f0;
  align-self: start;
}

.sidebar-avatar {
  margin-bottom: 10px;
}

.avatar-icon {
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  color: #fff;
  font-size: 24px;
}

.sidebar-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.sidebar-email {
  font-size: 12px;
  color: #999;
  margin-bottom: 16px;
}

.sidebar-menu {
  text-align: left;
}

.menu-item {
  padding: 8px 12px;
  font-size: 13px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
  color: #555;
  transition: all 0.2s;
}

.menu-item:hover {
  background: #f8f7ff;
  color: #6c63ff;
}

.menu-item.active {
  background: #f0eeff;
  color: #6c63ff;
  font-weight: 500;
}

.profile-content {
  min-width: 0;
}

.content-card {
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  border: 1px solid #f0f0f0;
}

.content-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.report-placeholder {
  padding: 40px 0;
}
</style>