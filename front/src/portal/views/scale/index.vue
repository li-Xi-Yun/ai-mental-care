<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import { useUserStore } from "@/portal/stores/user";
import { getScaleCategoryList } from "@/portal/api/scale/category";
import {
  getScaleDetail,
  getScaleList,
  type ScaleCategoryVO,
  type ScaleDetailVO,
  type ScaleVO,
} from "@/portal/api/scale/scale";
import {
  getScaleRecords,
  getUnfinishedAssessment,
  terminateAssessment,
  type ScaleRecordVO,
} from "@/portal/api/scale/user-scale";
import { dayjs, FORMAT_DATE } from "@/shared/utils";
import { SCALE_DEFAULT_PAGE_SIZE, SCALE_PAGINATION_LAYOUT } from "@/shared/api/config";

/* ==================== 类型 ==================== */

interface CategoryTab {
  key: number | "all";
  label: string;
  scaleCount: number;
}

interface ScaleTint {
  gradient: string;
  soft: string;
  fg: string;
}

/** 卡片展示模型：量表列表字段 + 详情增强字段 + 视觉元信息 */
interface ScaleCard extends ScaleVO {
  icon: string;
  tint: ScaleTint;
  dimensions: string[];
  questionTypeText: string;
  tagText?: string;
  description: string;
}

/* ==================== 状态 ==================== */

const router = useRouter();
const userStore = useUserStore();

/** 分类筛选（number = 分类 ID，"all" = 全部） */
const activeCategory = ref<number | "all">("all");
/** 关键字搜索 */
const keyword = ref("");

/** 量表列表加载中 */
const scalesLoading = ref(false);
/** 正在拉取详情的量表 ID（卡片内骨架提示） */
const expandingId = ref<number | null>(null);
/** 已加载的量表详情缓存 */
const detailMap = ref<Record<number, ScaleDetailVO | undefined>>({});
/** 详情面板展开状态 */
const expandedMap = ref<Record<number, boolean>>({});

const pageNum = ref(1);
const pageSize = ref(SCALE_DEFAULT_PAGE_SIZE);
const total = ref(0);

/** 分类总数 */
const categoryCount = ref(0);
/** 量表列表（当前页） */
const scaleList = ref<ScaleVO[]>([]);
/** 分类列表（来自接口） */
const categories = ref<ScaleCategoryVO[]>([]);
/** 最近测评记录（登录用户） */
const recentRecords = ref<ScaleRecordVO[]>([]);

/* ==================== 派生数据 ==================== */

/** 分类筛选项（"全部" + 接口分类，带各自量表数） */
const categoryTabs = computed<CategoryTab[]>(() => [
  {
    key: "all",
    label: "全部",
    scaleCount: total.value,
  },
  ...categories.value.map((c) => ({
    key: c.id,
    label: c.categoryName,
    scaleCount: c.scaleCount,
  })),
]);

/** 平台量表总数（各类别求和） */
const totalScaleCount = computed(() =>
  categories.value.reduce((sum, c) => sum + Number(c.scaleCount || 0), 0),
);

/** 卡片展示数据（合并详情缓存 + 视觉元信息） */
const scaleCards = computed<ScaleCard[]>(() =>
  scaleList.value.map((scale) => {
    const detail = detailMap.value[scale.id];
    return {
      ...scale,
      icon: iconFor(scale),
      tint: tintFor(scale.scaleCategoryId, scale.id),
      description: scale.description || "暂无量表说明，进入后请先阅读指导语。",
      dimensions: detail?.dimensions ?? [],
      questionTypeText: buildQuestionTypeText(detail),
      tagText: detail?.dimensions?.length
        ? detail.dimensions.slice(0, 3).join(" · ")
        : undefined,
    };
  }),
);

/** 最近测评记录（截取前 5 条） */
const recentRecordItems = computed(() => recentRecords.value.slice(0, 5));

/** 是否展示"我的测评记录"区块（登录后才有记录） */
const hasRecentRecords = computed(() => recentRecordItems.value.length > 0);

