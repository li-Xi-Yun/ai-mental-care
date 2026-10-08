<template>
  <div class="diagnosis-page">
    <!-- 页头：标题 + 搜索 -->
    <header class="page-header">
      <div class="header-copy">
        <div class="header-eyebrow">AI 心理健康 · 情绪诊断</div>
        <h2 class="page-title">心理诊断</h2>
        <p class="page-subtitle">AI 根据对话生成的诊断书沉淀于此，持续追踪你的情绪状态变化</p>
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

    <!-- 首屏加载骨架 -->
    <div v-if="loading" class="list-loading">
      <div v-for="i in 3" :key="i" class="sk-card">
        <div class="sk-line sk-title"></div>
        <div class="sk-line sk-meta"></div>
      </div>
    </div>

    <template v-else>
      <!-- 加载失败 -->
      <div v-if="loadError" class="empty-wrap">
        <el-empty description="诊断列表加载失败，请稍后重试">
          <el-button type="primary" @click="fetchList">重新加载</el-button>
        </el-empty>
      </div>

      <!-- 空态 -->
      <el-empty
        v-else-if="sessions.length === 0"
        class="empty-wrap"
        description="暂未生成诊断记录，快去 AI 对话中聊聊吧"
      />

      <!-- 会话卡片列表 -->
      <template v-else>
        <div class="session-list">
          <div
            v-for="session in sessions"
            :key="session.conversationId"
            class="session-card"
            :class="{ expanded: expandedConversationId === session.conversationId }"
          >
            <!-- 状态色条 -->
            <span
              class="card-strip"
              :style="{ backgroundImage: tintFor(session.conversationId).gradient }"
            ></span>

            <!-- 卡片头部：点击展开/收起 -->
            <div class="card-header" @click="toggleExpand(session)">
              <span
                class="card-icon"
                :style="{
                  background: tintFor(session.conversationId).soft,
                  color: tintFor(session.conversationId).fg,
                }"
              >{{ iconFor(session.conversationName) }}</span>

              <div class="card-main">
                <div class="card-title-row">
                  <span class="card-title" :title="session.conversationName">{{
                    session.conversationName
                  }}</span>
                  <el-tag
                    :type="countTone(session.diagnosisCount).type"
                    size="small"
                    effect="light"
                    round
                  >{{ countTone(session.diagnosisCount).text }}</el-tag>
                </div>
                <div class="card-meta">
                  <span class="meta-item">最新诊断 {{ roundText(session.latestRoundNum) }}</span>
                  <span class="meta-dot">·</span>
                  <span class="meta-item">最近 {{ formatDateTime(session.diagnosisUpdatedTime) }}</span>
                </div>
              </div>

              <span
                class="card-chevron"
                :class="{ rotated: expandedConversationId === session.conversationId }"
              >
                <el-icon :size="16"><ArrowDown /></el-icon>
              </span>
            </div>

            <!-- 展开区 -->
            <transition name="expand">
              <div v-show="expandedConversationId === session.conversationId" class="card-body">
                <div class="body-inner">
                  <div class="info-chips">
                    <div class="info-chip">
                      <span class="chip-label">会话创建</span>
                      <span class="chip-value">{{ formatDateTime(session.conversationCreatedTime) }}</span>
                    </div>
                    <div class="info-chip">
                      <span class="chip-label">最近诊断</span>
                      <span class="chip-value">{{ formatDateTime(session.diagnosisUpdatedTime) }}</span>
                    </div>
                    <div class="info-chip">
                      <span class="chip-label">最新轮次</span>
                      <span class="chip-value">{{ roundText(session.latestRoundNum) }}</span>
                    </div>
                    <div class="info-chip">
                      <span class="chip-label">诊断次数</span>
                      <span class="chip-value">{{ session.diagnosisCount ?? 0 }} 次</span>
                    </div>
                  </div>

                  <!-- 诊断记录（展开时懒加载） -->
                  <div class="records-block">
                    <div class="records-head">
                      <span class="records-title">诊断记录</span>
                      <el-button
                        text
                        type="primary"
                        size="small"
                        class="records-link"
                        @click="goConversationRecords(session)"
                      >
                        查看全部记录 <el-icon><ArrowRight /></el-icon>
                      </el-button>
                    </div>

                    <div v-if="isRecordsLoading(session)" class="records-state">
                      <span class="mini-spinner"></span>
                      正在加载诊断记录…
                    </div>
                    <div v-else-if="recordsOf(session).length === 0" class="records-state">
                      该会话暂无诊断书记录
                    </div>
                    <div v-else class="records-list">
                      <div
                        v-for="record in recordsOf(session)"
                        :key="record.id"
                        class="record-row"
                        @click="goDiagnosisDetail(session.conversationId, record.id)"
                      >
                        <span
                          class="record-badge"
                          :style="{
                            background: tintFor(session.conversationId).soft,
                            color: tintFor(session.conversationId).fg,
                          }"
                        >{{ record.roundNum ? `第${record.roundNum}轮` : "诊断书" }}</span>
                        <span class="record-time">{{ formatDateTime(record.createdTime) }}</span>
                        <span class="record-more">
                          查看详情 <el-icon :size="13"><ArrowRight /></el-icon>
                        </span>
                      </div>
                    </div>
                  </div>

                  <!-- 查看诊断记录入口 -->
                  <button type="button" class="view-all-btn" @click="goConversationRecords(session)">
                    查看诊断记录
                    <el-icon class="btn-arrow"><ArrowRight /></el-icon>
                  </button>
                </div>
              </div>
            </transition>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="total > 0" class="pagination-bar">
          <el-pagination
            v-model:current-page="pageNum"
            :page-size="DEFAULT_PAGE_SIZE"
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
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ArrowDown, ArrowRight, Search } from "@element-plus/icons-vue";
import { dayjs } from "@/shared/utils";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import {
  getEmotionDiagnosisList,
  getEmotionDiagnosisListByConversation,
  type DiagnosisConversationVO,
  type EmotionDiagnosisListVO,
} from "@/portal/api/conversation/emotion-diagnosis";

