import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import { getScalePage, addScale, updateScale, updateScaleStatus, deleteScale, restoreScale } from "@/admin/api/scale/scale";
import { getScaleCategoryPage, addScaleCategory, updateScaleCategory, updateScaleCategorySort, deleteScaleCategory } from "@/admin/api/scale/category";
import { SCALE_CATEGORY_COLORS, SCALE_CATEGORY_PALETTE } from "@/admin/constants/scale";

/* ==================== 类型定义 ==================== */

export interface ScaleRow {
  id: number;
  scaleName: string;
  scaleCategoryId?: number | null;
  scaleCategoryName?: string | null;
  status: number;
  allowRepeat: number;
  coolMinutes?: number | null;
  timeLimit?: number | null;
  anonymous: number;
  currentVersionNo?: string | null;
  questionCount?: number | null;
  dimensionCount?: number | null;
  createdTime?: string;
  deletedFlag?: number;
}

export interface ScaleCategoryRow {
  id: number;
  categoryName: string;
  sort?: number;
  scaleCount?: number;
}

/** 新增/编辑量表表单（子组件内部持有，submit 事件回传） */
export interface ScaleForm {
  id: number | null;
  scaleName: string;
  scaleCategoryId: number | null;
  status: number;
  allowRepeat: number;
  coolMinutes: number;
  timeLimit: number;
  anonymous: number;
}

/** 新增/编辑分类提交载荷（CategoryEditDialog submit 事件回传） */
export interface CategorySubmitPayload {
  id: number;
  categoryName: string;
  sort: number;
}

type PageResp = { records: ScaleRow[]; total: number };

/* ==================== 颜色 & 工具 ==================== */

function hashStr(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i += 1) h = (h * 31 + s.charCodeAt(i)) & 0xffffffff;
  return h;
}

/** 分类徽章配色：命中预设取主题色，否则按名称哈希从候选调色板取色 */
function categoryStyle(cat?: string | null): Record<string, string> {
  const meta = (cat && SCALE_CATEGORY_COLORS[cat]) || SCALE_CATEGORY_PALETTE[(hashStr(cat ?? "") >>> 0) % SCALE_CATEGORY_PALETTE.length] || {
    color: "#6366f1",
    bg: "#eef2ff",
    border: "#e0e7ff",
  };
  return { color: meta.color, background: meta.bg, borderColor: meta.border };
}

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

/**
 * 量表管理列表页业务逻辑（单一职责拆分）：
 * - 列表加载 / 搜索 / 分页 / 统计卡数据
 * - 启停用、删除、恢复、跳转详情
 * - 新增/编辑量表弹窗状态与提交
 * - 分类管理（列表、增改、排序、删除）
 *
 * 弹窗子组件各自持有表单状态，通过 submit 事件回传载荷。
 */
