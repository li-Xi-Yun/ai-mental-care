import { computed } from "vue";
import dayjs from "@/shared/utils/dayjs";
import { EMOTION_COLORS } from "@/shared/api/config";
import type { EmotionDiagnosisVO } from "@/portal/api/conversation/emotion-diagnosis";

/* ==================== 格式化工具 ==================== */

export function toNumber(v?: number | null): number | null {
  if (v == null) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

export function formatPercent(v?: number | null, digits = 1): string {
  const n = toNumber(v);
  return n === null ? "--" : `${(n * 100).toFixed(digits)}%`;
}

export function formatNumber(v?: number | null): string {
  const n = toNumber(v);
  return n === null ? "--" : n.toFixed(2);
}

export function toPercent(v?: number | null): number {
  const n = toNumber(v);
  if (n === null) return 0;
  return n <= 1 ? n * 100 : n;
}

export function signed(v?: number | null): string {
  const n = toNumber(v);
  if (n === null) return "--";
  return `${n > 0 ? "+" : ""}${n.toFixed(2)}`;
}

export function formatDateTime(v?: string): string {
  if (!v) return "--";
  const d = dayjs(v);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(v);
}

export function splitTags(s?: string): string[] {
  if (!s) return [];
  return s
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean);
}

/* ==================== 常量 ==================== */

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

export const TREND_META: Record<number, { text: string; icon: string; type: "success" | "warning" | "danger" | "info" }> = {
  0: { text: "上升", icon: "↑", type: "danger" },
  1: { text: "下降", icon: "↓", type: "success" },
  2: { text: "平稳", icon: "→", type: "info" },
  3: { text: "无法判断", icon: "·", type: "info" },
};

export const EMOTION_RISK_META: Record<number, { text: string; type: "success" | "warning" | "danger" | "info" }> = {
  0: { text: "低", type: "success" },
  1: { text: "中", type: "warning" },
  2: { text: "高", type: "danger" },
  3: { text: "危急", type: "danger" },
  4: { text: "无法判断", type: "info" },
};

export const HARM_RISK_META: Record<number, { text: string; type: "success" | "warning" | "danger" | "info" }> = {
  0: { text: "无", type: "success" },
  1: { text: "低", type: "success" },
  2: { text: "中", type: "warning" },
  3: { text: "高", type: "danger" },
  4: { text: "极高", type: "danger" },
  5: { text: "无法判断", type: "info" },
};

export const PRIORITY_META: Record<number, { text: string; type: "success" | "warning" | "danger" | "info" }> = {
  1: { text: "自助调节为主", type: "success" },
  2: { text: "建议寻求支持", type: "warning" },
  3: { text: "强烈建议专业干预", type: "danger" },
  4: { text: "无法判断", type: "info" },
};

export const RING_RADIUS = 40;
export const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

const EMOTION_LABEL_ZH: Record<string, string> = {
  anger: "愤怒",
  sadness: "悲伤",
  fear: "恐惧",
  anxiety: "焦虑",
  disgust: "厌恶",
  surprise: "惊讶",
  happy: "开心",
  neutral: "中性",
  guilt: "内疚",
  shame: "羞耻",
  hope: "希望",
  confusion: "困惑",
};

/** 将后端英文情绪枚举统一为中文展示文本。 */
export function normalizeEmotionLabel(label?: string | null): string {
  if (!label) return "未知情绪";
  return EMOTION_LABEL_ZH[label] ?? label;
}

/** 中文化并保留原始情绪数据，供图表与详情页共用。 */
export function normalizeEmotionEntries(entries: { label: string; value: number }[]) {
  const merged = new Map<string, number>();
  for (const entry of entries) {
    const label = normalizeEmotionLabel(entry.label);
    merged.set(label, (merged.get(label) ?? 0) + entry.value);
  }
  return [...merged.entries()]
    .map(([label, value]) => ({ label, value }))
    .sort((a, b) => b.value - a.value);
}

/** 中文标签使用语义色，英文枚举先归一化后再取色。 */
export function normalizedEmotionColor(label: string, index: number): string {
  return emotionColor(normalizeEmotionLabel(label), index);
}