const router = useRouter();

/* ==================== 状态 ==================== */

const keyword = ref("");
const pageNum = ref(1);
const total = ref(0);
const sessions = ref<DiagnosisConversationVO[]>([]);
const loading = ref(false);
const loadError = ref(false);

/** 当前展开的会话 ID（同一时刻仅展开一个） */
const expandedConversationId = ref<number | null>(null);
/** 各会话下已加载的诊断书缓存 */
const recordMap = reactive<Record<number, EmotionDiagnosisListVO[]>>({});
/** 各会话诊断书加载状态 */
const recordsLoadingMap = reactive<Record<number, boolean>>({});

/* ==================== 数据请求 ==================== */

async function fetchList() {
  loading.value = true;
  loadError.value = false;
  resetExpandState();
  try {
    const keywordTrimmed = keyword.value.trim();
    const res = await getEmotionDiagnosisList({
      pageNum: pageNum.value,
      pageSize: DEFAULT_PAGE_SIZE,
      ...(keywordTrimmed ? { conversationName: keywordTrimmed } : {}),
    });
    const page = res.data?.data;
    sessions.value = page?.records ?? [];
    total.value = page?.total ?? 0;
  } catch {
    sessions.value = [];
    total.value = 0;
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

/** 清除展开状态与按会话缓存的诊断书（换页/搜索后会话集合变化） */
function resetExpandState() {
  expandedConversationId.value = null;
  for (const key of Object.keys(recordMap)) delete recordMap[Number(key)];
  for (const key of Object.keys(recordsLoadingMap)) delete recordsLoadingMap[Number(key)];
}

async function loadRecords(conversationId: number) {
  recordsLoadingMap[conversationId] = true;
  try {
    const res = await getEmotionDiagnosisListByConversation(String(conversationId), {
      pageNum: 1,
      pageSize: 50,
    });
    recordMap[conversationId] = res.data?.data?.records ?? [];
  } catch {
    recordMap[conversationId] = [];
  } finally {
    recordsLoadingMap[conversationId] = false;
  }
}

/* ==================== 交互 ==================== */

function handleSearch() {
  pageNum.value = 1;
  void fetchList();
}

function toggleExpand(session: DiagnosisConversationVO) {
  const id = session.conversationId;
  if (!id) return;
  if (expandedConversationId.value === id) {
    expandedConversationId.value = null;
    return;
  }
  expandedConversationId.value = id;
  // 首次展开才懒加载诊断记录
  if (!recordMap[id] && !recordsLoadingMap[id]) void loadRecords(id);
}

function goConversationRecords(session: DiagnosisConversationVO) {
  if (session.conversationId == null) return;
  router.push(`/diagnosis/${session.conversationId}`);
}

function goDiagnosisDetail(conversationId?: number, diagnosisId?: number) {
  if (conversationId == null || diagnosisId == null) return;
  router.push(`/diagnosis/${conversationId}/${diagnosisId}`);
}

/* ==================== 派生展示辅助 ==================== */

function recordsOf(session: DiagnosisConversationVO): EmotionDiagnosisListVO[] {
  return session.conversationId ? recordMap[session.conversationId] ?? [] : [];
}

function isRecordsLoading(session: DiagnosisConversationVO): boolean {
  return !!session.conversationId && !!recordsLoadingMap[session.conversationId];
}

function roundText(round?: number): string {
  return round ? `第${round}轮` : "--";
}

function formatDateTime(value?: string | null): string {
  if (!value) return "--";
  const d = dayjs(value);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(value);
}

type TagType = "success" | "warning" | "danger" | "info";

/** 依据诊断次数给出表现层关注度（非临床风险判定） */
function countTone(count?: number): { type: TagType; text: string } {
  const c = count ?? 0;
  if (c >= 5) return { type: "danger", text: `已诊断 ${c} 次` };
  if (c >= 3) return { type: "warning", text: `已诊断 ${c} 次` };
  return { type: "success", text: `已诊断 ${c} 次` };
}

/* ==================== 视觉元信息 ==================== */

/** 会话名称关键词 → 表情图标（与量表页风格一致） */
const EMOJI_RULES: [RegExp, string][] = [
  [/焦虑|紧张|惶恐|压力/, "😰"],
  [/抑郁|低落/, "😔"],
  [/睡眠|失眠|入睡/, "😴"],
  [/工作|职场|加班|职业|裁员/, "💼"],
  [/关系|家庭|人际|社交|朋友|亲子/, "👥"],
  [/考试|学业|考研|学习/, "🎓"],
  [/成长|自我|探索/, "🌱"],
  [/恋爱|失恋|情感|亲密|婚姻/, "💔"],
  [/健康|身体|不适/, "💪"],
];

function iconFor(name?: string): string {
  if (!name) return "💬";
  const hit = EMOJI_RULES.find(([re]) => re.test(name));
  return hit ? hit[1] : "💭";
}

interface Tint {
  gradient: string;
  soft: string;
  fg: string;
}

const TINTS: Tint[] = [
  { gradient: "linear-gradient(135deg,#6c63ff,#9a93ff)", soft: "#f0eeff", fg: "#6c63ff" },
  { gradient: "linear-gradient(135deg,#fa8c16,#ffb54d)", soft: "#fff7e6", fg: "#fa8c16" },
  { gradient: "linear-gradient(135deg,#52c41a,#7ed357)", soft: "#f6ffed", fg: "#52c41a" },
  { gradient: "linear-gradient(135deg,#ff4d4f,#ff8080)", soft: "#fff1f0", fg: "#ff4d4f" },
  { gradient: "linear-gradient(135deg,#1890ff,#5cb0ff)", soft: "#e6f7ff", fg: "#1890ff" },
];

function tintFor(conversationId?: number): Tint {
  return TINTS[Math.abs(conversationId ?? 0) % TINTS.length];
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  void fetchList();
});
</script>

<style scoped>
.diagnosis-page {
  padding: 28px 24px 48px;
  max-width: 1080px;
  margin: 0 auto;
}

/* ============ 页头 ============ */

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
  font-size: 28px;
  font-weight: 800;
  color: #1a1a2e;
  letter-spacing: -0.5px;
  margin: 0;
}

