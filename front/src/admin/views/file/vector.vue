<template>
  <div class="vector-page">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/admin/file" class="breadcrumb-link">文件管理</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">{{ fileName }}</span>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-sub">向量数据</span>
    </div>

    <!-- 文件信息卡 -->
    <div class="file-card">
      <div class="file-icon-box">
        <el-icon :size="26"><Document /></el-icon>
      </div>
      <div class="file-meta">
        <div class="file-name-row">
          <span class="file-name">{{ fileName }}</span>
          <el-tag size="small" effect="light" :type="vectorStatus.tagType">{{ vectorStatus.label }}</el-tag>
        </div>
        <div class="file-sub">
          <span>{{ suffixText }}</span>
          <span class="dot">·</span>
          <span>{{ fileSizeText }}</span>
          <span class="dot">·</span>
          <span>向量片段 {{ total }} 个</span>
        </div>
      </div>
    </div>

    <!-- 统计 + 工具栏 -->
    <div class="overview-card">
      <div class="stats-row">
        <div class="stat-card">
          <div class="stat-icon indigo"><el-icon :size="20"><Document /></el-icon></div>
          <div class="stat-info">
            <div class="stat-num">{{ total }}</div>
            <div class="stat-label">向量片段总数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon purple"><el-icon :size="20"><CollectionTag /></el-icon></div>
          <div class="stat-info">
            <div class="stat-num">{{ chunkCountL1 }}</div>
            <div class="stat-label">一级分块 L1</div>
          </div>
        </div>
        <div class="stat-card" :class="statusTone">
          <div class="stat-icon">
            <el-icon :size="20" :class="{ 'is-loading': isParsing }"><component :is="statusIcon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-num is-text">{{ vectorStatus.label }}</div>
            <div class="stat-label">加载状态</div>
          </div>
        </div>
      </div>

      <div class="toolbar">
        <div class="toolbar-left">
          <span v-if="isParsing" class="toolbar-hint">
            <el-icon class="is-loading" :size="14"><Loading /></el-icon>
            向量解析进行中，可随时中断
          </span>
          <span v-else class="toolbar-hint">文件将按层级切分为向量片段，供 AI 知识库检索</span>
        </div>
        <div class="toolbar-right">
          <el-button
            type="primary"
            :icon="Refresh"
            :loading="submitting"
            :disabled="!canLoad"
            @click="handleLoad"
          >
            {{ loadLabel }}
          </el-button>
          <el-button type="warning" plain :icon="VideoPause" :disabled="!isParsing" @click="handleInterrupt">
            中断加载
          </el-button>
          <el-button
            type="danger"
            plain
            :icon="Delete"
            :disabled="!canDelete"
            :loading="deleting"
            @click="handleDeleteAll"
          >
            删除全部向量
          </el-button>
        </div>
      </div>

      <!-- 解析进度条 -->
      <div v-if="isParsing" class="parsing-banner">
        <div class="parsing-top">
          <span class="parsing-title">正在向量化解析中</span>
          <span class="parsing-sub">已耗时 {{ formatElapsed(elapsed) }} · 完成后将自动刷新片段列表</span>
        </div>
        <el-progress
          class="parsing-progress"
          :percentage="100"
          :indeterminate="true"
          :duration="2"
          :stroke-width="4"
          :show-text="false"
          color="#6366f1"
        />
      </div>
    </div>

    <!-- 向量片段列表 -->
    <div class="list-section">
      <div class="section-head">
        <div class="section-title-wrap">
          <h3 class="section-title">向量片段列表</h3>
          <span v-if="total > 0" class="section-count">共 {{ total }} 条</span>
        </div>
        <el-button :icon="Refresh" circle plain size="small" title="刷新列表" @click="loadVectors" />
      </div>

      <div v-if="isParsing && total === 0" class="skeleton-list">
        <div v-for="n in 3" :key="n" class="skeleton-card">
          <div class="skeleton-badges">
            <span class="sk-badge" />
            <span class="sk-badge" />
          </div>
          <div class="skeleton-lines">
            <div class="sk-line" style="width: 92%" />
            <div class="sk-line" style="width: 74%" />
            <div class="sk-line" style="width: 40%" />
          </div>
        </div>
      </div>

      <el-empty v-else-if="!listLoading && total === 0" :description="emptyText" :image-size="120" />

      <div v-else class="chunk-list" v-loading="listLoading">
        <div v-for="item in vectorList" :key="item.id" class="chunk-card">
          <div class="chunk-left">
            <span class="chunk-badge l1">L1 · {{ padIdx(item.chunkLevel1Idx) }}</span>
            <span class="chunk-badge l2">L2 · {{ padIdx(item.chunkLevel2Idx) }}</span>
          </div>
          <div class="chunk-main">
            <p class="chunk-content">{{ item.content }}</p>
            <div class="chunk-foot">
              <span class="chunk-char">{{ item.content.length }} 字</span>
              <span class="chunk-time">
                <el-icon :size="12"><Clock /></el-icon>
                {{ formatTime(item.createdTime) }}
              </span>
              <el-button text type="primary" size="small" :icon="View" class="chunk-view" @click="openDetail(item)">
                查看完整内容
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="total > pageSize" class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 完整内容弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailTitle" width="640" append-to-body>
      <div class="detail-meta">
        <span>创建时间：{{ formatTime(activeItem?.createdTime) }}</span>
        <span>字符数：{{ activeItem?.content.length ?? 0 }}</span>
        <span>L1 / L2：{{ padIdx(activeItem?.chunkLevel1Idx) }} / {{ padIdx(activeItem?.chunkLevel2Idx) }}</span>
      </div>
      <div class="detail-content">{{ activeItem?.content }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Clock,
  CollectionTag,
  Delete,
  Document,
  Loading,
  Refresh,
  SuccessFilled,
  VideoPause,
  View,
  Warning,
} from "@element-plus/icons-vue";
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

