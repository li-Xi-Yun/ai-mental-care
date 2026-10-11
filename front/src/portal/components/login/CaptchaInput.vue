<template>
  <div class="captcha-row">
    <el-input
      :model-value="modelValue"
      placeholder="请输入验证码"
      prefix-icon="Key"
      :disabled="disabled"
      @update:model-value="$emit('update:modelValue', $event)"
    />
    <button
      type="button"
      class="captcha-box"
      :disabled="loading || !image"
      :title="image ? '点击刷新验证码' : '验证码加载中'"
      @click="$emit('refresh')"
    >
      <img v-if="image && !loading" :src="image" alt="验证码图片，点击刷新" class="captcha-img" />
      <span v-else class="captcha-placeholder" aria-hidden="true">
        <el-icon class="is-loading"><Loading /></el-icon>
      </span>
    </button>
    <button type="button" class="captcha-refresh" :disabled="loading" @click="$emit('refresh')">换一张</button>
  </div>
</template>

<script setup lang="ts">
import { Loading } from "@element-plus/icons-vue";

defineProps<{
  modelValue: string;
  image: string;
  loading: boolean;
  disabled: boolean;
}>();

defineEmits<{
  (e: "update:modelValue", val: string): void;
  (e: "refresh"): void;
}>();
</script>

<style scoped>
.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-box {
  cursor: pointer;
  width: 100px;
  height: 36px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
  padding: 0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-box:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-placeholder {
  color: #ccc;
  font-size: 20px;
}

.captcha-refresh {
  border: none;
  background: none;
  color: #6c63ff;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  padding: 0;
  font-family: inherit;
}

.captcha-refresh:disabled {
  color: #ccc;
  cursor: not-allowed;
}
</style>