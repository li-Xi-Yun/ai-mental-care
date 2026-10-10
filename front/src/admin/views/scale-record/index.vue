<template>
  <div class="scale-record-page">
    <!-- ==================== 风险统计卡 ==================== -->
    <section class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><DataLine /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.totalCount ?? "—" }}</div>
          <div class="stat-label">测评总人次</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheck /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.noRiskCount ?? "—" }}</div>
          <div class="stat-label">正常人次</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-yellow">
          <el-icon :size="22"><Warning /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.lowRiskCount ?? "—" }}</div>
          <div class="stat-label">低风险人次</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-red">
          <el-icon :size="22"><CircleClose /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ midHighCountText }}</div>
          <div class="stat-label">中高风险人次</div>
        </div>
      </div>
    </section>

    <!-- ==================== 主卡片 ==================== -->
    <section class="content-card">
      <!-- 搜索条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索用户名 / 量表名"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-date-picker
          v-model="searchForm.timeRange"
          type="daterange"
          range-separator="至"
          start-placeholder="完成时间起"
          end-placeholder="完成时间止"
          value-format="YYYY-MM-DD"
          :clearable="true"
          style="width: 260px"
          @change="handleSearch"
        />
        <el-select v-model="searchForm.riskLevel" placeholder="风险等级" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="s in RISK_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-select v-model="searchForm.finishStatus" placeholder="完成状态" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="s in FINISH_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" row-key="recordId" class="record-table">
        <el-table-column label="记录ID" width="90" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ row.recordId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户名" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="user-name">{{ row.userName || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="量表名" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="scale-name">{{ row.scaleName || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="版本号" width="90" align="center">
          <template #default="{ row }">
            <span class="ver-tag">{{ row.versionNo || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总分" width="84" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ formatScore(row.totalScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标准分" width="84" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ formatScore(row.standardScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="百分位" width="84" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ formatScore(row.percentile) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="104" align="center">
          <template #default="{ row }">
            <span class="status-badge" :style="riskBadgeStyle(row.riskLevel)">
              <span class="status-dot" :style="{ background: RISK_META[riskKey(row.riskLevel)].color }" />
              {{ riskLabel(row.riskLevel) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="完成状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="finishTagType(row.finishStatus)" size="small" effect="light" round>
              {{ finishLabel(row.finishStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="150">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.startTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="150">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" class="op-btn op-detail" @click="openDetail(row as AdminScaleRecordVO)">详情</el-button>
            <el-button text size="small" class="op-btn op-answer" @click="openAnswers(row as AdminScaleRecordVO)">答题明细</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无测评记录" :image-size="90" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="page-total">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="loadData"
        />
      </div>
    </section>

    <!-- ==================== 记录详情抽屉 ==================== -->
    <el-drawer v-model="detailVisible" :title="`测评记录详情 · ${detailTarget?.userName ?? ''}`" size="560px">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail">
          <!-- 概览头 -->
          <div class="detail-head">
            <div class="detail-head-info">
              <div class="detail-scale">{{ detail.scaleName || "—" }}</div>
              <div class="detail-meta">
                <span class="meta-item">用户：{{ detail.userName || "—" }}</span>
                <span class="meta-divider">·</span>
                <span class="meta-item">版本 {{ detail.versionNo || "—" }}</span>
              </div>
              <div class="detail-badges">
                <span class="status-badge" :style="riskBadgeStyle(detail.riskLevel)">
                  <span class="status-dot" :style="{ background: RISK_META[riskKey(detail.riskLevel)].color }" />
                  {{ riskLabel(detail.riskLevel) }}
                </span>
                <el-tag :type="finishTagType(detail.finishStatus)" size="small" effect="light" round>
                  {{ finishLabel(detail.finishStatus) }}
                </el-tag>
              </div>
            </div>
          </div>

          <!-- 基础信息 -->
          <el-descriptions :column="1" border size="small" class="detail-desc">
            <el-descriptions-item label="记录ID">
              <span class="desc-highlight">{{ detail.recordId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="量表名称">{{ detail.scaleName || "—" }}</el-descriptions-item>
            <el-descriptions-item label="版本号">{{ detail.versionNo || "—" }}</el-descriptions-item>
            <el-descriptions-item label="常模组ID">{{ detail.normGroupId ?? "—" }}</el-descriptions-item>
            <el-descriptions-item label="总分">
              <span class="desc-highlight">{{ formatScore(detail.totalScore) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="标准分">
              <span class="desc-highlight">{{ formatScore(detail.standardScore) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="百分位">
              <span class="desc-highlight">{{ formatScore(detail.percentile) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="结果描述">
              <span class="desc-intro">{{ detail.resultText || "—" }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(detail.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ formatTime(detail.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.createdTime) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 维度得分 -->
          <div class="dim-section">
            <div class="dim-section-title">
              <el-icon :size="15"><Odometer /></el-icon>
              维度得分
            </div>
            <div v-if="detail.dimensionResults?.length" class="dim-list">
              <div v-for="dim in detail.dimensionResults" :key="dim.dimensionId" class="dim-item">
                <div class="dim-head">
                  <span class="dim-name">{{ dim.dimensionName || "—" }}</span>
                  <span class="dim-score">{{ formatScore(dim.dimScore) }} 分</span>
                  <span class="status-badge dim-risk" :style="riskBadgeStyle(dim.riskLevel)">
                    <span class="status-dot" :style="{ background: RISK_META[riskKey(dim.riskLevel)].color }" />
                    {{ riskLabel(dim.riskLevel) }}
                  </span>
                </div>
                <el-progress
                  class="dim-bar"
                  :percentage="dimPercent(dim.dimScore)"
                  :color="RISK_META[riskKey(dim.riskLevel)].color"
                  :show-text="false"
                  :stroke-width="8"
                />
                <div class="dim-result">{{ dim.dimResult || "—" }}</div>
              </div>
            </div>
            <el-empty v-else description="该记录暂无维度得分" :image-size="60" />
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- ==================== 答题明细抽屉 ==================== -->
    <el-drawer v-model="answersVisible" :title="`答题明细 · ${answerTarget?.scaleName ?? ''}`" size="640px">
      <div v-loading="answersLoading" class="drawer-body">
        <template v-if="answers.length">
          <div class="answer-summary">
            <span>共 {{ answers.length }} 题</span>
            <span class="meta-divider">·</span>
            <span>已作答 {{ answeredCount }}</span>
            <span class="meta-divider">·</span>
            <span>未作答 {{ answers.length - answeredCount }}</span>
          </div>
          <div class="answer-list">
            <div v-for="(item, idx) in answers" :key="item.questionId ?? idx" class="answer-card" :class="{ 'is-unanswered': isUnanswered(item) }">
              <div class="answer-head">
                <span class="answer-no">{{ idx + 1 }}</span>
                <el-tag :type="questionTypeTag(item.questionType)" size="small" effect="plain" class="answer-type">
                  {{ questionTypeLabel(item.questionType) }}
                </el-tag>
                <el-tag v-if="isUnanswered(item)" type="warning" size="small" effect="light" round>未作答</el-tag>
                <el-tag v-else type="success" size="small" effect="light" round>已作答</el-tag>
              </div>
              <div class="answer-title">{{ item.questionTitle || "—" }}</div>

              <!-- 选择题：选中选项 -->
              <div v-if="item.selectedOptions?.length" class="answer-options">
                <span v-for="opt in item.selectedOptions" :key="opt.optionId ?? opt.optionText" class="answer-option">
                  <el-icon :size="13" class="option-icon"><Check /></el-icon>
                  {{ opt.optionText || "—" }}
                </span>
              </div>

              <!-- 填空：答案文本 -->
              <div v-else-if="item.answerText" class="answer-text">
                <span class="answer-text-label">作答内容：</span>
                <span class="answer-text-value">{{ item.answerText }}</span>
              </div>

              <div v-else class="answer-empty">
                <el-icon :size="14"><InfoFilled /></el-icon>
                <span>本题未作答</span>
              </div>

              <div v-if="item.createdTime" class="answer-time">作答时间：{{ formatTime(item.createdTime) }}</div>
            </div>
          </div>
        </template>
        <el-empty v-else description="该记录暂无答题明细" :image-size="80" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  Check,
  CircleCheck,
  CircleClose,
  DataLine,
  InfoFilled,
  Odometer,
  RefreshRight,
  Search,
  Warning,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import type { AdminScaleAnswerDetailVO } from "@/admin/api/scale/record";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import {
  getScaleRecordPage,
  getScaleRecordDetail,
  getScaleRecordAnswers,
  getScaleRecordRiskStatistics,
} from "@/admin/api/scale/record";
import type {
  AdminScaleRecordVO,
  AdminScaleRecordDetailVO,
  AdminScaleRiskStatisticsVO,
} from "@/admin/api/scale/record";

/* ==================== 元数据 ==================== */

/** 风险等级：0=无 1=低 2=中 3=高（预警） */
const RISK_META: Record<number, { label: string; color: string; bg: string; border: string }> = {
  0: { label: "正常", color: "#10b981", bg: "#ecfdf5", border: "#a7f3d0" },
  1: { label: "低风险", color: "#eab308", bg: "#fefce8", border: "#fde68a" },
  2: { label: "中风险", color: "#f97316", bg: "#fff7ed", border: "#fed7aa" },
  3: { label: "高风险", color: "#ef4444", bg: "#fef2f2", border: "#fecaca" },
};

const RISK_OPTIONS = Object.entries(RISK_META).map(([value, meta]) => ({
  value: Number(value),
  label: meta.label,
}));

/** 作答状态：0=未完成 1=已完成 2=中途终止 */
const FINISH_META: Record<number, { label: string; tag: "info" | "success" | "warning" }> = {
  0: { label: "未完成", tag: "info" },
  1: { label: "已完成", tag: "success" },
  2: { label: "中途终止", tag: "warning" },
};

const FINISH_OPTIONS = Object.entries(FINISH_META).map(([value, meta]) => ({
  value: Number(value),
  label: meta.label,
}));

/** 题目类型：1=单选 2=多选 3=填空 */
const QUESTION_TYPE_META: Record<number, { label: string; tag: "primary" | "warning" | "info" }> = {
  1: { label: "单选", tag: "primary" },
  2: { label: "多选", tag: "warning" },
  3: { label: "填空", tag: "info" },
};

/* ==================== 工具函数 ==================== */

/** 安全取风险等级 key，未知/null 落到 0（正常展示） */
function riskKey(level?: number | null): number {
  return level === undefined || level === null ? 0 : RISK_META[level] ? level : 0;
}

function riskLabel(level?: number | null): string {
  return RISK_META[riskKey(level)].label;
}

function riskBadgeStyle(level?: number | null): Record<string, string> {
  const meta = RISK_META[riskKey(level)];
  return { color: meta.color, background: meta.bg, borderColor: meta.border };
}

function finishLabel(status?: number | null): string {
  return status === undefined || status === null ? "未知" : (FINISH_META[status]?.label ?? "未知");
}

function finishTagType(status?: number | null): "info" | "success" | "warning" {
  return status === undefined || status === null ? "info" : (FINISH_META[status]?.tag ?? "info");
}

function questionTypeLabel(type?: number | null): string {
  return type === undefined || type === null ? "未知" : (QUESTION_TYPE_META[type]?.label ?? "未知");
}

function questionTypeTag(type?: number | null): "primary" | "warning" | "info" {
  return type === undefined || type === null ? "info" : (QUESTION_TYPE_META[type]?.tag ?? "info");
}

function isUnanswered(item: AdminScaleAnswerDetailVO): boolean {
  return item.answerStatus === 0;
}

function formatScore(v?: number | null): string {
  return v === undefined || v === null ? "—" : String(v);
}

function formatTime(v?: string | number): string {
  if (v === undefined || v === null || v === "") return "—";
  // 后端可能返回时间戳（number）或 ISO 字符串；number 转 Date，字符串沿用原格式
  if (typeof v === "number") {
    const d = new Date(v);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
  return String(v).replace("T", " ").slice(0, 16);
}

/** 维度得分归一化为百分比进度条（按 100 分制展示） */
function dimPercent(score?: number | null): number {
  if (score === undefined || score === null) return 0;
  const n = Number(score);
  if (Number.isNaN(n)) return 0;
  return Math.min(100, Math.max(0, n));
}

/* ==================== 页面状态 ==================== */

const loading = ref(false);
const tableData = ref<AdminScaleRecordVO[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);

interface SearchForm {
  keyword: string;
  timeRange: [string, string] | null;
  riskLevel?: number;
  finishStatus?: number;
}

const searchForm = reactive<SearchForm>({
  keyword: "",
  timeRange: null,
  riskLevel: undefined,
  finishStatus: undefined,
});

/** 风险统计（接口失败时各卡降级为「—」） */
const stats = reactive<Partial<AdminScaleRiskStatisticsVO>>({});

const midHighCountText = computed(() => {
  const medium = stats.mediumRiskCount;
  const high = stats.highRiskCount;
  if (medium === undefined || high === undefined) return "—";
  return String((medium ?? 0) + (high ?? 0));
});

const answeredCount = computed(() => answers.value.filter((a) => a.answerStatus === 1).length);

/* ==================== 数据加载 ==================== */

/** 时间范围（YYYY-MM-DD）转完整时间字符串，结束时间取当天 23:59:59 */
function rangeToFullTime(range: [string, string] | null): { startTime?: string; endTime?: string } {
  if (!range || !range[0] || !range[1]) return {};
  return {
    startTime: `${range[0]} 00:00:00`,
    endTime: `${range[1]} 23:59:59`,
  };
}

function buildPageParams() {
  const time = rangeToFullTime(searchForm.timeRange);
  return {
    pageNum: currentPage.value,
    pageSize: pageSize.value,
    scaleName: searchForm.keyword.trim() || undefined,
    riskLevel: searchForm.riskLevel,
    finishStatus: searchForm.finishStatus,
    ...time,
  };
}

function unwrapPage(res: { data: { data?: { records?: AdminScaleRecordVO[]; total?: number } } }) {
  const d = res?.data?.data;
  return { records: d?.records ?? [], total: d?.total ?? 0 };
}

async function loadData() {
  loading.value = true;
  try {
    const res = await getScaleRecordPage(buildPageParams());
    const d = unwrapPage(res as never);
    tableData.value = d.records;
    total.value = d.total;
  } catch {
    ElMessage.error("加载测评记录失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}

/** 风险统计：使用当前筛选的时间范围（完成时间），失败时保持各卡为「—」 */
async function loadStatistics() {
  const time = rangeToFullTime(searchForm.timeRange);
  try {
    const res = await getScaleRecordRiskStatistics({ ...time });
    const data = res?.data?.data;
    if (data) {
      stats.totalCount = data.totalCount;
      stats.noRiskCount = data.noRiskCount;
      stats.lowRiskCount = data.lowRiskCount;
      stats.mediumRiskCount = data.mediumRiskCount;
      stats.highRiskCount = data.highRiskCount;
    }
  } catch {
    // 失败时保持各卡为「—」（不覆盖已加载数据，重置为空以显式降级）
    resetStats();
  }
}

function resetStats() {
  stats.totalCount = undefined;
  stats.noRiskCount = undefined;
  stats.lowRiskCount = undefined;
  stats.mediumRiskCount = undefined;
  stats.highRiskCount = undefined;
}

async function refreshAll() {
  await Promise.all([loadData(), loadStatistics()]);
}

/* ==================== 搜索 / 重置 ==================== */

function handleSearch() {
  currentPage.value = 1;
  refreshAll();
}

function handleReset() {
  searchForm.keyword = "";
  searchForm.timeRange = null;
  searchForm.riskLevel = undefined;
  searchForm.finishStatus = undefined;
  currentPage.value = 1;
  refreshAll();
}

/* ==================== 详情抽屉 ==================== */

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailTarget = ref<AdminScaleRecordVO | null>(null);
const detail = ref<AdminScaleRecordDetailVO | null>(null);

async function openDetail(row: AdminScaleRecordVO) {
  detailTarget.value = row;
  detail.value = null;
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    const res = await getScaleRecordDetail(row.recordId);
    detail.value = res?.data?.data ?? null;
  } catch {
    ElMessage.error("加载测评记录详情失败，请稍后重试");
  } finally {
    detailLoading.value = false;
  }
}

/* ==================== 答题明细抽屉 ==================== */

const answersVisible = ref(false);
const answersLoading = ref(false);
const answerTarget = ref<AdminScaleRecordVO | null>(null);
const answers = ref<AdminScaleAnswerDetailVO[]>([]);

async function openAnswers(row: AdminScaleRecordVO) {
  answerTarget.value = row;
  answers.value = [];
  answersVisible.value = true;
  answersLoading.value = true;
  try {
    const res = await getScaleRecordAnswers(row.recordId);
    answers.value = res?.data?.data ?? [];
  } catch {
    ElMessage.error("加载答题明细失败，请稍后重试");
  } finally {
    answersLoading.value = false;
  }
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  refreshAll();
});
</script>

<style scoped>
.scale-record-page {
  --el-color-primary: #6366f1;
  --el-color-primary-light-3: #818cf8;
  --el-color-primary-light-5: #a5b4fc;
  --el-color-primary-light-7: #c7d2fe;
  --el-color-primary-light-8: #e0e7ff;
  --el-color-primary-light-9: #eef2ff;
  --el-color-primary-dark-2: #4338ca;
  --el-border-radius-base: 8px;

  display: flex;
  flex-direction: column;
  gap: 16px;
  color: #1e293b;
}

/* ==================== 统计卡 ==================== */

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.stat-card:hover {
  box-shadow: 0 6px 18px rgba(16, 24, 40, 0.08);
  transform: translateY(-1px);
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-icon-indigo {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.stat-icon-green {
  background: linear-gradient(135deg, #10b981, #059669);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.stat-icon-yellow {
  background: linear-gradient(135deg, #f59e0b, #eab308);
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}

.stat-icon-red {
  background: linear-gradient(135deg, #ef4444, #f97316);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.stat-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

/* ==================== 主卡片 ==================== */

.content-card {
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.toolbar-spacer {
  flex: 1;
}

.btn-icon {
  margin-right: 4px;
}

/* ==================== 表格 ==================== */

.record-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
}

.record-table :deep(.el-table__header th) {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.record-table :deep(.el-table__cell) {
  padding: 12px 0;
}

.user-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.scale-name {
  font-weight: 600;
  color: #334155;
  font-size: 13px;
}

.ver-tag {
  display: inline-block;
  font-size: 11.5px;
  color: #4f46e5;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  border-radius: 6px;
  padding: 1px 7px;
  font-variant-numeric: tabular-nums;
}

.num-text {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.op-btn {
  font-size: 12px;
  font-weight: 500;
}

.op-detail {
  color: #3b82f6;
}

.op-detail:hover {
  color: #2563eb;
}

.op-answer {
  color: #6366f1;
}

.op-answer:hover {
  color: #4f46e5;
}

/* ==================== 分页 ==================== */

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.page-total {
  font-size: 12px;
  color: #94a3b8;
}

/* ==================== 抽屉通用 ==================== */

.drawer-body {
  min-height: 200px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0 18px;
}

.detail-head-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.detail-scale {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: #94a3b8;
}

.detail-badges {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-item {
  color: #64748b;
}

.meta-divider {
  color: #cbd5e1;
}

.detail-desc {
  margin-bottom: 20px;
}

.detail-desc :deep(.el-descriptions__label) {
  color: #64748b;
  font-weight: 500;
  width: 96px;
}

.desc-highlight {
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.desc-intro {
  color: #475569;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* ==================== 维度得分 ==================== */

.dim-section {
  margin-top: 6px;
}

.dim-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 12px;
}

.dim-section-title .el-icon {
  color: #6366f1;
}

.dim-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dim-item {
  background: #f8fafc;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  padding: 12px 14px;
}

.dim-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.dim-name {
  font-weight: 600;
  color: #1e293b;
  font-size: 13.5px;
}

.dim-score {
  font-size: 12.5px;
  color: #475569;
  font-variant-numeric: tabular-nums;
}

.dim-risk {
  margin-left: auto;
}

.dim-bar {
  margin-bottom: 8px;
}

.dim-result {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

/* ==================== 答题明细 ==================== */

.answer-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 14px;
}

.answer-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.answer-card {
  background: #f8fafc;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  padding: 12px 14px;
}

.answer-card.is-unanswered {
  background: #fffbeb;
  border-color: #fde68a;
}

.answer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.answer-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.answer-type {
  margin-left: 2px;
}

.answer-title {
  font-size: 14px;
  color: #1e293b;
  line-height: 1.6;
  margin-bottom: 8px;
}

.answer-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.answer-option {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 999px;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  color: #3730a3;
  font-size: 12.5px;
  font-weight: 500;
}

.option-icon {
  color: #4f46e5;
}

.answer-text {
  font-size: 13px;
  color: #334155;
  line-height: 1.6;
}

.answer-text-label {
  color: #64748b;
  font-weight: 500;
}

.answer-text-value {
  font-weight: 500;
  color: #1e293b;
}

.answer-empty {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: #b45309;
}

.answer-empty .el-icon {
  color: #f59e0b;
}

.answer-time {
  margin-top: 8px;
  font-size: 11.5px;
  color: #94a3b8;
}
</style>
