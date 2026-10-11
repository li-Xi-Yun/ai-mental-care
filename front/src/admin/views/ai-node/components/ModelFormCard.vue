<template>
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
        <el-input
          :model-value="modelNameValue"
          placeholder="未配置时沿用平台全局默认模型"
          maxlength="100"
          @update:model-value="emit('update:modelNameValue', $event)"
        />
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
</template>

<script setup lang="ts">
import type { NodeFormState } from "@/admin/views/ai-node/useAiNodeDetail";

defineProps<{
  form: NodeFormState;
  modelTypeOptions: ReadonlyArray<{ label: string; value: number }>;
  /** 父组件模板中 computed 已自动解包，这里接收的是普通字符串 */
  modelNameLabel: string;
  modelNameValue: string;
}>();

const emit = defineEmits<{
  (e: "update:modelNameValue", v: string): void;
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
</style>