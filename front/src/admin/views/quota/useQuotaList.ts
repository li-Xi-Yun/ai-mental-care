import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { getQuotaConfigList, updateQuotaConfig } from "@/admin/api/quota/quota";
import type { QuotaConfigVO } from "@/admin/api/quota/quota";
import {
  QUOTA_AVAILABLE_META,
  QUOTA_AVAILABLE_OPTIONS,
  QUOTA_CYCLE_META,
  QUOTA_CYCLE_OPTIONS,
  QUOTA_ENABLED_META,
  QUOTA_ENABLED_OPTIONS,
  QUOTA_RESOURCE_META,
  QUOTA_RESOURCE_OPTIONS,
  quotaResourceColor,
  quotaResourceName,
} from "@/admin/constants/quota";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";

/**
 * 全局限流管理页业务逻辑（单一职责拆分）：
 * - 展示工具（资源徽章配色 / 可用 / 限流 / 周期 元数据）
 * - 全局配置加载（资源固定 4 种，无分页，前端按筛选条件过滤）
 * - 统计卡（资源总数 / 已启用限流 / 已停用资源 / 默认总额度）
 * - 配置编辑弹窗（回填 / 提交 / 刷新）
 *
 * 配置更新为全字段局部更新（后端 PUT /admin/quota/config/{resourceType}），
 * remark 不允许在管理端编辑，不随载荷提交。
 */

/** 全局配置编辑表单模型（父级持有，子组件只渲染与校验） */
export interface QuotaConfigForm {
  resourceType: number;
  defaultLimit: number | null;
  available: number;
  enabled: number;
  cycleType: number;
}
export function useQuotaList() {
  // ==================== 展示工具 ====================
  const availableMeta = useEnumMeta(QUOTA_AVAILABLE_META, { fallbackKey: 1 });
  const enabledMeta = useEnumMeta(QUOTA_ENABLED_META, { fallbackKey: 0 });
  const cycleMeta = useEnumMeta(QUOTA_CYCLE_META, { fallbackKey: 0 });

  function resourceNameOf(row: { resourceName?: string; resourceType?: number | null }): string {
    return row.resourceName?.trim() || quotaResourceName(row.resourceType);
  }

  function resourceBadgeStyle(type?: number | null): Record<string, string> {
    const meta = type == null ? undefined : QUOTA_RESOURCE_META[type];
    return {
      color: meta?.color ?? "#64748b",
      background: meta?.bg ?? "#f1f5f9",
      borderColor: meta?.border ?? "#e2e8f0",
    };
  }

  function resourceDotStyle(type?: number | null): Record<string, string> {
    return { background: quotaResourceColor(type) };
  }

  // ==================== 列表与筛选 ====================
  const loading = ref(false);
  const configList = ref<QuotaConfigVO[]>([]);

  const filterState = reactive<{
    resourceType?: number;
    available?: number;
    enabled?: number;
  }>({
    resourceType: undefined,
    available: undefined,
    enabled: undefined,
  });

  async function loadData() {
    loading.value = true;
    try {
      const res = await getQuotaConfigList();
      configList.value = res.data?.data ?? [];
    } catch {
      ElMessage.error("加载全局配额配置失败，请稍后重试");
    } finally {
      loading.value = false;
    }
  }

  /** 前端过滤（资源配置固定 4 种，后端接口不支持筛选，故在本地过滤） */
  const filteredList = computed(() =>
    configList.value.filter((c) => {
      if (filterState.resourceType != null && c.resourceType !== filterState.resourceType) return false;
      if (filterState.available != null && c.available !== filterState.available) return false;
      if (filterState.enabled != null && c.enabled !== filterState.enabled) return false;
      return true;
    }),
  );

  function handleSearch() {
    // 本地筛选，无请求开销；保持函数签名一致便于模板绑定
  }

  function handleReset() {
    filterState.resourceType = undefined;
    filterState.available = undefined;
    filterState.enabled = undefined;
  }

  // ==================== 统计卡 ====================
  const stats = computed(() => {
    const list = configList.value;
    return {
      total: list.length,
      enabledCount: list.filter((c) => c.enabled === 1).length,
      disabledCount: list.filter((c) => c.available === 0).length,
      sumLimit: list.reduce((sum, c) => sum + (c.defaultLimit ?? 0), 0),
    };
  });

  // ==================== 编辑弹窗 ====================
  const dialogVisible = ref(false);
  const submitting = ref(false);
  const editingType = ref<number | null>(null);

  const emptyForm: QuotaConfigForm = {
    resourceType: 1,
    defaultLimit: null,
    available: 1,
    enabled: 1,
    cycleType: 0,
  };

  const form = reactive<QuotaConfigForm>({ ...emptyForm });

  function openEdit(row: QuotaConfigVO) {
    editingType.value = row.resourceType ?? null;
    Object.assign(form, {
      resourceType: row.resourceType ?? 1,
      defaultLimit: row.defaultLimit ?? null,
      available: row.available ?? 1,
      enabled: row.enabled ?? 0,
      cycleType: row.cycleType ?? 0,
    });
    dialogVisible.value = true;
  }

  /** 弹窗校验通过后提交：全字段局部更新，成功后刷新列表 */
  async function handleSubmit() {
    if (editingType.value == null) return;
    submitting.value = true;
    try {
      await updateQuotaConfig(editingType.value, {
        defaultLimit: form.defaultLimit,
        available: form.available,
        enabled: form.enabled,
        cycleType: form.cycleType,
      });
      ElMessage.success("全局配额配置已更新");
      dialogVisible.value = false;
      await loadData();
    } catch {
      ElMessage.error("更新失败，请稍后重试");
    } finally {
      submitting.value = false;
    }
  }

  onMounted(() => {
    loadData();
  });

  return {
    // 展示工具
    resourceNameOf,
    resourceBadgeStyle,
    resourceDotStyle,
    availableLabel: availableMeta.labelOf,
    availableBadgeStyle: availableMeta.styleOf,
    availableDotStyle: availableMeta.dotStyleOf,
    enabledLabel: enabledMeta.labelOf,
    enabledBadgeStyle: enabledMeta.styleOf,
    enabledDotStyle: enabledMeta.dotStyleOf,
    cycleLabel: cycleMeta.labelOf,
    // 列表与筛选
    loading,
    configList,
    filteredList,
    filterState,
    handleSearch,
    handleReset,
    // 统计
    stats,
    // 编辑弹窗
    dialogVisible,
    submitting,
    form,
    openEdit,
    handleSubmit,
    // 常量
    QUOTA_RESOURCE_OPTIONS,
    QUOTA_AVAILABLE_OPTIONS,
    QUOTA_ENABLED_OPTIONS,
    QUOTA_CYCLE_OPTIONS,
  };
}

export type UseQuotaList = ReturnType<typeof useQuotaList>;