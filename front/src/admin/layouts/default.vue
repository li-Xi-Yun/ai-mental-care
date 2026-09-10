<template>
  <el-container class="admin-layout">
    <el-aside :width="ADMIN_SIDEBAR_WIDTH + 'px'" class="admin-aside">
      <div class="as-header">
        <div class="as-logo">✏️</div>
        <div class="as-logo-text">{{ PLATFORM_NAME }}</div>
        <div class="as-logo-sub">心理健康管理平台</div>
      </div>
      <div class="as-nav">
        <div
          v-for="item in ADMIN_NAV_ITEMS"
          :key="item.route"
          class="nav-item"
          :class="{ active: route.path === item.route || (item.route === '/admin/user' && route.path === '/admin') }"
          @click="router.push(item.route)"
        >
          <span class="nav-icon">{{ iconMap[item.icon] }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </div>
      </div>
      <div class="as-user">
        <div class="as-user-avatar">👤</div>
        <div class="as-user-info">
          <div class="as-user-name">Admin</div>
          <div class="as-user-role">超级管理员</div>
        </div>
        <div class="as-logout" @click="handleLogout">🚪</div>
      </div>
    </el-aside>
    <el-container>
      <el-header class="admin-header" :style="{ height: ADMIN_HEADER_HEIGHT + 'px' }">
        <span class="header-title">{{ route.meta.title || ADMIN_PLATFORM_TITLE }}</span>
        <div class="header-right">
          <span class="admin-name">管理员：admin</span>
        </div>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { PLATFORM_NAME, ADMIN_PLATFORM_TITLE, ADMIN_SIDEBAR_WIDTH, ADMIN_HEADER_HEIGHT, ADMIN_NAV_ITEMS } from "@/shared/api/config";

const route = useRoute();
const router = useRouter();

const iconMap: Record<string, string> = {
  User: "👥",
  UserFilled: "🛡️",
  Document: "📁",
  DataAnalysis: "📊",
  FirstAidKit: "🏥",
  SetUp: "🤖",
  MagicStick: "⚡",
};

function handleLogout() {
  router.push("/admin/login");
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-aside {
  background: linear-gradient(180deg, #1a1a2e 0%, #2d1b69 50%, #1a1a2e 100%);
  border-right: none;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.as-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  text-align: center;
}

.as-logo {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin: 0 auto 10px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.as-logo-text {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}

.as-logo-sub {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 2px;
}

.as-nav {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

.nav-item {
  padding: 12px 16px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  border-radius: 8px;
  margin-bottom: 4px;
  color: rgba(255, 255, 255, 0.75);
  font-weight: 500;
  transition: all 0.3s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  transform: translateX(2px);
}

.nav-item.active {
  background: linear-gradient(90deg, rgba(255, 107, 107, 0.2), rgba(255, 107, 107, 0.05));
  color: #ff6b6b;
  border-left: 3px solid #ff6b6b;
}

.nav-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.nav-label {
  flex: 1;
}

.as-user {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  gap: 12px;
}

.as-user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.as-user-info {
  flex: 1;
  min-width: 0;
}

.as-user-name {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.as-user-role {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}

.as-logout {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.6);
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.as-logout:hover {
  background: rgba(255, 77, 79, 0.2);
  color: #ff4d4f;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
  padding: 0 20px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-name {
  font-size: 13px;
  color: #999;
}

.admin-main {
  background: #f5f5f7;
  padding: 20px;
}
</style>