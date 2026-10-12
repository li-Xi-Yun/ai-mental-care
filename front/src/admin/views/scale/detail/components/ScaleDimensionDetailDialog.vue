<!-- 维度详情弹窗：纯行数据渲染，零请求 -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="维度详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="sdd-body">
      <!-- 基本信息块 -->
      <div class="sdd-head">
        <span class="sdd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><Collection /></el-icon>
        </span>
        <div class="sdd-head-main">
          <p class="sdd-name">{{ dimName }}</p>
          <p class="sdd-sub">{{ dimCode || "维度" }}</p>
        </div>
      </div>

      <!-- 字段行 -->
      <div class="sdd-fields">
        <div v-for="field in fields" :key="field.label" class="sdd-field">
          <el-icon class="sdd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="sdd-field-label">{{ field.label }}</span>
          <span class="sdd-field-value">{{ field.value || "—" }}</span>
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
import { Clock, Collection, Document, Key, Sort, Tickets } from "@element-plus/icons-vue";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import type { AdminScaleDimensionVO } from "@/admin/api/scale/types";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 空值时隐藏该行 */
  optional?: boolean;
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 列表行数据（即时渲染与兜底） */
    row?: AdminScaleDimensionVO | null;
    /** 详情接口数据（返回后补全） */
    detail?: AdminScaleDimensionVO | null;
    loading?: boolean;
  }>(),
  { row: null, detail: null, loading: false },
);
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();

const dimName = computed(() => props.detail?.dimName ?? props.row?.dimName ?? "—");
const dimCode = computed(() => props.detail?.dimCode ?? props.row?.dimCode ?? "");
const color = computed(() => hashColorFor(dimCode.value || dimName.value, 2));

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  const list: DetailField[] = [
    { icon: Collection, label: "维度名称", value: d?.dimName ?? r?.dimName ?? "" },
    { icon: Key, label: "编码", value: d?.dimCode ?? r?.dimCode ?? "" },
    { icon: Document, label: "描述", value: d?.dimDesc ?? r?.dimDesc ?? "", optional: true },
    { icon: Sort, label: "排序", value: String(d?.sort ?? r?.sort ?? 0) },
    { icon: Tickets, label: "题数", value: String(d?.questionCount ?? r?.questionCount ?? 0) },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
    { icon: Clock, label: "更新时间", value: formatTime(d?.updatedTime ?? r?.updatedTime) },
  ];
  // 描述为空时不展示该行，其余字段始终展示
  return list.filter((f) => !f.optional || f.value.trim() !== "");
});
</script>

<style scoped>
.sdd-body {
  min-height: 120px;
}
.sdd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.sdd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.sdd-head-main {
  min-width: 0;
}
.sdd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
  word-break: break-all;
}
.sdd-sub {
  margin: 3px 0 0;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  color: var(--admin-text-secondary, #6b7280);
}
.sdd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sdd-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.sdd-field-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--admin-text-muted, #9ca3af);
}
.sdd-field-label {
  width: 68px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.sdd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
</style>
