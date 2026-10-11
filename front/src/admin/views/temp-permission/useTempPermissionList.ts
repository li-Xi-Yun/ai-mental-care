import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { DEFAULT_PAGE_SIZE } from "@/shared/api/config";
import {
  pageUserTempPermissions,
  pageAdminTempPermissions,
  listUserTempPermissions,
  listAdminTempPermissions,
  grantTempPermission,
  revokeTempPermission,
} from "@/admin/api/permission/temp-permission";
import { getPermissionTree } from "@/admin/api/permission/permission";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";

/* ==================== 类型定义 ==================== */

/** 人员列表行（被授权人） */
export type PageRow = { personId: number; personUsername: string; permCount?: number };

/** 临时权限明细项 */
export interface PermItem {
  id: number;
  personId: number;
  personUsername: string;
  permissionId: number;
  /** 权限码：后端可能返回字符串或字符串数组 */
  perms: string | string[];
  permissionName: string;
  groupName: string;
  startTime: string;
  expireTime: string;
  status: number;
  grantReason: string;
  grantUsername: string;
  createdTime: string;
}

/** 权限树分组（getPermissionTree 返回结构） */
export interface PermGroup {
  groupName: string;
  permissions: PermNode[];
}

/** 权限树节点（平铺复选胶囊） */
export interface PermNode {
  id: number | string;
  name: string;
  perms?: string | string[];
  status?: number;
}

/** 授予权限弹窗提交载荷（GrantPermissionDialog 校验后回传） */
export interface GrantSubmitPayload {
  personId: number;
  personUsername: string;
  permissionIdList: number[];
  startTime: string;
  expireTime: string;
  grantReason: string;
}

