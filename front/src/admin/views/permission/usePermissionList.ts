import { computed, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getPermissionTree, getPermissionDetail, addPermission, updatePermission, deletePermission } from "@/admin/api/permission/permission";
import { getAllSimpleRoles, getRolePermissions } from "@/admin/api/permission/role";
import { PERMISSION_STATUS_META } from "@/admin/constants/permission";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import type { EnumMetaItem } from "@/admin/types";
import type { Result } from "@/shared/api/types";

/** 权限项 */
export interface PermissionItem {
  id: number;
  perms: string;
  name: string;
  status: number;
}

/** 权限分组 */
export interface PermissionGroup {
  groupName: string;
  permissions: PermissionItem[];
}

/** 新增 / 编辑权限提交载荷（由 PermissionFormDialog 校验后回传） */
export interface PermissionSubmitPayload {
  perms: string;
  name: string;
  groupName: string;
  remark: string;
}

/** 权限详情（GET /admin/permission/{id}） */
export interface PermissionDetail {
  id?: number;
  perms?: string;
  name?: string;
  groupName?: string;
  remark?: string;
  status?: number;
}

/** 角色下拉项（GET /admin/sysRole/allSimple） */
export interface SimpleRole {
  id: number;
  name: string;
}

/** 视图范围：全部权限 / 按角色查看 */
export type PermissionViewMode = "all" | "role";

/** 详情弹窗目标（权限项本身不含分组名，需随分组名一并记录） */
export interface PermissionDetailTarget {
  perm: PermissionItem;
  groupName: string;
}

/** 权限状态徽章元数据（0 正常绿 / 1 停用灰） */
export const PERMISSION_BADGE_META: Record<number, EnumMetaItem> = {
  0: { label: "正常", color: "#059669", bg: "#ecfdf5", border: "#a7f3d0" },
  1: { label: "停用", color: "#6b7280", bg: "#f3f4f6", border: "#e5e7eb" },
};

/**
 * 权限管理页全部业务逻辑：
 * 全部权限 / 按角色查看双视图、分组树加载、派生统计、搜索与分组筛选、
 * 分组折叠、新增/编辑弹窗、详情弹窗、删除。
 * 弹窗子组件自持展示状态，通过 props / emits 与页面交互。
 */
