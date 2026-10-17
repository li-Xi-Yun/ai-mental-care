import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { PAGINATION_LAYOUT } from "@/shared/api/config";
import { getUserPage } from "@/admin/api/admin/user-manage";
import type { SysUserPageVO } from "@/admin/api/admin/user-manage";
import { USER_STATUS_META, USER_STATUS_OPTIONS } from "@/admin/constants/user";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import { useAvatar } from "@/admin/composables/useAvatar";
import { formatTime as formatTimeUtil } from "@/admin/composables/useTimeFormat";

/**
 * 用户限流管理页业务逻辑（单一职责拆分）：
 * - 展示工具（用户状态徽章 / 头像 / 时间格式化）
 * - 用户列表加载 / 搜索 / 分页
 * - 配额总览弹窗状态（visible / loading / target）
 *
 * 配额弹窗内部的数据拉取与调整刷新由 QuotaDialog 自行负责，
 * 此处只维护弹窗开关与被操作的用户行。
 */
export function useQuotaUserList() {
  // ==================== 展示工具 ====================
  const { labelOf: statusLabel, styleOf: statusBadgeStyle, dotStyleOf: statusDotStyle } = useEnumMeta(
    USER_STATUS_META,
    { fallbackKey: 3 },
  );
  const { genderGradStyle } = useAvatar();

  function avatarText(row?: SysUserPageVO | null): string {
    const name = row?.username?.trim();
    const account = row?.loginAccount?.trim();
    return (name || account || "?").charAt(0).toUpperCase();
  }

  function avatarStyle(row?: SysUserPageVO | null): Record<string, string> {
    return genderGradStyle(row?.gender);
  }

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

  // ==================== 配额总览弹窗 ====================
  const quotaVisible = ref(false);
  const quotaLoading = ref(false);
  const quotaTarget = ref<SysUserPageVO | null>(null);

  function openQuota(row: SysUserPageVO) {
    quotaTarget.value = row;
    quotaVisible.value = true;
  }

  onMounted(() => {
    loadData();
  });

  return {
    // 展示工具
    statusLabel,
    statusBadgeStyle,
    statusDotStyle,
    avatarText,
    avatarStyle,
    formatTime,
    // 列表
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    searchForm,
    loadData,
    handleSearch,
    handleReset,
    // 配额弹窗
    quotaVisible,
    quotaLoading,
    quotaTarget,
    openQuota,
    // 常量
    USER_STATUS_OPTIONS,
    PAGINATION_LAYOUT,
  };
}

export type UseQuotaUserList = ReturnType<typeof useQuotaUserList>;