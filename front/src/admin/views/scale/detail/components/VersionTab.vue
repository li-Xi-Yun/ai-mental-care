<template>
  <div class="pane-toolbar">
    <div class="pane-title">
      <span>版本列表</span>
      <el-tag size="small" effect="plain">{{ versions.length }} 个版本</el-tag>
    </div>
    <div class="toolbar-spacer" />
    <el-button type="primary" @click="openAddVersion">
      <el-icon class="btn-icon"><Plus /></el-icon>新增版本
    </el-button>
  </div>

  <el-table v-loading="pageLoading" :data="versions" class="pane-table" row-key="id" @row-click="handleVersionRowClick">
    <el-table-column label="版本号" width="120">
      <template #default="{ row }">
        <span class="ver-no">{{ row.versionNo }}</span>
      </template>
    </el-table-column>
    <el-table-column label="说明" min-width="180" show-overflow-tooltip>
      <template #default="{ row }">
        <span v-if="row.description" class="cell-text">{{ row.description }}</span>
        <span v-else class="muted">—</span>
      </template>
    </el-table-column>
    <el-table-column label="题目数" width="84" align="center">
      <template #default="{ row }">
        <span class="num-text">{{ row.questionCount ?? 0 }}</span>
      </template>
    </el-table-column>
    <el-table-column label="维度数" width="84" align="center">
      <template #default="{ row }">
        <span class="num-text">{{ row.dimensionCount ?? 0 }}</span>
      </template>
    </el-table-column>
    <el-table-column label="是否当前" width="96" align="center">
      <template #default="{ row }">
        <el-tag v-if="row.isCurrent" type="success" size="small" effect="light">当前生效</el-tag>
        <span v-else class="muted">—</span>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" width="140">
      <template #default="{ row }">
        <span class="time-text">{{ formatTime(row.createdTime) }}</span>
      </template>
    </el-table-column>
    <el-table-column label="创建人" width="96">
      <template #default="{ row }">
        <span>{{ row.createdByName || "—" }}</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="208" align="center" fixed="right">
      <template #default="{ row }">
        <div class="op-cell">
          <el-button text size="small" class="op-btn op-primary" :loading="publishingId === row.id" @click.stop="handlePublishVersion(row)">
            <el-icon class="op-icon"><Promotion /></el-icon>发布
          </el-button>
          <el-button text size="small" class="op-btn op-edit" @click.stop="openCopyVersion(row)">
            <el-icon class="op-icon"><CopyDocument /></el-icon>复制
          </el-button>
          <el-button text size="small" class="op-btn op-del" @click.stop="handleDeleteVersion(row)">
            <el-icon class="op-icon"><Delete /></el-icon>删除
          </el-button>
        </div>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="该量表暂无版本，请先新增版本" :image-size="70" />
    </template>
  </el-table>

  <!-- ==================== 新增版本弹窗 ==================== -->
  <el-dialog v-model="versionDialogVisible" title="新增版本" width="520px" align-center :close-on-click-modal="false">
    <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="90px">
      <el-form-item label="版本号" prop="versionNo">
        <el-input v-model="versionForm.versionNo" placeholder="如 v1.1" maxlength="20" clearable />
      </el-form-item>
      <el-form-item label="量表说明">
        <el-input v-model="versionForm.description" type="textarea" :rows="3" placeholder="指导语 / 量表说明" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="版权信息">
        <el-input v-model="versionForm.copyrightInfo" placeholder="如 © 2026 某机构" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="versionDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="versionSubmitting" @click="handleAddVersion">确 定</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 复制版本弹窗 ==================== -->
  <el-dialog v-model="copyVersionVisible" title="复制版本" width="440px" align-center :close-on-click-modal="false">
    <el-alert
      v-if="copyVersionSource"
      :title="`将复制版本「${copyVersionSource.versionNo}」的全部题目 / 维度 / 选项至新版本`"
      type="info"
      :closable="false"
      class="copy-alert"
    />
    <el-form ref="copyVersionFormRef" :model="copyVersionForm" :rules="copyVersionRules" label-width="90px">
      <el-form-item label="新版本号" prop="newVersionNo">
        <el-input v-model="copyVersionForm.newVersionNo" placeholder="如 v1.2" maxlength="20" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="copyVersionVisible = false">取 消</el-button>
      <el-button type="primary" :loading="copyVersionSubmitting" @click="handleCopyVersion">开 始 复 制</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 版本详情弹窗 ==================== -->
  <ScaleVersionDetailDialog
    v-model="versionDetailVisible"
    :row="versionDetailRow"
    :detail="versionDetailData"
    :loading="versionDetailLoading"
  />
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { CopyDocument, Delete, Plus, Promotion } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  addScaleVersion,
  copyScaleVersion,
  getScaleVersionDetail,
  publishScaleVersion,
  deleteScaleVersion,
} from "@/admin/api/scale/version";
import type { AdminScaleVersionVO } from "@/admin/api/scale/types";
import ScaleVersionDetailDialog from "./ScaleVersionDetailDialog.vue";

const props = defineProps<{
  scaleId: number;
  versions: AdminScaleVersionVO[];
  pageLoading: boolean;
  formatTime: (v?: string | number) => string;
}>();
const emit = defineEmits<{ (e: "changed"): void }>();

