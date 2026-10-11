<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑管理员' : '新增管理员'"
    width="520px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleDialogClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" placeholder="2-12 个字符，不可为保留字" maxlength="12" show-word-limit />
      </el-form-item>
      <template v-if="!isEdit">
        <el-form-item label="初始密码">
          <div class="pwd-row">
            <el-switch v-model="form.sysPassword" active-text="使用默认密码" inactive-text="自定义密码" />
            <span class="form-tip">{{ form.sysPassword ? "系统默认密码：Admin@123456" : "请输入登录密码（须含大小写字母和数字）" }}</span>
          </div>
        </el-form-item>
        <el-form-item v-if="!form.sysPassword" label="自定义密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-20 位，须包含大小写字母和数字" />
        </el-form-item>
      </template>
      <el-form-item label="手机号" prop="mobile">
        <el-input v-model="form.mobile" placeholder="选填，11 位手机号" maxlength="11" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="选填，用于接收系统通知" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { AdminPageVO } from "@/admin/api/admin/admin-manage";
import type { AdminFormSubmitPayload } from "../useAdminList";

/**
 * 新增 / 编辑管理员弹窗：
 * 表单状态内部自持，打开时按 isEdit + target 初始化，校验通过后 emit('submit', payload)。
 */
const props = defineProps<{
  modelValue: boolean;
  isEdit: boolean;
  target?: AdminPageVO | null;
  submitting?: boolean;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: AdminFormSubmitPayload): void;
}>();

const formRef = ref<FormInstance>();
const form = reactive<AdminFormSubmitPayload>({
  id: null,
  username: "",
  password: "",
  mobile: "",
  email: "",
  sysPassword: true,
});

const rules = computed<FormRules>(() => {
  const base: Record<string, any[]> = {
    username: [
      { required: true, message: "请输入用户名", trigger: "blur" },
      { min: 2, max: 12, message: "用户名长度为 2-12 个字符", trigger: "blur" },
    ],
    mobile: [{ pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }],
    email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
  };
  if (!props.isEdit && !form.sysPassword) {
    base.password = [
      { required: true, message: "请输入登录密码", trigger: "blur" },
      {
        pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d]{6,20}$/,
        message: "密码须包含大小写字母和数字，长度 6-20 位",
        trigger: "blur",
      },
    ];
  }
  return base as unknown as FormRules;
});

/** 打开弹窗时初始化表单（与拆分前 openAdd / openEdit 赋值行为一致） */
function initForm() {
  if (props.isEdit && props.target) {
    form.id = props.target.id;
    form.username = props.target.username;
    form.password = "";
    form.mobile = props.target.mobile ?? "";
    form.email = props.target.email ?? "";
    form.sysPassword = false;
  } else {
    form.id = null;
    form.username = "";
    form.password = "";
    form.mobile = "";
    form.email = "";
    form.sysPassword = true;
  }
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) initForm();
  },
);

function handleDialogClosed() {
  formRef.value?.clearValidate();
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  emit("submit", {
    id: form.id,
    username: form.username,
    password: form.password,
    mobile: form.mobile,
    email: form.email,
    sysPassword: form.sysPassword,
  });
}
</script>

<style scoped>
.pwd-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}

.pwd-row .form-tip {
  margin-top: 0;
}
</style>
