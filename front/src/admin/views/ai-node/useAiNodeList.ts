import { computed, onMounted, reactive, ref, shallowRef } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import {
  pageAiNodeConfig,
  createAiNodeConfig,
  updateAiNodeConfig,
  deleteAiNodeConfig,
  batchUpdateAiNodeConfigEnabled,
  getAiNodeGroups,
  getAiNodeConfigDetail,
} from "@/admin/api/ai-node/config";
import type {
  AiNodeConfigSimpleVO,
  AiNodeConfigVO,
  AiNodeGroupVO,
} from "@/admin/api/ai-node/config";
import {
  AI_NODE_GROUP_COLORS,
  AI_NODE_MODEL_TYPE_COLORS,
  AI_NODE_MODEL_TYPE_OPTIONS,
} from "@/admin/constants/aiNode";

/** 新增 / 编辑弹窗表单模型 */
export interface NodeForm {
  nodeKey: string;
  nodeName: string;
  nodeGroup: string;
  modelType: number;
  enabled: number;
  systemPrompt: string;
  remark: string;
}

/**
 * AI 节点配置列表页全部业务逻辑（单一职责）：
 * - 展示工具（模型类型标签/配色、分组配色、时间格式化）
 * - 列表加载 / 筛选 / 分页 / 统计卡
 * - 单条与批量启停用
 * - 新增 / 编辑 / 删除 / 跳转详情
 */
