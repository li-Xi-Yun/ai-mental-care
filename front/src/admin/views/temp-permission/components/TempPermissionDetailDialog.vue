<!-- 临时权限明细详情弹窗：纯行数据渲染（零请求，仅右侧「权限明细」表行点击打开） -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="权限明细详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="row" class="td-body">
      <!-- 基本信息块 -->
      <div class="td-head">
        <span class="td-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><Key /></el-icon>
        </span>
        <div class="td-head-main">
          <p class="td-name">{{ row.permissionName || "—" }}</p>
          <StatusBadge :label="statusLabel" :color="status.color" :bg="status.bg" :border="status.border" />
        </div>
      </div>

      <!-- 字段行 -->
      <div class="td-fields">
        <div v-for="field in fields" :key="field.label" class="td-field">
          <el-icon class="td-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="td-field-label">{{ field.label }}</span>
          <StatusBadge v-if="field.badge" :label="statusLabel" :color="status.color" :bg="status.bg" :border="status.border" />
          <span v-else class="td-field-value" :class="{ 'td-mono': field.mono }">{{ field.value || "—" }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button class="td-close-btn" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Component } from "vue";
import { CircleCheck, Clock, CollectionTag, FolderOpened, Key, Memo, Postcard, Tickets, User } from "@element-plus/icons-vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { useHashColor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import { tempPermissionStatusText } from "@/admin/constants/tempPermission";
import { statusMetaOf, type PermItem } from "../useTempPermissionList";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 状态行以徽章渲染 */
  badge?: boolean;
  /** 等宽字体（权限标识等代码类字段） */
  mono?: boolean;
}

const props = defineProps<{
  modelValue: boolean;
  /** 权限明细行数据（纯行数据渲染，无异步详情） */
  row?: PermItem | null;
}>();
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();
const { colorFor } = useHashColor();

const row = computed(() => props.row);
const statusLabel = computed(() => tempPermissionStatusText(props.row?.status));
const status = computed(() => statusMetaOf(props.row?.status));
const color = computed(() => colorFor(props.row?.permissionName ?? "", 5));

const fields = computed<DetailField[]>(() => {
  const r = props.row;
  const list: DetailField[] = [
    { icon: Postcard, label: "记录 ID", value: String(r?.id ?? "") },
    { icon: CollectionTag, label: "权限名称", value: r?.permissionName ?? "" },
    { icon: Tickets, label: "权限标识", value: permsText(r?.perms), mono: true },
    { icon: FolderOpened, label: "所属分组", value: r?.groupName ?? "" },
    { icon: CircleCheck, label: "状态", value: "", badge: true },
    { icon: Clock, label: "生效时间", value: formatTime(r?.startTime) },
    { icon: Clock, label: "过期时间", value: formatTime(r?.expireTime) },
    { icon: User, label: "授权人", value: r?.grantUsername ?? "" },
    { icon: Clock, label: "授权时间", value: formatTime(r?.createdTime) },
    { icon: Memo, label: "授权原因", value: r?.grantReason ?? "" },
  ];
  // 授权原因为空时不展示该行，其余字段始终展示
  return list.filter((f) => f.badge || f.label !== "授权原因" || f.value.trim() !== "");
});

/** 权限标识展示：数组用「、」连接 / 字符串原样 */
function permsText(perms?: string | string[] | null): string {
  if (Array.isArray(perms)) return perms.length ? perms.join("、") : "";
  return perms || "";
}
</script>

<style scoped>
.td-body {
  min-height: 120px;
}
.td-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.td-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.td-head-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}
.td-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
}
.td-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.td-field {
  display: flex;
  align-items: center;
  gap: 10px;
}
.td-field-icon {
  flex-shrink: 0;
  color: var(--admin-text-muted, #9ca3af);
}
.td-field-label {
  width: 84px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.td-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
.td-mono {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #475569;
}
.td-close-btn {
  width: 100%;
}
</style>
