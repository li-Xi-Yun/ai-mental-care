<template>
  <el-dialog
    v-model="userStore.loginDialogVisible"
    width="400px"
    :show-close="true"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    class="login-dialog"
    @closed="onClosed"
  >
    <div class="login-header">
      <div class="login-logo">AI Mental Care</div>
      <div class="login-subtitle">AI 心理健康关怀平台</div>
    </div>

    <el-tabs v-model="activeTab" class="login-tabs">
      <el-tab-pane label="登录" name="login">
        <el-form :model="loginForm" label-position="top" class="login-form">
          <el-form-item label="用户名">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
          </el-form-item>
          <div class="login-options">
            <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
            <el-link type="primary" :underline="false">忘记密码？</el-link>
          </div>
          <el-button type="primary" class="login-btn" @click="handleLogin">登 录</el-button>
          <div class="login-other">
            <span class="login-other-label">其他方式：</span>
            <el-link type="primary" :underline="false">微信</el-link>
            <span class="login-other-sep">|</span>
            <el-link type="primary" :underline="false">手机验证码</el-link>
          </div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="注册" name="register">
        <el-form :model="registerForm" label-position="top" class="login-form">
          <el-form-item label="用户名">
            <el-input v-model="registerForm.username" placeholder="请输入用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="registerForm.phone" placeholder="请输入手机号" prefix-icon="Phone" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="registerForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" prefix-icon="Lock" show-password />
          </el-form-item>
          <el-button type="primary" class="login-btn" @click="handleRegister">注 册</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { useUserStore } from "@/portal/stores/user";

const userStore = useUserStore();

const activeTab = ref<"login" | "register">(userStore.loginDialogTab);

watch(
  () => userStore.loginDialogTab,
  (val) => {
    activeTab.value = val;
  },
);

const loginForm = ref({
  username: "",
  password: "",
  remember: true,
});

const registerForm = ref({
  username: "",
  phone: "",
  password: "",
  confirmPassword: "",
});

function handleLogin() {
  console.log("登录", loginForm.value);
}

function handleRegister() {
  console.log("注册", registerForm.value);
}

function onClosed() {
  loginForm.value = { username: "", password: "", remember: true };
  registerForm.value = { username: "", phone: "", password: "", confirmPassword: "" };
}
</script>

<style scoped>
.login-dialog :deep(.el-dialog) {
  border-radius: 16px;
}

.login-dialog :deep(.el-dialog__header) {
  display: none;
}

.login-dialog :deep(.el-dialog__body) {
  padding: 24px 28px;
}

.login-header {
  text-align: center;
  margin-bottom: 8px;
}

.login-logo {
  font-size: 22px;
  font-weight: 700;
  color: #6c63ff;
}

.login-subtitle {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.login-tabs :deep(.el-tabs__nav) {
  width: 100%;
}

.login-tabs :deep(.el-tabs__item) {
  width: 50%;
  text-align: center;
  font-size: 14px;
}

.login-form {
  margin-top: 8px;
}

.login-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: #666;
}

.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 12px;
}

.login-btn {
  width: 100%;
  height: 40px;
  font-size: 15px;
  border-radius: 8px;
}

.login-other {
  text-align: center;
  margin-top: 16px;
  font-size: 12px;
  color: #999;
}

.login-other-label {
  margin-right: 4px;
}

.login-other-sep {
  margin: 0 6px;
  color: #d9d9d9;
}
</style>