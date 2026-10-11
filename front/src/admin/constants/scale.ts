/**
 * 量表管理模块常量（admin 量表列表页 / 分类管理弹窗共用）：
 * 启用与删除状态下拉选项、新增量表表单默认值、分类徽章配色。
 */

/** 下拉选项结构 */
export interface ScaleSelectOption {
  label: string;
  value: number;
}

/** 量表启用状态选项（1 启用 / 0 禁用） */
export const SCALE_ENABLE_OPTIONS: ScaleSelectOption[] = [
  { label: "启用", value: 1 },
  { label: "禁用", value: 0 },
];

/** 量表删除状态选项（0 正常 / 1 已删除） */
export const SCALE_DELETED_OPTIONS: ScaleSelectOption[] = [
  { label: "正常", value: 0 },
  { label: "已删除", value: 1 },
];

/** 新增量表表单默认值（作答配置等） */
export const SCALE_FORM_DEFAULTS = {
  status: 1,
  allowRepeat: 1,
  coolMinutes: 1440,
  timeLimit: 0,
  anonymous: 0,
} as const;

/** 分类徽章配色元数据 */
export interface ScaleCategoryColorMeta {
  color: string;
  bg: string;
  border: string;
}

/** 分类徽章配色：命中预设取主题色 */
export const SCALE_CATEGORY_COLORS: Record<string, ScaleCategoryColorMeta> = {
  焦虑: { color: "#ef4444", bg: "#fef2f2", border: "#fecaca" },
  抑郁: { color: "#3b82f6", bg: "#eff6ff", border: "#bfdbfe" },
  睡眠: { color: "#f59e0b", bg: "#fffbeb", border: "#fde68a" },
  压力: { color: "#10b981", bg: "#f0fdf4", border: "#a7f3d0" },
  创伤: { color: "#8b5cf6", bg: "#f5f3ff", border: "#ddd6fe" },
  情绪障碍: { color: "#ec4899", bg: "#fdf2f8", border: "#fbcfe8" },
  综合评估: { color: "#0ea5e9", bg: "#f0f9ff", border: "#bae6fd" },
  社会功能: { color: "#06b6d4", bg: "#ecfeff", border: "#a5f3fc" },
  进食障碍: { color: "#f97316", bg: "#fff7ed", border: "#fed7aa" },
  精神症状: { color: "#64748b", bg: "#f1f5f9", border: "#cbd5e1" },
};

/** 分类徽章候选调色板：未命中预设时按名称哈希从候选调色板取色 */
export const SCALE_CATEGORY_PALETTE: ScaleCategoryColorMeta[] = [
  { color: "#ff5b6e", bg: "#fff0f1", border: "#ffd6da" },
  { color: "#2f80ed", bg: "#edf5ff", border: "#c9dffb" },
  { color: "#f2994a", bg: "#fff7ed", border: "#fde3c2" },
  { color: "#10b981", bg: "#ecfdf5", border: "#b9efd6" },
  { color: "#8b5cf6", bg: "#f5f3ff", border: "#ddd6fe" },
  { color: "#06b6d4", bg: "#ecfeff", border: "#a5f3fc" },
  { color: "#ec4899", bg: "#fdf2f8", border: "#fbcfe8" },
  { color: "#64748b", bg: "#f8fafc", border: "#cbd5e1" },
];
