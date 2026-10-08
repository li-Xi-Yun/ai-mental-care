<template>
  <div class="scale-records-page">
    <div class="breadcrumb">
      <router-link to="/scale" class="breadcrumb-link">量表测评</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">我的测评记录</span>
    </div>

    <h2 class="page-title">我的测评记录</h2>

    <!-- 顶部统计横幅 -->
    <div class="stats-banner">
      <div class="stat-item">
        <div class="stat-value">{{ stats.totalCount }}</div>
        <div class="stat-label">测评总次数</div>
      </div>
      <div class="stat-divider" />
      <div class="stat-item">
        <div class="stat-value">{{ stats.avgScore }}</div>
        <div class="stat-label">总分均值</div>
      </div>
      <div class="stat-divider" />
      <div class="stat-item">
        <div class="stat-value">{{ stats.maxRisk }}</div>
        <div class="stat-label">最高风险等级</div>
      </div>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select
          v-model="queryParams.scaleId"
          placeholder="全部量表"
          clearable
          class="scale-select"
          @change="handleSearch"
        >
          <el-option
            v-for="scale in scaleOptions"
            :key="scale.scaleId"
            :label="scale.scaleName"
            :value="scale.scaleId"
          />
        </el-select>
        <el-radio-group v-model="queryParams.finishStatus" @change="handleSearch">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">未完成</el-radio-button>
          <el-radio-button :value="1">已完成</el-radio-button>
          <el-radio-button :value="2">中途终止</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 记录卡片列表 -->
    <el-empty v-if="!loading && records.length === 0" description="暂无测评记录" />
    <div v-else class="record-list">
      <div
        v-for="record in records"
        :key="record.recordId"
        class="record-card"
        @click="handleViewDetail(record)"
      >
        <div class="record-main">
          <div class="record-title-row">
            <div class="scale-name">{{ record.scaleName }}</div>
            <el-tag class="status-tag" :type="statusMeta(record.finishStatus).tagType" effect="light" size="small">
              {{ statusMeta(record.finishStatus).label }}
            </el-tag>
          </div>
          <div class="record-time">
            {{ statusMeta(record.finishStatus).timeLabel }}：{{ formatTime(record.endTime || record.createdTime) }}
          </div>
        </div>

        <div class="record-scores">
          <div class="score-item">
            <div class="score-label">总分</div>
            <div class="score-value">{{ formatScore(record.totalScore) }}</div>
          </div>
          <div class="score-item">
            <div class="score-label">标准分</div>
            <div class="score-value">{{ formatScore(record.standardScore) }}</div>
          </div>
          <div class="score-item">
            <div class="score-label">百分位</div>
            <div class="score-value">{{ formatPercentile(record.percentile) }}</div>
          </div>
        </div>

        <div class="record-result">
          <div
            class="result-text"
            :class="`risk-${record.riskLevel}`"
            :style="{ color: riskColor(record.riskLevel), background: riskBg(record.riskLevel) }"
          >
            {{ riskMeta(record.riskLevel).label }}
          </div>
          <div class="result-desc">{{ record.resultText || "——" }}</div>
        </div>

        <div class="record-action">
          <span class="detail-btn">{{ detailButtonText(record.finishStatus) }}</span>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="PAGINATION_LAYOUT"
        background
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { getScaleList } from "@/portal/api/scale";
import { getScaleRecords as fetchRecordsApi } from "@/portal/api/scale";
import type { ScaleVO } from "@/portal/api/scale/scale";
import type { ScaleRecordQuery, ScaleRecordVO } from "@/portal/api/scale/user-scale";
import { PAGINATION_LAYOUT } from "@/shared/api/config";
import type { TagProps } from "element-plus";
import { dayjs, FORMAT_DATETIME } from "@/shared/utils";

/* ==================== 页面数据 ==================== */
const router = useRouter();