export function useAiNodeList() {
  /* ==================== 常量 & 映射 ==================== */

  const MODEL_TYPE_OPTIONS = AI_NODE_MODEL_TYPE_OPTIONS;

  function modelTypeLabel(type?: number | null): string {
    const meta = MODEL_TYPE_OPTIONS.find((m) => m.value === type);
    return meta?.label ?? "未知";
  }

  function modelTypeStyle(type?: number | null): Record<string, string> {
    const meta = AI_NODE_MODEL_TYPE_COLORS[type ?? -1] ?? { color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0" };
    return { color: meta.color, backgroundColor: meta.bg, borderColor: meta.border };
  }

  /** 稳定字符串 hash（用于分组取色，保证同分组颜色一致） */
  function hashString(str: string): number {
    let h = 0;
    for (let i = 0; i < str.length; i++) {
      h = (h << 5) - h + str.charCodeAt(i);
      h |= 0;
    }
    return Math.abs(h);
  }

  function groupStyle(group?: string | null): Record<string, string> {
    const meta = group
      ? AI_NODE_GROUP_COLORS[hashString(group) % AI_NODE_GROUP_COLORS.length]
      : { color: "#94a3b8", bg: "#f8fafc", border: "#eef1f6" };
    return { color: meta.color, backgroundColor: meta.bg, borderColor: meta.border };
  }

  function formatTime(v?: string | number | null): string {
    if (v === undefined || v === null || v === "") return "—";
    // 后端可能返回时间戳（number）或 ISO 字符串；number 转 Date，字符串沿用原格式
    if (typeof v === "number") {
      const d = new Date(v);
      const pad = (n: number) => String(n).padStart(2, "0");
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    }
    return String(v).replace("T", " ").slice(0, 16);
  }

  /* ==================== 页面状态 ==================== */

  const router = useRouter();
  const loading = ref(false);
  const tableData = shallowRef<AiNodeConfigSimpleVO[]>([]);
  const total = ref(0);
  const currentPage = ref(1);
  const pageSize = DEFAULT_PAGE_SIZE;
  const selectedRows = shallowRef<AiNodeConfigSimpleVO[]>([]);

  const searchForm = reactive<{ keyword: string; nodeGroup: string; modelType?: number; enabled?: number }>({
    keyword: "",
    nodeGroup: "",
    modelType: undefined,
    enabled: undefined,
  });

  const rawGroupOptions = shallowRef<AiNodeGroupVO[]>([]);
  const groupOptions = computed<AiNodeGroupVO[]>(() => rawGroupOptions.value);

  const enabledCount = computed(() => tableData.value.filter((r) => r.enabled === 1).length);

  /* ==================== 弹窗状态 ==================== */

  const dialogVisible = ref(false);
  const submitting = ref(false);
  const dialogTitle = ref("新增节点");
  const isEdit = ref(false);
  const editingId = ref<number | null>(null);
  const editingVersion = ref(0);

  const emptyForm: NodeForm = {
    nodeKey: "",
    nodeName: "",
    nodeGroup: "",
    modelType: 1,
    enabled: 1,
    systemPrompt: "",
    remark: "",
  };

  const form = reactive<NodeForm>({ ...emptyForm });

  /* ==================== 数据加载 ==================== */

  async function loadGroups() {
    try {
      const res = await getAiNodeGroups();
      rawGroupOptions.value = res.data.data ?? [];
    } catch {
      rawGroupOptions.value = [];
    }
  }

  function buildParams() {
    // 单输入框需同时支持「节点标识 / 名称」搜索，但后端对 nodeKey 与 nodeName 为 AND 语义，
    // 同一关键词同时下发两个条件会互相过滤导致恒为 0 条（历史缺陷：搜索不可用）。
    // 前端按输入特征选择更匹配的字段下发：纯 ASCII 无空格（节点标识形态）→ nodeKey，其余（中文名称）→ nodeName。
    // 根治建议：后端提供关键字 OR 查询（已在第五轮报告中登记）。
    const keyword = searchForm.keyword.trim();
    const keyLikely = /^[\x21-\x7E]+$/.test(keyword);
    return {
      pageNum: currentPage.value,
      pageSize,
      nodeKey: keyword && keyLikely ? keyword : undefined,
      nodeName: keyword && !keyLikely ? keyword : undefined,
      nodeGroup: searchForm.nodeGroup || undefined,
      modelType: searchForm.modelType,
      enabled: searchForm.enabled,
    };
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await pageAiNodeConfig(buildParams());
      tableData.value = res.data.data?.records ?? [];
      total.value = res.data.data?.total ?? 0;
    } catch (e) {
      ElMessage.error((e as Error)?.message || "节点列表加载失败，请稍后重试");
    } finally {
      loading.value = false;
    }
  }

  /* ==================== 交互：搜索 / 重置 ==================== */

  function handleSearch() {
    currentPage.value = 1;
    loadData();
  }

  function handleReset() {
    searchForm.keyword = "";
    searchForm.nodeGroup = "";
    searchForm.modelType = undefined;
    searchForm.enabled = undefined;
    currentPage.value = 1;
    loadData();
  }

  /* ==================== 交互：勾选 / 状态 ==================== */

  function handleSelectionChange(rows: AiNodeConfigSimpleVO[]) {
    selectedRows.value = rows;
  }

  async function handleStatusToggle(row: AiNodeConfigSimpleVO, val: string | number | boolean) {
    const next = val ? 1 : 0;
    try {
      await batchUpdateAiNodeConfigEnabled({ nodeKeys: [String(row.nodeKey ?? "")], enabled: next });
      ElMessage.success(next === 1 ? `「${row.nodeName}」已启用` : `「${row.nodeName}」已禁用`);
      // 列表数据可能跨页，整体刷新以同步状态与统计
      await loadData();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "状态更新失败，请稍后重试");
    }
  }

  async function handleBatchToggle(enabled: boolean) {
    if (!selectedRows.value.length) return;
    const keys = selectedRows.value.map((r) => String(r.nodeKey ?? "")).filter(Boolean);
    const num = keys.length;
    if (!num) return;
    const text = enabled ? "启用" : "禁用";
    try {
      await ElMessageBox.confirm(`确定将选中的 ${num} 个节点${text}吗？`, `批量${text}确认`, {
        type: "warning",
        confirmButtonText: `批量${text}`,
        cancelButtonText: "取消",
      });
    } catch {
      return;
    }
    try {
      await batchUpdateAiNodeConfigEnabled({ nodeKeys: keys, enabled: enabled ? 1 : 0 });
      ElMessage.success(`已${text} ${num} 个节点`);
      selectedRows.value = [];
      await Promise.all([loadData(), loadGroups()]);
    } catch (e) {
      ElMessage.error((e as Error)?.message || `批量${text}失败，请稍后重试`);
    }
  }

  /* ==================== 交互：新增 / 编辑 ==================== */

  function handleAdd() {
    isEdit.value = false;
    editingId.value = null;
    editingVersion.value = 0;
    Object.assign(form, emptyForm);
    dialogTitle.value = "新增节点";
    dialogVisible.value = true;
  }

  async function handleEdit(row: AiNodeConfigSimpleVO) {
    isEdit.value = true;
    editingId.value = row.id ?? null;
    editingVersion.value = row.version ?? 0;
    Object.assign(form, {
      nodeKey: row.nodeKey ?? "",
      nodeName: row.nodeName ?? "",
      nodeGroup: row.nodeGroup ?? "",
      modelType: row.modelType ?? 1,
      enabled: row.enabled ?? 1,
      systemPrompt: "",
      remark: row.remark ?? "",
    });
    dialogTitle.value = "编辑节点";
    dialogVisible.value = true;
    // 拉取完整配置（含系统提示词）回填
    if (row.id != null) {
      try {
        const res = await getAiNodeConfigDetail(row.id);
        const detail: AiNodeConfigVO | undefined = res.data.data;
        if (detail) {
          form.systemPrompt = detail.systemPrompt ?? "";
          if (detail.version != null) editingVersion.value = detail.version;
        }
      } catch {
        ElMessage.warning("系统提示词加载失败，可稍后在详情页补充");
      }
    }
  }

  function handleViewDetail(row: AiNodeConfigSimpleVO) {
    if (row.id == null) {
      ElMessage.warning("该节点缺少ID，无法查看详情");
      return;
    }
    router.push(`/admin/ai-node/${row.id}`);
  }

  /** 弹窗表单校验通过后提交（表单校验在 AiNodeFormDialog 内完成） */
  async function handleSubmit() {
    submitting.value = true;
    try {
      const shared = {
        nodeName: form.nodeName.trim(),
        nodeGroup: form.nodeGroup.trim() || undefined,
        modelType: form.modelType,
        enabled: form.enabled,
        remark: form.remark.trim() || undefined,
      };
      if (isEdit.value) {
        if (editingId.value == null) {
          ElMessage.error("缺少节点ID，无法更新");
          return;
        }
        await updateAiNodeConfig(editingId.value, {
          ...shared,
          nodeKey: form.nodeKey || undefined,
          systemPrompt: form.systemPrompt.trim(),
          maxToken: 2048,
          temperature: 0.7,
          topP: 0.85,
          retryMaxAttempts: 3,
          retryDelay: 1000,
          retryMultiplier: 2,
          version: editingVersion.value,
        });
        ElMessage.success("节点更新成功");
      } else {
        await createAiNodeConfig({
          ...shared,
          nodeKey: form.nodeKey.trim(),
          systemPrompt: form.systemPrompt.trim(),
          maxToken: 2048,
          temperature: 0.7,
          topP: 0.85,
          retryMaxAttempts: 3,
          retryDelay: 1000,
          retryMultiplier: 2,
        });
        ElMessage.success("节点新增成功");
      }
      dialogVisible.value = false;
      await Promise.all([loadData(), loadGroups()]);
    } catch (e) {
      ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
    } finally {
      submitting.value = false;
    }
  }

  /* ==================== 交互：删除 ==================== */

  async function handleDelete(row: AiNodeConfigSimpleVO) {
    try {
      await ElMessageBox.confirm(`确定删除节点「${row.nodeName}」吗？删除后不可恢复。`, "删除确认", {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        confirmButtonClass: "el-button--danger",
      });
    } catch {
      return;
    }
    if (row.id == null) return;
    try {
      await deleteAiNodeConfig(row.id);
      ElMessage.success("删除成功");
      if (tableData.value.length === 1 && currentPage.value > 1) {
        currentPage.value -= 1;
      }
      await Promise.all([loadData(), loadGroups()]);
    } catch (e) {
      ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
    }
  }

  /* ==================== 初始化 ==================== */

  onMounted(() => {
    loadGroups();
    loadData();
  });

  return {
    // 展示工具
    modelTypeLabel,
    modelTypeStyle,
    groupStyle,
    formatTime,
    // 列表
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    selectedRows,
    searchForm,
    groupOptions,
    enabledCount,
    handleSelectionChange,
    loadData,
    handleSearch,
    handleReset,
    // 启停用
    handleStatusToggle,
    handleBatchToggle,
    // 新增 / 编辑 / 删除 / 详情
    handleAdd,
    handleEdit,
    handleViewDetail,
    handleDelete,
    handleSubmit,
    // 弹窗状态
    dialogVisible,
    dialogTitle,
    isEdit,
    submitting,
    form,
    // 常量
    MODEL_TYPE_OPTIONS,
    PAGINATION_LAYOUT,
  };
}

export type UseAiNodeList = ReturnType<typeof useAiNodeList>;
