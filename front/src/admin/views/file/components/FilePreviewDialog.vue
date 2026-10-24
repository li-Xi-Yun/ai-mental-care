<!--
  文件预览弹窗：按 previewMode 渲染不同格式
  - image：直连静态资源 URL
  - pdf：浏览器原生 iframe 预览（静态资源免鉴权）
  - excel：SheetJS 解析 → 多 sheet 表格渲染（行数截断保护）
  - word：docx-preview 渲染（旧版 .doc 不支持）
  - ppt：pptx-preview 渲染
  - text：fetch 文本 → pre 展示
  - none / 渲染失败：占位 + 下载兜底
  预览库全部动态 import（按需加载，不进主包）
-->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="900"
    align-center
    destroy-on-close
    class="preview-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <div v-loading="busy || loading" class="preview-body">
      <!-- 图片 -->
      <div v-if="mode === 'image'" class="preview-center">
        <img
          v-if="previewUrl"
          :src="previewUrl"
          :alt="row?.originalName"
          class="preview-image"
          @error="renderError = '图片加载失败，请下载后查看'"
        />
        <el-empty v-else description="图片地址不可用" :image-size="80" />
      </div>

      <!-- PDF：fetch 取 blob 后用浏览器原生预览（静态资源带 X-Frame-Options: DENY，不能直接 iframe 直连） -->
      <iframe v-else-if="mode === 'pdf' && pdfBlobUrl" :src="pdfBlobUrl" class="preview-pdf" />

      <!-- Excel -->
      <div v-else-if="mode === 'excel' && !renderError" class="preview-excel">
        <el-tabs v-if="excelSheets.length > 1" v-model="activeSheet" class="preview-sheet-tabs">
          <el-tab-pane v-for="s in excelSheets" :key="s.name" :label="s.name" :name="s.name" />
        </el-tabs>
        <el-table
          :data="currentSheetRows"
          border
          size="small"
          max-height="58vh"
          class="preview-excel-table"
        >
          <el-table-column
            v-for="(h, i) in currentSheetHeaders"
            :key="i"
            :prop="`c${i}`"
            :label="h || `列${i + 1}`"
            min-width="110"
            show-overflow-tooltip
          />
          <template #empty>
            <el-empty description="该工作表暂无数据" :image-size="70" />
          </template>
        </el-table>
        <div v-if="currentSheetTruncated" class="preview-tip">
          仅展示前 {{ EXCEL_MAX_ROWS }} 行，完整内容请下载查看
        </div>
        <div v-if="currentSheetColsTruncated" class="preview-tip">
          列数较多，已省略超出 {{ EXCEL_MAX_COLS }} 列部分
        </div>
      </div>

      <!-- Word -->
      <div v-else-if="mode === 'word' && !renderError" ref="wordContainer" class="preview-word" />

      <!-- PPT -->
      <div v-else-if="mode === 'ppt' && !renderError" ref="pptContainer" class="preview-ppt" />

      <!-- 文本 -->
      <pre v-else-if="mode === 'text' && !renderError" class="preview-text">{{ textContent }}</pre>
      <div v-if="mode === 'text' && !renderError && textTruncated" class="preview-tip">
        文件较大，仅预览开头部分，完整内容请下载查看
      </div>

      <!-- 不支持 / 渲染失败 -->
      <div v-else class="preview-center">
        <el-empty :image-size="90" :description="renderError || '该文件类型暂不支持在线预览，请下载后查看'" />
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('download', row)">
        <el-icon class="btn-icon"><Download /></el-icon>下载
      </el-button>
      <el-button type="primary" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { ElMessageBox } from "element-plus";
import { Download } from "@element-plus/icons-vue";
import { formatSize, type FileRow } from "../useFileList";

export type PreviewMode = "image" | "pdf" | "excel" | "word" | "ppt" | "text" | "none";

