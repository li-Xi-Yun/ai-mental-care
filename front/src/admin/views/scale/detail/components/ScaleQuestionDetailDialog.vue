<!-- 题目详情弹窗：打开即用行数据渲染，getScaleQuestionDetail 返回后补全（失败静默回退行数据） -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="题目详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="sqd-body">
      <!-- 基本信息块 -->
      <div class="sqd-head">
        <span class="sqd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><List /></el-icon>
        </span>
        <div class="sqd-head-main">
          <p class="sqd-name">{{ title }}</p>
          <p class="sqd-sub">{{ headSub }}</p>
        </div>
        <StatusBadge v-bind="requiredBadge" class="sqd-status" />
      </div>

      <!-- 字段行 -->
      <div class="sqd-fields">
        <div v-for="field in fields" :key="field.label" class="sqd-field">
          <el-icon class="sqd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="sqd-field-label">{{ field.label }}</span>
          <StatusBadge v-if="field.badge" v-bind="requiredBadge" />
          <span v-else class="sqd-field-value">{{ field.value || "—" }}</span>
        </div>
      </div>

      <!-- 选项表格 -->
      <div class="sqd-options">
        <div class="sqd-options-head">
          <span class="sqd-options-title">选项</span>
          <span class="sqd-options-count">{{ options.length }} 项</span>
        </div>
        <el-table v-if="options.length" :data="options" size="small" class="sqd-opt-table">
          <el-table-column label="序号" width="56" align="center">
            <template #default="{ $index }">
              <span class="sqd-index">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="optionText" label="选项文本" min-width="180" show-overflow-tooltip />
          <el-table-column label="分值" width="70" align="center">
            <template #default="{ row: opt }">
              <span class="sqd-score">{{ opt.score ?? 0 }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="sqd-options-empty">暂无选项</div>
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
import { CircleCheck, Grid, Histogram, List, Sort } from "@element-plus/icons-vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { hashColorFor } from "@/admin/composables/useHashColor";
import type { AdminScaleOptionVO, AdminScaleQuestionVO } from "@/admin/api/scale/types";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 必答状态行以徽章渲染 */
  badge?: boolean;
}

/** 题型文字映射（与列表页保持一致） */
const QUESTION_TYPE_TEXT: Record<number, string> = { 1: "单选", 2: "多选", 3: "填空" };
/** 计分方式文字映射（与列表页保持一致） */
const SCORE_TYPE_TEXT: Record<number, string> = { 0: "不计分", 1: "正向计分", 2: "反向计分" };

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 列表行数据（即时渲染与兜底） */
    row?: AdminScaleQuestionVO | null;
    /** 详情接口数据（返回后补全） */
    detail?: AdminScaleQuestionVO | null;
    loading?: boolean;
  }>(),
  { row: null, detail: null, loading: false },
);
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const title = computed(() => props.detail?.title ?? props.row?.title ?? "—");
const questionType = computed(() => props.detail?.questionType ?? props.row?.questionType);
const dimensionName = computed(() => props.detail?.dimensionName ?? props.row?.dimensionName ?? "");
const required = computed(() => props.detail?.required ?? props.row?.required ?? 0);
const options = computed<AdminScaleOptionVO[]>(() => props.detail?.options ?? props.row?.options ?? []);

const color = computed(() => hashColorFor(title.value, 3));
const questionTypeText = computed(() => QUESTION_TYPE_TEXT[questionType.value ?? -1] ?? "未知");
const scoreTypeText = computed(() => SCORE_TYPE_TEXT[props.detail?.scoreType ?? props.row?.scoreType ?? -1] ?? "—");
const headSub = computed(() =>
  dimensionName.value ? `${questionTypeText.value} · ${dimensionName.value}` : questionTypeText.value,
);
const requiredBadge = computed(() =>
  required.value === 1
    ? { label: "必答", color: "#dc2626", bg: "#fef2f2", border: "#fecaca" }
    : { label: "选答", color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0" },
);

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  return [
    { icon: List, label: "题型", value: QUESTION_TYPE_TEXT[d?.questionType ?? r?.questionType ?? -1] ?? "未知" },
    { icon: Grid, label: "所属维度", value: d?.dimensionName ?? r?.dimensionName ?? "" },
    { icon: Histogram, label: "计分方式", value: scoreTypeText.value },
    { icon: CircleCheck, label: "是否必答", value: "", badge: true },
    { icon: Sort, label: "排序", value: String(d?.sort ?? r?.sort ?? 0) },
  ];
});
</script>

<style scoped>
.sqd-body {
  min-height: 120px;
}
.sqd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.sqd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.sqd-head-main {
  min-width: 0;
  flex: 1;
}
.sqd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
  word-break: break-all;
}
.sqd-sub {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--admin-text-secondary, #6b7280);
}
.sqd-status {
  flex-shrink: 0;
}
.sqd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sqd-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.sqd-field-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--admin-text-muted, #9ca3af);
}
.sqd-field-label {
  width: 68px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.sqd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}

/* ==================== 选项表格 ==================== */
.sqd-options {
  margin-top: 18px;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  overflow: hidden;
}
.sqd-options-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #eef1f6;
}
.sqd-options-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.sqd-options-count {
  font-size: 11px;
  color: #94a3b8;
}
.sqd-opt-table {
  width: 100%;
}
.sqd-index {
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}
.sqd-score {
  font-size: 12px;
  color: #475569;
  font-variant-numeric: tabular-nums;
}
.sqd-options-empty {
  padding: 18px;
  text-align: center;
  font-size: 12px;
  color: #cbd5e1;
}
</style>