const loading = ref(false);
const records = ref<ScaleRecordVO[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

/** 量表筛选下拉选项（取当前记录中出现的量表快照） */
const scaleOptions = ref<ScaleOption[]>([]);

interface ScaleOption {
  scaleId: number;
  scaleName: string;
}

/** 完成状态元信息：0 未完成 / 1 已完成 / 2 中途终止 */
const STATUS_META: Record<number, { label: string; tagType: TagProps["type"]; timeLabel: string }> = {
  0: { label: "未完成", tagType: "warning", timeLabel: "开始时间" },
  1: { label: "已完成", tagType: "success", timeLabel: "完成时间" },
  2: { label: "中途终止", tagType: "info", timeLabel: "最后时间" },
};

/** 风险等级元信息：0 无 / 1 低 / 2 中 / 3 高 */
const RISK_META: Record<number, { label: string }> = {
  0: { label: "正常" },
  1: { label: "低风险" },
  2: { label: "中风险" },
  3: { label: "高风险" },
};

/* ==================== 查询逻辑 ==================== */

/** 顶部统计数据：取当前页结果中用户已完成记录做轻量统计（也可改由后端聚合） */
const stats = reactive({
  totalCount: 0,
  avgScore: "0.00",
  maxRisk: "正常",
});

/** 当前查询条件 */
const queryParams = reactive<ScaleRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  scaleId: undefined,
  finishStatus: undefined,
});

/**
 * 拉取记录列表。
 * @param keepKeys 切换筛选时重置到第一页
 */