/* ==================== 视觉元信息 ==================== */

/** 按量表名称关键字匹配情绪图标（与设计稿一致的表情符号风格） */
const EMOJI_RULES: [RegExp, string][] = [
  [/焦虑/, "😰"],
  [/抑郁/, "😔"],
  [/睡眠|匹兹堡/, "😴"],
  [/压力|感知|知觉/, "😤"],
  [/正念|幸福感|幸福/, "🧘"],
  [/创伤|应激/, "💢"],
  [/社会支持/, "🤝"],
  [/考试|学习/, "🎓"],
  [/症状|SCL/, "🧩"],
];

function iconFor(scale: ScaleVO): string {
  const name = scale.scaleName || "";
  const matched = EMOJI_RULES.find(([rule]) => rule.test(name));
  return matched ? matched[1] : "🧾";
}

/** 渐变主题：同分类同色（按 scaleCategoryId 轮换），未知分类按量表 id 兜底 */
const PALETTES: ScaleTint[] = [
  { gradient: "linear-gradient(135deg,#ff8a80,#ff5580)", soft: "#fff1f0", fg: "#ff5580" },
  { gradient: "linear-gradient(135deg,#7c9bff,#6c63ff)", soft: "#f0f5ff", fg: "#6c63ff" },
  { gradient: "linear-gradient(135deg,#4fd18a,#2bb673)", soft: "#f0fff0", fg: "#22a35f" },
  { gradient: "linear-gradient(135deg,#ffb74d,#fb8c00)", soft: "#fff7e6", fg: "#f57f17" },
  { gradient: "linear-gradient(135deg,#3db9db,#2a8fbc)", soft: "#e6f7ff", fg: "#1890ff" },
  { gradient: "linear-gradient(135deg,#b39ddb,#7e57c2)", soft: "#f3f0ff", fg: "#7e57c2" },
];

function tintFor(categoryId: number, scaleId: number): ScaleTint {
  const base = Number.isFinite(categoryId) ? categoryId : scaleId;
  return PALETTES[Math.abs(base) % PALETTES.length];
}

/** 汇总题型统计："单选 20 · 多选 3" */
function buildQuestionTypeText(detail: ScaleDetailVO | undefined): string {
  if (!detail?.questionTypes?.length) return "";
  return detail.questionTypes
    .filter((t) => t.count > 0)
    .map((t) => `${t.questionTypeLabel} ${t.count}`)
    .join(" · ");
}

/* ==================== 数据请求 ==================== */

async function loadCategories() {
  try {
    const res = await getScaleCategoryList();
    const list = res.data?.data ?? [];
    categories.value = list;
    categoryCount.value = list.length;
  } catch {
    categories.value = [];
    categoryCount.value = 0;
  }
}

/** 切换分类 / 触发搜索：重置到第一页并重新查询 */
async function handleFilterChange() {
  pageNum.value = 1;
  await fetchScales();
}

async function updateCategory(key: number | "all") {
  if (activeCategory.value === key) return;
  activeCategory.value = key;
  await handleFilterChange();
}

function handleSearch() {
  void handleFilterChange();
}

async function fetchScales() {
  scalesLoading.value = true;
  try {
    const res = await getScaleList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(activeCategory.value !== "all"
        ? { scaleCategoryId: activeCategory.value }
        : {}),
      ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}),
    });
    const page = res.data?.data;
    scaleList.value = page?.records ?? [];
    total.value = page?.total ?? 0;
    // 换页后详情缓存与展开状态全部失效
    detailMap.value = {};
    expandedMap.value = {};
  } catch {
    scaleList.value = [];
    total.value = 0;
  } finally {
    scalesLoading.value = false;
  }
}

async function loadRecentRecords() {
  try {
    const res = await getScaleRecords({ pageNum: 1, pageSize: 5 });
    recentRecords.value = res.data?.data?.records ?? [];
  } catch {
    recentRecords.value = [];
  }
}

