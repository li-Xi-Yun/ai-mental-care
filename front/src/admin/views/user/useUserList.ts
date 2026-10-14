import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { AxiosResponse } from "axios";
import type { ListResult } from "@shared/api/types";
import { PAGINATION_LAYOUT } from "@/shared/api/config";
import {
  getUserPage,
  getUserDetail,
  updateUserStatus,
  getUserRoles,
  assignUserRoles,
  getUserTempPermissions,
} from "@/admin/api/admin/user-manage";
import type {
  SysUserPageVO,
  SysUserDetailVO,
  SysUserStatusUpdateDTO,
  SimpleRole,
  TempPermissionVO,
} from "@/admin/api/admin/user-manage";
import { getAllSimpleRoles } from "@/admin/api/permission/role";
import { USER_STATUS_META, USER_STATUS_OPTIONS, USER_STATUS_TAG_TYPE, getUserGenderMeta } from "@/admin/constants/user";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import { useAvatar } from "@/admin/composables/useAvatar";
import { formatTime as formatTimeUtil } from "@/admin/composables/useTimeFormat";

/** 封禁表单提交载荷 */
export interface BanSubmitPayload {
  reason: string;
  banEndTime?: string;
}

/**
 * 用户管理页业务逻辑（单一职责拆分）：
 * - 状态/性别展示工具
 * - 列表加载 / 搜索 / 分页 / 统计
 * - 封禁 / 启用 / 批量封禁
 * - 角色分配
 * - 用户详情抽屉
 * - 临时权限抽屉
 *
 * 弹窗/抽屉子组件各自持有表单状态，通过事件回传提交载荷。
 */
