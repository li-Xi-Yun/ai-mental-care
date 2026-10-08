<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import { Loading } from "@element-plus/icons-vue";
import type { TagProps } from "element-plus";
import { getScaleRecordAnswers, getScaleRecordDetail } from "@/portal/api/scale";
import { dayjs, FORMAT_DATETIME } from "@/shared/utils";

/* ==================== 接口数据结构（与后端 VO 对齐） ==================== */

interface SelectedOptionVO {
  optionId: number;
  optionText: string;
}

interface DimensionResultVO {
  dimensionId: number;
  dimensionName: string;
  dimScore: number;
  dimResult: string;
  riskLevel: number;
}

interface ScaleRecordDetailVO {
  recordId: number;
  scaleId: number;
  scaleName: string;
  totalScore?: number;
  standardScore?: number;
  percentile?: number;
  /** 风险等级：0-正常 1-轻度 2-中度 3-重度 */
  riskLevel?: number;
  resultText?: string;
  /** 完成状态：0-未完成 1-已完成 2-中途终止 */
  finishStatus?: number;
  startTime?: string;
  endTime?: string;
  createdTime?: string;
  versionNo?: string;
  /** 是否匿名测评：0-否 1-是 */
  anonymous?: number;
  dimensionResults?: DimensionResultVO[];
}

interface ScaleAnswerDetailVO {
  questionId: number;
  questionTitle: string;
  /** 题目类型：1-单选 2-多选 3-填空 */
  questionType: number;
  /** 作答状态：0-未作答 1-已作答 */
  answerStatus: number;
  answerText?: string;
  selectedOptions?: SelectedOptionVO[];
  createdTime?: string;
}

/** 维度得分在页面上的展示模型（含风险配色与渐变权重比例） */
interface DimensionView {
  dimensionId: number;
  dimensionName: string;
  dimResult: string;
  score: number;
  percent: number;
  color: string;
  bg: string;
  riskLabel: string;
}

/* ==================== 基础状态 ==================== */

const route = useRoute();
const recordId = route.params.recordId as string;

const loading = ref(true);
const error = ref("");

const detail = ref<ScaleRecordDetailVO | null>(null);
const answers = ref<ScaleAnswerDetailVO[]>([]);
const answersLoading = ref(false);

/* ==================== 元信息配置 ==================== */

/** 完成状态：0 未完成 / 1 已完成 / 2 中途终止 */
const STATUS_META: Record<number, { label: string; tagType: TagProps["type"] }> = {
  0: { label: "未完成", tagType: "warning" },
  1: { label: "已完成", tagType: "success" },
  2: { label: "中途终止", tagType: "info" },
};

/** 风险等级配色：0 正常绿 / 1 轻度黄 / 2 中度橙 / 3 重度红 */
const RISK_META: Record<number, { label: string; color: string; bg: string }> = {
  0: { label: "正常", color: "#52c41a", bg: "#f6ffed" },
  1: { label: "轻度", color: "#fa8c16", bg: "#fff7e6" },
  2: { label: "中度", color: "#fa541c", bg: "#fff2e8" },
  3: { label: "重度", color: "#ff4d4f", bg: "#fff1f0" },
};

/** 题目类型文案：1-单选 2-多选 3-填空 */
const QUESTION_TYPE_LABELS: Record<number, string> = {
  1: "单选",
  2: "多选",
  3: "填空",
};

function statusMeta(status?: number) {
  return STATUS_META[status ?? 0] ?? STATUS_META[0];
}

function riskMeta(level?: number) {
  return RISK_META[level ?? 0] ?? RISK_META[0];
}

function questionTypeLabel(type?: number): string {
  return QUESTION_TYPE_LABELS[type ?? 0] ?? "未知题型";
}

/* ==================== 派生数据 ==================== */

/** 顶部状态标签（完成后展示） */
const status = computed(() => statusMeta(detail.value?.finishStatus));

/** 核心结果风险元信息 */
const risk = computed(() => riskMeta(detail.value?.riskLevel));

/** 维度得分列表 */
const dimensions = computed(() => detail.value?.dimensionResults ?? []);

/** 最大维度得分：作为进度条宽度比例基准 */
const maxDimScore = computed(() =>
  Math.max(1, ...dimensions.value.map((d) => Number(d.dimScore) || 0)),
);

/** 维度展示模型：按「与最大维度占比」计算进度条宽度 */
const dimensionItems = computed<DimensionView[]>(() =>
  dimensions.value.map((dim) => {
    const score = Number(dim.dimScore) || 0;
    const meta = riskMeta(dim.riskLevel);
    return {
      dimensionId: dim.dimensionId,
      dimensionName: dim.dimensionName,
      dimResult: dim.dimResult,
      score,
      percent: Math.round((score / maxDimScore.value) * 100),
      color: meta.color,
      bg: meta.bg,
      riskLabel: meta.label,
    };
  }),
);

