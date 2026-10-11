/**
 * 临时权限模块常量（状态元数据 / 权限树字段映射）
 * 从 temp-permission/index.vue 的 scoped 常量中抽离，供 useTempPermissionList.ts 与子组件直接引用。
 */

/** 临时权限状态元数据（0 有效 / 1 已作废 / 2 已过期） */
export const TEMP_PERMISSION_STATUS_META: Record<
  number,
  { text: string; tag: "success" | "danger" | "info"; type: "primary" | "danger" | "info" }
> = {
  0: { text: "有效", tag: "success", type: "primary" },
  1: { text: "已作废", tag: "danger", type: "danger" },
  2: { text: "已过期", tag: "info", type: "info" },
};

/** 状态文案（未定义/越界返回「未知」） */
export function tempPermissionStatusText(status?: number): string {
  return TEMP_PERMISSION_STATUS_META[status ?? 0]?.text ?? "未知";
}

/** 状态 → el-tag 类型 */
export function tempPermissionStatusTagType(status?: number): "success" | "danger" | "info" {
  return TEMP_PERMISSION_STATUS_META[status ?? 0]?.tag ?? "info";
}

/** 状态 → el-timeline-item 类型 */
export function tempPermissionStatusType(status?: number): "primary" | "danger" | "info" {
  return TEMP_PERMISSION_STATUS_META[status ?? 0]?.type ?? "info";
}

/** 权限树字段映射（name / permissions 与后端返回结构一致） */
export const TEMP_PERMISSION_TREE_PROPS = { label: "name", children: "permissions" };
