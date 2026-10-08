<template>
  <div class="diagnosis-list-page">
    <!-- 面包屑 -->
    <nav class="breadcrumb">
      <router-link to="/diagnosis" class="breadcrumb-link">心理诊断</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">{{ sessionName || "会话诊断" }}</span>
    </nav>

    <!-- 页头 -->
    <header class="page-header">
      <div class="header-copy">
        <div class="header-eyebrow">AI 心理健康 · 情绪诊断</div>
        <h2 class="page-title">{{ sessionName || "会话诊断" }} <span class="title-slash">—</span> 诊断记录</h2>
        <p class="page-subtitle">AI 根据对话生成的诊断书沉淀于此，按轮次由新到旧排列</p>
      </div>
      <div v-if="total > 0" class="header-stats">
        <div class="stat-chip">
          <span class="stat-num">{{ total }}</span>
          <span class="stat-text">份诊断书</span>
        </div>
        <div class="stat-chip">
          <span class="stat-num">{{ latestRoundText }}</span>
          <span class="stat-text">最新轮次</span>
        </div>
      </div>
    </header>

    <!-- 列表主体 -->
    <div class="list-body">
      <!-- 加载骨架 -->
      <div v-if="loading" class="timeline">
        <div v-for="i in 3" :key="i" class="timeline-item">
          <div class="timeline-rail">
            <span class="timeline-dot skeleton-dot"></span>
            <span class="timeline-line"></span>
          </div>
          <div class="timeline-card skeleton-card">
            <div class="sk-line sk-round"></div>
            <div class="sk-line sk-time"></div>
          </div>
        </div>
      </div>

      <!-- 加载失败 -->
      <div v-else-if="loadError" class="empty-wrap">
        <el-empty description="诊断记录加载失败，请稍后重试">
          <el-button type="primary" @click="fetchList">重新加载</el-button>
        </el-empty>
      </div>

      <!-- 空态 -->
      <el-empty
        v-else-if="records.length === 0"
        class="empty-wrap"
        description="该会话暂无诊断记录，快去 AI 对话中聊聊吧"
      >
        <el-button type="primary" @click="goChat">去 AI 对话</el-button>
        <el-button @click="backToDiagnosis">返回诊断列表</el-button>
      </el-empty>

      <!-- 时间线列表 -->
      <div v-else class="timeline">
        <div v-for="(item, index) in records" :key="item.id ?? index" class="timeline-item">
          <div class="timeline-rail">
            <span class="timeline-dot" :class="{ latest: index === 0 }" :style="dotStyle(index)">
              {{ item.roundNum ?? "·" }}
            </span>
            <span v-if="index < records.length - 1" class="timeline-line"></span>
          </div>

          <div class="timeline-card" @click="goDetail(item)">
            <div class="card-head">
              <div class="card-title-row">
                <span class="round-badge" :style="badgeStyle(index)">{{ roundText(item) }}</span>
                <span v-if="index === 0" class="latest-tag">最新</span>
              </div>
              <div class="card-time">
                <el-icon :size="13"><Clock /></el-icon>
                <span>{{ formatDateTime(item.createdTime) }}</span>
              </div>
            </div>
            <div class="card-foot">
              <span v-if="item.updatedTime && item.updatedTime !== item.createdTime" class="updated-time">
                <el-icon :size="12"><Refresh /></el-icon>
                更新于 {{ formatDateTime(item.updatedTime) }}
              </span>
              <span v-else class="updated-time placeholder">创建即生成</span>
              <span class="detail-link">
                查看详情
                <el-icon :size="14"><ArrowRight /></el-icon>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0 && !loading" class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="DEFAULT_PAGE_SIZE"
        :total="total"
        :layout="PAGINATION_LAYOUT"
        background
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowRight, Clock, Refresh } from "@element-plus/icons-vue";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import { dayjs } from "@/shared/utils";
import {
  getEmotionDiagnosisListByConversation,
  type EmotionDiagnosisListVO,
} from "@/portal/api/conversation/emotion-diagnosis";
import { getConversationDetail } from "@/portal/api/conversation/conversation";

const route = useRoute();
const router = useRouter();

const sessionId = String(route.params.sessionId ?? "");

const sessionName = ref("");
const currentPage = ref(1);
const total = ref(0);
const records = ref<EmotionDiagnosisListVO[]>([]);
const loading = ref(false);
const loadError = ref(false);

/* ==================== 数据请求 ==================== */

async function fetchSessionName() {
  if (!sessionId) {
    sessionName.value = "会话";
    return;
  }
  try {
    const res = await getConversationDetail(sessionId);
    sessionName.value = res.data?.data?.name?.trim() || `会话 #${sessionId}`;
  } catch {
    sessionName.value = `会话 #${sessionId}`;
  }
}