/* ==================== 颜色映射 ==================== */

export function emotionColor(label: string, index: number): string {
  return EMOTION_COLORS[label] ?? FALLBACK_COLORS[index % FALLBACK_COLORS.length];
}

/* ==================== 计算函数 ==================== */

export function useDiagnosisAnalysis(diagnosis: () => EmotionDiagnosisVO | null) {
  /* 情绪占比 */
  const emotionEntries = computed(() => {
    const map = diagnosis()?.coreEmotion ?? {};
    return normalizeEmotionEntries(
      Object.entries(map).map(([label, value]) => ({ label, value: Number(value) || 0 })),
    );
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
    const label = normalizeEmotionLabel(diagnosis()?.coreEmotionLabel);
    const seg = emotionSegments.value.find((s) => normalizeEmotionLabel(s.label) === label);
    return seg ? seg.percent.toFixed(0) : "0";
  });

  const ratioSummary = computed(() => {
    const d = diagnosis();
    return [
      { label: "负向情绪", value: toNumber(d?.negativeEmotionRatio), color: "#ff6b6b" },
      { label: "中性情绪", value: toNumber(d?.neutralEmotionRatio), color: "#6c63ff" },
      { label: "正向情绪", value: toNumber(d?.positiveEmotionRatio), color: "#52c41a" },
    ];
  });

  /* 趋势 */
  const trendMeta = computed(() => TREND_META[diagnosis()?.emotionTrend ?? 3] ?? TREND_META[3]);
  const emotionRiskMeta = computed(() => EMOTION_RISK_META[diagnosis()?.emotionRiskLevel ?? 4] ?? EMOTION_RISK_META[4]);
  const selfHarmRiskMeta = computed(() => HARM_RISK_META[diagnosis()?.selfHarmRiskLevel ?? 5] ?? HARM_RISK_META[5]);
  const suicideRiskMeta = computed(() => HARM_RISK_META[diagnosis()?.suicideRiskLevel ?? 5] ?? HARM_RISK_META[5]);

  const crisisText = computed(() => {
    const v = diagnosis()?.crisisWarning;
    return v === 1 ? "已触发" : v === 0 ? "未触发" : "--";
  });

  /* PAD */
  const padItems = computed(() => {
    const d = diagnosis();
    return [
      { name: "P", label: "愉悦度 Pleasure", mean: toNumber(d?.avgP), std: toNumber(d?.stdP), color: "#6c63ff" },
      { name: "A", label: "唤醒度 Arousal", mean: toNumber(d?.avgA), std: toNumber(d?.stdA), color: "#fa8c16" },
      { name: "D", label: "支配度 Dominance", mean: toNumber(d?.avgD), std: toNumber(d?.stdD), color: "#52c41a" },
    ];
  });

  /* 社会功能 */
  const socialImpactType = computed<"success" | "warning" | "danger" | "info">(() => {
    const v = diagnosis()?.socialFunctionImpact ?? "";
    if (v.includes("重")) return "danger";
    if (v.includes("中")) return "warning";
    if (v.includes("无") || v.includes("正常")) return "success";
    return "info";
  });

  const socialSupportText = computed(() => {
    const map: Record<number, string> = { 0: "良好", 1: "一般", 2: "较差", 3: "匮乏", 4: "无法判断" };
    const v = diagnosis()?.socialSupportLevel;
    return v != null ? map[v] ?? "无法判断" : "--";
  });

  /* AI建议优先级 */
  const suggestionPriority = computed(() => diagnosis()?.suggestionPriority ?? 4);
  const priorityMeta = computed(() => PRIORITY_META[suggestionPriority.value] ?? PRIORITY_META[4]);

  /* 标签拆分 */
  const symptomTagList = computed(() => splitTags(diagnosis()?.symptomTags));
  const triggerKeywords = computed(() => splitTags(diagnosis()?.coreTriggerKeywords));
  const protectiveFactorsList = computed(() => splitTags(diagnosis()?.protectiveFactors));

  return {
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
  };
}