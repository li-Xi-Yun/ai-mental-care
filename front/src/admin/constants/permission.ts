/**
 * 权限管理模块常量：角色 / 权限状态元数据与下拉选项。
 * 供 role/index.vue、permission/index.vue 及其子组件复用。
 */

/** 角色状态元数据（0 正常 / 1 停用） */
export const ROLE_STATUS_META: Record<number, { label: string }> = {
  0: { label: "正常" },
  1: { label: "停用" },
};

/** 角色状态下拉选项 */
export const ROLE_STATUS_OPTIONS: { value: number; label: string }[] = [0, 1].map((v) => ({
  value: v,
  label: ROLE_STATUS_META[v].label,
}));

/** 权限状态元数据（0 正常 / 1 停用），tagType 供状态标签使用 */
export const PERMISSION_STATUS_META: Record<number, { label: string; tagType: "success" | "danger" }> = {
  0: { label: "正常", tagType: "success" },
  1: { label: "停用", tagType: "danger" },
};
