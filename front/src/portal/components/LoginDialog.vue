<template>
  <el-dialog
    v-model="userStore.loginDialogVisible"
    width="400px"
    align-center
    :show-close="true"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    class="login-dialog"
    @closed="onClosed"
  >
    <div class="dialog-header">
      <div class="logo-badge" aria-hidden="true">
        <el-icon :size="22"><MagicStick /></el-icon>
      </div>
      <h2 class="dialog-title">AI Mental Care</h2>
      <p class="dialog-subtitle">AI 心理健康关怀平台</p>
    </div>

    <el-tabs v-model="activeTab" class="login-tabs" stretch>
      <el-tab-pane label="登录" name="login">
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-position="top"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="账号名" prop="loginAccount">
            <el-input
              v-model.trim="loginForm.loginAccount"
              placeholder="请输入账号名"
              prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>

          <el-form-item label="验证码" prop="code">
            <div class="captcha-row">
              <el-input v-model.trim="loginForm.code" placeholder="请输入验证码" prefix-icon="Key" :disabled="!captchaImage" />
              <button
                type="button"
                class="captcha-box"
                :disabled="captchaLoading || !captchaImage"
                :title="captchaImage ? '点击刷新验证码' : '验证码加载中'"
                @click="refreshCaptcha"
              >
                <img v-if="captchaImage && !captchaLoading" :src="captchaImage" alt="验证码图片，点击刷新" class="captcha-img" />
                <span v-else class="captcha-placeholder" aria-hidden="true">
                  <el-icon class="is-loading"><Loading /></el-icon>
                </span>
              </button>
              <button type="button" class="captcha-refresh" :disabled="captchaLoading" @click="refreshCaptcha">换一张</button>
            </div>
          </el-form-item>

          <div class="login-options">
            <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
            <el-link type="primary" :underline="false" @click="showForgotPassword">忘记密码？</el-link>
          </div>

          <el-button type="primary" native-type="submit" class="submit-btn" :loading="loginLoading">登 录</el-button>

          <div class="login-other">
            <span>其他方式：</span>
            <el-link type="primary" :underline="false" @click="showOtherLogin">微信</el-link>
            <span class="other-separator">|</span>
            <el-link type="primary" :underline="false" @click="showOtherLogin">手机验证码</el-link>
          </div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="注册" name="register">
        <el-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-position="top"
          class="login-form register-form"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="账号名" prop="loginAccount">
            <el-input v-model.trim="registerForm.loginAccount" placeholder="2-32 个字符" prefix-icon="User" autocomplete="username" />
          </el-form-item>

          <el-form-item label="用户名" prop="username">
            <el-input v-model.trim="registerForm.username" placeholder="2-12 个字符" prefix-icon="Avatar" autocomplete="nickname" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="需包含大小写字母和数字"
              prefix-icon="Lock"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              prefix-icon="Lock"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <div class="form-grid">
            <el-form-item label="手机号（选填）" prop="mobile">
              <el-input v-model.trim="registerForm.mobile" placeholder="请输入手机号" prefix-icon="Iphone" />
            </el-form-item>
            <el-form-item label="邮箱（选填）" prop="email">
              <el-input v-model.trim="registerForm.email" placeholder="请输入邮箱" prefix-icon="Message" />
            </el-form-item>
          </div>

          <el-form-item label="验证码" prop="code">
            <div class="captcha-row">
              <el-input v-model.trim="registerForm.code" placeholder="请输入验证码" prefix-icon="Key" :disabled="!captchaImage" />
              <button
                type="button"
                class="captcha-box"
                :disabled="captchaLoading || !captchaImage"
                :title="captchaImage ? '点击刷新验证码' : '验证码加载中'"
                @click="refreshCaptcha"
              >
                <img v-if="captchaImage && !captchaLoading" :src="captchaImage" alt="验证码图片，点击刷新" class="captcha-img" />
                <span v-else class="captcha-placeholder" aria-hidden="true">
                  <el-icon class="is-loading"><Loading /></el-icon>
                </span>
              </button>
              <button type="button" class="captcha-refresh" :disabled="captchaLoading" @click="refreshCaptcha">换一张</button>
            </div>
          </el-form-item>

          <el-button type="primary" native-type="submit" class="submit-btn" :loading="registerLoading">注 册</el-button>
          <p class="register-tip">注册即表示同意平台服务条款</p>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { Loading, MagicStick } from "@element-plus/icons-vue";
import { login, register } from "@/portal/api/user/account";
import type { LoginResultVO } from "@/portal/api/user/account";
import { useUserStore } from "@/portal/stores/user";
import { setTokenInfo } from "@/shared/api/auth";
import { getCode } from "@shared/api/common/verification";
import { getStorage, removeStorage, setStorage } from "@/shared/utils/storage";

const REMEMBER_ACCOUNT_KEY = "portal-login-remember";

type LoginTab = "login" | "register";

