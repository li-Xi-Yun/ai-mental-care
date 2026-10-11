import { computed } from "vue";
import { EMOTION_COLORS } from "@/shared/api/config";
import dayjs from "@/shared/utils/dayjs";

/* ==================== 常量 ==================== */

const RING_C = 2 * Math.PI * 40;
const FALLBACK_PALETTE = ["#6c63ff", "#ff6b6b", "#fa8c16", "#52c41a", "#1890ff", "#13c2c2", "#722ed1", "#eb2f96"];

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

export const TREND_META: Record<string, { icon: string; text: string; type: "up" | "down" | "stable" }> = {
  escalating: { icon: "↑", text: "较上轮升级", type: "up" },
  deescalating: { icon: "↓", text: "较上轮缓和", type: "down" },
  stable: { icon: "→", text: "与上轮持平", type: "stable" },
  fluctuating: { icon: "↕", text: "波动明显", type: "up" },
  initial: { icon: "·", text: "首轮无对比", type: "stable" },
};

export const STOPWORDS = new Set([
  "的", "了", "我", "你", "他", "她", "它", "我们", "你们", "他们", "这个", "那个", "就是", "现在",
  "最近", "因为", "所以", "但是", "如果", "然后", "而且", "一个", "什么", "怎么", "感觉", "觉得",
  "有些", "一点", "还是", "可以", "没有", "真的", "时候", "来说", "对", "让", "把", "被", "在",
  "和", "与", "也", "都", "很", "太", "比较", "会", "想", "要", "能", "不", "是", "有", "吗", "吧",
  "啊", "呢", "哈", "嗯", "哦",
]);

/* ==================== 类型 ==================== */

export interface EmotionAnalysisItem {
  id: string;
  roundNum: number;
  analysisContent?: string;
  emotionLabel?: string;
  emotionSubLabel?: string;
  emotionConfidence?: number;
  emotionIntensity?: number;
  emotionTrend?: string;
  pScore?: number;
  aScore?: number;
  dScore?: number;
  negativeEmotionRatio?: number;
  neutralEmotionRatio?: number;
  positiveEmotionRatio?: number;
  createdTime?: string;
}

export interface RatioSeg {
  label: string;
  flex: number;
  pct: string;
  color: string;
}

export interface PadBar {
  key: string;
  label: string;
  width: number;
  left: number;
  color: string;
  text: string;
}

export interface RingSegment {
  label: string;
  color: string;
  dasharray: string;
  dashoffset: number;
  pct: string;
  flex: number;
}

/* ==================== 数值工具 ==================== */

