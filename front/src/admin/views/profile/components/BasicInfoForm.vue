<!-- 基本信息表单卡：账号只读，用户名/手机号/邮箱编辑，校验通过后触发保存 / 重置 -->
<template>
  <section class="form-card">
    <div class="form-card__header">
      <span class="form-card__icon" style="color: var(--admin-primary); background: var(--admin-primary-soft)">
        <el-icon :size="16"><User /></el-icon>
      </span>
      <span class="form-card__title">基本信息</span>
      <span class="form-card__tip">账号名不可修改，如需变更请联系超级管理员</span>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="84px" class="profile-form">
      <el-form-item label="账号" prop="loginAccount">
        <el-input v-model.trim="form.loginAccount" disabled placeholder="登录账号" />
      </el-form-item>
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model.trim="form.username"
          placeholder="请输入用户名"
          maxlength="12"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="手机号" prop="mobile">
        <el-input v-model.trim="form.mobile" placeholder="请输入手机号" maxlength="11" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model.trim="form.email" placeholder="请输入邮箱" maxlength="50" />
      </el-form-item>
      <el-form-item class="form-card__actions">
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon style="margin-right: 4px"><Check /></el-icon>
          保存修改
        </el-button>
        <el-button :disabled="saving" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { Check, User } from "@element-plus/icons-vue";
import type { FormInstance, FormRules } from "element-plus";
import type { ProfileFormState } from "../useProfile";
import "./form-card.css";

defineProps<{
  form: ProfileFormState;
  rules: FormRules;
  saving?: boolean;
}>();

const emit = defineEmits<{
  (e: "save"): void;
  (e: "reset"): void;
}>();

const formRef = ref<FormInstance>();

/** 校验通过后交由父级（useProfile）提交保存 */
async function handleSave() {
  const valid = formRef.value ? await formRef.value.validate().catch(() => false) : false;
  if (!valid) return;
  emit("save");
}

/** 重置为最近一次加载的资料值，并清除校验状态 */
function handleReset() {
  emit("reset");
  formRef.value?.clearValidate();
}
</script>
