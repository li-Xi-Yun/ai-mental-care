<template>
  <div class="ai-node-detail-page">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/admin/ai-node" class="breadcrumb-link">AI节点配置</router-link>
      <span class="breadcrumb-sep">›</span>
      <el-icon class="breadcrumb-icon"><Cpu /></el-icon>
      <span class="breadcrumb-current">{{ form.nodeName || detail?.nodeName || "节点详情" }}</span>
    </div>

    <!-- 加载态 -->
    <div v-if="pageLoading" class="page-state">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 失败态 -->
    <div v-else-if="pageError || !detail" class="page-state">
      <el-empty description="节点详情加载失败">
        <el-button type="primary" @click="handleReload">重新加载</el-button>
      </el-empty>
    </div>

    <template v-else>
      <!-- 顶部概览卡片 -->
      <div class="card overview-card">
        <div class="overview-head">
          <div class="node-title">
            <el-icon class="node-icon"><Cpu /></el-icon>
            <h1 class="node-name">{{ form.nodeName || "未命名节点" }}</h1>
            <el-tag :type="enabledTagType" size="small" effect="light">{{ enabledLabel }}</el-tag>
            <el-tag type="info" size="small" effect="plain">版本 v{{ detail.version ?? "-" }}</el-tag>
          </div>
          <div class="overview-actions">
            <el-button @click="handleReload" :loading="pageLoading">
              <el-icon class="btn-icon"><Refresh /></el-icon>刷新
            </el-button>
            <el-button type="primary" :loading="savingConfig" @click="handleSaveConfig">
              <el-icon class="btn-icon"><Save /></el-icon>保存配置
            </el-button>
          </div>
        </div>
        <div class="overview-meta">
          <span class="meta-item"><span class="meta-label">nodeKey</span><code class="meta-mono">{{ detail.nodeKey || "-" }}</code></span>
          <span class="meta-dot">·</span>
          <span class="meta-item"><span class="meta-label">分组</span>{{ form.nodeGroup || "-" }}</span>
          <span class="meta-dot">·</span>
          <span class="meta-item"><span class="meta-label">模型</span>{{ currentModelLabel }}</span>
          <span class="meta-dot">·</span>
          <span class="meta-item"><span class="meta-label">排序</span>{{ form.sort }}</span>
          <span class="meta-dot">·</span>
          <span class="meta-item"><span class="meta-label">创建</span>{{ detail.createdTime || "-" }}</span>
          <span class="meta-dot">·</span>
          <span class="meta-item"><span class="meta-label">更新</span>{{ detail.updatedTime || "-" }}</span>
        </div>
      </div>

      <!-- 基本信息 + 模型设置 -->
      <div class="info-grid">
        <div class="card">
          <div class="card-head">
            <el-icon><Document /></el-icon>
            <h3>基本信息</h3>
          </div>
          <el-form label-position="top" class="form-grid cols-2">
            <el-form-item label="节点名称">
              <el-input v-model="form.nodeName" placeholder="请输入节点名称" maxlength="50" />
            </el-form-item>
            <el-form-item label="节点标识 nodeKey">
              <el-input :model-value="detail.nodeKey || ''" disabled />
            </el-form-item>
            <el-form-item label="分组">
              <el-input v-model="form.nodeGroup" placeholder="如 diagnosis / process" maxlength="50" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch
                v-model="form.enabled"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="禁用"
              />
            </el-form-item>
            <el-form-item label="排序序号">
              <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="节点用途备注" maxlength="255" show-word-limit />
            </el-form-item>
          </el-form>
        </div>

        <div class="card">
          <div class="card-head">
            <el-icon><Cpu /></el-icon>
            <h3>模型设置</h3>
          </div>
          <el-form label-position="top">
            <el-form-item label="模型服务商">
              <el-select v-model="form.modelType" style="width: 100%">
                <el-option v-for="m in modelTypeOptions" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
            </el-form-item>
            <el-form-item :label="modelNameLabel">
              <el-input v-model="modelNameValue" placeholder="未配置时沿用平台全局默认模型" maxlength="100" />
            </el-form-item>
            <el-form-item label="输出格式">
              <el-select v-model="form.responseFormat" style="width: 100%">
                <el-option label="自由文本 TEXT" :value="0" />
                <el-option label="结构化 JSON" :value="1" />
              </el-select>
            </el-form-item>
            <el-form-item label="停止序列（JSON 数组）">
              <el-input
                v-model="form.stopSequences"
                placeholder='如 ["\\n\\n\\n","```"]'
                maxlength="255"
              />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- 生成参数 + 重试策略 -->
      <div class="info-grid">
        <div class="card">
          <div class="card-head">
            <el-icon><Setting /></el-icon>
            <h3>生成参数</h3>
            <span class="card-tip">Temperature / Tokens / 采样</span>
          </div>
          <el-form label-position="top" class="form-grid cols-3">
            <el-form-item label="温度 Temperature">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="最大输出 Token 数">
              <el-input-number v-model="form.maxToken" :min="1" :max="32768" :step="64" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="Top-P 核采样">
              <el-input-number v-model="form.topP" :min="0" :max="1" :step="0.05" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="Top-K">
              <el-input-number v-model="form.topK" :min="1" :max="500" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="频率惩罚 Frequency Penalty">
              <el-input-number v-model="form.frequencyPenalty" :min="-2" :max="2" :step="0.1" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="存在惩罚 Presence Penalty">
              <el-input-number v-model="form.presencePenalty" :min="-2" :max="2" :step="0.1" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-form>
        </div>

        <div class="card">
          <div class="card-head">
            <el-icon><RefreshRight /></el-icon>
            <h3>重试策略</h3>
            <span class="card-tip">指数退避重试</span>
          </div>
          <el-form label-position="top" class="form-grid cols-3">
            <el-form-item label="最大重试次数">
              <el-input-number v-model="form.retryMaxAttempts" :min="0" :max="10" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="初始间隔（毫秒）">
              <el-input-number v-model="form.retryDelay" :min="0" :max="60000" :step="100" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="间隔乘数">
              <el-input-number v-model="form.retryMultiplier" :min="1" :max="10" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item class="span-3">
              <div class="form-hint">第 N 次重试等待间隔约 = 初始间隔 × 乘数^(N-1)，超出最大次数后放弃本次调用。</div>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- System Prompt 编辑区 -->
      <div class="card prompt-card">
        <div class="card-head">
          <el-icon><Reading /></el-icon>
          <h3>System Prompt 系统提示词</h3>
          <span class="card-tip">独立保存，后端自动记录变更历史</span>
        </div>
        <el-input
          v-model="promptText"
          type="textarea"
          :rows="10"
          maxlength="20000"
          show-word-limit
          class="prompt-textarea"
          placeholder="请输入系统提示词，约定AI角色与任务……"
        />
        <div class="prompt-footer">
          <span class="prompt-meta">配置版本 v{{ promptVersion || "-" }} · 已保存版本 {{ promptText.length }} 字符（含空白）</span>
          <div class="prompt-actions">
            <el-button @click="loadPrompt">取消修改</el-button>
            <el-button type="primary" :loading="savingPrompt" @click="handleSavePrompt">
              <el-icon class="btn-icon"><Select /></el-icon>保存提示词
            </el-button>
          </div>
        </div>
      </div>

      <!-- Tab 区：变更历史 / 缓存管理 -->
      <div class="card tabs-card">
        <el-tabs v-model="activeTab">
          <!-- 变更历史 -->
          <el-tab-pane label="变更历史" name="history">
            <div class="tab-toolbar">
              <el-input
                v-model="historyKeyword"
                placeholder="按变更摘要搜索"
                clearable
                style="width: 220px"
                @keyup.enter="handleHistorySearch"
                @clear="handleHistorySearch"
              >
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-button type="primary" @click="handleHistorySearch">查询</el-button>
              <div class="toolbar-spacer"></div>
              <span class="muted">共 {{ historyTotal }} 条变更记录</span>
            </div>

            <el-table v-loading="historyLoading" :data="historyList" stripe class="history-table">
              <el-table-column prop="changeSummary" label="变更摘要" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="summary-text">{{ row.changeSummary || "-" }}</span>
                </template>
              </el-table-column>
              <el-table-column label="Prompt 变更（简要）" min-width="230">
                <template #default="{ row }">
                  <div v-if="hasPromptChange(row)" class="prompt-diff">
                    <div class="diff-row">
                      <span class="diff-badge">旧</span>
                      <span class="diff-text diff-old">{{ row.oldSystemPrompt || "（无）" }}</span>
                    </div>
                    <div class="diff-row">
                      <span class="diff-badge diff-new-badge">新</span>
                      <span class="diff-text diff-new">{{ row.newSystemPrompt || "（无）" }}</span>
                    </div>
                  </div>
                  <span v-else class="muted">仅参数调整</span>
                </template>
              </el-table-column>
              <el-table-column label="模型变更" width="150">
                <template #default="{ row }">
                  <template v-if="modelChanged(row)">
                    <el-tag size="small" type="info" effect="plain">{{ modelTypeLabel(row.oldModelType) }}</el-tag>
                    <span class="diff-arrow">→</span>
                    <el-tag size="small" type="success" effect="plain">{{ modelTypeLabel(row.newModelType) }}</el-tag>
                  </template>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="版本" width="90">
                <template #default="{ row }">
                  <span v-if="row.oldVersion != null || row.newVersion != null">v{{ row.oldVersion ?? "?" }} → v{{ row.newVersion ?? "?" }}</span>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdByName" label="变更人" width="110">
                <template #default="{ row }">{{ row.createdByName || "-" }}</template>
              </el-table-column>
              <el-table-column prop="createdTime" label="变更时间" width="170" />
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" text size="small" @click="openHistoryDetail(row)">详情</el-button>
                  <el-button type="warning" text size="small" @click="handleRollback(row)">回滚</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无可展示的变更记录" :image-size="70" />
              </template>
            </el-table>

            <div class="pagination-bar">
              <el-pagination
                v-model:current-page="historyPage"
                :page-size="historyPageSize"
                :total="historyTotal"
                :layout="PAGINATION_LAYOUT"
                background
                @current-change="loadHistory"
              />
            </div>
          </el-tab-pane>

          <!-- 缓存管理 -->
          <el-tab-pane label="缓存管理" name="cache">
            <div v-loading="cacheLoading" class="cache-panel">
              <div class="cache-stats">
                <div class="stat-box">
                  <div class="stat-value">{{ cacheStatus?.cacheSize ?? "-" }}</div>
                  <div class="stat-label">已缓存节点数量</div>
                </div>
                <div class="stat-hint">
                  <p>AI 节点配置以内存缓存服务线上调用：修改配置 / 提示词或执行回滚后，需刷新缓存使新配置生效。</p>
                  <p class="sub">当前节点 key：<code class="meta-mono">{{ detail.nodeKey || "-" }}</code></p>
                </div>
              </div>

              <div class="cache-keys">
                <span class="muted cache-keys-label">已缓存节点：</span>
                <div v-if="cacheStatus?.nodeKeys?.length" class="cache-key-tags">
                  <el-tag
                    v-for="k in cacheStatus.nodeKeys"
                    :key="k"
                    size="small"
                    effect="light"
                    :type="k === detail.nodeKey ? 'success' : 'info'"
                  >{{ k }}</el-tag>
                </div>
                <span v-else class="muted">缓存为空（首次调用或已清空）</span>
              </div>

              <div class="cache-actions">
                <el-button type="primary" plain :loading="cacheMutating" @click="handleRefreshAllCache">
                  <el-icon class="btn-icon"><Refresh /></el-icon>全量刷新缓存
                </el-button>
                <el-button type="primary" :loading="cacheMutating" @click="handleRefreshNodeCache">
                  <el-icon class="btn-icon"><RefreshRight /></el-icon>刷新当前节点
                </el-button>
                <el-button text @click="loadCacheStatus">重查状态</el-button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 变更历史详情弹窗 -->
      <el-dialog v-model="historyDialogVisible" title="变更历史详情" width="720" @closed="historyDetail = null">
        <div v-loading="historyDetailLoading" class="history-detail">
          <template v-if="historyDetail">
            <div class="hd-head">
              <el-tag size="small" type="info" effect="plain">版本 {{ versionRange }}</el-tag>
              <span class="hd-meta">操作人：{{ historyDetail.createdByName || "-" }} · {{ historyDetail.createdTime || "-" }}</span>
            </div>
            <el-alert v-if="historyDetail.changeSummary" :title="historyDetail.changeSummary" type="info" :closable="false" class="hd-summary" />
            <div class="hd-block">
              <div class="hd-label">修改前 System Prompt</div>
              <pre class="hd-pre old">{{ historyDetail.oldSystemPrompt || "（空）" }}</pre>
            </div>
            <div class="hd-block">
              <div class="hd-label">修改后 System Prompt</div>
              <pre class="hd-pre new">{{ historyDetail.newSystemPrompt || "（空）" }}</pre>
            </div>
            <div v-if="modelChanged(historyDetail)" class="hd-block">
              <div class="hd-label">模型变更</div>
              <div class="hd-models">
                <el-tag size="small" type="info" effect="plain">{{ modelTypeLabel(historyDetail.oldModelType) }}</el-tag>
                <span class="diff-arrow">→</span>
                <el-tag size="small" type="success" effect="plain">{{ modelTypeLabel(historyDetail.newModelType) }}</el-tag>
              </div>
            </div>
          </template>
          <el-empty v-else-if="!historyDetailLoading" description="未加载到该记录详情" :image-size="70" />
        </div>
        <template #footer>
          <el-button @click="historyDialogVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import {
  getAiNodeConfigDetail,
  updateAiNodeConfig,
  getAiNodePrompt,
  updateAiNodePrompt,
  getAiNodeCacheStatus,
  refreshAiNodeCacheAll,
  refreshAiNodeCache,
} from "@/admin/api/ai-node/config";
import type {
  AiNodeCacheVO,
  AiNodeConfigVO,
  AiNodeConfigUpdateDTO,
} from "@/admin/api/ai-node/config";
import {
  getAiNodeHistoryPage,
  getAiNodeHistoryDetail,
  rollbackAiNodeHistory,
} from "@/admin/api/ai-node/history";
import type { AiNodeConfigHistoryVO } from "@/admin/api/ai-node/history";

