/** AI 节点模型服务商选项（value 与后端枚举一致） */
export const AI_NODE_MODEL_TYPE_OPTIONS: ReadonlyArray<{ label: string; value: number }> = [
  { label: "本地 Ollama", value: 0 },
  { label: "DeepSeek", value: 1 },
  { label: "阿里 DashScope", value: 2 },
  { label: "OpenAI 兼容", value: 3 },
];

/** 各服务商模型名称字段（下标与 AI_NODE_MODEL_TYPE_OPTIONS.value 对应） */
export const AI_NODE_MODEL_NAME_FIELDS = [
  "ollamaModelName",
  "deepseekModelName",
  "dashscopeModelName",
  "openaiModelName",
] as const;

/** 生成参数 / 重试策略等数字字段的默认值（详情接口缺省时兜底） */
export const AI_NODE_FORM_DEFAULTS = {
  enabled: 1,
  sort: 0,
  modelType: 1,
  maxToken: 2048,
  temperature: 0.7,
  topP: 0.85,
  topK: 50,
  frequencyPenalty: 0,
  presencePenalty: 0,
  responseFormat: 0,
  retryMaxAttempts: 3,
  retryDelay: 1000,
  retryMultiplier: 2,
} as const;

export const AI_NODE_STOP_SEQUENCES_PLACEHOLDER = '如 ["\\n\\n\\n","```"]';

/** 列表页模型类型彩色徽章配色（下标与 AI_NODE_MODEL_TYPE_OPTIONS.value 对应，与后台 indigo 主题协调） */
export const AI_NODE_MODEL_TYPE_COLORS: ReadonlyArray<{ color: string; bg: string; border: string }> = [
  { color: "#7c3aed", bg: "#f5f3ff", border: "#ede9fe" }, // Ollama 紫
  { color: "#4f46e5", bg: "#eef2ff", border: "#e0e7ff" }, // DeepSeek Indigo
  { color: "#0ea5e9", bg: "#f0f9ff", border: "#e0f2fe" }, // DashScope 蓝
  { color: "#10b981", bg: "#f0fdf4", border: "#d1fae5" }, // OpenAI 绿
];

/** 列表页分组标签配色（动态 hash 到一组柔和色板，保证同组同色） */
export const AI_NODE_GROUP_COLORS: ReadonlyArray<{ color: string; bg: string; border: string }> = [
  { color: "#6366f1", bg: "#eef2ff", border: "#e0e7ff" },
  { color: "#8b5cf6", bg: "#f5f3ff", border: "#ede9fe" },
  { color: "#0ea5e9", bg: "#f0f9ff", border: "#e0f2fe" },
  { color: "#f59e0b", bg: "#fffbeb", border: "#fef3c7" },
  { color: "#10b981", bg: "#f0fdf4", border: "#d1fae5" },
  { color: "#f43f5e", bg: "#fff1f2", border: "#ffe4e6" },
];