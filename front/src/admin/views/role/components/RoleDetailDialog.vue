<!-- 角色详情弹窗：打开即用行数据渲染，getRoleDetail 返回后补全（失败仅用行数据兜底） -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="角色详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="!!loading" class="rd-body">
      <!-- 基本信息块 -->
      <div class="rd-head">
        <span class="rd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><Avatar /></el-icon>
        </span>
        <div class="rd-head-main">
          <p class="rd-name">{{ name }}</p>
          <p class="rd-rolekey">{{ roleKey || "—" }}</p>
        </div>
      </div>

      <!-- 字段行 -->
      <div class="rd-fields">
        <div v-for="field in fields" :key="field.label" class="rd-field">
          <el-icon class="rd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="rd-field-label">{{ field.label }}</span>
          <StatusBadge v-if="field.badge" :label="status.label" :color="status.color" :bg="status.bg" :border="status.border" />
          <span v-else class="rd-field-value">{{ field.value || "—" }}</span>
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
import { Avatar, CircleCheck, Clock, Document, Key, Postcard } from "@element-plus/icons-vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import { useHashColor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import { ROLE_STATUS_STYLE_META, type RoleDetail, type RoleItem } from "../useRoleList";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 状态行以徽章渲染 */
  badge?: boolean;
}

const props = defineProps<{
  modelValue: boolean;
  /** 列表行数据（即时渲染与兜底） */
  role?: RoleItem | null;
  /** 详情接口数据（返回后补全） */
  detail?: RoleDetail | null;
  loading?: boolean;
}>();
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();
const { colorFor } = useHashColor();
const roleStatus = useEnumMeta(ROLE_STATUS_STYLE_META);

const name = computed(() => props.detail?.name ?? props.role?.name ?? "—");
const roleKey = computed(() => props.detail?.roleKey ?? props.role?.roleKey ?? "");
const color = computed(() => colorFor(roleKey.value, 2));
const status = computed(() => roleStatus.itemOf(props.detail?.status ?? props.role?.status));

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.role;
  const list: DetailField[] = [
    { icon: Postcard, label: "角色 ID", value: String(d?.id ?? r?.id ?? "") },
    { icon: Key, label: "角色标识", value: d?.roleKey ?? r?.roleKey ?? "" },
    { icon: Document, label: "备注", value: d?.remark ?? "" },
    { icon: CircleCheck, label: "状态", value: "", badge: true },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
    { icon: Clock, label: "更新时间", value: formatTime(d?.updatedTime ?? r?.updatedTime) },
  ];
  // 备注为空时不展示该行，其余字段始终展示
  return list.filter((f) => f.badge || f.label !== "备注" || f.value.trim() !== "");
});
</script>

<style scoped>
.rd-body {
  min-height: 120px;
}
.rd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.rd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.rd-head-main {
  min-width: 0;
}
.rd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
}
.rd-rolekey {
  margin: 3px 0 0;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  color: var(--admin-text-secondary, #6b7280);
}
.rd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rd-field {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rd-field-icon {
  flex-shrink: 0;
  color: var(--admin-text-muted, #9ca3af);
}
.rd-field-label {
  width: 64px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.rd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
</style>
