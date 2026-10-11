<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="480px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="clearValidate"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="封禁截止时间">
        <el-date-picker
          v-model="form.banEndTime"
          type="datetime"
          placeholder="留空表示永久封禁"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
          :disabled-date="disablePastDate"
        />
        <div class="form-tip">不选择时间则为永久封禁；到期后将自动恢复正常状态。</div>
      </el-form-item>
      <el-form-item label="封禁理由" prop="banReason">
        <el-input
          v-model="form.banReason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请输入封禁理由"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="danger" :loading="submitting" @click="handleSubmit">确认封禁</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";

defineProps<{
  modelValue: boolean;
  title?: string;
  submitting?: boolean;
  /** 禁止选择过去时间 */
  disablePastDate?: (d: Date) => boolean;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: { reason: string; banEndTime?: string }): void;
}>();

const formRef = ref<FormInstance>();
const form = reactive({ banReason: "", banEndTime: "" });

const rules: FormRules = {
  banReason: [{ required: true, message: "请输入封禁理由", trigger: "blur" }],
};

function clearValidate() {
  form.banReason = "";
  form.banEndTime = "";
  formRef.value?.clearValidate();
}

function handleSubmit() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return;
    emit("submit", { reason: form.banReason.trim(), banEndTime: form.banEndTime || undefined });
  });
}
</script>

<style scoped>
.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}
</style>