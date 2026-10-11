<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="420px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="分类名称" prop="categoryName">
        <el-input v-model="form.categoryName" placeholder="请输入分类名称" maxlength="30" show-word-limit clearable />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 180px" />
        <div class="form-tip">数值越小越靠前，也可在列表中直接调整</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { CategorySubmitPayload, ScaleCategoryRow } from "@/admin/views/scale/useScaleList";

const props = defineProps<{
  modelValue: boolean;
  title?: string;
  /** 编辑目标（id 为 0 表示新增）；打开时回填表单 */
  target?: ScaleCategoryRow | null;
  submitting?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: CategorySubmitPayload): void;
}>();

const formRef = ref<FormInstance>();

const form = reactive<CategorySubmitPayload>({ id: 0, categoryName: "", sort: 0 });

const rules: FormRules = {
  categoryName: [{ required: true, message: "请输入分类名称", trigger: "blur" }],
};

function resetForm() {
  const row = props.target;
  Object.assign(form, {
    id: row?.id ?? 0,
    categoryName: row?.categoryName ?? "",
    sort: row?.sort ?? 0,
  });
}

function handleClosed() {
  formRef.value?.clearValidate();
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  emit("submit", { ...form });
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) resetForm();
  },
);
</script>

<style scoped>
.form-tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
}
</style>
