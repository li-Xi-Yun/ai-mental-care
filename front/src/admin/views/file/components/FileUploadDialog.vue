<template>
  <el-dialog
    :model-value="modelValue"
    title="上传文件"
    width="520"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="80px">
      <el-form-item label="文件分类">
        <el-tree-select
          :model-value="categoryId"
          :data="categoryTree"
          :props="categoryTreeProps"
          node-key="id"
          check-strictly
          clearable
          placeholder="请选择分类（可选）"
          style="width: 100%"
          @update:model-value="emit('update:categoryId', $event)"
        />
      </el-form-item>
      <el-form-item label="选择文件" required>
        <el-upload
          drag
          multiple
          :auto-upload="true"
          :show-file-list="true"
          :limit="5"
          :before-upload="beforeUpload"
          :http-request="uploadRequest"
          :file-list="fileList"
          @update:file-list="emit('update:fileList', $event)"
        >
          <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 PDF / Word / Excel / 图片 / 音视频等，单个文件不超过 100MB</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { UploadFilled } from "@element-plus/icons-vue";
import type { UploadUserFile } from "element-plus";
import type { CategoryNode, FileUploadRequestOptions } from "../useFileList";

defineProps<{
  modelValue: boolean;
  categoryTree: CategoryNode[];
  categoryTreeProps: { children: string; label: string };
  categoryId?: number;
  fileList: UploadUserFile[];
  beforeUpload: (file: File) => boolean;
  uploadRequest: (options: FileUploadRequestOptions) => Promise<void>;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "update:categoryId", v: number | undefined): void;
  (e: "update:fileList", v: UploadUserFile[]): void;
}>();
</script>