/** 点击卡片：首次点击懒加载详情并展开，再次点击折叠/展开详情面板 */
async function handleCardClick(scale: ScaleVO) {
  const id = scale.id;
  if (!detailMap.value[id]) {
    expandingId.value = id;
    try {
      const res = await getScaleDetail(id);
      const detail = res.data?.data;
      detailMap.value = { ...detailMap.value, [id]: detail };
      if (detail) {
        expandedMap.value = { ...expandedMap.value, [id]: true };
      }
    } catch {
      // 拉取失败保持折叠，静默降级
    } finally {
      expandingId.value = null;
    }
    return;
  }
  expandedMap.value = { ...expandedMap.value, [id]: !expandedMap.value[id] };
}

function handleStart(scale: ScaleVO) {
  void guardAndStart(scale);
}

/**
 * 开始测评入口：先查询该量表最近一条未完成记录。
 * 存在未完成记录 → 弹窗让用户选择「继续作答」或「重新开始」；否则直接进入答题页新建。
 * 游客不可作答：未登录时在当前页面弹出登录弹窗，不发送请求、不跳转。
 */
async function guardAndStart(scale: ScaleVO) {
  if (!userStore.token) {
    userStore.openLoginDialog("login");
    return;
  }
  let unfinished: { hasUnfinished: boolean; recordId?: number; startTime?: string } | undefined;
  try {
    const res = await getUnfinishedAssessment(scale.id);
    unfinished = res.data?.data ?? undefined;
  } catch {
    // 查询接口失败：不阻塞，直接按无未完成记录处理进入新建
  }

  if (!unfinished?.hasUnfinished || unfinished.recordId == null) {
    router.push(`/scale/${scale.id}/answer`);
    return;
  }

  const startText = unfinished.startTime
    ? dayjs(unfinished.startTime).isValid()
      ? dayjs(unfinished.startTime).format(FORMAT_DATE)
      : String(unfinished.startTime)
    : "—";

  try {
    await ElMessageBox.confirm(
      `检测到您有未完成的测评（上次开始于 ${startText}），是否继续作答？`,
      "继续测评",
      {
        confirmButtonText: "继续作答",
        cancelButtonText: "重新开始",
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
        type: "warning",
      },
    );
    // 继续作答：带 recordId 进入答题页，答题页走 resume 复用未完成记录
    router.push(`/scale/${scale.id}/answer?recordId=${unfinished.recordId}`);
  } catch (reason) {
    if (reason === "cancel") {
      // 重新开始：先终止旧未完成记录，再进入答题页新建
      try {
        await terminateAssessment(unfinished.recordId);
      } catch {
        // 终止失败不阻塞进入新建
      }
      router.push(`/scale/${scale.id}/answer`);
    } else if (reason === "close") {
      // 用户关闭弹窗：留在列表页
      return;
    } else {
      throw reason;
    }
  }
}

function goRecordDetail(record: ScaleRecordVO) {
  router.push(`/scale/records/${record.recordId}`);
}

/* ==================== 记录展示辅助 ==================== */

const RISK_META: Record<number, { label: string; className: string }> = {
  0: { label: "正常", className: "risk-0" },
  1: { label: "低风险", className: "risk-1" },
  2: { label: "中风险", className: "risk-2" },
  3: { label: "高风险", className: "risk-3" },
};

function riskLabel(level?: number): string {
  return RISK_META[level ?? 0]?.label ?? "正常";
}

function riskClass(level?: number): string {
  return RISK_META[level ?? 0]?.className ?? "risk-0";
}

function formatRecordTime(time?: string): string {
  if (!time) return "—";
  const d = dayjs(time);
  return d.isValid() ? d.format(FORMAT_DATE) : String(time);
}

/* ==================== 初始化 ==================== */

async function init() {
  // 游客可浏览量表列表/分类/详情（后端已放行只读接口）；「我的测评记录」是个人数据，仅登录后加载
  await Promise.all([loadCategories(), fetchScales(), userStore.token ? loadRecentRecords() : Promise.resolve()]);
}

void init();
</script>

