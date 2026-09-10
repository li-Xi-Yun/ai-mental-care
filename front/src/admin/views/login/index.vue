<template>
  <div class="admin-login-page">
    <div class="login-card">
      <div class="login-title">{{ ADMIN_PLATFORM_TITLE }}</div>
      <div class="login-subtitle">{{ ADMIN_PLATFORM_NAME }}</div>
      <el-form :model="loginForm" label-position="top" class="login-form">
        <el-form-item label="管理员账号">
          <el-input v-model="loginForm.username" placeholder="请输入管理员账号" prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="danger" class="login-btn" @click="handleLogin">登 录</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ADMIN_PLATFORM_NAME, ADMIN_PLATFORM_TITLE } from "@/shared/api/config";

const router = useRouter();

const loginForm = ref({
  username: "",
  password: "",
});

function handleLogin() {
  console.log("管理员登录", loginForm.value);
  router.push("/admin/user");
}
</script>

<style scoped>
.admin-login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff6b6b, #c44569);
}

.login-card {
  background: #fff;
  border-radius: 16px;
  padding: 36px 32px;
  width: 380px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.login-title {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: #ff6b6b;
  margin-bottom: 4px;
}

.login-subtitle {
  text-align: center;
  font-size: 12px;
  color: #999;
  margin-bottom: 24px;
}

.login-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: #666;
}

.login-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
  border-radius: 8px;
  margin-top: 8px;
}
</style>