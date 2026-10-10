<template>
  <div class="symptom-page">
    <!-- ==================== 统计卡 ==================== -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">💊</div>
        <div class="stat-body">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">症状总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-amber">🗂️</div>
        <div class="stat-body">
          <div class="stat-num">{{ categoryOptions.length }}</div>
          <div class="stat-label">症状大类</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">✅</div>
        <div class="stat-body">
          <div class="stat-num">{{ enabledCount }}</div>
          <div class="stat-label">启用症状</div>
        </div>
      </div>
    </div>

    <!-- ==================== 主卡片 ==================== -->
    <div class="content-card">
      <!-- 搜索条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.symptomTerm"
          placeholder="搜索症状术语 / 同义词"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="searchForm.symptomCategory" placeholder="症状大类" clearable style="width: 140px" @change="handleSearch">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" @click="handleAdd">
          <el-icon class="btn-icon"><Plus /></el-icon>新增症状
        </el-button>
        <el-button :danger="true" :disabled="!selectedIds.length" @click="handleBatchDelete">批量删除</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="symptom-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column label="症状术语" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="term-name">{{ row.symptomTerm }}</span>
          </template>
        </el-table-column>
        <el-table-column label="症状大类" width="120">
          <template #default="{ row }">
            <span class="cat-tag" :style="categoryStyle(row.symptomCategory)">
              <span class="cat-dot" :style="{ background: categoryStyle(row.symptomCategory).color }" />
              {{ row.symptomCategory }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="同义词" min-width="200">
          <template #default="{ row }">
            <div class="syn-list">
              <span v-for="(w, i) in row.synonymWords" :key="i" class="syn-chip">{{ w }}</span>
              <span v-if="!row.synonymWords?.length" class="syn-empty">暂无同义词</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="默认严重等级" width="150" align="center">
          <template #default="{ row }">
            <div class="sev-cell">
              <span class="sev-bars">
                <span
                  v-for="n in SYMPTOM_MAX_SEVERITY"
                  :key="n"
                  class="sev-bar"
                  :class="{ on: n <= (row.severityDefault ?? 0) }"
                  :style="n <= (row.severityDefault ?? 0) ? { background: levelColor(row.severityDefault) } : {}"
                />
              </span>
              <span class="sev-text" :style="{ color: levelColor(row.severityDefault) }">
                {{ severityText(row.severityDefault) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="86" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="(val: string | number | boolean) => handleStatusToggle(row as SymptomRow, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="130">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" class="op-btn op-edit" @click="handleEdit(row as SymptomRow)">编辑</el-button>
            <el-button text size="small" class="op-btn op-del" @click="handleDelete(row as SymptomRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="selected-info" v-if="selectedIds.length">已选 {{ selectedIds.length }} 项</span>
        <span v-else class="page-total">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- ==================== 新增 / 编辑弹窗 ==================== -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      align-center
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="symptom-form">
        <el-form-item label="标准症状术语" prop="symptomTerm">
          <el-input
            v-model="form.symptomTerm"
            placeholder="例：持续情绪低落"
            maxlength="50"
            show-word-limit
            clearable
          />
        </el-form-item>

        <el-form-item label="症状大类" prop="symptomCategory">
          <el-select v-model="form.symptomCategory" placeholder="请选择症状大类" style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <div class="form-tip">情绪症状 / 躯体症状 / 认知症状 / 行为症状</div>
        </el-form-item>

        <el-form-item label="同义口语词" prop="synonymWords">
          <div class="synonym-editor">
            <el-tag
              v-for="(w, i) in form.synonymWords"
              :key="i"
              class="syn-chip"
              closable
              @close="removeSynonym(i)"
            >
              {{ w }}
            </el-tag>
            <el-input
              ref="synonymInputRef"
              v-model="synonymInput"
              size="small"
              class="synonym-input"
              placeholder="输入后回车添加"
              :maxlength="20"
              @keyup.enter="addSynonym"
              @blur="addSynonym"
            />
          </div>
          <div class="form-tip">用于 AI 识别用户口语化表达，如「睡不着」「翻来覆去」</div>
        </el-form-item>

        <el-form-item label="默认严重等级" prop="severityDefault">
          <div class="sev-picker">
            <span
              v-for="n in SYMPTOM_MAX_SEVERITY"
              :key="n"
              class="sev-seg"
              :class="{ on: n <= form.severityDefault }"
              :style="n <= form.severityDefault ? levelSegStyle(n) : {}"
              @click="form.severityDefault = n"
            >
              {{ n }}
            </span>
            <span class="sev-meta" :style="{ color: levelColor(form.severityDefault) }">
              {{ severityText(form.severityDefault) }}
            </span>
          </div>
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Search, Plus, RefreshRight } from "@element-plus/icons-vue";
import { ElInput, ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT, SYMPTOM_MAX_SEVERITY } from "@/shared/api/config";
import {
  getSymptomDictPage,
  addSymptomDict,
  updateSymptomDict,
  updateSymptomDictStatus,
  batchDeleteSymptomDict,
  getSymptomDictOptions,
} from "@/admin/api/symptom/symptom-dict";

/* ==================== 类型定义 ==================== */

interface SymptomRow {
  id: number;
  symptomTerm: string;
  symptomCategory: string;
  synonymWords?: string[];
  severityDefault?: number;
  status?: number;
  createdTime?: string;
  updatedTime?: string;
}

interface SymptomOptionItem {
  id: number;
  symptomTerm: string;
  severityDefault: number;
  synonymWords: string[];
}

interface SymptomOptionGroup {
  symptomCategory: string;
  terms: SymptomOptionItem[];
}

interface SymptomForm {
  id: number | null;
  symptomTerm: string;
  symptomCategory: string;
  synonymWords: string[];
  severityDefault: number;
  status: number;
}

/* ==================== 常量 & 映射 ==================== */

const FALLBACK_CATEGORIES = ["情绪症状", "躯体症状", "认知症状", "行为症状"] as const;

const SEVERITY_META: Record<number, { label: string; color: string; bg: string }> = {
  1: { label: "轻微", color: "#10b981", bg: "#d1fae5" },
  2: { label: "轻度", color: "#14b8a6", bg: "#ccfbf1" },
  3: { label: "中度", color: "#f59e0b", bg: "#fef3c7" },
  4: { label: "较重", color: "#f97316", bg: "#ffedd5" },
  5: { label: "严重", color: "#ef4444", bg: "#fee2e2" },
};

const CATEGORY_COLORS: Record<string, { color: string; bg: string; border: string }> = {
  情绪症状: { color: "#8b5cf6", bg: "#f5f3ff", border: "#ede9fe" },
  躯体症状: { color: "#0ea5e9", bg: "#f0f9ff", border: "#e0f2fe" },
  认知症状: { color: "#f59e0b", bg: "#fffbeb", border: "#fef3c7" },
  行为症状: { color: "#10b981", bg: "#f0fdf4", border: "#d1fae5" },
};

function levelColor(v?: number): string {
  if (v == null || !SEVERITY_META[v]) return "#94a3b8";
  return SEVERITY_META[v].color;
}

function severityText(v?: number): string {
  if (v == null || !SEVERITY_META[v]) return "未设置";
  return SEVERITY_META[v].label;
}

function levelSegStyle(v: number): Record<string, string> {
  const meta = SEVERITY_META[v];
  return { color: meta.color, background: meta.bg };
}

function categoryStyle(cat?: string): Record<string, string> {
  const meta = (cat && CATEGORY_COLORS[cat]) || { color: "#6366f1", bg: "#eef2ff", border: "#e0e7ff" };
  return { color: meta.color, background: meta.bg, borderColor: meta.border };
}

function formatTime(v?: string | number): string {
  if (v === undefined || v === null || v === "") return "—";
  // 后端可能返回时间戳（number）或 ISO 字符串；number 转 Date，字符串沿用原格式
  if (typeof v === "number") {
    const d = new Date(v);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
  return String(v).replace("T", " ").slice(0, 16);
}

/* ==================== 页面状态 ==================== */

const loading = ref(false);
const tableData = ref<SymptomRow[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);
const selectedIds = ref<number[]>([]);

const searchForm = reactive<{ symptomTerm: string; symptomCategory: string; status?: number }>({
  symptomTerm: "",
  symptomCategory: "",
  status: undefined,
});

const categoryOptions = ref<string[]>([]);
const enabledCount = ref(0);

/* ==================== 弹窗状态 ==================== */

const dialogVisible = ref(false);
const submitting = ref(false);
const dialogTitle = ref("新增症状");
const formRef = ref<FormInstance>();
const synonymInputRef = ref<InstanceType<typeof ElInput>>();
const synonymInput = ref("");

const emptyForm: SymptomForm = {
  id: null,
  symptomTerm: "",
  symptomCategory: "",
  synonymWords: [],
  severityDefault: 1,
  status: 1,
};

const form = reactive<SymptomForm>({ ...emptyForm });

const rules: FormRules = {
  symptomTerm: [{ required: true, message: "请输入标准症状术语", trigger: "blur" }],
  symptomCategory: [{ required: true, message: "请选择症状大类", trigger: "change" }],
  severityDefault: [{ required: true, message: "请选择默认严重等级", trigger: "change" }],
};

/* ==================== 数据加载 ==================== */

async function loadOptions() {
  try {
    const res: { data: { data: SymptomOptionGroup[] } } = await getSymptomDictOptions();
    const groups = res.data.data ?? [];
    categoryOptions.value = groups.map((g) => g.symptomCategory).filter(Boolean);
    enabledCount.value = groups.reduce((sum, g) => sum + (g.terms?.length ?? 0), 0);
    if (!categoryOptions.value.length) categoryOptions.value = [...FALLBACK_CATEGORIES];
  } catch {
    categoryOptions.value = [...FALLBACK_CATEGORIES];
  }
}

function buildParams() {
  return {
    pageNum: currentPage.value,
    pageSize: pageSize.value,
    symptomTerm: searchForm.symptomTerm || undefined,
    symptomCategory: searchForm.symptomCategory || undefined,
    status: searchForm.status,
  };
}

async function loadData() {
  loading.value = true;
  try {
    const res: { data: { data: { records: SymptomRow[]; total: number } } } = await getSymptomDictPage(buildParams());
    tableData.value = res.data.data?.records ?? [];
    total.value = res.data.data?.total ?? 0;
  } catch {
    ElMessage.error("加载症状字典失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}

/* ==================== 交互：搜索 / 重置 ==================== */

function handleSearch() {
  currentPage.value = 1;
  loadData();
}

function handleReset() {
  searchForm.symptomTerm = "";
  searchForm.symptomCategory = "";
  searchForm.status = undefined;
  currentPage.value = 1;
  loadData();
}

/* ==================== 交互：勾选 / 状态 ==================== */

function handleSelectionChange(rows: SymptomRow[]) {
  selectedIds.value = rows.map((r) => r.id);
}

async function handleStatusToggle(row: SymptomRow, val: string | number | boolean) {
  const next = val ? 1 : 0;
  try {
    await updateSymptomDictStatus(String(row.id), next);
    row.status = next;
    ElMessage.success(next === 1 ? `「${row.symptomTerm}」已启用` : `「${row.symptomTerm}」已禁用`);
  } catch {
    ElMessage.error("状态更新失败，请稍后重试");
  }
}

/* ==================== 交互：新增 / 编辑 ==================== */

function handleAdd() {
  Object.assign(form, emptyForm);
  dialogTitle.value = "新增症状";
  dialogVisible.value = true;
}

async function handleEdit(row: SymptomRow) {
  dialogTitle.value = "编辑症状";
  Object.assign(form, {
    id: row.id,
    symptomTerm: row.symptomTerm,
    symptomCategory: row.symptomCategory,
    synonymWords: [...(row.synonymWords ?? [])],
    severityDefault: row.severityDefault ?? 1,
    status: row.status ?? 1,
  });
  dialogVisible.value = true;
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    const payload = {
      symptomTerm: form.symptomTerm.trim(),
      symptomCategory: form.symptomCategory,
      synonymWords: form.synonymWords,
      severityDefault: form.severityDefault,
      status: form.status,
    };
    if (form.id == null) {
      await addSymptomDict(payload);
      ElMessage.success("症状新增成功");
    } else {
      await updateSymptomDict(String(form.id), payload);
      ElMessage.success("症状更新成功");
    }
    dialogVisible.value = false;
    await loadOptions();
    await loadData();
  } catch {
    ElMessage.error("保存失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
}

function handleDialogClosed() {
  formRef.value?.clearValidate();
  synonymInput.value = "";
}

/* ==================== 交互：同义词 ==================== */

function addSynonym() {
  const word = synonymInput.value.trim();
  synonymInput.value = "";
  if (!word) return;
  if (form.synonymWords.includes(word)) {
    ElMessage.warning("同义词已存在，请勿重复添加");
    return;
  }
  form.synonymWords.push(word);
}

function removeSynonym(index: number) {
  form.synonymWords.splice(index, 1);
}

/* ==================== 交互：删除 ==================== */

async function handleDelete(row: SymptomRow) {
  try {
    await ElMessageBox.confirm(`确定删除症状「${row.symptomTerm}」吗？删除后不可恢复。`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  try {
    await batchDeleteSymptomDict([row.id]);
    ElMessage.success("删除成功");
    loadData();
  } catch {
    ElMessage.error("删除失败，请稍后重试");
  }
}

async function handleBatchDelete() {
  const num = selectedIds.value.length;
  if (!num) return;
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${num} 个症状吗？删除后不可恢复。`, "批量删除确认", {
      type: "warning",
      confirmButtonText: "批量删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  try {
    await batchDeleteSymptomDict(selectedIds.value);
    ElMessage.success(`已删除 ${num} 个症状`);
    selectedIds.value = [];
    loadData();
  } catch {
    ElMessage.error("批量删除失败，请稍后重试");
  }
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  loadOptions();
  loadData();
});
</script>

<style scoped>
.symptom-page {
  --el-color-primary: #6366f1;
  --el-color-primary-light-3: #818cf8;
  --el-color-primary-light-5: #a5b4fc;
  --el-color-primary-light-7: #c7d2fe;
  --el-color-primary-light-8: #e0e7ff;
  --el-color-primary-light-9: #eef2ff;
  --el-color-primary-dark-2: #4338ca;
  --el-border-radius-base: 8px;

  display: flex;
  flex-direction: column;
  gap: 16px;
  color: #1e293b;
}

/* ==================== 统计卡 ==================== */

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.stat-card:hover {
  box-shadow: 0 6px 18px rgba(16, 24, 40, 0.08);
  transform: translateY(-1px);
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.stat-icon-indigo {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.stat-icon-amber {
  background: linear-gradient(135deg, #f59e0b, #f97316);
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}

.stat-icon-green {
  background: linear-gradient(135deg, #10b981, #059669);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.stat-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

/* ==================== 主卡片 ==================== */

.content-card {
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.toolbar-spacer {
  flex: 1;
}

.btn-icon {
  margin-right: 4px;
}

/* ==================== 表格 ==================== */

.symptom-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
}

.symptom-table :deep(.el-table__header th) {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.symptom-table :deep(.el-table__cell) {
  padding: 12px 0;
}

.term-name {
  font-weight: 600;
  color: #0f172a;
}

.cat-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.cat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.syn-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
}

.syn-chip {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 11.5px;
  border: 1px solid #e0e7ff;
  line-height: 1.5;
}

.syn-empty {
  font-size: 12px;
  color: #cbd5e1;
}

.sev-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sev-bars {
  display: flex;
  gap: 3px;
}

.sev-bar {
  width: 16px;
  height: 6px;
  border-radius: 3px;
  background: #e2e8f0;
  transition: background 0.2s ease;
}

.sev-bar.on {
  background: #6366f1;
}

.sev-text {
  font-size: 11.5px;
  font-weight: 600;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.op-btn {
  font-size: 12px;
  font-weight: 500;
}

.op-edit {
  color: #6366f1;
}

.op-edit:hover {
  color: #4f46e5;
}

.op-del {
  color: #ef4444;
}

.op-del:hover {
  color: #dc2626;
}

/* ==================== 分页 ==================== */

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.page-total,
.selected-info {
  font-size: 12px;
  color: #94a3b8;
}

.selected-info {
  color: #6366f1;
  font-weight: 500;
}

/* ==================== 弹窗表单 ==================== */

.symptom-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 2px;
}

.form-tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
}

/* 同义词编辑区 */
.synonym-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-height: 42px;
  padding: 8px 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.synonym-editor:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.12);
  background: #fff;
}

.synonym-editor :deep(.el-tag) {
  border: none;
}

.synonym-input {
  width: 180px;
  flex-shrink: 1;
}

.synonym-input :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  padding: 0 4px;
}

/* 严重等级选择器 */
.sev-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.sev-seg {
  width: 40px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #94a3b8;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s ease;
}

.sev-seg:hover {
  border-color: #6366f1;
  color: #6366f1;
}

.sev-seg.on {
  border-color: transparent;
}

.sev-meta {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 600;
  min-width: 34px;
}
</style>