<template>
  <div class="profile-page">
    <!-- 左侧用户卡片 -->
    <aside class="profile-side">
      <div class="user-card">
        <div class="user-card__avatar">
          <el-upload
            class="avatar-uploader"
            action="#"
            :show-file-list="false"
            accept="image/*"
            :disabled="!userStore.token"
            :http-request="handleAvatarUpload"
            :before-upload="beforeAvatarUpload"
          >
            <div class="avatar-wrap">
              <el-avatar v-if="avatarUrl" :src="avatarUrl" :size="92" shape="circle" class="avatar-img" />
              <div v-else class="avatar-initials">{{ avatarText }}</div>
              <div class="avatar-mask">
                <el-icon class="avatar-mask__icon" :size="18"><Camera /></el-icon>
                <span>{{ avatarUploading ? "上传中…" : "更换头像" }}</span>
              </div>
            </div>
          </el-upload>
        </div>
        <div class="user-card__nickname">{{ displayName || "未设置昵称" }}</div>
        <div class="user-card__account">@{{ loginAccount || "—" }}</div>
        <div class="user-card__badges">
          <span class="badge"><el-icon :size="12"><User /></el-icon>{{ genderLabel }}</span>
          <span v-if="createdText" class="badge"><el-icon :size="12"><Calendar /></el-icon>{{ createdText }} 加入</span>
        </div>
        <p class="user-card__intro">{{ introduction.trim() || "还没有填写个人简介，点击下方编辑资料吧" }}</p>
      </div>

      <nav class="profile-menu">
        <div
          v-for="item in MENU_ITEMS"
          :key="item.key"
          class="menu-item"
          :class="{ active: activeMenu === item.key }"
          @click="activeMenu = item.key"
        >
          <el-icon :size="15"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
        <div class="menu-item menu-item--danger" @click="handleLogout">
          <el-icon :size="15"><SwitchButton /></el-icon>
          <span>退出登录</span>
        </div>
      </nav>
    </aside>

    <!-- 右侧内容区 -->
    <main class="profile-main" v-loading="loading">
      <div v-if="!userStore.token" class="login-tip">
        <el-empty description="登录后即可查看和管理个人资料">
          <el-button type="primary" @click="userStore.openLoginDialog('login')">去登录</el-button>
        </el-empty>
      </div>

      <template v-else>
        <!-- 基本信息 -->
        <section v-if="activeMenu === 'info'" class="content-card">
          <header class="section-header">
            <div>
              <h2 class="section-title">基本信息</h2>
              <p class="section-sub">完善个人资料，让 AI 更了解你的心理状态</p>
            </div>
          </header>

          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-width="86px"
            label-position="left"
            class="info-form"
          >
            <el-form-item label="账号名" prop="loginAccount">
              <el-input v-model.trim="profileForm.loginAccount" disabled placeholder="登录账号" />
            </el-form-item>
            <el-form-item label="昵称" prop="username">
              <el-input v-model.trim="profileForm.username" placeholder="请输入昵称" maxlength="12" show-word-limit />
            </el-form-item>
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model.trim="profileForm.mobile" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model.trim="profileForm.email" placeholder="用于找回密码与接收通知" maxlength="50" />
            </el-form-item>
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="profileForm.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="0">女</el-radio>
                <el-radio :value="2">保密</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="个人简介" prop="introduction">
              <el-input
                v-model.trim="profileForm.introduction"
                type="textarea"
                :rows="3"
                maxlength="80"
                show-word-limit
                placeholder="简单介绍一下自己，最多 80 个字"
              />
            </el-form-item>
            <el-form-item>
              <el-button class="action-btn" type="primary" :loading="profileSaving" @click="handleSaveProfile">
                保存修改
              </el-button>
              <el-button :disabled="profileSaving" @click="resetProfileForm">取消</el-button>
            </el-form-item>
          </el-form>
        </section>

        <!-- 修改密码 -->
        <section v-else-if="activeMenu === 'password'" class="content-card">
          <header class="section-header">
            <div>
              <h2 class="section-title">修改密码</h2>
              <p class="section-sub">新密码需包含大写字母、小写字母和数字，长度 6-20 位</p>
            </div>
          </header>

          <el-form
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-width="86px"
            label-position="left"
            class="info-form"
          >
            <el-form-item label="当前密码" prop="originalPassword">
              <el-input
                v-model="pwdForm.originalPassword"
                type="password"
                show-password
                placeholder="请输入当前密码"
                autocomplete="current-password"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="password">
              <el-input
                v-model="pwdForm.password"
                type="password"
                show-password
                placeholder="请输入新密码"
                autocomplete="new-password"
              />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="pwdForm.confirmPassword"
                type="password"
                show-password
                placeholder="请再次输入新密码"
                autocomplete="new-password"
                @keyup.enter="handleChangePassword"
              />
            </el-form-item>
            <el-form-item>
              <el-button class="action-btn" type="primary" :loading="pwdSaving" @click="handleChangePassword">
                确认修改
              </el-button>
            </el-form-item>
          </el-form>
        </section>

        <!-- 其他安全设置（占位） -->
        <section v-else class="content-card">
          <header class="section-header">
            <div>
              <h2 class="section-title">安全设置</h2>
              <p class="section-sub">账号安全相关功能，更多能力正在建设中</p>
            </div>
          </header>

          <div class="security-options">
            <div class="security-option">
              <div class="security-option__info">
                <span class="security-option__title">登录设备提醒</span>
                <span class="security-option__desc">当账号在新设备或异常地点登录时，及时向你发送提醒</span>
              </div>
              <el-tag type="info" effect="plain" size="small">敬请期待</el-tag>
            </div>
            <div class="security-option">
              <div class="security-option__info">
                <span class="security-option__title">双因素身份验证</span>
                <span class="security-option__desc">在登录时额外校验一次性验证码，为账号增加一层保护</span>
              </div>
              <el-tag type="info" effect="plain" size="small">敬请期待</el-tag>
            </div>
            <div class="security-option">
              <div class="security-option__info">
                <span class="security-option__title">隐私可见范围</span>
                <span class="security-option__desc">控制个人资料、测评记录等内容对外的可见范围</span>
              </div>
              <el-tag type="info" effect="plain" size="small">敬请期待</el-tag>
            </div>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Component } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { Calendar, Camera, Lock, Setting, SwitchButton, User } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/portal/stores/user";
