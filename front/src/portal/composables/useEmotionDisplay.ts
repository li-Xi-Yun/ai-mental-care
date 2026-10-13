/**
 * 情绪展示工具（职责单一：把情绪分析的原始字段映射为 UI 文案、颜色、图表派生数据）。
 *
 * - 后端 `emotionLabel` 存英文枚举（anger/neutral/happy/...），也可能出现中文（"开心"）。
 * - 颜色/视觉键参照设计稿 3-conversation.html 的 emotion 语义（anxiety/low/nervous/calm/expect/irritated）。
 */
import { EMOTION_COLORS } from "@/shared/api/config";
import dayjs from "@/shared/utils/dayjs";
import type { EmotionAnalysisItem } from "./useEmotionList";

/** 中性色回退（无法识别时使用） */
const NEUTRAL_KEY = "calm";
/** 兜底调色板（按顺序轮换） */
const FALLBACK_PALETTE = ["#6c63ff", "#ff6b6b", "#fa8c16", "#52c41a", "#1890ff", "#13c2c2", "#722ed1", "#eb2f96"];

/** 后端英文枚举 → UI 视觉键（与设计稿 emotion 语义一致） */
const EN_KEY_MAP: Record<string, string> = {
  anger: "irritated",
  sadness: "low",
  fear: "nervous",
  anxiety: "anxiety",
  disgust: "irritated",
  surprise: "expect",
  happy: "expect",
  neutral: "calm",
  guilt: "low",
  shame: "low",
  hope: "expect",
  confusion: "nervous",
};