export function useUserList() {
  // ==================== 展示工具 ====================
  const { labelOf: statusLabel, styleOf: statusBadgeStyle } = useEnumMeta(USER_STATUS_META, {
    tagTypeMap: USER_STATUS_TAG_TYPE,
  });
  const { genderGradStyle } = useAvatar();

  function genderLabel(g?: number): string {
    return getUserGenderMeta(g).label;
  }

  function genderMetaOf(g?: number) {
    return getUserGenderMeta(g);
  }

  function genderTagStyle(g?: number): Record<string, string> {
    const meta = getUserGenderMeta(g);
    return { color: meta.color, background: meta.bg, borderColor: meta.border };
  }

  function avatarStyle(g?: number): Record<string, string> {
    return genderGradStyle(g);
  }

  function avatarText(row: any): string {
    const name = row?.username?.trim();
    const account = row?.loginAccount?.trim();
    return (name || account || "?").charAt(0).toUpperCase();
  }

  function avatarLargeText(detail: SysUserDetailVO): string {
    const name = detail?.username?.trim();
    const account = detail?.loginAccount?.trim();
    return (name || account || "?").charAt(0).toUpperCase();
  }

  function permCodes(item: TempPermissionVO): string[] {
    return item.perms ? item.perms.split(",").map((s) => s.trim()).filter(Boolean) : [];
  }

  const PERM_STATUS_TEXT: Record<number, string> = { 0: "有效", 1: "已作废", 2: "已过期" };
  function permStatusText(status?: number): string {
    return status === undefined || status === null ? "未知" : (PERM_STATUS_TEXT[status] ?? "未知");
  }

  function permTagType(status?: number): "success" | "danger" | "info" {
    const t: Record<number, "success" | "danger" | "info"> = { 0: "success", 1: "danger", 2: "info" };
    return t[status ?? 0] ?? "info";
  }

  function permTimelineType(status?: number): "primary" | "danger" | "info" {
    const t: Record<number, "primary" | "danger" | "info"> = { 0: "primary", 1: "danger", 2: "info" };
    return t[status ?? 0] ?? "info";
  }

  const disablePastDate = (d: Date) => d.getTime() < Date.now();

  function formatTime(v?: string): string {
    return formatTimeUtil(v);
  }

  // ==================== 列表 ====================
  const loading = ref(false);
  const tableData = ref<SysUserPageVO[]>([]);
  const total = ref(0);
  const currentPage = ref(1);
  const pageSize = ref(10);
  const searchForm = reactive<{ keyword: string; status?: number }>({ keyword: "", status: undefined });
  const stats = reactive({ total: 0, normal: 0, ban: 0, logout: 0 });
  const selectedRows = ref<SysUserPageVO[]>([]);

  function handleSelectionChange(rows: any[]) {
    selectedRows.value = rows;
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await getUserPage({
        pageNum: currentPage.value,
        pageSize: pageSize.value,
        keyword: searchForm.keyword.trim() || undefined,
        status: (searchForm.status ?? "") === "" ? undefined : searchForm.status,
      });
      tableData.value = res.data.data?.records ?? [];
      total.value = res.data.data?.total ?? 0;
    } catch {
      ElMessage.error("加载用户列表失败，请稍后重试");
    } finally {
      loading.value = false;
    }
  }

  async function loadStats() {
    const tasks = [
      getUserPage({ pageNum: 1, pageSize: 1 }),
      getUserPage({ pageNum: 1, pageSize: 1, status: 0 }),
      getUserPage({ pageNum: 1, pageSize: 1, status: 2 }),
      getUserPage({ pageNum: 1, pageSize: 1, status: 3 }),
    ];
    const [totalRes, normalRes, banRes, logoutRes] = await Promise.allSettled(tasks);
    const extract = (r: PromiseSettledResult<AxiosResponse<ListResult<SysUserPageVO>>>) =>
      r.status === "fulfilled" ? (r.value.data.data?.total ?? 0) : 0;
    stats.total = extract(totalRes);
    stats.normal = extract(normalRes);
    stats.ban = extract(banRes);
    stats.logout = extract(logoutRes);
  }

  async function refreshAll() {
    await Promise.all([loadData(), loadStats()]);
  }

  function handleSearch() {
    currentPage.value = 1;
    loadData();
  }

  function handleReset() {
    searchForm.keyword = "";
    searchForm.status = undefined;
    currentPage.value = 1;
    loadData();
  }

  // ==================== 封禁 / 启用 ====================
  const banVisible = ref(false);
  const banTitle = ref("封禁账号");
  const banSubmitting = ref(false);
  const banTarget = ref<SysUserPageVO | null>(null);
  const batchTargets = ref<SysUserPageVO[]>([]);

  function openBanDialog(row: any) {
    banTarget.value = row;
    batchTargets.value = [];
    banTitle.value = `封禁账号：${row.username ?? row.loginAccount ?? ""}`;
    banVisible.value = true;
  }

  function openBatchBan() {
    if (!selectedRows.value.length) return;
    banTarget.value = null;
    batchTargets.value = [...selectedRows.value];
    banTitle.value = `批量封禁（${batchTargets.value.length} 个用户）`;
    banVisible.value = true;
  }

  /** 弹窗提交（载荷由 BanDialog 校验后回传） */
  async function submitBan(payload: BanSubmitPayload) {
    const reason = payload.reason.trim();
    if (!reason) return;
    const targets = batchTargets.value.length ? batchTargets.value : banTarget.value ? [banTarget.value] : [];
    if (!targets.length) return;
    banSubmitting.value = true;
    try {
      const body: SysUserStatusUpdateDTO = {
        status: 2,
        banReason: reason,
        banEndTime: payload.banEndTime || undefined,
      };
      await Promise.all(targets.map((t) => updateUserStatus(t.id, body)));
      ElMessage.success(batchTargets.value.length ? "所选用户已封禁" : "用户已封禁");
      banVisible.value = false;
      await refreshAll();
    } catch {
      ElMessage.error("封禁失败，请稍后重试");
    } finally {
      banSubmitting.value = false;
    }
  }

  async function handleEnable(row: any) {
    try {
      await ElMessageBox.confirm(`确定启用账号「${row.username ?? row.loginAccount}」吗？`, "启用确认", {
        type: "warning",
        confirmButtonText: "启用",
        cancelButtonText: "取消",
      });
    } catch {
      return;
    }
    try {
      await updateUserStatus(row.id, { status: 0 });
      ElMessage.success("账号已启用");
      await refreshAll();
    } catch {
      ElMessage.error("启用失败，请稍后重试");
    }
  }

  // ==================== 角色分配 ====================
  const assignVisible = ref(false);
  const assignSubmitting = ref(false);
  const assignTarget = ref<SysUserPageVO | null>(null);
  const roleLoading = ref(false);
  const roleOptions = ref<SimpleRole[]>([]);
  const selectedRoleIds = ref<number[]>([]);

  async function openAssignDialog(row: any) {
    assignTarget.value = row;
    assignVisible.value = true;
    roleLoading.value = true;
    selectedRoleIds.value = [];
    try {
      const [optsRes, curRes] = await Promise.all([getAllSimpleRoles(), getUserRoles(row.id)]);
      roleOptions.value = (optsRes.data?.data ?? []) as SimpleRole[];
      selectedRoleIds.value = (curRes.data?.data ?? []).map((r) => r.id);
    } catch {
      ElMessage.error("加载角色数据失败，请稍后重试");
    } finally {
      roleLoading.value = false;
    }
  }

  async function submitAssign() {
    if (!assignTarget.value) return;
    assignSubmitting.value = true;
    try {
      await assignUserRoles(assignTarget.value.id, selectedRoleIds.value);
      ElMessage.success("角色分配成功");
      assignVisible.value = false;
    } catch {
      ElMessage.error("角色分配失败，请稍后重试");
    } finally {
      assignSubmitting.value = false;
    }
  }

  // ==================== 用户详情抽屉 ====================
  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailTarget = ref<SysUserPageVO | null>(null);
  const detail = ref<SysUserDetailVO | null>(null);

  async function openDetail(row: any) {
    detailTarget.value = row;
    detail.value = null;
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      const res = await getUserDetail(row.id);
      detail.value = res.data?.data ?? null;
    } catch {
      ElMessage.error("加载用户详情失败，请稍后重试");
    } finally {
      detailLoading.value = false;
    }
  }

  /** 行点击打开详情抽屉：忽略选择列，其余列复用 openDetail */
  function handleRowClick(row: any, column?: any) {
    if (column?.type === "selection") return;
    openDetail(row);
  }

  // ==================== 临时权限抽屉 ====================
  const permVisible = ref(false);
  const permLoading = ref(false);
  const permTarget = ref<SysUserPageVO | null>(null);
  const permList = ref<TempPermissionVO[]>([]);

  async function openPermDrawer(row: any) {
    permTarget.value = row;
    permList.value = [];
    permVisible.value = true;
    permLoading.value = true;
    try {
      const res = await getUserTempPermissions(row.id);
      permList.value = res.data?.data ?? [];
    } catch {
      ElMessage.error("加载临时权限失败，请稍后重试");
    } finally {
      permLoading.value = false;
    }
  }

  onMounted(() => {
    refreshAll();
  });

  return {
    // 工具
    statusLabel,
    statusBadgeStyle,
    genderLabel,
    genderMetaOf,
    genderTagStyle,
    avatarText,
    avatarLargeText,
    avatarStyle,
    permCodes,
    permStatusText,
    permTagType,
    permTimelineType,
    disablePastDate,
    formatTime,
    // 列表
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    searchForm,
    stats,
    selectedRows,
    handleSelectionChange,
    loadData,
    handleSearch,
    handleReset,
    refreshAll,
    // 封禁
    banVisible,
    banTitle,
    banSubmitting,
    openBanDialog,
    openBatchBan,
    submitBan,
    handleEnable,
    // 角色
    assignVisible,
    assignSubmitting,
    assignTarget,
    roleLoading,
    roleOptions,
    selectedRoleIds,
    openAssignDialog,
    submitAssign,
    // 详情
    detailVisible,
    detailLoading,
    detailTarget,
    detail,
    openDetail,
    handleRowClick,
    // 临时权限
    permVisible,
    permLoading,
    permTarget,
    permList,
    openPermDrawer,
    // 常量
    STATUS_OPTIONS: USER_STATUS_OPTIONS,
    PAGINATION_LAYOUT,
  };
}

export type UseUserList = ReturnType<typeof useUserList>;
