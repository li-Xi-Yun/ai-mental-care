<template>
  <div class="ai-node-page">
    <!-- ==================== 统计卡 ==================== -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">🧠</div>
        <div class="stat-body">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">节点总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">✅</div>
        <div class="stat-body">
          <div class="stat-num">{{ enabledCount }}</div>
          <div class="stat-label">启用节点</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-amber">🗂️</div>
        <div class="stat-body">
          <div class="stat-num">{{ groupOptions.length }}</div>
          <div class="stat-label">节点分组</div>
        </div>
      </div>
    </div>

    <!-- ==================== 主卡片 ==================== -->
    <div class="content-card">
      <!-- 搜索筛选条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索节点标识 / 名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select v-model="searchForm.nodeGroup" placeholder="节点分组" clearable filterable style="width: 150px" @change="handleSearch">
          <el-option v-for="g in groupOptions" :key="g.nodeGroup" :label="g.nodeGroup" :value="g.nodeGroup ?? ''" />
        </el-select>

        <el-select v-model="searchForm.modelType" placeholder="模型类型" clearable style="width: 150px" @change="handleSearch">
          <el-option v-for="m in MODEL_TYPE_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>

        <el-select v-model="searchForm.enabled" placeholder="启用状态" clearable style="width: 120px" @change="handleSearch">
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

        <el-button :disabled="!selectedRows.length" @click="handleBatchToggle(true)">
          批量启用
        </el-button>
        <el-button :disabled="!selectedRows.length" @click="handleBatchToggle(false)">
          批量禁用
        </el-button>
        <el-button type="primary" @click="handleAdd">
          <el-icon class="btn-icon"><Plus /></el-icon>新增节点
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="ai-node-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column label="节点信息" min-width="220">
          <template #default="{ row }">
            <div class="node-cell">
              <span class="node-name">{{ row.nodeName || "-" }}</span>
              <code class="node-key">{{ row.nodeKey || "-" }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分组" width="130">
          <template #default="{ row }">
            <span class="group-tag" :style="groupStyle(row.nodeGroup)">
              <span class="group-dot" :style="{ background: groupStyle(row.nodeGroup).color }" />
              {{ row.nodeGroup || "未分组" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="模型" width="180">
          <template #default="{ row }">
            <div class="model-cell">
              <span class="model-type-badge" :style="modelTypeStyle(row.modelType)">
                {{ modelTypeLabel(row.modelType) }}
              </span>
              <span class="model-name">{{ row.modelName || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="96" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="(val: string | number | boolean) => handleStatusToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <span class="ver-tag">v{{ row.version ?? "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.updatedTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.remark" class="remark-text">{{ row.remark }}</span>
            <span v-else class="remark-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" class="op-btn op-edit" @click="handleViewDetail(row)">详情</el-button>
            <el-button text size="small" class="op-btn op-edit" @click="handleEdit(row)">编辑</el-button>
            <el-button text size="small" class="op-btn op-del" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无节点数据" :image-size="80" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span v-if="selectedRows.length" class="selected-info">已选 {{ selectedRows.length }} 项</span>
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
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="node-form">
        <el-form-item label="节点标识 nodeKey" prop="nodeKey">
          <el-input
            v-model="form.nodeKey"
            placeholder="如 psychologicalState"
            maxlength="50"
            clearable
            :disabled="isEdit"
          />
          <div class="form-tip">新增后不可修改，仅支持字母 / 数字 / 下划线</div>
        </el-form-item>

        <el-form-item label="节点名称" prop="nodeName">
          <el-input v-model="form.nodeName" placeholder="如 心理状态与症状评估" maxlength="50" clearable />
        </el-form-item>

        <el-form-item label="节点分组" prop="nodeGroup">
          <el-select v-model="form.nodeGroup" placeholder="选择已存在的分组，或输入新分组" filterable allow-create default-first-option clearable style="width: 100%">
            <el-option v-for="g in groupOptions" :key="g.nodeGroup" :label="g.nodeGroup" :value="g.nodeGroup ?? ''" />
          </el-select>
          <div class="form-tip">例如 process（主流程）/ input（输入）/ knowledge（知识）</div>
        </el-form-item>

        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" placeholder="请选择模型类型" style="width: 100%">
            <el-option v-for="m in MODEL_TYPE_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="启用状态">
          <el-radio-group v-model="form.enabled">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="3"
            maxlength="20000"
            show-word-limit
            placeholder="填写节点运行时的角色与任务约定……"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="节点用途备注" maxlength="255" show-word-limit />
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
import { computed, onMounted, reactive, ref, shallowRef } from "vue";
import { useRouter } from "vue-router";
import { Search, Plus, RefreshRight } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import type {
  AiNodeConfigSimpleVO,
  AiNodeGroupVO,
} from "@/admin/api/ai-node/config";
import {
  pageAiNodeConfig,
  createAiNodeConfig,
  updateAiNodeConfig,
  deleteAiNodeConfig,
  batchUpdateAiNodeConfigEnabled,
  getAiNodeGroups,
  getAiNodeConfigDetail,
} from "@/admin/api/ai-node/config";
import type { AiNodeConfigVO } from "@/admin/api/ai-node/config";

/* ==================== 常量 & 映射 ==================== */

/** 模型类型：0-本地Ollama 1-DeepSeek 2-阿里DashScope 3-OpenAI兼容 */
const MODEL_TYPE_OPTIONS: ReadonlyArray<{ label: string; value: number }> = [
  { label: "本地 Ollama", value: 0 },
  { label: "DeepSeek", value: 1 },
  { label: "阿里 DashScope", value: 2 },
  { label: "OpenAI 兼容", value: 3 },
];

/** 模型类型彩色徽章配色（与后台 indigo 主题协调） */
const MODEL_TYPE_COLORS: ReadonlyArray<{ color: string; bg: string; border: string }> = [
  { color: "#7c3aed", bg: "#f5f3ff", border: "#ede9fe" }, // Ollama 紫
  { color: "#4f46e5", bg: "#eef2ff", border: "#e0e7ff" }, // DeepSeek Indigo
  { color: "#0ea5e9", bg: "#f0f9ff", border: "#e0f2fe" }, // DashScope 蓝
  { color: "#10b981", bg: "#f0fdf4", border: "#d1fae5" }, // OpenAI 绿
];

/** 分组标签配色（动态 hash 到一组柔和色板，保证同组同色） */
const GROUP_COLORS: ReadonlyArray<{ color: string; bg: string; border: string }> = [
  { color: "#6366f1", bg: "#eef2ff", border: "#e0e7ff" },
  { color: "#8b5cf6", bg: "#f5f3ff", border: "#ede9fe" },
  { color: "#0ea5e9", bg: "#f0f9ff", border: "#e0f2fe" },
  { color: "#f59e0b", bg: "#fffbeb", border: "#fef3c7" },
  { color: "#10b981", bg: "#f0fdf4", border: "#d1fae5" },
  { color: "#f43f5e", bg: "#fff1f2", border: "#ffe4e6" },
];

function modelTypeLabel(type?: number | null): string {
  const meta = MODEL_TYPE_OPTIONS.find((m) => m.value === type);
  return meta?.label ?? "未知";
}

function modelTypeStyle(type?: number | null): Record<string, string> {
  const meta = MODEL_TYPE_COLORS[type ?? -1] ?? { color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0" };
  return { color: meta.color, backgroundColor: meta.bg, borderColor: meta.border };
}

/** 稳定字符串 hash（用于分组取色，保证同分组颜色一致） */
function hashString(str: string): number {
  let h = 0;
  for (let i = 0; i < str.length; i++) {
    h = (h << 5) - h + str.charCodeAt(i);
    h |= 0;
  }
  return Math.abs(h);
}

function groupStyle(group?: string | null): Record<string, string> {
  const meta = group
    ? GROUP_COLORS[hashString(group) % GROUP_COLORS.length]
    : { color: "#94a3b8", bg: "#f8fafc", border: "#eef1f6" };
  return { color: meta.color, backgroundColor: meta.bg, borderColor: meta.border };
}

function formatTime(v?: string | number | null): string {
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
const tableData = shallowRef<AiNodeConfigSimpleVO[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = DEFAULT_PAGE_SIZE;
const selectedRows = shallowRef<AiNodeConfigSimpleVO[]>([]);

const searchForm = reactive<{ keyword: string; nodeGroup: string; modelType?: number; enabled?: number }>({
  keyword: "",
  nodeGroup: "",
  modelType: undefined,
  enabled: undefined,
});

const groupOptions = computed<AiNodeGroupVO[]>(() => rawGroupOptions.value);
const rawGroupOptions = shallowRef<AiNodeGroupVO[]>([]);

const enabledCount = computed(() => tableData.value.filter((r) => r.enabled === 1).length);

/* ==================== 弹窗状态 ==================== */

const dialogVisible = ref(false);
const submitting = ref(false);
const dialogTitle = ref("新增节点");
const isEdit = ref(false);
const formRef = ref<FormInstance>();
const editingId = ref<number | null>(null);
const editingVersion = ref(0);

interface NodeForm {
  nodeKey: string;
  nodeName: string;
  nodeGroup: string;
  modelType: number;
  enabled: number;
  systemPrompt: string;
  remark: string;
}

const emptyForm: NodeForm = {
  nodeKey: "",
  nodeName: "",
  nodeGroup: "",
  modelType: 1,
  enabled: 1,
  systemPrompt: "",
  remark: "",
};

const form = reactive<NodeForm>({ ...emptyForm });

const rules: FormRules = {
  nodeKey: [
    { required: true, message: "请输入节点标识", trigger: "blur" },
    { pattern: /^[A-Za-z0-9_]+$/, message: "仅支持字母 / 数字 / 下划线", trigger: "blur" },
  ],
  nodeName: [{ required: true, message: "请输入节点名称", trigger: "blur" }],
  nodeGroup: [{ required: true, message: "请选择或输入节点分组", trigger: "change" }],
  modelType: [{ required: true, message: "请选择模型类型", trigger: "change" }],
  systemPrompt: [{ required: true, message: "请输入系统提示词", trigger: "blur" }],
};

/* ==================== 数据加载 ==================== */

async function loadGroups() {
  try {
    const res = await getAiNodeGroups();
    rawGroupOptions.value = res.data.data ?? [];
  } catch {
    rawGroupOptions.value = [];
  }
}

function buildParams() {
  // 单输入框需同时支持「节点标识 / 名称」搜索，但后端对 nodeKey 与 nodeName 为 AND 语义，
  // 同一关键词同时下发两个条件会互相过滤导致恒为 0 条（历史缺陷：搜索不可用）。
  // 前端按输入特征选择更匹配的字段下发：纯 ASCII 无空格（节点标识形态）→ nodeKey，其余（中文名称）→ nodeName。
  // 根治建议：后端提供关键字 OR 查询（已在第五轮报告中登记）。
  const keyword = searchForm.keyword.trim();
  const keyLikely = /^[\x21-\x7E]+$/.test(keyword);
  return {
    pageNum: currentPage.value,
    pageSize,
    nodeKey: keyword && keyLikely ? keyword : undefined,
    nodeName: keyword && !keyLikely ? keyword : undefined,
    nodeGroup: searchForm.nodeGroup || undefined,
    modelType: searchForm.modelType,
    enabled: searchForm.enabled,
  };
}

async function loadData() {
  loading.value = true;
  try {
    const res = await pageAiNodeConfig(buildParams());
    tableData.value = res.data.data?.records ?? [];
    total.value = res.data.data?.total ?? 0;
  } catch (e) {
    ElMessage.error((e as Error)?.message || "节点列表加载失败，请稍后重试");
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
  searchForm.keyword = "";
  searchForm.nodeGroup = "";
  searchForm.modelType = undefined;
  searchForm.enabled = undefined;
  currentPage.value = 1;
  loadData();
}

/* ==================== 交互：勾选 / 状态 ==================== */

function handleSelectionChange(rows: AiNodeConfigSimpleVO[]) {
  selectedRows.value = rows;
}

async function handleStatusToggle(row: AiNodeConfigSimpleVO, val: string | number | boolean) {
  const next = val ? 1 : 0;
  try {
    await batchUpdateAiNodeConfigEnabled({ nodeKeys: [String(row.nodeKey ?? "")], enabled: next });
    ElMessage.success(next === 1 ? `「${row.nodeName}」已启用` : `「${row.nodeName}」已禁用`);
    // 列表数据可能跨页，整体刷新以同步状态与统计
    await loadData();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "状态更新失败，请稍后重试");
  }
}

async function handleBatchToggle(enabled: boolean) {
  if (!selectedRows.value.length) return;
  const keys = selectedRows.value.map((r) => String(r.nodeKey ?? "")).filter(Boolean);
  const num = keys.length;
  if (!num) return;
  const text = enabled ? "启用" : "禁用";
  try {
    await ElMessageBox.confirm(`确定将选中的 ${num} 个节点${text}吗？`, `批量${text}确认`, {
      type: "warning",
      confirmButtonText: `批量${text}`,
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  try {
    await batchUpdateAiNodeConfigEnabled({ nodeKeys: keys, enabled: enabled ? 1 : 0 });
    ElMessage.success(`已${text} ${num} 个节点`);
    selectedRows.value = [];
    await Promise.all([loadData(), loadGroups()]);
  } catch (e) {
    ElMessage.error((e as Error)?.message || `批量${text}失败，请稍后重试`);
  }
}

/* ==================== 交互：新增 / 编辑 ==================== */

function handleAdd() {
  isEdit.value = false;
  editingId.value = null;
  editingVersion.value = 0;
  Object.assign(form, emptyForm);
  dialogTitle.value = "新增节点";
  dialogVisible.value = true;
}

async function handleEdit(row: AiNodeConfigSimpleVO) {
  isEdit.value = true;
  editingId.value = row.id ?? null;
  editingVersion.value = row.version ?? 0;
  Object.assign(form, {
    nodeKey: row.nodeKey ?? "",
    nodeName: row.nodeName ?? "",
    nodeGroup: row.nodeGroup ?? "",
    modelType: row.modelType ?? 1,
    enabled: row.enabled ?? 1,
    systemPrompt: "",
    remark: row.remark ?? "",
  });
  dialogTitle.value = "编辑节点";
  dialogVisible.value = true;
  // 拉取完整配置（含系统提示词）回填
  if (row.id != null) {
    try {
      const res = await getAiNodeConfigDetail(row.id);
      const detail: AiNodeConfigVO | undefined = res.data.data;
      if (detail) {
        form.systemPrompt = detail.systemPrompt ?? "";
        if (detail.version != null) editingVersion.value = detail.version;
      }
    } catch {
      ElMessage.warning("系统提示词加载失败，可稍后在详情页补充");
    }
  }
}

function handleViewDetail(row: AiNodeConfigSimpleVO) {
  if (row.id == null) {
    ElMessage.warning("该节点缺少ID，无法查看详情");
    return;
  }
  router.push(`/admin/ai-node/${row.id}`);
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    const shared = {
      nodeName: form.nodeName.trim(),
      nodeGroup: form.nodeGroup.trim() || undefined,
      modelType: form.modelType,
      enabled: form.enabled,
      remark: form.remark.trim() || undefined,
    };
    if (isEdit.value) {
      if (editingId.value == null) {
        ElMessage.error("缺少节点ID，无法更新");
        return;
      }
      await updateAiNodeConfig(editingId.value, {
        ...shared,
        nodeKey: form.nodeKey || undefined,
        systemPrompt: form.systemPrompt.trim(),
        maxToken: 2048,
        temperature: 0.7,
        topP: 0.85,
        retryMaxAttempts: 3,
        retryDelay: 1000,
        retryMultiplier: 2,
        version: editingVersion.value,
      });
      ElMessage.success("节点更新成功");
    } else {
      await createAiNodeConfig({
        ...shared,
        nodeKey: form.nodeKey.trim(),
        systemPrompt: form.systemPrompt.trim(),
        maxToken: 2048,
        temperature: 0.7,
        topP: 0.85,
        retryMaxAttempts: 3,
        retryDelay: 1000,
        retryMultiplier: 2,
      });
      ElMessage.success("节点新增成功");
    }
    dialogVisible.value = false;
    await Promise.all([loadData(), loadGroups()]);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
}

function handleDialogClosed() {
  formRef.value?.clearValidate();
}

/* ==================== 交互：删除 ==================== */

async function handleDelete(row: AiNodeConfigSimpleVO) {
  try {
    await ElMessageBox.confirm(`确定删除节点「${row.nodeName}」吗？删除后不可恢复。`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  if (row.id == null) return;
  try {
    await deleteAiNodeConfig(row.id);
    ElMessage.success("删除成功");
    if (tableData.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1;
    }
    await Promise.all([loadData(), loadGroups()]);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  loadGroups();
  loadData();
});
</script>

<style scoped>
.ai-node-page {
  /* 后台主题色 indigo */
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

.stat-body {
  min-width: 0;
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

.ai-node-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
}

.ai-node-table :deep(.el-table__header th) {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.ai-node-table :deep(.el-table__cell) {
  padding: 12px 0;
}

.node-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.node-name {
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-key {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 11.5px;
  color: #6366f1;
  background: #eef2ff;
  border-radius: 4px;
  padding: 1px 6px;
  align-self: flex-start;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-tag {
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

.group-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.model-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.model-type-badge {
  flex-shrink: 0;
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
}

.model-name {
  font-size: 12px;
  color: #475569;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ver-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11.5px;
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.remark-text {
  font-size: 12px;
  color: #475569;
}

.remark-empty {
  color: #cbd5e1;
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

.node-form :deep(.el-form-item__label) {
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
</style>