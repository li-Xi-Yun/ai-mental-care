<template>
  <div class="pane-toolbar">
    <VersionSwitch
      :versions="versions"
      :selected-version-id="versionId"
      @update:selected-version-id="emit('update:versionId', $event)"
    />
    <div class="toolbar-spacer" />
    <el-button type="primary" :disabled="!selectedVersion" @click="openAddOptionTemplate">
      <el-icon class="btn-icon"><Plus /></el-icon>新增模板
    </el-button>
  </div>

  <div v-loading="optionTemplateLoading" class="otm-wrap">
    <el-empty
      v-if="!optionTemplateList.length"
      description="该版本暂无选项模板，请先新增"
      :image-size="80"
    />
    <div v-else class="otm-cards">
      <div v-for="t in optionTemplateList" :key="t.id" class="otm-card" @click="openOptionTemplateDetail(t)">
        <div class="otm-card-head">
          <div class="otm-card-title">
            <span class="otm-name">{{ t.templateName }}</span>
            <span class="otm-count">{{ (t.items ?? []).length }} 项</span>
          </div>
          <div class="otm-card-ops">
            <el-button text size="small" class="op-btn op-edit" @click.stop="openEditOptionTemplate(t)">编辑</el-button>
            <el-button text size="small" class="op-btn op-primary" @click.stop="openApplyOptionTemplate(t)">
              <el-icon class="op-icon"><Promotion /></el-icon>应用
            </el-button>
            <el-button text size="small" class="op-btn op-primary" @click.stop="openCopyOptionTemplate(t)">
              <el-icon class="op-icon"><CopyDocument /></el-icon>复制
            </el-button>
            <el-button text size="small" class="op-btn op-del" @click.stop="handleDeleteOptionTemplate(t)">删除</el-button>
          </div>
        </div>
        <div v-if="t.templateDesc" class="otm-desc">{{ t.templateDesc }}</div>
        <div class="otm-items">
          <span v-for="(it, i) in (t.items ?? [])" :key="i" class="otm-item">
            {{ it.optionText }}({{ it.score ?? 0 }})
          </span>
          <span v-if="!(t.items ?? []).length" class="muted">—</span>
        </div>
        <div class="otm-foot">
          <span class="time-text">创建于 {{ formatTime(t.createdTime) }}</span>
        </div>
      </div>
    </div>
  </div>

  <!-- ==================== 新增 / 编辑选项模板弹窗 ==================== -->
  <el-dialog
    v-model="optionTemplateDialogVisible"
    :title="optionTemplateForm.id == null ? '新增选项模板' : '编辑选项模板'"
    width="620px"
    align-center
    :close-on-click-modal="false"
  >
    <el-form ref="optionTemplateFormRef" :model="optionTemplateForm" :rules="optionTemplateRules" label-width="90px">
      <el-form-item label="模板名称" prop="templateName">
        <el-input v-model="optionTemplateForm.templateName" placeholder="如 五点李克特通用选项" maxlength="60" show-word-limit clearable />
      </el-form-item>
      <el-form-item label="模板描述">
        <el-input v-model="optionTemplateForm.templateDesc" type="textarea" :rows="2" placeholder="选填" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="选项项">
        <div class="option-editor">
          <div class="option-editor-head">
            <span class="option-editor-title">选项配置</span>
            <span class="option-editor-tip">为每个选项设置文本、原始分值（score）与排序（sort）</span>
            <div class="toolbar-spacer" />
            <el-button size="small" type="primary" plain @click="addTemplateOptionRow">
              <el-icon class="btn-icon"><Plus /></el-icon>添加选项
            </el-button>
          </div>
          <div v-if="templateOptionRows.length" class="option-rows">
            <div v-for="(opt, i) in templateOptionRows" :key="i" class="option-row">
              <span class="option-idx">{{ i + 1 }}</span>
              <el-input v-model="opt.optionText" placeholder="选项文本" maxlength="50" clearable />
              <el-input-number v-model="opt.score" :min="0" placeholder="分值" style="width: 130px" />
              <el-input-number v-model="opt.sort" :min="0" :max="9999" placeholder="排序" style="width: 130px" />
              <el-button text class="op-btn op-del" @click="removeTemplateOptionRow(i)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <div v-else class="option-empty">暂无选项，点击「添加选项」开始配置</div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="optionTemplateDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="optionTemplateSubmitting" @click="handleOptionTemplateSubmit">确 定</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 应用选项模板弹窗 ==================== -->
  <el-dialog v-model="optionTemplateApplyVisible" title="应用选项模板" width="560px" align-center :close-on-click-modal="false">
    <el-alert
      v-if="optionTemplateApplySource"
      :title="`将模板「${optionTemplateApplySource.templateName}」应用到所选题目`"
      type="info"
      :closable="false"
      class="copy-alert"
    />
    <el-form label-width="90px">
      <el-form-item label="应用方式">
        <el-radio-group v-model="optionTemplateApplyMode">
          <el-radio-button :value="'REPLACE'">覆盖（REPLACE）</el-radio-button>
          <el-radio-button :value="'APPEND'">追加（APPEND）</el-radio-button>
        </el-radio-group>
        <div class="form-tip">覆盖：用模板选项替换题目全部原有选项；追加：在题目原有选项之后追加模板选项</div>
      </el-form-item>
      <el-form-item label="目标题目">
        <el-select
          v-model="optionTemplateApplyQuestionIds"
          multiple
          filterable
          placeholder="请选择题目（可多选）"
          style="width: 100%"
        >
          <el-option v-for="q in questions" :key="q.id" :label="q.title" :value="q.id" />
        </el-select>
        <div class="form-tip">当前版本共 {{ questions.length }} 道题目</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="optionTemplateApplyVisible = false">取 消</el-button>
      <el-button type="primary" :loading="optionTemplateApplySubmitting" @click="handleOptionTemplateApply">确 定</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 复制选项模板弹窗 ==================== -->
  <el-dialog v-model="optionTemplateCopyVisible" title="复制选项模板" width="440px" align-center :close-on-click-modal="false">
    <el-alert
      v-if="optionTemplateCopySource"
      :title="`将选项模板「${optionTemplateCopySource.templateName}」复制到目标版本`"
      type="info"
      :closable="false"
      class="copy-alert"
    />
    <el-form label-width="90px">
      <el-form-item label="目标版本">
        <el-select v-model="optionTemplateCopyVersionId" placeholder="请选择目标版本" style="width: 100%">
          <el-option v-for="v in copyableVersions" :key="v.id" :label="`${v.versionNo}${v.isCurrent ? '（当前）' : ''}`" :value="v.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="optionTemplateCopyVisible = false">取 消</el-button>
      <el-button type="primary" :loading="optionTemplateCopySubmitting" @click="handleOptionTemplateCopy">确 定</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 选项模板详情弹窗 ==================== -->
  <OptionTemplateDetailDialog v-model="optionTemplateDetailVisible" :row="optionTemplateDetailRow" />
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { CopyDocument, Delete, Plus, Promotion } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  getOptionTemplateList,
  addOptionTemplate,
  updateOptionTemplate,
  applyOptionTemplate,
  copyOptionTemplate,
  deleteOptionTemplate,
} from "@/admin/api/scale/option-template";
import type {
  AdminScaleOptionTemplateDTO,
  AdminScaleOptionTemplateItemDTO,
  AdminScaleOptionTemplateVO,
} from "@/admin/api/scale/option-template";
import type { AdminScaleQuestionVO, AdminScaleVersionVO } from "@/admin/api/scale/types";
import VersionSwitch from "./VersionSwitch.vue";
import OptionTemplateDetailDialog from "./OptionTemplateDetailDialog.vue";

