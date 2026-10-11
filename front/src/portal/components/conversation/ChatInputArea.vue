<template>
  <div class="ca-input-wrapper">
    <div class="ca-input" ref="inputBoxRef">
      <div class="input-upper">
        <el-input
          :model-value="modelValue"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          resize="none"
          placeholder="输入你的想法…"
          @update:model-value="emit('update:modelValue', $event)"
          @keydown.enter.exact.prevent="emit('send')"
        />
      </div>
      <div class="input-lower">
        <button class="func-btn" title="语音对话（开发中）" @click="emit('voice')">语音对话</button>
        <button class="func-btn" title="视频对话（开发中）" @click="emit('video')">视频对话</button>
        <span class="spacer"></span>
        <button class="send-btn" :disabled="!modelValue.trim() || disabled" title="发送" @click="emit('send')">
          <el-icon :size="18"><Promotion /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { Promotion } from "@element-plus/icons-vue";

defineProps<{
  modelValue: string;
  disabled: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", val: string): void;
  (e: "send"): void;
  (e: "voice"): void;
  (e: "video"): void;
}>();

const inputBoxRef = ref<HTMLElement | null>(null);

defineExpose({
  focus: () => {
    inputBoxRef.value?.querySelector("textarea")?.focus();
  },
});
</script>

<style scoped>
.ca-input-wrapper {
  padding: 10px 20px 14px;
  flex-shrink: 0;
}

.ca-input {
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #e5e7f0;
  padding: 10px 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.ca-input:focus-within {
  border-color: #6c63ff;
  box-shadow: 0 4px 16px rgba(108, 99, 255, 0.12);
}

.input-upper {
  margin-bottom: 8px;
}

.input-upper :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  background: transparent;
  padding: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #1e1e2e;
  resize: none;
}

.input-upper :deep(.el-textarea__inner::placeholder) {
  color: #bbb;
}

.input-upper :deep(.el-textarea__inner:focus) {
  border: none;
  box-shadow: none;
}

.input-lower {
  display: flex;
  align-items: center;
  gap: 10px;
}

.func-btn {
  border: none;
  background: #f0f1f7;
  color: #666;
  padding: 5px 12px;
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.func-btn:hover {
  background: #e4e6f0;
  color: #1e1e2e;
}

.spacer {
  flex: 1;
}

.send-btn {
  cursor: pointer;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  font-family: inherit;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(108, 99, 255, 0.3);
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(108, 99, 255, 0.4);
}

.send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}
</style>