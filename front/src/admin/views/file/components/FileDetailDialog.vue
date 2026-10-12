<!-- 文件详情弹窗：打开即用行数据渲染，getFileDetail 返回后补全（失败仅用行数据兜底） -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="文件详情"
    width="560"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="!!loading" class="fd-body">
      <!-- 头部：类型图标 + 文件名 + 解析/向量状态 -->
      <div class="fd-head">
        <span class="fd-icon" :style="{ background: meta.bg, color: meta.color }">
          <el-icon :size="20"><component :is="meta.icon" /></el-icon>
        </span>
        <div class="fd-head-main">
          <p class="fd-name">{{ name }}</p>
          <div class="fd-badges">
            <StatusBadge :label="statusBadge.label" :color="statusBadge.color" :bg="statusBadge.bg" :border="statusBadge.border" />
            <StatusBadge :label="vectorBadge.label" :color="vectorBadge.color" :bg="vectorBadge.bg" :border="vectorBadge.border" />
          </div>
        </div>
      </div>

      <!-- 字段行 -->
      <div class="fd-fields">
        <div v-for="field in fields" :key="field.label" class="fd-field">
          <el-icon class="fd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="fd-field-label">{{ field.label }}</span>
          <StatusBadge
            v-if="field.badge"
            :label="field.badge.label"
            :color="field.badge.color"
            :bg="field.badge.bg"
            :border="field.badge.border"
          />
          <span v-else class="fd-field-value">{{ field.value || "—" }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('preview', currentRow)">预览</el-button>
      <el-button type="primary" :icon="Download" @click="emit('download', currentRow)">下载</el-button>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Component } from "vue";
import {
  CircleCheck,
  Clock,
  Coin,
  Collection,
  Connection,
  Document,
  Download,
  FolderOpened,
  Position,
  Postcard,
  User,
  Warning,
} from "@element-plus/icons-vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import { fileMeta, KNOWLEDGE_TYPE_MAP } from "@/admin/constants/file";
import { formatSize, type FileRow } from "../useFileList";

interface BadgeStyle {
  label: string;
  color: string;
  bg: string;
  border: string;
}

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 状态行以徽章渲染 */
  badge?: BadgeStyle;
  /** 无值时隐藏该行 */
  optional?: boolean;
}

const props = defineProps<{
  modelValue: boolean;
  /** 列表行数据（即时渲染与兜底） */
  row?: FileRow | null;
  /** 详情接口数据（返回后补全） */
  detail?: FileRow | null;
  loading?: boolean;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "preview", row: FileRow | null): void;
  (e: "download", row: FileRow | null): void;
}>();

const { formatTime } = useTimeFormat();

/** 解析状态徽章配色（对齐列表页 pill 语义色） */
const STATUS_BADGES: Record<number, BadgeStyle> = {
  0: { label: "待解析", color: "#d97706", bg: "#fffbeb", border: "#fde68a" },
  1: { label: "解析中", color: "#2563eb", bg: "#eff6ff", border: "#bfdbfe" },
  2: { label: "解析失败", color: "#dc2626", bg: "#fef2f2", border: "#fecaca" },
  3: { label: "解析完成", color: "#16a34a", bg: "#f0fdf4", border: "#bbf7d0" },
};
const UNKNOWN_BADGE: BadgeStyle = { label: "未知", color: "#94a3b8", bg: "#f1f5f9", border: "#e2e8f0" };
const VECTOR_ENABLED_BADGE: BadgeStyle = { label: "已启用", color: "#16a34a", bg: "#f0fdf4", border: "#bbf7d0" };
const VECTOR_DISABLED_BADGE: BadgeStyle = { label: "未启用", color: "#94a3b8", bg: "#f1f5f9", border: "#e2e8f0" };

const currentRow = computed(() => props.detail ?? props.row ?? null);
const name = computed(() => props.detail?.originalName ?? props.row?.originalName ?? "—");
const meta = computed(() => fileMeta(currentRow.value ?? {}));
const statusBadge = computed(() => {
  const status = props.detail?.status ?? props.row?.status;
  return (status !== undefined && status !== null && STATUS_BADGES[status]) || UNKNOWN_BADGE;
});
const vectorBadge = computed(() =>
  (props.detail?.vectorStatus ?? props.row?.vectorStatus) === 1 ? VECTOR_ENABLED_BADGE : VECTOR_DISABLED_BADGE,
);

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  const knowledgeType = d?.knowledgeType ?? r?.knowledgeType;
  const list: DetailField[] = [
    { icon: Postcard, label: "文件 ID", value: String(d?.id ?? r?.id ?? "") },
    { icon: FolderOpened, label: "分类", value: d?.categoryName ?? r?.categoryName ?? "未分类" },
    { icon: Document, label: "类型", value: (d?.fileSuffix ?? r?.fileSuffix ?? "").toUpperCase() },
    { icon: Coin, label: "大小", value: formatSize(d?.fileSize ?? r?.fileSize) },
    { icon: CircleCheck, label: "解析状态", value: "", badge: statusBadge.value },
    { icon: Warning, label: "失败原因", value: d?.failReason ?? r?.failReason ?? "", optional: true },
    { icon: Connection, label: "向量状态", value: "", badge: vectorBadge.value },
    {
      icon: Collection,
      label: "知识类型",
      value: knowledgeType !== undefined && knowledgeType !== null ? KNOWLEDGE_TYPE_MAP[knowledgeType] ?? "未知" : "",
      optional: true,
    },
    { icon: User, label: "上传人", value: d?.creatorName ?? r?.creatorName ?? "" },
    { icon: Position, label: "来源", value: d?.source ?? r?.source ?? "", optional: true },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
    { icon: Clock, label: "更新时间", value: formatTime(d?.updatedTime ?? r?.updatedTime) },
  ];
  // 失败原因 / 知识类型 / 来源无值时隐藏，其余字段始终展示
  return list.filter((f) => !f.optional || f.value.trim() !== "");
});
</script>

<style scoped>
.fd-body {
  min-height: 120px;
}
.fd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.fd-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
}
.fd-head-main {
  min-width: 0;
}
.fd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
  word-break: break-all;
}
.fd-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}
.fd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.fd-field {
  display: flex;
  align-items: center;
  gap: 10px;
}
.fd-field-icon {
  flex-shrink: 0;
  color: var(--admin-text-muted, #9ca3af);
}
.fd-field-label {
  width: 64px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.fd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
</style>
