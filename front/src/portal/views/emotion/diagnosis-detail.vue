<template>
  <div class="es-page es-detail-page">
    <!-- 面包屑 -->
    <div class="es-breadcrumb">
      <router-link to="/diagnosis">心理评估</router-link>
      <span>›</span>
      <router-link :to="`/diagnosis/${sessionId}`">评估记录</router-link>
      <span>›</span>
      <span class="cur">评估详情</span>
    </div>

    <!-- 未登录 -->
    <template v-if="!userStore.token">
      <div class="es-empty-wrap">
        <el-empty description="登录后即可查看评估详情">
          <el-button type="primary" @click="userStore.openLoginDialog('login')">去登录</el-button>
        </el-empty>
      </div>
    </template>

    <!-- 加载骨架 -->
    <template v-else-if="loading">
      <div class="es-list-loading">
        <div class="es-sk-line" style="width: 60%"></div>
        <div class="es-sk-line" style="width: 100%"></div>
        <div class="es-sk-line" style="width: 85%"></div>
      </div>
    </template>

    <!-- 加载失败 -->
    <template v-else-if="loadError">
      <div class="es-empty-wrap">
        <el-empty description="评估报告加载失败，请稍后重试">
          <el-button type="primary" @click="loadDiagnosis">重新加载</el-button>
        </el-empty>
      </div>
    </template>

    <template v-else-if="diagnosis">
      <DiagnosisHero
        :diagnosis="diagnosis"
        :round-num-text="roundNumText"
        :format-date-time="formatDateTime"
        :format-percent="formatPercent"
        @scroll-to-feedback="scrollToFeedback"
      />

      <DiagnosisStatCards
        :diagnosis="diagnosis"
        :emotion-risk-meta="emotionRiskMeta"
        :crisis-text="crisisText"
        :format-percent="formatPercent"
        :signed="signed"
      />

      <!-- 情绪占比 + 情绪趋势 -->
      <div class="es-g2 es-chart-row es-overview-row">
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

        <section class="es-data-panel">
          <h4><span class="es-icon">📈</span>情绪趋势</h4>
          <div style="display:flex;align-items:center;gap:6px;font-size:11px;margin-bottom:8px;">
            <span>{{ trendMeta.icon }}</span>
            <span style="font-weight:600;color:#333;">整体情绪{{ trendMeta.text }}</span>
            <span class="es-pill" :class="trendMeta.type">{{ trendMeta.text }}</span>
          </div>
          <div class="es-data-item"><span class="es-key">峰值轮次</span><span class="es-val">第{{ diagnosis.emotionPeakRound ?? "--" }}轮</span></div>
          <div class="es-data-item"><span class="es-key">低谷轮次</span><span class="es-val">第{{ diagnosis.emotionValleyRound ?? "--" }}轮</span></div>
          <div class="es-data-item"><span class="es-key">波动幅度</span><span class="es-val">{{ formatNumber(diagnosis.emotionFluctuationAmplitude) }}</span></div>
          <div class="es-data-item"><span class="es-key">稳定轮次</span><span class="es-val">第{{ diagnosis.emotionStableRounds ?? "--" }}轮</span></div>
          <div class="es-data-item"><span class="es-key">稳定性得分</span><span class="es-val">{{ formatPercent(diagnosis.emotionStabilityScore) }}</span></div>
        </section>
      </div>

      <!-- 核心 + 次要情绪分布 -->
      <div class="es-g2 es-chart-row es-emotion-row">
        <EmotionBarChart title="📊 核心情绪分布" :entries="coreTopEntries" :color-of="normalizedEmotionColor" />
        <EmotionBarChart title="🔬 次要情绪分布" :entries="secondaryEntries" :color-of="normalizedEmotionColor" />
      </div>

      <!-- PAD 情绪维度雷达 -->
      <EmotionPadRadar :items="padRadarItems" />

      <!-- 基础信息 -->
      <div class="es-section-title">📌 基础信息</div>
      <section class="es-data-panel">
        <div class="es-g4">
          <div class="es-data-item"><span class="es-key">诊断ID</span><span class="es-val">{{ diagnosis.id ?? "--" }}</span></div>
          <div class="es-data-item"><span class="es-key">会话ID</span><span class="es-val">{{ diagnosis.conversationId ?? "--" }}</span></div>
          <div class="es-data-item"><span class="es-key">用户ID</span><span class="es-val">{{ diagnosis.userId ?? "--" }}</span></div>
          <div class="es-data-item"><span class="es-key">诊断轮次</span><span class="es-val">{{ roundNumText }}</span></div>
          <div class="es-data-item"><span class="es-key">创建时间</span><span class="es-val">{{ formatDateTime(diagnosis.createdTime) }}</span></div>
          <div class="es-data-item"><span class="es-key">更新时间</span><span class="es-val">{{ formatDateTime(diagnosis.updatedTime) }}</span></div>
          <div class="es-data-item">
            <span class="es-key">核心情绪标签</span>
            <span class="es-val" style="color:#6c63ff;">{{ diagnosis.coreEmotionLabel ?? "--" }}</span>
          </div>
          <div class="es-data-item"><span class="es-key">置信度</span><span class="es-val">{{ formatPercent(diagnosis.coreEmotionConfAvg) }}</span></div>
          <div class="es-data-item"><span class="es-key">强度</span><span class="es-val">{{ formatPercent(diagnosis.coreEmotionIntensityScore) }}</span></div>
          <div class="es-data-item">
            <span class="es-key">整体心理状态</span>
            <span class="es-pill" :class="emotionRiskMeta.type">{{ diagnosis.psychologicalState ?? "--" }}</span>
          </div>
        </div>
      </section>

      <div class="es-section-title">📝 诊断评估内容</div>
      <section class="es-data-panel">
        <p class="es-para">{{ diagnosis.diagnosisContent }}</p>
      </section>

      <!-- 趋势数据已在顶部趋势面板展示；PAD 均值与标准差已在雷达图右侧按维度归组 -->

      <!-- 触发因素分析 -->
      <div class="es-section-title">🎯 触发因素分析</div>
      <section class="es-data-panel">
        <div class="es-g4">
          <div class="es-data-item"><span class="es-key">触发场景</span><span class="es-val">{{ diagnosis.coreTriggerScene ?? "--" }}</span></div>
          <div class="es-data-item"><span class="es-key">触发关键词</span><span class="es-val">{{ triggerKeywords.join("、") || "--" }}</span></div>
          <div class="es-data-item"><span class="es-key">触发轮次</span><span class="es-val">{{ diagnosis.triggerRoundNum ? `第${diagnosis.triggerRoundNum}轮` : "--" }}</span></div>
          <div class="es-data-item" style="grid-column: 1 / -1;">
            <span class="es-key">首次触发描述</span>
            <span class="es-val">{{ diagnosis.firstTriggerDesc ?? "--" }}</span>
          </div>
        </div>
      </section>

      <!-- 症状与心理状态评估 -->
      <div class="es-section-title">🏥 症状与心理状态评估</div>
      <div class="es-g3">
        <section class="es-data-panel">
          <h4><span class="es-icon">🧠</span>心理状态</h4>
          <div class="es-data-item"><span class="es-key">整体状态</span><span class="es-val">{{ diagnosis.psychologicalState ?? "--" }}</span></div>
          <div class="es-data-item"><span class="es-key">持续时长</span><span class="es-val">{{ diagnosis.symptomDuration ?? "--" }}</span></div>
          <div class="es-data-item"><span class="es-key">发作模式</span><span class="es-val">{{ diagnosis.onsetPattern ?? "--" }}</span></div>
        </section>

        <section class="es-data-panel">
          <h4><span class="es-icon">📋</span>症状总结</h4>
          <p class="es-para">{{ diagnosis.symptomSummary }}</p>
        </section>

        <section class="es-data-panel">
          <h4><span class="es-icon">🏷️</span>症状标签</h4>
          <div style="display:flex;flex-wrap:wrap;gap:6px;">
            <span class="es-pill warning" v-for="t in symptomTagList" :key="t">{{ t }}</span>
          </div>
        </section>
      </div>

      <!-- 社会功能评估 -->
      <div class="es-section-title">🌐 社会功能评估</div>
      <div class="es-g3">
        <section class="es-data-panel">
          <h4><span class="es-icon">🤝</span>社会功能</h4>
          <div class="es-data-item">
            <span class="es-key">受损程度</span>
            <span class="es-pill" :class="socialImpactType">{{ diagnosis.socialFunctionImpact ?? "--" }}</span>
          </div>
          <div class="es-data-item"><span class="es-key">影响领域</span><span class="es-val">{{ diagnosis.impactDomains || "--" }}</span></div>
        </section>

        <section class="es-data-panel">
          <h4><span class="es-icon">🏠</span>日常生活影响</h4>
          <p class="es-para">{{ diagnosis.dailyLifeInfluence }}</p>
        </section>

        <section class="es-data-panel">
          <h4><span class="es-icon">💪</span>心理资源</h4>
          <div class="es-data-item"><span class="es-key">社会支持</span><span class="es-val">{{ socialSupportText }}</span></div>
          <div class="es-data-item"><span class="es-key">应对方式</span><span class="es-val">{{ diagnosis.copingStyle || "--" }}</span></div>
          <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:6px;">
            <span class="es-pill success" v-for="t in protectiveFactorsList" :key="t">{{ t }}</span>
          </div>
        </section>
      </div>

      <!-- 风险评估 -->
      <div class="es-section-title">⚠️ 风险评估</div>
      <section class="es-data-panel">
        <div class="es-g4">
          <div class="es-data-item" v-for="r in riskItems" :key="r.label">
            <span class="es-key">{{ r.label }}</span>
            <span class="es-pill" :class="r.type">{{ r.text }}</span>
          </div>
          <div v-if="diagnosis.riskDetail" class="es-data-item" style="grid-column: 1 / -1;">
            <span class="es-key">风险细节</span>
            <span class="es-val">{{ diagnosis.riskDetail }}</span>
          </div>
        </div>
      </section>

      <!-- 干预建议 -->
      <div class="es-section-title">💡 干预建议</div>
      <div class="es-g2">
        <section class="es-data-panel">
          <h4><span class="es-icon">🧘</span>自我调节建议</h4>
          <p class="es-para">{{ diagnosis.selfHelpSuggestion }}</p>
        </section>
        <section class="es-data-panel">
          <h4><span class="es-icon">🤝</span>社会支持建议</h4>
          <p class="es-para">{{ diagnosis.socialSupportSuggestion }}</p>
        </section>
      </div>
      <div class="es-g2">
        <section class="es-data-panel">
          <h4><span class="es-icon">🩺</span>专业干预建议</h4>
          <p class="es-para">{{ diagnosis.professionalInterveneSuggestion }}</p>
        </section>
        <section class="es-data-panel">
          <h4><span class="es-icon">💡</span>情绪调节建议</h4>
          <p class="es-para">{{ diagnosis.emotionAdjustSuggestion }}</p>
        </section>
      </div>
      <section class="es-data-panel">
        <div class="es-data-item">
          <span class="es-key">建议优先级</span>
          <span class="es-pill" :class="priorityMeta.type">{{ priorityMeta.text }}</span>
        </div>
      </section>

      <!-- 反馈 -->
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
  normalizeEmotionLabel,
  normalizeEmotionEntries,
  normalizedEmotionColor,
  RING_CIRCUMFERENCE,
  RING_RADIUS,
  emotionColor,
  useDiagnosisAnalysis,
} from "@/portal/composables/useDiagnosisFormatters";
import { useDiagnosisFeedback } from "@/portal/composables/useDiagnosisFeedback";
import DiagnosisHero from "@/portal/components/emotion/DiagnosisHero.vue";
import DiagnosisStatCards from "@/portal/components/emotion/DiagnosisStatCards.vue";
import EmotionPieChart from "@/portal/components/emotion/EmotionPieChart.vue";
import EmotionBarChart from "@/portal/components/emotion/EmotionBarChart.vue";
import EmotionPadRadar, { type PadRadarItem } from "@/portal/components/emotion/EmotionPadRadar.vue";
import DiagnosisFeedbackSection from "@/portal/components/emotion/DiagnosisFeedbackSection.vue";
import "@/shared/styles/emotion/emotion-design.css";

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