/** Excel 单表最大渲染行数（防大文件卡顿） */
const EXCEL_MAX_ROWS = 200;
/** Excel 单表最大渲染列数（防列数无限生成表格列卡顿） */
const EXCEL_MAX_COLS = 60;
/** 文本预览最大抓取字节数：HTTP Range 只取文件开头，避免整包读入内存 */
const TEXT_MAX_BYTES = 512 * 1024;
/** 文本预览最大渲染字符数（超长行/压缩 JSON 二次兜底，防主线程布局卡顿） */
const TEXT_MAX_CHARS = 200_000;
/** PDF/Word/PPT/Excel 无法截断渲染，超过该大小先弹确认框再渲染 */
const HEAVY_FILE_THRESHOLD = 5 * 1024 * 1024;

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    row: FileRow | null;
    loading?: boolean;
    /** 已解析的静态资源可访问 URL（resolveResourceUrl 处理过） */
    previewUrl?: string;
    previewMode?: PreviewMode;
  }>(),
  { loading: false, previewUrl: "", previewMode: "none" },
);

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "download", row: FileRow | null): void;
  (e: "closed"): void;
}>();

const busy = ref(false);
const renderError = ref("");
const textContent = ref("");
/** 文本内容因过大被截断（仅预览开头），渲染提示条 */
const textTruncated = ref(false);
const excelSheets = ref<{ name: string; rows: string[][]; truncated: boolean; colsTruncated: boolean }[]>([]);
const activeSheet = ref("");
const wordContainer = ref<HTMLElement>();
const pptContainer = ref<HTMLElement>();
/** PDF 预览用 blob URL（绕过后端静态资源的 X-Frame-Options: DENY） */
const pdfBlobUrl = ref("");
let pptPreviewer: { preview: (file: ArrayBuffer) => Promise<unknown>; slideCount?: number; destroy?: () => void } | null = null;

const mode = computed(() => props.previewMode);
const dialogTitle = computed(() => `文件预览 · ${props.row?.originalName ?? ""}`);

const currentSheet = computed(
  () => excelSheets.value.find((s) => s.name === activeSheet.value) ?? excelSheets.value[0],
);
const currentSheetHeaders = computed(() => (currentSheet.value?.rows[0] ?? []).slice(0, EXCEL_MAX_COLS));
const currentSheetTruncated = computed(() => !!currentSheet.value?.truncated);
const currentSheetColsTruncated = computed(() => (currentSheet.value?.rows[0]?.length ?? 0) > EXCEL_MAX_COLS);
const currentSheetRows = computed(() => {
  const sheet = currentSheet.value;
  if (!sheet || sheet.rows.length <= 1) return [];
  return sheet.rows.slice(1).map((row, idx) => {
    const obj: Record<string, string> = { __idx: String(idx + 1) };
    // 超出 EXCEL_MAX_COLS 的列不建列，避免无头列对象堆积
    row.slice(0, EXCEL_MAX_COLS).forEach((cell, i) => {
      obj[`c${i}`] = cell;
    });
    return obj;
  });
});

function resetState() {
  renderError.value = "";
  textContent.value = "";
  textTruncated.value = false;
  excelSheets.value = [];
  activeSheet.value = "";
  if (pdfBlobUrl.value) {
    URL.revokeObjectURL(pdfBlobUrl.value);
    pdfBlobUrl.value = "";
  }
  pptPreviewer?.destroy?.();
  pptPreviewer = null;
  if (wordContainer.value) wordContainer.value.innerHTML = "";
  if (pptContainer.value) pptContainer.value.innerHTML = "";
}

/** PDF：fetch 取字节 → blob URL → iframe（后端静态资源带 X-Frame-Options: DENY，不能直连 iframe） */
async function loadPdf(url: string) {
  const buf = await fetchBytes(url);
  const blob = new Blob([buf], { type: "application/pdf" });
  pdfBlobUrl.value = URL.createObjectURL(blob);
}

async function fetchBytes(url: string): Promise<ArrayBuffer> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.arrayBuffer();
}