/* ==================== 新增版本 ==================== */
const versionDialogVisible = ref(false);
const versionSubmitting = ref(false);
const versionFormRef = ref<FormInstance>();
const versionForm = reactive({ versionNo: "", description: "", copyrightInfo: "" });
const versionRules: FormRules = {
  versionNo: [{ required: true, message: "请输入版本号", trigger: "blur" }],
};

function openAddVersion() {
  versionForm.versionNo = "";
  versionForm.description = "";
  versionForm.copyrightInfo = "";
  versionDialogVisible.value = true;
}

async function handleAddVersion() {
  const valid = await versionFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  versionSubmitting.value = true;
  try {
    await addScaleVersion({
      scaleId: props.scaleId,
      versionNo: versionForm.versionNo.trim(),
      description: versionForm.description.trim() || undefined,
      copyrightInfo: versionForm.copyrightInfo.trim() || undefined,
    });
    ElMessage.success("版本已创建");
    versionDialogVisible.value = false;
    emit("changed");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "创建版本失败，请稍后重试");
  } finally {
    versionSubmitting.value = false;
  }
}

/* ==================== 复制版本 ==================== */
const copyVersionSource = ref<AdminScaleVersionVO | null>(null);
const copyVersionVisible = ref(false);
const copyVersionSubmitting = ref(false);
const copyVersionFormRef = ref<FormInstance>();
const copyVersionForm = reactive({ newVersionNo: "" });
const copyVersionRules: FormRules = {
  newVersionNo: [{ required: true, message: "请输入新版本号", trigger: "blur" }],
};

function openCopyVersion(row: any) {
  copyVersionSource.value = row;
  copyVersionForm.newVersionNo = "";
  copyVersionVisible.value = true;
}

async function handleCopyVersion() {
  const source = copyVersionSource.value;
  if (!source) return;
  const valid = await copyVersionFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  copyVersionSubmitting.value = true;
  try {
    await copyScaleVersion({
      sourceVersionId: source.id,
      targetScaleId: props.scaleId,
      versionNo: copyVersionForm.newVersionNo.trim(),
    });
    ElMessage.success(`版本「${source.versionNo}」复制成功`);
    copyVersionVisible.value = false;
    emit("changed");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "复制版本失败，请稍后重试");
  } finally {
    copyVersionSubmitting.value = false;
  }
}

/* ==================== 发布 / 删除 ==================== */
const publishingId = ref<number | null>(null);

async function handlePublishVersion(row: any) {
  const confirmText = (row.questionCount ?? 0) > 0
    ? `确定发布版本「${row.versionNo}」为当前生效版本吗？发布后测评将使用该版本。`
    : `版本「${row.versionNo}」下暂无题目，无法发布。请先添加题目。`;
  if ((row.questionCount ?? 0) <= 0) {
    ElMessage.warning(confirmText);
    return;
  }
  try {
    await ElMessageBox.confirm(`确定发布版本「${row.versionNo}」为当前生效版本吗？`, "发布确认", {
      type: "warning",
      confirmButtonText: "发布",
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  publishingId.value = row.id;
  try {
    await publishScaleVersion(row.id);
    ElMessage.success(`版本「${row.versionNo}」已发布`);
    emit("changed");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "发布失败，请稍后重试");
  } finally {
    publishingId.value = null;
  }
}

async function handleDeleteVersion(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除版本「${row.versionNo}」吗？该版本下的题目、维度、选项等将一并删除。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleVersion(row.id);
    ElMessage.success("版本已删除");
    emit("changed");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 行点击查看详情 ==================== */
const versionDetailVisible = ref(false);
const versionDetailRow = ref<AdminScaleVersionVO | null>(null);
const versionDetailData = ref<AdminScaleVersionVO | null>(null);
const versionDetailLoading = ref(false);

function handleVersionRowClick(row: AdminScaleVersionVO, column?: { type?: string } | null) {
  if (column?.type === "selection") return;
  const id = row.id;
  versionDetailRow.value = row;
  versionDetailData.value = null;
  versionDetailVisible.value = true;
  versionDetailLoading.value = true;
  getScaleVersionDetail(id)
    .then((res) => {
      // 旧响应丢弃守卫：快速连点其它行时忽略过期结果
      if (versionDetailRow.value?.id !== id) return;
      versionDetailData.value = res.data?.data ?? null;
    })
    .catch(() => undefined)
    .finally(() => {
      if (versionDetailRow.value?.id === id) versionDetailLoading.value = false;
    });
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
.pane-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

/* ==================== 表格 ==================== */
/* 表头底/文字/边框/hover 与单元格 padding、圆角均由全局 styles/index.css 统一提供 */
.pane-table {
  width: 100%;
}
:deep(.pane-table .el-table__row) {
  cursor: pointer;
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
.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}
.muted {
  color: #cbd5e1;
  font-size: 12px;
}
.ver-no {
  font-weight: 600;
  color: var(--admin-primary);
  font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
  font-size: 13px;
}

/* ==================== 弹窗通用 ==================== */
.copy-alert {
  margin-bottom: 14px;
}
</style>
