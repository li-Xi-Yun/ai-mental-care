<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="600px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="scale-form">
      <el-form-item label="量表名称" prop="scaleName">
        <el-input v-model="form.scaleName" placeholder="例：SAS 焦虑自评量表" maxlength="60" show-word-limit clearable />
      </el-form-item>

      <el-form-item label="量表分类" prop="scaleCategoryId">
        <el-select v-model="form.scaleCategoryId" placeholder="请选择分类（可选）" clearable style="width: 100%">
          <el-option v-for="c in categoryOptions" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
        <div class="form-tip">未选择分类时量表将归入「未分类」</div>
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio-button :value="1">启用</el-radio-button>
          <el-radio-button :value="0">禁用</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-divider content-position="left">作答配置</el-divider>

      <el-form-item label="重复作答">
        <el-radio-group v-model="form.allowRepeat">
          <el-radio-button :value="1">允许</el-radio-button>
          <el-radio-button :value="0">禁止</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.allowRepeat === 1" label="冷却时长">
        <el-input-number
          v-model="form.coolMinutes"
          :min="0"
          :max="10080"
          :step="60"
          style="width: 200px"
          @change="(v: number | undefined) => { if (v != null) form.coolMinutes = v }"
        />
        <span class="unit-text">分钟</span>
        <div class="form-tip">两次作答间隔不得少于该时长，0 表示不限制</div>
      </el-form-item>

      <el-form-item label="作答限时">
        <el-input-number
          v-model="form.timeLimit"
          :min="0"
          :max="7200"
          :step="60"
          placeholder="不限时"
          style="width: 200px"
          @change="(v: number | undefined) => { if (v != null) form.timeLimit = v }"
        />
        <span class="unit-text">秒</span>
        <div class="form-tip">0 或留空表示不限时</div>
      </el-form-item>

      <el-form-item label="匿名测评">
        <el-radio-group v-model="form.anonymous">
          <el-radio-button :value="1">匿名</el-radio-button>
          <el-radio-button :value="0">实名</el-radio-button>
        </el-radio-group>
        <div class="form-tip">开启后用户作答不与身份信息关联</div>
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
import { SCALE_FORM_DEFAULTS } from "@/admin/constants/scale";
import type { ScaleCategoryRow, ScaleForm, ScaleRow } from "@/admin/views/scale/useScaleList";

const props = defineProps<{
  modelValue: boolean;
  title?: string;
  /** 编辑目标行；为空表示新增 */
  target?: ScaleRow | null;
  submitting?: boolean;
  categoryOptions: ScaleCategoryRow[];
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", form: ScaleForm): void;
}>();

const formRef = ref<FormInstance>();

const emptyScaleForm = (): ScaleForm => ({
  id: null,
  scaleName: "",
  scaleCategoryId: null,
  status: SCALE_FORM_DEFAULTS.status,
  allowRepeat: SCALE_FORM_DEFAULTS.allowRepeat,
  coolMinutes: SCALE_FORM_DEFAULTS.coolMinutes,
  timeLimit: SCALE_FORM_DEFAULTS.timeLimit,
  anonymous: SCALE_FORM_DEFAULTS.anonymous,
});

const form = reactive<ScaleForm>(emptyScaleForm());

const rules: FormRules = {
  scaleName: [{ required: true, message: "请输入量表名称", trigger: "blur" }],
};

/** 打开弹窗时按目标行回填表单（无目标行则重置为默认值） */
function resetForm() {
  const row = props.target;
  if (row) {
    Object.assign(form, {
      id: row.id,
      scaleName: row.scaleName,
      scaleCategoryId: row.scaleCategoryId ?? null,
      status: row.status ?? 1,
      allowRepeat: row.allowRepeat ?? 1,
      coolMinutes: row.coolMinutes ?? 1440,
      timeLimit: row.timeLimit ?? 0,
      anonymous: row.anonymous ?? 0,
    });
  } else {
    Object.assign(form, emptyScaleForm());
  }
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
.scale-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.form-tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
}

.unit-text {
  margin-left: 8px;
  font-size: 13px;
  color: #64748b;
}

.scale-form :deep(.el-divider__text) {
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;
  letter-spacing: 1px;
}

.scale-form :deep(.el-radio-button__inner) {
  font-size: 12px;
}
</style>