/** 后端英文枚举 → 中文标签 */
export const LABEL_ZH: Record<string, string> = {
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

/** 趋势枚举 → 图标与文案（设计稿 trendMap） */
const TREND_META_MAP: Record<string, { icon: string; text: string; type: "up" | "down" | "stable" }> = {
  escalating: { icon: "↑", text: "较上轮上升", type: "up" },
  deescalating: { icon: "↓", text: "较上轮下降", type: "down" },
  stable: { icon: "→", text: "与上轮持平", type: "stable" },
  fluctuating: { icon: "↕", text: "波动明显", type: "up" },
  initial: { icon: "·", text: "首轮无对比", type: "stable" },
};

/** 视觉键 → 中文标签（设计稿 emotionLabelMap） */
const KEY_ZH: Record<string, string> = {
  calm: "平静",
  expect: "期待",
  anxiety: "焦虑",
  low: "低落",
  nervous: "紧张",
  irritated: "烦躁",
};

/** 中文标签 → 视觉键（后端可能直接存中文） */
const ZH_KEY_MAP: Record<string, string> = {
  平静: "calm",
  期待: "expect",
  焦虑: "anxiety",
  低落: "low",
  开心: "expect",
  紧张: "nervous",
  愤怒: "irritated",
  烦躁: "irritated",
};

/* ==================== 标签 / 颜色 ==================== */

/**
 * 后端 emotionLabel → UI 视觉键。
 * 先是英文枚举映射；若已是中文标签，走中文回退；最后默认中性键。
 */
export function toEmotionKey(label?: string | null): string {
  if (!label) return NEUTRAL_KEY;
  if (EN_KEY_MAP[label]) return EN_KEY_MAP[label];
  if (ZH_KEY_MAP[label]) return ZH_KEY_MAP[label];
  return NEUTRAL_KEY;
}

/** 视觉键 → 中文名 */
export function emotionKeyZh(key: string): string {
  return KEY_ZH[key] ?? key;
}

/** 视觉键 → 词条颜色（优先 Config 配置项，未知时走兜底调色板） */
export function emotionKeyColor(key: string, index = 0): string {
  return EMOTION_COLORS[emotionKeyZh(key)] ?? FALLBACK_PALETTE[index % FALLBACK_PALETTE.length];
}

/** 展示文案：优先细分标签 → emotionLabel 中文 → 键中文 */
export function displayLabel(emotionLabel?: string | null, emotionSubLabel?: string | null): string {
  if (emotionSubLabel) return emotionSubLabel;
  if (emotionLabel) return LABEL_ZH[emotionLabel] ?? emotionLabel;
  return "未知情绪";
}

/** 细分标签样式（颜色取主情绪键的浅色背景文字） */
export function chipStyle(label: string, index = 0) {
  const color = emotionKeyColor(label, index);
  return { color, background: `${color}1a`, borderColor: `${color}38` };
}

/** 趋势 → 图标/文案/类型 */
export function trendMeta(trend?: string | null): { icon: string; text: string; type: "up" | "down" | "stable" } {
  if (!trend) return { icon: "·", text: "暂无趋势", type: "stable" };
  return TREND_META_MAP[trend] ?? { icon: "·", text: trend, type: "stable" };
}

/* ==================== 数值工具 ==================== */

export function toNumber(v?: number | null): number | null {
  if (v === null || v === undefined) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function clamp01(v: number): number {
  return Math.min(1, Math.max(0, v));
}

/** 百分比文案（0-1 → 百分数，null 显示 --） */
export function toPct(v?: number | null, digits = 0): string {
  const n = toNumber(v);
  return n === null ? "--" : `${(n * 100).toFixed(digits)}%`;
}

/* ==================== 情绪倾向分布（负向/中性/正向） ==================== */

export interface RatioSeg {
  label: string;
  flex: number;
  pct: string;
  color: string;
}

export function ratioSegsOf(e: EmotionAnalysisItem | null): RatioSeg[] {
  if (!e) return [];
  const values = [toNumber(e.negativeEmotionRatio), toNumber(e.neutralEmotionRatio), toNumber(e.positiveEmotionRatio)].map(
    (v) => (v === null ? 0 : clamp01(v)),
  );
  const total = values.reduce((a, b) => a + b, 0) || 1;
  const metas = [
    { label: "负向", color: "#dc2626" },
    { label: "中性", color: "#9ca3af" },
    { label: "正向", color: "#16a34a" },
  ];
  return metas.map((meta, i) => ({
    ...meta,
    flex: total > 0 ? values[i] : 0,
    pct: ((values[i] / total) * 100).toFixed(0),
  }));
}

/* ==================== PAD 三维情绪（-1~1 居中刻度） ==================== */

export interface PadBar {
  key: string;
  label: string;
  width: number;
  left: number;
  color: string;
  text: string;
}

export function buildPadBars(e: EmotionAnalysisItem | null): PadBar[] {
  if (!e) return [];
  const specs = [
    { key: "pScore", label: "愉悦度 P", posColor: "#16a34a", negColor: "#dc2626" },
    { key: "aScore", label: "唤醒度 A", posColor: "#f59e0b", negColor: "#f59e0b" },
    { key: "dScore", label: "支配度 D", posColor: "#16a34a", negColor: "#dc2626" },
  ] as const;
  return specs.map((s) => {
    const n = toNumber(e[s.key]);
    if (n === null) return { key: s.key, label: s.label, width: 0, left: 50, color: "#d1d5db", text: "—" };
    const pct = ((n + 1) / 2) * 100;
    const color = n >= 0 ? s.posColor : s.negColor;
    return {
      key: s.key,
      label: s.label,
      width: pct,
      left: n >= 0 ? 50 : 50 - pct,
      color,
      text: `${n > 0 ? "+" : ""}${n.toFixed(2)}`,
    };
  });
}

export function padBarStyle(pad: PadBar) {
  return { width: `${pad.width}%`, left: `${pad.left}%`, backgroundColor: pad.color };
}

/* ==================== 时间排序 / 展示 ==================== */

/** 按创建时间升序比较（用于列表排序） */
export function cmpByTime(a: { createdTime?: number | string }, b: { createdTime?: number | string }): number {
  const va = dayjs(a.createdTime).valueOf();
  const vb = dayjs(b.createdTime).valueOf();
  if (!Number.isFinite(va)) return 1;
  if (!Number.isFinite(vb)) return -1;
  return va - vb;
}

/** 时间文案（今天→HH:mm，昨天→昨天 HH:mm，更早→M月D日） */
export function msgTime(v?: number | string): string {
  if (v === undefined || v === null || v === "") return "";
  const d = dayjs(v);
  if (!d.isValid()) return String(v);
  const now = dayjs();
  if (d.isSame(now, "day")) return d.format("HH:mm");
  if (d.isSame(now.subtract(1, "day"), "day")) return `昨天 ${d.format("HH:mm")}`;
  return d.format("M月D日 HH:mm");
}