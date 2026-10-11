<template>
  <div class="scale-detail-page">
    <!-- Loading -->
    <div v-if="loading" class="detail-loading">
      <div class="sk-hero"></div>
      <div class="sk-bar"></div>
      <div class="sk-block"></div>
    </div>

    <!-- Error -->
    <div v-else-if="loadError" class="empty-wrap">
      <el-empty description="量表详情加载失败">
        <el-button type="primary" @click="fetchDetail">重新加载</el-button>
      </el-empty>
    </div>

    <!-- Not Found -->
    <el-empty v-else-if="!detail" class="empty-wrap" description="未找到该量表信息" />

    <template v-else>
      <!-- Hero -->
      <header class="detail-hero" :style="{ background: tintFor(detail.scaleCategoryId ?? 0, detail.id).soft }">
        <div class="hero-icon" :style="{ background: tintFor(detail.scaleCategoryId ?? 0, detail.id).gradient }">
          <span class="hero-imoji">{{ iconFor(detail) }}</span>
        </div>
        <div class="hero-copy">
          <h1 class="hero-name">{{ detail.scaleName }}</h1>
          <p class="hero-type">{{ detail.versionNo ? `v${detail.versionNo}` : "" }}</p>
        </div>
        <div class="hero-actions">
          <el-button
            class="records-btn"
            size="large"
            round
            @click="router.push('/scale/records')"
          >
            我的测评记录
          </el-button>
          <el-button
            type="primary"
            class="hero-btn"
            :loading="starting"
            :disabled="starting"
            size="large"
            @click="handleStart"
          >
            {{ starting ? "正在启动..." : "开始测评" }}
          </el-button>
        </div>
      </header>

      <div v-if="startErrorReason" class="start-error">{{ startErrorReason }}</div>

      <main class="detail-main">
        <!-- Meta Bar -->
        <div class="meta-bar">
          <div class="meta-item"><el-icon :size="16"><Clock /></el-icon> {{ detail.estimatedMinutes ?? "--" }} 分钟</div>
          <div class="meta-item"><el-icon :size="16"><EditPen /></el-icon> {{ detail.questionCount ?? 0 }} 题</div>
          <div class="meta-item" v-if="questionTypeText">
            <el-icon :size="16"><List /></el-icon> {{ questionTypeText }}
          </div>
        </div>

        <!-- Description -->
        <section class="section">
          <h3 class="section-title">量表简介</h3>
          <p class="section-text">{{ detail.description || "暂无介绍" }}</p>
        </section>

        <!-- Guidance -->
        <section class="section">
          <h3 class="section-title">指导语</h3>
          <p class="section-text">请根据您的真实感受作答，答案无对错之分。</p>
        </section>

        <!-- Latest Result -->
        <section class="section" v-if="latestResult">
          <h3 class="section-title">最近一次测评结果</h3>
          <div class="result-box">
            <div class="result-score">
              <div class="score-value">{{ latestResult.totalScore ?? "--" }}</div>
              <div class="score-label">总得分</div>
            </div>
            <div class="result-meta">
              <span>测评时间：{{ formatDate(latestResult.createdTime) }}</span>
            </div>
            <div v-if="latestResult.resultText" class="result-interp">
              {{ latestResult.resultText }}
            </div>
          </div>
        </section>

        <!-- Disclaimer -->
        <section class="section disclaimer">
          <el-alert
            type="info"
            :closable="false"
            title="免责声明"
            description="本量表仅作为自我认知和心理状态评估的辅助工具，测评结果不能替代专业的心理诊断或治疗。如有需要，请及时寻求专业心理医生或心理咨询师的帮助。"
          />
        </section>
      </main>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Clock, EditPen, List } from "@element-plus/icons-vue";
import { useScaleDetail } from "@/portal/composables/useScaleDetail";
import { iconFor, tintFor, buildQuestionTypeText } from "./visual";

const router = useRouter();
const route = useRoute();
const scaleId = Number(route.params.scaleId);

const {
  detail,
  loading,
  loadError,
  starting,
  startErrorReason,
  latestResult,
  fetchDetail,
  fetchLatestResult,
  handleStart,
  formatDate,
} = useScaleDetail(scaleId);

const questionTypeText = computed(() => buildQuestionTypeText(detail.value ?? undefined));

onMounted(() => {
  void fetchDetail();
  void fetchLatestResult();
});
</script>

<style scoped>
.scale-detail-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 20px 60px;
}

.detail-loading {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sk-hero {
  height: 140px;
  border-radius: 16px;
  background: linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

.sk-bar {
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

.sk-block {
  height: 160px;
  border-radius: 12px;
  background: linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.empty-wrap {
  margin-top: 80px;
}

.detail-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 24px 28px;
  border-radius: 18px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.hero-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.hero-imoji {
  font-size: 28px;
  line-height: 1;
}

.hero-copy {
  flex: 1;
  min-width: 0;
}

.hero-name {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.hero-type {
  font-size: 13px;
  color: #888;
  margin: 6px 0 0;
}

.hero-btn {
  flex-shrink: 0;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.records-btn {
  font-size: 14px;
}

.start-error {
  margin-bottom: 12px;
  color: #e74c3c;
  font-size: 13px;
}

.detail-main {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.meta-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding: 14px 20px;
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #666;
}

.meta-sep {
  color: #d0d0d0;
  margin: 0 4px;
}

.section {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 12px;
  padding: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 12px;
}

.section-text {
  font-size: 13px;
  color: #666;
  line-height: 1.7;
  margin: 0;
  white-space: pre-wrap;
}

.result-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.score-value {
  font-size: 32px;
  font-weight: 700;
  color: #6c63ff;
}

.score-label {
  font-size: 13px;
  color: #999;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #b0b6c4;
}

.result-interp {
  font-size: 13px;
  color: #555;
  line-height: 1.7;
  padding: 12px;
  background: #f9f9fd;
  border-radius: 8px;
}

.disclaimer {
  padding: 0;
  border: none;
}
</style>