const props = defineProps<{
  versions: AdminScaleVersionVO[];
  versionId: number | null;
  selectedVersion: AdminScaleVersionVO | null;
  questions: AdminScaleQuestionVO[];
  formatTime: (v?: string | number) => string;
}>();
const emit = defineEmits<{
  (e: "update:versionId", v: number | null): void;
  (e: "questions-changed"): void;
}>();

/* ==================== 状态 ==================== */
const optionTemplateList = ref<AdminScaleOptionTemplateVO[]>([]);
const optionTemplateLoading = ref(false);

const optionTemplateDialogVisible = ref(false);
const optionTemplateSubmitting = ref(false);
const optionTemplateFormRef = ref<FormInstance>();
const optionTemplateForm = reactive({
  id: null as number | null,
  templateName: "",
  templateDesc: "",
});
const optionTemplateRules: FormRules = {
  templateName: [{ required: true, message: "请输入模板名称", trigger: "blur" }],
};
const templateOptionRows = ref<AdminScaleOptionTemplateItemDTO[]>([]);

const optionTemplateApplyVisible = ref(false);
const optionTemplateApplySubmitting = ref(false);
const optionTemplateApplySource = ref<AdminScaleOptionTemplateVO | null>(null);
const optionTemplateApplyQuestionIds = ref<number[]>([]);
const optionTemplateApplyMode = ref<string>("REPLACE");

