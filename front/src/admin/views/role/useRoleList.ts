import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getRolePage,
  getRoleDetail,
  addRole,
  updateRole,
  updateRoleStatus,
  deleteRole,
  getRolePermissions,
  assignRolePermissions,
} from "@/admin/api/permission/role";
import { getPermissionTree } from "@/admin/api/permission/permission";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import { ROLE_STATUS_OPTIONS } from "@/admin/constants/permission";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import type { EnumMetaItem } from "@/admin/types";
import type { Result } from "@/shared/api/types";

/** 角色列表项（分页列表 VO） */
export interface RoleItem {
  id: number;
  name: string;
  roleKey: string;
  status: number;
  remark?: string;
  createdTime?: string;
  updatedTime?: string;
}

/** 角色详情（详情接口补全，字段可能缺失） */
export interface RoleDetail {
  id?: number | string;
  name?: string;
  roleKey?: string;
  status?: number;
  remark?: string;
  createdTime?: string;
  updatedTime?: string;
}

/** 新增 / 编辑角色提交载荷（由 RoleFormDialog 校验后回传） */
export interface RoleSubmitPayload {
  name: string;
  roleKey: string;
  remark: string;
}

/** 权限项（权限树分组下的子项） */
export interface PermissionItem {
  id: number | string;
  name: string;
  perms?: string;
  status?: number;
}

/** 权限分组（分配权限弹窗按分组平铺渲染） */
export interface PermissionGroup {
  groupName: string;
  permissions: PermissionItem[];
}

/** 角色状态元数据（0 正常绿 / 1 停用灰；供 StatusBadge 与详情弹窗复用） */
export const ROLE_STATUS_STYLE_META: Record<number, EnumMetaItem> = {
  0: { label: "正常", color: "#059669", bg: "#ecfdf5", border: "#a7f3d0" },
  1: { label: "停用", color: "#6b7280", bg: "#f3f4f6", border: "#e5e7eb" },
};

/**
 * 角色管理页全部业务逻辑（单一职责拆分）：
 * 列表加载 / 搜索 / 分页、统计卡、新增编辑、状态切换、删除、详情与权限分配弹窗。
 * 弹窗子组件各自持有表单状态，通过事件回传提交载荷。
 */
