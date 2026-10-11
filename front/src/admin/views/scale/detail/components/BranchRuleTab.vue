<template>
  <div class="pane-toolbar">
    <VersionSwitch
      :versions="versions"
      :selected-version-id="versionId"
      @update:selected-version-id="emit('update:versionId', $event)"
    />
    <div class="toolbar-spacer" />
    <el-button type="primary" :disabled="!selectedVersion" @click="openAddBranchRule">
      <el-icon class="btn-icon"><Plus /></el-icon>新增规则
    </el-button>
  </div>

  <el-table v-loading="branchRuleLoading" :data="branchRuleList" class="pane-table" row-key="id">
    <el-table-column label="源题目" min-width="200" show-overflow-tooltip>
      <template #default="{ row }">
        <span class="q-title">{{ row.sourceQuestionTitle || "—" }}</span>
      </template>
    </el-table-column>
    <el-table-column label="源选项" width="110" align="center">
      <template #default="{ row }">
        <span v-if="row.sourceOptionText" class="branch-option">{{ row.sourceOptionText }}</span>
        <span v-else class="muted">—</span>
      </template>
    </el-table-column>
    <el-table-column label="跳转目标" min-width="200" show-overflow-tooltip>
      <template #default="{ row }">
        <span v-if="row.targetQuestionTitle" class="q-title">{{ row.targetQuestionTitle }}</span>
        <span v-else class="branch-end">结束测评</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="108" align="center" fixed="right">
      <template #default="{ row }">
        <div class="op-cell">
          <el-button text size="small" class="op-btn op-edit" @click="openEditBranchRule(row)">编辑</el-button>
          <el-button text size="small" class="op-btn op-del" @click="handleDeleteBranchRule(row)">删除</el-button>
        </div>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="该版本暂无跳题规则" :image-size="70" />
    </template>
  </el-table>

  <!-- ==================== 新增 / 编辑跳题规则弹窗 ==================== -->
  <el-dialog
    v-model="branchRuleDialogVisible"
    :title="branchRuleForm.id == null ? '新增跳题规则' : '编辑跳题规则'"
    width="580px"
    align-center
    :close-on-click-modal="false"
  >
    <el-form ref="branchRuleFormRef" :model="branchRuleForm" :rules="branchRuleRules" label-width="90px">
      <el-form-item label="源题目" prop="sourceQuestionId">
        <el-select
          v-model="branchRuleForm.sourceQuestionId"
          placeholder="请选择触发题目"
          filterable
          style="width: 100%"
          @change="handleBranchSourceChange"
        >
          <el-option v-for="q in questions" :key="q.id" :label="q.title" :value="q.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="源选项" prop="sourceOptionId">
        <el-select v-model="branchRuleForm.sourceOptionId" placeholder="请选择触发选项" style="width: 100%">
          <el-option v-for="o in branchSourceOptions" :key="o.id" :label="o.optionText ?? `选项 ${o.id}`" :value="o.id as number" />
        </el-select>
        <div class="form-tip">选中该选项后触发跳转</div>
      </el-form-item>
      <el-form-item label="跳转目标">
        <el-select v-model="branchRuleForm.targetQuestionId" placeholder="留空表示结束测评" clearable filterable style="width: 100%">
          <el-option v-for="q in questions" :key="q.id" :label="q.title" :value="q.id" />
        </el-select>
        <div class="form-tip">留空表示命中该规则后结束测评</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="branchRuleDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="branchRuleSubmitting" @click="handleBranchRuleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { Plus } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  getScaleBranchRuleList,
  addScaleBranchRule,
  updateScaleBranchRule,
  deleteScaleBranchRule,
} from "@/admin/api/scale/branch-rule";
import type {
  AdminScaleBranchRuleVO,
  AdminScaleQuestionVO,
  AdminScaleVersionVO,
} from "@/admin/api/scale/types";
import VersionSwitch from "./VersionSwitch.vue";

const props = defineProps<{
  versions: AdminScaleVersionVO[];
  versionId: number | null;
  selectedVersion: AdminScaleVersionVO | null;
  questions: AdminScaleQuestionVO[];
}>();
const emit = defineEmits<{ (e: "update:versionId", v: number | null): void }>();

