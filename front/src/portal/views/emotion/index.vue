<template>
  <div class="diagnosis-page">
    <header class="page-header">
      <div class="header-copy">
        <div class="header-eyebrow">AI 心理健康 · 情绪评估</div>
        <h2 class="page-title">心理评估</h2>
        <p class="page-subtitle">AI 根据对话生成的评估报告沉淀于此，持续追踪你的情绪状态变化</p>
      </div>
      <div class="search-row">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索会话名称"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" :icon="Search" class="search-btn" @click="handleSearch">搜索</el-button>
      </div>
    </header>

    <div v-if="loading" class="list-loading">
      <div v-for="i in 3" :key="i" class="sk-card">
        <div class="sk-line sk-title"></div>
        <div class="sk-line sk-meta"></div>
      </div>
    </div>

    <template v-else>
      <el-empty v-if="!userStore.token" class="empty-wrap" description="登录后即可查看评估记录">
        <el-button type="primary" @click="userStore.openLoginDialog('login')">去登录</el-button>
      </el-empty>
      <div v-else-if="loadError" class="empty-wrap">
        <el-empty description="评估列表加载失败，请稍后重试">
          <el-button type="primary" @click="fetchList">重新加载</el-button>
        </el-empty>
      </div>
      <el-empty v-else-if="sessions.length === 0" class="empty-wrap" description="暂未生成评估记录，快去 AI 对话中聊聊吧" />
      <template v-else>
        <div class="session-list">
          <div
            v-for="session in sessions"
            :key="session.conversationId"
            class="session-card"
            :class="{ expanded: expandedConversationId === session.conversationId }"
          >
            <span class="card-strip" :style="{ backgroundImage: tintFor(session.conversationId).gradient }"></span>
            <div class="card-header" @click="toggleExpand(session)">
              <span class="card-icon" :style="{ background: tintFor(session.conversationId).soft, color: tintFor(session.conversationId).fg }">{{ iconFor(session.conversationName) }}</span>
              <div class="card-main">
                <div class="card-title-row">
                  <span class="card-title" :title="session.conversationName">{{ session.conversationName }}</span>
                  <el-tag :type="countTone(session.diagnosisCount).type" size="small" effect="light" round>{{ countTone(session.diagnosisCount).text }}</el-tag>
                </div>
                <div class="card-meta">
                  <span class="meta-item">最新评估 {{ roundText(session.latestRoundNum) }}</span>
                  <span class="meta-dot">·</span>
                  <span class="meta-item">最近 {{ formatDateTime(session.diagnosisUpdatedTime) }}</span>
                </div>
              </div>
              <span class="card-chevron" :class="{ rotated: expandedConversationId === session.conversationId }">
                <el-icon :size="16"><ArrowDown /></el-icon>
              </span>
            </div>
            <transition name="expand">
              <div v-show="expandedConversationId === session.conversationId" class="card-body">
                <div class="body-inner">
                  <div class="info-chips">
                    <div class="info-chip"><span class="chip-label">会话创建</span><span class="chip-value">{{ formatDateTime(session.conversationCreatedTime) }}</span></div>
                    <div class="info-chip"><span class="chip-label">最近评估</span><span class="chip-value">{{ formatDateTime(session.diagnosisUpdatedTime) }}</span></div>
                    <div class="info-chip"><span class="chip-label">最新轮次</span><span class="chip-value">{{ roundText(session.latestRoundNum) }}</span></div>
                    <div class="info-chip"><span class="chip-label">评估次数</span><span class="chip-value">{{ session.diagnosisCount ?? 0 }} 次</span></div>
                  </div>
                  <div class="records-block">
                    <div class="records-head">
                      <span class="records-title">评估记录</span>
                      <el-button text type="primary" size="small" class="records-link" @click="goConversationRecords(session)">
                        查看全部记录 <el-icon><ArrowRight /></el-icon>
                      </el-button>
                    </div>
                    <div v-if="isRecordsLoading(session)" class="records-state"><span class="mini-spinner"></span>正在加载评估记录…</div>
                    <div v-else-if="recordsOf(session).length === 0" class="records-state">该会话暂无评估报告记录</div>
                    <div v-else class="records-list">
                      <div
                        v-for="record in recordsOf(session)"
                        :key="record.id"
                        class="record-row"
                        @click="goDiagnosisDetail(session.conversationId, record.id)"
                      >
                        <span class="record-badge" :style="{ background: tintFor(session.conversationId).soft, color: tintFor(session.conversationId).fg }">{{ record.roundNum ? `第${record.roundNum}轮` : "评估报告" }}</span>
                        <span class="record-time">{{ formatDateTime(record.createdTime) }}</span>
                        <span class="record-more">查看详情 <el-icon :size="13"><ArrowRight /></el-icon></span>
                      </div>
                    </div>
                  </div>
                  <button type="button" class="view-all-btn" @click="goConversationRecords(session)">
                    查看评估记录 <el-icon class="btn-arrow"><ArrowRight /></el-icon>
                  </button>
                </div>
              </div>
            </transition>
          </div>
        </div>
        <div v-if="total > 0" class="pagination-bar">
          <el-pagination
            v-model:current-page="pageNum"
            :page-size="PAGE_SIZE"
            :total="total"
            :layout="PAGINATION_LAYOUT"
            background
            @current-change="fetchList"
          />
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/portal/stores/user";
import { ArrowDown, ArrowRight, Search } from "@element-plus/icons-vue";
import { dayjs } from "@/shared/utils";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import { useDiagnosisList } from "@/portal/composables/useDiagnosisList";
import type { DiagnosisConversationVO } from "@/portal/api/conversation/emotion-diagnosis";

