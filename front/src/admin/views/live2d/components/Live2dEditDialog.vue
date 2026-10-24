<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑 Live2D 模型配置"
    width="520px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" label-position="top" class="edit-form">
      <el-form-item label="模型名称">
        <el-input v-model="form.modelName" disabled />
        <div class="form-tip">模型名称即解压目录名，上传后不可修改</div>
      </el-form-item>

      <el-form-item label="模型别名">
        <el-input
          v-model="form.aliasName"
          placeholder="选填，中文展示名称（如：小阳 / 知暖），清空保存则恢复英文名"
          maxlength="50"
          clearable
        />
        <div class="form-tip">后台展示用中文名，留空则展示英文模型名称</div>
      </el-form-item>

      <el-form-item label="封面图">
        <el-upload
          class="cover-uploader"
          :show-file-list="false"
          accept="image/*"
          :http-request="handleCoverUpload"
          :before-upload="beforeCoverUpload"
        >
          <div class="cover-upload-box">
            <img v-if="coverPreviewUrl" :src="coverPreviewUrl" class="cover-img" alt="" />
            <div v-else class="cover-placeholder">
              <el-icon v-if="!coverUploading" class="cover-plus"><Plus /></el-icon>
              <span>{{ coverUploading ? "上传中…" : "上传图片" }}</span>
            </div>
          </div>
        </el-upload>
        <div v-if="form.coverImagePath" class="cover-actions">
          <el-button text size="small" type="danger" @click="form.coverImagePath = ''">
            移除封面
          </el-button>
        </div>
        <div class="form-tip">封面图存至公开图片目录，用于列表缩略图与用户端预览展示</div>
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="排序值">
            <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="备注">
            <el-input v-model="form.remark" maxlength="255" placeholder="选填" clearable />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保 存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { FormInstance, UploadRequestOptions } from "element-plus";
import { ElMessage } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { uploadImage } from "@/shared/api/common/upload";
import { resolveResourceUrl } from "@/shared/utils/resource";
import type { EditForm } from "@/admin/views/live2d/useLive2dList";

const props = defineProps<{
  modelValue: boolean;
  submitting?: boolean;
  /** 表单数据由 useLive2dDetail 持有，子组件只负责渲染、校验与封面图上传 */
  form: EditForm;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit"): void;
}>();

const formRef = ref<FormInstance>();
const coverUploading = ref(false);

const coverPreviewUrl = computed(() => resolveResourceUrl(props.form.coverImagePath));

function beforeCoverUpload(file: File): boolean {
  if (!file.type.startsWith("image/")) {
    ElMessage.warning("仅支持上传图片文件");
    return false;
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning("封面图大小不能超过 5MB");
    return false;
  }
  return true;
}

async function handleCoverUpload(options: UploadRequestOptions) {
  const file = options.file as File;
  coverUploading.value = true;
  try {
    const res = await uploadImage(file);
    const url = res.data?.data;
    if (!url) {
      ElMessage.error("封面上传失败：未返回文件路径");
      return;
    }
    props.form.coverImagePath = url;
    ElMessage.success("封面上传成功");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "封面上传失败，请稍后重试");
  } finally {
    coverUploading.value = false;
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  emit("submit");
}

function handleClosed() {
  formRef.value?.clearValidate();
}
</script>

<style scoped>
.edit-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 2px;
}

.form-tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
}

/* ---------- 封面上传 ---------- */
.cover-uploader :deep(.el-upload) {
  border: 1px dashed #d9d9e3;
  border-radius: 6px;
  background: #fafafe;
  transition: border-color 0.15s;
}

.cover-uploader :deep(.el-upload:hover) {
  border-color: #6c63ff;
}

.cover-upload-box {
  width: 140px;
  height: 92px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #94a3b8;
  font-size: 12px;
}

.cover-plus {
  font-size: 18px;
}

.cover-actions {
  margin-top: 6px;
}
</style>
