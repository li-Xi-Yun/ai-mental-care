<template>
  <div class="card prompt-card">
    <div class="card-head">
      <el-icon><Reading /></el-icon>
      <h3>System Prompt 系统提示词</h3>
      <span class="card-tip">独立保存，后端自动记录变更历史</span>
    </div>
    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="10"
      maxlength="20000"
      show-word-limit
      class="prompt-textarea"
      placeholder="请输入系统提示词，约定AI角色与任务……"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <div class="prompt-footer">
      <span class="prompt-meta">配置版本 v{{ version || "-" }} · 已保存版本 {{ modelValue.length }} 字符（含空白）</span>
      <div class="prompt-actions">
        <el-button @click="emit('cancel')">取消修改</el-button>
        <el-button type="primary" :loading="saving" @click="emit('save')">
          <el-icon class="btn-icon"><Select /></el-icon>保存提示词
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: string;
  version?: number;
  saving?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: string): void;
  (e: "cancel"): void;
  (e: "save"): void;
}>();
</script>

<style scoped>
.card {
  background: #ffffff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 20px 22px;
  box-shadow: var(--admin-shadow-card);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6366f1;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--admin-border);
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
  color: var(--admin-text-muted);
}

.prompt-textarea :deep(.el-textarea__inner) {
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
  color: var(--admin-text-muted);
}

.prompt-actions {
  display: flex;
  gap: 8px;
}

.btn-icon {
  margin-right: 4px;
}
</style>