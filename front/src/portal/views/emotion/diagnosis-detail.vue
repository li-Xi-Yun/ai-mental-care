<template>
  <div class="diagnosis-detail-page">
    <div class="breadcrumb">
      <router-link to="/diagnosis" class="breadcrumb-link">心理诊断</router-link>
      <span class="breadcrumb-sep">›</span>
      <router-link :to="`/diagnosis/${sessionId}`" class="breadcrumb-link">诊断记录</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">诊断详情</span>
    </div>

    <!-- 游客态：诊断详情是个人数据，登录后自动加载 -->
    <template v-if="!userStore.token">
      <el-empty description="登录后即可查看诊断详情">
        <el-button type="primary" @click="userStore.openLoginDialog('login')">去登录</el-button>
      </el-empty>
    </template>

    <template v-else-if="loading">
      <div class="skeleton">
        <div class="sk-line" style="width: 60%"></div>
        <div class="sk-line" style="width: 100%"></div>
        <div class="sk-line" style="width: 85%"></div>
        <div class="sk-line" style="width: 40%"></div>
      </div>
    </template>

    <template v-else-if="loadError">
      <div class="error-box">
        <el-empty description="诊断报告加载失败，请稍后重试" />
        <el-button type="primary" @click="loadDiagnosis">重新加载</el-button>
      </div>
    </template>

    <template v-else-if="diagnosis">
      <!-- 顶部面包屑 + 核心情绪徽章 -->
      <header class="report-header">
        <div class="hero">
          <div class="hero-info">
            <h1 class="hero-title">诊断详情</h1>
            <p class="hero-sub">
              {{ roundNumText }} · 创建 {{ formatDateTime(diagnosis.createdTime) }} · 更新
              {{ formatDateTime(diagnosis.updatedTime) }}
            </p>
          </div>
          <div class="core-badge">
            <span class="core-badge-dot"></span>
            <div class="core-badge-text">
              <span class="core-badge-label">核心情绪</span>
              <strong class="core-badge-value">{{ diagnosis.coreEmotionLabel ?? "--" }}</strong>
            </div>
            <div class="core-badge-meta">
              <div>置信度 {{ formatPercent(diagnosis.coreEmotionConfAvg) }}</div>
              <div>强度 {{ formatPercent(diagnosis.coreEmotionIntensityScore) }}</div>
            </div>
          </div>
        </div>
      </header>

      <!-- 关键指标 stat 卡片区 -->
      <section class="stat-strip">
        <div class="stat-card" v-for="s in statCards" :key="s.label">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-sub">{{ s.sub }}</div>
        </div>
      </section>

      <!-- 诊断摘要 -->
      <section v-if="diagnosis.diagnosisContent" class="panel">
        <div class="panel-title"><span class="panel-icon">📝</span>诊断摘要</div>
        <p class="para summary-text">{{ diagnosis.diagnosisContent }}</p>
      </section>

      <!-- 情绪占比分析 + 情绪趋势 -->
      <div class="grid-2">
        <section class="panel">
          <div class="panel-title"><span class="panel-icon">🎭</span>情绪占比分析</div>
          <div v-if="emotionSegments.length" class="emotion-wrap">
            <div class="ring-box">
              <svg viewBox="0 0 100 100" class="ring-svg">
                <circle
                  cx="50"
                  cy="50"
                  :r="RING_RADIUS"
                  fill="none"
                  stroke="#f0f0f7"
                  stroke-width="12"
                />
                <circle
                  v-for="(seg, i) in emotionSegments"
                  :key="seg.label"
                  cx="50"
                  cy="50"
                  :r="RING_RADIUS"
                  fill="none"
                  :stroke="emotionColor(seg.label, i)"
                  stroke-width="12"
                  :stroke-dasharray="`${(seg.percent / 100) * RING_CIRCUMFERENCE} ${RING_CIRCUMFERENCE}`"
                  :stroke-dashoffset="`${-(seg.start / 100) * RING_CIRCUMFERENCE}`"
                  stroke-linecap="butt"
                />
              </svg>
              <div class="ring-center">
                <span class="ring-center-label">核心情绪</span>
                <span class="ring-center-value">{{ diagnosis.coreEmotionLabel ?? "--" }}</span>
                <span class="ring-center-sub">{{ coreEmotionShare }}%</span>
              </div>
            </div>
            <div class="legend">
              <div class="legend-item" v-for="(e, i) in emotionEntries.slice(0, 5)" :key="e.label">
                <span class="legend-dot" :style="{ backgroundColor: emotionColor(e.label, i) }"></span>
                <span class="legend-name">{{ e.label }}</span>
                <span class="legend-pct">{{ toPercent(e.value).toFixed(0) }}%</span>
              </div>
            </div>
          </div>
          <div v-else class="no-emotion">暂无情绪占比数据</div>
          <div v-if="ratioSummary.length" class="ratio-summary">
            <span class="ratio-item" v-for="r in ratioSummary" :key="r.label">
              <i class="ratio-dot" :style="{ backgroundColor: r.color }"></i>
              {{ r.label }}
              {{ r.value != null ? `${(r.value * 100).toFixed(0)}%` : "--" }}
            </span>
          </div>
        </section>

        <section class="panel">
          <div class="panel-title"><span class="panel-icon">📈</span>情绪趋势</div>
          <div class="trend-badge">
            <span class="trend-icon">{{ trendMeta.icon }}</span>
            <span class="trend-text">整体情绪{{ trendMeta.text }}</span>
            <el-tag :type="trendMeta.type" size="small" effect="light">{{ trendMeta.text }}</el-tag>
          </div>
          <div class="trend-grid">
            <div class="trend-item">
              <div class="trend-label">峰值轮次</div>
              <div class="trend-value">第{{ diagnosis.emotionPeakRound ?? "--" }}轮</div>
            </div>
            <div class="trend-item">
              <div class="trend-label">低谷轮次</div>
              <div class="trend-value">第{{ diagnosis.emotionValleyRound ?? "--" }}轮</div>
            </div>
            <div class="trend-item">
              <div class="trend-label">波动幅度</div>
              <div class="trend-value">{{ formatNumber(diagnosis.emotionFluctuationAmplitude) }}</div>
            </div>
            <div class="trend-item">
              <div class="trend-label">稳定轮次</div>
              <div class="trend-value">{{ diagnosis.emotionStableRounds ?? "--" }} 轮</div>
            </div>
          </div>
        </section>
      </div>

      <!-- 核心情绪分布 -->
      <section v-if="emotionEntries.length" class="panel">
        <div class="panel-title"><span class="panel-icon">📊</span>核心情绪分布</div>
        <div class="bar-row" v-for="(e, i) in emotionEntries" :key="e.label">
          <span class="bar-name">{{ e.label }}</span>
          <div class="bar-track">
            <div
              class="bar-fill"
              :style="{
                width: `${emotionTotal ? (e.value / emotionTotal) * 100 : 0}%`,
                backgroundColor: emotionColor(e.label, i),
              }"
            ></div>
          </div>
          <span class="bar-value">{{ toPercent(e.value).toFixed(0) }}%</span>
        </div>
      </section>

      <!-- PAD 情绪维度 -->
      <section class="panel">
        <div class="panel-title"><span class="panel-icon">🎯</span>PAD 情绪维度</div>
        <div class="pad-grid">
          <div class="pad-card" v-for="item in padItems" :key="item.name">
            <div class="pad-head">
              <div>
                <div class="pad-name">{{ item.name }} · {{ item.label }}</div>
                <div class="pad-value" :style="{ color: item.color }">{{ signed(item.mean) }}</div>
              </div>
              <el-tag size="small" :type="padStdTagType(item)" effect="light">
                σ {{ item.std != null ? item.std.toFixed(2) : "--" }}
              </el-tag>
            </div>
            <div class="pad-track-labels"><span>-1</span><span>0</span><span>+1</span></div>
            <div class="pad-bar">
              <div class="pad-zero"></div>
              <div class="pad-fill" :style="padBarStyle(item)"></div>
            </div>
            <div class="pad-sub">{{ padStdText(item) }}</div>
          </div>
        </div>
      </section>

      <!-- 触发因素 + 风险评估 -->
      <div class="grid-2">
        <section class="panel">
          <div class="panel-title"><span class="panel-icon">⚡</span>触发因素</div>
          <div class="trigger-scene">
            <span class="trigger-icon">📍</span>
            <div>
              <div class="kv-label">核心触发场景</div>
              <div class="trigger-scene-value">{{ diagnosis.coreTriggerScene ?? "--" }}</div>
            </div>
          </div>
          <div class="trigger-meta">
            <span class="kv-label">首次触发轮次</span>
            <span class="trigger-meta-value">
              {{ diagnosis.triggerRoundNum ? `第${diagnosis.triggerRoundNum}轮` : "--" }}
            </span>
          </div>
          <div v-if="triggerKeywords.length" class="tag-block">
            <span class="kv-label">触发关键词</span>
            <div class="tag-list">
              <el-tag v-for="kw in triggerKeywords" :key="kw" size="small" effect="plain" class="kw-tag">{{ kw }}</el-tag>
            </div>
          </div>
          <p v-if="diagnosis.firstTriggerDesc" class="para trigger-desc">{{ diagnosis.firstTriggerDesc }}</p>
        </section>

        <section class="panel">
          <div class="panel-title"><span class="panel-icon">⚠️</span>风险评估</div>
          <div class="kv-grid">
            <div class="kv-item" v-for="r in riskItems" :key="r.label">
              <span class="kv-label">{{ r.label }}</span>
              <el-tag :type="r.type" size="small" effect="light">{{ r.text }}</el-tag>
            </div>
          </div>
          <p v-if="diagnosis.riskDetail" class="para risk-detail">{{ diagnosis.riskDetail }}</p>
        </section>
      </div>

      <!-- 症状与心理状态 + 社会功能评估 -->
      <div class="grid-2">
        <section class="panel">
          <div class="panel-title"><span class="panel-icon">🏥</span>症状与心理状态</div>
          <div class="kv-grid">
            <div class="kv-item">
              <span class="kv-label">心理状态</span>
              <strong class="kv-strong">{{ diagnosis.psychologicalState ?? "--" }}</strong>
            </div>
            <div class="kv-item">
              <span class="kv-label">持续时长</span>
              <span>{{ diagnosis.symptomDuration ?? "--" }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-label">发作模式</span>
              <span>{{ diagnosis.onsetPattern ?? "--" }}</span>
            </div>
          </div>
          <p v-if="diagnosis.symptomSummary" class="para">{{ diagnosis.symptomSummary }}</p>
          <div v-if="symptomTagList.length" class="tag-block">
            <span class="kv-label">症状标签</span>
            <div class="tag-list">
              <el-tag v-for="t in symptomTagList" :key="t" size="small" effect="light" type="warning">{{ t }}</el-tag>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-title"><span class="panel-icon">🌐</span>社会功能评估</div>
          <div class="kv-grid">
            <div class="kv-item">
              <span class="kv-label">社会功能</span>
              <el-tag :type="socialImpactType" size="small" effect="light">
                {{ diagnosis.socialFunctionImpact ?? "--" }}
              </el-tag>
            </div>
            <div class="kv-item">
              <span class="kv-label">社会支持</span>
              <span>{{ socialSupportText }}</span>
            </div>
          </div>
          <p v-if="diagnosis.impactDomains" class="para">影响领域：{{ diagnosis.impactDomains }}</p>
          <p v-if="diagnosis.dailyLifeInfluence" class="para">{{ diagnosis.dailyLifeInfluence }}</p>
          <div v-if="protectiveFactorsList.length" class="tag-block">
            <span class="kv-label">保护因素</span>
            <div class="tag-list">
              <el-tag v-for="t in protectiveFactorsList" :key="t" size="small" effect="plain" type="success">{{ t }}</el-tag>
            </div>
          </div>
        </section>
      </div>

      <!-- AI 建议三栏 -->
      <section class="panel">
        <div class="panel-title"><span class="panel-icon">💡</span>AI 建议</div>
        <div class="advice-grid">
          <div class="advice-card" :class="{ hot: suggestionPriority === 1 }">
            <div class="advice-head">
              <span class="advice-icon">🧘</span>
              <span class="advice-title">自助调节</span>
            </div>
            <p>{{ diagnosis.selfHelpSuggestion || "暂无建议" }}</p>
          </div>
          <div class="advice-card" :class="{ hot: suggestionPriority === 2 }">
            <div class="advice-head">
              <span class="advice-icon">🤝</span>
              <span class="advice-title">社会支持</span>
            </div>
            <p>{{ diagnosis.socialSupportSuggestion || "暂无建议" }}</p>
          </div>
          <div class="advice-card" :class="{ hot: suggestionPriority === 3 }">
            <div class="advice-head">
              <span class="advice-icon">🩺</span>
              <span class="advice-title">专业干预</span>
            </div>
            <p>{{ diagnosis.professionalInterveneSuggestion || "暂无建议" }}</p>
          </div>
        </div>
        <div class="priority-banner" :class="priorityMeta.type">
          <span class="priority-icon">💡</span>
          <span>建议优先级：<strong>{{ priorityMeta.text }}</strong></span>
        </div>
      </section>

      <!-- 我的反馈 -->
      <section class="panel">
        <div class="panel-title"><span class="panel-icon">✍️</span>我的反馈</div>

        <div v-if="feedback && !editing" class="fb-summary">
          <div class="fb-stars">
            <el-rate :model-value="feedback.diagnosisScore ?? 0" disabled />
            <span class="fb-score-text">{{ feedback.diagnosisScore ? `${feedback.diagnosisScore} / 5` : "未评分" }}</span>
          </div>
          <div class="fb-agree-grid">
            <div class="fb-agree-item">
              <span>风险评估</span>
              <el-tag :type="agreeTagType(feedback.agreeRiskJudge)" size="small" effect="light">
                {{ agreeText(feedback.agreeRiskJudge) }}
              </el-tag>
            </div>
            <div class="fb-agree-item">
              <span>自助建议</span>
              <el-tag :type="agreeTagType(feedback.agreeSuggestionSelf)" size="small" effect="light">
                {{ agreeText(feedback.agreeSuggestionSelf) }}
              </el-tag>
            </div>
            <div class="fb-agree-item">
              <span>社会支持建议</span>
              <el-tag :type="agreeTagType(feedback.agreeSuggestionSocial)" size="small" effect="light">
                {{ agreeText(feedback.agreeSuggestionSocial) }}
              </el-tag>
            </div>
            <div class="fb-agree-item">
              <span>专业干预建议</span>
              <el-tag :type="agreeTagType(feedback.agreeSuggestionProfessional)" size="small" effect="light">
                {{ agreeText(feedback.agreeSuggestionProfessional) }}
              </el-tag>
            </div>
          </div>
          <div class="fb-use">
            <span class="kv-label">建议采纳</span>
            <strong>{{ useSuggestionText(feedback.useSuggestion) }}</strong>
          </div>
          <p v-if="feedback.feedbackContent" class="fb-content">“{{ feedback.feedbackContent }}”</p>
          <div class="fb-actions">
            <el-button type="primary" size="small" @click="startEdit">编辑反馈</el-button>
          </div>
        </div>

        <div v-else class="fb-form">
          <div class="fb-field">
            <label class="fb-label">诊断评分</label>
            <el-rate v-model="form.diagnosisScore" :max="5" show-score :allow-half="false" />
          </div>
          <div class="fb-field">
            <label class="fb-label">建议认同</label>
            <div class="fb-check-list">
              <el-checkbox v-model="form.agreeRisk">认同风险评估</el-checkbox>
              <el-checkbox v-model="form.agreeSelf">认同自助调节建议</el-checkbox>
              <el-checkbox v-model="form.agreeSocial">认同社会支持建议</el-checkbox>
              <el-checkbox v-model="form.agreePro">认同专业干预建议</el-checkbox>
            </div>
          </div>
          <div class="fb-field">
            <label class="fb-label">建议采纳情况</label>
            <el-radio-group
              :model-value="form.useSuggestion ?? undefined"
              @change="onUseSuggestionChange"
            >
              <el-radio-button :value="0">未采纳</el-radio-button>
              <el-radio-button :value="1">尝试部分</el-radio-button>
              <el-radio-button :value="2">全部采纳</el-radio-button>
            </el-radio-group>
          </div>
          <div class="fb-field">
            <label class="fb-label">文字反馈</label>
            <el-input
              v-model="form.feedbackContent"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="写下你对本次诊断的看法或建议..."
            />
          </div>
          <div class="fb-actions">
            <el-button v-if="editing" @click="editing = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitFeedback">
              {{ feedback ? "保存修改" : "提交反馈" }}
            </el-button>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import type { CSSProperties } from "vue";
import { useRoute } from "vue-router";
import { useUserStore } from "@/portal/stores/user";
import { ElMessage } from "element-plus";
import dayjs from "@/shared/utils/dayjs";
import { EMOTION_COLORS } from "@/shared/api/config";
import {
  getEmotionDiagnosisDetail,
  getDiagnosisFeedback,
  submitDiagnosisFeedback,
} from "@/portal/api/conversation/emotion-diagnosis";
import type { AssessmentFeedbackVO, EmotionDiagnosisVO } from "@/portal/api/conversation/emotion-diagnosis";

const route = useRoute();
const userStore = useUserStore();
const sessionId = route.params.sessionId as string;
const diagnosisId = route.params.diagnosisId as string;

const loading = ref(true);
const loadError = ref(false);
const submitting = ref(false);
const editing = ref(false);
const diagnosis = ref<EmotionDiagnosisVO | null>(null);
const feedback = ref<AssessmentFeedbackVO | null>(null);

const RING_RADIUS = 40;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

/* ==================== 数据加载 ==================== */

async function loadDiagnosis() {
  loading.value = true;
  loadError.value = false;
  try {
    const res = await getEmotionDiagnosisDetail(diagnosisId);
    diagnosis.value = res.data.data ?? null;
    if (!diagnosis.value) loadError.value = true;
  } catch {
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

async function loadFeedback() {
  try {
    const res = await getDiagnosisFeedback(diagnosisId);
    feedback.value = res.data.data ?? null;
  } catch {
    feedback.value = null;
  }
}

/* ==================== 初始化 ==================== */

async function init() {
  // 诊断详情是个人数据：游客不可查看，未登录时在当前页面弹登录窗，不发请求
  if (!userStore.token) {
    loading.value = false; // 游客态不使用骨架
    userStore.openLoginDialog("login");
    return;
  }
  await Promise.all([loadDiagnosis(), loadFeedback()]);
}

// 游客态经弹窗登录成功后自动加载
watch(
  () => userStore.token,
  (t) => {
    if (t && !diagnosis.value && !loading.value) void init();
  },
);

onMounted(() => {
  void init();
});

/* ==================== 格式化工具 ==================== */

function toNumber(v?: number | null): number | null {
  if (v == null) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function formatPercent(v?: number | null, digits = 1): string {
  const n = toNumber(v);
  return n === null ? "--" : `${(n * 100).toFixed(digits)}%`;
}

function formatNumber(v?: number | null): string {
  const n = toNumber(v);
  return n === null ? "--" : n.toFixed(2);
}

function toPercent(v?: number | null): number {
  const n = toNumber(v);
  if (n === null) return 0;
  return n <= 1 ? n * 100 : n;
}

function signed(v?: number | null): string {
  const n = toNumber(v);
  if (n === null) return "--";
  return `${n > 0 ? "+" : ""}${n.toFixed(2)}`;
}

function formatDateTime(v?: string): string {
  if (!v) return "--";
  const d = dayjs(v);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(v);
}

function splitTags(s?: string): string[] {
  if (!s) return [];
  return s
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean);
}

/* ==================== 情绪相关 ==================== */

const FALLBACK_COLORS = [
  "#6c63ff",
  "#ff6b6b",
  "#fa8c16",
  "#52c41a",
  "#1890ff",
  "#13c2c2",
  "#722ed1",
  "#eb2f96",
  "#a0d911",
  "#ff7a45",
];

function emotionColor(label: string, index: number): string {
  return EMOTION_COLORS[label] ?? FALLBACK_COLORS[index % FALLBACK_COLORS.length];
}

const emotionEntries = computed(() => {
  const map = diagnosis.value?.coreEmotion ?? {};
  return Object.entries(map)
    .map(([label, value]) => ({ label, value: Number(value) || 0 }))
    .sort((a, b) => b.value - a.value);
});

const emotionTotal = computed(() => emotionEntries.value.reduce((sum, e) => sum + e.value, 0));

const emotionSegments = computed(() => {
  const total = emotionTotal.value || 1;
  let acc = 0;
  return emotionEntries.value.map((e) => {
    const start = (acc / total) * 100;
    acc += e.value;
    const end = (acc / total) * 100;
    return { ...e, percent: total ? (e.value / total) * 100 : 0, start, end };
  });
});

const coreEmotionShare = computed(() => {
  const label = diagnosis.value?.coreEmotionLabel;
  const seg = emotionSegments.value.find((s) => s.label === label);
  return seg ? seg.percent.toFixed(0) : "0";
});

const ratioSummary = computed(() => {
  const d = diagnosis.value;
  return [
    { label: "负向情绪", value: toNumber(d?.negativeEmotionRatio), color: "#ff6b6b" },
    { label: "中性情绪", value: toNumber(d?.neutralEmotionRatio), color: "#6c63ff" },
    { label: "正向情绪", value: toNumber(d?.positiveEmotionRatio), color: "#52c41a" },
  ];
});

/* ==================== 顶部指标 ==================== */

const TREND_META: Record<number, { text: string; icon: string; type: "success" | "warning" | "danger" | "info" }> = {
  0: { text: "上升", icon: "↑", type: "danger" },
  1: { text: "下降", icon: "↓", type: "success" },
  2: { text: "平稳", icon: "→", type: "info" },
  3: { text: "无法判断", icon: "·", type: "info" },
};

const EMOTION_RISK_META: Record<number, { text: string; type: "success" | "warning" | "danger" | "info" }> = {
  0: { text: "低", type: "success" },
  1: { text: "中", type: "warning" },
  2: { text: "高", type: "danger" },
  3: { text: "危急", type: "danger" },
  4: { text: "无法判断", type: "info" },
};

const HARM_RISK_META: Record<number, { text: string; type: "success" | "warning" | "danger" | "info" }> = {
  0: { text: "无", type: "success" },
  1: { text: "低", type: "success" },
  2: { text: "中", type: "warning" },
  3: { text: "高", type: "danger" },
  4: { text: "极高", type: "danger" },
  5: { text: "无法判断", type: "info" },
};

const roundNumText = computed(() => (diagnosis.value?.roundNum ? `第${diagnosis.value.roundNum}轮` : "--"));

const trendMeta = computed(
  () => TREND_META[diagnosis.value?.emotionTrend ?? 3] ?? TREND_META[3],
);

const emotionRiskMeta = computed(
  () => EMOTION_RISK_META[diagnosis.value?.emotionRiskLevel ?? 4] ?? EMOTION_RISK_META[4],
);

const selfHarmRiskMeta = computed(
  () => HARM_RISK_META[diagnosis.value?.selfHarmRiskLevel ?? 5] ?? HARM_RISK_META[5],
);

const suicideRiskMeta = computed(
  () => HARM_RISK_META[diagnosis.value?.suicideRiskLevel ?? 5] ?? HARM_RISK_META[5],
);

const crisisText = computed(() => {
  const v = diagnosis.value?.crisisWarning;
  return v === 1 ? "已触发" : v === 0 ? "未触发" : "--";
});

const statCards = computed(() => {
  const d = diagnosis.value;
  return [
    {
      label: "核心情绪",
      value: d?.coreEmotionLabel ?? "--",
      sub: `置信度 ${formatPercent(d?.coreEmotionConfAvg)}`,
    },
    {
      label: "风险等级",
      value: emotionRiskMeta.value.text,
      sub: `危机预警 ${crisisText.value}`,
    },
    {
      label: "情绪稳定性",
      value: formatPercent(d?.emotionStabilityScore),
      sub: `峰值 第${d?.emotionPeakRound ?? "--"}轮`,
    },
    {
      label: "PAD均值",
      value: `P ${signed(d?.avgP)}`,
      sub: `A ${signed(d?.avgA)} · D ${signed(d?.avgD)}`,
    },
  ];
});

const riskItems = computed(() => {
  const d = diagnosis.value;
  return [
    { label: "情绪风险", text: emotionRiskMeta.value.text, type: emotionRiskMeta.value.type as "success" | "warning" | "danger" | "info" },
    { label: "自伤风险", text: selfHarmRiskMeta.value.text, type: selfHarmRiskMeta.value.type as "success" | "warning" | "danger" | "info" },
    { label: "自杀风险", text: suicideRiskMeta.value.text, type: suicideRiskMeta.value.type as "success" | "warning" | "danger" | "info" },
    {
      label: "危机预警",
      text: crisisText.value,
      type: (d?.crisisWarning === 1 ? "danger" : "success") as "success" | "warning" | "danger" | "info",
    },
    {
      label: "人工干预",
      text: d?.needManualIntervene === 1 ? "需要" : d?.needManualIntervene === 0 ? "暂不需要" : "--",
      type: (d?.needManualIntervene === 1 ? "warning" : "info") as "success" | "warning" | "danger" | "info",
    },
  ];
});

/* ==================== PAD 维度 ==================== */

interface PadItem {
  name: string;
  label: string;
  mean: number | null;
  std: number | null;
  color: string;
}

const padItems = computed<PadItem[]>(() => {
  const d = diagnosis.value;
  return [
    { name: "P", label: "愉悦度 Pleasure", mean: toNumber(d?.avgP), std: toNumber(d?.stdP), color: "#6c63ff" },
    { name: "A", label: "唤醒度 Arousal", mean: toNumber(d?.avgA), std: toNumber(d?.stdA), color: "#fa8c16" },
    { name: "D", label: "支配度 Dominance", mean: toNumber(d?.avgD), std: toNumber(d?.stdD), color: "#52c41a" },
  ];
});

function padBarStyle(item: PadItem): CSSProperties {
  const v = item.mean ?? 0;
  const width = Math.abs(v) * 50;
  const left = v >= 0 ? 50 : 50 - width;
  return {
    left: `${left}%`,
    width: `${width}%`,
    backgroundColor: v >= 0 ? "#6c63ff" : "#ff6b6b",
  };
}

function padStdTagType(item: PadItem): "success" | "warning" | "danger" | "info" {
  if (item.std == null) return "info";
  if (item.std >= 0.3) return "warning";
  return "success";
}

function padStdText(item: PadItem): string {
  if (item.std == null) return "标准差缺失，无法评估波动";
  if (item.std >= 0.3) return "波动较强，情绪起伏明显";
  if (item.std >= 0.15) return "波动中等，情绪较为稳定";
  return "波动较小，情绪非常稳定";
}

/* ==================== 症状与社会功能 ==================== */

const symptomTagList = computed(() => splitTags(diagnosis.value?.symptomTags));
const triggerKeywords = computed(() => splitTags(diagnosis.value?.coreTriggerKeywords));
const protectiveFactorsList = computed(() => splitTags(diagnosis.value?.protectiveFactors));

const socialImpactType = computed<"success" | "warning" | "danger" | "info">(() => {
  const v = diagnosis.value?.socialFunctionImpact ?? "";
  if (v.includes("重")) return "danger";
  if (v.includes("中")) return "warning";
  if (v.includes("无") || v.includes("正常")) return "success";
  return "info";
});

const socialSupportText = computed(() => {
  const map: Record<number, string> = { 0: "良好", 1: "一般", 2: "较差", 3: "匮乏", 4: "无法判断" };
  const v = diagnosis.value?.socialSupportLevel;
  return v != null ? map[v] ?? "无法判断" : "--";
});

/* ==================== AI 建议优先级 ==================== */

const PRIORITY_META: Record<number, { text: string; type: "success" | "warning" | "danger" | "info" }> = {
  1: { text: "自助调节为主", type: "success" },
  2: { text: "建议寻求支持", type: "warning" },
  3: { text: "强烈建议专业干预", type: "danger" },
  4: { text: "无法判断", type: "info" },
};

const suggestionPriority = computed(() => diagnosis.value?.suggestionPriority ?? 4);
const priorityMeta = computed(() => PRIORITY_META[suggestionPriority.value] ?? PRIORITY_META[4]);

/* ==================== 我的反馈 ==================== */

const form = reactive({
  diagnosisScore: 0,
  agreeRisk: false,
  agreeSelf: false,
  agreeSocial: false,
  agreePro: false,
  useSuggestion: null as number | null,
  feedbackContent: "" as string | null,
});

function startEdit() {
  const f = feedback.value;
  form.diagnosisScore = f?.diagnosisScore ?? 0;
  form.agreeRisk = f?.agreeRiskJudge === 1;
  form.agreeSelf = f?.agreeSuggestionSelf === 1;
  form.agreeSocial = f?.agreeSuggestionSocial === 1;
  form.agreePro = f?.agreeSuggestionProfessional === 1;
  form.useSuggestion = f?.useSuggestion ?? null;
  form.feedbackContent = f?.feedbackContent ?? "";
  editing.value = true;
}

function onUseSuggestionChange(val: string | number | boolean | undefined) {
  form.useSuggestion = val == null ? null : Number(val) || null;
}

async function submitFeedback() {
  submitting.value = true;
  try {
    await submitDiagnosisFeedback(diagnosisId, {
      diagnosisScore: form.diagnosisScore > 0 ? form.diagnosisScore : null,
      feedbackContent: (form.feedbackContent ?? "").trim() || null,
      agreeRiskJudge: form.agreeRisk ? 1 : 0,
      agreeSuggestionSelf: form.agreeSelf ? 1 : 0,
      agreeSuggestionSocial: form.agreeSocial ? 1 : 0,
      agreeSuggestionProfessional: form.agreePro ? 1 : 0,
      useSuggestion: form.useSuggestion,
    });
    ElMessage.success("反馈提交成功，感谢你的参与");
    editing.value = false;
    await loadFeedback();
  } catch {
    ElMessage.error("反馈提交失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
}

function agreeText(v?: number | null): string {
  if (v == null) return "未反馈";
  return v === 1 ? "认同" : "不认同";
}

function agreeTagType(v?: number | null): "success" | "warning" | "danger" | "info" {
  if (v == null) return "info";
  return v === 1 ? "success" : "danger";
}

function useSuggestionText(v?: number | null): string {
  if (v == null) return "未反馈";
  return ["未采纳", "尝试部分", "全部采纳"][v] ?? "未反馈";
}
</script>

<style scoped>
.diagnosis-detail-page {
  max-width: 980px;
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
  margin-bottom: 16px;
}

.breadcrumb-link {
  color: #6c63ff;
  text-decoration: none;
  transition: color 0.2s;
}

.breadcrumb-link:hover {
  color: #3f3d9e;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

/* ==================== 顶部英雄区 ==================== */

.report-header {
  margin-bottom: 16px;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
  background: linear-gradient(135deg, #f0eeff 0%, #f7f6ff 60%, #ffffff 100%);
  border: 1px solid #e6e3ff;
  border-radius: 16px;
}

.hero-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.hero-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: #999;
}

.core-badge {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-radius: 999px;
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  color: #fff;
  box-shadow: 0 4px 14px rgba(108, 99, 255, 0.35);
  flex-shrink: 0;
}

.core-badge-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.25);
  animation: pulse 2s infinite;
  flex-shrink: 0;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

.core-badge-label {
  display: block;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
}

.core-badge-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
}

.core-badge-meta {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  text-align: right;
  line-height: 1.6;
  flex-shrink: 0;
}

/* ==================== 指标卡片 ==================== */

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  padding: 16px 18px;
  transition: box-shadow 0.2s, transform 0.2s;
}

.stat-card:hover {
  box-shadow: 0 6px 18px rgba(26, 26, 46, 0.06);
  transform: translateY(-2px);
}

.stat-label {
  font-size: 12px;
  color: #9aa0b5;
}

.stat-value {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.2;
}

.stat-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #b0b6c4;
}

/* ==================== 面板 ==================== */

.panel {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  padding: 20px;
  margin-bottom: 16px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 16px;
}

.panel-icon {
  font-size: 16px;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.para {
  font-size: 13px;
  line-height: 1.9;
  color: #555;
  margin: 10px 0 0;
  white-space: pre-wrap;
}

.summary-text {
  color: #444;
}

/* ==================== 情绪占比 ==================== */

.emotion-wrap {
  display: flex;
  gap: 28px;
  align-items: center;
}

.ring-box {
  position: relative;
  width: 150px;
  height: 150px;
  flex-shrink: 0;
}

.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-center-label {
  font-size: 11px;
  color: #b0b6c4;
}

.ring-center-value {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
  margin-top: 2px;
}

.ring-center-sub {
  font-size: 13px;
  font-weight: 600;
  color: #6c63ff;
  margin-top: 2px;
}

.legend {
  flex: 1;
  min-width: 0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  font-size: 13px;
  color: #555;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.legend-name {
  flex: 1;
}

.legend-pct {
  font-weight: 600;
  color: #333;
}

.no-emotion {
  color: #999;
  font-size: 13px;
  text-align: center;
  padding: 20px 0;
}

.ratio-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #e6e8f0;
}

.ratio-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #666;
}

.ratio-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

/* ==================== 情绪趋势 ==================== */

.trend-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.trend-icon {
  font-size: 18px;
  font-weight: 700;
  color: #6c63ff;
}

.trend-text {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  flex: 1;
}

.trend-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.trend-label {
  font-size: 12px;
  color: #b0b6c4;
}

.trend-value {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  margin-top: 2px;
}

/* ==================== 情绪分布条形 ==================== */

.bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.bar-name {
  width: 56px;
  font-size: 13px;
  color: #555;
  flex-shrink: 0;
  text-align: right;
}

.bar-track {
  flex: 1;
  height: 10px;
  background: #f2f2f8;
  border-radius: 5px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 5px;
  transition: width 0.5s ease;
}

.bar-value {
  width: 56px;
  font-size: 12px;
  font-weight: 600;
  color: #333;
  text-align: right;
}

/* ==================== PAD 维度 ==================== */

.pad-grid {
  display: grid;
  gap: 18px;
}

.pad-card {
  background: #fafafe;
  border: 1px solid #ececf2;
  border-radius: 12px;
  padding: 14px 16px;
}

.pad-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.pad-name {
  font-size: 13px;
  color: #666;
}

.pad-value {
  font-size: 22px;
  font-weight: 700;
  margin-top: 2px;
}

.pad-track-labels {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #b0b6c4;
  margin-bottom: 4px;
}

.pad-bar {
  position: relative;
  height: 12px;
  background: linear-gradient(90deg, #fff0ef, #fff 45%, #fff 55%, #eef9f0);
  border-radius: 6px;
}

.pad-zero {
  position: absolute;
  left: 50%;
  top: -3px;
  bottom: -3px;
  width: 2px;
  background: #d6d9e4;
  z-index: 1;
}

.pad-fill {
  position: absolute;
  top: 0;
  height: 100%;
  border-radius: 6px;
  min-width: 2px;
}

.pad-sub {
  margin-top: 8px;
  font-size: 12px;
  color: #b0b6c4;
}

/* ==================== 通用键值 ==================== */

.kv-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.kv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #333;
}

.kv-label {
  font-size: 12px;
  color: #b0b6c4;
  flex-shrink: 0;
}

.kv-strong {
  font-weight: 600;
}

.tag-block {
  margin-top: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

/* ==================== 触发因素 ==================== */

.trigger-scene {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
}

.trigger-icon {
  font-size: 20px;
  line-height: 1.4;
}

.trigger-scene-value {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin-top: 2px;
}

.trigger-meta {
  margin-bottom: 12px;
}

.trigger-meta-value {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-left: 4px;
}

.kw-tag {
  border-radius: 999px;
}

.trigger-desc {
  border-left: 3px solid #6c63ff;
  padding-left: 12px;
  background: #f8f7ff;
  padding: 8px 12px;
  border-radius: 0 8px 8px 0;
  margin-top: 12px;
}

.risk-detail {
  border-left: 3px solid #fa8c16;
  padding-left: 12px;
  background: #fffaf2;
  padding: 8px 12px;
  border-radius: 0 8px 8px 0;
  margin-top: 12px;
}

/* ==================== AI 建议 ==================== */

.advice-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.advice-card {
  background: #fafafe;
  border: 1px solid #ececf2;
  border-radius: 12px;
  padding: 16px;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.advice-card.hot {
  background: linear-gradient(135deg, #f0eeff, #ffffff);
  border-color: #6c63ff;
  box-shadow: 0 6px 18px rgba(108, 99, 255, 0.14);
}

.advice-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.advice-icon {
  font-size: 18px;
}

.advice-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.advice-card p {
  font-size: 13px;
  line-height: 1.9;
  color: #555;
  margin: 0;
  white-space: pre-wrap;
}

.priority-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.priority-banner.warning {
  background: #fff7ed;
  color: #c2410c;
  border: 1px solid #fed7aa;
}

.priority-banner.danger {
  background: #fff1f0;
  color: #cf1322;
  border: 1px solid #ffccc7;
}

.priority-banner.success {
  background: #f0fff0;
  color: #237804;
  border: 1px solid #b7eb8f;
}

.priority-banner.info {
  background: #f0f5ff;
  color: #1d39c4;
  border: 1px solid #adc6ff;
}

.priority-icon {
  font-size: 16px;
}

/* ==================== 我的反馈 ==================== */

.fb-stars {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.fb-score-text {
  font-size: 13px;
  color: #666;
}

.fb-agree-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.fb-agree-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: #fafafe;
  border: 1px solid #ececf2;
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 13px;
  color: #555;
}

.fb-use {
  margin-bottom: 12px;
  font-size: 13px;
}

.fb-use strong {
  margin-left: 8px;
  color: #333;
}

.fb-content {
  background: #fafafe;
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 13px;
  color: #555;
  line-height: 1.8;
  margin-bottom: 14px;
  white-space: pre-wrap;
}

.fb-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.fb-field {
  margin-bottom: 18px;
}

.fb-field:last-of-type {
  margin-bottom: 0;
}

.fb-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.fb-check-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}

/* ==================== 加载与错误 ==================== */

.skeleton {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  padding: 28px 24px;
}

.sk-line {
  height: 14px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f6, #fafafd, #f0f0f6);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
  margin-bottom: 14px;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.error-box {
  text-align: center;
  padding: 60px 20px;
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
}

/* ==================== 响应式 ==================== */

@media (max-width: 860px) {
  .stat-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .grid-2 {
    grid-template-columns: 1fr;
  }

  .advice-grid {
    grid-template-columns: 1fr;
  }

  .fb-agree-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .core-badge {
    align-self: stretch;
    justify-content: flex-start;
  }

  .emotion-wrap {
    flex-direction: column;
    align-items: center;
  }

  .legend {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .stat-strip {
    grid-template-columns: 1fr;
  }

  .fb-agree-grid {
    grid-template-columns: 1fr;
  }

  .trend-grid {
    grid-template-columns: 1fr;
  }
}
</style>