/* ==================== 路由参数 ==================== */
const route = useRoute();
const nodeConfigId = String(route.params.id ?? "");

/* ==================== 常量 ==================== */
const modelTypeOptions: ReadonlyArray<{ label: string; value: number }> = [
  { label: "本地 Ollama", value: 0 },
  { label: "DeepSeek", value: 1 },
  { label: "阿里 DashScope", value: 2 },
  { label: "OpenAI 兼容", value: 3 },
];

const MODEL_NAME_FIELDS = [
  "ollamaModelName",
  "deepseekModelName",
  "dashscopeModelName",
  "openaiModelName",
] as const;

interface NodeFormState {
  nodeName: string;
  nodeGroup: string;
  remark: string;
  enabled: number;
  sort: number;
  modelType: number;
  deepseekModelName: string;
  ollamaModelName: string;
  dashscopeModelName: string;
  openaiModelName: string;
  responseFormat: number;
  stopSequences: string;
  maxToken: number;
  temperature: number;
  topP: number;
  topK: number;
  frequencyPenalty: number;
  presencePenalty: number;
  retryMaxAttempts: number;
  retryDelay: number;
  retryMultiplier: number;
}

/* ==================== 页面状态 ==================== */
const pageLoading = ref(true);
const pageError = ref(false);
const detail = shallowRef<AiNodeConfigVO | null>(null);