.page-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: #9aa0b5;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-input {
  width: 240px;
}

.search-btn {
  border-radius: 8px;
}

/* ============ 加载骨架 ============ */

.list-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sk-card {
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  padding: 18px 20px;
}

.sk-line {
  height: 14px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f6 25%, #fafafd 50%, #f0f0f6 75%);
  background-size: 200% 100%;
  animation: sk 1.2s infinite;
}

.sk-title {
  width: 40%;
  margin-bottom: 12px;
}

.sk-meta {
  width: 70%;
}

@keyframes sk {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

/* ============ 空态/错误 ============ */

.empty-wrap {
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  padding: 48px 0;
}

/* ============ 会话卡片列表 ============ */

.session-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.session-card {
  position: relative;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  overflow: hidden;
  transition: box-shadow 0.25s, transform 0.25s, border-color 0.25s;
}

.session-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(108, 99, 255, 0.12);
  border-color: #d9d2ff;
}

.session-card.expanded {
  border-color: #d9d2ff;
  box-shadow: 0 8px 26px rgba(108, 99, 255, 0.14);
}

.card-strip {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}

/* ============ 卡片头部 ============ */

.card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px 18px 22px;
  cursor: pointer;
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.card-main {
  flex: 1;
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9aa0b5;
}

.meta-dot {
  color: #d6d9e4;
}

