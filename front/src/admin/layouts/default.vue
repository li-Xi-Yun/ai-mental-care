<template>
  <el-container class="admin-layout">
    <el-aside
      :width="(sidebarCollapsed ? 64 : ADMIN_SIDEBAR_WIDTH) + 'px'"
      class="admin-aside"
      :class="{ collapsed: sidebarCollapsed }"
    >
      <div class="as-stars" aria-hidden="true">
        <span v-for="star in stars" :key="star.id" class="as-star" :style="star.style" />
      </div>
      <div class="as-header">
        <div class="as-logo">
          <el-icon :size="16"><MagicStick /></el-icon>
        </div>
        <div class="as-logo-textwrap">
          <div class="as-logo-text">{{ PLATFORM_NAME }}</div>
          <div class="as-logo-sub">心理健康管理平台</div>
        </div>
      </div>
      <div class="as-nav">
        <!-- 分组导航（可折叠） -->
        <div
          v-for="group in groups"
          :key="group.key"
          class="nav-group"
          :class="{ 'is-collapsed': !isGroupExpanded(group.key), 'has-active': activeGroupKey === group.key }"
        >
          <div
            class="nav-group-toggle"
            role="button"
            tabindex="0"
            :aria-expanded="isGroupExpanded(group.key)"
            :title="sidebarCollapsed ? group.label : undefined"
            @click="handleGroupClick(group)"
            @keydown.enter.prevent="handleGroupClick(group)"
            @keydown.space.prevent="handleGroupClick(group)"
          >
            <span class="nav-icon"><el-icon :size="18"><component :is="group.icon" /></el-icon></span>
            <span class="nav-group-label">{{ group.label }}</span>
            <el-icon class="nav-group-arrow" :size="14"><ArrowRight /></el-icon>
          </div>
          <div class="nav-sub">
            <div class="nav-sub-inner">
              <div
                v-for="leaf in group.children"
                :key="leaf.key"
                class="nav-item"
                :class="{ active: activeLeafKey === leaf.key }"
                :title="sidebarCollapsed ? leaf.label : undefined"
                @click="router.push(leaf.route)"
              >
                <span class="nav-icon"><el-icon :size="16"><component :is="leaf.icon" /></el-icon></span>
                <span class="nav-label">{{ leaf.label }}</span>
                <el-icon v-if="activeLeafKey === leaf.key" class="nav-chevron" :size="14"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </div>
        <!-- 平铺项（个人资料） -->
        <div
          v-for="leaf in leaves"
          :key="leaf.key"
          class="nav-item"
          :class="{ active: activeLeafKey === leaf.key }"
          :title="sidebarCollapsed ? leaf.label : undefined"
          @click="router.push(leaf.route)"
        >
          <span class="nav-icon"><el-icon :size="18"><component :is="leaf.icon" /></el-icon></span>
          <span class="nav-label">{{ leaf.label }}</span>
          <el-icon v-if="activeLeafKey === leaf.key" class="nav-chevron" :size="14"><ArrowRight /></el-icon>
        </div>
      </div>
      <div class="as-user">
        <div
          class="as-user-profile"
          role="button"
          tabindex="0"
          title="个人资料"
          @click="router.push('/admin/profile')"
          @keydown.enter.prevent="router.push('/admin/profile')"
        >
          <div class="as-user-avatar">👤</div>
          <div class="as-user-info">
            <div class="as-user-name">{{ displayName }}</div>
            <div class="as-user-role">超级管理员</div>
          </div>
        </div>
        <div class="as-logout" title="退出登录" @click.stop="handleLogout">
          <el-icon :size="15"><SwitchButton /></el-icon>
        </div>
      </div>
    </el-aside>
    <el-container class="admin-body">
      <el-header class="admin-header" :style="{ height: ADMIN_HEADER_HEIGHT + 'px' }">
        <div class="header-left">
          <button type="button" class="header-toggle" :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'" @click="toggleSidebar">
            <el-icon :size="16"><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
          </button>
          <nav class="header-breadcrumb" aria-label="面包屑">
            <template v-for="(crumb, index) in breadcrumbs" :key="crumb.key">
              <span v-if="index > 0" class="header-breadcrumb-sep" aria-hidden="true">›</span>
              <router-link v-if="!crumb.current && crumb.route" class="header-breadcrumb-link" :to="crumb.route">
                {{ crumb.label }}
              </router-link>
              <span v-else-if="crumb.current" class="header-title">{{ crumb.label }}</span>
              <span v-else class="header-breadcrumb-text">{{ crumb.label }}</span>
            </template>
          </nav>
        </div>
        <div class="header-right">
          <span class="admin-name">管理员：{{ displayName }}</span>
        </div>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { ArrowRight, Expand, Fold, MagicStick, SwitchButton } from "@element-plus/icons-vue";
import { PLATFORM_NAME, ADMIN_SIDEBAR_WIDTH, ADMIN_HEADER_HEIGHT } from "@/shared/api/config";
import type { AdminNavGroup } from "@/shared/api/config";
import { useAdminNav } from "@/admin/composables/useAdminNav";
import { useAdminStore } from "@/admin/stores/admin";
import { clearTokenInfo, clearAllTokenInfo } from "@/shared/api/auth";
import { logout } from "@/admin/api/admin/account";
import "@/admin/styles/index.css";

const router = useRouter();
const adminStore = useAdminStore();

/** 侧栏星空：70 颗随机星点（对齐 Blog-Front StarField，仅在初始化时生成一次） */
const stars = Array.from({ length: 70 }, (_, i) => {
  const rand = Math.random();
  const size = rand < 0.12 ? 2.5 : rand < 0.5 ? 1.5 : 1;
  return {
    id: i,
    style: {
      left: `${(Math.random() * 100).toFixed(2)}%`,
      top: `${(Math.random() * 100).toFixed(2)}%`,
      width: `${size}px`,
      height: `${size}px`,
      opacity: (0.35 + Math.random() * 0.6).toFixed(2),
      animationDelay: `${(Math.random() * 6).toFixed(2)}s`,
      animationDuration: `${(2.4 + Math.random() * 4).toFixed(2)}s`,
    } as Record<string, string>,
  };
});

const { groups, leaves, activeLeafKey, activeGroupKey, isGroupExpanded, toggleGroup, expandGroup, breadcrumbs } =
  useAdminNav();

const sidebarCollapsed = ref(false);
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};

/** 收起态点击分组：先展开侧栏，同时展开该分组；展开态点击则为折叠/展开切换 */
function handleGroupClick(group: AdminNavGroup) {
  if (sidebarCollapsed.value) {
    sidebarCollapsed.value = false;
    expandGroup(group.key);
    return;
  }
  toggleGroup(group.key);
}

/** 管理员展示名：username → loginAccount → 'admin' 三级兜底 */
const displayName = computed(() => {
  const info = adminStore.adminInfo ?? {};
  const name = typeof info.username === "string" ? info.username.trim() : "";
  const account = typeof info.loginAccount === "string" ? info.loginAccount.trim() : "";
  return name || account || "admin";
});

async function handleLogout() {
  try {
    // 先调用后端登出接口（清理 Redis 中的登录态），再清理本地凭证
    await logout();
  } catch {
    // 后端登出失败不阻断本地退出（本地凭证仍会被清理）
  } finally {
    adminStore.clearAdmin();
    clearTokenInfo("admin");
    clearAllTokenInfo();
    router.push("/admin/login");
  }
}
</script>
