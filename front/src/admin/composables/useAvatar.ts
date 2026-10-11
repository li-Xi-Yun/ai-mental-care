import type { GradEnumMetaItem } from "@/admin/types";
import { getUserGenderMeta } from "@/admin/constants/user";

/**
 * 头像相关工具：取姓名/账号首字母、按性别生成渐变背景。
 * 从 user/index.vue 的 avatarText / avatarStyle / avatarLargeText 中抽离。
 */
export function useAvatar() {
  /** 取首个字符并转大写（中文字符保持不变） */
  function letterOf(name?: string): string {
    const t = name?.trim();
    return (t || "?").charAt(0).toUpperCase();
  }

  /** 按性别元数据生成头像渐变背景样式 */
  function genderGradStyle(g?: number): Record<string, string> {
    return { background: getUserGenderMeta(g).grad };
  }

  /** 自定义渐变背景样式 */
  function gradStyle(grad: string): Record<string, string> {
    return { background: grad };
  }

  return { letterOf, genderGradStyle, gradStyle };
}

export type UseAvatar = ReturnType<typeof useAvatar>;

/** 独立可用的纯函数版本（不依赖 composable 生命周期） */
export function avatarLetter(name?: string): string {
  const t = name?.trim();
  return (t || "?").charAt(0).toUpperCase();
}

export function avatarGenderGradStyle(g?: number): Record<string, string> {
  return { background: getUserGenderMeta(g).grad };
}

export type { GradEnumMetaItem };
