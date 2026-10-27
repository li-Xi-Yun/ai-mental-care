<template>
  <div class="live2d-detail-page">
    <!-- 加载态 -->
    <div v-if="pageLoading" class="page-state">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 失败态 -->
    <div v-else-if="pageError || !detail" class="page-state">
      <el-empty description="模型详情加载失败">
        <el-button type="primary" @click="handleReload">重新加载</el-button>
      </el-empty>
    </div>

    <template v-else>
      <!-- 顶部概览 + 操作区 -->
      <div class="detail-head">
        <div class="head-left">
          <h2 class="model-title">{{ detail.aliasName || detail.modelName || "-" }}</h2>
          <el-tag :type="enabledTagType" size="small" effect="light">{{ enabledLabel }}</el-tag>
        </div>
        <div class="head-ops">
          <el-button type="primary" plain size="small" @click="handleEdit">
            <el-icon class="btn-icon"><Edit /></el-icon>编辑
          </el-button>
          <el-button
            :type="detail.enabled === 1 ? 'warning' : 'success'"
            plain
            size="small"
            @click="handleStatusToggle"
          >
            {{ detail.enabled === 1 ? "禁用" : "启用" }}
          </el-button>
          <el-button type="danger" plain size="small" @click="handleDelete">
            <el-icon class="btn-icon"><Delete /></el-icon>删除
          </el-button>
        </div>
      </div>

      <!-- 基础信息 -->
      <div class="info-card">
        <div class="card-title">基础信息</div>
        <div class="info-grid">
          <div class="info-item">
            <div class="info-label">模型名称</div>
            <div class="info-value">{{ detail.modelName || "—" }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">模型别名</div>
            <div class="info-value">{{ detail.aliasName || "—" }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">启用状态</div>
            <div class="info-value">
              <el-tag :type="enabledTagType" size="small" effect="light">{{ enabledLabel }}</el-tag>
            </div>
          </div>
          <div class="info-item">
            <div class="info-label">封面图</div>
            <div class="info-value">
              <img
                v-if="detail.coverImagePath"
                :src="resolveResourceUrl(detail.coverImagePath)"
                class="cover-preview"
                alt=""
              />
              <span v-else class="value-empty">暂无封面</span>
            </div>
          </div>
          <div class="info-item">
            <div class="info-label">排序值</div>
            <div class="info-value">{{ detail.sort ?? "—" }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">model3 入口路径</div>
            <div class="info-value code-text">{{ detail.modelUrl || "—" }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">模型包根目录</div>
            <div class="info-value code-text">{{ detail.modelRelativePath || "—" }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">创建时间</div>
            <div class="info-value time-text">{{ formatTime(detail.createdTime, FORMAT_DATETIME) }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">更新时间</div>
            <div class="info-value time-text">{{ formatTime(detail.updatedTime, FORMAT_DATETIME) }}</div>
          </div>
          <div class="info-item info-item-full">
            <div class="info-label">备注</div>
            <div class="info-value">{{ detail.remark || "—" }}</div>
          </div>
        </div>
      </div>

      <!-- 表情 / 动作白名单（宫格卡片，含中文含义标签，可编辑） -->
      <div class="asset-panel">
        <AssetWhitelistCard
          title="表情白名单"
          kind="expression"
          :entries="expressionEntries"
          empty-text="暂无表情资产"
          @edit="handleOpenLabelEditor"
        />
        <AssetWhitelistCard
          title="动作白名单"
          kind="motion"
          :entries="motionEntries"
          empty-text="暂无动作资产"
          @edit="handleOpenLabelEditor"
        />
      </div>

      <!-- 语义提示词（内联编辑） -->
      <SemanticPromptCard
        v-model="promptText"
        :saving="savingPrompt"
        @cancel="handleCancelPrompt"
        @save="handleSavePrompt"
      />

      <!-- 待机配置 -->
      <IdleConfigCard
        v-model:idle-expression-list="idleForm.idleExpressionList"
        v-model:idle-motion-list="idleForm.idleMotionList"
        v-model:idle-interval-seconds="idleForm.idleIntervalSeconds"
        v-model:idle-auto-play="idleForm.idleAutoPlay"
        :saving="idleSaving"
        :detail="detail"
        @save="handleSaveIdle"
        @reset="handleResetIdle"
      />

      <!-- 白名单中文含义编辑器 -->
      <AssetLabelEditor
        v-model="labelEditorVisible"
        :submitting="labelSaving"
        :semantic-prompt="promptText"
        :expression-list="detail.expressionList ?? {}"
        :motion-list="detail.motionList ?? {}"
        @save="handleSaveLabels"
      />

      <!-- 编辑弹窗（复用） -->
      <Live2dEditDialog
        v-model="editVisible"
        :submitting="editSubmitting"
        :form="editForm"
        @submit="handleEditSubmit"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { Edit, Delete } from "@element-plus/icons-vue";
import { computed } from "vue";
import { FORMAT_DATETIME } from "@/shared/utils/dayjs";
import { formatTime } from "@/admin/composables/useTimeFormat";
import { resolveResourceUrl } from "@/shared/utils/resource";
import { useLive2dDetail } from "./useLive2dDetail";
import Live2dEditDialog from "./components/Live2dEditDialog.vue";
import SemanticPromptCard from "./components/SemanticPromptCard.vue";
import AssetLabelEditor from "./components/AssetLabelEditor.vue";
import AssetWhitelistCard from "./components/AssetWhitelistCard.vue";
import IdleConfigCard from "./components/IdleConfigCard.vue";

const {
  pageLoading,
  pageError,
  detail,
  editVisible,
  editSubmitting,
  editForm,
  labelEditorVisible,
  labelSaving,
  handleOpenLabelEditor,
  handleSaveLabels,
  promptText,
  savingPrompt,
  handleCancelPrompt,
  handleSavePrompt,
  idleForm,
  idleSaving,
  handleSaveIdle,
  handleResetIdle,
  enabledTagType,
  enabledLabel,
  handleReload,
  handleEdit,
  handleEditSubmit,
  handleStatusToggle,
  handleDelete,
} = useLive2dDetail();

/** 表情/动作白名单 entries（id → 中文含义），模板渲染 id + label */
const expressionEntries = computed(() => Object.entries(detail.value?.expressionList ?? {}));
const motionEntries = computed(() => Object.entries(detail.value?.motionList ?? {}));
</script>

<style scoped>
.live2d-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: #1e293b;
}

.page-state {
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 40px 24px;
  box-shadow: var(--admin-shadow-card);
}

/* ---------- 顶部概览 + 操作 ---------- */
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 16px 20px;
  box-shadow: var(--admin-shadow-card);
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.model-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.head-ops {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.btn-icon {
  margin-right: 3px;
}

/* ---------- 信息卡 ---------- */
.info-card {
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 18px 20px;
  box-shadow: var(--admin-shadow-card);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 14px;
}

.card-count {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 999px;
  padding: 1px 8px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
}
.info-item {
  min-width: 0;
}

.info-item-full {
  grid-column: 1 / -1;
}

.info-label {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 3px;
}

.info-value {
  font-size: 13px;
  color: #334155;
  word-break: break-all;
}

.code-text {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border-radius: 4px;
  padding: 2px 6px;
}

.time-text {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  color: #94a3b8;
}

/* ---------- 白名单（宫格卡片容器） ---------- */
.asset-panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 20px;
}

.value-empty {
  font-size: 12px;
  color: #cbd5e1;
}

/* ---------- 封面预览 ---------- */
.cover-preview {
  max-width: 160px;
  max-height: 120px;
  object-fit: contain;
  border-radius: 6px;
  border: 1px solid var(--admin-border);
  background: #f8fafc;
}
</style>
