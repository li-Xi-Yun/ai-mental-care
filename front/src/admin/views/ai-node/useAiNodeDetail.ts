import { computed, onMounted, reactive, ref, shallowRef } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import {
  getAiNodeConfigDetail,
  updateAiNodeConfig,
  getAiNodePrompt,
  updateAiNodePrompt,
  getAiNodeCacheStatus,
  refreshAiNodeCacheAll,
  refreshAiNodeCache,
} from "@/admin/api/ai-node/config";
import type {
  AiNodeCacheVO,
  AiNodeConfigVO,
  AiNodeConfigUpdateDTO,
} from "@/admin/api/ai-node/config";
import {
  getAiNodeHistoryPage,
  getAiNodeHistoryDetail,
  rollbackAiNodeHistory,
} from "@/admin/api/ai-node/history";
import type { AiNodeConfigHistoryVO } from "@/admin/api/ai-node/history";
import {
  AI_NODE_MODEL_TYPE_OPTIONS,
  AI_NODE_MODEL_NAME_FIELDS,
  AI_NODE_FORM_DEFAULTS,
} from "@/admin/constants/aiNode";

export interface NodeFormState {
  nodeName: string;
  nodeGroup: string;
  remark: string;
  enabled: number;
  sort: number;
  modelType: number;
  deepseekModelName: string;
  ollamaModelName: string;
  dashscopeModelName: string;
  openaiModelName: string;
  responseFormat: number;
  stopSequences: string;
  maxToken: number;
  temperature: number;
  topP: number;
  topK: number;
  frequencyPenalty: number;
  presencePenalty: number;
  retryMaxAttempts: number;
  retryDelay: number;
  retryMultiplier: number;
}

