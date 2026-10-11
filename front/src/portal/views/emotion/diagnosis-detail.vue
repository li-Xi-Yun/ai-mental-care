<template>
  <div class="diagnosis-detail-page">
    <div class="breadcrumb">
      <router-link to="/diagnosis" class="breadcrumb-link">心理诊断</router-link>
      <span class="breadcrumb-sep">›</span>
      <router-link :to="`/diagnosis/${sessionId}`" class="breadcrumb-link">诊断记录</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">诊断详情</span>
    </div>

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
      <DiagnosisHero
        :diagnosis="diagnosis"
        :round-num-text="roundNumText"
        :format-date-time="formatDateTime"
        :format-percent="formatPercent"
      />

      <DiagnosisStatCards :diagnosis="diagnosis" :emotion-risk-meta="emotionRiskMeta" :crisis-text="crisisText" :format-percent="formatPercent" :signed="signed" />

      <section v-if="diagnosis.diagnosisContent" class="panel">
        <div class="panel-title"><span class="panel-icon">📝</span>诊断摘要</div>
        <p class="para summary-text">{{ diagnosis.diagnosisContent }}</p>
      </section>

      <div class="grid-2">
        <EmotionPieChart
          :emotion-segments="emotionSegments"
          :core-emotion-label="diagnosis.coreEmotionLabel"
          :core-emotion-share="coreEmotionShare"
          :emotion-entries="emotionEntries"
          :ratio-summary="ratioSummary"
          :emotion-color="emotionColor"
          :to-percent="toPercent"
          :ring-radius="RING_RADIUS"
          :ring-circumference="RING_CIRCUMFERENCE"
        />

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
            <span class="trigger-meta-value">{{ diagnosis.triggerRoundNum ? `第${diagnosis.triggerRoundNum}轮` : "--" }}</span>
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

      <div class="grid-2">
        <section class="panel">
          <div class="panel-title"><span class="panel-icon">🏥</span>症状与心理状态</div>
          <div class="kv-grid">
            <div class="kv-item"><span class="kv-label">心理状态</span><strong class="kv-strong">{{ diagnosis.psychologicalState ?? "--" }}</strong></div>
            <div class="kv-item"><span class="kv-label">持续时长</span><span>{{ diagnosis.symptomDuration ?? "--" }}</span></div>
            <div class="kv-item"><span class="kv-label">发作模式</span><span>{{ diagnosis.onsetPattern ?? "--" }}</span></div>
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
            <div class="kv-item"><span class="kv-label">社会功能</span><el-tag :type="socialImpactType" size="small" effect="light">{{ diagnosis.socialFunctionImpact ?? "--" }}</el-tag></div>
            <div class="kv-item"><span class="kv-label">社会支持</span><span>{{ socialSupportText }}</span></div>
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

      <section class="panel">
        <div class="panel-title"><span class="panel-icon">💡</span>AI 建议</div>
        <div class="advice-grid">
          <div class="advice-card" :class="{ hot: suggestionPriority === 1 }">
            <div class="advice-head"><span class="advice-icon">🧘</span><span class="advice-title">自助调节</span></div>
            <p>{{ diagnosis.selfHelpSuggestion || "暂无建议" }}</p>
          </div>
          <div class="advice-card" :class="{ hot: suggestionPriority === 2 }">
            <div class="advice-head"><span class="advice-icon">🤝</span><span class="advice-title">社会支持</span></div>
            <p>{{ diagnosis.socialSupportSuggestion || "暂无建议" }}</p>
          </div>
          <div class="advice-card" :class="{ hot: suggestionPriority === 3 }">
            <div class="advice-head"><span class="advice-icon">🩺</span><span class="advice-title">专业干预</span></div>
            <p>{{ diagnosis.professionalInterveneSuggestion || "暂无建议" }}</p>
          </div>
        </div>
        <div class="priority-banner" :class="priorityMeta.type">
          <span class="priority-icon">💡</span>
          <span>建议优先级：<strong>{{ priorityMeta.text }}</strong></span>
        </div>
      </section>

      <DiagnosisFeedbackSection
        :feedback="feedback"
        :editing="editing"
        :submitting="submitting"
        :agree-tag-type="agreeTagType"
        :agree-text="agreeText"
        :use-suggestion-text="useSuggestionText"
        :form="form"
        @start-edit="startEdit"
        @cancel-edit="editing = false"
        @submit-feedback="submitFeedback"
        @use-suggestion-change="onUseSuggestionChange"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { CSSProperties } from "vue";
