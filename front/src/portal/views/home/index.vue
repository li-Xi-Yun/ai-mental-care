<template>
  <div class="home-page">
    <!-- ============ Hero ============ -->
    <section class="hero" aria-labelledby="hero-title">
      <div class="hero-orb hero-orb-a" aria-hidden="true"></div>
      <div class="hero-orb hero-orb-b" aria-hidden="true"></div>

      <div class="hero-content">
        <span class="hero-badge">✦ AI 驱动 · 心理健康</span>
        <h1 id="hero-title" class="hero-title">
          <span class="hero-title-gradient">{{ PLATFORM_NAME }}</span>
        </h1>
        <p class="hero-subtitle">{{ PLATFORM_SLOGAN }}</p>
        <p class="hero-supplement">{{ PLATFORM_SUBTITLE }}</p>
      </div>
    </section>

    <!-- ============ 功能卡片 ============ -->
    <section class="features" aria-label="平台服务">
      <div class="feature-grid">
        <article
          v-for="(card, index) in HOME_FEATURE_CARDS"
          :key="card.title"
          class="feature-card"
          :class="`feature-card--${index + 1}`"
        >
          <span class="feature-card-accent" aria-hidden="true"></span>
          <div class="feature-card-icon">{{ card.icon }}</div>
          <h2 class="feature-card-title">{{ card.title }}</h2>
          <p class="feature-card-desc">{{ card.description }}</p>
          <el-button
            type="primary"
            size="small"
            round
            class="feature-card-action"
            @click="handleNavigate(card.route)"
          >
            {{ card.action }}
          </el-button>
        </article>
      </div>
    </section>

    <!-- ============ 平台数据统计 ============ -->
    <section class="stats" aria-label="平台数据">
      <div v-for="stat in HOME_STATS" :key="stat.label" class="stat-item">
        <span class="stat-value">{{ stat.value }}</span>
        <span class="stat-label">{{ stat.label }}</span>
      </div>
    </section>

    <!-- ============ 平台动态 ============ -->
    <section class="announce" aria-labelledby="announce-title">
      <h2 id="announce-title" class="announce-title">
        <span class="announce-title-icon" aria-hidden="true">📢</span>
        平台动态
      </h2>
      <ul class="announce-list">
        <li v-for="(item, index) in HOME_ANNOUNCEMENTS" :key="index" class="announce-item">
          <span class="announce-dot" aria-hidden="true"></span>
          <span class="announce-text">{{ item }}</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import {
  PLATFORM_NAME,
  PLATFORM_SUBTITLE,
  PLATFORM_SLOGAN,
  HOME_FEATURE_CARDS,
  HOME_ANNOUNCEMENTS,
} from "@/shared/api/config";

const router = useRouter();

/** 首页数据统计（无真实接口，纯展示） */
const HOME_STATS: Array<{ value: string; label: string }> = [
  { value: "10+", label: "专业量表" },
  { value: "24h", label: "全天候陪伴" },
  { value: "100%", label: "隐私保障" },
  { value: "AI", label: "智能深度分析" },
];

type FeatureRoute = (typeof HOME_FEATURE_CARDS)[number]["route"];

function handleNavigate(route: FeatureRoute) {
  // 功能卡片跳转：无需等待导航 Promise
  void router.push(route);
}
</script>

<style scoped>
/* ============ 页面容器 ============ */
.home-page {
  max-width: 1120px;
  margin: 0 auto;
  padding: 40px 24px 88px;
  background: linear-gradient(180deg, #f9f8ff 0%, #f5f5f7 60%, #f7f6fc 100%);
  min-height: 100%;
}

/* ============ Hero ============ */
.hero {
  position: relative;
  text-align: center;
  padding: 56px 16px 40px;
  overflow: hidden;
}

.hero-orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(56px);
}

.hero-orb-a {
  width: 420px;
  height: 420px;
  left: -140px;
  top: -180px;
  background: rgba(108, 99, 255, 0.16);
}

.hero-orb-b {
  width: 380px;
  height: 380px;
  right: -140px;
  bottom: -190px;
  background: rgba(78, 205, 196, 0.15);
}

.hero-content {
  position: relative;
  z-index: 1;
  animation: hero-in 0.7s ease both;
}

.hero-badge {
  display: inline-block;
  background: rgba(108, 99, 255, 0.1);
  border: 1px solid rgba(108, 99, 255, 0.18);
  color: #5b53e0;
  font-size: 0.8125rem;
  font-weight: 600;
  padding: 6px 18px;
  border-radius: 999px;
  letter-spacing: 1px;
  margin-bottom: 22px;
}

.hero-title {
  margin-bottom: 18px;
  line-height: 1.15;
  letter-spacing: -1px;
  font-size: clamp(2.125rem, 6vw, 3.25rem);
  font-weight: 800;
  color: #1a1a2e;
}