const userStore = useUserStore();
const activeTab = ref<LoginTab>(userStore.loginDialogTab);
const loginFormRef = ref<FormInstance>();
const registerFormRef = ref<FormInstance>();

const loginForm = reactive({
  loginAccount: "",
  password: "",
  code: "",
  remember: true,
});

const registerForm = reactive({
  loginAccount: "",
  username: "",
  password: "",
  confirmPassword: "",
  mobile: "",
  email: "",
  code: "",
});

const loginLoading = ref(false);
const registerLoading = ref(false);
const captchaLoading = ref(false);
const captchaImage = ref("");
const captchaKey = ref("");

const loginRules: FormRules = {
  loginAccount: [
    { required: true, message: "请输入账号名", trigger: "blur" },
    { min: 2, max: 32, message: "账号名长度为 2-32 个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度为 6-20 个字符", trigger: "blur" },
  ],
  code: [{ required: true, message: "请输入验证码", trigger: "blur" }],
};

const registerRules: FormRules = {
  loginAccount: [
    { required: true, message: "请输入账号名", trigger: "blur" },
    { min: 2, max: 32, message: "账号名长度为 2-32 个字符", trigger: "blur" },
  ],
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 2, max: 12, message: "用户名长度为 2-12 个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[\s\S]{6,20}$/, message: "密码需包含大小写字母和数字，长度 6-20 位", trigger: "blur" },
  ],
  confirmPassword: [
    {
      validator: (_rule, value, callback) => {
        if (!value) callback(new Error("请再次输入密码"));
        else if (value !== registerForm.password) callback(new Error("两次输入的密码不一致"));
        else callback();
      },
      trigger: ["blur", "change"],
    },
  ],
  mobile: [{ pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }],
  email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
  code: [{ required: true, message: "请输入验证码", trigger: "blur" }],
};

watch(
  () => userStore.loginDialogTab,
  (tab) => {
    activeTab.value = tab;
  },
);

watch(activeTab, (tab) => {
  userStore.loginDialogTab = tab;
  if (userStore.loginDialogVisible) void refreshCaptcha();
});

watch(
  () => userStore.loginDialogVisible,
  (visible) => {
    if (visible) {
      restoreRememberedAccount();
      void refreshCaptcha();
    }
  },
);

function restoreRememberedAccount() {
  const rememberedAccount = getStorage<string>(REMEMBER_ACCOUNT_KEY);
  if (rememberedAccount) {
    loginForm.loginAccount = rememberedAccount;
    loginForm.remember = true;
  }
}

function normalizeCaptchaImage(image: string) {
  return image.startsWith("data:") ? image : `data:image/png;base64,${image}`;
}

async function refreshCaptcha() {
  if (captchaLoading.value) return;

  captchaLoading.value = true;
  try {
    const response = await getCode();
    const data = response.data?.data;
    if (!data?.image || !data.key) {
      ElMessage.warning("验证码加载失败，请重试");
      return;
    }

    captchaImage.value = normalizeCaptchaImage(data.image);
    captchaKey.value = data.key;
    loginForm.code = "";
    registerForm.code = "";
  } catch (error) {
    ElMessage.error((error as Error)?.message || "验证码加载失败，请检查网络后重试");
  } finally {
    captchaLoading.value = false;
  }
}

async function handleLogin() {
  if (loginLoading.value) return;

  const valid = loginFormRef.value ? await loginFormRef.value.validate().catch(() => false) : false;
  if (!valid) return;

  loginLoading.value = true;
  try {
    const response = await login({
      loginAccount: loginForm.loginAccount,
      password: loginForm.password,
      key: captchaKey.value,
      code: loginForm.code,
    });
    const data: LoginResultVO | undefined = response.data?.data;

    if (!data?.token) {
      ElMessage.error("登录失败：未返回有效凭证，请重试");
      void refreshCaptcha();
      return;
    }

    userStore.setToken(data.token);
    if (data.userInfo) userStore.setUserInfo(data.userInfo as Record<string, any>);
    // 记录该模块的请求头名与 token，供请求拦截器按接口类别携带
    setTokenInfo("user", { token: data.token, headerName: data.headerName || "user-token" });

    if (loginForm.remember) setStorage(REMEMBER_ACCOUNT_KEY, loginForm.loginAccount);
    else removeStorage(REMEMBER_ACCOUNT_KEY);

    ElMessage.success("登录成功");
    userStore.closeLoginDialog();
  } catch (error) {
    ElMessage.error((error as Error)?.message || "登录失败，请稍后重试");
    void refreshCaptcha();
  } finally {
    loginLoading.value = false;
  }
}

async function handleRegister() {
  if (registerLoading.value) return;

  const valid = registerFormRef.value ? await registerFormRef.value.validate().catch(() => false) : false;
  if (!valid) return;

  registerLoading.value = true;
  try {
    await register({
      loginAccount: registerForm.loginAccount,
      username: registerForm.username,
      password: registerForm.password,
      mobile: registerForm.mobile || undefined,
      email: registerForm.email || undefined,
      key: captchaKey.value,
      code: registerForm.code,
    });

    loginForm.loginAccount = registerForm.loginAccount;
    loginForm.password = registerForm.password;
    ElMessage.success("注册成功，请登录");
    activeTab.value = "login";
  } catch (error) {
    ElMessage.error((error as Error)?.message || "注册失败，请稍后重试");
    void refreshCaptcha();
  } finally {
    registerLoading.value = false;
  }
}