import { useRoute } from "vue-router";
import { useUserStore } from "@/portal/stores/user";
import { getEmotionDiagnosisDetail } from "@/portal/api/conversation/emotion-diagnosis";
import type { EmotionDiagnosisVO } from "@/portal/api/conversation/emotion-diagnosis";
import {
  formatDateTime,
  formatPercent,
  formatNumber,
  signed,
  toPercent,
  RING_CIRCUMFERENCE,
  RING_RADIUS,
  emotionColor,
  useDiagnosisAnalysis,
} from "@/portal/composables/useDiagnosisFormatters";
import { useDiagnosisFeedback } from "@/portal/composables/useDiagnosisFeedback";
import DiagnosisHero from "@/portal/components/emotion/DiagnosisHero.vue";
import DiagnosisStatCards from "@/portal/components/emotion/DiagnosisStatCards.vue";
import EmotionPieChart from "@/portal/components/emotion/EmotionPieChart.vue";
import DiagnosisFeedbackSection from "@/portal/components/emotion/DiagnosisFeedbackSection.vue";

/* ==================== 基础状态 ==================== */

const route = useRoute();
const userStore = useUserStore();
const sessionId = route.params.sessionId as string;
const diagnosisId = route.params.diagnosisId as string;

const loading = ref(true);
const loadError = ref(false);
const diagnosis = ref<EmotionDiagnosisVO | null>(null);

/* ==================== composables ==================== */

const {
  emotionEntries,
  emotionTotal,
  emotionSegments,
  coreEmotionShare,
  ratioSummary,
  trendMeta,
  emotionRiskMeta,
  selfHarmRiskMeta,
  suicideRiskMeta,
  crisisText,
  padItems,
  socialImpactType,
  socialSupportText,
  suggestionPriority,
  priorityMeta,
  symptomTagList,
  triggerKeywords,
  protectiveFactorsList,
} = useDiagnosisAnalysis(() => diagnosis.value);

const {
  feedback,
  editing,
  submitting,
  form,
  loadFeedback,
  startEdit,
  onUseSuggestionChange,
  submitFeedback,
  agreeText,
  agreeTagType,
  useSuggestionText,
} = useDiagnosisFeedback(diagnosisId);

/* ==================== 衍生数据 ==================== */

const roundNumText = computed(() => (diagnosis.value?.roundNum ? `第${diagnosis.value.roundNum}轮` : "--"));

const riskItems = computed(() => {
  const d = diagnosis.value;
  return [
    { label: "情绪风险", text: emotionRiskMeta.value.text, type: emotionRiskMeta.value.type as "success" | "warning" | "danger" | "info" },
    { label: "自伤风险", text: selfHarmRiskMeta.value.text, type: selfHarmRiskMeta.value.type as "success" | "warning" | "danger" | "info" },
    { label: "自杀风险", text: suicideRiskMeta.value.text, type: suicideRiskMeta.value.type as "success" | "warning" | "danger" | "info" },
    { label: "危机预警", text: crisisText.value, type: (d?.crisisWarning === 1 ? "danger" : "success") as "success" | "warning" | "danger" | "info" },
    { label: "人工干预", text: d?.needManualIntervene === 1 ? "需要" : d?.needManualIntervene === 0 ? "暂不需要" : "--", type: (d?.needManualIntervene === 1 ? "warning" : "info") as "success" | "warning" | "danger" | "info" },
  ];
});

/* ==================== PAD 样式 ==================== */

function padBarStyle(item: { mean: number | null; name: string }): CSSProperties {
  const v = item.mean ?? 0;
  const width = Math.abs(v) * 50;
  const left = v >= 0 ? 50 : 50 - width;
  return { left: `${left}%`, width: `${width}%`, backgroundColor: v >= 0 ? "#6c63ff" : "#ff6b6b" };
}

function padStdTagType(item: { std: number | null }): "success" | "warning" | "danger" | "info" {
  if (item.std == null) return "info";
  return item.std >= 0.3 ? "warning" : "success";
}

function padStdText(item: { std: number | null }): string {
  if (item.std == null) return "标准差缺失，无法评估波动";
  if (item.std >= 0.3) return "波动较强，情绪起伏明显";
  return item.std >= 0.15 ? "波动中等，情绪较为稳定" : "波动较小，情绪非常稳定";
}

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

async function init() {
  if (!userStore.token) {
    loading.value = false;
    userStore.openLoginDialog("login");
    return;
  }
  await Promise.all([loadDiagnosis(), loadFeedback()]);
}

watch(
  () => userStore.token,
  (t) => {
    if (t && !diagnosis.value && !loading.value) void init();
  },
);

onMounted(() => void init());
</script>

