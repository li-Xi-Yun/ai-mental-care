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
  getFileVectorStats,
  interruptFileVector,
  loadFileVector,
} from "@/admin/api/file/vector";
import type { FileVectorVO } from "@/admin/api/file/vector";
import { useAdminStomp, type FileVectorStatusEvent } from "@/admin/ws/admin-stomp";

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
  parseStartTime?: string;
  parseEndTime?: string;
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

  /* ==================== 分块统计（后端权威统计） ==================== */
  /** 一级分块总数（后端 COUNT(DISTINCT chunk_level1_idx)，全量精确值） */
  const chunkCountL1 = ref(0);
  /** 二级分块总数（=向量片段总数，后端统计；解析中随批次增长） */
  const chunkCountL2 = ref(0);
  /* ==================== WebSocket 实时解析状态 ==================== */
  /** 当前实时阶段文案（如“读取中/语义分割中/向量化中/写入中”） */
  const liveStage = ref<string | null>(null);
  /** 当前实时进度百分比（0-100，后端无可靠总量时为 null） */
  const liveProgress = ref<number | null>(null);
  /** 本地已处理的最大事件序号（去重） */
  let lastSeq = -1;
  const ws = useAdminStomp({
    onTransportClose: () => {
      // 传输层断开：保留轮询兜底（startPolling 若未运行会在收到请求/刷新时再起）
      liveStage.value = null;
      liveProgress.value = null;
    },
  });

  /* ==================== 派生状态 ==================== */
  const fileName = computed(() => fileInfo.value?.originalName || `文件 #${fileId}`);
  const suffixText = computed(() => {
    const s = fileInfo.value?.fileSuffix;
    return s ? s.toUpperCase() : "文件";
  });
  const fileSizeText = computed(() => formatSize(fileInfo.value?.fileSize));
  const isParsing = computed(() => fileInfo.value?.status === 1);
  /** 知识类型为 0（无）时后端拒绝向量化（1818），前端禁用按钮并给出提示 */
  const knowledgeTypeNone = computed(() => (fileInfo.value?.knowledgeType ?? 0) === 0);
  const canLoad = computed(() => {
    const s = fileInfo.value?.status;
    return (s === 0 || s === 2) && !knowledgeTypeNone.value;
  });
  const loadBlockedReason = computed(() =>
    knowledgeTypeNone.value
      ? "该文件未设置知识类型（当前为「无」），请先在文件管理中编辑知识类型后再向量化"
      : "",
  );
  const canDelete = computed(() => fileInfo.value?.status === 3);
  const loadLabel = computed(() => {
    const s = fileInfo.value?.status;
    if (s === 1) return "向量解析中…";
    if (s === 2) return "重新向量化";
    return "开始向量化";
  });
  /** 解析耗时（秒）：解析中为实时已耗时，终态为 parseEndTime - parseStartTime；无有效记录则 null */
  const parseDuration = computed<number | null>(() => {
    const start = fileInfo.value?.parseStartTime;
    const end = fileInfo.value?.parseEndTime;
    if (start && end) {
      const s = dayjs(start).valueOf();
      const e = dayjs(end).valueOf();
      if (!Number.isNaN(s) && !Number.isNaN(e) && e > s) {
        return Math.floor((e - s) / 1000);
      }
    }
    return null;
  });
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

  /** 加载片段列表；silent=true 时（轮询静默刷新）不触发 loading 遮罩与错误弹窗，避免解析中每 2s 闪烁 */
  async function loadVectors(silent = false) {
    if (!silent) listLoading.value = true;
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
      if (!silent) ElMessage.error((e as Error)?.message || "向量列表加载失败");
    } finally {
      if (!silent) listLoading.value = false;
    }
  }

  /** 加载分块统计（一级/二级），解析中也允许查询（数据随批次增长） */
  async function loadStats() {
    try {
      const res = await getFileVectorStats(fileId);
      const data = res.data?.data;
      if (data) {
        chunkCountL1.value = data.level1Count ?? 0;
        chunkCountL2.value = data.level2Count ?? 0;
      }
    } catch {
      // 静默失败，保留上次统计
    }
  }

  /* ==================== 轮询进度 ==================== */
  /** 轮询最长时长（防任务卡死导致无限轮询） */
  const MAX_POLL_MS = 10 * 60 * 1000;

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
    // 计时起点 = 服务端记录的本次解析开始时间（重进页面不再从 0 重置）；
    // 取不到（历史数据/接口异常）时退化为当前时刻本地计时
    let baseMs = 0;
    const startStr = fileInfo.value?.parseStartTime;
    if (startStr) {
      const t = dayjs(startStr).valueOf();
      if (!Number.isNaN(t)) baseMs = t;
    }
    if (!baseMs) baseMs = Date.now();
    elapsed.value = Math.max(0, Math.floor((Date.now() - baseMs) / 1000));
    submitting.value = false;
    tickTimer = window.setInterval(() => {
      elapsed.value = Math.max(0, Math.floor((Date.now() - baseMs) / 1000));
    }, 1000);
    pollTimer = window.setInterval(async () => {
      // 超时保护：超过 10 分钟仍未完成则停止轮询，提示手动刷新
      if (Date.now() - baseMs > MAX_POLL_MS) {
        stopPolling();
        ElMessage.warning("向量解析超时（超过 10 分钟），请稍后刷新页面查看结果");
        loadVectors();
        return;
      }
      await refreshFileInfo();
      const s = fileInfo.value?.status;
      if (s === 3) {
        stopPolling();
        ElMessage.success("向量加载完成");
      } else if (s === 2) {
        stopPolling();
        ElMessage.error(`向量解析失败：${fileInfo.value?.failReason || "未知原因"}`);
      }
      // 边解析边展示：解析中持续刷新已入库的片段与分块统计（待解析时后端返回空，无副作用）
      // 轮询用静默刷新（不弹错误、不闪 loading），避免每 2s 一次列表闪烁
      await Promise.all([loadStats(), loadVectors(true)]);
    }, 2000);
  }

  /** WebSocket 状态事件处理：去重 + 实时阶段/进度 + 终态刷新 */
  function handleStatusEvent(event: FileVectorStatusEvent) {
    if (event.sequence === undefined || event.sequence <= lastSeq) {
      // 丢弃重复 / 乱序事件
      return;
    }
    lastSeq = event.sequence;
    liveProgress.value = typeof event.progress === "number" && event.progress >= 0 && event.progress <= 100
      ? event.progress
      : null;
    liveStage.value = stageLabel(event.status);

    // 终态：REST 权威刷新 + 停止轮询
    if (event.status === "COMPLETED" || event.status === "FAILED" || event.status === "INTERRUPTED") {
      stopPolling();
      void refreshFileInfo().then(async () => {
        if (event.status === "COMPLETED" || event.status === "FAILED") {
          await Promise.all([loadStats(), loadVectors()]);
        }
        if (event.status === "INTERRUPTED") {
          liveStage.value = null;
          liveProgress.value = null;
        }
      });
    }
  }

  /** 状态 → 中文阶段文案；无进度时前端只展示阶段 */
  function stageLabel(status: string): string | null {
    switch (status) {
      case "PARSING": return "解析启动中";
      case "READING": return "文件读取中";
      case "TRANSFORMING": return "语义分割中";
      case "VECTORIZING": return "向量化中";
      case "WRITING": return "向量写入中";
      case "COMPLETED": return "解析完成";
      case "FAILED": return "解析失败";
      case "INTERRUPTED": return "解析已中断";
      default: return null;
    }
  }

  /** 建立 WebSocket 订阅（失败不抛错，轮询兜底） */
  async function setupWsSubscription() {
    lastSeq = -1;
    liveStage.value = null;
    liveProgress.value = null;
    const ok = await ws.connect();
    if (!ok) {
      return; // 连接失败：依赖既有轮询兜底
    }
    ws.subscribeFileStatus(fileId, handleStatusEvent);
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
    // 双重保险：知识类型为「无」时后端会拒绝（1818），前端先行拦截
    if (knowledgeTypeNone.value) {
      ElMessage.warning(loadBlockedReason.value);
      return;
    }
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
      await loadStats();
      if (isParsing.value) {
        startPolling();
      } else {
        loadVectors();
      }
    } catch (e) {
      submitting.value = false;
      // 透出后端业务错误（如 1818 知识类型为空、1805 文件过大等）
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
      await loadStats();
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
      await loadStats();
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

  /** 手动刷新：文件信息 + 分块统计 + 片段列表 */
  function handleRefresh() {
    void Promise.all([refreshFileInfo(), loadStats(), loadVectors()]);
  }

  /* ==================== 初始化 ==================== */
  onMounted(async () => {
    await refreshFileInfo();
    await loadStats();
    void setupWsSubscription();
    if (isParsing.value) {
      startPolling();
    } else {
      loadVectors();
    }
  });

  onBeforeUnmount(() => {
    stopPolling();
    ws.dispose();
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
    chunkCountL2,
    parseDuration,
    statusTone,
    statusIcon,
    isParsing,
    canLoad,
    canDelete,
    loadLabel,
    loadBlockedReason,
    submitting,
    deleting,
    elapsed,
    formatElapsed,
    loadStats,
    handleRefresh,
    // 实时解析状态
    liveStage,
    liveProgress,
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
