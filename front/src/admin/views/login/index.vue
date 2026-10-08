<template>
  <div class="admin-login-page">
    <!-- 背景装饰 -->
    <div class="bg-glow bg-glow--one" aria-hidden="true"></div>
    <div class="bg-glow bg-glow--two" aria-hidden="true"></div>

    <!-- 左侧品牌区（窄屏隐藏） -->
    <section class="brand-panel" aria-label="AI Mental Care 品牌介绍">
      <div class="brand-content">
        <div class="brand-logo">
          <span class="brand-logo-mark" aria-hidden="true">
            <el-icon :size="24"><MagicStick /></el-icon>
          </span>
          <span class="brand-logo-text">{{ PLATFORM_NAME }}</span>
        </div>

        <h1 class="brand-title">AI Mental Care<br />管理后台</h1>
        <p class="brand-slogan">倾听你的心声，守护你的心灵</p>

        <div class="brand-highlights">
          <div v-for="item in HIGHLIGHTS" :key="item.title" class="brand-highlight">
            <span class="brand-highlight-icon" aria-hidden="true"><el-icon :size="16"><component :is="item.icon" /></el-icon></span>
            <div class="brand-highlight-text">
              <div class="brand-highlight-title">{{ item.title }}</div>
              <div class="brand-highlight-desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 右侧登录卡片 -->
    <section class="form-panel" aria-label="登录表单">
      <div class="login-card">
        <div class="card-logo" aria-hidden="true">
          <div class="card-logo-mark"><el-icon :size="22"><MagicStick /></el-icon></div>
        </div>
        <h2 class="card-title">{{ ADMIN_PLATFORM_TITLE }}</h2>
        <p class="card-subtitle">{{ ADMIN_PLATFORM_NAME }}</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="login-form"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="管理员账号" prop="loginAccount">
            <el-input
              v-model.trim="form.loginAccount"
              placeholder="请输入管理员账号"
              prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>

          <el-form-item label="验证码" prop="code">
            <div class="code-row">
              <el-input
                v-model.trim="form.code"
                placeholder="请输入验证码"
                prefix-icon="Key"
                :disabled="!captchaImage"
              />
              <button
                type="button"
                class="captcha-box"
                :class="{ 'is-loading': captchaLoading }"
                :title="captchaImage ? '点击刷新验证码' : '加载中…'"
                :disabled="captchaLoading || !captchaImage"
                @click="refreshCaptcha"
              >
                <img
                  v-if="captchaImage && !captchaLoading"
                  :src="captchaImage"
                  alt="验证码图片，点击可刷新"
                  class="captcha-img"
                />
                <span v-else class="captcha-placeholder" aria-hidden="true">
                  <el-icon :size="18" class="is-loading"><Loading /></el-icon>
                </span>
              </button>
            </div>
          </el-form-item>

          <div class="login-options">
            <el-checkbox v-model="form.remember">记住我</el-checkbox>
          </div>

          <el-button
            type="primary"
            class="login-btn"
            native-type="submit"
            :loading="loading"
            @click.prevent="handleLogin"
          >
            登 录
          </el-button>
        </el-form>

        <p class="copyright">© {{ currentYear }} {{ PLATFORM_NAME }} · 心理健康管理平台</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { MagicStick, Loading } from "@element-plus/icons-vue";
import { ADMIN_PLATFORM_NAME, ADMIN_PLATFORM_TITLE, PLATFORM_NAME } from "@/shared/api/config";
import { login } from "@/admin/api/admin/account";
import type { AdminLoginResultVO } from "@/admin/api/admin/account";
import { setTokenInfo } from "@/shared/api/auth";
import { getCode } from "@shared/api/common/verification";
import { useAdminStore } from "@/admin/stores/admin";
import { getStorage, setStorage } from "@/shared/utils/storage";

/** 左侧品牌亮点（图标为全局注册的 Element Plus 图标名） */
const HIGHLIGHTS = [
  { icon: "MagicStick", title: "智能 AI 陪护", desc: "7x24 小时在线倾听，情绪实时分析" },
  { icon: "DataAnalysis", title: "专业量表测评", desc: "SCL-90 等专业量表，科学评估心理状态" },
  { icon: "Lock", title: "数据安全守护", desc: "账号权限分级管控，隐私数据多重加密" },
] as const;

const router = useRouter();
const adminStore = useAdminStore();

const formRef = ref<FormInstance>();

const form = reactive({
  loginAccount: "",
  password: "",
  code: "",
  remember: true,
});

const rules: FormRules = {
  loginAccount: [
    { required: true, message: "请输入管理员账号", trigger: "blur" },
    { min: 2, max: 20, message: "账号长度为 2 ~ 20 个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度为 6 ~ 20 个字符", trigger: "blur" },
  ],
  code: [{ required: true, message: "请输入验证码", trigger: "blur" }],
};

const loading = ref(false);
const captchaImage = ref("");
const captchaKey = ref("");
const captchaLoading = ref(false);
const currentYear = new Date().getFullYear();

/** 从本地缓存恢复记住的账号 */
function restoreRememberedAccount() {
  const saved = getStorage<string>("admin-login-remember");
  if (saved) {
    form.loginAccount = saved;
    form.remember = true;
  }
}

