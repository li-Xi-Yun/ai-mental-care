<template>
  <div class="pane-toolbar">
    <VersionSwitch
      :versions="versions"
      :selected-version-id="versionId"
      @update:selected-version-id="emit('update:versionId', $event)"
    />
    <div class="toolbar-spacer" />
    <el-button :disabled="!selectedVersion || !resultRuleList.length" :loading="resultRuleSaving" @click="handleResultRuleBatchSave">
      <el-icon class="op-icon"><Promotion /></el-icon>整体保存
    </el-button>
    <el-button type="primary" :disabled="!selectedVersion" @click="openAddResultRule(null)">
      <el-icon class="btn-icon"><Plus /></el-icon>新增规则
    </el-button>
  </div>

  <div v-loading="resultRuleLoading" class="rule-wrap">
    <el-empty
      v-if="!resultRuleList.length"
      description="该版本暂无结果规则，请先新增「总分规则」或按维度维护"
      :image-size="80"
    />
    <div v-else v-for="[dimensionId, rules] in groupedResultRules" :key="`${dimensionId ?? 'total'}`" class="rule-group-card">
      <div class="rule-group-head">
        <div class="rgh-left">
          <span class="rgh-title">{{ resultRuleGroupTitle(dimensionId) }}</span>
          <span class="rgh-count">{{ rules.length }} 条区间</span>
        </div>
        <div class="rgh-actions">
          <el-button text size="small" class="op-btn op-edit" @click="openAddResultRule(dimensionId)">
            <el-icon class="op-icon"><Plus /></el-icon>新增
          </el-button>
          <el-button text size="small" class="op-btn op-primary" :loading="resultRuleSaving" @click="handleResultRuleGroupSave(dimensionId)">
            <el-icon class="op-icon"><Promotion /></el-icon>按维度保存
          </el-button>
        </div>
      </div>
      <el-table :data="rules" class="pane-table" row-key="id">
        <el-table-column label="得分区间" min-width="150">
          <template #default="{ row }">
            <span class="score-range">{{ formatScore(row.minScore) }} ~ {{ formatScore(row.maxScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果文案" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="cell-text">{{ row.resultText }}</span>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="riskLevelTag(row.riskLevel).type as any">
              {{ riskLevelTag(row.riskLevel).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ row.sort ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="108" align="center" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button text size="small" class="op-btn op-edit" @click="openEditResultRule(row)">编辑</el-button>
              <el-button text size="small" class="op-btn op-del" @click="handleDeleteResultRule(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>

  <!-- ==================== 新增 / 编辑结果规则弹窗 ==================== -->
  <el-dialog
    v-model="resultRuleDialogVisible"
    :title="resultRuleForm.id == null ? '新增结果规则' : '编辑结果规则'"
    width="560px"
    align-center
    :close-on-click-modal="false"
  >
    <el-form ref="resultRuleFormRef" :model="resultRuleForm" :rules="resultRuleRules" label-width="100px">
      <el-form-item label="适用范围">
        <el-select v-model="resultRuleForm.dimensionId" placeholder="留空表示总分规则" clearable style="width: 100%">
          <el-option v-for="d in dimensions" :key="d.id" :label="d.dimName" :value="d.id" />
        </el-select>
        <div class="form-tip">同一维度下各区间不可重叠</div>
      </el-form-item>
      <div class="norm-pop-row">
        <el-form-item label="最低分" prop="minScore" class="norm-pop-item">
          <el-input-number v-model="resultRuleForm.minScore" :precision="2" :controls="false" style="width: 100%" placeholder="区间最低分（含）" />
        </el-form-item>
        <el-form-item label="最高分" prop="maxScore" class="norm-pop-item">
          <el-input-number v-model="resultRuleForm.maxScore" :precision="2" :controls="false" style="width: 100%" placeholder="区间最高分（含）" />
        </el-form-item>
      </div>
      <el-form-item label="结果文案" prop="resultText">
        <el-input v-model="resultRuleForm.resultText" type="textarea" :rows="3" placeholder="如 轻度抑郁状态，建议关注情绪变化" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="风险等级">
        <el-select v-model="resultRuleForm.riskLevel" style="width: 200px">
          <el-option v-for="o in RISK_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <div class="form-tip">0=正常（绿） 1=轻度 2=中度 3=重度（预警）</div>
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="resultRuleForm.sort" :min="0" :max="9999" style="width: 160px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="resultRuleDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="resultRuleSubmitting" @click="handleResultRuleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { Plus, Promotion } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  getScaleResultRuleList,
  addScaleResultRule,
  updateScaleResultRule,
  deleteScaleResultRule,
  saveScaleResultRuleBatch,
} from "@/admin/api/scale/result-rule";
import type {
  AdminScaleDimensionVO,
  AdminScaleResultRuleVO,
  AdminScaleVersionVO,
} from "@/admin/api/scale/types";
import VersionSwitch from "./VersionSwitch.vue";

const props = defineProps<{
  versions: AdminScaleVersionVO[];
  versionId: number | null;
  selectedVersion: AdminScaleVersionVO | null;
  dimensions: AdminScaleDimensionVO[];
}>();
const emit = defineEmits<{ (e: "update:versionId", v: number | null): void }>();

/* ==================== 常量 ==================== */
const RISK_LEVEL_OPTIONS = [
  { label: "正常", value: 0, type: "success" },
  { label: "轻度", value: 1, type: "primary" },
  { label: "中度", value: 2, type: "warning" },
  { label: "重度", value: 3, type: "danger" },
];

/* ==================== 状态 ==================== */
const resultRuleList = ref<AdminScaleResultRuleVO[]>([]);
const resultRuleLoading = ref(false);
const resultRuleDialogVisible = ref(false);
const resultRuleSubmitting = ref(false);
const resultRuleFormRef = ref<FormInstance>();
const resultRuleForm = reactive({
  id: null as number | null,
  dimensionId: null as number | null,
  minScore: null as number | null,
  maxScore: null as number | null,
  resultText: "",
  riskLevel: 0,
  sort: 0,
});
const resultRuleRules: FormRules = {
  minScore: [{ required: true, message: "请输入最低分", trigger: "blur" }],
  maxScore: [{ required: true, message: "请输入最高分", trigger: "blur" }],
  resultText: [{ required: true, message: "请输入结果文案", trigger: "blur" }],
};
const resultRuleSaving = ref(false);

const groupedResultRules = computed(() => {
  const map = new Map<number | null, AdminScaleResultRuleVO[]>();
  for (const rule of resultRuleList.value) {
    const key = rule.dimensionId ?? null;
    const list = map.get(key) ?? [];
    list.push(rule);
    map.set(key, list);
  }
  return [...map.entries()].sort((a, b) => {
    if (a[0] === null) return -1;
    if (b[0] === null) return 1;
    return a[0] - b[0];
  });
});

function resultRuleGroupTitle(dimensionId: number | null): string {
  if (dimensionId === null) return "总分规则";
  const dim = props.dimensions.find((d) => d.id === dimensionId);
  return dim?.dimName ?? `维度 ${dimensionId}`;
}

function resultRuleGroupRules(dimensionId: number | null): AdminScaleResultRuleVO[] {
  return resultRuleList.value.filter((r) => (r.dimensionId ?? null) === dimensionId);
}

function riskLevelTag(level?: number): { type: string; text: string } {
  const opt = RISK_LEVEL_OPTIONS.find((o) => o.value === level);
  return { type: opt?.type ?? "info", text: opt?.label ?? "—" };
}

function formatScore(v: number): string {
  return Number.isInteger(v) ? String(v) : String(v);
}

/* ==================== 数据加载 ==================== */
async function loadResultRules(versionId: number) {
  resultRuleLoading.value = true;
  try {
    const res = await getScaleResultRuleList(versionId);
    resultRuleList.value = res.data.data ?? [];
  } catch (e) {
    resultRuleList.value = [];
    ElMessage.error((e as Error)?.message || "结果规则加载失败");
  } finally {
    resultRuleLoading.value = false;
  }
}

watch(
  () => props.versionId,
  (id) => {
    if (id == null) {
      resultRuleList.value = [];
      return;
    }
    loadResultRules(id);
  },
  { immediate: true },
);

/* ==================== 交互 ==================== */
function openAddResultRule(dimensionId?: number | null) {
  Object.assign(resultRuleForm, {
    id: null,
    dimensionId: dimensionId ?? null,
    minScore: null,
    maxScore: null,
    resultText: "",
    riskLevel: 0,
    sort: resultRuleList.value.filter((r) => (r.dimensionId ?? null) === (dimensionId ?? null)).length,
  });
  resultRuleDialogVisible.value = true;
}

function openEditResultRule(row: any) {
  Object.assign(resultRuleForm, {
    id: row.id,
    dimensionId: row.dimensionId ?? null,
    minScore: row.minScore,
    maxScore: row.maxScore,
    resultText: row.resultText ?? "",
    riskLevel: row.riskLevel ?? 0,
    sort: row.sort ?? 0,
  });
  resultRuleDialogVisible.value = true;
}

async function handleResultRuleSubmit() {
  const vid = props.versionId;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await resultRuleFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (resultRuleForm.minScore != null && resultRuleForm.maxScore != null && resultRuleForm.minScore > resultRuleForm.maxScore) {
    ElMessage.warning("最低分不能大于最高分");
    return;
  }
  resultRuleSubmitting.value = true;
  try {
    const payload = {
      scaleVersionId: vid,
      dimensionId: resultRuleForm.dimensionId,
      minScore: Number(resultRuleForm.minScore),
      maxScore: Number(resultRuleForm.maxScore),
      resultText: resultRuleForm.resultText.trim(),
      riskLevel: resultRuleForm.riskLevel,
      sort: resultRuleForm.sort,
    };
    if (resultRuleForm.id == null) {
      await addScaleResultRule(payload);
      ElMessage.success("结果规则已新增");
    } else {
      await updateScaleResultRule(resultRuleForm.id, payload);
      ElMessage.success("结果规则已更新");
    }
    resultRuleDialogVisible.value = false;
    await loadResultRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    resultRuleSubmitting.value = false;
  }
}

async function handleDeleteResultRule(row: any) {
  try {
    await ElMessageBox.confirm("确定删除该条结果规则吗？", "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  try {
    await deleteScaleResultRule(row.id);
    ElMessage.success("规则已删除");
    const vid = props.versionId;
    if (vid != null) await loadResultRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/** 整体保存：将当前版本的整份规则集提交，服务端差异更新（增/改/删） */
async function handleResultRuleBatchSave() {
  const vid = props.versionId;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  if (!resultRuleList.value.length) {
    ElMessage.warning("暂无结果规则可保存");
    return;
  }
  resultRuleSaving.value = true;
  try {
    await saveScaleResultRuleBatch({
      scaleVersionId: vid,
      rules: resultRuleList.value.map((r) => ({
        id: r.id,
        scaleVersionId: vid,
        dimensionId: r.dimensionId ?? undefined,
        minScore: Number(r.minScore),
        maxScore: Number(r.maxScore),
        resultText: r.resultText,
        riskLevel: r.riskLevel ?? 0,
        sort: r.sort ?? 0,
      })),
    });
    ElMessage.success("结果规则已整体保存");
    await loadResultRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    resultRuleSaving.value = false;
  }
}

async function handleResultRuleGroupSave(dimensionId: number | null) {
  const vid = props.versionId;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const rules = resultRuleGroupRules(dimensionId);
  if (rules.length === 0) {
    ElMessage.warning("该维度下暂无规则可保存");
    return;
  }
  resultRuleSaving.value = true;
  try {
    await saveScaleResultRuleBatch({
      scaleVersionId: vid,
      rules: rules.map((r) => ({
        id: r.id,
        scaleVersionId: vid,
        dimensionId: r.dimensionId ?? undefined,
        minScore: Number(r.minScore),
        maxScore: Number(r.maxScore),
        resultText: r.resultText,
        riskLevel: r.riskLevel ?? 0,
        sort: r.sort ?? 0,
      })),
    });
    ElMessage.success("该维度规则已整体保存");
    await loadResultRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    resultRuleSaving.value = false;
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

/* ==================== 弹窗通用 ==================== */
.form-tip {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
  margin-top: 4px;
}
.norm-pop-row {
  display: flex;
  gap: 14px;
}
.norm-pop-item {
  flex: 1;
  min-width: 0;
}

/* ==================== 结果规则 ==================== */
.rule-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rule-group-card {
  border: 1px solid #eef1f6;
  border-radius: 10px;
  overflow: hidden;
}
.rule-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #eef1f6;
}
.rgh-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rgh-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.rgh-count {
  font-size: 11px;
  color: #94a3b8;
}
.rgh-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.score-range {
  font-weight: 600;
  color: var(--admin-primary);
  font-variant-numeric: tabular-nums;
}
</style>
