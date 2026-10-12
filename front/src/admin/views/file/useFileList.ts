import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { UploadUserFile } from "element-plus";
import { DEFAULT_PAGE_SIZE } from "@/shared/api/config";
import dayjs, { FORMAT_DATE } from "@/shared/utils/dayjs";
import {
  uploadFile,
  getFilePage,
  getFileDetail,
  deleteFile as deleteFileApi,
  updateFile,
} from "@/admin/api/file/file";
import {
  getCategoryTree,
  addCategory,
  updateCategory,
  deleteCategory,
} from "@/admin/api/file/category";
import { getAuthHeaders } from "@/shared/api/auth";
import {
  STATUS_MAP,
  KNOWLEDGE_TYPE_MAP,
  COMMON_FILE_SUFFIXES,
  IMAGE_SUFFIXES,
  TEXT_SUFFIXES,
  categoryTreeProps,
  fileMeta,
  categoryTagType,
  statusMeta,
} from "@/admin/constants/file";

/* ==================== 类型定义 ==================== */

export interface CategoryNode {
  id: number;
  parentId: number;
  categoryName: string;
  defaultType?: number;
  fileCount?: number;
  children?: CategoryNode[];
}

export interface FileRow {
  id: number;
  personId?: number;
  creatorName?: string;
  categoryId?: number;
  categoryName?: string;
  originalName: string;
  fileUrl?: string;
  fileSuffix?: string;
  fileSize?: number;
  status?: number;
  failReason?: string;
  vectorStatus?: number;
  knowledgeType?: number;
  source?: string;
  createdTime?: string;
  updatedTime?: string;
}

/** el-upload 自定义上传回调参数（保持页面原有签名） */
export interface FileUploadRequestOptions {
  file: File;
  onSuccess: (resp: any) => void;
  onError: (err: any) => void;
}

/* ==================== 格式化工具 ==================== */

