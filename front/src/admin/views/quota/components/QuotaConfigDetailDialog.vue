<template>
  <el-dialog
    :model-value="modelValue"
    title="资源配置详情"
    width="560px"
    align-center
    :close-on-click-modal="true"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="row">
      <!-- 头部：资源徽章 + 名称 -->
      <div class="qd-head">
        <span class="resource-badge" :style="resourceBadgeStyle(row.resourceType)">
          <span class="resource-dot" :style="resourceDotStyle(row.resourceType)" />
          {{ resourceNameOf(row) }}
        </span>
      </div>

      <!-- 字段行 -->
      <div class="qd-fields">
        <div class="qd-field">
          <el-icon class="qd-field-icon" :size="14"><DataLine /></el-icon>
          <span class="qd-field-label">资源类型</span>
          <span class="qd-field-value qd-mono">{{ row.resourceType }}</span>
        </div>
        <div class="qd-field">
          <el-icon class="qd-field-icon" :size="14"><Coin /></el-icon>
          <span class="qd-field-label">默认上限（次）</span>
          <span class="qd-field-value">{{ row.defaultLimit ?? "—" }}</span>
        </div>
        <div class="qd-field">
          <el-icon class="qd-field-icon" :size="14"><CircleCheckFilled /></el-icon>
          <span class="qd-field-label">资源可用</span>
          <span class="qd-field-value">
            <StatusBadge v-bind="availableBadge" />
          </span>
        </div>
        <div class="qd-field">
          <el-icon class="qd-field-icon" :size="14"><Lock /></el-icon>
          <span class="qd-field-label">限流状态</span>
          <span class="qd-field-value">
            <StatusBadge v-bind="enabledBadge" />
          </span>
        </div>
        <div class="qd-field">
          <el-icon class="qd-field-icon" :size="14"><Calendar /></el-icon>
          <span class="qd-field-label">计量周期</span>
          <span class="qd-field-value">
            <StatusBadge v-bind="cycleBadge" />
          </span>
        </div>
        <div class="qd-field">
          <el-icon class="qd-field-icon" :size="14"><InfoFilled /></el-icon>
          <span class="qd-field-label">说明</span>
          <span class="qd-field-value">{{ remarkText }}</span>
        </div>
      </div>
    </template>
    <el-empty v-else description="未选择资源配置" :image-size="70" />

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Calendar, CircleCheckFilled, Coin, DataLine, InfoFilled, Lock } from "@element-plus/icons-vue";
import { StatusBadge } from "@/admin/components";
import type { QuotaConfigVO } from "@/admin/api/quota/quota";
import {
  QUOTA_AVAILABLE_META,
  QUOTA_CYCLE_META,
  QUOTA_ENABLED_META,
  QUOTA_RESOURCE_META,
  quotaResourceColor,
  quotaResourceName,
} from "@/admin/constants/quota";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 列表行数据（列表接口即返回全字段，无需异步详情接口） */
    row?: QuotaConfigVO | null;
  }>(),
  { row: null },
);

const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

/** 资源名称：优先后端返回，缺省回退常量元数据 */
function resourceNameOf(row: QuotaConfigVO): string {
  return row.resourceName?.trim() || quotaResourceName(row.resourceType);
}

function resourceMetaOf(type?: number | null) {
  if (type == null) return undefined;
  return QUOTA_RESOURCE_META[type];
}

function resourceBadgeStyle(type?: number | null): Record<string, string> {
  const meta = resourceMetaOf(type);
  return {
    color: meta?.color ?? "#64748b",
    background: meta?.bg ?? "#f1f5f9",
    borderColor: meta?.border ?? "#e2e8f0",
  };
}

function resourceDotStyle(type?: number | null): Record<string, string> {
  return { background: quotaResourceColor(type) };
}

const availableMeta = useEnumMeta(QUOTA_AVAILABLE_META, { fallbackKey: 1 });
const enabledMeta = useEnumMeta(QUOTA_ENABLED_META, { fallbackKey: 0 });
const cycleMeta = useEnumMeta(QUOTA_CYCLE_META, { fallbackKey: 0 });

/** 徽章对象（StatusBadge props） */
function badgeOf(meta: ReturnType<typeof useEnumMeta>, key?: number | null) {
  const item = meta.itemOf(key);
  return { label: item.label, color: item.color, bg: item.bg, border: item.border };
}

const availableBadge = computed(() => badgeOf(availableMeta, props.row?.available));
const enabledBadge = computed(() => badgeOf(enabledMeta, props.row?.enabled));
const cycleBadge = computed(() => badgeOf(cycleMeta, props.row?.cycleType));

const remarkText = computed(() => {
  const remark = props.row?.remark;
  return remark && remark.trim() ? remark : "—";
});
</script>

<style scoped>
.qd-head {
  display: flex;
  align-items: center;
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid var(--admin-border, #e2e8f0);
  border-radius: 12px;
}

.resource-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 12px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.resource-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.qd-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.qd-field {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.qd-field-icon {
  color: var(--admin-text-muted, #94a3b8);
  flex-shrink: 0;
}

.qd-field-label {
  width: 96px;
  flex-shrink: 0;
  color: var(--admin-text-secondary, #64748b);
}

.qd-field-value {
  color: var(--admin-text-primary, #1e293b);
  word-break: break-all;
  line-height: 1.6;
}

.qd-mono {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
}
</style>