const optionTemplateCopyVisible = ref(false);
const optionTemplateCopySubmitting = ref(false);
const optionTemplateCopyVersionId = ref<number | null>(null);
const optionTemplateCopySource = ref<AdminScaleOptionTemplateVO | null>(null);

/** 复制目标版本（排除当前版本） */
const copyableVersions = computed(() =>
  props.versions.filter((v) => v.id !== props.versionId),
);

/* ==================== 数据加载 ==================== */
async function loadOptionTemplates(versionId: number) {
  optionTemplateLoading.value = true;
  try {
    const res = await getOptionTemplateList(versionId);
    optionTemplateList.value = res.data.data ?? [];
  } catch (e) {
    optionTemplateList.value = [];
    ElMessage.error((e as Error)?.message || "选项模板加载失败");
  } finally {
    optionTemplateLoading.value = false;
  }
}

watch(
  () => props.versionId,
  (id) => {
    if (id == null) {
      optionTemplateList.value = [];
      return;
    }
    loadOptionTemplates(id);
  },
  { immediate: true },
);

/* ==================== 选项行编辑 ==================== */
function addTemplateOptionRow() {
  templateOptionRows.value.push({ optionText: "", score: 0, sort: templateOptionRows.value.length });
}

function removeTemplateOptionRow(index: number) {
  templateOptionRows.value.splice(index, 1);
}

function buildTemplateItemPayload(): AdminScaleOptionTemplateItemDTO[] {
  return templateOptionRows.value
    .filter((r) => r.optionText.trim())
    .map((r, i) => ({ optionText: r.optionText.trim(), score: r.score ?? 0, sort: r.sort ?? i }));
}

/* ==================== 交互：新增 / 编辑 ==================== */
function openAddOptionTemplate() {
  Object.assign(optionTemplateForm, { id: null, templateName: "", templateDesc: "" });
  templateOptionRows.value = [];
  addTemplateOptionRow();
  optionTemplateDialogVisible.value = true;
}

function openEditOptionTemplate(row: AdminScaleOptionTemplateVO) {
  Object.assign(optionTemplateForm, {
    id: row.id,
    templateName: row.templateName ?? "",
    templateDesc: row.templateDesc ?? "",
  });
  templateOptionRows.value = (row.items ?? []).map((it) => ({
    optionText: it.optionText ?? "",
    score: it.score ?? 0,
    sort: it.sort ?? 0,
  }));
  if (!templateOptionRows.value.length) addTemplateOptionRow();
  optionTemplateDialogVisible.value = true;
}

