/**
 * 量表列表/详情页共用视觉元信息
 *
 * 从量表名称推导表情图标、按分类轮换主题渐变，以及汇总题型文案。
 * 供「量表测评」目录页与「量表详情」介绍页复用，避免两处各自维护。
 */
import type { ScaleDetailVO, ScaleVO } from "@/portal/api/scale/scale";

export interface ScaleTint {
  gradient: string;
  soft: string;
  fg: string;
}

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

export function iconFor(scale: ScaleVO): string {
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

export function tintFor(categoryId: number, scaleId: number): ScaleTint {
  const base = Number.isFinite(categoryId) ? categoryId : scaleId;
  return PALETTES[Math.abs(base) % PALETTES.length];
}

/** 汇总题型统计："单选 20 · 多选 3" */
export function buildQuestionTypeText(detail: ScaleDetailVO | undefined): string {
  if (!detail?.questionTypes?.length) return "";
  return detail.questionTypes
    .filter((t) => t.count > 0)
    .map((t) => `${t.questionTypeLabel} ${t.count}`)
    .join(" · ");
}