import { getProfile, updateProfile } from "@/portal/api/user/profile";
import type { UserProfileUpdateDTO, UserProfileVO } from "@/portal/api/user/profile";
import { logout, pwdUpdate } from "@/portal/api/user/account";
import { uploadImage } from "@/shared/api/common/upload";
import { clearTokenInfo } from "@/shared/api/auth";
import dayjs, { FORMAT_DATE } from "@/shared/utils/dayjs";

/* ==================== 菜单与字典 ==================== */

type MenuKey = "info" | "password" | "security";

const MENU_ITEMS: { key: MenuKey; label: string; icon: Component }[] = [
  { key: "info", label: "基本信息", icon: User },
  { key: "password", label: "修改密码", icon: Lock },
  { key: "security", label: "安全设置", icon: Setting },
];

/** 性别字典：0 女 / 1 男 / 2 保密 */
const GENDER_MAP: Record<number, string> = { 0: "女", 1: "男", 2: "保密" };

/* ==================== 页内状态 ==================== */

const router = useRouter();
const userStore = useUserStore();

const activeMenu = ref<MenuKey>("info");
const loading = ref(false);

const profile = ref<UserProfileVO>({});

/** 基本信息表单（同时作为更新接口请求体） */
const profileForm = reactive<UserProfileUpdateDTO>({
  loginAccount: "",
  username: "",
  avatar: "",
  mobile: "",
  email: "",
  introduction: "",
  gender: 2,
});

const avatarUrl = computed(() => profileForm.avatar);
const displayName = computed(() => profileForm.username || userStore.userInfo?.username || "");
const loginAccount = computed(() => profileForm.loginAccount || userStore.userInfo?.loginAccount || "");
const introduction = computed(() => profileForm.introduction || "");
const genderLabel = computed(() => GENDER_MAP[profileForm.gender ?? 2] ?? "保密");
const createdText = computed(() =>
  profile.value.createdTime ? dayjs(profile.value.createdTime).format(FORMAT_DATE) : "",
);

const avatarText = computed(() => {
  const name = displayName.value.trim();
  return (name.charAt(0) || "U").toUpperCase();
});

/* ==================== 加载资料 ==================== */

function syncProfile(data: UserProfileVO) {
  profile.value = data;
  profileForm.loginAccount = data.loginAccount ?? "";
  profileForm.username = data.username ?? "";
  profileForm.avatar = data.avatar ?? "";
  profileForm.mobile = data.mobile ?? "";
  profileForm.email = data.email ?? "";
  profileForm.introduction = data.introduction ?? "";
  profileForm.gender = data.gender ?? 2;
}

