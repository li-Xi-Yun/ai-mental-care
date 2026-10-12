<!-- 跳题规则详情弹窗：纯行数据渲染，零请求；源题 → 源选项 → 目标题 链路可视化 -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="跳题规则详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="brd-body">
      <!-- 基本信息块 -->
      <div class="brd-head">
        <span class="brd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><Connection /></el-icon>
        </span>
        <div class="brd-head-main">
          <p class="brd-name">{{ sourceQuestionTitle || "跳题规则" }}</p>
          <p class="brd-sub">{{ headSub }}</p>
        </div>
      </div>

      <!-- 链路可视化：源题 → 源选项 → 目标题 -->
      <div class="brd-chain">
        <div class="brd-node">
          <span class="brd-node-label">源题</span>
          <span class="brd-node-value">{{ sourceQuestionTitle || "—" }}</span>
        </div>
        <el-icon class="brd-arrow" :size="16"><ArrowRight /></el-icon>
        <div class="brd-node">
          <span class="brd-node-label">源选项</span>
          <span class="brd-node-value">{{ sourceOptionText || "—" }}</span>
        </div>
        <el-icon class="brd-arrow" :size="16"><ArrowRight /></el-icon>
        <div class="brd-node">
          <span class="brd-node-label">目标题</span>
          <span v-if="targetQuestionTitle" class="brd-node-value">{{ targetQuestionTitle }}</span>
          <span v-else class="brd-end">结束测评</span>
        </div>
      </div>

      <!-- 字段行 -->
      <div class="brd-fields">
        <div v-for="field in fields" :key="field.label" class="brd-field">
          <el-icon class="brd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="brd-field-label">{{ field.label }}</span>
          <span class="brd-field-value">{{ field.value || "—" }}</span>
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
import { ArrowRight, Clock, Connection, Tickets } from "@element-plus/icons-vue";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import type { AdminScaleBranchRuleVO } from "@/admin/api/scale/types";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 列表行数据（即时渲染与兜底） */
    row?: AdminScaleBranchRuleVO | null;
    /** 详情接口数据（返回后补全） */
    detail?: AdminScaleBranchRuleVO | null;
    loading?: boolean;
  }>(),
  { row: null, detail: null, loading: false },
);
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();

const sourceQuestionTitle = computed(() => props.detail?.sourceQuestionTitle ?? props.row?.sourceQuestionTitle ?? "");
const sourceOptionText = computed(() => props.detail?.sourceOptionText ?? props.row?.sourceOptionText ?? "");
const targetQuestionTitle = computed(() => props.detail?.targetQuestionTitle ?? props.row?.targetQuestionTitle ?? "");
const color = computed(() => hashColorFor(sourceQuestionTitle.value, 5));
const headSub = computed(() => (sourceOptionText.value ? `「${sourceOptionText.value}」触发` : "跳题规则"));

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  return [
    { icon: Tickets, label: "规则 ID", value: String(d?.id ?? r?.id ?? "") },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
    { icon: Clock, label: "更新时间", value: formatTime(d?.updatedTime ?? r?.updatedTime) },
  ];
});
</script>

<style scoped>
.brd-body {
  min-height: 120px;
}
.brd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.brd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.brd-head-main {
  min-width: 0;
}
.brd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
  word-break: break-all;
}
.brd-sub {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--admin-text-secondary, #6b7280);
}

/* ==================== 链路可视化 ==================== */
.brd-chain {
  display: flex;
  align-items: stretch;
  gap: 6px;
  margin-bottom: 18px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #eef1f6;
  border-radius: 10px;
}
.brd-node {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.brd-node-label {
  font-size: 11px;
  color: #94a3b8;
}
.brd-node-value {
  font-size: 12px;
  color: #1e293b;
  line-height: 1.5;
  word-break: break-all;
}
.brd-end {
  display: inline-block;
  align-self: flex-start;
  padding: 1px 8px;
  border-radius: 6px;
  background: #fef2f2;
  color: #ef4444;
  font-size: 12px;
  border: 1px solid #fecaca;
}
.brd-arrow {
  flex-shrink: 0;
  align-self: center;
  color: #cbd5e1;
}

/* ==================== 字段行 ==================== */
.brd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.brd-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.brd-field-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--admin-text-muted, #9ca3af);
}
.brd-field-label {
  width: 68px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.brd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
</style>