.hero-title-gradient {
  background: linear-gradient(120deg, #6c63ff 0%, #3f3d9e 48%, #4ecdc4 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-size: 1.0625rem;
  font-weight: 500;
  line-height: 1.7;
  color: #3a3a55;
  max-width: 520px;
  margin: 0 auto 8px;
}

.hero-supplement {
  font-size: 0.875rem;
  line-height: 1.6;
  color: #8a8aa3;
  max-width: 480px;
  margin: 0 auto;
}

@keyframes hero-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ============ 功能卡片 ============ */
.features {
  margin-top: 24px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr));
  gap: 20px;
}

.feature-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  background: #fff;
  border: 1px solid #ecebf6;
  border-radius: 18px;
  padding: 32px 28px 28px;
  box-shadow: 0 2px 10px rgba(30, 27, 90, 0.04);
  overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.feature-card:hover {
  transform: translateY(-6px);
  border-color: rgba(108, 99, 255, 0.35);
  box-shadow: 0 18px 40px rgba(72, 66, 214, 0.12);
}

.feature-card-accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  opacity: 0;
  transform: scaleX(0.4);
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.feature-card--1 .feature-card-accent {
  background: linear-gradient(90deg, #6c63ff, #9e98ff);
}

.feature-card--2 .feature-card-accent {
  background: linear-gradient(90deg, #4ecdc4, #87e8de);
}

.feature-card--3 .feature-card-accent {
  background: linear-gradient(90deg, #ff9f43, #ffd08a);
}

.feature-card:hover .feature-card-accent {
  opacity: 1;
  transform: scaleX(1);
}

.feature-card-icon {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: #f0eeff;
  box-shadow: inset 0 0 0 1px rgba(108, 99, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  margin-bottom: 18px;
  transition: transform 0.25s ease, background 0.25s ease;
}

.feature-card:hover .feature-card-icon {
  background: #e8e6ff;
  transform: translateY(-2px) scale(1.03);
}

.feature-card-title {
  font-size: 1.0625rem;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.feature-card-desc {
  font-size: 0.875rem;
  line-height: 1.7;
  color: #6e6e8f;
  margin-bottom: 20px;
}

.feature-card-action {
  margin-top: auto;
  border-radius: 999px;
  padding-left: 22px;
  padding-right: 22px;
}

/* ============ 平台数据统计 ============ */
.stats {
  margin: 64px 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: #fff;
  border: 1px solid #ecebf6;
  border-radius: 18px;
  padding: 28px 12px;
  box-shadow: 0 2px 10px rgba(30, 27, 90, 0.04);
}

.stat-item {
  text-align: center;
  padding: 4px 8px;
}

.stat-item + .stat-item {
  border-left: 1px solid #f0effa;
}

.stat-value {
  display: block;
  font-size: 1.75rem;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -0.5px;
  color: #1a1a2e;
}

.stat-label {
  display: block;
  margin-top: 6px;
  font-size: 0.8125rem;
  color: #6e6e8f;
}

/* ============ 平台动态 ============ */
.announce {
  max-width: 720px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #ecebf6;
  border-radius: 18px;
  padding: 28px 32px;
  box-shadow: 0 2px 10px rgba(30, 27, 90, 0.04);
}

.announce-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.0625rem;
  font-weight: 700;
  color: #1a1a2e;
}

.announce-title-icon {
  font-size: 1.125rem;
}

.announce-list {
  list-style: none;
  margin-top: 12px;
}

.announce-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  transition: background 0.2s ease;
}

.announce-item:hover {
  background: #f8f7ff;
}

.announce-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #6c63ff;
  margin-top: 7px;
  flex-shrink: 0;
}

.announce-text {
  font-size: 0.875rem;
  line-height: 1.7;
  color: #4b4b63;
}

/* ============ 响应式 ============ */
@media (max-width: 900px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
    gap: 20px 0;
  }

  .stat-item + .stat-item {
    border-left: none;
  }

  .stat-item:nth-child(odd) {
    border-left: none;
  }

  .stat-item:nth-child(even) {
    border-left: 1px solid #f0effa;
  }
}

@media (max-width: 640px) {
  .home-page {
    padding: 24px 16px 64px;
  }

  .hero {
    padding: 40px 8px 32px;
  }

  .feature-card {
    padding: 26px 20px 24px;
  }

  .announce {
    padding: 22px 20px;
  }

  .stats {
    margin: 48px 0;
  }

  .stat-item:nth-child(even) {
    border-left: none;
  }

  .stat-item:nth-child(3) {
    border-top: 1px solid #f0effa;
  }

  .stat-item:nth-child(4) {
    border-top: 1px solid #f0effa;
  }
}

/* ============ 无障碍：尊重弱动效偏好 ============ */
@media (prefers-reduced-motion: reduce) {
  .hero-content {
    animation: none;
  }

  .feature-card,
  .feature-card-accent,
  .feature-card-icon,
  .announce-item {
    transition: none;
  }

  .feature-card:hover {
    transform: none;
  }

  .feature-card:hover .feature-card-icon {
    transform: none;
  }
}
</style>