/** 该记录是否展示核心结果（已完成 / 中途终止均有得分） */
const hasScore = computed(() => {
  const total = detail.value?.totalScore;
  return total !== undefined && total !== null && !Number.isNaN(Number(total));
});

/* ==================== 展示辅助 ==================== */

function formatScore(value?: number): string {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return "—";
  return String(value);
}

function formatPercentile(value?: number): string {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return "—";
  return `${value}%`;
}

function formatDatetime(time?: string): string {
  if (!time) return "—";
  const d = dayjs(time);
  return d.isValid() ? d.format(FORMAT_DATETIME) : String(time);
}

/** 本次作答用时（startTime → endTime/createdTime） */
function formatDuration(): string {
  const start = detail.value?.startTime;
  const end = detail.value?.endTime || detail.value?.createdTime;
  if (!start || !end) return "—";
  const s = dayjs(start);
  const e = dayjs(end);
  if (!s.isValid() || !e.isValid()) return "—";
  const seconds = Math.max(0, e.diff(s, "second"));
  const m = Math.floor(seconds / 60);
  const sec = seconds % 60;
  if (m <= 0) return `${sec} 秒`;
  return `${m} 分 ${sec} 秒`;
}

/* ==================== 数据请求 ==================== */

async function loadDetail() {
  try {
    const res = await getScaleRecordDetail(recordId);
    detail.value = res.data?.data ?? null;
    if (!detail.value) {
      error.value = "未找到该测评记录，可能已被删除";
    }
  } catch (e) {
    error.value = (e as Error)?.message || "测评详情加载失败，请稍后重试";
  }
}

async function loadAnswers() {
  answersLoading.value = true;
  try {
    const res = await getScaleRecordAnswers(recordId);
    answers.value = (res.data?.data ?? []) as ScaleAnswerDetailVO[];
  } catch {
    // 明细拉取失败不影响结果展示，答题明细区呈现空态并提示
    answers.value = [];
  } finally {
    answersLoading.value = false;
  }
}

async function init() {
  loading.value = true;
  error.value = "";
  await Promise.all([loadDetail(), loadAnswers()]);
  loading.value = false;
}

void init();
</script>