async function fetchRecords(keepKeys: boolean = false) {
  if (!keepKeys) {
    currentPage.value = 1;
    queryParams.pageNum = 1;
  }
  queryParams.pageSize = pageSize.value;

  loading.value = true;
  try {
    const res = await fetchRecordsApi({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      ...(queryParams.scaleId ? { scaleId: queryParams.scaleId } : {}),
      ...(queryParams.finishStatus !== undefined ? { finishStatus: queryParams.finishStatus } : {}),
    });
    const pageData = res.data?.data;
    records.value = pageData?.records ?? [];
    total.value = pageData?.total ?? 0;

    buildScaleOptions(pageData?.records ?? []);
    updateStats();
  } catch {
    records.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

/** 查询量表下拉选项（从分页记录中提取不重复的表量快照） */
function buildScaleOptions(list: ScaleRecordVO[]) {
  const seen = new Map<number, string>();
  list.forEach((item) => {
    if (item.scaleId && item.scaleName && !seen.has(item.scaleId)) {
      seen.set(item.scaleId, item.scaleName);
    }
  });
  // 与现有选项去重合并
  const current = new Map(scaleOptions.value.map((o) => [o.scaleId, o.scaleName]));
  seen.forEach((name, id) => {
    if (!current.has(id)) current.set(id, name);
  });
  scaleOptions.value = Array.from(current).map(([id, name]) => ({ scaleId: id, scaleName: name }));
}

/** 更新顶部统计（基于当前结果集） */
function updateStats() {
  const finished = records.value.filter((r) => r.finishStatus === 1);
  stats.totalCount = total.value;

  const scores = finished
    .map((r) => Number(r.totalScore))
    .filter((n) => !Number.isNaN(n) && n > 0);

  if (scores.length > 0) {
    const avg = scores.reduce((a, b) => a + b, 0) / scores.length;
    stats.avgScore = avg.toFixed(2);
  } else {
    stats.avgScore = "0.00";
  }

  const levels = finished.map((r) => r.riskLevel ?? 0);
  const maxLevel = levels.length > 0 ? Math.max(0, Math.min(3, ...levels)) : 0;
  stats.maxRisk = RISK_META[maxLevel]?.label ?? "正常";
}

/** 切换筛选条件（带重置页码） */
function handleSearch() {
  fetchRecords();
}

/** 分页器页码变化 */
function handlePageChange(page: number) {
  queryParams.pageNum = page;
  fetchRecords(true);
}

/** 分页器每页条数变化：重置到第一页并刷新 */
function handlePageSizeChange() {
  handleSearch();
}

/* ==================== 展示辅助 ==================== */

function statusMeta(status?: number) {
  return STATUS_META[status ?? 0] ?? STATUS_META[0];
}

function riskMeta(level?: number) {
  return RISK_META[level ?? 0] ?? RISK_META[0];
}

/** 风险等级配色：0 正常绿 / 1 低黄 / 2 中橙 / 3 高红 */
const RISK_COLORS = [
  { color: "#52c41a", bg: "#f6ffed" },
  { color: "#fa8c16", bg: "#fff7e6" },
  { color: "#fa541c", bg: "#fff2e8" },
  { color: "#ff4d4f", bg: "#fff1f0" },
];

function riskColor(level?: number) {
  const info = RISK_COLORS[level ?? 0] ?? RISK_COLORS[0];
  return info.color;
}

function riskBg(level?: number) {
  const info = RISK_COLORS[level ?? 0] ?? RISK_COLORS[0];
  return info.bg;
}

function formatScore(value?: number) {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return "—";
  return String(value);
}

function formatPercentile(value?: number) {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return "—";
  return `${value}%`;
}

function formatTime(time?: string) {
  if (!time) return "—";
  const d = dayjs(time);
  return d.isValid() ? d.format(FORMAT_DATETIME) : String(time);
}

/** 详情按钮文案：未完成时提示“继续作答” */
function detailButtonText(status?: number) {
  if (status === 0) return "继续作答 →";
  return "查看详情 →";
}

/* ==================== 跳转 ==================== */

function handleViewDetail(record: ScaleRecordVO) {
  if (record.finishStatus === 0) {
    router.push(`/scale/${record.scaleId}/answer`);
    return;
  }
  router.push(`/scale/records/${record.recordId}`);
}

/* ==================== 初始化：并行拉取量表列表与记录 ==================== */

async function init() {
  // 拉取记录（量表下拉从记录快照提取）
  await fetchRecords(true);

  // 并行拉取量表列表，丰富筛选下拉
  const listRes = await getScaleList({ pageNum: 1, pageSize: 100 }).catch(() => null);
  const list: ScaleVO[] = listRes?.data?.data?.records ?? [];
  if (list.length > 0) {
    const map = new Map<number, string>();
    list.forEach((item) => {
      if (item.id && item.scaleName) map.set(Number(item.id), item.scaleName);
    });
    const recorded = new Map(scaleOptions.value.map((o) => [o.scaleId, o.scaleName]));
    map.forEach((name, id) => recorded.set(id, name));
    scaleOptions.value = Array.from(recorded).map(([id, name]) => ({ scaleId: id, scaleName: name }));
  }
}

init();
</script>

<style scoped>
.scale-records-page {
  padding: 24px;
  max-width: 1080px;
  margin: 0 auto;
}

/* ---------- 面包屑 ---------- */
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
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

/* ---------- 标题 ---------- */
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 18px;
}

/* ---------- 统计横幅 ---------- */
.stats-banner {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  border-radius: 12px;
  padding: 20px 32px;
  margin-bottom: 20px;
  color: #fff;
  box-shadow: 0 4px 16px rgba(108, 99, 255, 0.25);
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(255, 255, 255, 0.25);
}

/* ---------- 筛选条 ---------- */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.scale-select {
  width: 200px;
}

/* ---------- 记录卡片 ---------- */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;
}

.record-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border-color: #d9d2ff;
  transform: translateY(-1px);
}

.record-main {
  flex: 1.2;
  min-width: 200px;
}

.record-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.scale-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
}

.status-tag {
  flex-shrink: 0;
}

.record-time {
  font-size: 12px;
  color: #999;
}

.record-scores {
  flex: 1;
  display: flex;
  gap: 18px;
  min-width: 180px;
}

.score-item {
  text-align: center;
}

.score-label {
  font-size: 11px;
  color: #aaa;
  margin-bottom: 2px;
}

.score-value {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  font-variant-numeric: tabular-nums;
}

.record-result {
  flex: 1.4;
  min-width: 140px;
  text-align: right;
}

.result-text {
  font-size: 15px;
  font-weight: 600;
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  margin-bottom: 4px;
}

.result-desc {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
  margin-left: auto;
}

.record-action {
  flex-shrink: 0;
}

.detail-btn {
  display: inline-block;
  font-size: 13px;
  font-weight: 500;
  color: #6c63ff;
  background: #f0eeff;
  padding: 6px 14px;
  border-radius: 16px;
  transition: background 0.2s;
}

.record-card:hover .detail-btn {
  background: #6c63ff;
  color: #fff;
}

/* ---------- 分页 ---------- */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>