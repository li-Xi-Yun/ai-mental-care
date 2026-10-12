<!-- Skill 详情弹窗：纯行数据渲染（零请求，页面加载期间不产生任何 /api 调用） -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="Skill 详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="kd-body">
      <!-- 基本信息块 -->
      <div class="kd-head">
        <span class="kd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><Grid /></el-icon>
        </span>
        <div class="kd-head-main">
          <p class="kd-name">{{ name }}</p>
          <StatusBadge :label="status.label" :color="status.color" :bg="status.bg" :border="status.border" />
        </div>
      </div>

      <!-- 字段行 -->
      <div class="kd-fields">
        <div v-for="field in fields" :key="field.label" class="kd-field">
          <el-icon class="kd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="kd-field-label">{{ field.label }}</span>
          <StatusBadge v-if="field.badge" :label="status.label" :color="status.color" :bg="status.bg" :border="status.border" />
          <span v-else class="kd-field-value" :class="{ 'kd-multiline': field.multiline }">{{ field.value || "—" }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button class="kd-close-btn" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Component } from "vue";
import { CircleCheck, Clock, CollectionTag, Document, Grid, Postcard } from "@element-plus/icons-vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { useHashColor } from "@/admin/composables/useHashColor";
import { SKILL_STATUS_BADGE_META, type SkillRow } from "../useSkillList";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 状态行以徽章渲染 */
  badge?: boolean;
  /** 多行文本（描述） */
  multiline?: boolean;
}

const props = defineProps<{
  modelValue: boolean;
  /** 列表行数据（纯行数据渲染，无异步详情） */
  row?: SkillRow | null;
}>();
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { colorFor } = useHashColor();

const name = computed(() => props.row?.name ?? "—");
const color = computed(() => colorFor(name.value, 5));
const status = computed(() => SKILL_STATUS_BADGE_META[props.row?.status ?? ""] ?? SKILL_STATUS_BADGE_META["禁用"]);

const fields = computed<DetailField[]>(() => {
  const r = props.row;
  return [
    { icon: Postcard, label: "ID", value: String(r?.id ?? "") },
    { icon: CollectionTag, label: "名称", value: r?.name ?? "" },
    { icon: Grid, label: "类型", value: r?.type ?? "" },
    { icon: CircleCheck, label: "状态", value: "", badge: true },
    { icon: Clock, label: "创建时间", value: r?.createdTime ?? "" },
    { icon: Document, label: "描述", value: r?.description ?? "", multiline: true },
  ];
});
</script>

<style scoped>
.kd-body {
  min-height: 120px;
}
.kd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.kd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.kd-head-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}
.kd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
}
.kd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.kd-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.kd-field-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--admin-text-muted, #9ca3af);
}
.kd-field-label {
  width: 84px;
  flex-shrink: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--admin-text-secondary, #6b7280);
}
.kd-field-value {
  font-size: 13px;
  line-height: 1.6;
  color: #374151;
  word-break: break-all;
}
.kd-multiline {
  white-space: pre-wrap;
}
.kd-close-btn {
  width: 100%;
}
</style>
