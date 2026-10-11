import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  DEFAULT_PAGE_SIZE,
  PAGINATION_LAYOUT,
  SYMPTOM_MAX_SEVERITY,
} from "@/shared/api/config";
import {
  getSymptomDictPage,
  addSymptomDict,
  updateSymptomDict,
  updateSymptomDictStatus,
  batchDeleteSymptomDict,
  getSymptomDictOptions,
} from "@/admin/api/symptom/symptom-dict";
import {
  FALLBACK_CATEGORIES,
  categoryStyle,
  levelColor,
  severityText,
} from "@/admin/constants/symptom";

/* ==================== 类型定义 ==================== */

/** 症状字典列表行 */
export interface SymptomRow {
  id: number;
  symptomTerm: string;
  symptomCategory: string;
  synonymWords?: string[];
  severityDefault?: number;
  status?: number;
  createdTime?: string;
  updatedTime?: string;
}

/** 新增 / 编辑弹窗提交载荷（表单校验通过后由 SymptomFormDialog 回传） */
export interface SymptomSubmitPayload {
  id: number | null;
  symptomTerm: string;
  symptomCategory: string;
  synonymWords: string[];
  severityDefault: number;
  status: number;
}

interface SymptomOptionItem {
  id: number;
  symptomTerm: string;
  severityDefault: number;
  synonymWords: string[];
}

interface SymptomOptionGroup {
  symptomCategory: string;
  terms: SymptomOptionItem[];
}

/**
 * 症状字典列表页业务逻辑（单一职责拆分）：
 * - 列表加载 / 搜索 / 分页 / 统计卡数据
 * - 新增 / 编辑弹窗开关与提交
 * - 删除 / 批量删除 / 启停用
 *
 * 表单内部状态与校验由 SymptomFormDialog 自持，通过 submit 事件回传载荷。
 */
