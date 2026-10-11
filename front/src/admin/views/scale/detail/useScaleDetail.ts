import { computed, onMounted, ref, shallowRef, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { getScaleDetail, updateScale } from "@/admin/api/scale/scale";
import { getScaleDimensionList } from "@/admin/api/scale/dimension";
import { getScaleQuestionList } from "@/admin/api/scale/question";
import { getScaleCategoryPage } from "@/admin/api/scale/category";
import type {
  AdminScaleCategoryVO,
  AdminScaleDetailVO,
  AdminScaleDimensionVO,
  AdminScaleQuestionVO,
  AdminScaleVersionVO,
} from "@/admin/api/scale/types";

/** 量表信息编辑弹窗提交的表单数据 */
export interface ScaleInfoSubmitPayload {
  scaleName: string;
  scaleCategoryId: number | null;
  status: number;
  allowRepeat: number;
  coolMinutes: number;
  timeLimit: number;
  anonymous: number;
}

/** 量表详情页页面级共享逻辑：路由参数、基本信息、版本选择、维度/题目共享列表、量表信息弹窗。 */
export function useScaleDetail() {
  /* ==================== 路由参数 ==================== */
  const route = useRoute();
  const scaleId = Number(route.params.scaleId);

  /* ==================== 页面状态 ==================== */
  const pageLoading = ref(true);
  const pageError = ref(false);
  const detail = shallowRef<AdminScaleDetailVO | null>(null);
  const activeTab = ref("version");

  /* ==================== 量表与版本 ==================== */
  const scaleName = computed(() => detail.value?.scaleName || "量表详情");
  const versions = computed<AdminScaleVersionVO[]>(() => detail.value?.versions ?? []);
  const currentVersion = computed(() => versions.value.find((v) => v.isCurrent) ?? versions.value[0] ?? null);
  const scaleDescription = computed(
    () => currentVersion.value?.description || detail.value?.scaleName || "",
  );

  const selectedVersionId = ref<number | null>(null);
  const selectedVersion = computed(() => versions.value.find((v) => v.id === selectedVersionId.value) ?? null);

  /* ==================== 维度 / 题目（题目、常模、结果规则、分支规则、选项模板 Tab 共享） ==================== */
  const dimensionList = ref<AdminScaleDimensionVO[]>([]);
  const dimensionLoading = ref(false);
  const questionList = ref<AdminScaleQuestionVO[]>([]);
  const questionLoading = ref(false);

  const categoryList = ref<AdminScaleCategoryVO[]>([]);

  /* ==================== 工具函数 ==================== */
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

  /* ==================== 数据加载 ==================== */
  async function loadDetail() {
    pageLoading.value = true;
    pageError.value = false;
    try {
      const res = await getScaleDetail(scaleId);
      const data = res.data.data ?? null;
      detail.value = data;
      if (!data) {
        pageError.value = true;
        return;
      }
      const fallback = data.currentVersionId ?? data.versions?.[0]?.id ?? null;
      if (
        selectedVersionId.value == null ||
        !data.versions?.some((v: AdminScaleVersionVO) => v.id === selectedVersionId.value)
      ) {
        selectedVersionId.value = fallback;
      }
    } catch (e) {
      pageError.value = true;
      ElMessage.error((e as Error)?.message || "量表详情加载失败");
    } finally {
      pageLoading.value = false;
    }
  }

  async function loadCategories() {
    try {
      const res = await getScaleCategoryPage({ pageNum: 1, pageSize: 1000 });
      categoryList.value = (res.data.data as { records?: AdminScaleCategoryVO[] })?.records ?? [];
    } catch {
      categoryList.value = [];
    }
  }

  async function loadDimensions(versionId: number) {
    dimensionLoading.value = true;
    try {
      const res = await getScaleDimensionList(versionId);
      dimensionList.value = res.data.data ?? [];
    } catch (e) {
      dimensionList.value = [];
      ElMessage.error((e as Error)?.message || "维度列表加载失败");
    } finally {
      dimensionLoading.value = false;
    }
  }

  async function loadQuestions(versionId: number) {
    questionLoading.value = true;
    try {
      const res = await getScaleQuestionList(versionId);
      questionList.value = res.data.data ?? [];
    } catch (e) {
      questionList.value = [];
      ElMessage.error((e as Error)?.message || "题目列表加载失败");
    } finally {
      questionLoading.value = false;
    }
  }

  watch(selectedVersionId, (id) => {
    if (id == null) {
      dimensionList.value = [];
      questionList.value = [];
      return;
    }
    loadDimensions(id);
    loadQuestions(id);
  });

  async function handleReload() {
    await loadDetail();
  }

  /* ==================== 跨 Tab 刷新（子组件事件回传） ==================== */
  /** 版本管理发生增删改 / 发布后：刷新量表详情（版本列表与统计） */
  async function handleVersionsChanged() {
    await loadDetail();
  }

  /** 维度变更后：维度列表、题目列表（所属维度名称）与详情统计一并刷新 */
  async function handleDimensionsChanged() {
    const vid = selectedVersionId.value;
    if (vid != null) {
      await Promise.all([loadDimensions(vid), loadQuestions(vid)]);
    }
    await loadDetail();
  }

  /** 题目变更后：题目列表与详情统计刷新 */
  async function handleQuestionsChanged() {
    const vid = selectedVersionId.value;
    if (vid != null) await loadQuestions(vid);
    await loadDetail();
  }

  /** 复制题目后：仅当目标版本为当前编辑版本时才刷新题目列表 */
  async function handleQuestionCopied(targetVersionId: number) {
    if (targetVersionId === selectedVersionId.value) {
      await loadQuestions(targetVersionId);
    }
    await loadDetail();
  }

  /** 维度排序失败后的回滚刷新 */
  function reloadDimensions() {
    const vid = selectedVersionId.value;
    if (vid != null) loadDimensions(vid);
  }

  /** 选项模板应用到题目后：刷新题目列表（选项摘要变化） */
  function reloadQuestions() {
    const vid = selectedVersionId.value;
    if (vid != null) loadQuestions(vid);
  }

  /* ==================== 交互：量表信息编辑 ==================== */
  const scaleEditVisible = ref(false);
  const scaleEditSubmitting = ref(false);

  function openScaleEdit() {
    scaleEditVisible.value = true;
  }

  async function handleScaleSubmit(payload: ScaleInfoSubmitPayload) {
    scaleEditSubmitting.value = true;
    try {
      await updateScale(scaleId, {
        scaleName: payload.scaleName.trim(),
        scaleCategoryId: payload.scaleCategoryId,
        status: payload.status,
        allowRepeat: payload.allowRepeat,
        coolMinutes: payload.allowRepeat === 1 ? payload.coolMinutes : 0,
        timeLimit: payload.timeLimit || null,
        anonymous: payload.anonymous,
      });
      ElMessage.success("量表信息已更新");
      scaleEditVisible.value = false;
      await loadDetail();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
    } finally {
      scaleEditSubmitting.value = false;
    }
  }

  /* ==================== 初始化 ==================== */
  onMounted(() => {
    loadDetail();
    loadCategories();
  });

  return {
    // 路由
    scaleId,
    // 页面状态
    pageLoading,
    pageError,
    detail,
    activeTab,
    // 量表与版本
    scaleName,
    versions,
    currentVersion,
    scaleDescription,
    selectedVersionId,
    selectedVersion,
    // 共享列表
    dimensionList,
    dimensionLoading,
    questionList,
    questionLoading,
    categoryList,
    // 工具
    formatTime,
    // 加载
    loadDetail,
    loadDimensions,
    loadQuestions,
    loadCategories,
    handleReload,
    // 跨 Tab 刷新
    handleVersionsChanged,
    handleDimensionsChanged,
    handleQuestionsChanged,
    handleQuestionCopied,
    reloadDimensions,
    reloadQuestions,
    // 量表信息弹窗
    scaleEditVisible,
    scaleEditSubmitting,
    openScaleEdit,
    handleScaleSubmit,
  };
}

export type UseScaleDetail = ReturnType<typeof useScaleDetail>;
