<!-- 修改密码表单卡：当前密码 / 新密码 / 确认密码，校验通过后触发提交 / 重置 -->
<template>
  <section class="form-card">
    <div class="form-card__header">
      <span class="form-card__icon" style="color: var(--el-color-warning); background: var(--el-color-warning-light-9)">
        <el-icon :size="16"><Lock /></el-icon>
      </span>
      <span class="form-card__title">修改密码</span>
      <span class="form-card__tip">新密码需包含大写字母、小写字母和数字，长度 6-20 位</span>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="84px" class="profile-form">
      <el-form-item label="当前密码" prop="originalPassword">
        <el-input
          v-model="form.originalPassword"
          type="password"
          show-password
          placeholder="请输入当前密码"
          autocomplete="current-password"
        />
      </el-form-item>
      <el-form-item label="新密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="请输入新密码"
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="请再次输入新密码"
          autocomplete="new-password"
          @keyup.enter="handleSubmit"
        />
      </el-form-item>
      <el-form-item class="form-card__actions">
        <el-button type="primary" :loading="saving" @click="handleSubmit">
          <el-icon style="margin-right: 4px"><Key /></el-icon>
          修改密码
        </el-button>
        <el-button :disabled="saving" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { Key, Lock } from "@element-plus/icons-vue";
import type { FormInstance, FormRules } from "element-plus";
import type { PwdFormState } from "../useProfile";
import "./form-card.css";

defineProps<{
  form: PwdFormState;
  rules: FormRules;
  saving?: boolean;
}>();

const emit = defineEmits<{
  (e: "save"): void;
  (e: "reset"): void;
}>();

const formRef = ref<FormInstance>();

/** 校验通过后交由父级（useProfile）提交修改 */
async function handleSubmit() {
  const valid = formRef.value ? await formRef.value.validate().catch(() => false) : false;
  if (!valid) return;
  emit("save");
}

/** 清空密码表单并清除校验状态 */
function handleReset() {
  emit("reset");
  formRef.value?.clearValidate();
}
</script>