export function useRoleList() {
  const { formatTime } = useTimeFormat();
  const roleStatus = useEnumMeta(ROLE_STATUS_STYLE_META);

  // ==================== 列表 ====================
  const loading = ref(false);
  const roleList = ref<RoleItem[]>([]);
  const total = ref(0);

  const query = reactive({ pageNum: 1, pageSize: DEFAULT_PAGE_SIZE, name: "", roleKey: "", status: undefined as number | undefined });

  async function fetchRoles() {
    loading.value = true;
    try {
      const res = (await getRolePage({ ...query })).data as Result<any>;
      const data = res.data ?? {};
      roleList.value = data.records ?? [];
      total.value = data.total ?? 0;
    } catch {
      ElMessage.error("加载角色列表失败");
    } finally {
      loading.value = false;
    }
  }

  function handleSearch() {
    query.pageNum = 1;
    fetchRoles();
  }

  function handleReset() {
    query.name = "";
    query.roleKey = "";
    query.status = undefined;
    query.pageNum = 1;
    fetchRoles();
  }

  function handleSizeChange() {
    query.pageNum = 1;
    fetchRoles();
  }

  // ==================== 统计卡 ====================
  const stats = ref({ total: 0, normal: 0, disabled: 0 });

  /** 轻量查询（pageSize=1）取总数；三项相互独立，单项失败按 0 计 */
  async function fetchStats() {
    const results = await Promise.allSettled([
      getRolePage({ pageNum: 1, pageSize: 1 }),
      getRolePage({ pageNum: 1, pageSize: 1, status: 0 }),
      getRolePage({ pageNum: 1, pageSize: 1, status: 1 }),
    ]);
    const totalOf = (r: PromiseSettledResult<Awaited<ReturnType<typeof getRolePage>>>) => {
      if (r.status !== "fulfilled") return 0;
      const page = (r.value.data as Result<any>)?.data ?? {};
      return Number(page.total ?? 0);
    };
    stats.value = {
      total: totalOf(results[0]),
      normal: totalOf(results[1]),
      disabled: totalOf(results[2]),
    };
  }

  // ==================== 新增 / 编辑 ====================
  const dialogVisible = ref(false);
  const submitting = ref(false);
  const formTarget = ref<RoleItem | null>(null);

  // 行参数保持 any：el-table 插槽 row 为 DefaultRow，与 RoleItem 结构不互相兼容
  function openDialog(row?: any) {
    formTarget.value = row ?? null;
    dialogVisible.value = true;
  }

  /** 弹窗提交（载荷由 RoleFormDialog 校验后回传） */
  async function handleSubmit(payload: RoleSubmitPayload) {
    submitting.value = true;
    try {
      if (formTarget.value) {
        await updateRole(formTarget.value.id, payload);
        ElMessage.success("已更新");
      } else {
        await addRole(payload);
        ElMessage.success("已新增");
      }
      dialogVisible.value = false;
      await fetchRoles();
      void fetchStats();
    } catch {
      ElMessage.error("保存失败");
    } finally {
      submitting.value = false;
    }
  }

  // ==================== 状态切换 / 删除 ====================
  async function handleToggleStatus(row: any, val: string | number | boolean) {
    const next = val === 0 || val === false ? 0 : 1;
    try {
      await updateRoleStatus(row.id, next);
      // 成功后才本地改状态（无二次确认、不整表刷新）
      row.status = next;
      ElMessage.success(next === 0 ? "已启用" : "已停用");
      void fetchStats();
    } catch {
      ElMessage.error("操作失败");
    }
  }

  async function handleDelete(row: any) {
    try {
      await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, "删除确认", { type: "warning" });
    } catch {
      return;
    }
    try {
      await deleteRole(row.id);
      ElMessage.success("已删除");
      await fetchRoles();
      void fetchStats();
    } catch {
      ElMessage.error("删除失败");
    }
  }

  // ==================== 详情 ====================
  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailRole = ref<RoleItem | null>(null);
  const detailData = ref<RoleDetail | null>(null);

  /** 打开详情弹窗：行数据即时渲染，详情接口返回后补全（失败仅用行数据兜底） */
  async function openDetailDialog(row: any) {
    detailRole.value = row;
    detailData.value = null;
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      const res = (await getRoleDetail(row.id)).data as Result<RoleDetail>;
      detailData.value = res.data ?? null;
    } catch {
      detailData.value = null;
    } finally {
      detailLoading.value = false;
    }
  }

  // ==================== 权限分配 ====================
  const permDialogVisible = ref(false);
  const permLoading = ref(false);
  const permSubmitting = ref(false);
  const currentRole = ref<RoleItem>();
  const permGroups = ref<PermissionGroup[]>([]);
  const selectedPermIds = ref<string[]>([]);

  async function openPermissionDialog(row: any) {
    currentRole.value = row;
    permDialogVisible.value = true;
    permLoading.value = true;
    permGroups.value = [];
    selectedPermIds.value = [];
    try {
      const treeRes = (await getPermissionTree()).data as Result<any[]>;
      permGroups.value = (treeRes.data ?? []).map((g: any) => ({
        groupName: g.groupName,
        permissions: (g.permissions ?? []).map((p: any) => ({
          id: p.id,
          name: p.name,
          perms: p.perms,
          status: p.status,
        })),
      }));
      const permRes = (await getRolePermissions(row.id)).data as Result<any[]>;
      const bound = new Set<string>();
      for (const g of permRes.data ?? []) {
        for (const p of g.permissions ?? []) bound.add(String(p.id));
      }
      selectedPermIds.value = [...bound];
    } catch {
      ElMessage.error("加载权限失败");
    } finally {
      permLoading.value = false;
    }
  }

  /** 勾选 / 取消勾选单个权限（仅接受真实权限 id） */
  function togglePermission(id: string | number) {
    const key = String(id);
    if (!/^\d+$/.test(key)) return;
    const idx = selectedPermIds.value.indexOf(key);
    if (idx >= 0) selectedPermIds.value.splice(idx, 1);
    else selectedPermIds.value.push(key);
  }

  /** 关闭分配权限弹窗并清空勾选 / 分组 */
  function closePermissionDialog() {
    permDialogVisible.value = false;
    permGroups.value = [];
    selectedPermIds.value = [];
    currentRole.value = undefined;
  }

  /** 弹窗保存：提交完整已选权限 id 数组（仅真实权限 id，过滤非数字） */
  async function handleSavePermissions() {
    if (!currentRole.value) return;
    // 保留字符串形态的雪花 ID，避免 Number() 精度丢失；非数字脏值直接过滤
    const ids = [...new Set(selectedPermIds.value.filter((id) => /^\d+$/.test(id)))];
    permSubmitting.value = true;
    try {
      await assignRolePermissions(currentRole.value.id, ids as unknown as number[]);
      ElMessage.success("权限已更新");
      closePermissionDialog();
    } catch {
      ElMessage.error("分配失败");
    } finally {
      permSubmitting.value = false;
    }
  }

  onMounted(() => {
    void fetchRoles();
    void fetchStats();
  });

  return {
    // 列表
    loading,
    roleList,
    total,
    query,
    fetchRoles,
    handleSearch,
    handleReset,
    handleSizeChange,
    formatTime,
    handleToggleStatus,
    handleDelete,
    roleStatus,
    // 统计卡
    stats,
    // 新增 / 编辑
    dialogVisible,
    submitting,
    formTarget,
    openDialog,
    handleSubmit,
    // 详情
    detailVisible,
    detailLoading,
    detailRole,
    detailData,
    openDetailDialog,
    // 权限分配
    permDialogVisible,
    permLoading,
    permSubmitting,
    currentRole,
    permGroups,
    selectedPermIds,
    openPermissionDialog,
    togglePermission,
    handleSavePermissions,
    closePermissionDialog,
    // 常量
    ROLE_STATUS_OPTIONS,
    PAGINATION_LAYOUT,
  };
}
