import type { Component } from "vue";

/** 枚举元数据项：状态/性别的展示标签与配色（供 useEnumMeta 与 StatusBadge 使用） */
export interface EnumMetaItem {
  label: string;
  color: string;
  bg: string;
  border: string;
}

/** 可带渐变背景的枚举元数据项（用于头像等场景） */
export interface GradEnumMetaItem extends EnumMetaItem {
  grad: string;
}

/** 状态徽章可用的 Element Plus tag 类型 */
export type TagType = "success" | "warning" | "danger" | "info" | "primary";

/** 统计卡 tone 变体（对应公共样式中 stat-icon-* 渐变） */
export type StatTone = "indigo" | "green" | "red" | "yellow" | "gray";

/** 统计卡配置项（供 StatCard 使用；icon 为全局注册的图标名或组件） */
export interface StatItem {
  icon?: string | Component;
  tone?: StatTone;
  num?: string | number;
  label?: string;
}
