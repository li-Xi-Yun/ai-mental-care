<template>
  <div class="pane-toolbar">
    <VersionSwitch
      :versions="versions"
      :selected-version-id="versionId"
      @update:selected-version-id="emit('update:versionId', $event)"
    />
    <div class="toolbar-spacer" />
    <el-button type="primary" :disabled="!selectedVersion" @click="openAddNormGroup">
      <el-icon class="btn-icon"><Plus /></el-icon>新增常模组
    </el-button>
  </div>

  <div v-loading="normGroupLoading" class="norm-split">
    <div class="norm-group-pane">
      <div class="norm-group-head">
        <span class="norm-group-title">常模组</span>
        <el-tag size="small" effect="plain">{{ normGroupList.length }} 组</el-tag>
      </div>
      <el-empty v-if="!normGroupList.length" description="该版本暂无常模组，请先新增" :image-size="70" />
      <div v-else class="norm-group-list">
        <div
          v-for="g in normGroupList"
          :key="g.id"
          class="norm-group-item"
          :class="{ active: selectedNormGroupId === g.id }"
          @click="handleNormGroupRow(g)"
        >
          <div class="ngi-main">
            <span class="ngi-name">{{ g.groupName }}</span>
            <el-tag v-if="g.dimensionId == null" size="small" effect="light" class="ngi-tag">总分</el-tag>
            <el-tag v-else size="small" effect="light" class="ngi-tag">{{ g.dimensionName || "维度" }}</el-tag>
          </div>
          <div class="ngi-sub">{{ normGroupAppliedInfo(g) }}</div>
          <div class="ngi-meta">
            <span v-if="g.normType === 0" class="ngi-stats">M {{ fmtNum(g.mean) }} / SD {{ fmtNum(g.sd) }}</span>
            <span v-else class="ngi-stats">查表法 · {{ g.normCount ?? 0 }} 条</span>
            <span class="ngi-sort">排序 {{ g.sort ?? 0 }}</span>
          </div>
          <div class="ngi-ops">
            <el-button text size="small" class="op-btn op-edit" @click.stop="openEditNormGroup(g)">编辑</el-button>
            <el-button text size="small" class="op-btn op-del" @click.stop="handleDeleteNormGroup(g)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="norm-detail-pane">
      <div v-if="!activeNormGroup" class="norm-placeholder">
        <el-empty description="请选择左侧常模组以查看常模换算表" :image-size="80" />
      </div>
      <template v-else>
        <div class="norm-detail-head">
          <div class="ndh-title">
            <span class="ndh-name">{{ activeNormGroup.groupName }}</span>
            <span class="ndh-sub">常模换算表（原始分 → T/Z/百分位/标准九/DIQ）</span>
          </div>
          <div class="ndh-actions">
            <el-button text size="small" class="op-btn op-edit" @click="openEditNormGroup(activeNormGroup)">
              <el-icon class="op-icon"><Edit /></el-icon>编辑组信息
            </el-button>
            <el-button type="primary" size="small" :loading="normSaving" @click="handleNormRowsSave">批量保存</el-button>
          </div>
        </div>

        <el-table v-loading="normLoading" :data="normRows" class="pane-table norm-table" row-key="id" height="430">
          <el-table-column label="原始分" min-width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.rawScore" :controls="false" :precision="2" :step="1" style="width: 100%" placeholder="原始分" />
            </template>
          </el-table-column>
          <el-table-column label="T 分" min-width="105">
            <template #default="{ row }">
              <el-input-number v-model="row.tScore" :controls="false" :precision="2" style="width: 100%" placeholder="T 分" />
            </template>
          </el-table-column>
          <el-table-column label="Z 分" min-width="105">
            <template #default="{ row }">
              <el-input-number v-model="row.zScore" :controls="false" :precision="2" style="width: 100%" placeholder="Z 分" />
            </template>
          </el-table-column>
          <el-table-column label="百分位" min-width="105">
            <template #default="{ row }">
              <el-input-number v-model="row.percentile" :controls="false" :precision="2" :min="0" :max="100" style="width: 100%" placeholder="0~100" />
            </template>
          </el-table-column>
          <el-table-column label="标准九" min-width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.stanine" :controls="false" :min="1" :max="9" style="width: 100%" placeholder="1~9" />
            </template>
          </el-table-column>
          <el-table-column label="DIQ" min-width="105">
            <template #default="{ row }">
              <el-input-number v-model="row.diq" :controls="false" :precision="2" style="width: 100%" placeholder="DIQ" />
            </template>
          </el-table-column>
          <el-table-column label="等级标签" min-width="100">
            <template #default="{ row }">
              <el-select v-model="row.levelLabel" placeholder="—" clearable style="width: 100%">
                <el-option v-for="o in LEVEL_LABEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="center">
            <template #default="{ row, $index }">
              <div class="op-cell">
                <el-button text size="small" class="op-btn op-del" @click="removeNormRow($index)">移除</el-button>
                <el-button
                  v-if="row.id != null"
                  text
                  size="small"
                  class="op-btn op-del"
                  @click="handleDeleteNormRow(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无换算数据，点击左下角「新增行」开始维护" :image-size="70" />
          </template>
        </el-table>

        <div class="norm-detail-foot">
          <el-button text size="small" class="op-btn op-primary" @click="addNormRow">
            <el-icon class="op-icon"><Plus /></el-icon>新增行
          </el-button>
          <span class="norm-foot-tip">rawScore 需唯一，服务端按原始分 upsert；留空的换算值将存为 NULL</span>
        </div>
      </template>
    </div>
  </div>

  <!-- ==================== 新增 / 编辑常模组弹窗 ==================== -->
  <el-dialog
    v-model="normGroupDialogVisible"
    :title="normGroupForm.id == null ? '新增常模组' : '编辑常模组'"
    width="620px"
    align-center
    :close-on-click-modal="false"
  >
    <el-form ref="normGroupFormRef" :model="normGroupForm" :rules="normGroupRules" label-width="100px">
      <el-form-item label="常模组名称" prop="groupName">
        <el-input v-model="normGroupForm.groupName" placeholder="如 全国成年男性常模" maxlength="60" show-word-limit clearable />
      </el-form-item>
      <el-form-item label="常模组编码">
        <el-input v-model="normGroupForm.groupCode" placeholder="程序查找用，如 ADULT_MALE" maxlength="40" clearable />
        <div class="form-tip">留空则自动生成</div>
      </el-form-item>
      <el-form-item label="适用维度">
        <el-select v-model="normGroupForm.dimensionId" placeholder="留空表示总分常模" clearable style="width: 100%">
          <el-option v-for="d in dimensions" :key="d.id" :label="d.dimName" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-divider content-position="left">适用人群</el-divider>
      <div class="norm-pop-row">
        <el-form-item label="性别" class="norm-pop-item">
          <el-select v-model="normGroupForm.gender" placeholder="不限" clearable style="width: 100%">
            <el-option v-for="o in GENDER_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄范围" class="norm-pop-item">
          <div class="age-range">
            <el-input-number v-model="normGroupForm.ageMin" :min="0" :max="150" :controls="false" placeholder="最小" style="width: 82px" />
            <span class="age-sep">~</span>
            <el-input-number v-model="normGroupForm.ageMax" :min="0" :max="150" :controls="false" placeholder="最大" style="width: 82px" />
          </div>
        </el-form-item>
      </div>
      <div class="norm-pop-row">
        <el-form-item label="学历" class="norm-pop-item">
          <el-select v-model="normGroupForm.education" placeholder="不限" clearable style="width: 100%">
            <el-option v-for="o in EDUCATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="职业群体" class="norm-pop-item">
          <el-select v-model="normGroupForm.occupation" placeholder="不限" clearable style="width: 100%">
            <el-option v-for="o in OCCUPATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="地区">
        <el-select v-model="normGroupForm.region" placeholder="不限" clearable style="width: 100%">
          <el-option v-for="o in REGION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-divider content-position="left">换算参数</el-divider>
      <el-form-item label="计算方式" prop="normType">
        <el-radio-group v-model="normGroupForm.normType">
          <el-radio-button v-for="t in NORM_TYPE_OPTIONS" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="normGroupForm.normType === 0">
        <div class="norm-pop-row">
          <el-form-item label="原始分均值 M" class="norm-pop-item">
            <el-input-number v-model="normGroupForm.mean" :precision="2" :step="0.5" :controls="false" style="width: 100%" placeholder="如 32.5" />
          </el-form-item>
          <el-form-item label="标准差 SD" class="norm-pop-item">
            <el-input-number v-model="normGroupForm.sd" :precision="2" :step="0.1" :min="0" :controls="false" style="width: 100%" placeholder="如 6.8" />
          </el-form-item>
        </div>
        <div class="form-tip" style="margin: -4px 0 6px">公式法：T = 50 + 10 × (X - M) / SD</div>
      </template>
      <el-form-item v-else label="说明">
        <div class="form-tip">查表法需在「常模换算表」中维护 原始分 → 各标准分 的对照数据</div>
      </el-form-item>
      <div class="norm-pop-row">
        <el-form-item label="常模年份" class="norm-pop-item">
          <el-input-number v-model="normGroupForm.normYear" :min="1950" :max="2100" :controls="false" style="width: 100%" placeholder="如 2020" />
        </el-form-item>
        <el-form-item label="排序" class="norm-pop-item">
          <el-input-number v-model="normGroupForm.sort" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
      </div>
      <el-form-item label="常模来源">
        <el-input v-model="normGroupForm.source" placeholder="如 XX量表使用手册（2020）" maxlength="200" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="normGroupDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="normGroupSubmitting" @click="handleNormGroupSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { Edit, Plus } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  getScaleNormGroupList,
  addScaleNormGroup,
  updateScaleNormGroup,
  deleteScaleNormGroup,
  getScaleNormList,
  saveScaleNormBatch,
  deleteScaleNorms,
} from "@/admin/api/scale/norm";
import type {
  AdminScaleDimensionVO,
  AdminScaleNormGroupVO,
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
const NORM_TYPE_OPTIONS = [
  { label: "公式法（M/SD）", value: 0 },
  { label: "查表法（换算表）", value: 1 },
];
const GENDER_OPTIONS = [
  { label: "男", value: 1 },
  { label: "女", value: 0 },
];
const LEVEL_LABEL_OPTIONS = [
  { label: "极低", value: 0 },
  { label: "偏低", value: 1 },
  { label: "正常", value: 2 },
  { label: "偏高", value: 3 },
  { label: "极高", value: 4 },
];
const EDUCATION_OPTIONS = [
  { label: "初中", value: 1 },
  { label: "高中", value: 2 },
  { label: "大专", value: 3 },
  { label: "本科", value: 4 },
  { label: "硕士", value: 5 },
  { label: "博士", value: 6 },
];
const OCCUPATION_OPTIONS = [
  { label: "学生", value: 1 },
  { label: "医护", value: 2 },
  { label: "军人", value: 3 },
  { label: "企业员工", value: 4 },
];
const REGION_OPTIONS = [
  { label: "华北", value: 1 },
  { label: "华东", value: 2 },
  { label: "华南", value: 3 },
  { label: "华中", value: 4 },
  { label: "西南", value: 5 },
];

/* ==================== 常模组 ==================== */
const normGroupList = ref<AdminScaleNormGroupVO[]>([]);
const normGroupLoading = ref(false);
const normGroupDialogVisible = ref(false);
const normGroupSubmitting = ref(false);
const normGroupFormRef = ref<FormInstance>();
const normGroupForm = reactive({
  id: null as number | null,
  groupName: "",
  groupCode: "",
  dimensionId: null as number | null,
  gender: null as number | null,
  ageMin: null as number | null,
  ageMax: null as number | null,
  education: null as number | null,
  occupation: null as number | null,
  region: null as number | null,
  normType: 0,
  mean: null as number | null,
  sd: null as number | null,
  source: "",
  normYear: null as number | null,
  sort: 0,
});
const normGroupRules: FormRules = {
  groupName: [{ required: true, message: "请输入常模组名称", trigger: "blur" }],
};

const selectedNormGroupId = ref<number | null>(null);
const normLoading = ref(false);
const normSaving = ref(false);

const activeNormGroup = computed(() => normGroupList.value.find((g) => g.id === selectedNormGroupId.value) ?? null);

/** 常模换算表编辑行 */
interface AdminScaleNormRowState {
  id: number | null;
  rawScore: number | null;
  tScore: number | null;
  zScore: number | null;
  percentile: number | null;
  stanine: number | null;
  diq: number | null;
  levelLabel: number | null;
}

const normRows = ref<AdminScaleNormRowState[]>([]);

/* ==================== 数据加载 ==================== */
async function loadNormGroups(versionId: number) {
  normGroupLoading.value = true;
  try {
    const res = await getScaleNormGroupList(versionId);
    normGroupList.value = res.data.data ?? [];
    if (selectedNormGroupId.value != null && !normGroupList.value.some((g) => g.id === selectedNormGroupId.value)) {
      selectedNormGroupId.value = null;
      normRows.value = [];
    }
  } catch (e) {
    normGroupList.value = [];
    normRows.value = [];
    ElMessage.error((e as Error)?.message || "常模组列表加载失败");
  } finally {
    normGroupLoading.value = false;
  }
}

async function loadNorms(normGroupId: number) {
  normLoading.value = true;
  try {
    const res = await getScaleNormList(normGroupId);
    normRows.value = [...(res.data.data ?? [])].sort((a, b) => Number(a.rawScore) - Number(b.rawScore)).map((n) => ({
      id: n.id,
      rawScore: typeof n.rawScore === "number" ? n.rawScore : Number(n.rawScore),
      tScore: n.tScore ?? null,
      zScore: n.zScore ?? null,
      percentile: n.percentile ?? null,
      stanine: n.stanine ?? null,
      diq: n.diq ?? null,
      levelLabel: n.levelLabel ?? null,
    }));
  } catch (e) {
    normRows.value = [];
    ElMessage.error((e as Error)?.message || "常模明细加载失败");
  } finally {
    normLoading.value = false;
  }
}

watch(
  () => props.versionId,
  (id) => {
    if (id == null) {
      normGroupList.value = [];
      normRows.value = [];
      selectedNormGroupId.value = null;
      return;
    }
    loadNormGroups(id);
  },
  { immediate: true },
);

/* ==================== 交互：常模组 ==================== */
function maxNormSort(): number {
  return normGroupList.value.reduce((m, g) => Math.max(m, g.sort ?? 0), -1);
}

function openAddNormGroup() {
  Object.assign(normGroupForm, {
    id: null,
    groupName: "",
    groupCode: "",
    dimensionId: null,
    gender: null,
    ageMin: null,
    ageMax: null,
    education: null,
    occupation: null,
    region: null,
    normType: 0,
    mean: null,
    sd: null,
    source: "",
    normYear: null,
    sort: maxNormSort() + 1,
  });
  normGroupDialogVisible.value = true;
}

function openEditNormGroup(row: AdminScaleNormGroupVO) {
  Object.assign(normGroupForm, {
    id: row.id,
    groupName: row.groupName ?? "",
    groupCode: row.groupCode ?? "",
    dimensionId: row.dimensionId ?? null,
    gender: row.gender ?? null,
    ageMin: row.ageMin ?? null,
    ageMax: row.ageMax ?? null,
    education: row.education ?? null,
    occupation: row.occupation ?? null,
    region: row.region ?? null,
    normType: row.normType ?? 0,
    mean: row.mean ?? null,
    sd: row.sd ?? null,
    source: row.source ?? "",
    normYear: row.normYear ?? null,
    sort: row.sort ?? 0,
  });
  normGroupDialogVisible.value = true;
}

async function handleNormGroupSubmit() {
  const vid = props.versionId;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await normGroupFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  const requireMinMax = normGroupForm.normType === 0;
  if (requireMinMax && (normGroupForm.mean == null || normGroupForm.sd == null || Number(normGroupForm.sd) <= 0)) {
    ElMessage.warning("公式法需要提供原始分均值（M）与标准差（SD），且 SD 必须大于 0");
    return;
  }
  if (normGroupForm.ageMin != null && normGroupForm.ageMax != null && normGroupForm.ageMin > normGroupForm.ageMax) {
    ElMessage.warning("最小年龄不能大于最大年龄");
    return;
  }

  normGroupSubmitting.value = true;
  try {
    const payload = {
      scaleVersionId: vid,
      dimensionId: normGroupForm.dimensionId,
      groupName: normGroupForm.groupName.trim(),
      groupCode: normGroupForm.groupCode.trim() || undefined,
      gender: normGroupForm.gender,
      ageMin: normGroupForm.ageMin,
      ageMax: normGroupForm.ageMax,
      education: normGroupForm.education,
      occupation: normGroupForm.occupation,
      region: normGroupForm.region,
      normType: normGroupForm.normType,
      mean: normGroupForm.mean,
      sd: normGroupForm.sd,
      source: normGroupForm.source.trim() || undefined,
      normYear: normGroupForm.normYear,
      sort: normGroupForm.sort,
    };
    if (normGroupForm.id == null) {
      await addScaleNormGroup(payload);
      ElMessage.success("常模组已新增");
    } else {
      await updateScaleNormGroup(normGroupForm.id, payload);
      ElMessage.success("常模组已更新");
    }
    normGroupDialogVisible.value = false;
    await loadNormGroups(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    normGroupSubmitting.value = false;
  }
}

async function handleNormGroupSelect(group: AdminScaleNormGroupVO) {
  selectedNormGroupId.value = group.id;
  await loadNorms(group.id);
}

function handleNormGroupRow(row: AdminScaleNormGroupVO) {
  if (row.id !== selectedNormGroupId.value) {
    handleNormGroupSelect(row);
  }
}

async function handleDeleteNormGroup(row: AdminScaleNormGroupVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除常模组「${row.groupName}」吗？其常模明细将一并删除；已被测评记录引用的常模组将禁止删除。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleNormGroup(row.id);
    ElMessage.success("常模组已删除");
    if (selectedNormGroupId.value === row.id) {
      selectedNormGroupId.value = null;
      normRows.value = [];
    }
    const vid = props.versionId;
    if (vid != null) await loadNormGroups(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 交互：常模换算表 ==================== */
function addNormRow() {
  normRows.value.push({
    id: null,
    rawScore: null,
    tScore: null,
    zScore: null,
    percentile: null,
    stanine: null,
    diq: null,
    levelLabel: null,
  });
}

function removeNormRow(index: number) {
  normRows.value.splice(index, 1);
}

async function handleNormRowsSave() {
  const group = activeNormGroup.value;
  if (!group) {
    ElMessage.warning("请先选择常模组");
    return;
  }
  const rows = normRows.value.filter((r) => r.rawScore != null);
  if (rows.length === 0) {
    ElMessage.warning("请至少维护一条「原始分」记录");
    return;
  }
  const rawScores = rows.map((r) => Number(r.rawScore));
  if (new Set(rawScores).size !== rawScores.length) {
    ElMessage.warning("原始分存在重复，同一常模组下原始分需唯一");
    return;
  }
  normSaving.value = true;
  try {
    await saveScaleNormBatch({
      normGroupId: group.id,
      items: rows.map((r) => ({
        id: r.id ?? undefined,
        normGroupId: group.id,
        dimensionId: group.dimensionId ?? undefined,
        rawScore: Number(r.rawScore),
        tScore: r.tScore ?? undefined,
        zScore: r.zScore ?? undefined,
        percentile: r.percentile ?? undefined,
        stanine: r.stanine ?? undefined,
        diq: r.diq ?? undefined,
        levelLabel: r.levelLabel ?? undefined,
      })),
    });
    ElMessage.success("常模换算表已保存");

    const vid = props.versionId;
    const targetVid = vid ?? group.scaleVersionId;
    await loadNormGroups(targetVid ?? group.id);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    normSaving.value = false;
  }
}

async function handleDeleteNormRow(row: any) {
  const group = activeNormGroup.value;
  if (!group || row.id == null) return;
  try {
    await ElMessageBox.confirm("确定删除该条常模换算记录吗？", "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  try {
    await deleteScaleNorms({ normGroupId: group.id, normIds: [row.id] });
    ElMessage.success("已删除");
    await loadNorms(group.id);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 展示格式化 ==================== */
function normGroupGenderText(g?: number | null): string {
  return g == null ? "不限" : g === 1 ? "男" : "女";
}

function educationText(v?: number | null): string {
  return v == null ? "不限" : (EDUCATION_OPTIONS.find((o) => o.value === v)?.label ?? "不限");
}

function occupationText(v?: number | null): string {
  return v == null ? "不限" : (OCCUPATION_OPTIONS.find((o) => o.value === v)?.label ?? "不限");
}

function regionText(v?: number | null): string {
  return v == null ? "不限" : (REGION_OPTIONS.find((o) => o.value === v)?.label ?? "不限");
}

function normGroupAppliedInfo(g: AdminScaleNormGroupVO): string {
  return `${normGroupGenderText(g.gender)}，${g.ageMin ?? "—"}~${g.ageMax ?? "—"} 岁，${educationText(g.education)}，${occupationText(g.occupation)}，${regionText(g.region)}`;
}

/** 数值展示（空值显示 —，小数保留 2 位） */
function fmtNum(v?: number | null): string {
  return v == null ? "—" : String(Number.isInteger(v) ? v : Number(v).toFixed(2));
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

/* ==================== 弹窗通用 ==================== */
.form-tip {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
  margin-top: 4px;
}

/* ==================== 常模管理 ==================== */
.norm-split {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-height: 520px;
}
.norm-group-pane {
  width: 348px;
  flex-shrink: 0;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  background: #fbfcfe;
  overflow: hidden;
}
.norm-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #eef1f6;
  background: #f8fafc;
}
.norm-group-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.norm-group-list {
  max-height: 520px;
  overflow-y: auto;
  padding: 10px;
}
.norm-group-item {
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  background: #fff;
  transition: all 0.2s ease;
}
.norm-group-item:last-child {
  margin-bottom: 0;
}
.norm-group-item:hover {
  border-color: #e0e7ff;
  background: #f8faff;
}
.norm-group-item.active {
  border-color: var(--admin-primary);
  background: var(--admin-primary-soft);
}
.ngi-main {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.ngi-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  word-break: break-all;
}
.ngi-tag {
  flex-shrink: 0;
}
.ngi-sub {
  font-size: 11.5px;
  color: #64748b;
  line-height: 1.5;
  margin-bottom: 6px;
}
.ngi-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ngi-stats {
  font-size: 11px;
  color: var(--admin-primary);
  font-variant-numeric: tabular-nums;
}
.ngi-sort {
  font-size: 11px;
  color: #94a3b8;
}
.ngi-ops {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}
.norm-detail-pane {
  flex: 1;
  min-width: 0;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  overflow: hidden;
}
.norm-placeholder {
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.norm-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid #eef1f6;
  background: #f8fafc;
}
.ndh-title {
  min-width: 0;
}
.ndh-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.ndh-sub {
  margin-left: 8px;
  font-size: 11.5px;
  color: #94a3b8;
}
.ndh-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.norm-table :deep(.el-input-number .el-input__inner) {
  text-align: left;
}
.norm-detail-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-top: 1px solid #eef1f6;
}
.norm-foot-tip {
  font-size: 11px;
  color: #94a3b8;
}
.norm-pop-row {
  display: flex;
  gap: 14px;
}
.norm-pop-item {
  flex: 1;
  min-width: 0;
}
.age-range {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.age-sep {
  color: #94a3b8;
  font-size: 12px;
}
</style>
