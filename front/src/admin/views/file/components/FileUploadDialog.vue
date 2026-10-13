<!-- 上传文件弹窗：文件在浏览器暂存（auto-upload=false），点「确定」后由父层串行上传 -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="上传文件"
    width="520"
    align-center
    destroy-on-close
    class="upload-dialog"
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
          :auto-upload="false"
          :show-file-list="true"
          :limit="5"
          :file-list="fileList"
          :on-exceed="handleExceed"
          @update:file-list="emit('update:fileList', $event)"
          @change="handleChange"
        >
          <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">
              支持 PDF / Word / Excel / PPT / 图片 / 文本等，单个文件不超过 10MB，最多 5 个；
              选择后暂存于浏览器，点击「确定」后统一上传
            </div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <span v-if="submitting && progressText" class="upload-progress">{{ progressText }}</span>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!fileList.length"
        @click="emit('submit')"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import type { UploadFile, UploadUserFile } from "element-plus";
import type { CategoryNode } from "../useFileList";

defineProps<{
  modelValue: boolean;
  categoryTree: CategoryNode[];
  categoryTreeProps: { children: string; label: string };
  categoryId?: number;
  fileList: UploadUserFile[];
  /** 上传中（确定按钮 loading、禁用取消） */
  submitting?: boolean;
  /** 上传进度文案（如「正在上传 2/5…」） */
  progressText?: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "update:categoryId", v: number | undefined): void;
  (e: "update:fileList", v: UploadUserFile[]): void;
  (e: "change", file: UploadFile, list: UploadUserFile[]): void;
  (e: "submit"): void;
}>();

function handleChange(file: UploadFile, list: UploadUserFile[]) {
  emit("change", file, list);
}

function handleExceed() {
  ElMessage.warning("最多同时上传 5 个文件，请先移除部分文件");
}
</script>

<!--
  非 scoped 样式：el-dialog 通过 teleport 挂到 body，scoped :deep() 无法命中其内部节点，
  因此用 .upload-dialog 类前缀限定作用域
-->
<style>
/* 弹窗内容超高时内部滚动，保证 footer 常驻可见（修复内容溢出视口问题） */
.upload-dialog .el-dialog__body {
  max-height: 60vh;
  overflow-y: auto;
}

/* 暂存文件列表超高时自身滚动 */
.upload-dialog .el-upload-list {
  max-height: 220px;
  overflow-y: auto;
}
</style>

<style scoped>
.upload-progress {
  margin-right: 12px;
  font-size: 12px;
  color: var(--admin-text-secondary, #6b7280);
}
</style>