<template>
  <div class="scale-page">
    <!-- 顶部横幅区 -->
    <section class="hero-banner">
      <div class="hero-inner">
        <div class="hero-copy">
          <div class="hero-eyebrow">专业心理量表 · 科学自评</div>
          <h1 class="hero-title">量表测评</h1>
          <p class="hero-subtitle">
            使用经过国际标准验证的专业心理量表，客观了解自己当前的心理健康状况，认真完成选项后即可获取科学解读。
          </p>
        </div>
        <div class="hero-stats">
          <div class="stat-block">
            <span class="stat-num">{{ totalScaleCount }}</span>
            <span class="stat-label">专业量表</span>
          </div>
          <div class="stat-block">
            <span class="stat-num">{{ categoryCount }}</span>
            <span class="stat-label">测评分类</span>
          </div>
          <div class="stat-block">
            <span class="stat-num">24h</span>
            <span class="stat-label">全天候陪伴</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 分类筛选 + 搜索 -->
    <section class="filter-bar">
      <div class="category-tabs">
        <div
          v-for="tab in categoryTabs"
          :key="tab.key"
          class="category-tab"
          :class="{ active: activeCategory === tab.key }"
          @click="updateCategory(tab.key)"
        >
          <span class="tab-name">{{ tab.label }}</span>
          <span class="tab-count">{{ tab.scaleCount }}</span>
        </div>
      </div>
      <div class="search-box">
        <input
          v-model="keyword"
          type="text"
          class="search-input"
          placeholder="搜索量表名称"
          @keyup.enter="handleSearch"
        />
        <button class="search-btn" type="button" @click="handleSearch">搜索</button>
      </div>
    </section>

    <!-- 量表卡片网格 -->
    <section class="scale-section">
      <div v-if="scalesLoading" class="loading-hint">
        <span class="loading-icon">⏳</span>
        正在加载量表，请稍候…
      </div>

      <el-empty
        v-else-if="scaleCards.length === 0"
        description="暂无匹配的量表，换个筛选条件试试"
      />

      <div v-else class="scale-grid">
        <div
          v-for="card in scaleCards"
          :key="card.id"
          class="scale-card"
          :class="{ expanded: expandedMap[card.id] }"
          @click="handleCardClick(card)"
        >
          <div class="card-top">
            <div class="card-icon" :style="{ background: card.tint.gradient }">
              <span class="card-icon-emoji">{{ card.icon }}</span>
            </div>
            <span v-if="card.versionNo" class="card-version">v{{ card.versionNo }}</span>
          </div>

          <div class="card-name">{{ card.scaleName }}</div>
          <div class="card-desc" :title="card.description">{{ card.description }}</div>

          <div v-if="card.tagText && !expandedMap[card.id]" class="card-preview-tag">
            <span class="preview-tag-inner">{{ card.tagText }}</span>
          </div>

          <div class="card-meta">
            <span class="meta-item">{{ card.questionCount }} 题</span>
            <span class="meta-sep">·</span>
            <span class="meta-item">约 {{ card.estimatedMinutes }} 分钟</span>
            <span v-if="card.anonymous === 1" class="meta-item anon">匿名</span>
          </div>

          <!-- 展开详情面板（懒加载自量表详情接口） -->
          <transition name="fade">
            <div v-if="expandedMap[card.id]" class="card-expanded">
              <div v-if="card.dimensions.length" class="exp-row">
                <span class="exp-label">维度</span>
                <el-tag
                  v-for="dim in card.dimensions"
                  :key="dim"
                  size="small"
                  effect="plain"
                >
                  {{ dim }}
                </el-tag>
              </div>
              <div v-if="card.questionTypeText" class="exp-row">
                <span class="exp-label">题型</span>
                <span class="exp-value">{{ card.questionTypeText }}</span>
              </div>
              <div v-if="card.allowRepeat === 0" class="exp-note">
                该量表仅可完成一次，提交后将保留结果
              </div>
              <div v-else-if="card.coolMinutes > 0" class="exp-note">
                两次测评间隔需满 {{ card.coolMinutes }} 分钟
              </div>
            </div>
          </transition>

          <div v-if="expandingId === card.id" class="card-loading-mask">
            加载中…
          </div>

          <div class="card-footer">
            <button type="button" class="start-btn" @click.stop="handleStart(card)">
              开始测评
            </button>
            <span class="detail-hint">{{ expandedMap[card.id] ? "收起" : "详情" }}</span>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="!scalesLoading && total > 0" class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :layout="SCALE_PAGINATION_LAYOUT"
          background
          @current-change="fetchScales"
        />
      </div>
    </section>

    <!-- 底部：我的测评记录快捷入口 -->
    <section v-if="hasRecentRecords" class="recent-section">
      <div class="recent-header">
        <div class="recent-title">我的测评记录</div>
        <router-link to="/scale/records" class="recent-more">全部记录 →</router-link>
      </div>

      <div class="recent-list">
        <button
          v-for="record in recentRecordItems"
          :key="record.recordId"
          type="button"
          class="recent-item"
          @click="goRecordDetail(record)"
        >
          <span class="recent-scale">{{ record.scaleName }}</span>
          <span class="recent-time">
            {{ formatRecordTime(record.endTime || record.createdTime) }}
          </span>
          <span class="recent-result" :class="riskClass(record.riskLevel)">
            {{ riskLabel(record.riskLevel) }}
          </span>
          <span class="recent-go">→</span>
        </button>
        <router-link to="/scale/records" class="recent-foot-link">
          进入「我的测评记录」查看全部历史 →
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.scale-page {
  padding: 28px 24px 48px;
  max-width: 1080px;
  margin: 0 auto;
}

