/**
 * 文件管理模块常量与展示元数据
 *
 * 从 views/file/index.vue 的 scoped 常量中抽离，供文件管理页与文件向量页复用。
 * 说明：shared/api/config.ts 中的 FILE_CATEGORIES / FILE_TYPES 为静态演示选项，
 * 与页面实际使用的动态分类树接口及常见后缀列表不一致，故未复用。
 */
import {
  Box,
  DataLine,
  Document,
  Headset,
  Link,
  Monitor,
  Notebook,
  Picture,
  VideoPlay,
} from "@element-plus/icons-vue";

/** 文件解析状态（FileVO.status） */
export const STATUS_MAP = [
  { label: "待解析", value: 0 },
  { label: "解析中", value: 1 },
  { label: "解析失败", value: 2 },
  { label: "解析完成", value: 3 },
] as const;

/** 向量库知识类型 */
export const KNOWLEDGE_TYPE_MAP: Record<number, string> = {
  0: "无",
  1: "症状库",
  2: "诊断标准库",
  3: "干预方案库",
};

/** 常见文件后缀 */
export const COMMON_FILE_SUFFIXES = [
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

export const PDF_SUFFIXES = ["pdf"];
export const OFFICE_SUFFIXES = ["doc", "docx", "xls", "xlsx", "ppt", "pptx"];
/** 预览分支用的细分后缀集 */
export const EXCEL_SUFFIXES = ["xls", "xlsx"];
export const WORD_SUFFIXES = ["doc", "docx"];
export const PPT_SUFFIXES = ["ppt", "pptx"];
export const IMAGE_SUFFIXES = ["jpg", "jpeg", "png", "gif", "webp", "svg", "bmp"];
export const AUDIO_SUFFIXES = ["mp3", "wav", "m4a", "ogg", "flac"];
export const VIDEO_SUFFIXES = ["mp4", "avi", "mov", "mkv", "webm", "flv"];
export const TEXT_SUFFIXES = ["txt", "md", "csv", "json", "log", "xml", "yml", "yaml", "html", "css", "js", "ts", "vue", "java"];
export const ZIP_SUFFIXES = ["zip", "rar", "7z", "tar", "gz"];

/** 按后缀映射的彩色图标 */
export interface FileIconMeta {
  icon: unknown;
  bg: string;
  color: string;
}

export const FILE_ICONS: Record<string, FileIconMeta> = {
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

export function fileMeta(row: any): FileIconMeta {
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

export type TagType = "primary" | "success" | "info" | "warning" | "danger";

/** 分类标签配色（按名称散列取色，稳定不闪动） */
export function categoryTagType(name?: string): TagType {
  const palette: TagType[] = ["primary", "success", "info", "warning", "danger"];
  if (!name) return "info";
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 131 + name.charCodeAt(i)) % 10007;
  }
  return palette[hash % palette.length];
}

/** 解析状态徽章 */
export function statusMeta(status?: number) {
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

/** 分类树字段映射 */
export const categoryTreeProps = {
  children: "children",
  label: "categoryName",
} as const;