function showForgotPassword() {
  ElMessage.info("密码找回功能即将上线，暂请联系客服");
}

function showOtherLogin() {
  ElMessage.info("该登录方式即将上线，敬请期待");
}

function onClosed() {
  loginForm.loginAccount = "";
  loginForm.password = "";
  loginForm.code = "";
  loginForm.remember = true;

  registerForm.loginAccount = "";
  registerForm.username = "";
  registerForm.password = "";
  registerForm.confirmPassword = "";
  registerForm.mobile = "";
  registerForm.email = "";
  registerForm.code = "";

  captchaImage.value = "";
  captchaKey.value = "";
  loginLoading.value = false;
  registerLoading.value = false;
  loginFormRef.value?.clearValidate();
  registerFormRef.value?.clearValidate();
}
</script>

<style scoped>
.login-dialog :deep(.el-dialog) {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.login-dialog :deep(.el-dialog__header) {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 2;
  width: 0;
  height: 0;
  padding: 0;
  margin: 0;
}

.login-dialog :deep(.el-dialog__headerbtn) {
  top: 14px;
  right: 16px;
}

.login-dialog :deep(.el-dialog__close) {
  color: #b5b5c3;
  transition: color 0.2s ease;
}

.login-dialog :deep(.el-dialog__close:hover) {
  color: #6c63ff;
}

.login-dialog :deep(.el-dialog__body) {
  max-height: 78vh;
  padding: 0;
  overflow-y: auto;
}

.dialog-header {
  padding: 28px 32px 0;
  text-align: center;
}

.logo-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  color: #fff;
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  border-radius: 14px;
  box-shadow: 0 8px 20px rgba(108, 99, 255, 0.35);
}

.dialog-title {
  margin: 0;
  color: #1a1a2e;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.dialog-subtitle {
  margin: 4px 0 0;
  color: #999;
  font-size: 12px;
}

.login-tabs {
  margin: 16px 32px 0;
}

.login-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: #f0f0f0;
}

.login-tabs :deep(.el-tabs__item) {
  height: 40px;
  color: #999;
  font-size: 14px;
  font-weight: 600;
}

.login-tabs :deep(.el-tabs__item.is-active) {
  color: #6c63ff;
}

.login-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background: #6c63ff;
  border-radius: 2px;
}

.login-form {
  padding: 12px 0 24px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 13px;
}

.login-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  color: #666;
  font-size: 12px;
  line-height: 1.4;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  transition: box-shadow 0.2s ease;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1.5px #6c63ff inset;
}

.captcha-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
}

.captcha-row .el-input {
  min-width: 0;
  flex: 1;
}

.captcha-box {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 92px;
  height: 32px;
  padding: 0;
  overflow: hidden;
  background: #f8f8fa;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.captcha-box:not(:disabled):hover {
  border-color: #6c63ff;
}

.captcha-box:disabled {
  cursor: default;
}

.captcha-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}

.captcha-refresh {
  flex-shrink: 0;
  padding: 0;
  color: #6c63ff;
  font-size: 11px;
  white-space: nowrap;
  background: none;
  border: 0;
  cursor: pointer;
}

.captcha-refresh:disabled {
  color: #c8c8d0;
  cursor: default;
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 0 15px;
}

.login-options :deep(.el-checkbox__label) {
  color: #666;
  font-size: 12px;
}

.submit-btn {
  width: 100%;
  height: 40px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 6px;
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  border: 0;
  border-radius: 8px;
  box-shadow: 0 8px 20px rgba(108, 99, 255, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.submit-btn:hover {
  color: #fff;
  background: linear-gradient(135deg, #5e55ee, #35338b);
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(108, 99, 255, 0.45);
}

.submit-btn:active {
  transform: translateY(0);
}

.login-other {
  margin-top: 15px;
  color: #999;
  font-size: 12px;
  text-align: center;
}

.other-separator {
  margin: 0 7px;
  color: #d9d9df;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.register-form {
  padding-bottom: 22px;
}

.register-tip {
  margin: 12px 0 0;
  color: #b7b7c0;
  font-size: 11px;
  text-align: center;
}

@media (max-width: 480px) {
  .login-dialog :deep(.el-dialog) {
    width: calc(100vw - 32px) !important;
  }

  .dialog-header,
  .login-tabs {
    margin-right: 22px;
    margin-left: 22px;
  }

  .dialog-header {
    padding-right: 0;
    padding-left: 0;
  }

  .login-tabs {
    margin-top: 14px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .captcha-box,
  .login-form :deep(.el-input__wrapper),
  .submit-btn {
    transition: none;
  }
}
</style>
