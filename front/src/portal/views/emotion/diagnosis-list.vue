<template>
  <div class="diagnosis-list-page">
    <header class="page-header">
      <div class="header-bar">
        <el-button text class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
      </div>
      <h2 class="page-title">诊断记录列表</h2>
      <p class="page-sub">会话 {{ conversationName || `#${conversationId}` }} 的全部历史诊断书</p>
    </header>

    <div v-if="loading" class="list-loading">
      <div v-for="i in 3" :key="i" class="sk-row"></div>
    </div>

    <el-empty v-else-if="loadError" class="empty-wrap" description="诊断记录加载失败，请稍后重试">
      <el-button type="primary" @click="fetchList">重新加载</el-button>
    </el-empty>

    <el-empty v-else-if="records.length === 0" class="empty-wrap" description="该会话暂无诊断书" />

    <template v-else>
      <div class="record-list">
        <div
          v-for="record in records"
          :key="record.id"
          class="record-card"
          @click="goDetail(record.id)"
        >
          <span class="record-strip"></span>
          <div class="record-main">
            <div class="record-title-row">
              <span class="record-round">{{ record.roundNum ? `第${record.roundNum}轮诊断` : "诊断书" }}</span>
              <el-tag :type="riskTagType(record.emotionRisk)" size="small" effect="light">
                {{ riskLabel(record.emotionRisk) }}
              </el-tag>
            </div>
            <div class="record-meta">
              <span>创建 {{ formatDateTime(record.createdTime) }}</span>
              <span class="dot">·</span>
              <span>更新 {{ formatDateTime(record.updatedTime) }}</span>
            </div>
            <div v-if="record.coreEmotionLabel" class="record-brief">
              核心情绪：{{ record.coreEmotionLabel }}
            </div>
          </div>
          <el-icon class="record-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
      <div v-if="total > pageSize" class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="fetchList"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, ArrowRight } from "@element-plus/icons-vue";
import { getEmotionDiagnosisListByConversation } from "@/portal/api/conversation/emotion-diagnosis";
import type { EmotionDiagnosisListVO } from "@/portal/api/conversation/emotion-diagnosis";
import { dayjs } from "@/shared/utils";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import { EMOTION_RISK_META } from "@/portal/composables/useDiagnosisFormatters";

const route = useRoute();
const router = useRouter();
// 路由 /diagnosis/:sessionId；会话ID为 19 位雪花ID，按字符串直传避免 Number() 丢精度
const conversationId = String(route.params.sessionId ?? "");
const conversationName = ref(route.query.name ?? "");

const records = ref<EmotionDiagnosisListVO[]>([]);
const loading = ref(false);
const loadError = ref(false);
const pageNum = ref(1);
const pageSize = DEFAULT_PAGE_SIZE;
const total = ref(0);

function formatDateTime(v?: string): string {
  if (!v) return "--";
  const d = dayjs(v);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(v);
}

function riskTagType(level?: number): "danger" | "warning" | "info" {
  if (level == null) return "info";
  if (level >= 4) return "danger";
  if (level >= 2) return "warning";
  return "info";
}

function riskLabel(level?: number): string {
  if (level == null) return "暂无";
  const meta = EMOTION_RISK_META[level];
  return meta?.text ?? `等级${level}`;
}

async function fetchList() {
  loading.value = true;
  loadError.value = false;
  try {
    const res = await getEmotionDiagnosisListByConversation(conversationId, {
      pageNum: pageNum.value,
      pageSize: pageSize,
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

function goDetail(recordId?: number) {
  if (recordId == null) return;
  router.push(`/diagnosis/${conversationId}/${recordId}`);
}

function goBack() {
  router.back();
}

onMounted(() => {
  void fetchList();
});
</script>

<style scoped>
.diagnosis-list-page {
  max-width: 760px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.page-header {
  margin-bottom: 24px;
}

.header-bar {
  margin-bottom: 12px;
}

.back-btn {
  color: #6c63ff;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.page-sub {
  font-size: 13px;
  color: #888;
  margin: 6px 0 0;
}

.list-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sk-row {
  height: 80px;
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
  margin-top: 60px;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-card {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.record-card:hover {
  box-shadow: 0 4px 16px rgba(26, 26, 46, 0.06);
}

.record-strip {
  display: block;
  width: 4px;
  height: 72px;
  background: linear-gradient(180deg, #6c63ff, #a29bfe);
  flex-shrink: 0;
}

.record-main {
  flex: 1;
  padding: 14px 16px;
  min-width: 0;
}

.record-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.record-round {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.record-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.dot {
  color: #d0d0d0;
}

.record-brief {
  font-size: 12px;
  color: #6c63ff;
  margin-top: 4px;
}

.record-arrow {
  color: #ccc;
  margin-right: 16px;
  flex-shrink: 0;
}

.pagination-bar {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>