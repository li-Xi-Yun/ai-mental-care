<!-- 选项模板详情弹窗：纯行数据渲染，零请求；含完整选项明细表格 -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="选项模板详情"
    width="520"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="otd-body">
      <!-- 基本信息块 -->
      <div class="otd-head">
        <span class="otd-badge" :style="{ background: color.gradient }">
          <el-icon :size="18"><Files /></el-icon>
        </span>
        <div class="otd-head-main">
          <p class="otd-name">{{ templateName }}</p>
          <p class="otd-sub">{{ items.length }} 项选项</p>
        </div>
      </div>

      <!-- 字段行 -->
      <div class="otd-fields">
        <div v-for="field in fields" :key="field.label" class="otd-field">
          <el-icon class="otd-field-icon" :size="14"><component :is="field.icon" /></el-icon>
          <span class="otd-field-label">{{ field.label }}</span>
          <span class="otd-field-value">{{ field.value || "—" }}</span>
        </div>
      </div>

      <!-- 完整选项表格 -->
      <div class="otd-items">
        <div class="otd-items-head">
          <span class="otd-items-title">选项明细</span>
          <span class="otd-items-count">{{ items.length }} 项</span>
        </div>
        <el-table v-if="items.length" :data="items" size="small" class="otd-item-table">
          <el-table-column label="序号" width="56" align="center">
            <template #default="{ $index }">
              <span class="otd-index">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="optionText" label="选项文本" min-width="160" show-overflow-tooltip />
          <el-table-column label="分值" width="70" align="center">
            <template #default="{ row: item }">
              <span class="otd-score">{{ item.score ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="排序" width="70" align="center">
            <template #default="{ row: item }">
              <span class="otd-score">{{ item.sort ?? 0 }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="otd-items-empty">暂无选项</div>
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
import { Clock, Document, Files, Tickets } from "@element-plus/icons-vue";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { useTimeFormat } from "@/admin/composables/useTimeFormat";
import type {
  AdminScaleOptionTemplateItemVO,
  AdminScaleOptionTemplateVO,
} from "@/admin/api/scale/option-template";

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
    row?: AdminScaleOptionTemplateVO | null;
    /** 详情接口数据（返回后补全） */
    detail?: AdminScaleOptionTemplateVO | null;
    loading?: boolean;
  }>(),
  { row: null, detail: null, loading: false },
);
const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

const { formatTime } = useTimeFormat();

const templateName = computed(() => props.detail?.templateName ?? props.row?.templateName ?? "—");
const items = computed<AdminScaleOptionTemplateItemVO[]>(() => props.detail?.items ?? props.row?.items ?? []);
const color = computed(() => hashColorFor(templateName.value, 6));

const fields = computed<DetailField[]>(() => {
  const d = props.detail;
  const r = props.row;
  const list: DetailField[] = [
    { icon: Files, label: "模板名称", value: d?.templateName ?? r?.templateName ?? "" },
    { icon: Tickets, label: "项数", value: String(items.value.length) },
    { icon: Document, label: "描述", value: d?.templateDesc ?? r?.templateDesc ?? "", optional: true },
    { icon: Clock, label: "创建时间", value: formatTime(d?.createdTime ?? r?.createdTime) },
  ];
  // 描述为空时不展示该行，其余字段始终展示
  return list.filter((f) => !f.optional || f.value.trim() !== "");
});
</script>

<style scoped>
.otd-body {
  min-height: 120px;
}
.otd-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f9fafb;
  border-radius: 12px;
}
.otd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  color: #fff;
}
.otd-head-main {
  min-width: 0;
}
.otd-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
  word-break: break-all;
}
.otd-sub {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--admin-text-secondary, #6b7280);
}
.otd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.otd-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.otd-field-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--admin-text-muted, #9ca3af);
}
.otd-field-label {
  width: 68px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-text-secondary, #6b7280);
}
.otd-field-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}

/* ==================== 选项明细表格 ==================== */
.otd-items {
  margin-top: 18px;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  overflow: hidden;
}
.otd-items-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #eef1f6;
}
.otd-items-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.otd-items-count {
  font-size: 11px;
  color: #94a3b8;
}
.otd-item-table {
  width: 100%;
}
.otd-index,
.otd-score {
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}
.otd-items-empty {
  padding: 18px;
  text-align: center;
  font-size: 12px;
  color: #cbd5e1;
}
</style>
