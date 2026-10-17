<template>
  <el-dialog
    :model-value="modelValue"
    title="用户配额总览"
    width="720px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <div v-loading="loading" class="quota-dialog-body">
      <template v-if="target">
        <div class="target-info">
          <span class="target-name">{{ target.username || "—" }}</span>
          <span class="target-account">{{ target.loginAccount || "" }}</span>
          <span class="target-id">ID：{{ target.id }}</span>
        </div>

        <el-table
          v-loading="loading"
          :data="quotaList"
          row-key="resourceType"
          class="quota-table"
          :row-class-name="quotaRowClass"
        >
          <el-table-column label="资源类型" width="170">
            <template #default="{ row }">
              <span class="resource-badge" :style="resourceBadgeStyle((row as QuotaRow).resourceType)">
                <span class="resource-dot" :style="resourceDotStyle((row as QuotaRow).resourceType)" />
                {{ resourceNameOf(row as QuotaRow) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="已用次数" width="100" align="center">
            <template #default="{ row }">
              <span class="num-text">{{ usedCountOf(row as QuotaRow) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="生效上限" width="120" align="center">
            <template #default="{ row }">
              <span :class="effectiveLimitClass(row as QuotaRow)">{{ effectiveLimitText(row as QuotaRow) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="剩余次数" width="120" align="center">
            <template #default="{ row }">
              <span :class="remainingClass(row as QuotaRow)">{{ remainingText(row as QuotaRow) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="豁免状态" width="110" align="center">
            <template #default="{ row }">
              <span class="status-badge" :style="unlimitedBadgeStyle((row as QuotaRow).unlimited)">
                <span class="status-dot" :style="unlimitedDotStyle((row as QuotaRow).unlimited)" />
                {{ unlimitedLabel((row as QuotaRow).unlimited) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="96" align="center">
            <template #default="{ row }">
              <el-button
                text
                size="small"
                class="op-btn op-edit"
                :disabled="(row as QuotaRow).available === 0"
                @click.stop="openAdjust(row as QuotaRow)"
              >
                调整
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无配额数据" :image-size="70" />
          </template>
        </el-table>

        <div v-if="hasDisabled" class="disabled-tip">
          <el-icon class="tip-icon"><WarningFilled /></el-icon>
          标记为「当前不可用」的资源已停用，其配额列显示为灰色。
        </div>
      </template>
      <el-empty v-else description="未选择用户" :image-size="70" />
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关 闭</el-button>
    </template>

    <!-- ==================== 单资源调整弹窗 ==================== -->
    <QuotaAdjustDialog
      v-model="adjustVisible"
      :userId="userId"
      :resource-type="adjustTarget?.resourceType ?? null"
      :row="adjustTarget"
      :submitting="adjustSubmitting"
      @submit="handleAdjustSubmit"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { SysUserPageVO } from "@/admin/api/admin/user-manage";
import { getUserQuotaList, adjustUserQuota } from "@/admin/api/quota/quota";
import type { QuotaUserVO, QuotaUserAdjustDTO } from "@/admin/api/quota/quota";
import { QUOTA_RESOURCE_META, quotaResourceName, quotaResourceColor, QUOTA_UNLIMITED_META } from "@/admin/constants/quota";
import { useEnumMeta } from "@/admin/composables/useEnumMeta";
import QuotaAdjustDialog from "./QuotaAdjustDialog.vue";

/** 表格 slot 的行数据（el-table :data 已置为 QuotaUserVO[]，模板 slot 参数类型放松为 any 便于访问） */
type QuotaRow = QuotaUserVO;

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    loading?: boolean;
    target?: SysUserPageVO | null;
  }>(),
  { loading: false, target: null },
);

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "update:loading", v: boolean): void;
  (e: "closed"): void;
}>();

// ==================== 数据拉取 ====================
const quotaList = ref<QuotaUserVO[]>([]);
const fetchError = ref(false);

const userId = computed(() => {
  const id = props.target?.id;
  return id === undefined || id === null ? null : String(id);
});

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return;
    fetchError.value = false;
    quotaList.value = [];
    emit("update:loading", true);
    try {
      if (userId.value !== null) {
        const res = await getUserQuotaList(userId.value);
        quotaList.value = res.data?.data ?? [];
      }
    } catch {
      fetchError.value = true;
      ElMessage.error("加载用户配额失败，请稍后重试");
    } finally {
      emit("update:loading", false);
    }
  },
);

/** 调整成功后由父级（本弹窗）刷新总览数据 */
async function refreshQuota() {
  if (userId.value === null) return;
  try {
    const res = await getUserQuotaList(userId.value);
    quotaList.value = res.data?.data ?? [];
  } catch {
    ElMessage.error("刷新用户配额失败，请稍后重试");
  }
}

// ==================== 展示工具 ====================
const { labelOf: unlimitedLabel, styleOf: unlimitedBadgeStyle, dotStyleOf: unlimitedDotStyle } = useEnumMeta(
  QUOTA_UNLIMITED_META,
  { fallbackKey: 0 },
);

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

/** 资源名称：优先后端返回，缺省回退常量元数据 */
function resourceNameOf(row: QuotaUserVO): string {
  return row.resourceName?.trim() || quotaResourceName(row.resourceType);
}

function usedCountOf(row: QuotaUserVO): string {
  return row.usedCount != null ? String(row.usedCount) : "—";
}

/** 生效上限展示规则 */
function effectiveLimitText(row: QuotaUserVO): string {
  if (row.available === 0) return "—";
  if (row.effectiveLimit === null || row.effectiveLimit === undefined) {
    if (row.unlimited === 1) return "不限";
    return "已停用";
  }
  return String(row.effectiveLimit);
}

function effectiveLimitClass(row: QuotaUserVO): string {
  if (row.available === 0) return "num-text num-muted";
  if ((row.effectiveLimit === null || row.effectiveLimit === undefined) && row.unlimited === 1) {
    return "num-text num-unlimited";
  }
  if ((row.effectiveLimit === null || row.effectiveLimit === undefined) && row.unlimited !== 1) {
    return "num-text num-danger";
  }
  return "num-text";
}

/** 剩余次数展示规则 */
function remainingText(row: QuotaUserVO): string {
  if (row.available === 0) return "—";
  if (row.unlimited === 1) return "不限";
  if (row.remaining === null || row.remaining === undefined) {
    return row.effectiveLimit === null || row.effectiveLimit === undefined ? "—" : "已用尽";
  }
  return `剩余 ${row.remaining} 次`;
}

function remainingClass(row: QuotaUserVO): string {
  if (row.available === 0) return "num-text num-muted";
  if (row.unlimited === 1) return "num-text num-unlimited";
  if (row.remaining === 0) return "num-text num-exhausted";
  return "num-text";
}

/** 不可用的资源整行变灰 */
function quotaRowClass(row: QuotaUserVO): string {
  return row.available === 0 ? "quota-row-disabled" : "";
}

const hasDisabled = computed(() => quotaList.value.some((r) => r.available === 0));
// ==================== 调整弹窗 ====================
const adjustVisible = ref(false);
const adjustTarget = ref<QuotaUserVO | null>(null);
const adjustSubmitting = ref(false);

function openAdjust(row: QuotaUserVO) {
  adjustTarget.value = row;
  adjustVisible.value = true;
}

/** 子弹窗校验通过后回传载荷：在此提交并刷新总览（QuotaAdjustDialog 只负责表单与 emit） */
async function handleAdjustSubmit(payload: QuotaUserAdjustDTO) {
  if (userId.value === null || !adjustTarget.value) return;
  adjustSubmitting.value = true;
  try {
    await adjustUserQuota(userId.value, payload.resourceType, payload);
    ElMessage.success("配额调整成功");
    adjustVisible.value = false;
    await refreshQuota();
  } catch {
    ElMessage.error("配额调整失败，请稍后重试");
  } finally {
    adjustSubmitting.value = false;
  }
}

// ==================== 弹窗关闭重置 ====================
function handleClosed() {
  quotaList.value = [];
  fetchError.value = false;
  adjustVisible.value = false;
  adjustTarget.value = null;
}
</script>

<style scoped>
.quota-dialog-body {
  min-height: 120px;
}

/* 当前用户概要 */
.target-info {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 14px;
}

.target-name {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.target-account {
  font-size: 12px;
  color: #94a3b8;
}

.target-id {
  margin-left: auto;
  font-size: 11.5px;
  color: #cbd5e1;
  font-variant-numeric: tabular-nums;
}

/* 资源类型徽章 */
.resource-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.resource-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* 数字列 */
.num-text {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.num-muted {
  color: #cbd5e1;
  font-weight: 400;
}

.num-unlimited {
  color: #10b981;
}

.num-danger {
  color: #ef4444;
}

.num-exhausted {
  color: #ef4444;
  font-weight: 700;
}

/* 停用整行变灰 */
:deep(.quota-table .quota-row-disabled td.el-table__cell) {
  color: #cbd5e1;
  background: #f9fafb;
}

:deep(.quota-table .quota-row-disabled .num-text) {
  color: #cbd5e1;
  font-weight: 400;
}

:deep(.quota-table .quota-row-disabled .resource-badge) {
  opacity: 0.55;
  filter: saturate(0.3);
}

/* 表格外提示 */
.disabled-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 11.5px;
  color: #94a3b8;
}

.tip-icon {
  color: #f59e0b;
}
</style>