export function usePermissionList() {
  const loading = ref(false);
  const submitting = ref(false);
  /** 全部权限树（GET /admin/permission/list） */
  const permissionTree = ref<PermissionGroup[]>([]);

  // 视图范围：全部权限（默认） / 按角色查看
  const viewMode = ref<PermissionViewMode>("all");
  const roles = ref<SimpleRole[]>([]);
  const rolesLoading = ref(false);
  const roleId = ref<number | null>(null);
  /** 当前角色绑定的权限树（GET /admin/sysRole/{id}/permissions） */
  const roleTree = ref<PermissionGroup[]>([]);

  // 筛选（客户端）
  const keyword = ref("");
  const searchApplied = ref("");
  const groupFilter = ref("");

  // 分组折叠：记录“已收起”的分组名，默认全部展开
  const collapsedGroups = ref<Set<string>>(new Set());

  // 新增 / 编辑弹窗
  const dialogVisible = ref(false);
  const formTarget = ref<PermissionItem | null>(null);
  const formGroupName = ref("");
  const formRemark = ref("");

  // 详情弹窗
  const detailVisible = ref(false);
  const detailTarget = ref<PermissionDetailTarget | null>(null);
  const detailData = ref<PermissionDetail | null>(null);
  const detailLoading = ref(false);

  const { labelOf: permStatusLabel, itemOf: permStatusItem } = useEnumMeta(PERMISSION_BADGE_META);

  /** 状态徽章 props（直接 v-bind 到 StatusBadge） */
  function permStatusBadge(status?: number | null): { label: string; color: string; bg: string; border: string } {
    const item = permStatusItem(status);
    return { label: item.label, color: item.color, bg: item.bg, border: item.border };
  }

  /** 状态标签类型（保留原有契约，供潜在 el-tag 场景使用） */
  function permTagType(status?: number): "success" | "danger" {
    return PERMISSION_STATUS_META[status ?? 0]?.tagType ?? "success";
  }

  /** 当前视图（全部 / 角色）的完整树，统计与筛选均基于它 */
  const currentTree = computed<PermissionGroup[]>(() => (viewMode.value === "all" ? permissionTree.value : roleTree.value));
  const currentRole = computed<SimpleRole | null>(() => roles.value.find((r) => r.id === roleId.value) ?? null);

  // 派生统计（当前视图全量，不随搜索/分组筛选变化）
  const totalCount = computed(() => currentTree.value.reduce((s, g) => s + (g.permissions?.length ?? 0), 0));
  const groupCount = computed(() => currentTree.value.length);
  const disabledCount = computed(() => currentTree.value.reduce((s, g) => s + (g.permissions?.filter((p) => p.status === 1).length ?? 0), 0));
  const normalCount = computed(() => totalCount.value - disabledCount.value);

  /** 分组下拉选项（新增/编辑弹窗使用，始终取全部权限树，支持 allow-create 自定义输入） */
  const groupOptions = computed(() => permissionTree.value.map((g) => g.groupName));

  /** 搜索 + 分组筛选后的可见分组（客户端，跨分组；无匹配的分组整体隐藏） */
  const visibleTree = computed<PermissionGroup[]>(() => {
    let list = currentTree.value;
    if (viewMode.value === "all" && groupFilter.value) {
      list = list.filter((g) => g.groupName === groupFilter.value);
    }
    const kw = searchApplied.value.trim().toLowerCase();
    if (kw) {
      list = list
        .map((g) => ({
          groupName: g.groupName,
          permissions: (g.permissions ?? []).filter((p) => p.name.toLowerCase().includes(kw) || p.perms.toLowerCase().includes(kw)),
        }))
        .filter((g) => g.permissions.length > 0);
    }
    return list;
  });

  /** 分组内正常 / 停用数量（组头副行展示） */
  function groupNormalCount(group: PermissionGroup): number {
    return (group.permissions ?? []).filter((p) => p.status === 0).length;
  }

  function groupDisabledCount(group: PermissionGroup): number {
    return (group.permissions?.length ?? 0) - groupNormalCount(group);
  }

  /** 是否收起：搜索词非空时临时忽略折叠集合，命中分组自动展开（不覆盖用户手动折叠的其它组） */
  function isCollapsed(groupName: string): boolean {
    if (searchApplied.value.trim()) return false;
    return collapsedGroups.value.has(groupName);
  }

  function toggleGroup(groupName: string) {
    const next = new Set(collapsedGroups.value);
    if (next.has(groupName)) next.delete(groupName);
    else next.add(groupName);
    collapsedGroups.value = next;
  }

  /** 当前可见分组是否全部收起（用于「收起全部 / 展开全部」） */
  const allGroupsCollapsed = computed(() => visibleTree.value.length > 0 && visibleTree.value.every((g) => isCollapsed(g.groupName)));

  function toggleAllGroups() {
    if (allGroupsCollapsed.value) {
      collapsedGroups.value = new Set();
    } else {
      collapsedGroups.value = new Set(visibleTree.value.map((g) => g.groupName));
    }
  }

  /** 空态文案：搜索无结果 / 分组无结果 / 未选角色 / 角色无权限 / 全部为空 */
  const emptyText = computed(() => {
    if (searchApplied.value || (viewMode.value === "all" && groupFilter.value)) return "未找到匹配的权限";
    if (viewMode.value === "role") return roleId.value === null ? "请先选择一个角色" : "该角色暂未绑定权限";
    return "暂无权限数据，点击右上角「新增权限」创建第一个权限";
  });

  /** 加载全部权限树（GET /admin/permission/list） */
  async function fetchTree() {
    loading.value = true;
    try {
      const res = (await getPermissionTree()).data as Result<PermissionGroup[]>;
      permissionTree.value = res.data || [];
    } catch {
      ElMessage.error("加载权限列表失败");
    } finally {
      loading.value = false;
    }
  }

  /** 加载当前角色绑定的权限树（GET /admin/sysRole/{id}/permissions） */
  async function fetchRoleTree() {
    if (roleId.value === null) {
      roleTree.value = [];
      return;
    }
    loading.value = true;
    try {
      const res = (await getRolePermissions(roleId.value)).data as Result<PermissionGroup[]>;
      roleTree.value = res.data || [];
    } catch {
      roleTree.value = [];
      ElMessage.error("加载角色权限失败");
    } finally {
      loading.value = false;
    }
  }

  /** 加载角色下拉（GET /admin/sysRole/allSimple） */
  async function fetchRoles() {
    rolesLoading.value = true;
    try {
      const res = (await getAllSimpleRoles()).data as Result<SimpleRole[]>;
      roles.value = (res.data || []).map((r) => ({ id: r.id, name: r.name }));
    } catch {
      roles.value = [];
      ElMessage.error("角色列表加载失败");
    } finally {
      rolesLoading.value = false;
    }
  }

  /** 刷新：重拉全部权限树（按角色视图附加刷新角色权限树） */
  async function refresh() {
    await fetchTree();
    if (viewMode.value === "role") await fetchRoleTree();
  }

  /** 切换视图范围：分组折叠状态重置为全部展开 */
  function setViewMode(mode: PermissionViewMode) {
    if (viewMode.value === mode) return;
    viewMode.value = mode;
    collapsedGroups.value = new Set();
    if (mode === "role") {
      if (roleId.value !== null) fetchRoleTree();
      else roleTree.value = [];
    }
  }

  // 切换角色：重置折叠状态并重拉该角色权限
  watch(roleId, () => {
    collapsedGroups.value = new Set();
    if (viewMode.value === "role") fetchRoleTree();
  });

  function handleSearch() {
    searchApplied.value = keyword.value.trim();
  }

  function resetFilters() {
    keyword.value = "";
    searchApplied.value = "";
    groupFilter.value = "";
  }

  function openDialog(perm?: PermissionItem, groupName?: string) {
    formTarget.value = perm ?? null;
    formGroupName.value = groupName ?? "";
    formRemark.value = "";
    if (perm) {
      getPermissionDetail(perm.id)
        .then((res) => {
          formRemark.value = (res.data as Result<any>).data?.remark ?? "";
        })
        .catch(() => undefined);
    }
    dialogVisible.value = true;
  }

  /** 打开详情弹窗：先用行数据渲染，异步拉详情补 remark（失败静默回退行数据） */
  function openDetail(perm: PermissionItem, groupName: string) {
    const id = perm.id;
    detailTarget.value = { perm, groupName };
    detailData.value = null;
    detailLoading.value = true;
    detailVisible.value = true;
    getPermissionDetail(id)
      .then((res) => {
        if (detailTarget.value?.perm.id !== id) return;
        detailData.value = (res.data as Result<PermissionDetail>).data ?? null;
      })
      .catch(() => undefined)
      .finally(() => {
        if (detailTarget.value?.perm.id === id) detailLoading.value = false;
      });
  }

  /** 弹窗提交（载荷由 PermissionFormDialog 校验后回传） */
  async function handleSubmit(payload: PermissionSubmitPayload) {
    submitting.value = true;
    try {
      if (formTarget.value) {
        await updatePermission(formTarget.value.id, payload);
        ElMessage.success("权限已更新");
      } else {
        await addPermission(payload);
        ElMessage.success("权限已新增");
      }
      dialogVisible.value = false;
      await refresh();
    } catch {
      ElMessage.error("操作失败");
    } finally {
      submitting.value = false;
    }
  }

  async function handleDelete(perm: PermissionItem) {
    try {
      await ElMessageBox.confirm(`确认删除权限「${perm.name}」(${perm.perms})？`, "删除确认", { type: "warning" });
    } catch {
      return;
    }
    try {
      await deletePermission(perm.id);
      ElMessage.success("删除成功");
      await refresh();
    } catch {
      ElMessage.error("删除失败");
    }
  }

  onMounted(() => {
    fetchTree();
    fetchRoles();
  });

  return {
    // 列表 / 统计
    loading,
    permissionTree,
    totalCount,
    groupCount,
    normalCount,
    disabledCount,
    groupOptions,
    fetchTree,
    refresh,
    permStatusLabel,
    permStatusBadge,
    permTagType,
    handleDelete,
    // 视图范围 / 筛选
    viewMode,
    roles,
    rolesLoading,
    roleId,
    currentRole,
    keyword,
    searchApplied,
    groupFilter,
    visibleTree,
    emptyText,
    setViewMode,
    handleSearch,
    resetFilters,
    // 分组折叠
    isCollapsed,
    toggleGroup,
    allGroupsCollapsed,
    toggleAllGroups,
    groupNormalCount,
    groupDisabledCount,
    // 新增 / 编辑
    dialogVisible,
    submitting,
    formTarget,
    formGroupName,
    formRemark,
    openDialog,
    handleSubmit,
    // 详情
    detailVisible,
    detailTarget,
    detailData,
    detailLoading,
    openDetail,
  };
}
