<template>
  <div class="admin-file-page">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card total">
        <div class="stat-icon"><el-icon :size="22"><Files /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.totalFiles }}</div>
          <div class="stat-label">文件总数</div>
        </div>
      </div>
      <div class="stat-card size">
        <div class="stat-icon"><el-icon :size="22"><Monitor /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ formatSize(stats.totalSize) }}</div>
          <div class="stat-label">占用空间</div>
        </div>
      </div>
      <div class="stat-card vectorized">
        <div class="stat-icon"><el-icon :size="22"><DataAnalysis /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">
            {{ stats.vectorizedFiles }}
            <span v-if="stats.totalFiles" class="stat-sub">/ {{ stats.totalFiles }}</span>
          </div>
          <div class="stat-label">已向量化</div>
        </div>
      </div>
    </div>

    <div class="file-layout">
      <!-- 左侧：分类树 -->
      <aside class="category-panel">
        <div class="category-header">
          <span class="category-title">
            <el-icon :size="14"><FolderOpened /></el-icon>
            文件分类
          </span>
          <el-button text size="small" class="category-config-btn" @click="openCategoryManage">管理</el-button>
        </div>
        <div
          class="category-all"
          :class="{ active: searchCategoryId === undefined }"
          @click="handleSelectCategory(undefined)"
        >
          <el-icon :size="13"><Folder /></el-icon>
          <span class="node-name">全部文件</span>
          <span class="node-count">{{ stats.totalFiles }}</span>
        </div>
        <el-tree
          class="category-tree"
          :data="categoryTree"
          :props="categoryTreeProps"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :highlight-current="searchCategoryId !== undefined"
          :current-node-key="searchCategoryId"
          @node-click="handleTreeClick"
        >
          <template #default="{ data }">
            <div class="category-node-inner">
              <el-icon :size="13"><Folder /></el-icon>
              <span class="node-name">{{ data.categoryName }}</span>
              <span v-if="data.defaultType === 1" class="node-default">默认</span>
              <span v-if="data.fileCount" class="node-count">{{ data.fileCount }}</span>
            </div>
          </template>
        </el-tree>
        <el-button
          v-if="!categoryLoading && !categoryTree.length"
          class="category-add-tip"
          text
          type="primary"
          size="small"
          @click="openCategoryEdit()"
        >
          + 新增分类
        </el-button>
      </aside>

      <!-- 右侧：内容区 -->
      <section class="content-panel">
        <!-- 搜索条 -->
        <div class="search-bar">
          <el-input
            v-model="searchForm.fileName"
            placeholder="搜索文件名"
            clearable
            style="width: 170px"
            @keyup.enter="handleSearch"
          >
            <template #prefix><el-icon :size="14"><Search /></el-icon></template>
          </el-input>
          <el-tree-select
            v-model="searchCategoryId"
            :data="categoryTree"
            :props="categoryTreeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="全部分类"
            style="width: 140px"
          />
          <el-select v-model="searchForm.fileSuffix" placeholder="文件类型" clearable filterable style="width: 120px">
            <el-option v-for="s in COMMON_FILE_SUFFIXES" :key="s" :label="s.toUpperCase()" :value="s" />
          </el-select>
          <el-select v-model="searchForm.status" placeholder="解析状态" clearable style="width: 120px">
            <el-option
              v-for="s in STATUS_MAP"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
          <el-select v-model="searchForm.vectorStatus" placeholder="向量状态" clearable style="width: 120px">
            <el-option label="已启用" :value="1" />
            <el-option label="未启用" :value="0" />
          </el-select>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <div class="search-spacer"></div>
          <el-button type="primary" @click="openUploadDialog">
            <el-icon style="margin-right: 4px"><Upload /></el-icon>
            上传文件
          </el-button>
        </div>

        <!-- 文件表格 -->
        <el-table v-loading="tableLoading" :data="fileList" class="file-table" style="width: 100%">
          <el-table-column label="文件名" min-width="230">
            <template #default="{ row }">
              <div class="file-name-cell">
                <span class="file-type-icon" :style="{ background: fileMeta(row).bg, color: fileMeta(row).color }">
                  <el-icon :size="16"><component :is="fileMeta(row).icon" /></el-icon>
                </span>
                <span class="file-name-text" :title="row.originalName" @click="handlePreview(row)">
                  {{ row.originalName }}
                </span>
                <span v-if="row.vectorStatus === 1" class="vector-flag">向量</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="categoryTagType(row.categoryName)" effect="light" round>
                {{ row.categoryName || "未分类" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">
              <span class="suffix-text">{{ (row.fileSuffix || "-").toUpperCase() }}</span>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="解析状态" width="110">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.status === 2 && row.failReason"
                :content="`失败原因：${row.failReason}`"
                placement="top"
              >
                <span class="status-badge" :class="statusMeta(row.status).cls">
                  {{ statusMeta(row.status).label }}
                </span>
              </el-tooltip>
              <span v-else class="status-badge" :class="statusMeta(row.status).cls">
                {{ statusMeta(row.status).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="向量状态" width="100">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.knowledgeType"
                :content="`知识类型：${KNOWLEDGE_TYPE_MAP[row.knowledgeType as number] ?? '未知'}`"
                placement="top"
              >
                <span class="status-badge" :class="row.vectorStatus === 1 ? 'ok' : 'off'">
                  {{ row.vectorStatus === 1 ? "已启用" : "未启用" }}
                </span>
              </el-tooltip>
              <span v-else class="status-badge" :class="row.vectorStatus === 1 ? 'ok' : 'off'">
                {{ row.vectorStatus === 1 ? "已启用" : "未启用" }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="上传人" width="100" prop="creatorName" show-overflow-tooltip>
            <template #default="{ row }">{{ row.creatorName || "-" }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="228" fixed="right">
            <template #default="{ row }">
              <el-button text size="small" type="primary" @click="handlePreview(row)">预览</el-button>
              <el-button text size="small" @click="handleDownload(row)">下载</el-button>
              <el-button text size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button text size="small" type="primary" @click="goVector(row)">向量</el-button>
              <el-button text size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无文件数据" :image-size="80" />
          </template>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="loadFiles"
            @size-change="handleSizeChange"
          />
        </div>
      </section>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传文件" width="520" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="文件分类">
          <el-tree-select
            v-model="uploadCategoryId"
            :data="categoryTree"
            :props="categoryTreeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="请选择分类（可选）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="选择文件" required>
          <el-upload
            drag
            multiple
            :auto-upload="true"
            :show-file-list="true"
            :limit="5"
            :before-upload="beforeUpload"
            :http-request="handleUploadRequest"
            v-model:file-list="uploadFileList"
          >
            <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF / Word / Excel / 图片 / 音视频等，单个文件不超过 100MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑文件信息" width="440" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="文件名">
          <el-input v-model="editForm.originalName" placeholder="请输入文件名（含后缀）" />
        </el-form-item>
        <el-form-item label="分类">
          <el-tree-select
            v-model="editForm.categoryId"
            :data="categoryTree"
            :props="categoryTreeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="请选择分类"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分类管理弹窗 -->
    <el-dialog v-model="categoryManageVisible" title="分类管理" width="460" destroy-on-close>
      <div class="category-manage-header">
        <span class="category-manage-tip">共 {{ categoryTree.length }} 个一级分类，支持多级嵌套</span>
        <el-button type="primary" size="small" @click="openCategoryEdit()">
          <el-icon style="margin-right: 3px"><Plus /></el-icon>
          新增分类
        </el-button>
      </div>
      <div class="category-manage-body">
        <el-tree
          :data="categoryTree"
          :props="categoryTreeProps"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <div class="category-manage-node">
              <el-icon :size="14"><Folder /></el-icon>
              <span class="node-name">{{ data.categoryName }}</span>
              <span v-if="data.defaultType === 1" class="node-default">默认</span>
              <span v-if="data.fileCount" class="node-count">{{ data.fileCount }}</span>
              <span class="cm-actions">
                <el-button text size="small" type="primary" @click.stop="openCategoryEdit(data)">编辑</el-button>
                <el-button text size="small" type="danger" @click.stop="handleDeleteCategory(data)">删除</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="categoryManageVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 分类新增/编辑弹窗 -->
    <el-dialog
      v-model="categoryEditVisible"
      :title="editingCategoryId ? '编辑分类' : '新增分类'"
      width="380"
      destroy-on-close
    >
      <el-form label-width="70px">
        <el-form-item v-if="!editingCategoryId" label="父分类">
          <el-tree-select
            v-model="categoryParentId"
            :data="categoryTree"
            :props="categoryTreeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="不选则为一级分类"
            style="width: 100%"
            :default-expanded-keys="[]"
          />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="categoryForm.name" placeholder="请输入分类名称" maxlength="20" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="handleSaveCategory">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewRow?.originalName"
      width="860"
      top="6vh"
      destroy-on-close
      @closed="clearPreview"
    >
      <div class="preview-body" v-loading="previewLoading">
        <template v-if="isImagePreview">
          <img :src="previewImageUrl || ''" :alt="previewRow?.originalName" class="preview-image" />
        </template>
        <template v-else-if="isTextPreview && !previewLoading">
          <pre v-if="previewText" class="preview-text">{{ previewText }}</pre>
          <div v-else class="preview-placeholder">
            <el-icon :size="44"><Document /></el-icon>
            <p>文本内容获取失败，请下载后查看</p>
            <el-button type="primary" @click="handleDownload(previewRow)">立即下载</el-button>
          </div>
        </template>
        <template v-else-if="!previewLoading">
          <div class="preview-placeholder">
            <el-icon :size="44"><Document /></el-icon>
            <p>该文件类型暂不支持在线预览，请下载后查看</p>
            <el-button type="primary" @click="handleDownload(previewRow)">立即下载</el-button>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { UploadUserFile } from "element-plus";
import {
  Files,
  Monitor,
  DataAnalysis,
  Search,
  Folder,
  FolderOpened,
  Upload,
  UploadFilled,
  Document,
  Picture,
  VideoPlay,
  Headset,
  DataLine,
  Notebook,
  Box,
  Link,
  Plus,
} from "@element-plus/icons-vue";
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

/* ==================== 类型定义 ==================== */

interface CategoryNode {
  id: number;
  parentId: number;
  categoryName: string;
  defaultType?: number;
  fileCount?: number;
  children?: CategoryNode[];
}

interface FileRow {
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

type TagType = "primary" | "success" | "info" | "warning" | "danger";

/* ==================== 常量配置 ==================== */

/** 文件解析状态（FileVO.status） */
const STATUS_MAP = [
  { label: "待解析", value: 0 },
  { label: "解析中", value: 1 },
  { label: "解析失败", value: 2 },
  { label: "解析完成", value: 3 },
] as const;

/** 向量库知识类型 */
const KNOWLEDGE_TYPE_MAP: Record<number, string> = {
  1: "症状库",
  2: "诊断标准库",
  3: "干预方案库",
};

/** 常见文件后缀 */
const COMMON_FILE_SUFFIXES = [
  "pdf",
  "doc",
  "docx",
  "xls",
  "xlsx",
  "ppt",
  "pptx",
  "jpg",
  "jpeg",
  "png",
  "gif",
  "webp",
  "svg",
  "mp3",
  "wav",
  "mp4",
  "avi",
  "mov",
  "txt",
  "md",
  "csv",
] as const;

const PDF_SUFFIXES = ["pdf"];
const OFFICE_SUFFIXES = ["doc", "docx", "xls", "xlsx", "ppt", "pptx"];
const IMAGE_SUFFIXES = ["jpg", "jpeg", "png", "gif", "webp", "svg", "bmp"];
const AUDIO_SUFFIXES = ["mp3", "wav", "m4a", "ogg", "flac"];
const VIDEO_SUFFIXES = ["mp4", "avi", "mov", "mkv", "webm", "flv"];
const TEXT_SUFFIXES = ["txt", "md", "csv", "json", "log", "xml", "yml", "yaml", "html", "css", "js", "ts", "vue", "java"];
const ZIP_SUFFIXES = ["zip", "rar", "7z", "tar", "gz"];

/** 按后缀映射的彩色图标 */
interface FileIconMeta {
  icon: unknown;
  bg: string;
  color: string;
}

const FILE_ICONS: Record<string, FileIconMeta> = {
  pdf: { icon: Link, bg: "#fef2f2", color: "#ef4444" },
  word: { icon: Notebook, bg: "#eff6ff", color: "#3b82f6" },
  excel: { icon: DataLine, bg: "#f0fdf4", color: "#22c55e" },
  ppt: { icon: Monitor, bg: "#fff7ed", color: "#f97316" },
  image: { icon: Picture, bg: "#f0fdfa", color: "#14b8a6" },
  audio: { icon: Headset, bg: "#f5f3ff", color: "#8b5cf6" },
  video: { icon: VideoPlay, bg: "#fdf2f8", color: "#ec4899" },
  text: { icon: Document, bg: "#f1f5f9", color: "#64748b" },
  zip: { icon: Box, bg: "#fefce8", color: "#ca8a04" },
  default: { icon: Document, bg: "#eef2ff", color: "#6366f1" },
};

function fileMeta(row: any): FileIconMeta {
  const suffix = (row.fileSuffix || "").toLowerCase();
  if (PDF_SUFFIXES.includes(suffix)) return FILE_ICONS.pdf;
  if (OFFICE_SUFFIXES.includes(suffix)) {
    if (suffix.startsWith("x")) return FILE_ICONS.excel;
    if (suffix.startsWith("p")) return FILE_ICONS.ppt;
    return FILE_ICONS.word;
  }
  if (IMAGE_SUFFIXES.includes(suffix)) return FILE_ICONS.image;
  if (AUDIO_SUFFIXES.includes(suffix)) return FILE_ICONS.audio;
  if (VIDEO_SUFFIXES.includes(suffix)) return FILE_ICONS.video;
  if (ZIP_SUFFIXES.includes(suffix)) return FILE_ICONS.zip;
  if (TEXT_SUFFIXES.includes(suffix)) return FILE_ICONS.text;
  return FILE_ICONS.default;
}

/** 大小格式化（B / KB / MB） */
function formatSize(bytes?: number): string {
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

/** 分类标签配色（按名称散列取色，稳定不闪动） */
function categoryTagType(name?: string): TagType {
  const palette: TagType[] = ["primary", "success", "info", "warning", "danger"];
  if (!name) return "info";
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 131 + name.charCodeAt(i)) % 10007;
  }
  return palette[hash % palette.length];
}

/** 解析状态徽章 */
function statusMeta(status?: number) {
  switch (status) {
    case 0:
      return { label: "待解析", cls: "warn" };
    case 1:
      return { label: "解析中", cls: "info" };
    case 2:
      return { label: "解析失败", cls: "err" };
    case 3:
      return { label: "解析完成", cls: "ok" };
    default:
      return { label: "未知", cls: "off" };
  }
}

const categoryTreeProps = {
  children: "children",
  label: "categoryName",
} as const;

/* ==================== 状态 ==================== */

const router = useRouter();

// 列表
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

/* ==================== 计算属性 ==================== */

const isImagePreview = computed(() => {
  const suffix = (previewRow.value?.fileSuffix || "").toLowerCase();
  return IMAGE_SUFFIXES.includes(suffix);
});

const isTextPreview = computed(() => {
  const suffix = (previewRow.value?.fileSuffix || "").toLowerCase();
  return TEXT_SUFFIXES.includes(suffix);
});

/* ==================== 列表加载 ==================== */

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

/* ==================== 搜索 ==================== */

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

function handleTreeClick(data: CategoryNode) {
  handleSelectCategory(data.id);
}

function handleSizeChange() {
  page.value = 1;
  loadFiles();
}

/* ==================== 上传 ==================== */

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

async function handleUploadRequest(options: { file: File; onSuccess: (resp: any) => void; onError: (err: any) => void }) {
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

/* ==================== 编辑 ==================== */

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

/* ==================== 删除 ==================== */

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

/* ==================== 分类管理 ==================== */

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

/* ==================== 下载 / 预览 / 向量 ==================== */

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

function goVector(row: any) {
  router.push(`/admin/file/vector/${row.id}`);
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  loadCategoryTree();
  loadStats();
  loadFiles();
});
</script>

<style scoped>
.admin-file-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ===== 统计卡片 ===== */
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border-radius: 12px;
  padding: 18px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}

.stat-card:hover {
  box-shadow: 0 6px 18px rgba(99, 102, 241, 0.08);
  transform: translateY(-1px);
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card.total .stat-icon {
  background: #eef2ff;
  color: #6366f1;
}

.stat-card.size .stat-icon {
  background: #eff6ff;
  color: #3b82f6;
}

.stat-card.vectorized .stat-icon {
  background: #f0fdf4;
  color: #22c55e;
}

.stat-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-sub {
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  margin-left: 2px;
}

.stat-label {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

/* ===== 主布局 ===== */
.file-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.category-panel {
  width: 208px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  padding: 14px 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
}

.category-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.category-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.category-config-btn {
  color: #6366f1;
  font-weight: 500;
}

.category-all {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12.5px;
  color: #1e293b;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 2px;
}

.category-all:hover {
  background: #f1f5f9;
}

.category-all.active {
  background: #eef2ff;
  color: #6366f1;
}

.category-tree {
  flex: 1;
  overflow-y: auto;
  --el-tree-node-hover-bg-color: #f8fafc;
}

.category-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 8px;
  margin-bottom: 1px;
}

.category-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #eef2ff;
  color: #6366f1;
}

.category-node-inner {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  padding-right: 4px;
}

.node-name {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-count {
  font-size: 10.5px;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 8px;
  padding: 0 6px;
  line-height: 1.6;
  flex-shrink: 0;
}

.category-all.active .node-count,
.category-tree :deep(.el-tree-node.is-current) .node-count {
  background: #e0e7ff;
  color: #6366f1;
}

.node-default {
  font-size: 10px;
  background: #eef2ff;
  color: #6366f1;
  border-radius: 4px;
  padding: 0 4px;
  line-height: 1.6;
  flex-shrink: 0;
}

.category-add-tip {
  width: 100%;
  margin-top: 4px;
}

/* ===== 内容区 ===== */
.content-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.search-spacer {
  flex: 1;
}

/* ===== 表格 ===== */
.file-table {
  width: 100%;
}

.file-table :deep(th.el-table__cell) {
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-type-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-name-text {
  cursor: pointer;
  color: #334155;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.file-name-text:hover {
  color: #6366f1;
}

.vector-flag {
  font-size: 10px;
  color: #8b5cf6;
  background: #f5f3ff;
  border-radius: 4px;
  padding: 1px 5px;
  flex-shrink: 0;
}

.suffix-text {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

/* ===== 状态徽章 ===== */
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 10px;
  font-size: 11.5px;
  font-weight: 500;
  line-height: 1.6;
  white-space: nowrap;
  cursor: default;
}

.status-badge.ok {
  background: #f0fdf4;
  color: #16a34a;
}

.status-badge.warn {
  background: #fffbeb;
  color: #d97706;
}

.status-badge.err {
  background: #fef2f2;
  color: #dc2626;
}

.status-badge.info {
  background: #eff6ff;
  color: #2563eb;
}

.status-badge.off {
  background: #f1f5f9;
  color: #94a3b8;
}

/* ===== 分类管理弹窗 ===== */
.category-manage-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.category-manage-tip {
  font-size: 12px;
  color: #94a3b8;
}

.category-manage-body {
  max-height: 380px;
  overflow-y: auto;
}

.category-manage-body :deep(.el-tree-node__content) {
  height: 36px;
  border-radius: 8px;
}

.category-manage-body :deep(.el-tree-node__content:hover) {
  background: #f8fafc;
}

.category-manage-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  padding-right: 4px;
}

.cm-actions {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.cm-actions .el-button {
  margin-left: 4px;
}

/* ===== 预览 ===== */
.preview-body {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
}

.preview-image {
  max-width: 100%;
  max-height: 580px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.preview-text {
  width: 100%;
  height: 500px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 8px;
  font-size: 12.5px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #94a3b8;
  font-size: 13px;
  padding: 48px;
  text-align: center;
}

/* ===== 分页 ===== */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>