/* ==================== 图表数据 ==================== */

const coreTopEntries = computed(() =>
  emotionEntries.value.slice(0, 6).map((e) => ({ label: normalizeEmotionLabel(e.label), value: Math.round(toPercent(e.value)) })),
);

const secondaryEntries = computed(() =>
  normalizeEmotionEntries(
    Object.entries(diagnosis.value?.secondaryEmotion ?? {}).map(([label, value]) => ({
      label,
      value: Number(value) || 0,
    })),
  )
    .map((entry) => ({ label: entry.label, value: Math.round(toPercent(entry.value)) }))
    .sort((a, b) => b.value - a.value),
);

const padRadarItems = computed<PadRadarItem[]>(() =>
  padItems.value.map((item) => {
    const mean = item.mean;
    const std = item.std;
    return {
      name: item.name,
      label: item.label,
      mean,
      std,
      meanText: mean == null ? "--" : signed(mean),
      stdText: std == null ? "--" : formatNumber(std),
      stdSub: padStdText({ std }),
    };
  }),
);

/* ==================== PAD 文本 ==================== */

function padStdText(item: { std: number | null }): string {
  if (item.std == null) return "标准差缺失，无法评估波动";
  if (item.std >= 0.3) return "波动较强，情绪起伏明显";
  return item.std >= 0.15 ? "波动中等，情绪较为稳定" : "波动较小，情绪非常稳定";
}

/* ==================== 反馈滚动 ==================== */

function scrollToFeedback() {
  document.getElementById("feedback")?.scrollIntoView({ behavior: "smooth", block: "start" });
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