async function loadProfile() {
  loading.value = true;
  try {
    const res = await getProfile();
    const data = res.data?.data;
    if (data) {
      syncProfile(data);
      // 同步全局用户信息，保证顶栏头像 / 昵称保持一致
      userStore.setUserInfo({ ...userStore.userInfo, ...data });
    }
  } catch {
    ElMessage.error("加载个人资料失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (userStore.token) {
    void loadProfile();
  }
});

/* ==================== 头像上传 ==================== */

const AVATAR_MAX_SIZE = 5 * 1024 * 1024;
const avatarUploading = ref(false);

function beforeAvatarUpload(file: File): boolean {
  if (!file.type.startsWith("image/")) {
    ElMessage.error("请选择图片文件");
    return false;
  }
  if (file.size > AVATAR_MAX_SIZE) {
    ElMessage.error("图片大小不能超过 5MB");
    return false;
  }
  return true;
}

async function handleAvatarUpload(options: { file: File }) {
  if (avatarUploading.value) return;
  avatarUploading.value = true;
  try {
    const res = await uploadImage(options.file);
    const url = res.data?.data;
    if (!url) throw new Error("empty url");
    profileForm.avatar = url;
    // 上传成功后立即持久化头像并同步全局用户信息
    await updateProfile({ ...profileForm });
    profile.value = { ...profile.value, avatar: url };
    userStore.setUserInfo({ ...userStore.userInfo, ...profile.value });
    ElMessage.success("头像已更新");
  } catch {
    ElMessage.error("头像上传失败，请稍后重试");
  } finally {
    avatarUploading.value = false;
  }
}

/* ==================== 保存基本信息 ==================== */

const profileFormRef = ref<FormInstance>();
const profileSaving = ref(false);

const profileRules: FormRules = {
  username: [
    { required: true, message: "请输入昵称", trigger: "blur" },
    { min: 2, max: 12, message: "昵称长度为 2~12 个字符", trigger: "blur" },
  ],
  mobile: [{ pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }],
  email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
  introduction: [{ max: 80, message: "个人简介不能超过 80 个字", trigger: "blur" }],
};

function resetProfileForm() {
  syncProfile(profile.value);
  profileFormRef.value?.clearValidate();
}

async function handleSaveProfile() {
  const valid = profileFormRef.value ? await profileFormRef.value.validate().catch(() => false) : false;
  if (!valid) return;
  profileSaving.value = true;
  try {
    await updateProfile({ ...profileForm });
    profile.value = { ...profile.value, ...profileForm };
    userStore.setUserInfo({ ...userStore.userInfo, ...profile.value });
    ElMessage.success("个人资料已保存");
  } catch {
    ElMessage.error("保存失败，请稍后重试");
  } finally {
    profileSaving.value = false;
  }
}

/* ==================== 修改密码 ==================== */

const pwdFormRef = ref<FormInstance>();
const pwdSaving = ref(false);

const pwdForm = reactive({
  originalPassword: "",
  password: "",
  confirmPassword: "",
});

/** 校验两次输入的密码是否一致 */
const validateConfirm = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error("请再次输入新密码"));
  } else if (value !== pwdForm.password) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const pwdRules: FormRules = {
  originalPassword: [{ required: true, message: "请输入当前密码", trigger: "blur" }],
  password: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    {
      pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)[\s\S]{6,20}$/,
      message: "新密码需包含大写字母、小写字母和数字，长度 6-20 位",
      trigger: "blur",
    },
  ],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: "blur" }],
};

async function handleChangePassword() {
  const valid = pwdFormRef.value ? await pwdFormRef.value.validate().catch(() => false) : false;
  if (!valid) return;
  pwdSaving.value = true;
  try {
    await pwdUpdate({ originalPassword: pwdForm.originalPassword, password: pwdForm.password });
    ElMessage.success("密码修改成功，下次登录请使用新密码");
    pwdForm.originalPassword = "";
    pwdForm.password = "";
    pwdForm.confirmPassword = "";
    pwdFormRef.value?.clearValidate();
  } catch {
    ElMessage.error("修改失败，请检查当前密码是否正确");
  } finally {
    pwdSaving.value = false;
  }
}

/* ==================== 退出登录 ==================== */