async function fetchList() {
  loading.value = true;
  loadError.value = false;
  try {
    const res = await getEmotionDiagnosisListByConversation(sessionId, {
      pageNum: currentPage.value,
      pageSize: DEFAULT_PAGE_SIZE,
    });
    const page = res.data?.data;
    records.value = page?.records ?? [];
    total.value = page?.total ?? 0;
  } catch {
    records.value = [];
    total.value = 0;
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

/* ==================== 交互 ==================== */

function goDetail(item: EmotionDiagnosisListVO) {
  if (item.id == null) return;
  router.push(`/diagnosis/${sessionId}/${item.id}`);
}

function goChat() {
  router.push("/conversation");
}

function backToDiagnosis() {
  router.push("/diagnosis");
}

/* ==================== 派生展示 ==================== */

function formatDateTime(value?: string): string {
  if (!value) return "--";
  const d = dayjs(value);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(value);
}

function roundText(item: EmotionDiagnosisListVO): string {
  return item.roundNum != null ? `第 ${item.roundNum} 轮` : "--";
}

const latestRoundText = computed(() => {
  const first = records.value[0];
  return first?.roundNum != null ? String(first.roundNum) : "--";
});

/* ==================== 视觉着色 ==================== */

interface Tint {
  gradient: string;
  soft: string;
  fg: string;
}

const TINTS: Tint[] = [
  { gradient: "linear-gradient(135deg,#6c63ff,#9a93ff)", soft: "#f0eeff", fg: "#6c63ff" },
  { gradient: "linear-gradient(135deg,#fa8c16,#ffb54d)", soft: "#fff7e6", fg: "#fa8c16" },
  { gradient: "linear-gradient(135deg,#52c41a,#7ed357)", soft: "#f6ffed", fg: "#52c41a" },
  { gradient: "linear-gradient(135deg,#1890ff,#5cb0ff)", soft: "#e6f7ff", fg: "#1890ff" },
];

function tintFor(index: number): Tint {
  return TINTS[index % TINTS.length];
}

function dotStyle(index: number) {
  return { background: tintFor(index).gradient };
}

function badgeStyle(index: number) {
  const t = tintFor(index);
  return { background: t.soft, color: t.fg };
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  void fetchSessionName();
  void fetchList();
});
</script>

<style scoped>
.diagnosis-list-page {
  max-width: 920px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

/* ==================== 面包屑 ==================== */

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #999;
  margin-bottom: 14px;
}

.breadcrumb-link {
  color: #6c63ff;
  text-decoration: none;
  transition: color 0.2s;
}

.breadcrumb-link:hover {
  color: #3f3d9e;
}

.breadcrumb-sep {
  color: #d6d9e4;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

/* ==================== 页头 ==================== */

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.header-eyebrow {
  font-size: 12px;
  font-weight: 600;
  color: #6c63ff;
  letter-spacing: 2px;
  margin-bottom: 8px;
}

.page-title {
  font-size: 24px;
  font-weight: 800;
  color: #1a1a2e;
  letter-spacing: -0.5px;
  margin: 0;
}

.title-slash {
  color: #c5bdff;
  font-weight: 400;
}

.page-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: #9aa0b5;
}

.header-stats {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.stat-chip {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 12px;
  padding: 8px 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 72px;
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #6c63ff;
  line-height: 1.2;
}

.stat-text {
  font-size: 11px;
  color: #9aa0b5;
  margin-top: 2px;
}

/* ==================== 列表主体 ==================== */

.list-body {
  min-height: 260px;
}

.empty-wrap {
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  padding: 48px 0;
}

/* ==================== 时间线 ==================== */

.timeline {
  display: flex;
  flex-direction: column;
}

.timeline-item {
  display: flex;
  gap: 16px;
}

.timeline-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 42px;
  flex-shrink: 0;
}

.timeline-dot {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  box-shadow: 0 4px 10px rgba(108, 99, 255, 0.28);
  z-index: 1;
  flex-shrink: 0;
}

.timeline-dot.latest {
  box-shadow: 0 0 0 5px #f0eeff, 0 4px 12px rgba(108, 99, 255, 0.35);
}

.timeline-line {
  flex: 1;
  width: 2px;
  min-height: 26px;
  background: linear-gradient(to bottom, #e3e0ff, #f3f2ff);
  margin-top: 4px;
}

/* ==================== 诊断卡片 ==================== */

.timeline-card {
  flex: 1;
  min-width: 0;
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  padding: 14px 18px;
  margin-bottom: 18px;
  cursor: pointer;
  transition: box-shadow 0.25s, transform 0.25s, border-color 0.25s;
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px rgba(108, 99, 255, 0.12);
  border-color: #d9d2ff;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.round-badge {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
  padding: 3px 10px;
  border-radius: 8px;
}

.latest-tag {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #6c63ff, #9a93ff);
  padding: 2px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}

.card-time {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #9aa0b5;
  flex-shrink: 0;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #eef0f5;
}

.updated-time {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #b0b6c4;
}

.updated-time.placeholder {
  color: #d6d9e4;
}

.detail-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #6c63ff;
  flex-shrink: 0;
  transition: gap 0.2s;
}

.timeline-card:hover .detail-link {
  gap: 7px;
}

/* ==================== 骨架 ==================== */

.skeleton-dot {
  background: linear-gradient(90deg, #f0f0f6 25%, #fafafd 50%, #f0f0f6 75%);
  background-size: 200% 100%;
  animation: sk 1.2s infinite;
}

.skeleton-card {
  cursor: default;
}

.skeleton-card:hover {
  transform: none;
  box-shadow: none;
  border-color: #ececf2;
}

.sk-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f6 25%, #fafafd 50%, #f0f0f6 75%);
  background-size: 200% 100%;
  animation: sk 1.2s infinite;
}

.sk-round {
  width: 40%;
  margin-bottom: 10px;
}

.sk-time {
  width: 60%;
}

@keyframes sk {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

/* ==================== 分页 ==================== */

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

/* ==================== 响应式 ==================== */

@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .timeline-item {
    gap: 12px;
  }

  .timeline-rail {
    width: 36px;
  }

  .card-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .card-foot {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
