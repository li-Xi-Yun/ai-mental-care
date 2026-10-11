<template>
  <el-dialog
    v-model="visibleProxy"
    title=""
    width="400px"
    center
    :align-center="true"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    class="login-dialog"
  >
    <el-tabs v-model="tab" class="login-tabs">
      <el-tab-pane label="登录" name="login">
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-width="0"
          size="default"
          :hide-required-asterisk="true"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="email">
            <el-input v-model="loginForm.email" placeholder="请输入邮箱" :prefix-icon="Message" />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item prop="code">
            <CaptchaInput
              v-model="loginForm.code"
              :image="captchaImage"
              :loading="captchaLoading"
              :disabled="submitting"
              @refresh="refreshCaptcha(loginForm)"
            />
          </el-form-item>

          <el-form-item style="margin-bottom: 16px">
            <el-button type="primary" class="submit-btn" native-type="submit" :loading="submitting" :disabled="submitting"
              >登 录</el-button
            >
          </el-form-item>

          <div v-if="errorMessage" class="error-text">{{ errorMessage }}</div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="注册" name="register">
        <el-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-width="0"
          size="default"
          :hide-required-asterisk="true"
          @submit.prevent="handleRegister"
        >
          <el-form-item prop="email">
            <el-input v-model="registerForm.email" placeholder="请输入邮箱" :prefix-icon="Message" />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请设置密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请确认密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item prop="code">
            <CaptchaInput
              v-model="registerForm.code"
              :image="captchaImage"
              :loading="captchaLoading"
              :disabled="submitting"
              @refresh="refreshCaptcha(loginForm, registerForm)"
            />
          </el-form-item>

          <el-form-item style="margin-bottom: 16px">
            <el-button type="primary" class="submit-btn" native-type="submit" :loading="submitting" :disabled="submitting"
              >注 册</el-button
            >
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { Message, Lock } from "@element-plus/icons-vue";
import { login, register } from "@/portal/api/user/account";
import { useUserStore } from "@/portal/stores/user";
import { useCaptcha } from "@/portal/composables/useCaptcha";
import CaptchaInput from "@/portal/components/login/CaptchaInput.vue";

/* ==================== 用户 store ==================== */

const userStore = useUserStore();

/* ==================== 对话框 ==================== */

const tab = ref<"login" | "register">("login");

const visibleProxy = computed({
  get: () => userStore.loginDialogVisible,
  set: (v) => {
    if (!v) userStore.closeLoginDialog();
  },
});

/* ==================== 表单 ref ==================== */

const loginFormRef = ref<FormInstance | null>(null);
const registerFormRef = ref<FormInstance | null>(null);

/* ==================== 登录表单 ==================== */

const loginForm = ref({
  email: "",
  password: "",
  code: "",
});

const loginRules: FormRules = {
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" },
  ],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
  code: [{ required: true, message: "请输入验证码", trigger: "blur" }],
};

/* ==================== 注册表单 ==================== */

const registerForm = ref({
  email: "",
  password: "",
  confirmPassword: "",
  code: "",
});

const registerRules: FormRules = {
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请设置密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度应在 6~20 之间", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请再次输入密码", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value !== registerForm.value.password) {
          callback(new Error("两次密码输入不一致"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
  code: [{ required: true, message: "请输入验证码", trigger: "blur" }],
};

/* ==================== 验证码 ==================== */

const { captchaLoading, captchaImage, captchaKey, refreshCaptcha } = useCaptcha();

/* ==================== 状态 ==================== */

const submitting = ref(false);
const errorMessage = ref("");

/* ==================== 登录 ==================== */

function resetAllForms() {
  loginFormRef.value?.resetFields();
  registerFormRef.value?.resetFields();
}

function handleDialogClose() {
  resetAllForms();
  errorMessage.value = "";
  tab.value = "login";
}

watch(visibleProxy, (v) => {
  if (v) {
    refreshCaptcha(loginForm.value, registerForm.value);
  } else {
    handleDialogClose();
  }
});

async function handleLogin() {
  if (submitting.value) return;
  if (!loginFormRef.value) return;

  errorMessage.value = "";

  try {
    await loginFormRef.value.validate();
  } catch {
    return;
  }

  if (!captchaKey.value) {
    errorMessage.value = "验证码已失效，请刷新验证码后重试";
    return;
  }

  submitting.value = true;
  errorMessage.value = "";

  try {
    const response = await login({
      loginAccount: loginForm.value.email,
      password: loginForm.value.password,
      key: captchaKey.value,
      code: loginForm.value.code,
    });

    const result = response.data;
    if (result?.data) {
      userStore.setToken(result.data.token);
      userStore.setUserInfo(result.data.userInfo ?? {});
      ElMessage.success("登录成功");
      userStore.closeLoginDialog();
      resetAllForms();
    } else {
      errorMessage.value = result?.msg || "登录失败，请检查用户名和密码";
      refreshCaptcha(loginForm.value, registerForm.value);
    }
  } catch (error) {
    errorMessage.value = (error as Error).message || "登录失败，请检查网络后重试";
    refreshCaptcha(loginForm.value, registerForm.value);
  } finally {
    submitting.value = false;
  }
}

/* ==================== 注册 ==================== */

async function handleRegister() {
  if (submitting.value) return;
  if (!registerFormRef.value) return;

  try {
    await registerFormRef.value.validate();
  } catch {
    return;
  }

  if (!captchaKey.value) {
    ElMessage.warning("验证码已失效，请刷新验证码后重试。");
    return;
  }

  submitting.value = true;

  try {
    const response = await register({
      loginAccount: registerForm.value.email,
      username: registerForm.value.email,
      password: registerForm.value.password,
      email: registerForm.value.email,
      key: captchaKey.value,
      code: registerForm.value.code,
    });

    const result = response.data;
    if (result?.code === 200) {
      ElMessage.success("注册成功，请登录");

      loginForm.value.email = registerForm.value.email;
      loginForm.value.password = registerForm.value.password;

      registerFormRef.value?.resetFields();
      nextTick(() => {
        refreshCaptcha(loginForm.value, registerForm.value);
        tab.value = "login";
      });
    } else {
      ElMessage.warning(result?.msg || "注册失败，请稍后再试");
    }
  } catch (error) {
    ElMessage.warning((error as Error)?.message || "注册失败，请检查网络后重试");
  } finally {
    submitting.value = false;
  }
}

/* ==================== watch 初始模式 ==================== */

// 监听对话框初始模式
watch(
  () => userStore.loginDialogTab,
  (mode) => {
    if (mode) {
      tab.value = mode;
    }
  },
  { immediate: true },
);
</script>

<style>
.login-dialog .el-dialog__body {
  padding: 0 24px 16px;
}

.login-dialog .el-dialog__headerbtn {
  font-size: 18px;
  top: 12px;
  right: 16px;
}
</style>

<style scoped>
.login-tabs {
  margin-top: -16px;
}

.login-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.login-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 0;
}

.submit-btn {
  width: 100%;
  border-radius: 10px;
  font-weight: 600;
  letter-spacing: 1px;
  height: 42px;
  background: #6c63ff;
  border-color: #6c63ff;
}

.submit-btn.is-disabled {
  opacity: 0.6;
}

.submit-btn:not(.is-disabled):hover {
  background: #5549ef;
  border-color: #5549ef;
}

.error-text {
  color: #f56c6c;
  font-size: 13px;
  text-align: center;
  margin: -8px 0 4px;
}
</style>