/** AI 节点详情页全部逻辑（单一职责）：详情加载、表单装配、保存、提示词、历史、缓存。 */
export function useAiNodeDetail() {
  /* ==================== 路由参数 ==================== */
  const route = useRoute();
  const nodeConfigId = String(route.params.id ?? "");

  const modelTypeOptions = AI_NODE_MODEL_TYPE_OPTIONS;
  const MODEL_NAME_FIELDS = AI_NODE_MODEL_NAME_FIELDS;

  /* ==================== 页面状态 ==================== */
  const pageLoading = ref(true);
  const pageError = ref(false);
  const detail = shallowRef<AiNodeConfigVO | null>(null);

  const savingConfig = ref(false);
  const savingPrompt = ref(false);
  const activeTab = ref("history");

  const form = reactive<NodeFormState>({
    nodeName: "",
    nodeGroup: "",
    remark: "",
    enabled: AI_NODE_FORM_DEFAULTS.enabled,
    sort: AI_NODE_FORM_DEFAULTS.sort,
    modelType: AI_NODE_FORM_DEFAULTS.modelType,
    deepseekModelName: "",
    ollamaModelName: "",
    dashscopeModelName: "",
    openaiModelName: "",
    responseFormat: AI_NODE_FORM_DEFAULTS.responseFormat,
    stopSequences: "",
    maxToken: AI_NODE_FORM_DEFAULTS.maxToken,
    temperature: AI_NODE_FORM_DEFAULTS.temperature,
    topP: AI_NODE_FORM_DEFAULTS.topP,
    topK: AI_NODE_FORM_DEFAULTS.topK,
    frequencyPenalty: AI_NODE_FORM_DEFAULTS.frequencyPenalty,
    presencePenalty: AI_NODE_FORM_DEFAULTS.presencePenalty,
    retryMaxAttempts: AI_NODE_FORM_DEFAULTS.retryMaxAttempts,
    retryDelay: AI_NODE_FORM_DEFAULTS.retryDelay,
    retryMultiplier: AI_NODE_FORM_DEFAULTS.retryMultiplier,
  });

  const promptText = ref("");
  const promptVersion = ref(0);

  const cacheStatus = shallowRef<AiNodeCacheVO | null>(null);
  const cacheLoading = ref(false);
  const cacheMutating = ref(false);

  const historyList = shallowRef<AiNodeConfigHistoryVO[]>([]);
  const historyTotal = ref(0);
  const historyPage = ref(1);
  const historyPageSize = DEFAULT_PAGE_SIZE;
  const historyLoading = ref(false);
  const historyKeyword = ref("");

  const historyDialogVisible = ref(false);
  const historyDetailLoading = ref(false);
  const historyDetail = shallowRef<AiNodeConfigHistoryVO | null>(null);

  /* ==================== 派生状态 ==================== */
  const enabledTagType = computed<"success" | "info">(() => (form.enabled === 1 ? "success" : "info"));
  const enabledLabel = computed(() => (form.enabled === 1 ? "启用" : "禁用"));

  const modelNameLabel = computed(() => {
    const type = modelTypeOptions.find((m) => m.value === form.modelType);
    return `${type?.label ?? "模型"}名称`;
  });

  const modelNameValue = computed<string>({
    get: () => form[MODEL_NAME_FIELDS[form.modelType]],
    set: (val: string) => {
      form[MODEL_NAME_FIELDS[form.modelType]] = val;
    },
  });

  const currentModelLabel = computed(() => {
    const type = modelTypeOptions.find((m) => m.value === form.modelType);
    const name = String(form[MODEL_NAME_FIELDS[form.modelType]] || "");
    return name ? `${type?.label ?? "未知"} · ${name}` : type?.label ?? "未配置";
  });

  const versionRange = computed(() => {
    const oldV = historyDetail.value?.oldVersion;
    const newV = historyDetail.value?.newVersion;
    if (oldV != null || newV != null) return `v${oldV ?? "?"} → v${newV ?? "?"}`;
    return "-";
  });

  /* ==================== 工具函数 ==================== */
  function toFiniteNumber(value: unknown, fallback: number): number {
    const n = typeof value === "number" ? value : Number(value);
    return Number.isFinite(n) ? n : fallback;
  }

  function modelTypeLabel(type: number | null | undefined): string {
    if (type == null) return "未知";
    return modelTypeOptions.find((m) => m.value === type)?.label ?? `类型 ${type}`;
  }

  function modelChanged(row: AiNodeConfigHistoryVO): boolean {
    return row.oldModelType != null && row.newModelType != null && row.oldModelType !== row.newModelType;
  }

  function hasPromptChange(row: AiNodeConfigHistoryVO): boolean {
    return Boolean(row.oldSystemPrompt || row.newSystemPrompt);
  }

  /* ==================== 数据加载 ==================== */
  function applyDetail(data: AiNodeConfigVO) {
    form.nodeName = data.nodeName ?? "";
    form.nodeGroup = data.nodeGroup ?? "";
    form.remark = data.remark ?? "";
    form.enabled = data.enabled ?? AI_NODE_FORM_DEFAULTS.enabled;
    form.sort = data.sort ?? AI_NODE_FORM_DEFAULTS.sort;
    form.modelType = data.modelType ?? AI_NODE_FORM_DEFAULTS.modelType;
    form.deepseekModelName = data.deepseekModelName ?? "";
    form.ollamaModelName = data.ollamaModelName ?? "";
    form.dashscopeModelName = data.dashscopeModelName ?? "";
    form.openaiModelName = data.openaiModelName ?? "";
    form.maxToken = toFiniteNumber(data.maxToken, AI_NODE_FORM_DEFAULTS.maxToken);
    form.temperature = toFiniteNumber(data.temperature, AI_NODE_FORM_DEFAULTS.temperature);
    form.topP = toFiniteNumber(data.topP, AI_NODE_FORM_DEFAULTS.topP);
    form.topK = toFiniteNumber(data.topK, AI_NODE_FORM_DEFAULTS.topK);
    form.frequencyPenalty = toFiniteNumber(data.frequencyPenalty, AI_NODE_FORM_DEFAULTS.frequencyPenalty);
    form.presencePenalty = toFiniteNumber(data.presencePenalty, AI_NODE_FORM_DEFAULTS.presencePenalty);
    form.responseFormat = data.responseFormat ?? AI_NODE_FORM_DEFAULTS.responseFormat;
    form.stopSequences = data.stopSequences ?? "";
    form.retryMaxAttempts = toFiniteNumber(data.retryMaxAttempts, AI_NODE_FORM_DEFAULTS.retryMaxAttempts);
    form.retryDelay = toFiniteNumber(data.retryDelay, AI_NODE_FORM_DEFAULTS.retryDelay);
    form.retryMultiplier = toFiniteNumber(data.retryMultiplier, AI_NODE_FORM_DEFAULTS.retryMultiplier);
  }

  async function loadConfig() {
    pageLoading.value = true;
    pageError.value = false;
    try {
      const res = await getAiNodeConfigDetail(nodeConfigId);
      const data = res.data.data;
      detail.value = data ?? null;
      if (!data) {
        pageError.value = true;
        return;
      }
      applyDetail(data);
    } catch (e) {
      pageError.value = true;
      ElMessage.error((e as Error)?.message || "节点详情加载失败");
    } finally {
      pageLoading.value = false;
    }
  }

  async function loadPrompt() {
    if (!nodeConfigId) return;
    try {
      const res = await getAiNodePrompt(nodeConfigId);
      const data = res.data.data;
      if (data) {
        promptText.value = data.systemPrompt ?? "";
        promptVersion.value = data.version ?? 0;
        return;
      }
    } catch (e) {
      ElMessage.error((e as Error)?.message || "提示词加载失败");
    }
    // 兜底：直接使用详情中的提示词
    if (detail.value) {
      promptText.value = detail.value.systemPrompt ?? "";
      promptVersion.value = detail.value.version ?? 0;
    }
  }

  async function loadHistory(page = historyPage.value) {
    historyLoading.value = true;
    try {
      const res = await getAiNodeHistoryPage(nodeConfigId, {
        pageNum: page,
        pageSize: historyPageSize,
        changeSummary: historyKeyword.value.trim() || null,
      });
      const pageData = res.data.data;
      historyList.value = pageData?.records ?? [];
      historyTotal.value = pageData?.total ?? 0;
      historyPage.value = page;
    } catch (e) {
      historyList.value = [];
      historyTotal.value = 0;
      ElMessage.error((e as Error)?.message || "变更历史加载失败");
    } finally {
      historyLoading.value = false;
    }
  }

  async function loadCacheStatus() {
    cacheLoading.value = true;
    try {
      const res = await getAiNodeCacheStatus();
      cacheStatus.value = res.data.data ?? null;
    } catch (e) {
      cacheStatus.value = null;
      ElMessage.error((e as Error)?.message || "缓存状态获取失败");
    } finally {
      cacheLoading.value = false;
    }
  }

  /* ==================== 操作 ==================== */
  function buildConfigDTO(): AiNodeConfigUpdateDTO {
    const source = detail.value;
    return {
      nodeName: form.nodeName.trim(),
      nodeKey: source?.nodeKey ?? undefined,
      nodeGroup: form.nodeGroup.trim() || undefined,
      systemPrompt: source?.systemPrompt ?? promptText.value.trim(),
      modelType: form.modelType,
      deepseekModelName: form.deepseekModelName.trim() || undefined,
      ollamaModelName: form.ollamaModelName.trim() || undefined,
      dashscopeModelName: form.dashscopeModelName.trim() || undefined,
      openaiModelName: form.openaiModelName.trim() || undefined,
      maxToken: form.maxToken,
      temperature: form.temperature,
      topP: form.topP,
      topK: form.topK,
      frequencyPenalty: form.frequencyPenalty,
      presencePenalty: form.presencePenalty,
      responseFormat: form.responseFormat,
      stopSequences: form.stopSequences.trim() || undefined,
      retryMaxAttempts: form.retryMaxAttempts,
      retryDelay: form.retryDelay,
      retryMultiplier: form.retryMultiplier,
      enabled: form.enabled,
      sort: form.sort,
      remark: form.remark.trim() || undefined,
      version: source?.version ?? 0,
    };
  }

  /** 保存基本信息 / 模型 / 参数 */
  async function handleSaveConfig() {
    if (!form.nodeName.trim()) {
      ElMessage.warning("节点名称不能为空");
      return;
    }
    if (!promptText.value.trim()) {
      ElMessage.warning("系统提示词不能为空，请先在下方编辑并保存");
      return;
    }
    savingConfig.value = true;
    try {
      await updateAiNodeConfig(nodeConfigId, buildConfigDTO());
      ElMessage.success("节点配置已保存");
      await Promise.all([loadConfig(), loadHistory()]);
    } catch (e) {
      ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
    } finally {
      savingConfig.value = false;
    }
  }

  /** 独立保存 System Prompt */
  async function handleSavePrompt() {
    if (!promptText.value.trim()) {
      ElMessage.warning("系统提示词不能为空");
      return;
    }
    savingPrompt.value = true;
    try {
      await updateAiNodePrompt(nodeConfigId, {
        systemPrompt: promptText.value.trim(),
        version: promptVersion.value,
      });
      ElMessage.success("提示词已保存，变更历史已记录");
      await Promise.all([loadConfig(), loadPrompt(), loadHistory()]);
    } catch (e) {
      ElMessage.error((e as Error)?.message || "提示词保存失败，请稍后重试");
    } finally {
      savingPrompt.value = false;
    }
  }

  /** 历史搜索 */
  function handleHistorySearch() {
    historyPage.value = 1;
    loadHistory();
  }

  /** 查看历史详情 */
  async function openHistoryDetail(row: AiNodeConfigHistoryVO) {
    historyDialogVisible.value = true;
    historyDetailLoading.value = true;
    historyDetail.value = null;
    try {
      const res = await getAiNodeHistoryDetail(String(row.id ?? ""));
      historyDetail.value = res.data.data ?? row;
    } catch (e) {
      historyDetail.value = row;
      ElMessage.error((e as Error)?.message || "历史详情加载失败");
    } finally {
      historyDetailLoading.value = false;
    }
  }

  /** 回滚到指定历史版本 */
  async function handleRollback(row: AiNodeConfigHistoryVO) {
    try {
      await ElMessageBox.confirm(
        `确定将节点「${form.nodeName || detail.value?.nodeName}」回滚至 ${row.oldVersion != null ? `v${row.oldVersion} ` : ""}${row.createdTime || ""} 由「${row.createdByName || "系统"}」提交的版本吗？\n回滚后会自动记录新的变更记录并刷新缓存。`,
        "回滚确认",
        {
          type: "warning",
          confirmButtonText: "确认回滚",
          cancelButtonText: "取消",
          cancelButtonClass: "el-button--default",
        },
      );
    } catch {
      return; // 用户取消
    }
    try {
      await rollbackAiNodeHistory(nodeConfigId, String(row.id ?? ""));
      ElMessage.success("回滚成功，配置已还原");
      await Promise.all([loadConfig(), loadPrompt(), loadHistory(), loadCacheStatus()]);
    } catch (e) {
      ElMessage.error((e as Error)?.message || "回滚失败，请稍后重试");
    }
  }

  /** 全量刷新缓存 */
  async function handleRefreshAllCache() {
    cacheMutating.value = true;
    try {
      await refreshAiNodeCacheAll();
      ElMessage.success("已全量刷新缓存");
      await loadCacheStatus();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "缓存刷新失败");
    } finally {
      cacheMutating.value = false;
    }
  }

  /** 刷新当前节点缓存 */
  async function handleRefreshNodeCache() {
    cacheMutating.value = true;
    try {
      await refreshAiNodeCache(nodeConfigId);
      ElMessage.success("当前节点缓存已刷新");
      await loadCacheStatus();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "缓存刷新失败");
    } finally {
      cacheMutating.value = false;
    }
  }

  /** 全量刷新页面数据（顶部刷新按钮） */
  async function handleReload() {
    await Promise.all([loadConfig(), loadPrompt(), loadHistory(), loadCacheStatus()]);
  }

  onMounted(() => {
    loadConfig();
    loadPrompt();
    loadHistory();
    loadCacheStatus();
  });

  return {
    // 路由与常量
    nodeConfigId,
    modelTypeOptions,
    MODEL_NAME_FIELDS,
    // 页面状态
    pageLoading,
    pageError,
    detail,
    savingConfig,
    savingPrompt,
    activeTab,
    form,
    promptText,
    promptVersion,
    cacheStatus,
    cacheLoading,
    cacheMutating,
    historyList,
    historyTotal,
    historyPage,
    historyLoading,
    historyKeyword,
    historyDialogVisible,
    historyDetailLoading,
    historyDetail,
    // 派生
    enabledTagType,
    enabledLabel,
    modelNameLabel,
    modelNameValue,
    currentModelLabel,
    versionRange,
    // 工具
    modelTypeLabel,
    modelChanged,
    hasPromptChange,
    // 加载
    loadConfig,
    loadPrompt,
    loadHistory,
    loadCacheStatus,
    handleReload,
    // 操作
    handleSaveConfig,
    handleSavePrompt,
    handleHistorySearch,
    openHistoryDetail,
    handleRollback,
    handleRefreshAllCache,
    handleRefreshNodeCache,
    // 常量
    PAGINATION_LAYOUT,
    DEFAULT_PAGE_SIZE,
  };
}

export type UseAiNodeDetail = ReturnType<typeof useAiNodeDetail>;