<template>
  <div class="home-page">
    <div class="hero-section">
      <h1 class="hero-title">{{ PLATFORM_NAME }}</h1>
      <p class="hero-subtitle">{{ PLATFORM_SUBTITLE }} — {{ PLATFORM_SLOGAN }}</p>
    </div>

    <div class="feature-cards">
      <div v-for="card in HOME_FEATURE_CARDS" :key="card.title" class="feature-card">
        <div class="card-icon">{{ card.icon }}</div>
        <div class="card-title">{{ card.title }}</div>
        <div class="card-desc">{{ card.description }}</div>
        <el-button type="primary" size="small" round @click="handleNavigate(card.route)">{{ card.action }}</el-button>
      </div>
    </div>

    <div class="announcement-section">
      <div class="announcement-title">📢 平台公告</div>
      <div class="announcement-list">
        <div v-for="(item, index) in HOME_ANNOUNCEMENTS" :key="index" class="announcement-item">
          • {{ item }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { PLATFORM_NAME, PLATFORM_SUBTITLE, PLATFORM_SLOGAN, HOME_FEATURE_CARDS, HOME_ANNOUNCEMENTS } from "@/shared/api/config";

const router = useRouter();

function handleNavigate(route: string) {
  router.push(route);
}
</script>

<style scoped>
.home-page {
  padding: 40px 24px;
  max-width: 900px;
  margin: 0 auto;
}

.hero-section {
  text-align: center;
  margin-bottom: 40px;
}

.hero-title {
  font-size: 32px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.hero-subtitle {
  font-size: 16px;
  color: #666;
}

.feature-cards {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 36px;
}

.feature-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  width: 220px;
  border: 1px solid #f0f0f0;
  text-align: center;
  transition: box-shadow 0.3s, transform 0.3s;
}

.feature-card:hover {
  box-shadow: 0 6px 20px rgba(108, 99, 255, 0.15);
  transform: translateY(-4px);
}

.card-icon {
  font-size: 36px;
  margin-bottom: 10px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 6px;
}

.card-desc {
  font-size: 12px;
  color: #999;
  margin-bottom: 14px;
  line-height: 1.5;
}

.announcement-section {
  background: #fff;
  border-radius: 10px;
  padding: 20px 24px;
  border: 1px solid #f0f0f0;
  max-width: 600px;
  margin: 0 auto;
}

.announcement-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.announcement-list {
  font-size: 13px;
  color: #666;
  line-height: 2;
}
</style>