const savingConfig = ref(false);
const savingPrompt = ref(false);
const activeTab = ref("history");

const form = reactive<NodeFormState>({
  nodeName: "",
  nodeGroup: "",
  remark: "",
  enabled: 1,
  sort: 0,
  modelType: 1,
  deepseekModelName: "",
  ollamaModelName: "",
  dashscopeModelName: "",
  openaiModelName: "",
  responseFormat: 0,
  stopSequences: "",
  maxToken: 2048,
  temperature: 0.7,
  topP: 0.85,
  topK: 50,
  frequencyPenalty: 0,
  presencePenalty: 0,
  retryMaxAttempts: 3,
  retryDelay: 1000,
  retryMultiplier: 2,
});

const promptText = ref("");
const promptVersion = ref(0);

const cacheStatus = shallowRef<AiNodeCacheVO | null>(null);
const cacheLoading = ref(false);
const cacheMutating = ref(false);

const historyList = shallowRef<AiNodeConfigHistoryVO[]>([]);
const historyTotal = ref(0);
const historyPage = ref(1);
const historyPageSize = DEFAULT_PAGE_SIZE;
const historyLoading = ref(false);
const historyKeyword = ref("");

const historyDialogVisible = ref(false);
const historyDetailLoading = ref(false);
const historyDetail = shallowRef<AiNodeConfigHistoryVO | null>(null);

