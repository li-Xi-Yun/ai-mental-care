<!-- 版本详情弹窗：打开即用行数据渲染，getScaleVersionDetail 返回后补全（失败静默回退行数据） -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="版本详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="svd-body">
      <!-- 基本信息块 -->
      <div class="svd-head">
        <span class="svd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><Collection /></el-icon>
        </span>
        <div class="svd-head-main">
          <p class="svd-name">{{ versionNo }}</p>
          <p class="svd-sub">{{ description || "—" }}</p>
        </div>
        <StatusBadge v-bind="currentBadge" class="svd-status" />
      </div>

      <!-- 字段行 -->
      <div class="svd-fields">
        <div v-for="field in fields" :key="field.label" class="svd-field">
          <el-icon class="svd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="svd-field-label">{{ field.label }}</span>
          <span class="svd-field-value">{{ field.value || "—" }}</span>
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
import { CircleCheck, Clock, Collection, Document, Grid, Medal, Tickets, User, UserFilled } from "@element-plus/icons-vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import type { AdminScaleVersionVO } from "@/admin/api/scale/types";

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
    row?: AdminScaleVersionVO | null;
    /** 详情接口数据（返回后补全） */
    detail?: AdminScaleVersionVO | null;
    loading?: boolean;
  }>(),
  { row: null, detail: null, loading: false },
);
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();

const versionNo = computed(() => props.detail?.versionNo ?? props.row?.versionNo ?? "—");
const description = computed(() => props.detail?.description ?? props.row?.description ?? "");
const isCurrent = computed(() => props.detail?.isCurrent ?? props.row?.isCurrent ?? false);
const color = computed(() => hashColorFor(versionNo.value, 1));
const currentBadge = computed(() =>
  isCurrent.value
    ? { label: "当前生效", color: "#059669", bg: "#ecfdf5", border: "#a7f3d0" }
    : { label: "历史版本", color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0" },
);

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  const list: DetailField[] = [
    { icon: Collection, label: "版本号", value: d?.versionNo ?? r?.versionNo ?? "" },
    { icon: CircleCheck, label: "当前版本", value: isCurrent.value ? "当前生效" : "历史版本" },
    { icon: Tickets, label: "题数", value: String(d?.questionCount ?? r?.questionCount ?? 0) },
    { icon: Grid, label: "维度数", value: String(d?.dimensionCount ?? r?.dimensionCount ?? 0) },
    { icon: Document, label: "描述", value: d?.description ?? r?.description ?? "", optional: true },
    { icon: Medal, label: "版权信息", value: d?.copyrightInfo ?? r?.copyrightInfo ?? "", optional: true },
    { icon: User, label: "创建人", value: d?.createdByName ?? r?.createdByName ?? "", optional: true },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
    { icon: UserFilled, label: "更新人", value: d?.updatedByName ?? r?.updatedByName ?? "", optional: true },
    { icon: Clock, label: "更新时间", value: formatTime(d?.updatedTime ?? r?.updatedTime) },
  ];
  // 可选字段为空时不展示该行，其余字段始终展示
  return list.filter((f) => !f.optional || f.value.trim() !== "");
});
</script>

<style scoped>
.svd-body {
  min-height: 120px;
}
.svd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.svd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.svd-head-main {
  min-width: 0;
  flex: 1;
}
.svd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.svd-sub {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--admin-text-secondary, #6b7280);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.svd-status {
  flex-shrink: 0;
}
.svd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.svd-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.svd-field-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--admin-text-muted, #9ca3af);
}
.svd-field-label {
  width: 68px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.svd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
</style>