export function useSymptomList() {
  /* ==================== 时间格式化 ==================== */

  function formatTime(v?: string | number): string {
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

  const loading = ref(false);
  const tableData = ref<SymptomRow[]>([]);
  const total = ref(0);
  const currentPage = ref(1);
  const pageSize = ref(DEFAULT_PAGE_SIZE);
  const selectedIds = ref<number[]>([]);

  const searchForm = reactive<{ symptomTerm: string; symptomCategory: string; status?: number }>({
    symptomTerm: "",
    symptomCategory: "",
    status: undefined,
  });

  const categoryOptions = ref<string[]>([]);
  const enabledCount = ref(0);

  /* ==================== 弹窗状态 ==================== */

  const dialogVisible = ref(false);
  const submitting = ref(false);
  const dialogTitle = ref("新增症状");
  const dialogTarget = ref<SymptomRow | null>(null);

  /* ==================== 数据加载 ==================== */

  async function loadOptions() {
    try {
      const res: { data: { data: SymptomOptionGroup[] } } = await getSymptomDictOptions();
      const groups = res.data.data ?? [];
      categoryOptions.value = groups.map((g) => g.symptomCategory).filter(Boolean);
      enabledCount.value = groups.reduce((sum, g) => sum + (g.terms?.length ?? 0), 0);
      if (!categoryOptions.value.length) categoryOptions.value = [...FALLBACK_CATEGORIES];
    } catch {
      categoryOptions.value = [...FALLBACK_CATEGORIES];
    }
  }

  function buildParams() {
    return {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      symptomTerm: searchForm.symptomTerm || undefined,
      symptomCategory: searchForm.symptomCategory || undefined,
      status: searchForm.status,
    };
  }

  async function loadData() {
    loading.value = true;
    try {
      const res: { data: { data: { records: SymptomRow[]; total: number } } } = await getSymptomDictPage(buildParams());
      tableData.value = res.data.data?.records ?? [];
      total.value = res.data.data?.total ?? 0;
    } catch {
      ElMessage.error("加载症状字典失败，请稍后重试");
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
    searchForm.symptomTerm = "";
    searchForm.symptomCategory = "";
    searchForm.status = undefined;
    currentPage.value = 1;
    loadData();
  }

  /* ==================== 交互：勾选 / 状态 ==================== */

  function handleSelectionChange(rows: SymptomRow[]) {
    selectedIds.value = rows.map((r) => r.id);
  }

  async function handleStatusToggle(row: SymptomRow, val: string | number | boolean) {
    const next = val ? 1 : 0;
    try {
      await updateSymptomDictStatus(String(row.id), next);
      row.status = next;
      ElMessage.success(next === 1 ? `「${row.symptomTerm}」已启用` : `「${row.symptomTerm}」已禁用`);
    } catch {
      ElMessage.error("状态更新失败，请稍后重试");
    }
  }

  /* ==================== 交互：新增 / 编辑 ==================== */

  function handleAdd() {
    dialogTarget.value = null;
    dialogTitle.value = "新增症状";
    dialogVisible.value = true;
  }

  async function handleEdit(row: SymptomRow) {
    dialogTitle.value = "编辑症状";
    dialogTarget.value = row;
    dialogVisible.value = true;
  }

  /** 弹窗提交（载荷由 SymptomFormDialog 校验后回传） */
  async function handleSubmit(payload: SymptomSubmitPayload) {
    submitting.value = true;
    try {
      const { id, ...body } = payload;
      if (id == null) {
        await addSymptomDict(body);
        ElMessage.success("症状新增成功");
      } else {
        await updateSymptomDict(String(id), body);
        ElMessage.success("症状更新成功");
      }
      dialogVisible.value = false;
      await loadOptions();
      await loadData();
    } catch {
      ElMessage.error("保存失败，请稍后重试");
    } finally {
      submitting.value = false;
    }
  }

  /* ==================== 交互：删除 ==================== */

  async function handleDelete(row: SymptomRow) {
    try {
      await ElMessageBox.confirm(`确定删除症状「${row.symptomTerm}」吗？删除后不可恢复。`, "删除确认", {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        confirmButtonClass: "el-button--danger",
      });
    } catch {
      return;
    }
    try {
      await batchDeleteSymptomDict([row.id]);
      ElMessage.success("删除成功");
      loadData();
    } catch {
      ElMessage.error("删除失败，请稍后重试");
    }
  }

  async function handleBatchDelete() {
    const num = selectedIds.value.length;
    if (!num) return;
    try {
      await ElMessageBox.confirm(`确定删除选中的 ${num} 个症状吗？删除后不可恢复。`, "批量删除确认", {
        type: "warning",
        confirmButtonText: "批量删除",
        cancelButtonText: "取消",
        confirmButtonClass: "el-button--danger",
      });
    } catch {
      return;
    }
    try {
      await batchDeleteSymptomDict(selectedIds.value);
      ElMessage.success(`已删除 ${num} 个症状`);
      selectedIds.value = [];
      loadData();
    } catch {
      ElMessage.error("批量删除失败，请稍后重试");
    }
  }

  /* ==================== 初始化 ==================== */

  onMounted(() => {
    loadOptions();
    loadData();
  });

  return {
    // 展示工具
    formatTime,
    categoryStyle,
    levelColor,
    severityText,
    // 列表
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    searchForm,
    categoryOptions,
    enabledCount,
    selectedIds,
    handleSelectionChange,
    loadData,
    handleSearch,
    handleReset,
    handleStatusToggle,
    // 弹窗
    dialogVisible,
    dialogTitle,
    dialogTarget,
    submitting,
    handleAdd,
    handleEdit,
    handleSubmit,
    // 删除
    handleDelete,
    handleBatchDelete,
    // 常量
    SYMPTOM_MAX_SEVERITY,
    PAGINATION_LAYOUT,
  };
}

export type UseSymptomList = ReturnType<typeof useSymptomList>;
