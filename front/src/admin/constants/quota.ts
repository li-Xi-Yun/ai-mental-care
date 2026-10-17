import type { EnumMetaItem } from "@/admin/types";

/**
 * AI 对话配额-资源类型常量
 * 与后端 QuotaConfig 常量对齐：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D
 */
export const QUOTA_RESOURCE_TYPES = {
  TEXT: 1,
  ASR: 2,
  TTS: 3,
  AVATAR_2D: 4,
} as const;

/**
 * 资源类型展示元数据（名称 + 徽章配色）
 * 名称优先以后端 QuotaConfigVO.resourceName / QuotaUserVO.resourceName 返回为准，
 * 此处仅作常量兜底（如接口未返回名称）。
 */
export interface QuotaResourceMeta extends EnumMetaItem {
  /** 资源展示名称 */
  name: string;
}

export const QUOTA_RESOURCE_META: Record<number, QuotaResourceMeta> = {
  [QUOTA_RESOURCE_TYPES.TEXT]: { name: "AI文本对话", label: "文本", color: "#6366f1", bg: "#eef2ff", border: "#e0e7ff" },
  [QUOTA_RESOURCE_TYPES.ASR]: { name: "AI语音识别", label: "语音识别", color: "#0ea5e9", bg: "#f0f9ff", border: "#e0f2fe" },
  [QUOTA_RESOURCE_TYPES.TTS]: { name: "AI语音合成", label: "语音合成", color: "#f59e0b", bg: "#fffbeb", border: "#fef3c7" },
  [QUOTA_RESOURCE_TYPES.AVATAR_2D]: { name: "AI 2D 形象播报", label: "2D形象", color: "#8b5cf6", bg: "#f5f3ff", border: "#ede9fe" },
};

/** 资源类型名称兜底（接口未返回名称或未知类型时使用） */
export function quotaResourceName(type?: number | null): string {
  if (type == null) return "未知资源";
  return QUOTA_RESOURCE_META[type]?.name ?? "未知资源";
}

/** 资源类型徽章配色兜底 */
export function quotaResourceColor(type?: number | null): string {
  if (type == null) return "#64748b";
  return QUOTA_RESOURCE_META[type]?.color ?? "#64748b";
}

/** 资源类型下拉选项（按类型顺序排列） */
export const QUOTA_RESOURCE_OPTIONS = Object.values(QUOTA_RESOURCE_TYPES).map((v) => ({
  value: v,
  label: quotaResourceName(v),
}));

/**
 * 可用状态元数据（1=可用，0=已停用）
 * available 语义：资源是否允许使用；停用时消费点直接拒绝。
 */
export const QUOTA_AVAILABLE_META: Record<number, EnumMetaItem> = {
  0: { label: "已停用", color: "#ef4444", bg: "#fef2f2", border: "#fecaca" },
  1: { label: "可用", color: "#10b981", bg: "#ecfdf5", border: "#a7f3d0" },
};

/** 可用状态下拉选项 */
export const QUOTA_AVAILABLE_OPTIONS = [1, 0].map((v) => ({ value: v, label: QUOTA_AVAILABLE_META[v].label }));

/**
 * 限流状态元数据（1=已启用，0=未启用）
 * enabled 语义：是否检查并扣减配额；关闭时直接放行、不扣减。
 */
export const QUOTA_ENABLED_META: Record<number, EnumMetaItem> = {
  0: { label: "未启用", color: "#94a3b8", bg: "#f1f5f9", border: "#e2e8f0" },
  1: { label: "已启用", color: "#4f46e5", bg: "#eef2ff", border: "#e0e7ff" },
};

/** 限流状态下拉选项 */
export const QUOTA_ENABLED_OPTIONS = [1, 0].map((v) => ({ value: v, label: QUOTA_ENABLED_META[v].label }));

/**
 * 计量周期元数据（0=NONE 终身累计，1=MONTHLY 按自然月重置）
 */
export const QUOTA_CYCLE_META: Record<number, EnumMetaItem> = {
  0: { label: "不限周期", color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0" },
  1: { label: "按月", color: "#f59e0b", bg: "#fffbeb", border: "#fde68a" },
};

/** 计量周期下拉选项 */
export const QUOTA_CYCLE_OPTIONS = [0, 1].map((v) => ({ value: v, label: QUOTA_CYCLE_META[v].label }));

/**
 * 不限量（豁免）状态元数据（0=正常限流，1=不限量）
 * unlimited 语义：豁免用户不限额、不计数、不写消费审计。
 */
export const QUOTA_UNLIMITED_META: Record<number, EnumMetaItem> = {
  0: { label: "正常限流", color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0" },
  1: { label: "不限量", color: "#10b981", bg: "#ecfdf5", border: "#a7f3d0" },
};