export function toNumber(v?: number | null): number | null {
  if (v === null || v === undefined) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

export function clamp01(v: number): number {
  return Math.min(1, Math.max(0, v));
}

export function toPct(v?: number | null, digits = 0): string {
  const n = toNumber(v);
  return n === null ? "--" : `${(n * 100).toFixed(digits)}%`;
}

/* ==================== 情绪展示工具 ==================== */

export function zhOf(label?: string): string {
  if (!label) return "—";
  return LABEL_ZH[label] ?? label;
}

export function emotionColor(label?: string, index = 0): string {
  if (!label) return FALLBACK_PALETTE[index % FALLBACK_PALETTE.length];
  const zh = LABEL_ZH[label] ?? label;
  return EMOTION_COLORS[zh] ?? FALLBACK_PALETTE[index % FALLBACK_PALETTE.length];
}

export function chipStyle(label?: string, index = 0) {
  const color = emotionColor(label, index);
  return { color, background: `${color}1a`, borderColor: `${color}38` };
}

export function kwTagStyle(index: number) {
  const colors = ["#6c63ff", "#1890ff", "#13c2c2", "#52c41a", "#fa8c16", "#722ed1", "#eb2f96", "#ff6b6b"];
  const color = colors[index % colors.length];
  return { color, background: `${color}12` };
}

export function trendMeta(trend?: string): { icon: string; text: string; type: "up" | "down" | "stable" } {
  if (!trend) return { icon: "·", text: "暂无趋势", type: "stable" };
  return TREND_META[trend] ?? { icon: "·", text: trend, type: "stable" };
}

/* ==================== 情绪分布 ==================== */

export function ratioSegsOf(e: EmotionAnalysisItem | null): RatioSeg[] {
  if (!e) return [];
  const values = [toNumber(e.negativeEmotionRatio), toNumber(e.neutralEmotionRatio), toNumber(e.positiveEmotionRatio)].map((v) =>
    v === null ? 0 : clamp01(v),
  );
  const total = values.reduce((a, b) => a + b, 0) || 1;
  const metas = [
    { label: "负向", color: "#ff6b6b" },
    { label: "中性", color: "#6c63ff" },
    { label: "正向", color: "#52c41a" },
  ];
  return metas.map((meta, i) => {
    const flex = total > 0 ? values[i] : 0;
    return { ...meta, flex, pct: ((flex / total) * 100).toFixed(0) };
  });
}

export function buildRingSegments(e: EmotionAnalysisItem | null): RingSegment[] {
  if (!e) return [];
  const values = [toNumber(e.negativeEmotionRatio), toNumber(e.neutralEmotionRatio), toNumber(e.positiveEmotionRatio)].map((v) =>
    v === null ? 0 : clamp01(v),
  );
  const total = values.reduce((a, b) => a + b, 0);
  if (total <= 0) return [];
  const metas = [
    { label: "负向", color: "#ff6b6b" },
    { label: "中性", color: "#6c63ff" },
    { label: "正向", color: "#52c41a" },
  ];
  let acc = 0;
  return metas
    .map((meta, i) => {
      const pctVal = values[i] / total;
      const len = RING_C * pctVal;
      const start = acc * RING_C;
      acc += pctVal;
      return {
        ...meta,
        dasharray: `${len} ${RING_C - len}`,
        dashoffset: -start,
        pct: (pctVal * 100).toFixed(0),
        flex: values[i],
      };
    })
    .filter((s) => s.flex > 0);
}

/* ==================== PAD 三维情绪 ==================== */

export function buildPadBars(e: EmotionAnalysisItem | null): PadBar[] {
  if (!e) return [];
  const specs = [
    { key: "pScore", label: "愉悦度 P", posColor: "#6c63ff", negColor: "#ff6b6b" },
    { key: "aScore", label: "唤醒度 A", posColor: "#fa8c16", negColor: "#fa8c16" },
    { key: "dScore", label: "支配度 D", posColor: "#52c41a", negColor: "#ff4d4f" },
  ] as const;
  return specs.map((s) => {
    const n = toNumber(e[s.key]);
    if (n === null) return { key: s.key, label: s.label, width: 0, left: 50, color: "#dfe3ea", text: "—" };
    const width = Math.min(50, Math.abs(n) * 50);
    return {
      key: s.key,
      label: s.label,
      width,
      left: n >= 0 ? 50 : 50 - width,
      color: n >= 0 ? s.posColor : s.negColor,
      text: `${n > 0 ? "+" : ""}${n.toFixed(2)}`,
    };
  });
}

export function padBarStyle(pad: PadBar) {
  return { left: `${pad.left}%`, width: `${pad.width}%`, backgroundColor: pad.color };
}

/* ==================== 关键词提取 ==================== */

export function extractKeywords(texts: string[], limit = 8): string[] {
  const freq = new Map<string, number>();
  for (const text of texts) {
    const tokens = text
      .replace(/[，。！？、；：,.!?;:"'""''（）()【】\[\]·\s\n]/g, " ")
      .split(" ")
      .map((s) => s.trim())
      .filter((s) => s.length >= 2 && !STOPWORDS.has(s));
    for (const w of tokens) {
      freq.set(w, (freq.get(w) ?? 0) + 1);
    }
  }
  return [...freq.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map((entry) => entry[0]);
}

/* ==================== 时间排序 ==================== */

export function cmpByTime(a: { createdTime: string }, b: { createdTime: string }): number {
  const ta = dayjs(a.createdTime);
  const tb = dayjs(b.createdTime);
  const va = ta.isValid() ? ta.valueOf() : 0;
  const vb = tb.isValid() ? tb.valueOf() : 0;
  return va - vb;
}

/* ==================== 组合式函数 ==================== */

export function useEmotionAnalysis(emotionList: () => EmotionAnalysisItem[], allMessages: () => { content: string; type: string; roundNum: number }[]) {
  const currentEmotion = computed(() => emotionList()[0] ?? null);

  const ringSegments = computed(() => buildRingSegments(currentEmotion.value));

  const ratioSegments = computed(() => ratioSegsOf(currentEmotion.value));

  const currentKeywords = computed(() => {
    const e = currentEmotion.value;
    const msgs = allMessages();
    let texts = msgs
      .filter((m) => m.type === "user" && (e && e.roundNum ? m.roundNum === e.roundNum : true))
      .map((m) => m.content);
    if (!texts.length) {
      texts = msgs.filter((m) => m.type === "user").slice(-3).map((m) => m.content);
    }
    return extractKeywords(texts, 8);
  });

  return {
    currentEmotion,
    ringSegments,
    ratioSegments,
    currentKeywords,
  };
}