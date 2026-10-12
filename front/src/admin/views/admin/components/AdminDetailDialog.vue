<!-- 管理员详情弹窗：打开即用行数据渲染，getAdminDetail 返回后补全（失败仅用行数据兜底） -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="管理员详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="!!loading" class="ad-body">
      <!-- 基本信息块 -->
      <div class="ad-head">
        <span class="ad-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><Avatar /></el-icon>
        </span>
        <div class="ad-head-main">
          <p class="ad-name">{{ name }}</p>
          <StatusBadge :label="status.label" :color="status.color" :bg="status.bg" :border="status.border" />
        </div>
      </div>

      <!-- 字段行 -->
      <div class="ad-fields">
        <div v-for="field in fields" :key="field.label" class="ad-field">
          <el-icon class="ad-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="ad-field-label">{{ field.label }}</span>
          <StatusBadge v-if="field.badge" :label="status.label" :color="status.color" :bg="status.bg" :border="status.border" />
          <span v-else class="ad-field-value">{{ field.value || "—" }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button class="ad-close-btn" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Component } from "vue";
import { Avatar, CircleCheck, Clock, Iphone, Message, Postcard, Timer, User, Warning } from "@element-plus/icons-vue";
import { StatusBadge } from "@/admin/components";
import { USER_STATUS_META } from "@/admin/constants/user";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import { useHashColor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import type { AdminDetailVO, AdminPageVO } from "@/admin/api/admin/admin-manage";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 状态行以徽章渲染 */
  badge?: boolean;
  /** 空值行隐藏（封禁信息） */
  optional?: boolean;
}

const props = defineProps<{
  modelValue: boolean;
  /** 列表行数据（即时渲染与兜底） */
  row?: AdminPageVO | null;
  /** 详情接口数据（返回后补全） */
  detail?: AdminDetailVO | null;
  loading?: boolean;
}>();
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();
const { colorFor } = useHashColor();
const adminStatus = useEnumMeta(USER_STATUS_META, { fallbackKey: 3 });

const name = computed(() => props.detail?.username ?? props.row?.username ?? "—");
const colorKey = computed(() => {
  const d = props.detail;
  const r = props.row;
  return d?.username || r?.username || d?.loginAccount || r?.loginAccount || "";
});
const color = computed(() => colorFor(colorKey.value, 2));
const status = computed(() => adminStatus.itemOf(props.detail?.status ?? props.row?.status));

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  const list: DetailField[] = [
    { icon: Postcard, label: "管理员 ID", value: String(d?.id ?? r?.id ?? "") },
    { icon: User, label: "登录账号", value: d?.loginAccount ?? r?.loginAccount ?? "" },
    { icon: Avatar, label: "用户名", value: d?.username ?? r?.username ?? "" },
    { icon: Message, label: "邮箱", value: d?.email ?? r?.email ?? "" },
    { icon: Iphone, label: "手机号", value: d?.mobile ?? r?.mobile ?? "" },
    { icon: CircleCheck, label: "状态", value: "", badge: true },
    { icon: Warning, label: "封禁理由", value: d?.banReason ?? "", optional: true },
    { icon: Timer, label: "封禁结束时间", value: formatTime(d?.banEndTime), optional: true },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
    { icon: Clock, label: "更新时间", value: formatTime(d?.updatedTime ?? r?.updatedTime) },
  ];
  // 封禁信息为空时不展示对应行；其余字段始终展示（无值显示 —）
  return list.filter((f) => !f.optional || f.value.trim() !== "");
});
</script>

<style scoped>
.ad-body {
  min-height: 120px;
}
.ad-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.ad-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.ad-head-main {
  min-width: 0;
}
.ad-name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
}
.ad-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ad-field {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ad-field-icon {
  flex-shrink: 0;
  color: var(--admin-text-muted, #9ca3af);
}
.ad-field-label {
  width: 84px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.ad-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
.ad-close-btn {
  width: 100%;
}
</style>