async function handleOptionTemplateSubmit() {
  const vid = props.versionId;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await optionTemplateFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (!templateOptionRows.value.some((r) => r.optionText.trim())) {
    ElMessage.warning("选项模板至少需要一个选项项");
    return;
  }
  optionTemplateSubmitting.value = true;
  try {
    const payload: AdminScaleOptionTemplateDTO = {
      scaleVersionId: vid,
      templateName: optionTemplateForm.templateName.trim(),
      templateDesc: optionTemplateForm.templateDesc.trim() || undefined,
      items: buildTemplateItemPayload(),
    };
    if (optionTemplateForm.id == null) {
      await addOptionTemplate(payload);
      ElMessage.success("选项模板已新增");
    } else {
      await updateOptionTemplate(optionTemplateForm.id, payload);
      ElMessage.success("选项模板已更新");
    }
    optionTemplateDialogVisible.value = false;
    await loadOptionTemplates(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    optionTemplateSubmitting.value = false;
  }
}

/* ==================== 交互：应用 ==================== */
function openApplyOptionTemplate(row: AdminScaleOptionTemplateVO) {
  optionTemplateApplySource.value = row;
  optionTemplateApplyMode.value = "REPLACE";
  optionTemplateApplyQuestionIds.value = [];
  optionTemplateApplyVisible.value = true;
}

async function handleOptionTemplateApply() {
  const source = optionTemplateApplySource.value;
  if (!source) return;
  if (!optionTemplateApplyQuestionIds.value.length) {
    ElMessage.warning("请至少选择一道目标题目");
    return;
  }
  optionTemplateApplySubmitting.value = true;
  try {
    await applyOptionTemplate(source.id, {
      questionIds: optionTemplateApplyQuestionIds.value,
      mode: optionTemplateApplyMode.value,
    });
    ElMessage.success("模板已应用到题目");
    optionTemplateApplyVisible.value = false;
    const vid = props.versionId;
    if (vid != null) {
      await loadOptionTemplates(vid);
      emit("questions-changed");
    }
  } catch (e) {
    ElMessage.error((e as Error)?.message || "应用失败，请稍后重试");
  } finally {
    optionTemplateApplySubmitting.value = false;
  }
}

/* ==================== 交互：复制 / 删除 ==================== */
function openCopyOptionTemplate(row: AdminScaleOptionTemplateVO) {
  optionTemplateCopySource.value = row;
  optionTemplateCopyVersionId.value = null;
  optionTemplateCopyVisible.value = true;
}

async function handleOptionTemplateCopy() {
  const source = optionTemplateCopySource.value;
  if (!source) return;
  const targetVid = optionTemplateCopyVersionId.value;
  if (targetVid == null) {
    ElMessage.warning("请选择目标版本");
    return;
  }
  optionTemplateCopySubmitting.value = true;
  try {
    await copyOptionTemplate({ groupId: source.id, targetScaleVersionId: targetVid });
    ElMessage.success("选项模板已复制");
    optionTemplateCopyVisible.value = false;
  } catch (e) {
    ElMessage.error((e as Error)?.message || "复制失败，请稍后重试");
  } finally {
    optionTemplateCopySubmitting.value = false;
  }
}

async function handleDeleteOptionTemplate(row: AdminScaleOptionTemplateVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除选项模板「${row.templateName}」吗？删除后不影响已应用到题目的选项。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteOptionTemplate(row.id);
    ElMessage.success("选项模板已删除");
    const vid = props.versionId;
    if (vid != null) await loadOptionTemplates(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 卡片点击查看详情（纯行数据，零请求） ==================== */
const optionTemplateDetailVisible = ref(false);
const optionTemplateDetailRow = ref<AdminScaleOptionTemplateVO | null>(null);

function openOptionTemplateDetail(row: AdminScaleOptionTemplateVO) {
  optionTemplateDetailRow.value = row;
  optionTemplateDetailVisible.value = true;
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

.muted {
  color: #cbd5e1;
  font-size: 12px;
}
.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
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

/* ==================== 选项模板 ==================== */
.otm-wrap {
  min-height: 200px;
}
.otm-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}
.otm-card {
  border: 1px solid #eef1f6;
  border-radius: 10px;
  background: #fbfcfe;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  cursor: pointer;
}
.otm-card:hover {
  border-color: #e0e7ff;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.06);
}
.otm-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.otm-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.otm-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  word-break: break-all;
}
.otm-count {
  flex-shrink: 0;
  font-size: 11px;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 999px;
  padding: 1px 8px;
}
.otm-card-ops {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 0;
}
.otm-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.otm-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
}
.otm-item {
  font-size: 12px;
  color: #475569;
  background: var(--admin-primary-soft);
  border: 1px solid #e0e7ff;
  border-radius: 6px;
  padding: 2px 8px;
  line-height: 1.55;
}
.otm-foot {
  margin-top: auto;
  padding-top: 2px;
}

/* ==================== 模板弹窗：选项编辑器 ==================== */
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