/** 刷新验证码图片 */
async function refreshCaptcha() {
  if (captchaLoading.value) return;
  captchaLoading.value = true;
  try {
    const res = await getCode();
    const data = res.data?.data;
    if (data && data.image && data.key) {
      captchaImage.value = data.image;
      captchaKey.value = data.key;
      form.code = "";
    } else {
      ElMessage.warning("验证码加载失败，请重试");
    }
  } catch {
    ElMessage.error("验证码加载失败，请检查网络后重试");
  } finally {
    captchaLoading.value = false;
  }
}

/** 登录 */
async function handleLogin() {
  if (loading.value) return;
  const valid = formRef.value ? await formRef.value.validate().catch(() => false) : false;
  if (!valid) return;

  loading.value = true;
  try {
    const res = await login({
      loginAccount: form.loginAccount,
      password: form.password,
      key: captchaKey.value,
      code: form.code,
    });
    const data: AdminLoginResultVO | undefined = res.data?.data;
    if (data?.token) {
      // token 存入 admin store（persist 插件持久化），同时写 localStorage 供路由守卫与请求拦截器读取
      adminStore.setToken(data.token);
      if (data.adminInfo) {
        adminStore.setAdminInfo(data.adminInfo as unknown as Record<string, any>);
      }
      // 记录该模块的请求头名与 token（后台头名如 admin-token / Authorization）
      setTokenInfo("admin", { token: data.token, headerName: data.headerName || "admin-token" });

      if (form.remember) {
        setStorage("admin-login-remember", form.loginAccount);
      } else {
        localStorage.removeItem("admin-login-remember");
      }

      ElMessage.success("登录成功");
      router.replace("/admin/user");
    } else {
      ElMessage.error("登录失败：未返回有效凭证，请重试");
      refreshCaptcha();
    }
  } catch (e) {
    ElMessage.error((e as Error)?.message || "登录失败，请稍后重试");
    refreshCaptcha();
  } finally {
    loading.value = false;
  }
}

refreshCaptcha();
restoreRememberedAccount();
</script>

<style scoped>
.admin-login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  overflow: hidden;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
  color: #e2e8f0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}

/* —— 背景装饰光斑 —— */
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.5;
  pointer-events: none;
}

.bg-glow--one {
  width: 480px;
  height: 480px;
  left: -120px;
  top: -140px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.55), transparent 70%);
}

.bg-glow--two {
  width: 520px;
  height: 520px;
  right: -160px;
  bottom: -180px;
  background: radial-gradient(circle, rgba(124, 58, 237, 0.5), transparent 70%);
}

/* —— 左侧品牌区 —— */
.brand-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  min-width: 0;
}

.brand-content {
  max-width: 420px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 40px;
}

.brand-logo-mark {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #7c3aed);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.35);
}

.brand-logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.4px;
}

.brand-title {
  margin: 0;
  font-size: 30px;
  line-height: 1.4;
  font-weight: 700;
  color: #fff;
}

.brand-slogan {
  margin: 12px 0 36px;
  font-size: 15px;
  color: #94a3b8;
}

.brand-highlights {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.brand-highlight {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.brand-highlight-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.14);
}

.brand-highlight-title {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

.brand-highlight-desc {
  margin-top: 3px;
  font-size: 12px;
  color: #64748b;
}

/* —— 右侧登录卡片 —— */
.form-panel {
  flex: 0 0 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 40px 36px 24px;
  background: rgba(255, 255, 255, 0.97);
  border-radius: 20px;
  box-shadow: 0 24px 64px rgba(2, 6, 23, 0.45);
}

.card-logo {
  text-align: center;
}

.card-logo-mark {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #7c3aed);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.35);
}

.card-title {
  margin: 0;
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
}

.card-subtitle {
  margin: 4px 0 26px;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}

.login-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: #475569;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #6366f1 inset;
}

.code-row {
  display: flex;
  width: 100%;
  gap: 10px;
}

.code-row .el-input {
  flex: 1;
}

.captcha-box {
  flex-shrink: 0;
  width: 112px;
  height: 40px;
  padding: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.captcha-box:not(:disabled):hover {
  border-color: #6366f1;
}

.captcha-box:disabled {
  cursor: default;
}

.captcha-box:focus-visible {
  outline: 2px solid #6366f1;
  outline-offset: 2px;
}

.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}

.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 2px 0 18px;
}

.login-options :deep(.el-checkbox__label) {
  font-size: 13px;
  color: #64748b;
}

.login-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 6px;
  background: linear-gradient(135deg, #6366f1, #7c3aed);
  border: none;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.45);
}

.login-btn:active {
  transform: translateY(0);
}

.copyright {
  margin: 24px 0 0;
  text-align: center;
  font-size: 12px;
  color: #cbd5e1;
}

/* —— 响应式：窄屏隐藏左侧品牌区 —— */
@media (max-width: 960px) {
  .brand-panel {
    display: none;
  }

  .form-panel {
    flex: 1;
    padding: 24px;
  }
}

@media (max-width: 480px) {
  .admin-login-page {
    background: #0f172a;
  }

  .form-panel {
    padding: 16px;
  }

  .login-card {
    padding: 32px 24px 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-btn {
    transition: none;
  }
}
</style>
