<template>
  <el-dialog
    :model-value="modelValue"
    title="上传 Live2D 模型包"
    width="600px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="upload-form">
      <el-form-item label="模型名称" prop="modelName">
        <el-input
          v-model="form.modelName"
          placeholder="如 hiyori"
          maxlength="50"
          clearable
        />
        <div class="form-tip">仅支持字母 / 数字 / 下划线 / 中划线，作为解压目录名与启用互斥标识</div>
      </el-form-item>

      <el-form-item label="模型别名">
        <el-input
          v-model="form.aliasName"
          placeholder="选填，中文展示名称（如：小阳 / 知暖）"
          maxlength="50"
          clearable
        />
        <div class="form-tip">后台展示用中文名，留空则展示英文模型名称</div>
      </el-form-item>

      <el-form-item label="模型包文件（zip）" prop="file">
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          :file-list="fileList"
          :on-exceed="handleExceed"
          accept=".zip"
          @update:file-list="emit('change', undefined, $event)"
        >
          <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将模型包拖到此处，或 <em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              仅支持 .zip，内含 .model3.json（入口）+ .moc3 / 纹理 / .motion3.json / .exp3.json；
              上传后自动解析表情与动作白名单，需到列表手动启用
            </div>
          </template>
        </el-upload>
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="表情清单文件名" prop="expressionFileName">
            <el-input
              v-model="form.expressionFileName"
              placeholder="缺省按 *.exp3.json 匹配"
              clearable
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="动作清单文件名" prop="motionFileName">
            <el-input
              v-model="form.motionFileName"
              placeholder="缺省按 *.motion3.json 匹配"
              clearable
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="语义提示词" prop="semanticPrompt">
        <el-input
          v-model="form.semanticPrompt"
          type="textarea"
          :rows="3"
          maxlength="2000"
          show-word-limit
          placeholder="选填，如：F01=平静，F02=关切，F03=鼓励；上传后可编辑补录"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="排序值" prop="sort">
            <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" maxlength="255" placeholder="选填" clearable />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">上传</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules, UploadUserFile } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import type { UploadForm } from "@/admin/views/live2d/useLive2dList";

defineProps<{
  modelValue: boolean;
  submitting?: boolean;
  /** 表单数据由 useLive2dList 持有，子组件只负责渲染与校验 */
  form: UploadForm;
  fileList: UploadUserFile[];
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "change", file?: unknown, list?: UploadUserFile[]): void;
  (e: "submit"): void;
}>();

const formRef = ref<FormInstance>();

const rules: FormRules = {
  modelName: [
    { required: true, message: "请输入模型名称", trigger: "blur" },
    {
      pattern: /^[a-zA-Z0-9_-]+$/,
      message: "仅支持字母 / 数字 / 下划线 / 中划线",
      trigger: "blur",
    },
  ],
};

function handleExceed() {
  ElMessage.warning("仅支持上传 1 个模型包，请先移除当前文件");
}

async function handleSubmit() {
  // 弹窗打开/销毁切换时 form ref 可能尚未建立；模型名称仍由 composable 做二次校验，不能因 ref 缺失静默阻断上传。
  const valid = formRef.value ? await formRef.value.validate().catch(() => false) : true;
  if (!valid) return;
  emit("submit");
}

function handleClosed() {
  formRef.value?.clearValidate();
}
</script>

<style scoped>
.upload-form :deep(.el-form-item__label) {
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
</style>