.card-chevron {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: #b0b6c4;
  background: #f7f7fb;
  flex-shrink: 0;
  transition: transform 0.3s, color 0.3s, background 0.3s;
}

.card-chevron.rotated {
  transform: rotate(180deg);
  color: #6c63ff;
  background: #f0eeff;
}

/* ============ 展开区动画 ============ */

.card-body {
  display: grid;
  grid-template-rows: 1fr;
  overflow: hidden;
  border-top: 1px solid #f4f3fa;
  background: #faf9ff;
}

.body-inner {
  min-height: 0;
  overflow: hidden;
  padding: 18px 22px 20px;
}

.expand-enter-active,
.expand-leave-active {
  transition: grid-template-rows 0.4s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s;
}

.expand-enter-from,
.expand-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
}

.expand-enter-to,
.expand-leave-from {
  grid-template-rows: 1fr;
  opacity: 1;
}

/* ============ 展开区内容 ============ */

.info-chips {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 18px;
}

.info-chip {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 10px;
  padding: 10px 12px;
}

.chip-label {
  display: block;
  font-size: 11px;
  color: #b0b6c4;
  margin-bottom: 4px;
}

.chip-value {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.records-block {
  margin-bottom: 16px;
}

.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.records-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.records-link {
  font-size: 12px;
}

.records-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 0;
  font-size: 13px;
  color: #b0b6c4;
}

.mini-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid #e3e0ff;
  border-top-color: #6c63ff;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.record-row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.record-row:hover {
  border-color: #6c63ff;
  box-shadow: 0 4px 14px rgba(108, 99, 255, 0.12);
  transform: translateX(2px);
}

.record-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 8px;
  flex-shrink: 0;
}

.record-time {
  flex: 1;
  font-size: 13px;
  color: #666;
}

.record-more {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  color: #6c63ff;
}

.view-all-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 11px 0;
  border: 1px dashed #c5bdff;
  border-radius: 10px;
  background: #fff;
  font-size: 13px;
  font-weight: 500;
  color: #6c63ff;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}

.view-all-btn:hover {
  background: #f0eeff;
  border-color: #6c63ff;
}

.btn-arrow {
  display: inline-flex;
}

/* ============ 分页 ============ */

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 22px;
}

/* ============ 响应式 ============ */

@media (max-width: 860px) {
  .info-chips {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .search-row {
    width: 100%;
  }

  .search-input {
    flex: 1;
    min-width: 0;
  }

  .card-icon {
    width: 42px;
    height: 42px;
    font-size: 18px;
  }
}
</style>