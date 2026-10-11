/**
 * 管理员个人资料模块常量（账号状态元数据）
 * 从 profile/index.vue 的 scoped 常量中抽离，供 useProfile.ts 直接引用。
 */

/** 状态元数据（0 正常 / 1 异常 / 2 封禁 / 3 注销） */
export const PROFILE_STATUS_META: Record<number, { label: string; cls: string }> = {
  0: { label: "正常", cls: "ok" },
  1: { label: "异常", cls: "warn" },
  2: { label: "封禁", cls: "ban" },
  3: { label: "注销", cls: "off" },
};

/** 状态文案（未定义/越界返回「未知」） */
export function profileStatusLabel(status?: number): string {
  return status === undefined || status === null ? "未知" : (PROFILE_STATUS_META[status]?.label ?? "未知");
}