<template>
  <div class="record-detail-page">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/scale" class="breadcrumb-link">量表测评</router-link>
      <span class="breadcrumb-sep">›</span>
      <router-link to="/scale/records" class="breadcrumb-link">测评记录</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">{{ detail?.scaleName || "答题详情" }}</span>
    </div>

    <!-- 全页加载态 -->
    <div v-if="loading" class="page-state">
      <el-icon class="is-loading" :size="36" color="#6c63ff">
        <Loading />
      </el-icon>
      <p class="state-text">正在加载测评详情…</p>
    </div>

    <!-- 加载失败 / 记录不存在 -->
    <div v-else-if="error || !detail" class="page-state">
      <el-empty :description="error || '未找到该测评记录'">
        <el-button type="primary" plain @click="init">重新加载</el-button>
      </el-empty>
    </div>

    <template v-else>
      <!-- 标题 + 完成状态 -->
      <div class="page-head">
        <div class="head-left">
          <h2 class="page-title">{{ detail.scaleName }}</h2>
          <div class="head-meta">
            <template v-if="detail.versionNo">
              <span class="meta-item">版本 v{{ detail.versionNo }}</span>
              <span class="meta-sep">·</span>
            </template>
            <template v-if="detail.anonymous === 1">
              <span class="meta-anon">匿名作答</span>
              <span class="meta-sep">·</span>
            </template>
            <span class="meta-item">记录 #{{ detail.recordId }}</span>
            <span class="meta-sep">·</span>
            <span class="meta-item">创建于 {{ formatDatetime(detail.createdTime) }}</span>
          </div>
        </div>
        <el-tag :type="status.tagType" effect="light" round size="large">
          {{ status.label }}
        </el-tag>
      </div>

      <!-- 核心结果卡（渐变区） -->
      <section class="result-hero">
        <div class="hero-score">
          <span class="score-label">测评总分</span>
          <div class="score-row">
            <span class="score-value" :style="{ color: risk.color }">
              {{ formatScore(detail.totalScore) }}
            </span>
            <span class="score-unit">分</span>
          </div>
          <span class="score-subtitle">标准分 {{ formatScore(detail.standardScore) }}</span>
        </div>

        <div class="hero-divider" />

        <div class="hero-main">
          <span class="hero-badge" :style="{ color: risk.color, background: risk.bg }">
            {{ risk.label }}
          </span>
          <p class="hero-text">
            {{ detail.resultText || (hasScore ? "该量表暂无详细文字解读。" : "本次记录尚未完成，暂无结果解读。") }}
          </p>
        </div>

        <div class="hero-stats">
          <div class="hero-stat">
            <span class="stat-label">百分位</span>
            <span class="stat-value">{{ formatPercentile(detail.percentile) }}</span>
          </div>
          <div class="hero-stat">
            <span class="stat-label">作答时长</span>
            <span class="stat-value">{{ formatDuration() }}</span>
          </div>
          <div class="hero-stat">
            <span class="stat-label">完成时间</span>
            <span class="stat-value">{{ formatDatetime(detail.endTime || detail.createdTime) }}</span>
          </div>
        </div>
      </section>

      <!-- 维度得分卡 -->
      <section class="info-card dimension-section">
        <div class="section-head">
          <h3 class="section-title">维度得分分析</h3>
          <span class="section-count">共 {{ dimensionItems.length }} 个维度</span>
        </div>
        <el-empty
          v-if="dimensionItems.length === 0"
          description="该量表无维度得分数据"
          :image-size="80"
        />
        <div v-else class="dimension-list">
          <div v-for="dim in dimensionItems" :key="dim.dimensionId" class="dimension-item">
            <div class="dim-head">
              <span class="dim-name">{{ dim.dimensionName }}</span>
              <span class="dim-score">
                <b :style="{ color: dim.color }">{{ dim.score }}</b>
                <i class="dim-unit">分</i>
              </span>
            </div>
            <div class="dim-track">
              <div class="dim-bar" :style="{ width: dim.percent + '%', background: dim.color }" />
            </div>
            <div class="dim-foot">
              <span class="dim-tag" :style="{ color: dim.color, background: dim.bg }">
                {{ dim.riskLabel }}
              </span>
              <span class="dim-text">{{ dim.dimResult || "—" }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 答题明细区 -->
      <section class="info-card answer-section">
        <div class="section-head">
          <h3 class="section-title">答题明细</h3>
          <span class="section-count">共 {{ answers.length }} 题</span>
        </div>

        <div v-if="answersLoading" class="answers-loading">
          <el-icon class="is-loading" :size="24" color="#6c63ff"><Loading /></el-icon>
          <span class="answers-loading-text">正在加载答题明细…</span>
        </div>

        <el-empty
          v-else-if="answers.length === 0"
          description="该记录暂无答题明细"
          :image-size="80"
        />

        <div v-else class="answer-list">
          <div
            v-for="(item, index) in answers"
            :key="item.questionId"
            class="answer-card"
            :class="{ unanswered: item.answerStatus !== 1 }"
          >
            <div class="a-head">
              <span class="a-index">第 {{ index + 1 }} 题</span>
              <el-tag size="small" effect="plain" class="a-type">
                {{ questionTypeLabel(item.questionType) }}
              </el-tag>
              <el-tag
                size="small"
                effect="light"
                :type="item.answerStatus === 1 ? 'success' : 'warning'"
                class="a-status"
              >
                {{ item.answerStatus === 1 ? "已作答" : "未作答" }}
              </el-tag>
              <span class="a-time">{{ formatDatetime(item.createdTime) }}</span>
            </div>

            <h4 class="a-title">{{ item.questionTitle || `题目 ${item.questionId}` }}</h4>

            <div v-if="item.answerStatus === 1" class="a-body">
              <div v-if="item.selectedOptions?.length" class="a-options">
                <span
                  v-for="opt in item.selectedOptions"
                  :key="opt.optionId"
                  class="a-pill"
                >
                  {{ opt.optionText }}
                </span>
              </div>
              <div v-else-if="item.answerText" class="a-fill">{{ item.answerText }}</div>
              <div v-else class="a-fallback">本题未选择具体选项</div>
            </div>
            <div v-else class="a-unanswered">本小题未作答，已跳过</div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.record-detail-page {
  padding: 24px;
  max-width: 980px;
  margin: 0 auto;
}

/* ---------- 面包屑 ---------- */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #999;
  margin-bottom: 16px;
}

