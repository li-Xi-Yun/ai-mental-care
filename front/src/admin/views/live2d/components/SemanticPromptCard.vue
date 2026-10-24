<template>
  <div class="card semantic-prompt-card">
    <div class="card-head">
      <el-icon><Reading /></el-icon>
      <h3>语义提示词</h3>
      <span class="card-tip">直接在此编辑，保存后立即生效，发给大模型选择表情/动作的固定词汇说明</span>
    </div>
    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="10"
      maxlength="2000"
      show-word-limit
      class="prompt-textarea"
      placeholder="如：F01=平静，F02=关切，F03=鼓励；约束大模型从固定词汇中选择表情/动作"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <div class="prompt-footer">
      <span class="prompt-meta">{{ modelValue.length }} / 2000 字符（含空白）</span>
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
import { Reading, Select } from "@element-plus/icons-vue";

defineProps<{
  modelValue: string;
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
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 20px 22px;
  box-shadow: var(--admin-shadow-card);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.card-head .el-icon {
  color: #6c63ff;
  font-size: 16px;
}

.card-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.card-tip {
  margin-left: auto;
  font-size: 11.5px;
  color: #94a3b8;
}

.prompt-textarea :deep(.el-textarea__inner) {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  white-space: pre-wrap;
}

.prompt-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.prompt-meta {
  font-size: 12px;
  color: #94a3b8;
}

.prompt-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-icon {
  margin-right: 3px;
}
</style>