/* ==================== 派生状态 ==================== */
const enabledTagType = computed<"success" | "info">(() => (form.enabled === 1 ? "success" : "info"));
const enabledLabel = computed(() => (form.enabled === 1 ? "启用" : "禁用"));

const modelNameLabel = computed(() => {
  const type = modelTypeOptions.find((m) => m.value === form.modelType);
  return `${type?.label ?? "模型"}名称`;
});

const modelNameValue = computed<string>({
  get: () => form[MODEL_NAME_FIELDS[form.modelType]],
  set: (val: string) => {
    form[MODEL_NAME_FIELDS[form.modelType]] = val;
  },
});

const currentModelLabel = computed(() => {
  const type = modelTypeOptions.find((m) => m.value === form.modelType);
  const name = String(form[MODEL_NAME_FIELDS[form.modelType]] || "");
  return name ? `${type?.label ?? "未知"} · ${name}` : type?.label ?? "未配置";
});

const versionRange = computed(() => {
  const oldV = historyDetail.value?.oldVersion;
  const newV = historyDetail.value?.newVersion;
  if (oldV != null || newV != null) return `v${oldV ?? "?"} → v${newV ?? "?"}`;
  return "-";
});

/* ==================== 工具函数 ==================== */
function toFiniteNumber(value: unknown, fallback: number): number {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function modelTypeLabel(type: number | null | undefined): string {
  if (type == null) return "未知";
  return modelTypeOptions.find((m) => m.value === type)?.label ?? `类型 ${type}`;
}

function modelChanged(row: AiNodeConfigHistoryVO): boolean {
  return row.oldModelType != null && row.newModelType != null && row.oldModelType !== row.newModelType;
}

function hasPromptChange(row: AiNodeConfigHistoryVO): boolean {
  return Boolean(row.oldSystemPrompt || row.newSystemPrompt);
}

/* ==================== 数据加载 ==================== */
function applyDetail(data: AiNodeConfigVO) {
  form.nodeName = data.nodeName ?? "";
  form.nodeGroup = data.nodeGroup ?? "";
  form.remark = data.remark ?? "";
  form.enabled = data.enabled ?? 1;
  form.sort = data.sort ?? 0;
  form.modelType = data.modelType ?? 1;
  form.deepseekModelName = data.deepseekModelName ?? "";
  form.ollamaModelName = data.ollamaModelName ?? "";
  form.dashscopeModelName = data.dashscopeModelName ?? "";
  form.openaiModelName = data.openaiModelName ?? "";
  form.maxToken = toFiniteNumber(data.maxToken, 2048);
  form.temperature = toFiniteNumber(data.temperature, 0.7);
  form.topP = toFiniteNumber(data.topP, 0.85);
  form.topK = toFiniteNumber(data.topK, 50);
  form.frequencyPenalty = toFiniteNumber(data.frequencyPenalty, 0);
  form.presencePenalty = toFiniteNumber(data.presencePenalty, 0);
  form.responseFormat = data.responseFormat ?? 0;
  form.stopSequences = data.stopSequences ?? "";
  form.retryMaxAttempts = toFiniteNumber(data.retryMaxAttempts, 3);
  form.retryDelay = toFiniteNumber(data.retryDelay, 1000);
  form.retryMultiplier = toFiniteNumber(data.retryMultiplier, 2);
}

async function loadConfig() {
  pageLoading.value = true;
  pageError.value = false;
  try {
    const res = await getAiNodeConfigDetail(nodeConfigId);
    const data = res.data.data;
    detail.value = data ?? null;
    if (!data) {
      pageError.value = true;
      return;
    }
    applyDetail(data);
  } catch (e) {
    pageError.value = true;
    ElMessage.error((e as Error)?.message || "节点详情加载失败");
  } finally {
    pageLoading.value = false;
  }
}

async function loadPrompt() {
  if (!nodeConfigId) return;
  try {
    const res = await getAiNodePrompt(nodeConfigId);
    const data = res.data.data;
    if (data) {
      promptText.value = data.systemPrompt ?? "";
      promptVersion.value = data.version ?? 0;
      return;
    }
  } catch (e) {
    ElMessage.error((e as Error)?.message || "提示词加载失败");
  }
  // 兜底：直接使用详情中的提示词
  if (detail.value) {
    promptText.value = detail.value.systemPrompt ?? "";
    promptVersion.value = detail.value.version ?? 0;
  }
}

async function loadHistory(page = historyPage.value) {
  historyLoading.value = true;
  try {
    const res = await getAiNodeHistoryPage(nodeConfigId, {
      pageNum: page,
      pageSize: historyPageSize,
      changeSummary: historyKeyword.value.trim() || null,
    });
    const pageData = res.data.data;
    historyList.value = pageData?.records ?? [];
    historyTotal.value = pageData?.total ?? 0;
    historyPage.value = page;
  } catch (e) {
    historyList.value = [];
    historyTotal.value = 0;
    ElMessage.error((e as Error)?.message || "变更历史加载失败");
  } finally {
    historyLoading.value = false;
  }
}

async function loadCacheStatus() {
  cacheLoading.value = true;
  try {
    const res = await getAiNodeCacheStatus();
    cacheStatus.value = res.data.data ?? null;
  } catch (e) {
    cacheStatus.value = null;
    ElMessage.error((e as Error)?.message || "缓存状态获取失败");
  } finally {
    cacheLoading.value = false;
  }
}

/* ==================== 操作 ==================== */
function buildConfigDTO(): AiNodeConfigUpdateDTO {
  const source = detail.value;
  return {
    nodeName: form.nodeName.trim(),
    nodeKey: source?.nodeKey ?? undefined,
    nodeGroup: form.nodeGroup.trim() || undefined,
    systemPrompt: source?.systemPrompt ?? promptText.value.trim(),
    modelType: form.modelType,
    deepseekModelName: form.deepseekModelName.trim() || undefined,
    ollamaModelName: form.ollamaModelName.trim() || undefined,
    dashscopeModelName: form.dashscopeModelName.trim() || undefined,
    openaiModelName: form.openaiModelName.trim() || undefined,
    maxToken: form.maxToken,
    temperature: form.temperature,
    topP: form.topP,
    topK: form.topK,
    frequencyPenalty: form.frequencyPenalty,
    presencePenalty: form.presencePenalty,
    responseFormat: form.responseFormat,
    stopSequences: form.stopSequences.trim() || undefined,
    retryMaxAttempts: form.retryMaxAttempts,
    retryDelay: form.retryDelay,
    retryMultiplier: form.retryMultiplier,
    enabled: form.enabled,
    sort: form.sort,
    remark: form.remark.trim() || undefined,
    version: source?.version ?? 0,
  };
}

/** 保存基本信息 / 模型 / 参数 */
async function handleSaveConfig() {
  if (!form.nodeName.trim()) {
    ElMessage.warning("节点名称不能为空");
    return;
  }
  if (!promptText.value.trim()) {
    ElMessage.warning("系统提示词不能为空，请先在下方编辑并保存");
    return;
  }
  savingConfig.value = true;
  try {
    await updateAiNodeConfig(nodeConfigId, buildConfigDTO());
    ElMessage.success("节点配置已保存");
    await Promise.all([loadConfig(), loadHistory()]);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    savingConfig.value = false;
  }
}

/** 独立保存 System Prompt */
async function handleSavePrompt() {
  if (!promptText.value.trim()) {
    ElMessage.warning("系统提示词不能为空");
    return;
  }
  savingPrompt.value = true;
  try {
    await updateAiNodePrompt(nodeConfigId, {
      systemPrompt: promptText.value.trim(),
      version: promptVersion.value,
    });
    ElMessage.success("提示词已保存，变更历史已记录");
    await Promise.all([loadConfig(), loadPrompt(), loadHistory()]);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "提示词保存失败，请稍后重试");
  } finally {
    savingPrompt.value = false;
  }
}

