<template>
  <div class="admin-scale-page">
    <!-- ==================== 统计卡 ==================== -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><Document /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">量表总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheck /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ enabledCount }}</div>
          <div class="stat-label">启用量表</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-purple">
          <el-icon :size="22"><FolderOpened /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ categoryCount }}</div>
          <div class="stat-label">分类数量</div>
        </div>
      </div>
    </div>

    <!-- ==================== 主卡片 ==================== -->
    <div class="content-card">
      <!-- 搜索条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.scaleName"
          placeholder="搜索量表名称"
          clearable
          style="width: 210px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="searchForm.scaleCategoryId"
          placeholder="量表分类"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option v-for="c in categoryList" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
        <el-select
          v-model="searchForm.status"
          placeholder="启用状态"
          clearable
          style="width: 130px"
          @change="handleSearch"
        >
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-select
          v-model="searchForm.deletedFlag"
          placeholder="删除状态"
          clearable
          style="width: 130px"
          @change="handleSearch"
        >
          <el-option label="正常" :value="0" />
          <el-option label="已删除" :value="1" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" plain @click="openCategoryDialog()">
          <el-icon class="btn-icon"><FolderOpened /></el-icon>分类管理
        </el-button>
        <el-button type="primary" @click="handleAdd">
          <el-icon class="btn-icon"><Plus /></el-icon>新增量表
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" class="scale-table" row-key="id">
        <el-table-column prop="id" label="ID" width="72" align="center" />
        <el-table-column label="量表名称" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="scale-name-cell">
              <span class="scale-name" @click="goDetail(row)">{{ row.scaleName }}</span>
              <el-tag v-if="row.currentVersionNo" size="small" class="ver-tag" effect="plain">{{ row.currentVersionNo }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="130">
          <template #default="{ row }">
            <span v-if="row.scaleCategoryName" class="cat-tag" :style="categoryStyle(row.scaleCategoryName)">
              <span class="cat-dot" :style="{ background: categoryStyle(row.scaleCategoryName).color }" />
              {{ row.scaleCategoryName }}
            </span>
            <span v-else class="cat-empty">未分类</span>
          </template>
        </el-table-column>
        <el-table-column label="题目数" width="90" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ row.questionCount ?? "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="维度数" width="90" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ row.dimensionCount ?? "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="96" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="row.deletedFlag === 1"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="(val: number | string | boolean) => handleStatusToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="136">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" class="op-btn op-detail" @click="goDetail(row)">详情</el-button>
            <el-button v-if="row.deletedFlag !== 1" text size="small" class="op-btn op-edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.deletedFlag !== 1" text size="small" class="op-btn op-del" @click="handleDelete(row)">删除</el-button>
            <el-button v-else text size="small" class="op-btn op-restore" @click="handleRestore(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空态 -->
      <el-empty v-if="!loading && !tableData.length" :image-size="100" description="暂无量表数据，点击右上角「新增量表」开始创建" />

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="page-total">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="loadData"
        />
      </div>
    </div>    <!-- ==================== 新增 / 编辑量表弹窗 ==================== -->
    <el-dialog
      v-model="scaleDialogVisible"
      :title="scaleDialogTitle"
      width="600px"
      align-center
      :close-on-click-modal="false"
      @closed="handleScaleDialogClosed"
    >
      <el-form ref="scaleFormRef" :model="scaleForm" :rules="scaleRules" label-width="110px" class="scale-form">
        <el-form-item label="量表名称" prop="scaleName">
          <el-input v-model="scaleForm.scaleName" placeholder="例：SAS 焦虑自评量表" maxlength="60" show-word-limit clearable />
        </el-form-item>

        <el-form-item label="量表分类" prop="scaleCategoryId">
          <el-select v-model="scaleForm.scaleCategoryId" placeholder="请选择分类（可选）" clearable style="width: 100%">
            <el-option v-for="c in categoryList" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
          <div class="form-tip">未选择分类时量表将归入「未分类」</div>
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="scaleForm.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">作答配置</el-divider>

        <el-form-item label="重复作答">
          <el-radio-group v-model="scaleForm.allowRepeat">
            <el-radio-button :value="1">允许</el-radio-button>
            <el-radio-button :value="0">禁止</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="scaleForm.allowRepeat === 1" label="冷却时长">
          <el-input-number
            v-model="scaleForm.coolMinutes"
            :min="0"
            :max="10080"
            :step="60"
            style="width: 200px"
            @change="(v: number | undefined) => { if (v != null) scaleForm.coolMinutes = v }"
          />
          <span class="unit-text">分钟</span>
          <div class="form-tip">两次作答间隔不得少于该时长，0 表示不限制</div>
        </el-form-item>

        <el-form-item label="作答限时">
          <el-input-number
            v-model="scaleForm.timeLimit"
            :min="0"
            :max="7200"
            :step="60"
            placeholder="不限时"
            style="width: 200px"
            @change="(v: number | undefined) => { if (v != null) scaleForm.timeLimit = v }"
          />
          <span class="unit-text">秒</span>
          <div class="form-tip">0 或留空表示不限时</div>
        </el-form-item>

        <el-form-item label="匿名测评">
          <el-radio-group v-model="scaleForm.anonymous">
            <el-radio-button :value="1">匿名</el-radio-button>
            <el-radio-button :value="0">实名</el-radio-button>
          </el-radio-group>
          <div class="form-tip">开启后用户作答不与身份信息关联</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="scaleDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="scaleSubmitting" @click="handleScaleSubmit">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 分类管理弹窗 ==================== -->
    <el-dialog
      v-model="categoryDialogVisible"
      title="量表分类管理"
      width="620px"
      align-center
      :close-on-click-modal="false"
    >
      <div class="category-toolbar">
        <span class="category-hint">共 {{ categoryList.length }} 个分类，支持新增、编辑、排序与删除</span>
        <div class="toolbar-spacer" />
        <el-button type="primary" size="small" :loading="categorySubmitting" @click="openCategoryEdit()">
          <el-icon class="btn-icon"><Plus /></el-icon>新增分类
        </el-button>
      </div>

      <el-table v-loading="categoryLoading" :data="categoryList" class="category-table" row-key="id">
        <el-table-column label="排序" width="120" align="center">
          <template #default="{ row }">
            <div class="sort-actions">
              <el-button
                text
                size="small"
                class="sort-btn"
                :disabled="row.id === categoryList[0]?.id"
                @click="handleMoveSort(row, -1)"
              >
                <el-icon><CaretTop /></el-icon>
              </el-button>
              <span class="sort-num">{{ row.sort ?? "·" }}</span>
              <el-button
                text
                size="small"
                class="sort-btn"
                :disabled="row.id === categoryList[categoryList.length - 1]?.id"
                @click="handleMoveSort(row, 1)"
              >
                <el-icon><CaretBottom /></el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类名称" min-width="170" />
        <el-table-column label="量表数" width="90" align="center">
          <template #default="{ row }">
            <span class="num-text">{{ row.scaleCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button text size="small" class="op-btn op-edit" @click="openCategoryEdit(row as CategoryRow)">编辑</el-button>
            <el-button text size="small" class="op-btn op-del" @click="handleCategoryDelete(row as CategoryRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="categoryDialogVisible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 分类编辑/新增子弹窗 -->
    <el-dialog
      v-model="categoryEditVisible"
      :title="categoryEditTitle"
      width="420px"
      align-center
      :close-on-click-modal="false"
      @closed="categoryEditRef?.clearValidate"
    >
      <el-form ref="categoryEditRef" :model="categoryForm" :rules="categoryRules" label-width="90px">
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="categoryForm.categoryName" placeholder="请输入分类名称" maxlength="30" show-word-limit clearable />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sort" :min="0" :max="9999" style="width: 180px" />
          <div class="form-tip">数值越小越靠前，也可在列表中直接调整</div>
        </el-form-item>      </el-form>
      <template #footer>
        <el-button @click="categoryEditVisible = false">取 消</el-button>
        <el-button type="primary" :loading="categorySubmitting" @click="handleCategorySubmit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Search, RefreshRight, Plus, FolderOpened, Document, CircleCheck, CaretTop, CaretBottom } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import { getScalePage, addScale, updateScale, updateScaleStatus, deleteScale, restoreScale } from "@/admin/api/scale/scale";
import { getScaleCategoryPage, addScaleCategory, updateScaleCategory, updateScaleCategorySort, deleteScaleCategory } from "@/admin/api/scale/category";

/* ==================== 类型定义 ==================== */

interface ScaleRow {
  id: number;
  scaleName: string;
  scaleCategoryId?: number | null;
  scaleCategoryName?: string | null;
  status: number;
  allowRepeat: number;
  coolMinutes?: number | null;
  timeLimit?: number | null;
  anonymous: number;
  currentVersionNo?: string | null;
  questionCount?: number | null;
  dimensionCount?: number | null;
  createdTime?: string;
  deletedFlag?: number;
}

interface CategoryRow {
  id: number;
  categoryName: string;
  sort?: number;
  scaleCount?: number;
}

interface ScaleForm {
  id: number | null;
  scaleName: string;
  scaleCategoryId: number | null;
  status: number;
  allowRepeat: number;
  coolMinutes: number;
  timeLimit: number;
  anonymous: number;
}

type PageResp = { records: ScaleRow[]; total: number };

/* ==================== 颜色 & 工具 ==================== */

const CATEGORY_COLORS: Record<string, { color: string; bg: string; border: string }> = {
  焦虑: { color: "#ef4444", bg: "#fef2f2", border: "#fecaca" },
  抑郁: { color: "#3b82f6", bg: "#eff6ff", border: "#bfdbfe" },
  睡眠: { color: "#f59e0b", bg: "#fffbeb", border: "#fde68a" },
  压力: { color: "#10b981", bg: "#f0fdf4", border: "#a7f3d0" },
  创伤: { color: "#8b5cf6", bg: "#f5f3ff", border: "#ddd6fe" },
  情绪障碍: { color: "#ec4899", bg: "#fdf2f8", border: "#fbcfe8" },
  综合评估: { color: "#0ea5e9", bg: "#f0f9ff", border: "#bae6fd" },
  社会功能: { color: "#06b6d4", bg: "#ecfeff", border: "#a5f3fc" },
  进食障碍: { color: "#f97316", bg: "#fff7ed", border: "#fed7aa" },
  精神症状: { color: "#64748b", bg: "#f1f5f9", border: "#cbd5e1" },
};

/** 分类徽章配色：命中预设取主题色，否则按名称哈希从候选调色板取色 */
const PALETTE = [
  { color: "#ff5b6e", bg: "#fff0f1", border: "#ffd6da" },
  { color: "#2f80ed", bg: "#edf5ff", border: "#c9dffb" },
  { color: "#f2994a", bg: "#fff7ed", border: "#fde3c2" },
  { color: "#10b981", bg: "#ecfdf5", border: "#b9efd6" },
  { color: "#8b5cf6", bg: "#f5f3ff", border: "#ddd6fe" },
  { color: "#06b6d4", bg: "#ecfeff", border: "#a5f3fc" },
  { color: "#ec4899", bg: "#fdf2f8", border: "#fbcfe8" },
  { color: "#64748b", bg: "#f8fafc", border: "#cbd5e1" },
];

function hashStr(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i += 1) h = (h * 31 + s.charCodeAt(i)) & 0xffffffff;
  return h;
}

function categoryStyle(cat?: string | null): Record<string, string> {
  const meta = (cat && CATEGORY_COLORS[cat]) || PALETTE[(hashStr(cat ?? "") >>> 0) % PALETTE.length] || {
    color: "#6366f1",
    bg: "#eef2ff",
    border: "#e0e7ff",
  };
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

const router = useRouter();

const loading = ref(false);
const tableData = ref<ScaleRow[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);

const searchForm = reactive<{ scaleName: string; scaleCategoryId: number | null; status?: number; deletedFlag?: number }>({
  scaleName: "",
  scaleCategoryId: null,
  status: undefined,
  deletedFlag: undefined,
});

const categoryList = ref<CategoryRow[]>([]);
const enabledCount = ref(0);
const categoryCount = ref(0);

/* ==================== 数据加载 ==================== */

function buildParams() {
  return {
    pageNum: currentPage.value,
    pageSize: pageSize.value,
    scaleName: searchForm.scaleName.trim() || undefined,
    scaleCategoryId: searchForm.scaleCategoryId ?? undefined,
    status: searchForm.status,
    deletedFlag: searchForm.deletedFlag,
  };
}

function unwrapPage(res: { data: { data?: PageResp } }): PageResp {
  const d = res?.data?.data;
  return { records: d?.records ?? [], total: d?.total ?? 0 };
}

async function loadData() {
  loading.value = true;
  try {
    const res = await getScalePage(buildParams());
    const d = unwrapPage(res as any);
    tableData.value = d.records;
    total.value = d.total;
    await refreshEnabledCount();
  } catch {
    ElMessage.error("加载量表列表失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}

async function refreshEnabledCount() {
  try {
    const res = await getScalePage({ pageNum: 1, pageSize: 1, status: 1 });
    enabledCount.value = res.data.data?.total ?? 0;
  } catch {
    enabledCount.value = 0;
  }
}

async function loadCategories() {
  try {
    const res: { data: { data?: { records: CategoryRow[] } } } = await getScaleCategoryPage({ pageNum: 1, pageSize: 1000 });
    categoryList.value = res.data.data?.records ?? [];
    categoryCount.value = categoryList.value.length;
  } catch {
    categoryList.value = [];
    categoryCount.value = 0;
  }
}

/* ==================== 交互：搜索 / 重置 ==================== */

function handleSearch() {
  currentPage.value = 1;
  loadData();
}

function handleReset() {
  searchForm.scaleName = "";
  searchForm.scaleCategoryId = null;
  searchForm.status = undefined;
  searchForm.deletedFlag = undefined;
  currentPage.value = 1;
  loadData();
}

/* ==================== 交互：详情 / 状态 ==================== */

function goDetail(row: any) {
  router.push(`/admin/scale/${row.id}`);
}

async function handleStatusToggle(row: any, val: string | number | boolean) {
  const next = val ? 1 : 0;
  try {
    await updateScaleStatus(row.id, next);
    row.status = next;
    await refreshEnabledCount();
    ElMessage.success(next === 1 ? `「${row.scaleName}」已启用` : `「${row.scaleName}」已禁用`);
  } catch {
    ElMessage.error("状态更新失败，请稍后重试");
  }
}

/* ==================== 交互：新增 / 编辑量表 ==================== */

const scaleDialogVisible = ref(false);
const scaleSubmitting = ref(false);
const scaleDialogTitle = ref("新增量表");
const scaleFormRef = ref<FormInstance>();

const emptyScaleForm: ScaleForm = {
  id: null,
  scaleName: "",
  scaleCategoryId: null,
  status: 1,
  allowRepeat: 1,
  coolMinutes: 1440,
  timeLimit: 0,
  anonymous: 0,
};

const scaleForm = reactive<ScaleForm>({ ...emptyScaleForm });

const scaleRules: FormRules = {
  scaleName: [{ required: true, message: "请输入量表名称", trigger: "blur" }],
};

function handleAdd() {
  Object.assign(scaleForm, emptyScaleForm);
  scaleDialogTitle.value = "新增量表";
  scaleDialogVisible.value = true;
}

function handleEdit(row: any) {
  Object.assign(scaleForm, {
    id: row.id,
    scaleName: row.scaleName,
    scaleCategoryId: row.scaleCategoryId ?? null,
    status: row.status ?? 1,
    allowRepeat: row.allowRepeat ?? 1,
    coolMinutes: row.coolMinutes ?? 1440,
    timeLimit: row.timeLimit ?? 0,
    anonymous: row.anonymous ?? 0,
  });
  scaleDialogTitle.value = "编辑量表";
  scaleDialogVisible.value = true;
}

function handleScaleDialogClosed() {
  scaleFormRef.value?.clearValidate();
}

async function handleScaleSubmit() {
  const valid = await scaleFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  scaleSubmitting.value = true;
  try {
    const payload = {
      scaleName: scaleForm.scaleName.trim(),
      scaleCategoryId: scaleForm.scaleCategoryId,
      status: scaleForm.status,
      allowRepeat: scaleForm.allowRepeat,
      coolMinutes: scaleForm.allowRepeat === 1 ? scaleForm.coolMinutes : 0,
      timeLimit: scaleForm.timeLimit || null,
      anonymous: scaleForm.anonymous,
    };
    if (scaleForm.id == null) {
      await addScale(payload);
      ElMessage.success("量表新增成功");
    } else {
      await updateScale(scaleForm.id, payload);
      ElMessage.success("量表更新成功");
    }
    scaleDialogVisible.value = false;
    await loadData();
    await loadCategories();
  } catch {
    ElMessage.error("保存失败，请稍后重试");
  } finally {
    scaleSubmitting.value = false;
  }
}

/* ==================== 交互：删除 / 恢复 ==================== */

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除量表「${row.scaleName}」吗？删除后其版本、题目将一并进入回收站，可随时恢复。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScale(row.id);
    ElMessage.success("删除成功");
    loadData();
  } catch {
    ElMessage.error("删除失败，请稍后重试");
  }
}async function handleRestore(row: any) {
  try {
    await restoreScale(row.id);
    ElMessage.success(`「${row.scaleName}」已恢复`);
    loadData();
  } catch {
    ElMessage.error("恢复失败，请稍后重试");
  }
}

/* ==================== 交互：分类管理 ==================== */

const categoryDialogVisible = ref(false);
const categoryLoading = ref(false);
const categorySubmitting = ref(false);

function openCategoryDialog() {
  categoryDialogVisible.value = true;
  loadCategories();
}

const categoryEditVisible = ref(false);
const categoryEditTitle = ref("新增分类");
const categoryEditRef = ref<FormInstance>();

const emptyCategoryForm: CategoryRow = { id: 0, categoryName: "", sort: 0 };
const categoryForm = reactive<CategoryRow>({ ...emptyCategoryForm });

const categoryRules: FormRules = {
  categoryName: [{ required: true, message: "请输入分类名称", trigger: "blur" }],
};

function openCategoryEdit(row?: CategoryRow) {
  if (row) {
    Object.assign(categoryForm, { id: row.id, categoryName: row.categoryName, sort: row.sort ?? 0 });
    categoryEditTitle.value = `编辑分类「${row.categoryName}」`;
  } else {
    Object.assign(categoryForm, emptyCategoryForm, { sort: catMaxSort() + 1 });
    categoryEditTitle.value = "新增分类";
  }
  categoryEditVisible.value = true;
}

function catMaxSort(): number {
  return categoryList.value.reduce((max, c) => Math.max(max, c.sort ?? 0), 0);
}

async function handleCategorySubmit() {
  const valid = await categoryEditRef.value?.validate().catch(() => false);
  if (!valid) return;
  categorySubmitting.value = true;
  try {
    const payload = { categoryName: categoryForm.categoryName.trim(), sort: categoryForm.sort };
    if (categoryForm.id) {
      await updateScaleCategory(categoryForm.id, payload);
      ElMessage.success("分类更新成功");
    } else {
      await addScaleCategory(payload);
      ElMessage.success("分类新增成功");
    }
    categoryEditVisible.value = false;
    await loadCategories();
  } catch {
    ElMessage.error("保存失败，请稍后重试");
  } finally {
    categorySubmitting.value = false;
  }
}

async function handleMoveSort(row: any, dir: -1 | 1) {
  const list = [...categoryList.value].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id);
  const idx = list.findIndex((c) => c.id === row.id);
  if (idx < 0) return;
  const target = list[idx + dir];
  if (!target) return;

  const tmp = list[idx].sort ?? list[idx].id;
  list[idx].sort = target.sort ?? target.id;
  target.sort = tmp;
  list.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id);
  const order = list.map((c) => c.id);
  categoryList.value = [...list];

  try {
    await Promise.all(order.map((id, i) => updateScaleCategorySort(id, i + 1)));
    ElMessage.success("排序已更新");
  } catch {
    ElMessage.error("排序保存失败，请稍后重试");
    await loadCategories();
  }
}

async function handleCategoryDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${row.categoryName}」吗？该分类下存在量表时将禁止删除。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleCategory(row.id);
    ElMessage.success("分类删除成功");
    await loadCategories();
    await loadData();
  } catch {
    ElMessage.error("删除失败，可能该分类下仍有量表，请先删除或迁移其中的量表");
  }
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  loadData();
  loadCategories();
});
</script>