/** Excel：SheetJS 解析为多 sheet 二维数组（raw:false 取格式化值）；sheetRows 只解析前 N 行防大表卡顿 */
async function loadExcel(url: string) {
  const buf = await fetchBytes(url);
  const XLSX = await import("xlsx");
  // sheetRows 让解析器只解出前 EXCEL_MAX_ROWS+1 行，避免整表物化（10万行表解析成本可控）
  const wb = XLSX.read(new Uint8Array(buf), { type: "array", sheetRows: EXCEL_MAX_ROWS + 1 });
  excelSheets.value = wb.SheetNames.map((name) => {
    const ws = wb.Sheets[name];
    const all = XLSX.utils.sheet_to_json<unknown[]>(ws, { header: 1, raw: false, defval: "" });
    // !ref 被 sheetRows 裁剪过（decode_range 只能得到裁剪后的行高），
    // 真实总行数取 SheetJS 在限行时保留的 !fullref（裁剪前的完整范围）
    const fullRef = (ws as { "!fullref"?: string })["!fullref"];
    const ref = fullRef ?? ws["!ref"] ?? "";
    const totalRows = ref ? XLSX.utils.decode_range(ref).e.r + 1 : all.length;
    return {
      name,
      rows: all.map((r) => r.map((c) => String(c ?? ""))),
      truncated: totalRows > EXCEL_MAX_ROWS + 1,
      colsTruncated: (all[0]?.length ?? 0) > EXCEL_MAX_COLS,
    };
  });
  activeSheet.value = excelSheets.value[0]?.name ?? "";
}

/** Word：docx-preview 渲染（仅支持 .docx，旧版 .doc 直接提示） */
async function loadWord(url: string) {
  const suffix = (props.row?.fileSuffix || "").toLowerCase();
  if (suffix === "doc") {
    renderError.value = "旧版 .doc 格式暂不支持在线预览，请下载后查看";
    return;
  }
  const buf = await fetchBytes(url);
  const blob = new Blob([buf], {
    type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  });
  const { renderAsync } = await import("docx-preview");
  await nextTick();
  const container = wordContainer.value;
  if (!container) return;
  container.innerHTML = "";
  await renderAsync(blob, container, undefined, {
    inWrapper: true,
    ignoreWidth: true,
    ignoreHeight: true,
  });
}

/** PPT：pptx-preview 渲染（解析出 0 页视为失败） */
async function loadPpt(url: string) {
  const buf = await fetchBytes(url);
  const { init } = await import("pptx-preview");
  await nextTick();
  const container = pptContainer.value;
  if (!container) return;
  container.innerHTML = "";
  pptPreviewer?.destroy?.();
  pptPreviewer = init(container, { width: 820, height: 460 });
  await pptPreviewer.preview(buf);
  if (pptPreviewer.slideCount === 0) {
    throw new Error("未解析到幻灯片内容");
  }
}

/**
 * 文本预览：只取文件开头，绝不整包读入内存。
 * - 优先 HTTP Range（后端 Spring 静态资源支持 206），只下载前 TEXT_MAX_BYTES 字节；
 * - 兜底：若服务器忽略 Range 返回 200 整包，改用流式 reader 累计读满 TEXT_MAX_BYTES 即 cancel；
 * - 渲染前按 TEXT_MAX_CHARS 二次截断（超长行 / 压缩 JSON）。
 */
async function loadText(url: string) {
  const res = await fetch(url, {
    headers: { Range: `bytes=0-${TEXT_MAX_BYTES - 1}` },
  });
  if (!res.ok && res.status !== 206) throw new Error(`HTTP ${res.status}`);

  let text: string;
  if (res.status === 206) {
    // Range 生效：只拿到了前 TEXT_MAX_BYTES 字节，整个文件必然大于预览片段
    const buf = new Uint8Array(await res.arrayBuffer());
    // fatal:false —— 边界可能切到半个多字节字符；浏览器 TextDecoder 默认会将其替换为 U+FFFD，
    // 结尾粘连的替换符统一按文本处理，预览可接受
    text = new TextDecoder("utf-8", { fatal: false }).decode(buf);
    // 解析 Content-Range 里的文件总大小判截断（取不到时用行数据 fileSize 兜底）
    const total = (res.headers.get("Content-Range")?.match(/\/(\d+)$/)?.[1] ?? "0");
    const totalSize = Number(total);
    textTruncated.value =
      (props.row?.fileSize ?? 0) > TEXT_MAX_BYTES || (totalSize > 0 && totalSize > TEXT_MAX_BYTES);
  } else {
    // 服务器忽略 Range：流式读取，累计到上限即 cancel，防止整包落内存
    const reader = res.body!.getReader();
    const chunks: Uint8Array[] = [];
    let total = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      total += value.byteLength;
      if (total >= TEXT_MAX_BYTES) {
        await reader.cancel();
        break;
      }
    }
    const buf = new Uint8Array(total);
    let offset = 0;
    for (const c of chunks) {
      buf.set(c, offset);
      offset += c.byteLength;
    }
    text = new TextDecoder("utf-8", { fatal: false }).decode(buf);
    textTruncated.value = total >= TEXT_MAX_BYTES;
  }

  if (text.length > TEXT_MAX_CHARS) {
    text = text.slice(0, TEXT_MAX_CHARS);
    textTruncated.value = true;
  }
  textContent.value = text;
}

