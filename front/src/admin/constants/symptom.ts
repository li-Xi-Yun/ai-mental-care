/**
 * 症状字典模块常量（症状大类 / 严重等级元数据及展示映射）。
 * 从 symptom/index.vue 的 scoped 常量中抽离，供列表页与表单弹窗复用。
 */

/** 症状大类兜底选项（接口不可用或返回为空时使用） */
export const FALLBACK_CATEGORIES = ["情绪症状", "躯体症状", "认知症状", "行为症状"] as const;

/** 严重等级（1-5）元数据：文案与配色 */
export const SEVERITY_META: Record<number, { label: string; color: string; bg: string }> = {
  1: { label: "轻微", color: "#10b981", bg: "#d1fae5" },
  2: { label: "轻度", color: "#14b8a6", bg: "#ccfbf1" },
  3: { label: "中度", color: "#f59e0b", bg: "#fef3c7" },
  4: { label: "较重", color: "#f97316", bg: "#ffedd5" },
  5: { label: "严重", color: "#ef4444", bg: "#fee2e2" },
};

/** 症状大类 → 标签配色（color / bg / border） */
export const CATEGORY_COLORS: Record<string, { color: string; bg: string; border: string }> = {
  情绪症状: { color: "#8b5cf6", bg: "#f5f3ff", border: "#ede9fe" },
  躯体症状: { color: "#0ea5e9", bg: "#f0f9ff", border: "#e0f2fe" },
  认知症状: { color: "#f59e0b", bg: "#fffbeb", border: "#fef3c7" },
  行为症状: { color: "#10b981", bg: "#f0fdf4", border: "#d1fae5" },
};

/** 严重等级颜色（未设置/越界兜底为灰） */
export function levelColor(v?: number): string {
  if (v == null || !SEVERITY_META[v]) return "#94a3b8";
  return SEVERITY_META[v].color;
}

/** 严重等级文案（未设置/越界兜底为「未设置」） */
export function severityText(v?: number): string {
  if (v == null || !SEVERITY_META[v]) return "未设置";
  return SEVERITY_META[v].label;
}

/** 严重等级选择器选中段样式 */
export function levelSegStyle(v: number): Record<string, string> {
  const meta = SEVERITY_META[v];
  return { color: meta.color, background: meta.bg };
}

/** 症状大类标签样式（未知分类兜底为靛蓝） */
export function categoryStyle(cat?: string): Record<string, string> {
  const meta = (cat && CATEGORY_COLORS[cat]) || { color: "#6366f1", bg: "#eef2ff", border: "#e0e7ff" };
  return { color: meta.color, background: meta.bg, borderColor: meta.border };
}