/** 大小格式化（B / KB / MB） */
export function formatSize(bytes?: number): string {
  if (bytes === undefined || bytes === null) return "-";
  if (bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const idx = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const val = bytes / Math.pow(1024, idx);
  return `${val.toFixed(idx === 0 || val >= 100 ? 0 : 1)} ${units[idx]}`;
}

/** 时间格式化 */
function formatTime(time?: string): string {
  if (!time) return "-";
  return dayjs(time).format(FORMAT_DATE);
}

/**
 * 文件管理页全部业务逻辑（单一职责拆分）：
 * - 文件列表 / 搜索 / 分页 / 统计
 * - 分类树的加载与分类增删改
 * - 上传 / 编辑 / 删除 / 下载 / 预览
 *
 * 弹窗类子组件通过 props 接收状态，通过事件回传操作与表单数据。
 */
export function useFileList() {
  const router = useRouter();

  // ==================== 列表状态 ====================
  const page = ref(1);
  const pageSize = ref(DEFAULT_PAGE_SIZE);
  const total = ref(0);
  const tableLoading = ref(false);
  const fileList = ref<FileRow[]>([]);

  // 搜索
  const searchForm = reactive<{
    fileName: string;
    fileSuffix?: string;
    status?: number;
    vectorStatus?: number;
  }>({
    fileName: "",
    fileSuffix: undefined,
    status: undefined,
    vectorStatus: undefined,
  });
  const searchCategoryId = ref<number | undefined>(undefined);

  // 分类
  const categoryLoading = ref(false);
  const categoryTree = ref<CategoryNode[]>([]);

  // 统计
  const stats = reactive({ totalFiles: 0, totalSize: 0, vectorizedFiles: 0 });

  // 上传
  const uploadVisible = ref(false);
  const uploadCategoryId = ref<number | undefined>(undefined);
  const uploadFileList = ref<UploadUserFile[]>([]);

  // 编辑
  const editVisible = ref(false);
  const editSaving = ref(false);
  const editForm = reactive<{ originalName: string; categoryId?: number }>({
    originalName: "",
    categoryId: undefined,
  });
  const editingFileId = ref<number | undefined>(undefined);

  // 分类管理
  const categoryManageVisible = ref(false);
  const categoryEditVisible = ref(false);
  const categorySaving = ref(false);
  const editingCategoryId = ref<number | undefined>(undefined);
  const categoryParentId = ref<number | undefined>(undefined);
  const categoryForm = reactive({ name: "" });

  // 预览
  const previewVisible = ref(false);
  const previewLoading = ref(false);
  const previewRow = ref<FileRow | null>(null);
  const previewImageUrl = ref("");
  const previewText = ref("");

  // ==================== 计算属性 ====================
  const isImagePreview = computed(() => {
    const suffix = (previewRow.value?.fileSuffix || "").toLowerCase();
    return IMAGE_SUFFIXES.includes(suffix);
  });

  const isTextPreview = computed(() => {
    const suffix = (previewRow.value?.fileSuffix || "").toLowerCase();
    return TEXT_SUFFIXES.includes(suffix);
  });

  // ==================== 列表加载 ====================
  async function loadFiles() {
    tableLoading.value = true;
    try {
      const params: Record<string, any> = {
        pageNum: page.value,
        pageSize: pageSize.value,
      };
      if (searchForm.fileName) params.fileName = searchForm.fileName;
      if (searchCategoryId.value !== undefined) params.categoryId = searchCategoryId.value;
      if (searchForm.fileSuffix) params.fileSuffix = searchForm.fileSuffix;
      if (searchForm.status !== undefined) params.status = searchForm.status;
      if (searchForm.vectorStatus !== undefined) params.vectorStatus = searchForm.vectorStatus;

      const res = await getFilePage(params);
      const data = res.data?.data;
      fileList.value = data?.records ?? [];
      total.value = data?.total ?? 0;
    } catch {
      ElMessage.error("文件列表加载失败");
    } finally {
      tableLoading.value = false;
    }
  }

  /** 加载统计信息（全量数据聚合出总数/总大小/已向量化数） */
  async function loadStats() {
    try {
      const res = await getFilePage({ pageNum: 1, pageSize: 10000 });
      const records: FileRow[] = res.data?.data?.records ?? [];
      let totalSize = 0;
      let vectorized = 0;
      records.forEach((item) => {
        totalSize += item.fileSize ?? 0;
        if (item.vectorStatus === 1) vectorized += 1;
      });
      stats.totalFiles = records.length;
      stats.totalSize = totalSize;
      stats.vectorizedFiles = vectorized;
    } catch {
      // 统计失败不阻塞页面
    }
  }

  async function loadCategoryTree() {
    categoryLoading.value = true;
    try {
      const res = await getCategoryTree();
      categoryTree.value = (res.data?.data ?? []) as CategoryNode[];
    } catch {
      ElMessage.error("分类加载失败");
    } finally {
      categoryLoading.value = false;
    }
  }

  // ==================== 搜索 ====================
  function handleSearch() {
    page.value = 1;
    loadFiles();
  }

  function handleReset() {
    searchForm.fileName = "";
    searchForm.fileSuffix = undefined;
    searchForm.status = undefined;
    searchForm.vectorStatus = undefined;
    searchCategoryId.value = undefined;
    page.value = 1;
    loadFiles();
  }

  function handleSelectCategory(categoryId: number | undefined) {
    searchCategoryId.value = categoryId;
    page.value = 1;
    loadFiles();
  }

  function handleSizeChange() {
    page.value = 1;
    loadFiles();
  }

  // ==================== 上传 ====================
  function openUploadDialog() {
    uploadCategoryId.value = undefined;
    uploadFileList.value = [];
    uploadVisible.value = true;
  }

  function beforeUpload(file: File) {
    if (file.size > 100 * 1024 * 1024) {
      ElMessage.error(`「${file.name}」超过 100MB 限制`);
      return false;
    }
    return true;
  }

  async function handleUploadRequest(options: FileUploadRequestOptions) {
    const { file, onSuccess, onError } = options;
    const formData = new FormData();
    formData.append("file", file);
    if (uploadCategoryId.value !== undefined) {
      formData.append("categoryId", String(uploadCategoryId.value));
    }
    try {
      await uploadFile(formData);
      onSuccess({});
      ElMessage.success(`「${file.name}」上传成功`);
      loadFiles();
      loadStats();
      loadCategoryTree();
    } catch (err) {
      onError(err);
      ElMessage.error(`「${file.name}」上传失败`);
    }
  }

  // ==================== 编辑 ====================
  function openEditDialog(row: any) {
    editingFileId.value = row.id;
    editForm.originalName = row.originalName;
    editForm.categoryId = row.categoryId;
    editVisible.value = true;
  }

  async function handleSaveEdit() {
    if (editingFileId.value === undefined) return;
    if (!editForm.originalName.trim()) {
      ElMessage.warning("请输入文件名");
      return;
    }
    editSaving.value = true;
    try {
      const payload: Record<string, any> = { id: editingFileId.value };
      if (editForm.originalName.trim()) payload.fileName = editForm.originalName.trim();
      if (editForm.categoryId !== undefined) payload.categoryId = editForm.categoryId;
      await updateFile(String(editingFileId.value), payload);
      ElMessage.success("文件信息已更新");
      editVisible.value = false;
      loadFiles();
      loadStats();
    } catch {
      ElMessage.error("更新失败");
    } finally {
      editSaving.value = false;
    }
  }

  // ==================== 删除 ====================
  async function handleDelete(row: any) {
    try {
      await ElMessageBox.confirm(
        `确定删除「${row.originalName}」吗？删除后不可恢复。`,
        "删除文件",
        { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
      );
    } catch {
      return;
    }
    try {
      await deleteFileApi(String(row.id));
      ElMessage.success("文件已删除");
      loadFiles();
      loadStats();
    } catch {
      ElMessage.error("删除失败");
    }
  }

  // ==================== 分类管理 ====================
  function openCategoryManage() {
    categoryManageVisible.value = true;
  }

  function openCategoryEdit(category?: CategoryNode) {
    categoryEditVisible.value = true;
    if (category) {
      editingCategoryId.value = category.id;
      categoryForm.name = category.categoryName;
      categoryParentId.value = undefined;
    } else {
      editingCategoryId.value = undefined;
      categoryForm.name = "";
      categoryParentId.value = undefined;
    }
  }

  async function handleSaveCategory() {
    const name = categoryForm.name.trim();
    if (!name) {
      ElMessage.warning("请输入分类名称");
      return;
    }
    categorySaving.value = true;
    try {
      if (editingCategoryId.value !== undefined) {
        await updateCategory({ id: editingCategoryId.value, categoryName: name });
      } else {
        const payload: Record<string, any> = { categoryName: name };
        if (categoryParentId.value !== undefined) payload.parentId = categoryParentId.value;
        await addCategory(payload);
      }
      ElMessage.success("分类保存成功");
      categoryEditVisible.value = false;
      loadCategoryTree();
      loadStats();
    } catch {
      ElMessage.error("分类保存失败");
    } finally {
      categorySaving.value = false;
    }
  }

  async function handleDeleteCategory(category: CategoryNode) {
    const hasFiles = typeof category.fileCount === "number" && category.fileCount > 0;
    try {
      await ElMessageBox.confirm(
        hasFiles
          ? `分类「${category.categoryName}」下存在 ${category.fileCount} 个文件，删除后这些文件将变为未分类，确定删除吗？`
          : `确定删除分类「${category.categoryName}」吗？`,
        "删除分类",
        { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
      );
    } catch {
      return;
    }
    try {
      await deleteCategory(String(category.id));
      ElMessage.success("分类已删除");
      loadCategoryTree();
      loadStats();
      if (searchCategoryId.value === category.id) {
        searchCategoryId.value = undefined;
        page.value = 1;
        loadFiles();
      }
    } catch {
      ElMessage.error("删除分类失败");
    }
  }

  // ==================== 下载 / 预览 / 向量 ====================
  /** 需要鉴权的接口通过 fetch + 请求头携带 admin token（后端从请求头读取，不支持 query token） */
  async function handleDownload(row: any) {
    if (!row) return;
    try {
      const response = await fetch(`/admin/file/${row.id}/download`, {
        headers: getAuthHeaders("admin"),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = objectUrl;
      link.download = row.originalName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(objectUrl);
    } catch {
      ElMessage.error("下载失败，请稍后重试");
    }
  }

  async function handlePreview(row: any) {
    previewRow.value = row;
    previewVisible.value = true;
    previewLoading.value = true;
    previewText.value = "";
    previewImageUrl.value = "";

    try {
      const res = await getFileDetail(String(row.id));
      const detail: FileRow | null = res.data?.data ?? null;
      const fileUrl = detail?.fileUrl || row.fileUrl;

      if (isImagePreview.value) {
        if (!fileUrl) return;
        // 文件访问地址为 static-resources 静态资源（后端已放行），无需鉴权头
        previewImageUrl.value = fileUrl;
      } else if (isTextPreview.value) {
        if (!fileUrl) return;
        try {
          const response = await fetch(fileUrl, { headers: getAuthHeaders("admin") });
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          previewText.value = await response.text();
        } catch {
          previewText.value = "";
        }
      }
    } catch {
      // 详情获取失败时按类型展示占位
    } finally {
      previewLoading.value = false;
    }
  }

  function clearPreview() {
    previewRow.value = null;
    previewText.value = "";
    previewImageUrl.value = "";
  }

  // ==================== 详情 ====================
  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailRow = ref<FileRow | null>(null);
  const detailData = ref<FileRow | null>(null);

  /** 行点击：忽略选择列 */
  function handleRowClick(row: FileRow, column?: { type?: string } | null) {
    if (column?.type === "selection") return;
    openDetailDialog(row);
  }

  /** 打开详情弹窗：行数据即时渲染，getFileDetail 返回后补全（失败仅用行数据兜底） */
  async function openDetailDialog(row: FileRow) {
    detailRow.value = row;
    detailData.value = null;
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      const res = await getFileDetail(String(row.id));
      // 旧响应丢弃守卫：快速连点其他行时避免旧详情覆盖
      if (detailRow.value?.id !== row.id) return;
      const detail: FileRow | null = res.data?.data ?? null;
      detailData.value = detail;
      // 详情接口返回的 fileUrl 兜底刷回行数据，供预览 / 下载使用
      if (detail?.fileUrl) row.fileUrl = detail.fileUrl;
    } catch {
      // 详情获取失败时仅用行数据兜底
    } finally {
      if (detailRow.value?.id === row.id) detailLoading.value = false;
    }
  }

  function goVector(row: any) {
    router.push(`/admin/file/vector/${row.id}`);
  }

  // ==================== 初始化 ====================
  onMounted(() => {
    loadCategoryTree();
    loadStats();
    loadFiles();
  });

  return {
    // 工具
    fileMeta,
    formatSize,
    formatTime,
    categoryTagType,
    statusMeta,
    // 列表
    page,
    pageSize,
    total,
    tableLoading,
    fileList,
    loadFiles,
    handleSizeChange,
    // 搜索
    searchForm,
    searchCategoryId,
    handleSearch,
    handleReset,
    handleSelectCategory,
    // 分类
    categoryLoading,
    categoryTree,
    categoryTreeProps,
    loadCategoryTree,
    openCategoryManage,
    openCategoryEdit,
    handleDeleteCategory,
    categoryManageVisible,
    categoryEditVisible,
    categorySaving,
    editingCategoryId,
    categoryParentId,
    categoryForm,
    handleSaveCategory,
    // 统计
    stats,
    // 上传
    uploadVisible,
    uploadCategoryId,
    uploadFileList,
    openUploadDialog,
    beforeUpload,
    handleUploadRequest,
    // 编辑
    editVisible,
    editSaving,
    editForm,
    openEditDialog,
    handleSaveEdit,
    // 预览 / 下载 / 删除 / 跳转
    previewVisible,
    previewLoading,
    previewRow,
    previewImageUrl,
    previewText,
    isImagePreview,
    isTextPreview,
    handlePreview,
    clearPreview,
    handleDownload,
    handleDelete,
    goVector,
    // 详情
    detailVisible,
    detailLoading,
    detailRow,
    detailData,
    handleRowClick,
    openDetailDialog,
    // 常量
    STATUS_MAP,
    KNOWLEDGE_TYPE_MAP,
    COMMON_FILE_SUFFIXES,
  };
}

export type UseFileList = ReturnType<typeof useFileList>;