/** 历史搜索 */
function handleHistorySearch() {
  historyPage.value = 1;
  loadHistory();
}

/** 查看历史详情 */
async function openHistoryDetail(row: AiNodeConfigHistoryVO) {
  historyDialogVisible.value = true;
  historyDetailLoading.value = true;
  historyDetail.value = null;
  try {
    const res = await getAiNodeHistoryDetail(String(row.id ?? ""));
    historyDetail.value = res.data.data ?? row;
  } catch (e) {
    historyDetail.value = row;
    ElMessage.error((e as Error)?.message || "历史详情加载失败");
  } finally {
    historyDetailLoading.value = false;
  }
}

/** 回滚到指定历史版本 */
async function handleRollback(row: AiNodeConfigHistoryVO) {
  try {
    await ElMessageBox.confirm(
      `确定将节点「${form.nodeName || detail.value?.nodeName}」回滚至 ${row.oldVersion != null ? `v${row.oldVersion} ` : ""}${row.createdTime || ""} 由「${row.createdByName || "系统"}」提交的版本吗？\n回滚后会自动记录新的变更记录并刷新缓存。`,
      "回滚确认",
      {
        type: "warning",
        confirmButtonText: "确认回滚",
        cancelButtonText: "取消",
        cancelButtonClass: "el-button--default",
      },
    );
  } catch {
    return; // 用户取消
  }
  try {
    await rollbackAiNodeHistory(nodeConfigId, String(row.id ?? ""));
    ElMessage.success("回滚成功，配置已还原");
    await Promise.all([loadConfig(), loadPrompt(), loadHistory(), loadCacheStatus()]);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "回滚失败，请稍后重试");
  }
}

