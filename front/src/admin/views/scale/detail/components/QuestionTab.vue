<template>
  <div class="pane-toolbar">
    <VersionSwitch
      :versions="versions"
      :selected-version-id="versionId"
      @update:selected-version-id="emit('update:versionId', $event)"
    />
    <div class="toolbar-spacer" />
    <el-button type="primary" :disabled="!selectedVersion" @click="openAddQuestion">
      <el-icon class="btn-icon"><Plus /></el-icon>新增题目
    </el-button>
  </div>

  <el-table v-loading="loading" :data="questions" class="pane-table" row-key="id" @row-click="handleQuestionRowClick">
    <el-table-column label="序号" width="64" align="center">
      <template #default="{ $index }">
        <span class="num-text">{{ $index + 1 }}</span>
      </template>
    </el-table-column>
    <el-table-column label="题目" min-width="220" show-overflow-tooltip>
      <template #default="{ row }">
        <span class="q-title">{{ row.title }}</span>
      </template>
    </el-table-column>
    <el-table-column label="题型" width="84" align="center">
      <template #default="{ row }">
        <el-tag size="small" effect="plain" :type="questionTypeTag(row.questionType)">
          {{ questionTypeText(row.questionType) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="所属维度" width="130" show-overflow-tooltip>
      <template #default="{ row }">
        <span v-if="row.dimensionName" class="dim-tag">{{ row.dimensionName }}</span>
        <span v-else class="muted">—</span>
      </template>
    </el-table-column>
    <el-table-column label="必答" width="70" align="center">
      <template #default="{ row }">
        <el-tag v-if="row.required === 1" type="danger" size="small" effect="light">必答</el-tag>
        <el-tag v-else type="info" size="small" effect="light">选答</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="选项摘要" min-width="180" show-overflow-tooltip>
      <template #default="{ row }">
        <span v-if="optionSummary(row)" class="option-summary">{{ optionSummary(row) }}</span>
        <span v-else class="muted">—</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="164" align="center" fixed="right">
      <template #default="{ row }">
        <div class="op-cell">
          <el-button text size="small" class="op-btn op-edit" @click.stop="openEditQuestion(row)">编辑</el-button>
          <el-button text size="small" class="op-btn op-primary" @click.stop="openCopyQuestion(row)">
            <el-icon class="op-icon"><CopyDocument /></el-icon>复制
          </el-button>
          <el-button text size="small" class="op-btn op-del" @click.stop="handleDeleteQuestion(row)">删除</el-button>
        </div>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="该版本暂无题目，请先新增题目" :image-size="70" />
    </template>
  </el-table>

  <!-- ==================== 新增 / 编辑题目弹窗 ==================== -->
  <el-dialog
    v-model="questionDialogVisible"
    :title="questionForm.id == null ? '新增题目' : '编辑题目'"
    width="760px"
    align-center
    :close-on-click-modal="false"
  >
    <el-form ref="questionFormRef" :model="questionForm" :rules="questionRules" label-width="90px">
      <el-form-item label="题干" prop="title">
        <el-input v-model="questionForm.title" placeholder="请输入题干" maxlength="200" show-word-limit clearable />
      </el-form-item>
      <el-form-item label="题型">
        <el-radio-group v-model="questionForm.questionType">
          <el-radio-button v-for="t in QUESTION_TYPE_OPTIONS" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="所属维度">
        <el-select v-model="questionForm.dimensionId" placeholder="请选择维度（可选）" clearable style="width: 100%">
          <el-option v-for="d in dimensions" :key="d.id" :label="d.dimName" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="计分方式">
        <el-select v-model="questionForm.scoreType" style="width: 220px">
          <el-option v-for="s in SCORE_TYPE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="必答">
        <el-switch v-model="questionForm.required" :active-value="1" :inactive-value="0" active-text="必答" inactive-text="选答" />
      </el-form-item>

      <el-form-item v-if="isChoiceType" label="选项">
        <div class="option-editor">
          <div class="option-editor-head">
            <span class="option-editor-title">选项配置</span>
            <span class="option-editor-tip">为每个选项设置文本与原始分值</span>
            <div class="toolbar-spacer" />
            <el-button size="small" type="primary" plain @click="addOptionRow">
              <el-icon class="btn-icon"><Plus /></el-icon>添加选项
            </el-button>
          </div>
          <div v-if="optionRows.length" class="option-rows">
            <div v-for="(opt, i) in optionRows" :key="i" class="option-row">
              <span class="option-idx">{{ i + 1 }}</span>
              <el-input v-model="opt.optionText" placeholder="选项文本" maxlength="50" clearable />
              <el-input-number v-model="opt.score" :min="0" placeholder="分值" style="width: 130px" />
              <el-button text class="op-btn op-del" @click="removeOptionRow(i)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <div v-else class="option-empty">暂无选项，点击「添加选项」开始配置</div>
        </div>
      </el-form-item>
      <div v-else class="form-tip" style="margin: -4px 0 0 90px">填空题型无需配置选项</div>
    </el-form>
    <template #footer>
      <el-button @click="questionDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="questionSubmitting" @click="handleQuestionSubmit">确 定</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 复制题目弹窗 ==================== -->
  <el-dialog v-model="questionCopyVisible" title="复制题目" width="440px" align-center :close-on-click-modal="false">
    <el-alert
      v-if="questionCopySource"
      :title="`将题目「${questionCopySource.title}」及其选项复制到目标版本`"
      type="info"
      :closable="false"
      class="copy-alert"
    />
    <el-form label-width="90px">
      <el-form-item label="目标版本">
        <el-select v-model="questionCopyVersionId" placeholder="请选择目标版本" style="width: 100%">
          <el-option v-for="v in versions" :key="v.id" :label="`${v.versionNo}${v.isCurrent ? '（当前）' : ''}`" :value="v.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="questionCopyVisible = false">取 消</el-button>
      <el-button type="primary" :loading="questionCopySubmitting" @click="handleQuestionCopy">确 定</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 题目详情弹窗 ==================== -->
  <ScaleQuestionDetailDialog
    v-model="questionDetailVisible"
    :row="questionDetailRow"
    :detail="questionDetailData"
    :loading="questionDetailLoading"
  />
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { CopyDocument, Delete, Plus } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  addScaleQuestion,
  updateScaleQuestion,
  deleteScaleQuestion,
  copyScaleQuestion,
  getScaleQuestionDetail,
} from "@/admin/api/scale/question";
import type {
  AdminScaleDimensionVO,
  AdminScaleOptionItemDTO,
  AdminScaleQuestionVO,
  AdminScaleVersionVO,
} from "@/admin/api/scale/types";
import VersionSwitch from "./VersionSwitch.vue";
import ScaleQuestionDetailDialog from "./ScaleQuestionDetailDialog.vue";

const props = defineProps<{
  versions: AdminScaleVersionVO[];
  versionId: number | null;
  selectedVersion: AdminScaleVersionVO | null;
  dimensions: AdminScaleDimensionVO[];
  questions: AdminScaleQuestionVO[];
  loading: boolean;
}>();
const emit = defineEmits<{
  (e: "update:versionId", v: number | null): void;
  (e: "changed"): void;
  (e: "question-copied", targetVersionId: number): void;
}>();

/* ==================== 常量 ==================== */
const QUESTION_TYPE_OPTIONS = [
  { label: "单选", value: 1 },
  { label: "多选", value: 2 },
  { label: "填空", value: 3 },
] as const;

const SCORE_TYPE_OPTIONS = [
  { label: "正向计分", value: 1 },
  { label: "反向计分", value: 2 },
  { label: "不计分", value: 0 },
] as const;

/* ==================== 工具函数 ==================== */
function questionTypeText(type?: number): string {
  return QUESTION_TYPE_OPTIONS.find((t) => t.value === type)?.label ?? "未知";
}

function questionTypeTag(type?: number): "primary" | "warning" | "info" {
  if (type === 1) return "primary";
  if (type === 2) return "warning";
  return "info";
}

function optionSummary(row: any): string {
  return (row.options ?? []).map((o: any) => `${o.optionText}(${o.score ?? 0})`).join("、");
}

/* ==================== 新增 / 编辑题目 ==================== */
interface QuestionFormState {
  id: number | null;
  title: string;
  questionType: number;
  dimensionId: number | null;
  required: number;
  scoreType: number;
}

interface OptionRow {
  optionText: string;
  score: number | null;
}

const questionDialogVisible = ref(false);
const questionSubmitting = ref(false);
const questionFormRef = ref<FormInstance>();
const questionForm = reactive<QuestionFormState>({
  id: null,
  title: "",
  questionType: 1,
  dimensionId: null,
  required: 1,
  scoreType: 1,
});
const questionRules: FormRules = {
  title: [{ required: true, message: "请输入题干", trigger: "blur" }],
};
const optionRows = ref<OptionRow[]>([]);

const isChoiceType = computed(() => questionForm.questionType === 1 || questionForm.questionType === 2);

function openAddQuestion() {
  Object.assign(questionForm, { id: null, title: "", questionType: 1, dimensionId: null, required: 1, scoreType: 1 });
  optionRows.value = [];
  questionDialogVisible.value = true;
}

function openEditQuestion(row: any) {
  Object.assign(questionForm, {
    id: row.id,
    title: row.title,
    questionType: row.questionType ?? 1,
    dimensionId: row.dimensionId ?? null,
    required: row.required ?? 1,
    scoreType: row.scoreType ?? 1,
  });
  optionRows.value = (row.options ?? []).map((o: any) => ({ optionText: o.optionText ?? "", score: o.score ?? 0 }));
  questionDialogVisible.value = true;
}

function addOptionRow() {
  optionRows.value.push({ optionText: "", score: 0 });
}

function removeOptionRow(index: number) {
  optionRows.value.splice(index, 1);
}

function buildOptionPayload(): AdminScaleOptionItemDTO[] {
  return optionRows.value
    .filter((r) => r.optionText.trim())
    .map((r, i) => ({ optionText: r.optionText.trim(), score: r.score ?? 0, sort: i }));
}

async function handleQuestionSubmit() {
  const vid = props.versionId;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await questionFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (isChoiceType.value && optionRows.value.filter((r) => r.optionText.trim()).length === 0) {
    ElMessage.warning("单选 / 多选题目至少需要一个选项");
    return;
  }
  questionSubmitting.value = true;
  try {
    const options = isChoiceType.value ? buildOptionPayload() : [];
    const payload = {
      scaleVersionId: vid,
      dimensionId: questionForm.dimensionId,
      title: questionForm.title.trim(),
      questionType: questionForm.questionType,
      required: questionForm.required,
      scoreType: questionForm.scoreType,
      sort: questionForm.id == null ? props.questions.length : undefined,
      options,
    };
    if (questionForm.id == null) {
      await addScaleQuestion(payload);
      ElMessage.success("题目已新增");
    } else {
      await updateScaleQuestion(questionForm.id, payload);
      ElMessage.success("题目已更新");
    }
    questionDialogVisible.value = false;
    emit("changed");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    questionSubmitting.value = false;
  }
}

/* ==================== 复制题目 ==================== */
const questionCopySource = ref<AdminScaleQuestionVO | null>(null);
const questionCopyVisible = ref(false);
const questionCopySubmitting = ref(false);
const questionCopyVersionId = ref<number | null>(null);

function openCopyQuestion(row: any) {
  questionCopySource.value = row;
  questionCopyVersionId.value = props.versionId;
  questionCopyVisible.value = true;
}

async function handleQuestionCopy() {
  const source = questionCopySource.value;
  if (!source) return;
  const targetVid = questionCopyVersionId.value;
  if (targetVid == null) {
    ElMessage.warning("请选择目标版本");
    return;
  }
  questionCopySubmitting.value = true;
  try {
    await copyScaleQuestion(source.id, targetVid);
    ElMessage.success("题目已复制");
    questionCopyVisible.value = false;
    emit("question-copied", targetVid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "复制题目失败，请稍后重试");
  } finally {
    questionCopySubmitting.value = false;
  }
}

/* ==================== 删除题目 ==================== */
async function handleDeleteQuestion(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除题目「${row.title}」吗？该题目的选项与相关跳题规则将被一并清理。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleQuestion(row.id);
    ElMessage.success("题目已删除");
    emit("changed");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 行点击查看详情 ==================== */
const questionDetailVisible = ref(false);
const questionDetailRow = ref<AdminScaleQuestionVO | null>(null);
const questionDetailData = ref<AdminScaleQuestionVO | null>(null);
const questionDetailLoading = ref(false);

function handleQuestionRowClick(row: AdminScaleQuestionVO, column?: { type?: string } | null) {
  if (column?.type === "selection") return;
  const id = row.id;
  questionDetailRow.value = row;
  questionDetailData.value = null;
  questionDetailVisible.value = true;
  questionDetailLoading.value = true;
  getScaleQuestionDetail(id)
    .then((res) => {
      // 旧响应丢弃守卫：快速连点其它行时忽略过期结果
      if (questionDetailRow.value?.id !== id) return;
      questionDetailData.value = res.data?.data ?? null;
    })
    .catch(() => undefined)
    .finally(() => {
      if (questionDetailRow.value?.id === id) questionDetailLoading.value = false;
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

/* ==================== 表格 ==================== */
/* 表头底/文字/边框/hover 与单元格 padding、圆角均由全局 styles/index.css 统一提供 */
.pane-table {
  width: 100%;
}
:deep(.pane-table .el-table__row) {
  cursor: pointer;
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
.q-title {
  font-size: 13px;
  color: #1e293b;
  line-height: 1.5;
}
.dim-tag {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--admin-primary-soft);
  color: var(--admin-primary);
  font-size: 11.5px;
  border: 1px solid #e0e7ff;
  line-height: 1.5;
}
.option-summary {
  font-size: 12px;
  color: #64748b;
}

/* ==================== 弹窗通用 ==================== */
.form-tip {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
  margin-top: 4px;
}
.copy-alert {
  margin-bottom: 14px;
}

/* ==================== 题目弹窗：选项编辑器 ==================== */
.option-editor {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}
.option-editor-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #eef1f6;
}
.option-editor-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.option-editor-tip {
  font-size: 11.5px;
  color: #94a3b8;
}
.option-rows {
  padding: 10px 12px;
}
.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.option-row:last-child {
  margin-bottom: 0;
}
.option-idx {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--admin-primary-soft);
  color: var(--admin-primary);
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.option-empty {
  padding: 20px;
  text-align: center;
  font-size: 12px;
  color: #cbd5e1;
}
</style>