interface FileInfo {
  id: number;
  originalName?: string;
  fileSuffix?: string;
  fileSize?: number;
  status?: number;
  vectorStatus?: number;
  knowledgeType?: number;
  failReason?: string;
}

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
const vectorStatus = computed(() => {
  const s = fileInfo.value?.status;
  switch (s) {
    case 0:
      return { label: "待解析", tagType: "warning" as const };
    case 1:
      return { label: "解析中", tagType: "primary" as const };
    case 2:
      return { label: "解析失败", tagType: "danger" as const };
    case 3:
      return { label: "解析完成", tagType: "success" as const };
    default:
      return { label: "未知", tagType: "info" as const };
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

const statusIcon = computed(() => {
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

function handlePageChange() {
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
</script>

<style scoped>
.vector-page {
  --primary: #6366f1;
  --primary-hover: #4f46e5;
  --primary-bg: #eef2ff;
  --text-main: #1e293b;
  --text-sub: #64748b;
  --text-muted: #94a3b8;
  --border: #eef0f4;
  --bg-soft: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ==================== 面包屑 ==================== */

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

.breadcrumb-link {
  color: var(--primary);
  font-weight: 500;
}

.breadcrumb-link:hover {
  text-decoration: underline;
}

.breadcrumb-sep {
  color: #cbd5e1;
}

.breadcrumb-current {
  color: var(--text-main);
  font-weight: 600;
}

/* ==================== 文件信息卡 ==================== */

.file-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.file-icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 12px;
  color: #fff;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--primary), #8b5cf6);
  box-shadow: 0 6px 14px rgba(99, 102, 241, 0.35);
}

.file-meta {
  min-width: 0;
}

.file-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-sub);
}

.file-sub .dot {
  color: #cbd5e1;
}

/* ==================== 概览卡片（统计 + 工具栏） ==================== */

.overview-card {
  padding: 20px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--bg-soft);
  transition: border-color 0.2s ease;
}

.stat-card:hover {
  border-color: #e0e7ff;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-icon.indigo {
  background: var(--primary-bg);
  color: var(--primary);
}

.stat-icon.purple {
  background: #f5f3ff;
  color: #8b5cf6;
}

.stat-card.tone-success .stat-icon {
  background: #f0fdf4;
  color: #22c55e;
}

.stat-card.tone-danger .stat-icon {
  background: #fef2f2;
  color: #ef4444;
}

.stat-card.tone-warning .stat-icon {
  background: #fffbeb;
  color: #f59e0b;
}

.stat-card.tone-primary .stat-icon {
  background: var(--primary-bg);
  color: var(--primary);
}

.stat-card.tone-default .stat-icon {
  background: #f1f5f9;
  color: var(--text-sub);
}

.stat-info {
  min-width: 0;
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.stat-num.is-text {
  font-size: 16px;
  line-height: 24px;
}

.stat-label {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-muted);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.toolbar-left {
  min-width: 0;
}

.toolbar-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-muted);
}

.toolbar-right {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ==================== 解析进度条 ==================== */

.parsing-banner {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 10px;
  background: linear-gradient(90deg, #eef2ff, #f5f3ff);
  border: 1px solid #e0e7ff;
}

.parsing-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.parsing-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #3730a3;
}

.parsing-sub {
  font-size: 12px;
  color: #6b7280;
}

.parsing-progress {
  margin-top: 10px;
}

/* ==================== 列表卡片 ==================== */

.list-section {
  padding: 20px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.section-count {
  font-size: 12px;
  color: var(--text-muted);
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 160px;
}

.chunk-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #fff;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.chunk-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.08);
  transform: translateY(-1px);
}

.chunk-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 76px;
  flex-shrink: 0;
}

.chunk-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.chunk-badge.l1 {
  background: var(--primary-bg);
  color: var(--primary);
}

.chunk-badge.l2 {
  background: #f5f3ff;
  color: #8b5cf6;
}

.chunk-main {
  min-width: 0;
  flex: 1;
}

.chunk-content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  word-break: break-word;
}

.chunk-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.chunk-char {
  flex-shrink: 0;
}

.chunk-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.chunk-view {
  margin-left: auto;
}

/* ==================== 骨架屏 ==================== */

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #fff;
}

.skeleton-badges {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 76px;
  flex-shrink: 0;
}

.sk-badge {
  height: 22px;
  border-radius: 6px;
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 4px;
}

.sk-line {
  height: 12px;
  border-radius: 6px;
}

.sk-badge,
.sk-line {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 37%, #f1f5f9 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

@keyframes skeleton-loading {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

/* ==================== 分页 ==================== */

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

/* ==================== 弹窗 ==================== */

.detail-meta {
  display: flex;
  gap: 20px;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 12.5px;
  color: var(--text-sub);
}

.detail-content {
  padding: 14px;
  max-height: 420px;
  overflow: auto;
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  background: var(--bg-soft);
  font-size: 13.5px;
  line-height: 1.9;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>