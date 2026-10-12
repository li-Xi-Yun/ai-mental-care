<!-- 结果规则详情弹窗：纯行数据渲染，零请求；分组标题（维度名）由列表页传入 -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="结果规则详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="rrd-body">
      <!-- 基本信息块 -->
      <div class="rrd-head">
        <span class="rrd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><TrendCharts /></el-icon>
        </span>
        <div class="rrd-head-main">
          <p class="rrd-name">{{ groupTitle || "结果规则" }}</p>
          <p class="rrd-sub">{{ scoreRange }}</p>
        </div>
        <StatusBadge v-bind="riskBadge" class="rrd-status" />
      </div>

      <!-- 字段行 -->
      <div class="rrd-fields">
        <div v-for="field in fields" :key="field.label" class="rrd-field">
          <el-icon class="rrd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="rrd-field-label">{{ field.label }}</span>
          <StatusBadge v-if="field.badge" v-bind="riskBadge" />
          <span v-else class="rrd-field-value">{{ field.value || "—" }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Component } from "vue";
import { Clock, Document, Grid, Histogram, Sort, TrendCharts, Warning } from "@element-plus/icons-vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import type { AdminScaleResultRuleVO } from "@/admin/api/scale/types";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 风险等级行以徽章渲染 */
  badge?: boolean;
}

/** 风险等级映射（与列表页保持一致） */
const RISK_LEVELS = [
  { label: "正常", value: 0, color: "#059669", bg: "#ecfdf5", border: "#a7f3d0" },
  { label: "轻度", value: 1, color: "#4f46e5", bg: "#eef2ff", border: "#c7d2fe" },
  { label: "中度", value: 2, color: "#d97706", bg: "#fffbeb", border: "#fde68a" },
  { label: "重度", value: 3, color: "#dc2626", bg: "#fef2f2", border: "#fecaca" },
] as const;

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 列表行数据（即时渲染与兜底） */
    row?: AdminScaleResultRuleVO | null;
    /** 详情接口数据（返回后补全） */
    detail?: AdminScaleResultRuleVO | null;
    loading?: boolean;
    /** 所属分组标题（维度名 / 总分规则） */
    groupTitle?: string;
  }>(),
  { row: null, detail: null, loading: false, groupTitle: "" },
);
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();

function formatScore(v?: number | null): string {
  return v == null ? "—" : String(v);
}

const minScore = computed(() => props.detail?.minScore ?? props.row?.minScore);
const maxScore = computed(() => props.detail?.maxScore ?? props.row?.maxScore);
const riskLevel = computed(() => props.detail?.riskLevel ?? props.row?.riskLevel ?? 0);
const scoreRange = computed(() => `${formatScore(minScore.value)} ~ ${formatScore(maxScore.value)}`);
const color = computed(() => hashColorFor(props.groupTitle, 4));
const riskBadge = computed(() => {
  const item = RISK_LEVELS.find((o) => o.value === riskLevel.value) ?? RISK_LEVELS[0];
  return { label: item.label, color: item.color, bg: item.bg, border: item.border };
});

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  return [
    { icon: Grid, label: "所属维度", value: props.groupTitle },
    { icon: Histogram, label: "分数区间", value: scoreRange.value },
    { icon: Document, label: "结果文案", value: d?.resultText ?? r?.resultText ?? "" },
    { icon: Warning, label: "风险等级", value: "", badge: true },
    { icon: Sort, label: "排序", value: String(d?.sort ?? r?.sort ?? 0) },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
    { icon: Clock, label: "更新时间", value: formatTime(d?.updatedTime ?? r?.updatedTime) },
  ];
});
</script>

<style scoped>
.rrd-body {
  min-height: 120px;
}
.rrd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.rrd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.rrd-head-main {
  min-width: 0;
  flex: 1;
}
.rrd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
  word-break: break-all;
}
.rrd-sub {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--admin-text-secondary, #6b7280);
  font-variant-numeric: tabular-nums;
}
.rrd-status {
  flex-shrink: 0;
}
.rrd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rrd-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.rrd-field-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--admin-text-muted, #9ca3af);
}
.rrd-field-label {
  width: 68px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.rrd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
</style>
