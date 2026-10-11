import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import type { Component } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Clock, Loading, SuccessFilled, Warning } from "@element-plus/icons-vue";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import dayjs from "@/shared/utils/dayjs";
import { getFileDetail } from "@/admin/api/file/file";
import {
  deleteFileVector,
  getFileVector,
  interruptFileVector,
  loadFileVector,
} from "@/admin/api/file/vector";
import type { FileVectorVO } from "@/admin/api/file/vector";

/* ==================== 类型定义 ==================== */

export interface FileInfo {
  id: number;
  originalName?: string;
  fileSuffix?: string;
  fileSize?: number;
  status?: number;
  vectorStatus?: number;
  knowledgeType?: number;
  failReason?: string;
}

/** 文件状态标签元数据 */
export interface VectorStatusMeta {
  label: string;
  tagType: "primary" | "success" | "info" | "warning" | "danger";
}

/* ==================== 格式化工具 ==================== */

function padIdx(idx?: number): string {
  if (idx === undefined || idx === null) return "--";
  return String(idx).padStart(2, "0");
}

function formatSize(bytes?: number): string {
  if (!bytes || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const val = bytes / Math.pow(1024, i);
  return `${val >= 100 || i === 0 ? Math.round(val) : val.toFixed(1)} ${units[i]}`;
}

function formatTime(time?: string): string {
  if (!time) return "--";
  const d = dayjs(time);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(time);
}

function formatElapsed(sec: number): string {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

/**
 * 文件向量管理页全部业务逻辑（单一职责拆分）：
 * - 文件信息加载 / 向量片段分页查询
 * - 开始向量化 / 中断 / 删除全部向量
 * - 解析进度轮询与耗时统计
 */
export function useFileVector() {
  /* ==================== 基础状态 ==================== */
  const route = useRoute();
  const fileId = route.params.fileId as string;

  const fileInfo = ref<FileInfo | null>(null);
  const vectorList = ref<FileVectorVO[]>([]);
  const total = ref(0);
  const page = ref(1);
  const pageSize = ref(DEFAULT_PAGE_SIZE);

  const listLoading = ref(false);
  const submitting = ref(false);
  const deleting = ref(false);

  const detailVisible = ref(false);
  const activeItem = ref<FileVectorVO | null>(null);

  const elapsed = ref(0);
  let pollTimer: number | null = null;
  let tickTimer: number | null = null;
  let loadStartAt = 0;

  /* ==================== 派生状态 ==================== */
  const fileName = computed(() => fileInfo.value?.originalName || `文件 #${fileId}`);
  const suffixText = computed(() => {
    const s = fileInfo.value?.fileSuffix;
    return s ? s.toUpperCase() : "文件";
  });
  const fileSizeText = computed(() => formatSize(fileInfo.value?.fileSize));
  const isParsing = computed(() => fileInfo.value?.status === 1);
  const canLoad = computed(() => {
    const s = fileInfo.value?.status;
    return s === 0 || s === 2;
  });
  const canDelete = computed(() => fileInfo.value?.status === 3);
  const loadLabel = computed(() => {
    const s = fileInfo.value?.status;
    if (s === 1) return "向量解析中…";
    if (s === 2) return "重新向量化";
    return "开始向量化";
  });
  const chunkCountL1 = computed(() => new Set(vectorList.value.map((v) => v.chunkLevel1Idx)).size);
  const emptyText = computed(() => {
    const s = fileInfo.value?.status;
    if (s === 2) return `向量解析失败：${fileInfo.value?.failReason || "未知原因"}`;
    if (s === 0) return "该文件尚未向量化，点击「开始向量化」生成向量片段";
    return "暂无向量片段数据";
  });
  const detailTitle = computed(() => {
    const it = activeItem.value;
    return it
      ? `片段详情 · L1 ${padIdx(it.chunkLevel1Idx)} / L2 ${padIdx(it.chunkLevel2Idx)}`
      : "片段详情";
  });

  /** 文件状态：0-待解析，1-解析中，2-解析失败，3-解析完成 */
  const vectorStatus = computed<VectorStatusMeta>(() => {
    const s = fileInfo.value?.status;
    switch (s) {
      case 0:
        return { label: "待解析", tagType: "warning" };
      case 1:
        return { label: "解析中", tagType: "primary" };
      case 2:
        return { label: "解析失败", tagType: "danger" };
      case 3:
        return { label: "解析完成", tagType: "success" };
      default:
        return { label: "未知", tagType: "info" };
    }
  });

  const statusTone = computed(() => {
    const s = fileInfo.value?.status;
    switch (s) {
      case 3:
        return "tone-success";
      case 1:
        return "tone-primary";
      case 2:
        return "tone-danger";
      case 0:
        return "tone-warning";
      default:
        return "tone-default";
    }
  });

  const statusIcon = computed<Component>(() => {
    const s = fileInfo.value?.status;
    switch (s) {
      case 3:
        return SuccessFilled;
      case 1:
        return Loading;
      case 2:
        return Warning;
      case 0:
        return Clock;
      default:
        return Clock;
    }
  });

  /* ==================== 数据加载 ==================== */
  async function refreshFileInfo() {
    try {
      const res = await getFileDetail(fileId);
      fileInfo.value = res.data?.data ?? null;
    } catch {
      // 静默失败，保留上次状态
    }
  }

  async function loadVectors() {
    listLoading.value = true;
    try {
      const res = await getFileVector(fileId, { pageNum: page.value, pageSize: pageSize.value });
      const data = res.data?.data;
      if (data) {
        vectorList.value = data.records ?? [];
        total.value = data.total ?? 0;
      } else {
        vectorList.value = [];
        total.value = 0;
      }
    } catch (e) {
      ElMessage.error((e as Error)?.message || "向量列表加载失败");
    } finally {
      listLoading.value = false;
    }
  }

  /* ==================== 轮询进度 ==================== */
  function stopPolling() {
    if (pollTimer !== null) {
      window.clearInterval(pollTimer);
      pollTimer = null;
    }
    if (tickTimer !== null) {
      window.clearInterval(tickTimer);
      tickTimer = null;
    }
    submitting.value = false;
  }

  function startPolling() {
    stopPolling();
    loadStartAt = Date.now();
    elapsed.value = 0;
    submitting.value = false;
    tickTimer = window.setInterval(() => {
      elapsed.value = Math.floor((Date.now() - loadStartAt) / 1000);
    }, 1000);
    pollTimer = window.setInterval(async () => {
      await refreshFileInfo();
      const s = fileInfo.value?.status;
      if (s === 3) {
        stopPolling();
        ElMessage.success("向量加载完成");
        loadVectors();
      } else if (s === 2) {
        stopPolling();
        ElMessage.error(`向量解析失败：${fileInfo.value?.failReason || "未知原因"}`);
        loadVectors();
      }
    }, 2000);
  }

  async function waitForStatusChange(maxAttempts = 12) {
    for (let i = 0; i < maxAttempts; i++) {
      await refreshFileInfo();
      const s = fileInfo.value?.status;
      if (s !== undefined && s !== 1) return;
      await sleep(1000);
    }
  }

  /* ==================== 操作 ==================== */
  async function handleLoad() {
    try {
      await ElMessageBox.confirm(
        `确定开始对「${fileName.value}」${loadLabel.value}吗？文件将被切分为向量片段，可能需要较长时间。`,
        "向量化确认",
        { type: "info", confirmButtonText: "开始", cancelButtonText: "取消" },
      );
    } catch {
      return;
    }
    submitting.value = true;
    try {
      await loadFileVector(fileId);
      ElMessage.success("向量加载任务已启动");
      await refreshFileInfo();
      if (isParsing.value) {
        startPolling();
      } else {
        loadVectors();
      }
    } catch (e) {
      submitting.value = false;
      ElMessage.error((e as Error)?.message || "启动向量加载失败");
    }
  }

  async function handleInterrupt() {
    try {
      await ElMessageBox.confirm("确定中断当前文件的向量解析吗？已生成的部分向量将被清理。", "中断解析", {
        type: "warning",
        confirmButtonText: "中断",
        cancelButtonText: "取消",
      });
    } catch {
      return;
    }
    try {
      await interruptFileVector(fileId);
      stopPolling();
      ElMessage.info("已发起中断请求，正在清理已生成的向量…");
      await waitForStatusChange();
      await loadVectors();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "中断失败，请稍后重试");
    }
  }

  async function handleDeleteAll() {
    try {
      await ElMessageBox.confirm(
        `确定删除「${fileName.value}」的全部向量片段吗？删除后需重新向量化才可恢复。`,
        "删除全部向量",
        {
          type: "warning",
          confirmButtonText: "删除",
          cancelButtonText: "取消",
          confirmButtonClass: "el-button--danger",
        },
      );
    } catch {
      return;
    }
    deleting.value = true;
    try {
      await deleteFileVector(fileId);
      ElMessage.success("向量已全部删除");
      page.value = 1;
      await refreshFileInfo();
      await loadVectors();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
    } finally {
      deleting.value = false;
    }
  }

  function openDetail(item: FileVectorVO) {
    activeItem.value = item;
    detailVisible.value = true;
  }

  function handlePageChange(next?: number) {
    if (typeof next === "number") page.value = next;
    loadVectors();
  }

  /* ==================== 初始化 ==================== */
  onMounted(async () => {
    await refreshFileInfo();
    if (isParsing.value) {
      startPolling();
    } else {
      loadVectors();
    }
  });

  onBeforeUnmount(() => {
    stopPolling();
  });

  return {
    // 基础
    fileName,
    suffixText,
    fileSizeText,
    vectorStatus,
    // 统计 / 工具栏
    total,
    chunkCountL1,
    statusTone,
    statusIcon,
    isParsing,
    canLoad,
    canDelete,
    loadLabel,
    submitting,
    deleting,
    elapsed,
    formatElapsed,
    handleLoad,
    handleInterrupt,
    handleDeleteAll,
    // 列表
    vectorList,
    listLoading,
    page,
    pageSize,
    emptyText,
    loadVectors,
    handlePageChange,
    PAGINATION_LAYOUT,
    // 详情弹窗
    detailVisible,
    detailTitle,
    activeItem,
    formatTime,
    padIdx,
    openDetail,
  };
}

export type UseFileVector = ReturnType<typeof useFileVector>;
