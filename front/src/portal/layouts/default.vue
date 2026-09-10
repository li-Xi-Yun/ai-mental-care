<template>
  <div class="portal-layout">
    <header class="portal-header" :style="{ height: PORTAL_HEADER_HEIGHT + 'px' }">
      <div class="header-left">
        <router-link to="/" class="header-logo">{{ PLATFORM_NAME }}</router-link>
        <nav class="header-nav">
          <router-link
            v-for="item in PORTAL_NAV_ITEMS"
            :key="item.route"
            :to="item.route"
            class="nav-item"
            :class="{ active: route.path === item.route }"
          >{{ item.label }}</router-link>
        </nav>
      </div>
      <div class="header-right">
        <template v-if="userStore.token">
          <el-dropdown trigger="click">
            <div class="user-avatar">
              <el-avatar :size="32" class="avatar-icon">{{ avatarText }}</el-avatar>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" text @click="userStore.openLoginDialog('login')">登录</el-button>
          <el-button type="primary" @click="userStore.openLoginDialog('register')">注册</el-button>
        </template>
      </div>
    </header>
    <main class="portal-main">
      <router-view />
    </main>
    <LoginDialog />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "@/portal/stores/user";
import LoginDialog from "@/portal/components/LoginDialog.vue";
import { PLATFORM_NAME, PORTAL_HEADER_HEIGHT, PORTAL_NAV_ITEMS } from "@/shared/api/config";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const avatarText = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || "U";
  return name.charAt(0).toUpperCase();
});

function handleLogout() {
  userStore.clearUser();
  router.push("/");
}
</script>

<style scoped>
.portal-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f7;
}

.portal-header {
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.header-logo {
  font-size: 18px;
  font-weight: 700;
  color: #6c63ff;
  text-decoration: none;
  letter-spacing: -0.5px;
}

.header-nav {
  display: flex;
  gap: 4px;
}

.nav-item {
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 14px;
  color: #555;
  text-decoration: none;
  transition: all 0.2s;
}

.nav-item:hover {
  color: #6c63ff;
  background: #f0eeff;
}

.nav-item.active {
  color: #6c63ff;
  background: #f0eeff;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  cursor: pointer;
}

.avatar-icon {
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  color: #fff;
  font-size: 14px;
}

.portal-main {
  flex: 1;
}
</style>