<style scoped>
/* Note: All styles remain scoped as in the original for design consistency */
.diagnosis-detail-page {
  max-width: 980px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.breadcrumb {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; color: #999; margin-bottom: 16px;
}

.breadcrumb-link { color: #6c63ff; text-decoration: none; transition: color 0.2s; }
.breadcrumb-link:hover { color: #3f3d9e; }
.breadcrumb-current { color: #333; font-weight: 500; }

.panel {
  background: #fff; border: 1px solid #ececf2; border-radius: 14px;
  padding: 20px; margin-bottom: 16px;
}

.panel-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 15px; font-weight: 600; color: #1a1a2e; margin-bottom: 16px;
}

.panel-icon { font-size: 16px; }

.grid-2 {
  display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px;
}

.para {
  font-size: 13px; line-height: 1.9; color: #555; margin: 10px 0 0; white-space: pre-wrap;
}

.summary-text { color: #444; }

/* Bar chart */
.bar-row {
  display: flex; align-items: center; gap: 12px; margin-bottom: 10px;
}

.bar-name { width: 56px; font-size: 13px; color: #555; flex-shrink: 0; text-align: right; }
.bar-track { flex: 1; height: 10px; background: #f2f2f8; border-radius: 5px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 5px; transition: width 0.5s ease; }
.bar-value { width: 56px; font-size: 12px; font-weight: 600; color: #333; text-align: right; }

/* Trend */
.trend-badge { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.trend-icon { font-size: 18px; font-weight: 700; color: #6c63ff; }
.trend-text { font-size: 14px; font-weight: 600; color: #1a1a2e; flex: 1; }
.trend-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 16px; }
.trend-label { font-size: 12px; color: #b0b6c4; }
.trend-value { font-size: 15px; font-weight: 600; color: #1a1a2e; margin-top: 2px; }

/* PAD */
.pad-grid { display: grid; gap: 18px; }
.pad-card { background: #fafafe; border: 1px solid #ececf2; border-radius: 12px; padding: 14px 16px; }
.pad-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.pad-name { font-size: 13px; color: #666; }
.pad-value { font-size: 22px; font-weight: 700; margin-top: 2px; }
.pad-track-labels { display: flex; justify-content: space-between; font-size: 11px; color: #b0b6c4; margin-bottom: 4px; }
.pad-bar { position: relative; height: 8px; background: #ececf2; border-radius: 4px; }
.pad-zero { position: absolute; left: 50%; top: 0; bottom: 0; width: 2px; background: #ccc; border-radius: 1px; }
.pad-fill { position: absolute; top: 0; height: 100%; border-radius: 4px; min-width: 4px; }
.pad-sub { margin-top: 8px; font-size: 12px; color: #999; }

/* Trigger & Risk */
.trigger-scene { display: flex; gap: 12px; margin-bottom: 12px; }
.trigger-icon { font-size: 18px; flex-shrink: 0; }
.trigger-scene-value { font-size: 15px; font-weight: 600; color: #1a1a2e; margin-top: 4px; }
.trigger-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; font-size: 13px; }
.trigger-desc { font-size: 13px; color: #888; }
.kv-label { font-size: 12px; color: #9aa0b5; }
.kv-grid { display: grid; gap: 12px; }
.kv-item { display: flex; align-items: center; justify-content: space-between; font-size: 13px; }
.kv-strong { color: #1a1a2e; }
.tag-block { margin-top: 10px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.kw-tag { margin: 0; }

/* Advice */
.advice-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; margin-bottom: 12px; }
.advice-card { background: #fafafe; border: 1px solid #ececf2; border-radius: 12px; padding: 14px; }
.advice-card p { font-size: 12px; line-height: 1.7; color: #666; margin: 8px 0 0; }
.advice-head { display: flex; align-items: center; gap: 6px; }
.advice-icon { font-size: 16px; }
.advice-title { font-size: 13px; font-weight: 600; color: #1a1a2e; }
.advice-card.hot { border-color: #6c63ff; background: #f8f7ff; }
.priority-banner { display: flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 10px; font-size: 13px; border: 1px solid; }
.priority-banner.success { background: #f6ffed; border-color: #b7eb8f; color: #52c41a; }
.priority-banner.warning { background: #fffbe6; border-color: #ffe58f; color: #fa8c16; }
.priority-banner.danger { background: #fff1f0; border-color: #ffa39e; color: #f5222d; }
.priority-banner.info { background: #f0f5ff; border-color: #adc6ff; color: #1890ff; }
.priority-icon { font-size: 16px; }

/* Skeleton */
.skeleton { padding: 24px; display: flex; flex-direction: column; gap: 12px; }
.sk-line { height: 16px; background: linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%); background-size: 200% 100%; animation: shimmer 1.4s infinite; border-radius: 6px; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.error-box { text-align: center; padding: 40px 0; }
</style>