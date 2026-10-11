<template>
  <div class="pane-toolbar">
    <VersionSwitch
      :versions="versions"
      :selected-version-id="versionId"
      @update:selected-version-id="emit('update:versionId', $event)"
    />
    <div class="toolbar-spacer" />
    <el-button type="primary" :disabled="!selectedVersion" @click="openAddDimension">
      <el-icon class="btn-icon"><Plus /></el-icon>新增维度
    </el-button>
  </div>

  <el-table v-loading="loading" :data="dimensions" class="pane-table" row-key="id">
    <el-table-column label="维度名称" min-width="150" show-overflow-tooltip>
      <template #default="{ row }">
        <span class="dim-name">{{ row.dimName }}</span>
      </template>
    </el-table-column>
    <el-table-column label="编码" width="110">
      <template #default="{ row }">
        <code class="dim-code">{{ row.dimCode || "—" }}</code>
      </template>
    </el-table-column>
    <el-table-column label="说明" min-width="170" show-overflow-tooltip>
      <template #default="{ row }">
        <span v-if="row.dimDesc" class="cell-text">{{ row.dimDesc }}</span>
        <span v-else class="muted">—</span>
      </template>
    </el-table-column>
    <el-table-column label="排序" width="120" align="center">
      <template #default="{ row }">
        <div class="sort-actions">
          <el-button
            text
            size="small"
            class="sort-btn"
            :disabled="isDimFirst(row)"
            @click="handleMoveDimension(row, -1)"
          >
            <el-icon><CaretTop /></el-icon>
          </el-button>
          <span class="sort-num">{{ row.sort ?? 0 }}</span>
          <el-button
            text
            size="small"
            class="sort-btn"
            :disabled="isDimLast(row)"
            @click="handleMoveDimension(row, 1)"
          >
            <el-icon><CaretBottom /></el-icon>
          </el-button>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="题数" width="70" align="center">
      <template #default="{ row }">
        <span class="num-text">{{ row.questionCount ?? 0 }}</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="108" align="center" fixed="right">
      <template #default="{ row }">
        <div class="op-cell">
          <el-button text size="small" class="op-btn op-edit" @click="openEditDimension(row)">编辑</el-button>
          <el-button text size="small" class="op-btn op-del" @click="handleDeleteDimension(row)">删除</el-button>
        </div>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="该版本暂无维度" :image-size="70" />
    </template>
  </el-table>

  <!-- ==================== 新增 / 编辑维度弹窗 ==================== -->
  <el-dialog
    v-model="dimensionDialogVisible"
    :title="dimensionForm.id == null ? '新增维度' : '编辑维度'"
    width="520px"
    align-center
    :close-on-click-modal="false"
  >
    <el-form ref="dimensionFormRef" :model="dimensionForm" :rules="dimensionRules" label-width="90px">
      <el-form-item label="维度名称" prop="dimName">
        <el-input v-model="dimensionForm.dimName" placeholder="如 焦虑" maxlength="30" show-word-limit clearable />
      </el-form-item>
      <el-form-item label="维度编码" prop="dimCode">
        <el-input v-model="dimensionForm.dimCode" placeholder="程序计分用，如 ANX" maxlength="20" show-word-limit clearable />
      </el-form-item>
      <el-form-item label="维度说明">
        <el-input v-model="dimensionForm.dimDesc" type="textarea" :rows="2" placeholder="选填" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="dimensionForm.sort" :min="0" :max="9999" style="width: 160px" />
        <div class="form-tip">数值越小越靠前，也可在列表中直接拖动调整</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dimensionDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="dimensionSubmitting" @click="handleDimensionSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { CaretBottom, CaretTop, Plus } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  addScaleDimension,
  updateScaleDimension,
  updateScaleDimensionSort,
  deleteScaleDimension,
} from "@/admin/api/scale/dimension";
import type { AdminScaleDimensionVO, AdminScaleVersionVO } from "@/admin/api/scale/types";
import VersionSwitch from "./VersionSwitch.vue";

const props = defineProps<{
  versions: AdminScaleVersionVO[];
  versionId: number | null;
  selectedVersion: AdminScaleVersionVO | null;
  dimensions: AdminScaleDimensionVO[];
  loading: boolean;
}>();
const emit = defineEmits<{
  (e: "update:versionId", v: number | null): void;
  (e: "changed"): void;
  (e: "reload-dimensions"): void;
}>();

