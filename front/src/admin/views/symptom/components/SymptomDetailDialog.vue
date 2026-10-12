<!-- 症状详情弹窗：打开即用行数据渲染，getSymptomDictDetail 返回后补全（失败仅用行数据兜底） -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="症状详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="!!loading" class="sd-body">
      <!-- 基本信息块 -->
      <div class="sd-head">
        <span class="sd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><FirstAidKit /></el-icon>
        </span>
        <div class="sd-head-main">
          <p class="sd-name">{{ term }}</p>
          <StatusBadge :label="status.label" :color="status.color" :bg="status.bg" :border="status.border" />
        </div>
      </div>

      <!-- 字段行 -->
      <div class="sd-fields">
        <div v-for="field in fields" :key="field.label" class="sd-field">
          <el-icon class="sd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="sd-field-label">{{ field.label }}</span>
          <StatusBadge v-if="field.badge" :label="status.label" :color="status.color" :bg="status.bg" :border="status.border" />
          <span v-else class="sd-field-value" :class="{ 'sd-mono': field.mono }" :style="field.style">
            {{ field.value || "—" }}
          </span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button class="sd-close-btn" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Component } from "vue";
import { CircleCheck, Clock, CollectionTag, FirstAidKit, FolderOpened, Postcard, Tickets, Warning } from "@element-plus/icons-vue";
import StatusBadge from "@/admin/components/StatusBadge.vue";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import { useHashColor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import { levelColor, severityText } from "@/admin/constants/symptom";
import { SYMPTOM_STATUS_BADGE_META, type SymptomDetail, type SymptomRow } from "../useSymptomList";

interface DetailField {
  icon: Component;
  label: string;
  value: string;
  /** 状态行以徽章渲染 */
  badge?: boolean;
  /** 等宽字体（ID 等标识类字段） */
  mono?: boolean;
  /** 值内联样式（严重等级配色） */
  style?: Record<string, string>;
}

const props = defineProps<{
  modelValue: boolean;
  /** 列表行数据（即时渲染与兜底） */
  row?: SymptomRow | null;
  /** 详情接口数据（返回后补全） */
  detail?: SymptomDetail | null;
  loading?: boolean;
}>();
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();
const { colorFor } = useHashColor();
const symptomStatus = useEnumMeta(SYMPTOM_STATUS_BADGE_META);

const term = computed(() => props.detail?.symptomTerm ?? props.row?.symptomTerm ?? "—");
const color = computed(() => colorFor(term.value, 3));
const status = computed(() => symptomStatus.itemOf(props.detail?.status ?? props.row?.status));

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  const severity = d?.severityDefault ?? r?.severityDefault;
  const synonyms = d?.synonymWords ?? r?.synonymWords ?? [];
  const list: DetailField[] = [
    { icon: Postcard, label: "ID", value: String(d?.id ?? r?.id ?? ""), mono: true },
    { icon: CollectionTag, label: "症状术语", value: d?.symptomTerm ?? r?.symptomTerm ?? "" },
    { icon: FolderOpened, label: "症状大类", value: d?.symptomCategory ?? r?.symptomCategory ?? "" },
    { icon: Tickets, label: "同义词", value: Array.isArray(synonyms) ? synonyms.join("、") : String(synonyms || "") },
    { icon: Warning, label: "默认严重等级", value: severityText(severity), style: { color: levelColor(severity) } },
    { icon: CircleCheck, label: "状态", value: "", badge: true },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
    { icon: Clock, label: "更新时间", value: formatTime(d?.updatedTime ?? r?.updatedTime) },
  ];
  // 同义词为空时不展示该行，其余字段始终展示
  return list.filter((f) => f.badge || f.label !== "同义词" || f.value.trim() !== "");
});
</script>

<style scoped>
.sd-body {
  min-height: 120px;
}
.sd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.sd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.sd-head-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}
.sd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
}
.sd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sd-field {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sd-field-icon {
  flex-shrink: 0;
  color: var(--admin-text-muted, #9ca3af);
}
.sd-field-label {
  width: 84px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.sd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
.sd-mono {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}
.sd-close-btn {
  width: 100%;
}
</style>