<style scoped>
.admin-scale-page {
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
  color: #fff;
  flex-shrink: 0;
}

.stat-icon-indigo {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.stat-icon-green {
  background: linear-gradient(135deg, #10b981, #059669);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.stat-icon-purple {
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3);
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

.scale-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
}

.scale-table :deep(.el-table__header th) {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.scale-table :deep(.el-table__cell) {
  padding: 12px 0;
}

.scale-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scale-name {
  font-weight: 600;
  color: #0f172a;
  cursor: pointer;
  transition: color 0.2s ease;
}

.scale-name:hover {
  color: #6366f1;
}

.ver-tag {
  background: #eef2ff;
  border-color: #e0e7ff;
  color: #4f46e5;
  font-variant-numeric: tabular-nums;
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

.cat-empty {
  font-size: 12px;
  color: #cbd5e1;
}

.num-text {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
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

.op-detail {
  color: #3b82f6;
}

.op-detail:hover {
  color: #2563eb;
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

.op-restore {
  color: #10b981;
}

.op-restore:hover {
  color: #059669;
}

/* ==================== 分页 ==================== */

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.page-total {
  font-size: 12px;
  color: #94a3b8;
}

/* ==================== 量表弹窗表单 ==================== */

.scale-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.form-tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
}

.unit-text {
  margin-left: 8px;
  font-size: 13px;
  color: #64748b;
}

.scale-form :deep(.el-divider__text) {
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;
  letter-spacing: 1px;
}

.scale-form :deep(.el-radio-button__inner) {
  font-size: 12px;
}

/* ==================== 分类管理 ==================== */

.category-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.category-hint {
  font-size: 12px;
  color: #94a3b8;
}

.category-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
}

.sort-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.sort-btn {
  font-size: 14px;
  color: #94a3b8;
}

.sort-btn:not(.is-disabled):hover {
  color: #6366f1;
}

.sort-num {
  min-width: 20px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  font-variant-numeric: tabular-nums;
}

.category-table :deep(.el-table__cell) {
  padding: 10px 0;
}
</style>