const PAGE_SIZE = DEFAULT_PAGE_SIZE;
const router = useRouter();
const userStore = useUserStore();

const {
  keyword,
  pageNum,
  total,
  sessions,
  loading,
  loadError,
  expandedConversationId,
  fetchList,
  toggleExpand,
  isRecordsLoading,
  recordsOf,
  handleSearch,
} = useDiagnosisList();

function formatDateTime(v?: string): string {
  if (!v) return "--";
  const d = dayjs(v);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(v);
}

function roundText(v?: number): string {
  return v != null ? `第${v}轮` : "--";
}

function iconFor(name?: string): string {
  return name?.charAt(0) ?? "💬";
}

const TINT_PALETTE = [
  { gradient: "linear-gradient(135deg, #6c63ff, #3f3d9e)", soft: "#f0eeff", fg: "#6c63ff" },
  { gradient: "linear-gradient(135deg, #fa8c16, #d46b08)", soft: "#fff7e6", fg: "#fa8c16" },
  { gradient: "linear-gradient(135deg, #52c41a, #389e0d)", soft: "#f6ffed", fg: "#52c41a" },
  { gradient: "linear-gradient(135deg, #1890ff, #096dd9)", soft: "#e6f7ff", fg: "#1890ff" },
  { gradient: "linear-gradient(135deg, #722ed1, #531dab)", soft: "#f9f0ff", fg: "#722ed1" },
];

function tintFor(id: number) {
  return TINT_PALETTE[id % TINT_PALETTE.length];
}

function countTone(count?: number) {
  const c = count ?? 0;
  if (c >= 5) return { text: `${c}次评估`, type: "primary" as const };
  if (c >= 2) return { text: `${c}次评估`, type: "success" as const };
  return { text: c ? `${c}次评估` : "暂无评估", type: "info" as const };
}

function goDiagnosisDetail(conversationId: number, recordId: number | undefined) {
  if (recordId == null) return;
  router.push(`/diagnosis/${conversationId}/${recordId}`);
}

function goConversationRecords(session: DiagnosisConversationVO) {
  router.push(`/diagnosis/${session.conversationId}`);
}

watch(
  () => userStore.token,
  (t) => { if (t && sessions.value.length === 0 && !loading.value) void fetchList(); },
);

onMounted(() => { if (userStore.token) void fetchList(); });
</script>

<style scoped>
.diagnosis-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.page-header {
  margin-bottom: 24px;
}

.header-eyebrow {
  font-size: 12px;
  color: #6c63ff;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 6px 0;
}

.page-subtitle {
  font-size: 14px;
  color: #888;
  margin: 0 0 16px;
}

.search-row {
  display: flex;
  gap: 8px;
}

.search-input {
  flex: 1;
}

.list-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sk-card {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 12px;
  padding: 20px;
}

.sk-line {
  height: 14px;
  background: linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 6px;
}

.sk-title {
  width: 45%;
  margin-bottom: 10px;
}

.sk-meta {
  width: 60%;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.empty-wrap {
  margin-top: 60px;
}

/* Session Cards */
.session-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.session-card {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  overflow: hidden;
  transition: box-shadow 0.2s;
}

.session-card:hover {
  box-shadow: 0 6px 20px rgba(26, 26, 46, 0.06);
}

.card-strip {
  display: block;
  height: 3px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  cursor: pointer;
  user-select: none;
}

.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
}

.card-main {
  flex: 1;
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.meta-dot {
  color: #d0d0d0;
}

.card-chevron {
  transition: transform 0.25s;
  color: #ccc;
}

.card-chevron.rotated {
  transform: rotate(180deg);
}

/* Card Body */
.card-body {
  border-top: 1px solid #f0f0f5;
}

.expand-enter-active,
.expand-leave-active {
  transition: opacity 0.25s, max-height 0.3s;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 500px;
}

.body-inner {
  padding: 16px 20px 18px;
}

.info-chips {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 16px;
}

.info-chip {
  background: #f9f9fd;
  border-radius: 8px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chip-label {
  font-size: 11px;
  color: #b0b6c4;
}

.chip-value {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.records-block {
  margin-bottom: 14px;
}

.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.records-title {
  font-size: 13px;
  font-weight: 600;
  color: #666;
}

.records-state {
  font-size: 13px;
  color: #999;
  padding: 10px 0;
  text-align: center;
}

.mini-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid #e6e3ff;
  border-top-color: #6c63ff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  margin-right: 8px;
  vertical-align: middle;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.record-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.record-row:hover {
  background: #f8f7ff;
}

.record-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  flex-shrink: 0;
}

.record-time {
  font-size: 12px;
  color: #999;
  flex: 1;
}

.record-more {
  font-size: 12px;
  color: #6c63ff;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.view-all-btn {
  width: 100%;
  border: 1.5px solid #6c63ff;
  background: transparent;
  color: #6c63ff;
  border-radius: 10px;
  padding: 10px 0;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: background 0.2s, color 0.2s;
  font-family: inherit;
}

.view-all-btn:hover {
  background: #6c63ff;
  color: #fff;
}

.pagination-bar {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>