/* ===== 顶部横幅 ===== */
.hero-banner {
  position: relative;
  border-radius: 18px;
  overflow: hidden;
  background: linear-gradient(120deg, #f0eeff 0%, #e8e6ff 45%, #dcd8ff 100%);
  padding: 40px 40px 32px;
  margin-bottom: 24px;
}

.hero-banner::before {
  content: "";
  position: absolute;
  right: -60px;
  top: -80px;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(108, 99, 255, 0.18), transparent 70%);
}

.hero-banner::after {
  content: "";
  position: absolute;
  right: 120px;
  bottom: -60px;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(108, 99, 255, 0.12), transparent 70%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
}

.hero-copy {
  flex: 1;
}

.hero-eyebrow {
  font-size: 12px;
  font-weight: 600;
  color: #6c63ff;
  letter-spacing: 2px;
  margin-bottom: 8px;
}

.hero-title {
  font-size: 32px;
  font-weight: 800;
  color: #1a1a2e;
  letter-spacing: -0.5px;
  margin: 0 0 10px;
}

.hero-subtitle {
  font-size: 14px;
  color: #666;
  line-height: 1.7;
  max-width: 60ch;
  margin: 0;
}

.hero-stats {
  display: flex;
  gap: 28px;
  flex-shrink: 0;
}

.stat-block {
  text-align: center;
}

.stat-num {
  display: block;
  font-size: 28px;
  font-weight: 800;
  color: #6c63ff;
  line-height: 1.1;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #888;
  margin-top: 4px;
}

/* ===== 筛选条 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.category-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.category-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 20px;
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 13px;
  color: #555;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.category-tab:hover {
  border-color: #c5bdff;
  color: #6c63ff;
}

.category-tab.active {
  background: #6c63ff;
  border-color: #6c63ff;
  color: #fff;
  box-shadow: 0 4px 12px rgba(108, 99, 255, 0.25);
}

.tab-count {
  font-size: 11px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 10px;
  padding: 0 7px;
  line-height: 16px;
}

.category-tab.active .tab-count {
  background: rgba(255, 255, 255, 0.2);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-input {
  height: 34px;
  width: 200px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 13px;
  transition: border-color 0.2s, box-shadow 0.2s;
  outline: none;
}

.search-input:focus {
  border-color: #6c63ff;
  box-shadow: 0 0 0 3px rgba(108, 99, 255, 0.12);
}

.search-btn {
  height: 34px;
  padding: 0 16px;
  border: none;
  border-radius: 8px;
  background: #6c63ff;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.search-btn:hover {
  background: #5247e8;
}

/* ===== 量表卡片网格 ===== */
.scale-section {
  margin-bottom: 28px;
}

