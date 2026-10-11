<template>
  <el-dialog
    :model-value="modelValue"
    title="变更历史详情"
    width="720px"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <div v-loading="loading" class="history-detail">
      <template v-if="detail">
        <div class="hd-head">
          <el-tag size="small" type="info" effect="plain">版本 {{ versionRange }}</el-tag>
          <span class="hd-meta">操作人：{{ detail.createdByName || "-" }} · {{ detail.createdTime || "-" }}</span>
        </div>
        <el-alert v-if="detail.changeSummary" :title="detail.changeSummary" type="info" :closable="false" class="hd-summary" />
        <div class="hd-block">
          <div class="hd-label">修改前 System Prompt</div>
          <pre class="hd-pre old">{{ detail.oldSystemPrompt || "（空）" }}</pre>
        </div>
        <div class="hd-block">
          <div class="hd-label">修改后 System Prompt</div>
          <pre class="hd-pre new">{{ detail.newSystemPrompt || "（空）" }}</pre>
        </div>
        <div v-if="modelChanged(detail)" class="hd-block">
          <div class="hd-label">模型变更</div>
          <div class="hd-models">
            <el-tag size="small" type="info" effect="plain">{{ modelTypeLabel(detail.oldModelType) }}</el-tag>
            <span class="diff-arrow">→</span>
            <el-tag size="small" type="success" effect="plain">{{ modelTypeLabel(detail.newModelType) }}</el-tag>
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="未加载到该记录详情" :image-size="70" />
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { AiNodeConfigHistoryVO } from "@/admin/api/ai-node/history";

defineProps<{
  modelValue: boolean;
  loading: boolean;
  detail: AiNodeConfigHistoryVO | null;
  /** 父组件模板中 computed 已自动解包，这里接收的是普通字符串 */
  versionRange: string;
  modelChanged: (row: AiNodeConfigHistoryVO) => boolean;
  modelTypeLabel: (type: number | null | undefined) => string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "closed"): void;
}>();
</script>

<style scoped>
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
  color: var(--admin-text-muted);
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

.diff-arrow {
  color: var(--admin-text-muted);
  margin: 0 4px;
}
</style>