async function handleLogout() {
  try {
    // 先调用后端登出接口（清理 Redis 中的登录态），再清理本地凭证
    await logout();
  } catch {
    // 后端登出失败不阻断本地退出（本地凭证仍会被清除）
  } finally {
    userStore.clearUser();
    // 退出必须同步清除 localStorage 凭证（含 legacy 'token'），否则请求仍会携带旧 token
    clearTokenInfo("user");
    router.push("/");
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 1024px;
  margin: 0 auto;
  padding: 28px 24px;
  display: grid;
  grid-template-columns: 272px 1fr;
  gap: 20px;
  align-items: start;
}

/* ==================== 左侧用户卡片 ==================== */

.profile-side {
  background: #fff;
  border: 1px solid #eceef5;
  border-radius: 16px;
  overflow: hidden;
  position: sticky;
  top: 76px;
  box-shadow: 0 6px 24px rgba(108, 99, 255, 0.06);
}

.user-card {
  position: relative;
  padding: 28px 20px 20px;
  text-align: center;
  background: radial-gradient(120% 90% at 50% 0%, rgba(108, 99, 255, 0.12) 0%, rgba(108, 99, 255, 0.03) 55%, #fff 100%);
}

.avatar-uploader {
  display: inline-block;
}

.avatar-wrap {
  position: relative;
  width: 92px;
  height: 92px;
  margin: 0 auto 14px;
  border-radius: 50%;
  cursor: pointer;
}

.avatar-img,
.avatar-initials {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-initials {
  color: #fff;
  font-size: 38px;
  font-weight: 600;
  background: linear-gradient(135deg, #6c63ff, #8b83ff);
  box-shadow: 0 8px 20px rgba(108, 99, 255, 0.3);
}

.avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  background: rgba(40, 35, 90, 0.55);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
}

.avatar-mask__icon {
  margin-bottom: 2px;
}

.user-card__nickname {
  font-size: 18px;
  font-weight: 700;
  color: #2a2f45;
  line-height: 24px;
  word-break: break-all;
}

.user-card__account {
  margin-top: 3px;
  font-size: 12.5px;
  color: #8a8fa3;
}

.user-card__badges {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  color: #6c63ff;
  background: #f0eeff;
}

.user-card__intro {
  margin: 14px 0 0;
  padding: 10px 12px;
  min-height: 60px;
  font-size: 12px;
  line-height: 1.6;
  color: #6b7080;
  text-align: left;
  background: #fafaff;
  border: 1px solid #eef0f8;
  border-radius: 10px;
  word-break: break-all;
  white-space: pre-wrap;
}

.profile-menu {
  padding: 8px 12px 14px;
  border-top: 1px dashed #eceef5;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-top: 4px;
  border-radius: 10px;
  font-size: 13.5px;
  font-weight: 500;
  color: #555a6a;
  cursor: pointer;
  transition: all 0.18s ease;
}

.menu-item:hover {
  color: #6c63ff;
  background: #f5f3ff;
}

.menu-item.active {
  color: #fff;
  background: linear-gradient(135deg, #6c63ff, #8b83ff);
  box-shadow: 0 6px 14px rgba(108, 99, 255, 0.32);
}

.menu-item--danger {
  color: #e5484d;
}

.menu-item--danger:hover {
  color: #e5484d;
  background: #fff0f0;
}

/* ==================== 右侧内容区 ==================== */

.profile-main {
  background: #fff;
  border: 1px solid #eceef5;
  border-radius: 16px;
  padding: 28px 30px;
  min-height: 440px;
  box-shadow: 0 6px 24px rgba(108, 99, 255, 0.06);
}

.login-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.section-header {
  margin-bottom: 22px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0f1f6;
}

.section-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #2a2f45;
}

.section-sub {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: #9aa0b0;
}

.info-form {
  max-width: 480px;
}

.info-form :deep(.el-form-item__label) {
  color: #555a6a;
}

.info-form :deep(.el-input__wrapper) {
  border-radius: 9px;
}

.info-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #6c63ff inset;
}

.info-form :deep(.el-textarea__inner) {
  border-radius: 9px;
}

.info-form :deep(.el-radio__input.is-checked .el-radio__inner) {
  background-color: #6c63ff;
  border-color: #6c63ff;
}

.info-form :deep(.el-radio__input.is-checked + .el-radio__label) {
  color: #6c63ff;
}

.action-btn {
  border: none;
  background: linear-gradient(135deg, #6c63ff, #8b83ff);
  box-shadow: 0 6px 14px rgba(108, 99, 255, 0.32);
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(108, 99, 255, 0.4);
}

/* ==================== 安全设置（占位） ==================== */

.security-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.security-option {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  background: #fafaff;
  border: 1px solid #eef0f8;
  border-radius: 12px;
}

.security-option__info {
  flex: 1;
  min-width: 0;
}

.security-option__title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #33394a;
}

.security-option__desc {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: #9aa0b0;
  line-height: 1.5;
}

/* ==================== 响应式 ==================== */

@media (max-width: 860px) {
  .profile-page {
    grid-template-columns: 1fr;
    padding: 18px 14px;
  }

  .profile-side {
    position: static;
  }
}
</style>