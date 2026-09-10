<template>
  <div class="portal-login-redirect">
    <p>登录功能已改为弹窗形式，请点击导航栏"登录"按钮。</p!>
    <el-button type="primary" @click="goHome">返回首页</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { useUserStore } from "@/portal/stores/user";

const router = useRouter();
const userStore = useUserStore();

function goHome() {
  userStore.openLoginDialog("login");
  router.push("/");
}
</script>

<style scoped>
.portal-login-redirect {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 16px;
  color: #999;
}
</style>