const MODE_LABEL: Record<string, string> = {
  pdf: "PDF",
  excel: "Excel",
  word: "Word",
  ppt: "PPT",
  text: "文本",
};

const HEAVY_FILE_LABEL: Record<string, string> = {
  pdf: "PDF",
  excel: "Excel",
  word: "Word",
  ppt: "PPT",
};

/** PDF/Word/PPT/Excel 无法截断渲染，超过阈值先让用户确认，取消则不进渲染避免卡顿 */
async function confirmHeavyFile(m: string): Promise<boolean> {
  const size = props.row?.fileSize ?? 0;
  if (size <= HEAVY_FILE_THRESHOLD) return true;
  const label = HEAVY_FILE_LABEL[m] ?? "";
  try {
    await ElMessageBox.confirm(
      `该文件较大（${formatSize(size)}），${label}在线预览可能较慢或占用较多内存，是否继续？`,
      "大文件预览确认",
      { confirmButtonText: "继续预览", cancelButtonText: "下载查看", type: "warning" },
    );
    return true;
  } catch {
    return false;
  }
}

watch(
  () => [props.modelValue, props.previewUrl, props.previewMode] as const,
  async ([visible]) => {
    if (!visible) return;
    resetState();
    const url = props.previewUrl;
    const m = props.previewMode;
    if (m === "image" || m === "none") return;
    if (!url) {
      renderError.value = "文件地址不可用，请下载后查看";
      return;
    }
    // 大文件（>5MB）先确认；取消则不进加载分支（PDF/Excel/Word/PPT 均为此类）
    if (m !== "text" && !(await confirmHeavyFile(m))) {
      renderError.value = "已取消预览，请下载后查看";
      return;
    }
    busy.value = true;
    try {
      if (m === "pdf") await loadPdf(url);
      else if (m === "excel") await loadExcel(url);
      else if (m === "word") await loadWord(url);
      else if (m === "ppt") await loadPpt(url);
      else if (m === "text") await loadText(url);
    } catch (e) {
      renderError.value = `${MODE_LABEL[m] ?? ""}预览失败：${(e as Error)?.message || "请下载后查看"}`;
    } finally {
      busy.value = false;
    }
  },
  { immediate: true, flush: "post" },
);

function handleClosed() {
  resetState();
  emit("closed");
}
</script>

<style scoped>
.preview-body {
  min-height: 180px;
}

.preview-center {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 180px;
}

.preview-image {
  max-width: 100%;
  max-height: 62vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.preview-pdf {
  width: 100%;
  height: 62vh;
  border: 1px solid var(--admin-border, #f3f4f6);
  border-radius: 8px;
  background: #fff;
}

.preview-excel-table {
  width: 100%;
}

.preview-sheet-tabs {
  margin-bottom: 8px;
}

.preview-word {
  max-height: 62vh;
  overflow: auto;
  border: 1px solid var(--admin-border, #f3f4f6);
  border-radius: 8px;
  padding: 16px;
  background: #f8fafc;
}

.preview-ppt {
  max-height: 62vh;
  overflow: auto;
  display: flex;
  justify-content: center;
}

.preview-text {
  max-height: 62vh;
  overflow: auto;
  margin: 0;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid var(--admin-border, #f3f4f6);
  border-radius: 8px;
  font-size: 13px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  line-height: 1.7;
  white-space: pre-wrap;
  color: var(--admin-text-primary, #1f2937);
}

.preview-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--admin-text-muted, #9ca3af);
}
</style>