.breadcrumb-link {
  color: #6c63ff;
  text-decoration: none;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

/* ---------- 全页状态 ---------- */
.page-state {
  min-height: 55vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.state-text {
  font-size: 13px;
  color: #999;
  margin: 0;
}

/* ---------- 标题区 ---------- */
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.head-left {
  min-width: 0;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.3;
  margin: 0 0 6px;
}

.head-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 12px;
  color: #888;
}

.meta-sep {
  color: #d0d0d0;
}

.meta-anon {
  color: #6c63ff;
  background: #f0eeff;
  padding: 0 7px;
  border-radius: 5px;
  font-size: 11px;
  line-height: 18px;
}

/* ---------- 核心结果卡（渐变核心区） ---------- */
.result-hero {
  position: relative;
  overflow: hidden;
  background: linear-gradient(120deg, #f1efff 0%, #e9e7ff 55%, #ddd9ff 100%);
  border-radius: 18px;
  padding: 26px 32px;
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.result-hero::before {
  content: "";
  position: absolute;
  right: -70px;
  top: -90px;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(108, 99, 255, 0.16), transparent 70%);
}

.result-hero::after {
  content: "";
  position: absolute;
  right: 130px;
  bottom: -70px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(108, 99, 255, 0.1), transparent 70%);
}

.hero-score {
  flex-shrink: 0;
  text-align: center;
  min-width: 150px;
  position: relative;
  z-index: 1;
}

.score-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #6c63ff;
  letter-spacing: 1px;
  margin-bottom: 4px;
}

.score-row {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
}

.score-value {
  font-size: 52px;
  font-weight: 800;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.score-unit {
  font-size: 13px;
  color: #999;
}

.score-subtitle {
  display: block;
  font-size: 12px;
  color: #666;
  margin-top: 4px;
}

.hero-divider {
  width: 1px;
  height: 96px;
  background: rgba(108, 99, 255, 0.18);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.hero-main {
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  padding: 4px 14px;
  border-radius: 8px;
  margin-bottom: 10px;
}

.hero-text {
  font-size: 13px;
  color: #555;
  line-height: 1.8;
  margin: 0;
}

.hero-stats {
  flex-shrink: 0;
  display: grid;
  gap: 14px;
  min-width: 150px;
  position: relative;
  z-index: 1;
}

.hero-stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(108, 99, 255, 0.12);
  border-radius: 12px;
  padding: 8px 14px;
}

.stat-label {
  font-size: 12px;
  color: #888;
}

.stat-value {
  font-size: 13px;
  font-weight: 600;
  color: #3f3d9e;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

/* ---------- 通用信息卡 ---------- */
.info-card {
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  padding: 20px 24px;
  margin-bottom: 18px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f2f2f5;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.section-count {
  font-size: 12px;
  color: #aaa;
}

/* ---------- 维度得分 ---------- */
.dimension-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dimension-item {
  display: grid;
  grid-template-columns: 150px 1fr;
  grid-template-rows: auto auto;
  column-gap: 16px;
  row-gap: 6px;
  align-items: center;
}

.dim-head {
  grid-column: 1;
  grid-row: 1;
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.dim-name {
  font-size: 13px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dim-score {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.dim-score b {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.dim-unit {
  font-style: normal;
  font-size: 11px;
  color: #aaa;
}

.dim-track {
  grid-column: 2;
  grid-row: 1;
  height: 8px;
  border-radius: 6px;
  background: #f0f0f4;
  overflow: hidden;
}

.dim-bar {
  height: 100%;
  border-radius: 6px;
  transition: width 0.5s ease;
  min-width: 2px;
}

.dim-foot {
  grid-column: 2;
  grid-row: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.dim-tag {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 500;
  padding: 1px 8px;
  border-radius: 8px;
}

.dim-text {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 答题明细 ---------- */
.answers-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 0;
  color: #999;
  font-size: 13px;
}

.answer-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.answer-card {
  border: 1px solid #eef0f4;
  border-radius: 12px;
  padding: 14px 18px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.answer-card:hover {
  border-color: #d9d2ff;
  box-shadow: 0 4px 14px rgba(108, 99, 255, 0.06);
}

.answer-card.unanswered {
  background: #fafbfc;
}

.a-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.a-index {
  font-size: 13px;
  font-weight: 600;
  color: #6c63ff;
  font-variant-numeric: tabular-nums;
}

.a-type {
  border-color: #d9d2ff;
  color: #6c63ff;
}

.a-status {
  margin-left: 2px;
}

.a-time {
  margin-left: auto;
  font-size: 11px;
  color: #bbb;
}

.a-title {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a2e;
  line-height: 1.6;
  margin: 0 0 10px;
}

.a-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.a-pill {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  color: #6c63ff;
  background: #f0eeff;
  border: 1px solid #d9d2ff;
  border-radius: 16px;
  padding: 4px 14px;
  line-height: 1.4;
}

.a-fill {
  font-size: 13px;
  color: #333;
  line-height: 1.7;
  background: #faf9ff;
  border: 1px dashed #d9d2ff;
  border-radius: 8px;
  padding: 10px 14px;
  white-space: pre-wrap;
  word-break: break-all;
}

.a-fallback,
.a-unanswered {
  font-size: 12px;
  color: #999;
  background: #fafbfc;
  border: 1px dashed #e5e5e8;
  border-radius: 8px;
  padding: 8px 12px;
}
</style>