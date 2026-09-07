<template>
  <div class="portal-layout">
    <header class="portal-header">
      <nav>
        <router-link to="/">首页</router-link>
        <router-link to="/conversation">AI对话</router-link>
        <router-link to="/emotion">情绪分析</router-link>
        <router-link to="/scale">量表测评</router-link>
        <router-link to="/profile">个人中心</router-link>
      </nav>
    </header>
    <main class="portal-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.portal-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.portal-header {
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #e5e7eb;
}
.portal-header nav {
  display: flex;
  gap: 20px;
}
.portal-main {
  flex: 1;
  padding: 24px;
}
</style>