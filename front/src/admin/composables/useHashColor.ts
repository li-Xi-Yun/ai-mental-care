/**
 * 哈希取色：按字符串 key 稳定取一套渐变/浅底色/文字色/边框色。
 * 对齐 Blog-Front 的 colorFor / roleColorFor / colorForId 算法，用于
 * 权限分组图标、角色徽章、人员头像等"同 key 同色"的场景。
 */

export interface HashColor {
  /** 渐变背景（图标块/头像） */
  gradient: string;
  /** 浅色底（选中块/软标签背景） */
  softBg: string;
  /** 主文字色 */
  textColor: string;
  /** 边框色 */
  borderColor: string;
}

/** 6 套调色板（靛蓝/天蓝/翠绿/琥珀/玫红/紫） */
const PALETTES: readonly HashColor[] = [
  { gradient: "linear-gradient(135deg, #6366f1, #8b5cf6)", softBg: "#eef2ff", textColor: "#4f46e5", borderColor: "#c7d2fe" },
  { gradient: "linear-gradient(135deg, #0ea5e9, #06b6d4)", softBg: "#f0f9ff", textColor: "#0284c7", borderColor: "#bae6fd" },
  { gradient: "linear-gradient(135deg, #10b981, #14b8a6)", softBg: "#f0fdf4", textColor: "#059669", borderColor: "#a7f3d0" },
  { gradient: "linear-gradient(135deg, #f59e0b, #f97316)", softBg: "#fffbeb", textColor: "#d97706", borderColor: "#fde68a" },
  { gradient: "linear-gradient(135deg, #ec4899, #f43f5e)", softBg: "#fdf2f8", textColor: "#db2777", borderColor: "#fbcfe8" },
  { gradient: "linear-gradient(135deg, #8b5cf6, #a855f7)", softBg: "#f5f3ff", textColor: "#7c3aed", borderColor: "#ddd6fe" },
];

/** 稳定字符串哈希（同 key 同色；空值归入第 0 套） */
function hashString(key: string): number {
  let h = 0;
  for (let i = 0; i < key.length; i += 1) {
    h = (h * 31 + key.charCodeAt(i)) % 100000007;
  }
  return h;
}

/**
 * 纯函数版取色。
 * @param key 分组名 / roleKey / 人员标识等
 * @param offset 实体类型偏移（不同实体类型错开调色板，避免同页撞色）
 */
export function hashColorFor(key: string | number | null | undefined, offset = 0): HashColor {
  const k = key === null || key === undefined ? "" : String(key);
  if (!k) return PALETTES[0];
  return PALETTES[(hashString(k) + offset) % PALETTES.length];
}

/** Composable 版（仅包一层，保持与既有 composables 风格一致） */
export function useHashColor() {
  return { colorFor: hashColorFor };
}

export type UseHashColor = ReturnType<typeof useHashColor>;