/** 全量刷新缓存 */
async function handleRefreshAllCache() {
  cacheMutating.value = true;
  try {
    await refreshAiNodeCacheAll();
    ElMessage.success("已全量刷新缓存");
    await loadCacheStatus();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "缓存刷新失败");
  } finally {
    cacheMutating.value = false;
  }
}

/** 刷新当前节点缓存 */
async function handleRefreshNodeCache() {
  cacheMutating.value = true;
  try {
    await refreshAiNodeCache(nodeConfigId);
    ElMessage.success("当前节点缓存已刷新");
    await loadCacheStatus();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "缓存刷新失败");
  } finally {
    cacheMutating.value = false;
  }
}

/** 全量刷新页面数据（顶部刷新按钮） */
async function handleReload() {
  await Promise.all([loadConfig(), loadPrompt(), loadHistory(), loadCacheStatus()]);
}

onMounted(() => {
  loadConfig();
  loadPrompt();
  loadHistory();
  loadCacheStatus();
});
</script>

<style scoped>
.ai-node-detail-page {
  color: #1e293b;
}
/* ---------- 面包屑 ---------- */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 16px;
}
.breadcrumb-link {
  color: #6366f1;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s ease;
}
.breadcrumb-link:hover {
  color: #4f46e5;
  text-decoration: underline;
}
.breadcrumb-sep {
  color: #cbd5e1;
}
.breadcrumb-icon {
  color: #818cf8;
}
.breadcrumb-current {
  color: #1e293b;
  font-weight: 600;
}

