<!-- 权限详情弹窗：打开时先用行数据渲染，异步详情（remark 等）到位后自动补全；失败静默回退行数据 -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="权限详情"
    width="520"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="target">
      <!-- 基本信息 -->
      <div class="pd-basic">
        <span class="pd-basic-icon" :style="{ background: groupColor.gradient }">
          <el-icon :size="18"><Key /></el-icon>
        </span>
        <div class="pd-basic-body">
          <p class="pd-basic-name">{{ detail?.name || target.perm.name }}</p>
          <code class="pd-basic-code">{{ detail?.perms || target.perm.perms }}</code>
          <div class="pd-basic-badges">
            <StatusBadge v-bind="statusBadge" />
            <span class="pd-group-badge">{{ groupName }}</span>
          </div>
        </div>
      </div>

      <!-- 详细字段 -->
      <ul class="pd-fields">
        <li class="pd-field">
          <el-icon class="pd-field-icon" :size="14"><Tickets /></el-icon>
          <span class="pd-field-label">权限 ID</span>
          <span class="pd-field-value">{{ detail?.id ?? target.perm.id }}</span>
        </li>
        <li class="pd-field">
          <el-icon class="pd-field-icon" :size="14"><Key /></el-icon>
          <span class="pd-field-label">权限标识</span>
          <span class="pd-field-value pd-mono">{{ detail?.perms || target.perm.perms }}</span>
        </li>
        <li class="pd-field">
          <el-icon class="pd-field-icon" :size="14"><FolderOpened /></el-icon>
          <span class="pd-field-label">权限分组</span>
          <span class="pd-field-value">{{ groupName }}</span>
        </li>
        <li class="pd-field">
          <el-icon class="pd-field-icon" :size="14"><InfoFilled /></el-icon>
          <span class="pd-field-label">备注</span>
          <span class="pd-field-value">{{ remarkText }}</span>
        </li>
        <li class="pd-field">
          <el-icon class="pd-field-icon" :size="14"><component :is="statusIcon" /></el-icon>
          <span class="pd-field-label">状态</span>
          <span class="pd-field-value"><StatusBadge v-bind="statusBadge" /></span>
        </li>
      </ul>
    </template>

    <template #footer>
      <el-button class="pd-close-btn" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, type Component } from "vue";
import { CircleCheck, CircleClose, FolderOpened, InfoFilled, Key, Tickets } from "@element-plus/icons-vue";
import { StatusBadge } from "@/admin/components";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import { PERMISSION_BADGE_META, type PermissionDetail, type PermissionDetailTarget } from "../usePermissionList";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 行数据（权限项 + 所属分组名） */
    target?: PermissionDetailTarget | null;
    /** 异步详情（未返回/失败时用行数据渲染） */
    detail?: PermissionDetail | null;
    /** 详情加载中（仅影响备注占位文案） */
    loading?: boolean;
  }>(),
  { target: null, detail: null, loading: false },
);
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { itemOf } = useEnumMeta(PERMISSION_BADGE_META);

const groupName = computed(() => props.detail?.groupName || props.target?.groupName || "—");
const groupColor = computed(() => hashColorFor(groupName.value));
const statusKey = computed(() => props.detail?.status ?? props.target?.perm.status ?? 0);
const statusBadge = computed(() => {
  const item = itemOf(statusKey.value);
  return { label: item.label, color: item.color, bg: item.bg, border: item.border };
});
const statusIcon = computed<Component>(() => (statusKey.value === 0 ? CircleCheck : CircleClose));
const remarkText = computed(() => {
  if (props.loading && !props.detail) return "加载中...";
  const remark = props.detail?.remark;
  return remark && remark.trim() ? remark : "—";
});
</script>

<style scoped>
.pd-basic {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  margin-bottom: 18px;
  background: #f8fafc;
  border: 1px solid var(--admin-border);
  border-radius: 12px;
}
.pd-basic-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.pd-basic-body {
  min-width: 0;
}
.pd-basic-name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--admin-text-primary);
}
.pd-basic-code {
  display: block;
  margin-top: 2px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  color: var(--admin-text-secondary);
  word-break: break-all;
}
.pd-basic-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.pd-group-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid var(--admin-border-strong);
  background: #fff;
  font-size: 12px;
  color: var(--admin-text-secondary);
  white-space: nowrap;
}
.pd-fields {
  list-style: none;
  margin: 0 0 4px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pd-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 13px;
}
.pd-field-icon {
  color: var(--admin-text-muted);
  margin-top: 2px;
  flex-shrink: 0;
}
.pd-field-label {
  width: 72px;
  flex-shrink: 0;
  color: var(--admin-text-secondary);
}
.pd-field-value {
  color: var(--admin-text-primary);
  word-break: break-all;
}
.pd-mono {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
}
.pd-close-btn {
  width: 100%;
}
</style>