/* ==================== 排序 ==================== */
const sortedDims = computed(() =>
  [...props.dimensions].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id),
);

function isDimFirst(row: any): boolean {
  return sortedDims.value[0]?.id === row.id;
}

function isDimLast(row: any): boolean {
  return sortedDims.value[sortedDims.value.length - 1]?.id === row.id;
}

async function handleMoveDimension(row: any, dir: -1 | 1) {
  const list = sortedDims.value;
  const idx = list.findIndex((d) => d.id === row.id);
  const target = list[idx + dir];
  if (idx < 0 || !target) return;

  const tmp = row.sort ?? idx;
  row.sort = target.sort ?? idx + dir;
  target.sort = tmp;

  try {
    await Promise.all([
      updateScaleDimensionSort(row.id, row.sort ?? 0),
      updateScaleDimensionSort(target.id, target.sort ?? 0),
    ]);
    ElMessage.success("排序已更新");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "排序更新失败，请稍后重试");
    emit("reload-dimensions");
  }
}

/* ==================== 新增 / 编辑 ==================== */
const dimensionDialogVisible = ref(false);
const dimensionSubmitting = ref(false);
const dimensionFormRef = ref<FormInstance>();
const dimensionForm = reactive({
  id: null as number | null,
  dimName: "",
  dimCode: "",
  dimDesc: "",
  sort: 0,
});
const dimensionRules: FormRules = {
  dimName: [{ required: true, message: "请输入维度名称", trigger: "blur" }],
  dimCode: [{ required: true, message: "请输入维度编码", trigger: "blur" }],
};

function maxDimSort(): number {
  return props.dimensions.reduce((m, d) => Math.max(m, d.sort ?? 0), -1);
}

function openAddDimension() {
  Object.assign(dimensionForm, { id: null, dimName: "", dimCode: "", dimDesc: "", sort: maxDimSort() + 1 });
  dimensionDialogVisible.value = true;
}

function openEditDimension(row: any) {
  Object.assign(dimensionForm, {
    id: row.id,
    dimName: row.dimName,
    dimCode: row.dimCode ?? "",
    dimDesc: row.dimDesc ?? "",
    sort: row.sort ?? 0,
  });
  dimensionDialogVisible.value = true;
}

async function handleDimensionSubmit() {
  const vid = props.versionId;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await dimensionFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  dimensionSubmitting.value = true;
  try {
    const payload = {
      scaleVersionId: vid,
      dimName: dimensionForm.dimName.trim(),
      dimCode: dimensionForm.dimCode.trim(),
      dimDesc: dimensionForm.dimDesc.trim() || undefined,
      sort: dimensionForm.sort,
    };
    if (dimensionForm.id == null) {
      await addScaleDimension(payload);
      ElMessage.success("维度已新增");
    } else {
      await updateScaleDimension(dimensionForm.id, payload);
      ElMessage.success("维度已更新");
    }
    dimensionDialogVisible.value = false;
    emit("changed");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    dimensionSubmitting.value = false;
  }
}

async function handleDeleteDimension(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除维度「${row.dimName}」吗？该维度下存在题目时将禁止删除。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleDimension(row.id);
    ElMessage.success("维度已删除");
    emit("changed");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}
</script>

<style scoped>
/* ==================== Tab 内工具栏 ==================== */
.pane-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin: 6px 0 14px;
}

/* ==================== 表格 ==================== */
/* 表头底/文字/边框/hover 与单元格 padding、圆角均由全局 styles/index.css 统一提供 */
.pane-table {
  width: 100%;
}

.cell-text {
  font-size: 13px;
  color: #475569;
}
.num-text {
  font-size: 13px;
  color: #475569;
  font-variant-numeric: tabular-nums;
}
.muted {
  color: #cbd5e1;
  font-size: 12px;
}
.dim-name {
  font-weight: 600;
  color: #334155;
}
.dim-code {
  font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  color: var(--admin-primary);
  background: var(--admin-primary-soft);
  border-radius: 4px;
  padding: 1px 6px;
}

/* ==================== 排序 ==================== */
.sort-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.sort-btn {
  padding: 2px;
  height: auto;
}
.sort-num {
  min-width: 24px;
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

/* ==================== 弹窗通用 ==================== */
.form-tip {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