.loading-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: #999;
  font-size: 13px;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

.scale-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.scale-card {
  position: relative;
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  border: 1px solid #eef0f4;
  cursor: pointer;
  transition: box-shadow 0.25s, transform 0.25s, border-color 0.25s;
  overflow: hidden;
}

.scale-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #6c63ff, #9e98ff);
  opacity: 0;
  transition: opacity 0.25s;
}

.scale-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 30px rgba(108, 99, 255, 0.12);
  border-color: #d9d2ff;
}

.scale-card:hover::before {
  opacity: 1;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.card-icon {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.12);
}

.card-icon-emoji {
  line-height: 1;
}

.card-version {
  font-size: 11px;
  color: #999;
  background: #f5f5f7;
  padding: 2px 8px;
  border-radius: 10px;
}

.card-name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 6px;
}

.card-desc {
  font-size: 12px;
  color: #999;
  line-height: 1.6;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 38px;
}

.card-preview-tag {
  margin-bottom: 10px;
}

.preview-tag-inner {
  display: inline-block;
  font-size: 11px;
  color: #6c63ff;
  background: #f0eeff;
  padding: 2px 8px;
  border-radius: 10px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #888;
  margin-bottom: 14px;
}

.meta-item {
  font-variant-numeric: tabular-nums;
}

.meta-sep {
  color: #d9d9d9;
}

.anon {
  color: #6c63ff;
  background: #f0eeff;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
}

/* ===== 详情面板 ===== */
.card-expanded {
  background: #faf9ff;
  border: 1px dashed #d9d2ff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}

.exp-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.exp-row:last-child {
  margin-bottom: 0;
}

.exp-label {
  flex-shrink: 0;
  font-size: 12px;
  color: #999;
}

.exp-value {
  font-size: 12px;
  color: #333;
}

.exp-note {
  font-size: 12px;
  color: #fa8c16;
  margin-top: 8px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.card-loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.88);
  font-size: 12px;
  color: #6c63ff;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.start-btn {
  border: none;
  background: linear-gradient(135deg, #6c63ff, #877bef);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  padding: 8px 20px;
  border-radius: 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.start-btn:hover {
  box-shadow: 0 6px 16px rgba(108, 99, 255, 0.35);
  transform: translateY(-1px);
}

.detail-hint {
  font-size: 12px;
  color: #6c63ff;
  background: #f0eeff;
  padding: 5px 12px;
  border-radius: 14px;
}

/* ===== 分页 ===== */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

/* ===== 最近记录 ===== */
.recent-section {
  background: #fff;
  border-radius: 14px;
  border: 1px solid #eef0f4;
  padding: 20px 24px;
}

.recent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.recent-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
}

.recent-more {
  font-size: 13px;
  color: #6c63ff;
  text-decoration: none;
}

.recent-list {
  display: flex;
  flex-direction: column;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: none;
  border-bottom: 1px solid #f5f5f7;
  padding: 11px 4px;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s;
}

.recent-item:hover {
  background: #faf9ff;
}

.recent-scale {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  flex-shrink: 0;
  font-size: 12px;
  color: #999;
}

.recent-result {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
  min-width: 56px;
  text-align: center;
}

.recent-result.risk-0 {
  color: #52c41a;
  background: #f6ffed;
}

.recent-result.risk-1 {
  color: #fa8c16;
  background: #fff7e6;
}

.recent-result.risk-2 {
  color: #fa541c;
  background: #fff2e8;
}

.recent-result.risk-3 {
  color: #ff4d4f;
  background: #fff1f0;
}

.recent-go {
  color: #bbb;
  font-size: 14px;
}

.recent-item:hover .recent-go {
  color: #6c63ff;
}

.recent-foot-link {
  display: inline-block;
  margin-top: 12px;
  font-size: 13px;
  color: #6c63ff;
  text-decoration: none;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>