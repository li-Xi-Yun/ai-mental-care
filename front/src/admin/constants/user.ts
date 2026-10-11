import type { EnumMetaItem, GradEnumMetaItem, TagType } from "@/admin/types";

/**
 * 用户账号状态元数据（0 正常 / 1 异常 / 2 封禁 / 3 注销）
 * 从 user/index.vue、admin/index.vue 的 scoped 常量中抽离，供多页面复用。
 */
export const USER_STATUS_META: Record<number, EnumMetaItem> = {
  0: { label: "正常", color: "#10b981", bg: "#ecfdf5", border: "#a7f3d0" },
  1: { label: "异常", color: "#f59e0b", bg: "#fffbeb", border: "#fde68a" },
  2: { label: "封禁", color: "#ef4444", bg: "#fef2f2", border: "#fecaca" },
  3: { label: "注销", color: "#94a3b8", bg: "#f1f5f9", border: "#e2e8f0" },
};

/** 用户状态下拉选项 */
export const USER_STATUS_OPTIONS: { value: number; label: string }[] = [0, 1, 2, 3].map(
  (v) => ({ value: v, label: USER_STATUS_META[v].label }),
);

/** 用户状态 → Element tag 类型 */
export const USER_STATUS_TAG_TYPE: Record<number, TagType | undefined> = {
  0: "success",
  1: "warning",
  2: "danger",
  3: "info",
};

/**
 * 性别元数据（0 女 / 1 男 / 2 未知），含头像渐变 grad。
 * 从 user/index.vue 的 scoped 常量中抽离。
 */
export const USER_GENDER_META: Record<number, GradEnumMetaItem> = {
  0: { label: "女", color: "#db2777", bg: "#fdf2f8", border: "#fbcfe8", grad: "linear-gradient(135deg,#ec4899,#f472b6)" },
  1: { label: "男", color: "#2563eb", bg: "#eff6ff", border: "#bfdbfe", grad: "linear-gradient(135deg,#2563eb,#60a5fa)" },
  2: { label: "未知", color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0", grad: "linear-gradient(135deg,#94a3b8,#cbd5e1)" },
};

/** 默认（未定义/越界）性别选项 */
export const USER_GENDER_UNKNOWN = 2;

/** 取性别元数据（带兜底） */
export function getUserGenderMeta(g?: number): GradEnumMetaItem {
  return g === undefined || g === null ? USER_GENDER_META[USER_GENDER_UNKNOWN] : (USER_GENDER_META[g] ?? USER_GENDER_META[USER_GENDER_UNKNOWN]);
}