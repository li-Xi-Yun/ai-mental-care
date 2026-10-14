import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import dayjs from "dayjs";
import type { AxiosResponse } from "axios";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import type { ListResult, Result } from "@shared/api/types";
import { useAdminStore } from "@/admin/stores/admin";
import {
  getAdminPage,
  addAdmin,
  getAdminDetail,
  updateAdmin,
  updateAdminStatus,
  resetAdminPwd,
  deleteAdmin,
  getAdminRoles,
  assignAdminRoles,
  getAllSimpleRoles,
} from "@/admin/api/admin/admin-manage";
import type {
  AdminAddDTO,
  AdminAddVO,
  AdminDetailVO,
  AdminPageVO,
  AdminUpdateDTO,
  AdminStatusUpdateDTO,
  RoleVO,
  SimpleRole,
} from "@/admin/api/admin/admin-manage";
import { USER_STATUS_META, USER_STATUS_OPTIONS } from "@/admin/constants/user";

/** 新增 / 编辑管理员弹窗提交载荷（由 AdminFormDialog 校验后回传） */
export interface AdminFormSubmitPayload {
  id: number | null;
  username: string;
  password: string;
  mobile: string;
  email: string;
  sysPassword: boolean;
}

/** 封禁弹窗提交载荷（由 BanDialog 回传） */
export interface AdminBanSubmitPayload {
  banEndTime?: string;
  banReason: string;
}

/**
 * 管理员管理页全部业务逻辑（单一职责拆分）：
 * - 状态/角色展示工具与当前登录管理员自我保护判断
 * - 列表加载 / 搜索 / 重置 / 分页 / 统计
 * - 新增 / 编辑 / 状态切换 / 封禁 / 重置密码 / 注销
 * - 角色分配
 *
 * 弹窗子组件各自持有表单状态，通过事件回传提交载荷。
 * 本页状态元数据与 constants/user.ts 中 USER_STATUS_META 完全一致，直接复用。
 */