export function useScaleList() {
  const router = useRouter();

  /* ==================== 页面状态 ==================== */

  const loading = ref(false);
  const tableData = ref<ScaleRow[]>([]);
  const total = ref(0);
  const currentPage = ref(1);
  const pageSize = ref(DEFAULT_PAGE_SIZE);

  const searchForm = reactive<{ scaleName: string; scaleCategoryId: number | null; status?: number; deletedFlag?: number }>({
    scaleName: "",
    scaleCategoryId: null,
    status: undefined,
    deletedFlag: undefined,
  });

  const categoryList = ref<ScaleCategoryRow[]>([]);
  const enabledCount = ref(0);
  const categoryCount = ref(0);

  /* ==================== 数据加载 ==================== */

  function buildParams() {
    return {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      scaleName: searchForm.scaleName.trim() || undefined,
      scaleCategoryId: searchForm.scaleCategoryId ?? undefined,
      status: searchForm.status,
      deletedFlag: searchForm.deletedFlag,
    };
  }

  function unwrapPage(res: { data: { data?: PageResp } }): PageResp {
    const d = res?.data?.data;
    return { records: d?.records ?? [], total: d?.total ?? 0 };
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await getScalePage(buildParams());
      const d = unwrapPage(res as any);
      tableData.value = d.records;
      total.value = d.total;
      await refreshEnabledCount();
    } catch {
      ElMessage.error("加载量表列表失败，请稍后重试");
    } finally {
      loading.value = false;
    }
  }

  async function refreshEnabledCount() {
    try {
      const res = await getScalePage({ pageNum: 1, pageSize: 1, status: 1 });
      enabledCount.value = res.data.data?.total ?? 0;
    } catch {
      enabledCount.value = 0;
    }
  }

  async function loadCategories() {
    try {
      const res: { data: { data?: { records: ScaleCategoryRow[] } } } = await getScaleCategoryPage({ pageNum: 1, pageSize: 1000 });
      categoryList.value = res.data.data?.records ?? [];
      categoryCount.value = categoryList.value.length;
    } catch {
      categoryList.value = [];
      categoryCount.value = 0;
    }
  }

  /* ==================== 交互：搜索 / 重置 ==================== */

  function handleSearch() {
    currentPage.value = 1;
    loadData();
  }

  function handleReset() {
    searchForm.scaleName = "";
    searchForm.scaleCategoryId = null;
    searchForm.status = undefined;
    searchForm.deletedFlag = undefined;
    currentPage.value = 1;
    loadData();
  }

  /* ==================== 交互：详情 / 状态 ==================== */

  function goDetail(row: any) {
    router.push(`/admin/scale/${row.id}`);
  }

  async function handleStatusToggle(row: any, val: string | number | boolean) {
    const next = val ? 1 : 0;
    try {
      await updateScaleStatus(row.id, next);
      row.status = next;
      await refreshEnabledCount();
      ElMessage.success(next === 1 ? `「${row.scaleName}」已启用` : `「${row.scaleName}」已禁用`);
    } catch {
      ElMessage.error("状态更新失败，请稍后重试");
    }
  }

  /* ==================== 交互：新增 / 编辑量表 ==================== */

  const scaleDialogVisible = ref(false);
  const scaleSubmitting = ref(false);
  const scaleDialogTitle = ref("新增量表");
  const scaleTarget = ref<ScaleRow | null>(null);

  function handleAdd() {
    scaleTarget.value = null;
    scaleDialogTitle.value = "新增量表";
    scaleDialogVisible.value = true;
  }

  function handleEdit(row: any) {
    scaleTarget.value = row as ScaleRow;
    scaleDialogTitle.value = "编辑量表";
    scaleDialogVisible.value = true;
  }

  /** 弹窗提交（载荷由 ScaleFormDialog 校验后回传） */
  async function handleScaleSubmit(form: ScaleForm) {
    scaleSubmitting.value = true;
    try {
      const payload = {
        scaleName: form.scaleName.trim(),
        scaleCategoryId: form.scaleCategoryId,
        status: form.status,
        allowRepeat: form.allowRepeat,
        coolMinutes: form.allowRepeat === 1 ? form.coolMinutes : 0,
        timeLimit: form.timeLimit || null,
        anonymous: form.anonymous,
      };
      if (form.id == null) {
        await addScale(payload);
        ElMessage.success("量表新增成功");
      } else {
        await updateScale(form.id, payload);
        ElMessage.success("量表更新成功");
      }
      scaleDialogVisible.value = false;
      await loadData();
      await loadCategories();
    } catch {
      ElMessage.error("保存失败，请稍后重试");
    } finally {
      scaleSubmitting.value = false;
    }
  }

  /* ==================== 交互：删除 / 恢复 ==================== */

  async function handleDelete(row: any) {
    try {
      await ElMessageBox.confirm(
        `确定删除量表「${row.scaleName}」吗？删除后其版本、题目将一并进入回收站，可随时恢复。`,
        "删除确认",
        { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
      );
    } catch {
      return;
    }
    try {
      await deleteScale(row.id);
      ElMessage.success("删除成功");
      loadData();
    } catch {
      ElMessage.error("删除失败，请稍后重试");
    }
  }

  async function handleRestore(row: any) {
    try {
      await restoreScale(row.id);
      ElMessage.success(`「${row.scaleName}」已恢复`);
      loadData();
    } catch {
      ElMessage.error("恢复失败，请稍后重试");
    }
  }

  /* ==================== 交互：分类管理 ==================== */

  const categoryDialogVisible = ref(false);
  const categoryLoading = ref(false);
  const categorySubmitting = ref(false);

  function openCategoryDialog() {
    categoryDialogVisible.value = true;
    loadCategories();
  }

  const categoryEditVisible = ref(false);
  const categoryEditTitle = ref("新增分类");
  const categoryEditTarget = ref<ScaleCategoryRow | null>(null);

  function catMaxSort(): number {
    return categoryList.value.reduce((max, c) => Math.max(max, c.sort ?? 0), 0);
  }

  function openCategoryEdit(row?: ScaleCategoryRow) {
    if (row) {
      categoryEditTarget.value = { id: row.id, categoryName: row.categoryName, sort: row.sort ?? 0 };
      categoryEditTitle.value = `编辑分类「${row.categoryName}」`;
    } else {
      categoryEditTarget.value = { id: 0, categoryName: "", sort: catMaxSort() + 1 };
      categoryEditTitle.value = "新增分类";
    }
    categoryEditVisible.value = true;
  }

  /** 分类弹窗提交（载荷由 CategoryEditDialog 校验后回传） */
  async function handleCategorySubmit(payload: CategorySubmitPayload) {
    categorySubmitting.value = true;
    try {
      const body = { categoryName: payload.categoryName.trim(), sort: payload.sort };
      if (payload.id) {
        await updateScaleCategory(payload.id, body);
        ElMessage.success("分类更新成功");
      } else {
        await addScaleCategory(body);
        ElMessage.success("分类新增成功");
      }
      categoryEditVisible.value = false;
      await loadCategories();
    } catch {
      ElMessage.error("保存失败，请稍后重试");
    } finally {
      categorySubmitting.value = false;
    }
  }

  async function handleMoveSort(row: any, dir: -1 | 1) {
    const list = [...categoryList.value].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id);
    const idx = list.findIndex((c) => c.id === row.id);
    if (idx < 0) return;
    const target = list[idx + dir];
    if (!target) return;

    const tmp = list[idx].sort ?? list[idx].id;
    list[idx].sort = target.sort ?? target.id;
    target.sort = tmp;
    list.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id);
    const order = list.map((c) => c.id);
    categoryList.value = [...list];

    try {
      await Promise.all(order.map((id, i) => updateScaleCategorySort(id, i + 1)));
      ElMessage.success("排序已更新");
    } catch {
      ElMessage.error("排序保存失败，请稍后重试");
      await loadCategories();
    }
  }

  async function handleCategoryDelete(row: any) {
    try {
      await ElMessageBox.confirm(
        `确定删除分类「${row.categoryName}」吗？该分类下存在量表时将禁止删除。`,
        "删除确认",
        { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
      );
    } catch {
      return;
    }
    try {
      await deleteScaleCategory(row.id);
      ElMessage.success("分类删除成功");
      await loadCategories();
      await loadData();
    } catch {
      ElMessage.error("删除失败，可能该分类下仍有量表，请先删除或迁移其中的量表");
    }
  }

  /* ==================== 初始化 ==================== */

  onMounted(() => {
    loadData();
    loadCategories();
  });

  return {
    // 工具
    categoryStyle,
    formatTime,
    // 列表
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    searchForm,
    categoryList,
    enabledCount,
    categoryCount,
    loadData,
    handleSearch,
    handleReset,
    // 详情 / 状态
    goDetail,
    handleStatusToggle,
    // 新增 / 编辑量表
    scaleDialogVisible,
    scaleSubmitting,
    scaleDialogTitle,
    scaleTarget,
    handleAdd,
    handleEdit,
    handleScaleSubmit,
    // 删除 / 恢复
    handleDelete,
    handleRestore,
    // 分类管理
    categoryDialogVisible,
    categoryLoading,
    categorySubmitting,
    openCategoryDialog,
    categoryEditVisible,
    categoryEditTitle,
    categoryEditTarget,
    openCategoryEdit,
    handleCategorySubmit,
    handleMoveSort,
    handleCategoryDelete,
    // 常量
    PAGINATION_LAYOUT,
  };
}

export type UseScaleList = ReturnType<typeof useScaleList>;
