<template>
  <div class="admin-login-page">
    <!-- 背景装饰 -->
    <div class="bg-glow bg-glow--one" aria-hidden="true"></div>
    <div class="bg-glow bg-glow--two" aria-hidden="true"></div>

    <!-- 居中玻璃登录卡片 -->
    <section class="login-card" aria-label="管理员登录">
      <div class="card-logo-mark" aria-hidden="true">
        <el-icon :size="24"><MagicStick /></el-icon>
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
    </section>
  </div>
</template>

<script setup lang="ts">
import { MagicStick, Loading } from "@element-plus/icons-vue";
import { ADMIN_PLATFORM_NAME, ADMIN_PLATFORM_TITLE, PLATFORM_NAME } from "@/shared/api/config";
import { useLogin } from "./useLogin";
// 登录页不在后台布局内，需单独引入后台主题层（body.admin-mode token）
import "@/admin/styles/index.css";

const { formRef, form, rules, loading, captchaImage, captchaLoading, currentYear, refreshCaptcha, handleLogin } =
  useLogin();
</script>

<style scoped>
.admin-login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background: linear-gradient(135deg, #f1f5f9 0%, #eef2ff 50%, #f8fafc 100%);
  color: #1e293b;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}

/* —— 背景装饰光斑 —— */
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.35;
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

/* —— 居中玻璃卡片 —— */
.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  padding: 40px 36px 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 24px;
  box-shadow: 0 24px 60px rgba(99, 102, 241, 0.16), 0 4px 16px rgba(15, 23, 42, 0.05);
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
  color: #94a3b8;
}

/* —— 响应式 —— */
@media (max-width: 480px) {
  .admin-login-page {
    padding: 16px;
  }

  .login-card {
    padding: 28px 22px 20px;
    border-radius: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-btn,
  .captcha-box {
    transition: none;
  }

  .login-btn:hover {
    transform: none;
  }
}
</style>