/* ---------- 卡片通用 ---------- */
.card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px 22px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: box-shadow 0.2s ease;
}
.card:hover {
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6366f1;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid #f1f5f9;
}
.card-head h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}
.card-tip {
  flex: 1;
  text-align: right;
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.info-grid .card {
  margin-bottom: 0;
}

.form-grid {
  display: grid;
  gap: 0 16px;
}
.form-grid.cols-2 {
  grid-template-columns: 1fr 1fr;
}
.form-grid.cols-3 {
  grid-template-columns: 1fr 1fr 1fr;
}
.form-grid .span-3 {
  grid-column: span 3;
}
.form-grid .el-form-item {
  margin-bottom: 14px;
}
.form-hint {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px;
  width: 100%;
}

/* ---------- 顶部概览 ---------- */
.overview-card {
  background: linear-gradient(180deg, #ffffff 0%, #f8faff 100%);
  border: 1px solid #e0e7ff;
}
.overview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.node-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.node-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 20px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
.node-name {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.overview-actions {
  display: flex;
  gap: 8px;
}
.mr {
  margin-right: 4px;
}
.btn-icon {
  margin-right: 4px;
}
.overview-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #eef2f7;
  font-size: 12px;
  color: #475569;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.meta-label {
  color: #94a3b8;
}
.meta-mono {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  background: #f1f5f9;
  border-radius: 4px;
  padding: 1px 6px;
  color: #4f46e5;
}
.meta-dot {
  color: #cbd5e1;
}

/* ---------- Prompt 编辑区 ---------- */
.prompt-card .prompt-textarea :deep(.el-textarea__inner) {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  line-height: 1.7;
  border-radius: 8px;
}
.prompt-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}
.prompt-meta {
  font-size: 12px;
  color: #94a3b8;
}
.prompt-actions {
  display: flex;
  gap: 8px;
}

/* ---------- Tab 区 ---------- */
.tabs-card .el-tabs__item.is-active {
  color: #6366f1;
}
.tabs-card .el-tabs__item:hover {
  color: #4f46e5;
}
.tabs-card .el-tabs__active-bar {
  background-color: #6366f1;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.toolbar-spacer {
  flex: 1;
}
.muted {
  color: #94a3b8;
  font-size: 12px;
}
.summary-text {
  font-size: 13px;
  color: #334155;
}
.history-table {
  width: 100%;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* ---------- Prompt diff ---------- */
.prompt-diff {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.diff-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  line-height: 1.5;
  min-width: 0;
}
.diff-badge {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background: #fef2f2;
  color: #ef4444;
  margin-top: 1px;
}
.diff-new-badge {
  background: #f0fdf4;
  color: #16a34a;
}
.diff-text {
  display: inline-block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
  color: #475569;
}
.diff-new {
  color: #16a34a;
}
.diff-old {
  color: #ef4444;
}
.diff-arrow {
  color: #94a3b8;
  margin: 0 4px;
}

/* ---------- 缓存管理 ---------- */
.cache-panel {
  padding: 4px 0;
}
.cache-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  margin-bottom: 16px;
}
.stat-box {
  min-width: 140px;
}
.stat-value {
  font-size: 34px;
  font-weight: 700;
  color: #6366f1;
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}
.stat-hint {
  flex: 1;
  min-width: 240px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.7;
}
.stat-hint p {
  margin: 0;
}
.stat-hint .sub {
  margin-top: 4px;
  color: #94a3b8;
}
.cache-keys {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 16px;
}
.cache-keys-label {
  flex-shrink: 0;
  line-height: 24px;
}
.cache-key-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cache-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ---------- 历史详情弹窗 ---------- */
.history-detail {
  min-height: 120px;
}
.hd-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.hd-meta {
  font-size: 12px;
  color: #94a3b8;
}
.hd-summary {
  margin-bottom: 12px;
}
.hd-block {
  margin-bottom: 14px;
}
.hd-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 6px;
}
.hd-pre {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  border-radius: 8px;
  padding: 12px 14px;
  margin: 0;
  max-height: 200px;
  overflow-y: auto;
}
.hd-pre.old {
  background: #fef2f2;
  border: 1px solid #fee2e2;
  color: #9f1239;
}
.hd-pre.new {
  background: #f0fdf4;
  border: 1px solid #dcfce7;
  color: #166534;
}
.hd-models {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ---------- 页面状态 ---------- */
.page-state {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 40px 24px;
}
</style>