export function useAdminList() {
  /* ==================== 状态元数据（复用用户状态常量） ==================== */

  const STATUS_META = USER_STATUS_META;
  const STATUS_OPTIONS = USER_STATUS_OPTIONS;

  function statusBadgeStyle(status: number): Record<string, string> {
    const meta = STATUS_META[status] ?? STATUS_META[3];
    return { color: meta.color, background: meta.bg, borderColor: meta.border };
  }

  function statusLabel(status: number): string {
    return STATUS_META[status]?.label ?? "未知";
  }

  function roleStyle(role: RoleVO): Record<string, string> {
    const colors = [
      { color: "#6366f1", bg: "#eef2ff", border: "#e0e7ff" },
      { color: "#0ea5e9", bg: "#eff6ff", border: "#e0f2fe" },
      { color: "#10b981", bg: "#ecfdf5", border: "#a7f3d0" },
    ];
    const c = colors[(role.id ?? 0) % colors.length];
    return { color: c.color, background: c.bg, borderColor: c.border };
  }

  function formatTime(v?: string): string {
    return v ? dayjs(v).format("YYYY-MM-DD HH:mm") : "—";
  }

  /** 生成一个满足后端正则（大小写字母+数字，6-20 位）的占位密码，仅在「使用默认密码」时用于通过服务端校验 */
  function generateValidPassword(): string {
    const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    const rand = (n: number) =>
      Array.from({ length: n }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
    return `A${rand(3)}a${rand(3)}1${rand(5)}`;
  }

  /* ==================== 页面状态 ==================== */

  const loading = ref(false);
  const tableData = ref<AdminPageVO[]>([]);
  const total = ref(0);
  const currentPage = ref(1);
  const pageSize = ref(DEFAULT_PAGE_SIZE);
  const rolesMap = ref<Record<number, RoleVO[]>>({});

  const searchForm = reactive<{ keyword: string; status?: number }>({ keyword: "", status: undefined });

  const stats = reactive({ total: 0, normal: 0, ban: 0, logout: 0 });

  /* ==================== 当前登录管理员（用于自我保护） ==================== */

  const adminStore = useAdminStore();
  const currentAdminId = computed(() => (adminStore.adminInfo?.id as number | undefined) ?? null);

  function isSelf(row: any): boolean {
    return currentAdminId.value != null && row.id === currentAdminId.value;
  }

  /* ==================== 数据加载 ==================== */

  function rowRoles(row: any): RoleVO[] {
    return rolesMap.value[row.id] ?? [];
  }

  async function loadRoles() {
    const rows = tableData.value;
    const map: Record<number, RoleVO[]> = {};
    await Promise.all(
      rows.map(async (row) => {
        try {
          const res = await getAdminRoles(row.id);
          map[row.id] = res.data.data ?? [];
        } catch {
          map[row.id] = [];
        }
      }),
    );
    rolesMap.value = map;
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await getAdminPage({
        pageNum: currentPage.value,
        pageSize: pageSize.value,
        keyword: searchForm.keyword || undefined,
        status: (searchForm.status ?? "") === "" ? undefined : searchForm.status,
      });
      tableData.value = res.data.data?.records ?? [];
      total.value = res.data.data?.total ?? 0;
    } catch {
      ElMessage.error("加载管理员列表失败，请稍后重试");
    } finally {
      loading.value = false;
    }
    await loadRoles();
  }

  async function loadStats() {
    const tasks = [
      getAdminPage({ pageNum: 1, pageSize: 1 }),
      getAdminPage({ pageNum: 1, pageSize: 1, status: 0 }),
      getAdminPage({ pageNum: 1, pageSize: 1, status: 2 }),
      getAdminPage({ pageNum: 1, pageSize: 1, status: 3 }),
    ];
    const [totalRes, normalRes, banRes, logoutRes] = await Promise.allSettled(tasks);
    const extract = (r: PromiseSettledResult<AxiosResponse<ListResult<AdminPageVO>>>) =>
      r.status === "fulfilled" ? (r.value.data.data?.total ?? 0) : 0;
    stats.total = extract(totalRes);
    stats.normal = extract(normalRes);
    stats.ban = extract(banRes);
    stats.logout = extract(logoutRes);
  }

  async function refreshAll() {
    await Promise.all([loadData(), loadStats()]);
  }

  /* ==================== 搜索 / 重置 ==================== */

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

  /* ==================== 新增 / 编辑 ==================== */

  const dialogVisible = ref(false);
  const isEdit = ref(false);
  const submitting = ref(false);
  const editTarget = ref<AdminPageVO | null>(null);

  /* 新增成功一次性展示账号 */
  const addSuccessVisible = ref(false);
  const addSuccessData = ref<AdminAddVO | null>(null);
  const addUsedDefaultPwd = ref(false);

  function openAdd() {
    editTarget.value = null;
    isEdit.value = false;
    dialogVisible.value = true;
  }

  function openEdit(row: any) {
    editTarget.value = row;
    isEdit.value = true;
    dialogVisible.value = true;
  }

  /** 弹窗提交（载荷由 AdminFormDialog 校验后回传） */
  async function submitAdminForm(payload: AdminFormSubmitPayload) {
    submitting.value = true;
    try {
      if (isEdit.value && payload.id != null) {
        const dto: AdminUpdateDTO = {
          id: payload.id,
          username: payload.username.trim(),
          mobile: payload.mobile.trim() || undefined,
          email: payload.email.trim() || undefined,
        };
        await updateAdmin(dto);
        ElMessage.success("管理员信息已更新");
        dialogVisible.value = false;
        await refreshAll();
      } else {
        const dto: AdminAddDTO = {
          username: payload.username.trim(),
          mobile: payload.mobile.trim() || undefined,
          email: payload.email.trim() || undefined,
          // 使用默认密码：sysPassword 置空走后端默认密码分支；自定义密码：sysPassword=false 使用输入密码
          password: payload.sysPassword ? generateValidPassword() : payload.password,
          sysPassword: payload.sysPassword ? null : false,
        };
        const res = await addAdmin(dto);
        dialogVisible.value = false;
        await refreshAll();
        // 一次性展示系统生成的登录账号
        addSuccessData.value = res.data?.data ?? null;
        addUsedDefaultPwd.value = !!payload.sysPassword;
        addSuccessVisible.value = true;
      }
    } catch {
      ElMessage.error("保存失败，请稍后重试");
    } finally {
      submitting.value = false;
    }
  }

  /* ==================== 状态切换 / 封禁 ==================== */

  const banVisible = ref(false);
  const banTarget = ref<AdminPageVO | null>(null);
  const banSubmitting = ref(false);

  async function enableAdmin(row: AdminPageVO) {
    try {
      await ElMessageBox.confirm(`确定启用账号「${row.username}」吗？`, "启用确认", { type: "warning" });
    } catch {
      return;
    }
    try {
      await updateAdminStatus(row.id, { status: 0 });
      ElMessage.success("账号已启用");
      await refreshAll();
    } catch {
      ElMessage.error("操作失败，请稍后重试");
    }
  }

  function handleStatusToggle(row: any, val: string | number | boolean) {
    if (Boolean(val)) {
      enableAdmin(row);
    } else {
      banTarget.value = row;
      banVisible.value = true;
    }
  }

  /** 封禁提交（载荷由 BanDialog 回传） */
  async function submitBan(payload: AdminBanSubmitPayload) {
    if (!banTarget.value) return;
    banSubmitting.value = true;
    try {
      const dto: AdminStatusUpdateDTO = {
        status: 2,
        banReason: payload.banReason.trim() || undefined,
        banEndTime: payload.banEndTime || undefined,
      };
      await updateAdminStatus(banTarget.value.id, dto);
      ElMessage.success("账号已封禁");
      banVisible.value = false;
      await refreshAll();
    } catch {
      ElMessage.error("封禁失败，请稍后重试");
    } finally {
      banSubmitting.value = false;
    }
  }

  /* ==================== 重置密码 / 注销 ==================== */

  async function handleResetPwd(row: any) {
    try {
      await ElMessageBox.confirm(
        `确定将「${row.username}」的密码重置为系统默认密码吗？重置后该账号需重新登录。`,
        "重置密码",
        { type: "warning", confirmButtonText: "重置", cancelButtonText: "取消" },
      );
    } catch {
      return;
    }
    try {
      await resetAdminPwd(row.id);
      ElMessage.success("密码已重置为系统默认密码");
    } catch {
      ElMessage.error("重置失败，请稍后重试");
    }
  }

  async function handleDelete(row: any) {
    try {
      await ElMessageBox.confirm(
        `确定注销账号「${row.username}」吗？注销后该账号将无法登录。`,
        "注销确认",
        { type: "warning", confirmButtonText: "注销", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
      );
    } catch {
      return;
    }
    try {
      await deleteAdmin(row.id);
      ElMessage.success("账号已注销");
      await refreshAll();
    } catch {
      ElMessage.error("注销失败，请稍后重试");
    }
  }

  /* ==================== 分配角色 ==================== */

  const assignVisible = ref(false);
  const assignTarget = ref<AdminPageVO | null>(null);
  const roleOptions = ref<SimpleRole[]>([]);
  const selectedRoleIds = ref<number[]>([]);
  const roleLoading = ref(false);
  const assignSubmitting = ref(false);

  async function openAssignRoles(row: any) {
    assignTarget.value = row;
    assignVisible.value = true;
    selectedRoleIds.value = [];
    roleLoading.value = true;
    try {
      const [optsRes, curRes] = await Promise.all([getAllSimpleRoles(), getAdminRoles(row.id)]);
      roleOptions.value = optsRes.data.data ?? [];
      selectedRoleIds.value = (curRes.data.data ?? []).map((r) => r.id);
    } catch {
      ElMessage.error("加载角色数据失败，请稍后重试");
    } finally {
      roleLoading.value = false;
    }
  }

  async function handleAssignSubmit() {
    if (!assignTarget.value) return;
    assignSubmitting.value = true;
    try {
      await assignAdminRoles(assignTarget.value.id, selectedRoleIds.value);
      ElMessage.success("角色分配成功");
      assignVisible.value = false;
      await loadRoles();
    } catch {
      ElMessage.error("角色分配失败，请稍后重试");
    } finally {
      assignSubmitting.value = false;
    }
  }

  /* ==================== 详情 ==================== */

  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailRow = ref<AdminPageVO | null>(null);
  const detailData = ref<AdminDetailVO | null>(null);

  /** 打开详情弹窗：行数据即时渲染，getAdminDetail 返回后补全（失败静默回退行数据） */
  async function openDetailDialog(row: any) {
    const id = row.id;
    detailRow.value = row;
    detailData.value = null;
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      const res = (await getAdminDetail(id)).data as Result<AdminDetailVO>;
      if (detailRow.value?.id !== id) return; // 旧响应丢弃守卫
      detailData.value = res.data ?? null;
    } catch {
      // 静默回退行数据
    } finally {
      if (detailRow.value?.id === id) detailLoading.value = false;
    }
  }

  /** 行点击打开详情：忽略选择列，其余列打开详情弹窗 */
  function handleRowClick(row: any, column?: any) {
    if (column?.type === "selection") return;
    openDetailDialog(row);
  }

  /* ==================== 初始化 ==================== */

  onMounted(() => {
    refreshAll();
  });

  return {
    // 常量
    STATUS_META,
    STATUS_OPTIONS,
    PAGINATION_LAYOUT,
    // 展示工具
    statusBadgeStyle,
    statusLabel,
    roleStyle,
    formatTime,
    isSelf,
    rowRoles,
    // 列表
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    searchForm,
    stats,
    loadData,
    handleSearch,
    handleReset,
    refreshAll,
    // 新增 / 编辑
    dialogVisible,
    isEdit,
    editTarget,
    submitting,
    openAdd,
    openEdit,
    submitAdminForm,
    // 新增成功一次性展示账号
    addSuccessVisible,
    addSuccessData,
    addUsedDefaultPwd,
    // 状态切换 / 封禁
    banVisible,
    banTarget,
    banSubmitting,
    handleStatusToggle,
    submitBan,
    // 重置密码 / 注销
    handleResetPwd,
    handleDelete,
    // 角色分配
    assignVisible,
    assignTarget,
    roleLoading,
    roleOptions,
    selectedRoleIds,
    assignSubmitting,
    openAssignRoles,
    handleAssignSubmit,
    // 详情
    detailVisible,
    detailLoading,
    detailRow,
    detailData,
    openDetailDialog,
    handleRowClick,
  };
}

export type UseAdminList = ReturnType<typeof useAdminList>;
