import { computed, onMounted, reactive, ref, shallowRef } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import type { UploadUserFile } from "element-plus";
import { pageLive2dModels, uploadLive2dModel } from "@/admin/api/live2d/config";
import type { Live2dConfigAdminVO } from "@/admin/api/live2d/config";

/** 上传弹窗表单模型（modelName 必填，其余可选） */
export interface UploadForm {
  modelName: string;
  /** 模型别名（中文展示名称，可选；留空则展示英文模型名称） */
  aliasName: string;
  /** 表情清单文件名（可选，缺省按 *.exp3.json 模糊匹配） */
  expressionFileName: string;
  /** 动作清单文件名（可选，缺省按 *.motion3.json 模糊匹配） */
  motionFileName: string;
  /** 语义提示词（可选，留空则上传后编辑补录） */
  semanticPrompt: string;
  /** 排序值 */
  sort: number;
  /** 备注 */
  remark: string;
}

/** 编辑弹窗表单模型（详情页编辑弹窗复用；启用态走详情页按钮、语义提示词走详情页内联编辑，均不入弹窗） */
export interface EditForm {
  id: number | string | null;
  modelName: string;
  aliasName: string;
  coverImagePath: string;
  sort: number;
  remark: string;
}

/**
 * Live2D 模型配置管理列表页全部业务逻辑（单一职责）：
 * - 列表加载 / 搜索 / 重置 / 分页 / 统计
 * - 上传 zip 模型包（暂存文件 → 提交 multipart）
 * - 跳转详情页（编辑 / 启用互斥切换 / 删除统一在详情页完成）
 */
export function useLive2dList() {
  const router = useRouter();

  /* ==================== 页面状态 ==================== */

  const loading = ref(false);
  const tableData = shallowRef<Live2dConfigAdminVO[]>([]);
  const total = ref(0);
  const currentPage = ref(1);
  const pageSize = DEFAULT_PAGE_SIZE;

  const searchForm = reactive<{ modelName: string; enabled?: number }>({
    modelName: "",
    enabled: undefined,
  });

  const enabledCount = computed(() => tableData.value.filter((r) => r.enabled === 1).length);

  /* ==================== 上传弹窗状态 ==================== */

  const uploadVisible = ref(false);
  const uploadSubmitting = ref(false);
  const uploadFileList = shallowRef<UploadUserFile[]>([]);

  const emptyUploadForm: UploadForm = {
    modelName: "",
    aliasName: "",
    expressionFileName: "",
    motionFileName: "",
    semanticPrompt: "",
    sort: 0,
    remark: "",
  };
  const uploadForm = reactive<UploadForm>({ ...emptyUploadForm });

  /* ==================== 数据加载 ==================== */

  function buildParams() {
    return {
      pageNum: currentPage.value,
      pageSize,
      modelName: searchForm.modelName.trim() || undefined,
      enabled: searchForm.enabled,
    };
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await pageLive2dModels(buildParams());
      tableData.value = res.data.data?.records ?? [];
      total.value = res.data.data?.total ?? 0;
    } catch (e) {
      ElMessage.error((e as Error)?.message || "模型列表加载失败，请稍后重试");
    } finally {
      loading.value = false;
    }
  }

  /* ==================== 搜索 / 重置 ==================== */

  function handleSearch() {
    currentPage.value = 1;
    loadData();
  }

  function handleReset() {
    searchForm.modelName = "";
    searchForm.enabled = undefined;
    currentPage.value = 1;
    loadData();
  }

  /* ==================== 上传 ==================== */

  function openUploadDialog() {
    uploadVisible.value = true;
  }

  function handleUploadChange(_file: unknown, list?: UploadUserFile[]) {
    uploadFileList.value = list ?? [];
  }

  function validateUpload(): string | null {
    const modelName = uploadForm.modelName.trim();
    if (!modelName) return "请填写模型名称";
    if (!/^[a-zA-Z0-9_-]+$/.test(modelName)) {
      return "模型名称仅支持字母 / 数字 / 下划线 / 中划线";
    }
    const raw = uploadFileList.value[0]?.raw;
    if (!raw) return "请选择模型包文件";
    const lower = (raw.name || "").toLowerCase();
    if (!lower.endsWith(".zip")) return "模型包仅支持 zip 格式";
    return null;
  }

  async function handleUploadSubmit() {
    const errMsg = validateUpload();
    if (errMsg) {
      ElMessage.warning(errMsg);
      return;
    }
    const raw = uploadFileList.value[0].raw as File;

    uploadSubmitting.value = true;
    try {
      const formData = new FormData();
      formData.append("file", raw);
      formData.append("modelName", uploadForm.modelName.trim());
      if (uploadForm.aliasName.trim()) {
        formData.append("aliasName", uploadForm.aliasName.trim());
      }
      if (uploadForm.expressionFileName.trim()) {
        formData.append("expressionFileName", uploadForm.expressionFileName.trim());
      }
      if (uploadForm.motionFileName.trim()) {
        formData.append("motionFileName", uploadForm.motionFileName.trim());
      }
      if (uploadForm.semanticPrompt.trim()) {
        formData.append("semanticPrompt", uploadForm.semanticPrompt.trim());
      }
      formData.append("sort", String(uploadForm.sort ?? 0));
      if (uploadForm.remark.trim()) {
        formData.append("remark", uploadForm.remark.trim());
      }

      const res = await uploadLive2dModel(formData);
      const id = res.data.data;
      ElMessage.success(`模型上传成功${id ? `（id：${id}）` : ""}，可到列表手动启用`);
      uploadVisible.value = false;
      Object.assign(uploadForm, emptyUploadForm);
      uploadFileList.value = [];
      // 回到第一页便于查看新记录
      currentPage.value = 1;
      await loadData();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "模型上传失败，请稍后重试");
    } finally {
      uploadSubmitting.value = false;
    }
  }

  /* ==================== 跳转详情 ==================== */

  function handleViewDetail(row: Live2dConfigAdminVO) {
    if (row.id == null) {
      ElMessage.warning("该模型缺少ID，无法查看详情");
      return;
    }
    router.push(`/admin/live2d/${row.id}`);
  }

  /* ==================== 初始化 ==================== */

  onMounted(() => {
    loadData();
  });

  return {
    // 列表相关
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    enabledCount,
    searchForm,
    handleSearch,
    handleReset,
    loadData,
    // 上传相关
    uploadVisible,
    uploadSubmitting,
    uploadFileList,
    uploadForm,
    openUploadDialog,
    handleUploadChange,
    handleUploadSubmit,
    // 详情
    handleViewDetail,
    // 常量
    PAGINATION_LAYOUT,
  };
}

export type UseLive2dList = ReturnType<typeof useLive2dList>;
