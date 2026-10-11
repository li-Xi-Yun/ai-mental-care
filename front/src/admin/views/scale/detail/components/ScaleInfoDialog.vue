<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑量表信息"
    width="600px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="量表名称" prop="scaleName">
        <el-input v-model="form.scaleName" placeholder="例：SAS 焦虑自评量表" maxlength="60" show-word-limit clearable />
      </el-form-item>
      <el-form-item label="量表分类">
        <el-select v-model="form.scaleCategoryId" placeholder="请选择分类（可选）" clearable style="width: 100%">
          <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
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
        <el-input-number v-model="form.coolMinutes" :min="0" :max="10080" :step="60" style="width: 200px" />
        <span class="unit-text">分钟</span>
      </el-form-item>
      <el-form-item label="作答限时">
        <el-input-number v-model="form.timeLimit" :min="0" :max="7200" :step="60" style="width: 200px" />
        <span class="unit-text">秒</span>
        <div class="form-tip">0 或留空表示不限时</div>
      </el-form-item>
      <el-form-item label="匿名测评">
        <el-radio-group v-model="form.anonymous">
          <el-radio-button :value="1">匿名</el-radio-button>
          <el-radio-button :value="0">实名</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSave">保 存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { AdminScaleCategoryVO, AdminScaleDetailVO } from "@/admin/api/scale/types";
import type { ScaleInfoSubmitPayload } from "../useScaleDetail";

const props = defineProps<{
  modelValue: boolean;
  detail: AdminScaleDetailVO | null;
  categories: AdminScaleCategoryVO[];
  submitting?: boolean;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: ScaleInfoSubmitPayload): void;
}>();

const formRef = ref<FormInstance>();
const form = reactive({
  scaleName: "",
  scaleCategoryId: null as number | null,
  status: 1,
  allowRepeat: 1,
  coolMinutes: 1440,
  timeLimit: 0,
  anonymous: 0,
});
const rules: FormRules = {
  scaleName: [{ required: true, message: "请输入量表名称", trigger: "blur" }],
};

/** 打开弹窗时以最新量表详情回填表单 */
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    const d = props.detail;
    if (d) {
      Object.assign(form, {
        scaleName: d.scaleName ?? "",
        scaleCategoryId: d.scaleCategoryId ?? null,
        status: d.status ?? 1,
        allowRepeat: d.allowRepeat ?? 1,
        coolMinutes: d.coolMinutes ?? 1440,
        timeLimit: d.timeLimit ?? 0,
        anonymous: d.anonymous ?? 0,
      });
    }
    formRef.value?.clearValidate();
  },
);

function handleSave() {
  formRef.value?.validate((valid) => {
    if (!valid) return;
    emit("submit", {
      scaleName: form.scaleName,
      scaleCategoryId: form.scaleCategoryId,
      status: form.status,
      allowRepeat: form.allowRepeat,
      coolMinutes: form.coolMinutes,
      timeLimit: form.timeLimit,
      anonymous: form.anonymous,
    });
  });
}
</script>

<style scoped>
.form-tip {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
  margin-top: 4px;
}
.unit-text {
  margin-left: 8px;
  font-size: 12px;
  color: #64748b;
}
</style>
