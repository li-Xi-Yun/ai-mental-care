<template>
  <el-dialog
    :model-value="modelValue"
    :title="row?.originalName"
    width="860"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <div class="preview-body" v-loading="loading">
      <template v-if="isImage">
        <img :src="imageUrl || ''" :alt="row?.originalName" class="preview-image" />
      </template>
      <template v-else-if="isText && !loading">
        <pre v-if="text" class="preview-text">{{ text }}</pre>
        <div v-else class="preview-placeholder">
          <el-icon :size="44"><Document /></el-icon>
          <p>文本内容获取失败，请下载后查看</p>
          <el-button type="primary" @click="emit('download', row)">立即下载</el-button>
        </div>
      </template>
      <template v-else-if="!loading">
        <div class="preview-placeholder">
          <el-icon :size="44"><Document /></el-icon>
          <p>该文件类型暂不支持在线预览，请下载后查看</p>
          <el-button type="primary" @click="emit('download', row)">立即下载</el-button>
        </div>
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { Document } from "@element-plus/icons-vue";
import type { FileRow } from "../useFileList";

defineProps<{
  modelValue: boolean;
  row: FileRow | null;
  loading: boolean;
  imageUrl: string;
  text: string;
  isImage: boolean;
  isText: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "download", row: FileRow | null): void;
  (e: "closed"): void;
}>();
</script>

<style scoped>
.preview-body {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
}

.preview-image {
  max-width: 100%;
  max-height: 580px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.preview-text {
  width: 100%;
  height: 500px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 8px;
  font-size: 12.5px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #94a3b8;
  font-size: 13px;
  padding: 48px;
  text-align: center;
}
</style>