/* ==================== 状态 ==================== */
const branchRuleList = ref<AdminScaleBranchRuleVO[]>([]);
const branchRuleLoading = ref(false);
const branchRuleDialogVisible = ref(false);
const branchRuleSubmitting = ref(false);
const branchRuleFormRef = ref<FormInstance>();
const branchRuleForm = reactive({
  id: null as number | null,
  sourceQuestionId: null as number | null,
  sourceOptionId: null as number | null,
  targetQuestionId: null as number | null,
});
const branchRuleRules: FormRules = {
  sourceQuestionId: [{ required: true, message: "请选择源题目", trigger: "change" }],
  sourceOptionId: [{ required: true, message: "请选择源选项", trigger: "change" }],
};

const branchSourceOptions = computed(() => {
  const q = props.questions.find((item) => item.id === branchRuleForm.sourceQuestionId);
  return (q?.options ?? []).filter((o) => o.id != null);
});

/* ==================== 数据加载 ==================== */
async function loadBranchRules(versionId: number) {
  branchRuleLoading.value = true;
  try {
    const res = await getScaleBranchRuleList(versionId);
    branchRuleList.value = res.data.data ?? [];
  } catch (e) {
    branchRuleList.value = [];
    ElMessage.error((e as Error)?.message || "跳题规则加载失败");
  } finally {
    branchRuleLoading.value = false;
  }
}

watch(
  () => props.versionId,
  (id) => {
    if (id == null) {
      branchRuleList.value = [];
      return;
    }
    loadBranchRules(id);
  },
  { immediate: true },
);

/* ==================== 交互 ==================== */
function openAddBranchRule() {
  Object.assign(branchRuleForm, { id: null, sourceQuestionId: null, sourceOptionId: null, targetQuestionId: null });
  branchRuleDialogVisible.value = true;
}

function openEditBranchRule(row: any) {
  Object.assign(branchRuleForm, {
    id: row.id,
    sourceQuestionId: row.sourceQuestionId ?? null,
    sourceOptionId: row.sourceOptionId ?? null,
    targetQuestionId: row.targetQuestionId ?? null,
  });
  branchRuleDialogVisible.value = true;
}

function handleBranchSourceChange() {
  branchRuleForm.sourceOptionId = null;
}

async function handleBranchRuleSubmit() {
  const vid = props.versionId;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await branchRuleFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  branchRuleSubmitting.value = true;
  try {
    const payload = {
      scaleVersionId: vid,
      sourceQuestionId: Number(branchRuleForm.sourceQuestionId),
      sourceOptionId: branchRuleForm.sourceOptionId == null ? undefined : Number(branchRuleForm.sourceOptionId),
      targetQuestionId: branchRuleForm.targetQuestionId == null ? undefined : Number(branchRuleForm.targetQuestionId),
    };
    if (branchRuleForm.id == null) {
      await addScaleBranchRule(payload);
      ElMessage.success("跳题规则已新增");
    } else {
      await updateScaleBranchRule(branchRuleForm.id, payload);
      ElMessage.success("跳题规则已更新");
    }
    branchRuleDialogVisible.value = false;
    await loadBranchRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    branchRuleSubmitting.value = false;
  }
}

async function handleDeleteBranchRule(row: any) {
  try {
    await ElMessageBox.confirm("确定删除该条跳题规则吗？", "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  try {
    await deleteScaleBranchRule(row.id);
    ElMessage.success("规则已删除");
    const vid = props.versionId;
    if (vid != null) await loadBranchRules(vid);
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

.muted {
  color: #cbd5e1;
  font-size: 12px;
}
.q-title {
  font-size: 13px;
  color: #1e293b;
  line-height: 1.5;
}

/* ==================== 弹窗通用 ==================== */
.form-tip {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
  margin-top: 4px;
}

/* ==================== 分支规则 ==================== */
.branch-option {
  display: inline-block;
  padding: 1px 10px;
  border-radius: 999px;
  background: #f0f9ff;
  color: #0369a1;
  font-size: 12px;
  border: 1px solid #e0f2fe;
}
.branch-end {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  background: #fef2f2;
  color: #ef4444;
  font-size: 12px;
  border: 1px solid #fecaca;
}
</style>