/** 临时权限状态展示元数据（0 有效 / 1 已作废 / 2 已过期） */
export const TEMP_STATUS_META: Record<number, { label: string; color: string; bg: string; border: string }> = {
  0: { label: "有效", color: "#059669", bg: "#ecfdf5", border: "#a7f3d0" },
  1: { label: "已作废", color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0" },
  2: { label: "已过期", color: "#d97706", bg: "#fffbeb", border: "#fde68a" },
};

/** 状态 → 徽章元数据（未知状态回落「有效」） */
export function statusMetaOf(status?: number) {
  return TEMP_STATUS_META[status ?? 0] ?? TEMP_STATUS_META[0];
}

/**
 * 临时权限管理页全部业务逻辑（单一职责拆分）：
 * - 前台用户 / 后台管理员 Tab 切换与人员分页列表（左卡）
 * - 被授权人权限明细加载与手动作废（右卡）
 * - 授予权限弹窗（权限树缓存、提交）
 * - 两类被授权人数统计卡
 */
export function useTempPermissionList() {
  const { formatTime } = useTimeFormat();

  /* ==================== 列表状态 ==================== */

  const activeTab = ref<"user" | "admin">("user");
  const loading = ref(false);
  const tableData = ref<PageRow[]>([]);
  const total = ref(0);
  const pageNum = ref(1);
  const pageSize = ref(DEFAULT_PAGE_SIZE);
  const selectedPersonId = ref<number | null>(null);

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));

  /** 当前页展示范围文案：第 x–y 条 */
  const pageRange = computed(() => {
    if (total.value === 0) return "0";
    const start = (pageNum.value - 1) * pageSize.value + 1;
    const end = Math.min(pageNum.value * pageSize.value, total.value);
    return `${start}–${end}`;
  });

  const selectedPersonName = computed(
    () => tableData.value.find((p) => p.personId === selectedPersonId.value)?.personUsername ?? "",
  );

  /* ==================== 统计卡：被授权人数 ==================== */

  const stats = reactive({ user: 0, admin: 0 });

  async function loadStats() {
    try {
      const [userRes, adminRes] = await Promise.all([
        pageUserTempPermissions({ pageNum: 1, pageSize: 1 }),
        pageAdminTempPermissions({ pageNum: 1, pageSize: 1 }),
      ]);
      stats.user = userRes.data.data?.total ?? 0;
      stats.admin = adminRes.data.data?.total ?? 0;
    } catch {
      // 统计失败不阻塞页面
    }
  }

  /* ==================== 数据加载：人员列表 ==================== */

  let listSeq = 0;

  function buildParams() {
    return {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    };
  }

  async function loadData() {
    const seq = ++listSeq;
    loading.value = true;
    try {
      const fetcher = activeTab.value === "user" ? pageUserTempPermissions : pageAdminTempPermissions;
      const res = await fetcher(buildParams());
      if (seq !== listSeq) return;
      const records: PageRow[] = res.data.data?.records ?? [];
      const count: number = res.data.data?.total ?? 0;
      // 数据量变化导致当前页越界：回退到最后一页重新加载
      const maxPage = Math.max(1, Math.ceil(count / pageSize.value));
      if (pageNum.value > maxPage) {
        pageNum.value = maxPage;
        await loadData();
        return;
      }
      tableData.value = records;
      total.value = count;
    } catch {
      if (seq === listSeq) ElMessage.error("加载临时权限人员失败，请稍后重试");
    } finally {
      if (seq === listSeq) loading.value = false;
    }
  }

  function handleTabChange() {
    pageNum.value = 1;
    selectedPersonId.value = null;
    detailList.value = [];
    loadData();
  }

  function handlePrevPage() {
    if (pageNum.value <= 1) return;
    pageNum.value -= 1;
    loadData();
  }

  function handleNextPage() {
    if (pageNum.value >= totalPages.value) return;
    pageNum.value += 1;
    loadData();
  }

  function handleReset() {
    pageNum.value = 1;
    loadData();
  }

  function handleRefresh() {
    loadData();
    loadStats();
    if (selectedPersonId.value !== null) loadDetails(selectedPersonId.value);
  }

  /* ==================== 权限明细（右卡） ==================== */

  const detailLoading = ref(false);
  const detailList = ref<PermItem[]>([]);

  let detailSeq = 0;

  async function loadDetails(personId: number) {
    const seq = ++detailSeq;
    detailLoading.value = true;
    detailList.value = [];
    try {
      const fetcher = activeTab.value === "user" ? listUserTempPermissions : listAdminTempPermissions;
      const res = await fetcher(personId);
      if (seq !== detailSeq) return;
      detailList.value = res.data.data ?? [];
    } catch {
      if (seq === detailSeq) ElMessage.error("加载临时权限明细失败，请稍后重试");
    } finally {
      if (seq === detailSeq) detailLoading.value = false;
    }
  }

  async function handleRevoke(item: PermItem) {
    try {
      await ElMessageBox.confirm(
        `确定要作废「${item.permissionName}」的临时权限吗？作废后该权限立即失效。`,
        "手动作废确认",
        { type: "warning", confirmButtonText: "作废", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
      );
    } catch {
      return;
    }
    try {
      await revokeTempPermission(item.id);
      ElMessage.success(`「${item.permissionName}」已作废`);
      // 本地状态即时更新，再刷新明细与左列表/人数卡
      item.status = 1;
      if (selectedPersonId.value !== null) await loadDetails(selectedPersonId.value);
      await Promise.all([loadData(), loadStats()]);
    } catch {
      ElMessage.error("作废失败，请稍后重试");
    }
  }

  // 列表变化后自动选中第一行；被移除的人置空
  watch(tableData, (rows) => {
    if (!rows.length) {
      selectedPersonId.value = null;
      return;
    }
    if (!rows.some((p) => p.personId === selectedPersonId.value)) {
      selectedPersonId.value = rows[0].personId;
    }
  });

  // 选中人员后加载其权限明细
  watch(selectedPersonId, (id) => {
    if (id === null || id === undefined) {
      detailList.value = [];
      return;
    }
    loadDetails(id);
  });

  /* ==================== 授予权限弹窗 ==================== */

  const grantVisible = ref(false);
  const grantSubmitting = ref(false);
  const permissionTree = ref<PermGroup[]>([]);
  const permLoading = ref(false);
  const treeLoaded = ref(false);

  async function loadPermissionTree() {
    if (treeLoaded.value || permLoading.value) return;
    permLoading.value = true;
    try {
      const res = await getPermissionTree();
      permissionTree.value = (res.data.data ?? []).map((g: any) => ({
        groupName: g.groupName ?? "未分组",
        permissions: (g.permissions ?? []).map((p: any) => ({
          id: p.id,
          name: p.name ?? "",
          perms: p.perms,
          status: p.status,
        })),
      }));
      treeLoaded.value = true;
    } catch {
      ElMessage.error("加载权限树失败，请稍后重试");
    } finally {
      permLoading.value = false;
    }
  }

  function handleGrant() {
    grantVisible.value = true;
    loadPermissionTree();
  }

  /** 弹窗提交（校验与载荷组装由 GrantPermissionDialog 完成） */
  async function handleGrantSubmit(payload: GrantSubmitPayload) {
    grantSubmitting.value = true;
    try {
      await grantTempPermission({
        personId: payload.personId,
        permissionIdList: payload.permissionIdList,
        startTime: payload.startTime,
        expireTime: payload.expireTime,
        grantReason: payload.grantReason,
      });
      ElMessage.success("临时权限授予成功");
      grantVisible.value = false;
      await Promise.all([loadData(), loadStats()]);
      // 若当前正在查看该被授权人的明细，同步刷新
      if (selectedPersonId.value === payload.personId) {
        await loadDetails(payload.personId);
      }
    } catch {
      ElMessage.error("授予失败，请稍后重试");
    } finally {
      grantSubmitting.value = false;
    }
  }

  /* ==================== 展示工具 ==================== */

  /** 权限码展示：数组逗号连接 / 字符串原样 */
  function formatPerms(perms?: string | string[] | null): string {
    if (Array.isArray(perms)) return perms.length ? perms.join(", ") : "—";
    return perms || "—";
  }

  /** 被授权人头像渐变（同用户名同色，人员偏移 4） */
  function avatarStyle(name?: string): Record<string, string> {
    return { background: hashColorFor(name, 4).gradient };
  }

  /** 头像首字母 */
  function initialOf(name?: string): string {
    return (name || "?").charAt(0).toUpperCase();
  }

  /* ==================== 初始化 ==================== */

  onMounted(() => {
    loadData();
    loadStats();
  });

  return {
    // 页面状态
    activeTab,
    loading,
    tableData,
    total,
    pageNum,
    pageSize,
    totalPages,
    pageRange,
    selectedPersonId,
    selectedPersonName,
    stats,
    handleTabChange,
    handlePrevPage,
    handleNextPage,
    handleReset,
    handleRefresh,
    loadData,
    // 权限明细
    detailLoading,
    detailList,
    handleRevoke,
    // 授予权限弹窗
    grantVisible,
    grantSubmitting,
    permissionTree,
    permLoading,
    handleGrant,
    handleGrantSubmit,
    // 展示工具 / 常量
    statusMetaOf,
    formatTime,
    formatPerms,
    avatarStyle,
    initialOf,
  };
}

export type UseTempPermissionList = ReturnType<typeof useTempPermissionList>;
