<template>
  <div class="profile-page">
    <!-- 顶部用户卡片 -->
    <section class="user-card">
      <div class="user-card__glow" aria-hidden="true"></div>
      <div class="user-card__avatar">
        <el-upload
          class="avatar-uploader"
          :show-file-list="false"
          accept="image/*"
          :http-request="handleAvatarUpload"
          :before-upload="beforeAvatarUpload"
        >
          <div class="avatar-wrap">
            <el-avatar v-if="avatarUrl" :src="avatarUrl" :size="92" shape="circle" class="avatar-img" />
            <div v-else class="avatar-initials">{{ avatarText }}</div>
            <div class="avatar-mask">
              <el-icon class="avatar-mask__icon" :size="18"><Camera /></el-icon>
              <span>更换头像</span>
            </div>
          </div>
        </el-upload>
      </div>
      <div class="user-card__info">
        <div class="user-card__name-row">
          <span class="user-card__name">{{ profile.username || "—" }}</span>
          <span class="status-badge" :class="statusMeta.cls">{{ statusMeta.label }}</span>
        </div>
        <div class="user-card__account">
          <el-icon :size="14"><User /></el-icon>
          <span>账号：{{ profile.loginAccount || "—" }}</span>
        </div>
        <div class="user-card__created">
          <el-icon :size="14"><Calendar /></el-icon>
          <span>创建时间：{{ formatTime(profile.createdTime) }}</span>
        </div>
        <div v-if="status === 2 && profile.banReason" class="user-card__ban">
          <el-icon :size="14"><WarningFilled /></el-icon>
          <span class="user-card__ban-reason">{{ profile.banReason }}</span>
        </div>
      </div>
      <el-button class="user-card__refresh" text size="small" :loading="loading" @click="loadProfile">
        刷新
      </el-button>
    </section>

    <!-- 基本信息 + 修改密码 -->
    <div class="form-grid">
      <section class="form-card">
        <div class="form-card__header">
          <span class="form-card__icon" style="color: #6366f1; background: #eef2ff">
            <el-icon :size="16"><User /></el-icon>
          </span>
          <span class="form-card__title">基本信息</span>
          <span class="form-card__tip">账号名不可修改，如需变更请联系超级管理员</span>
        </div>

        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-width="84px"
          class="profile-form"
        >
          <el-form-item label="账号" prop="loginAccount">
            <el-input v-model.trim="profileForm.loginAccount" disabled placeholder="登录账号" />
          </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model.trim="profileForm.username"
              placeholder="请输入用户名"
              maxlength="12"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="手机号" prop="mobile">
            <el-input v-model.trim="profileForm.mobile" placeholder="请输入手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model.trim="profileForm.email" placeholder="请输入邮箱" maxlength="50" />
          </el-form-item>
          <el-form-item class="form-card__actions">
            <el-button type="primary" :loading="profileSaving" @click="handleSaveProfile">
              <el-icon style="margin-right: 4px"><Check /></el-icon>
              保存修改
            </el-button>
            <el-button :disabled="profileSaving" @click="resetProfileForm">重置</el-button>
          </el-form-item>
        </el-form>
      </section>

      <section class="form-card">
        <div class="form-card__header">
          <span class="form-card__icon" style="color: #f59e0b; background: #fffbeb">
            <el-icon :size="16"><Lock /></el-icon>
          </span>
          <span class="form-card__title">修改密码</span>
          <span class="form-card__tip">新密码需包含大写字母、小写字母和数字，长度 6-20 位</span>
        </div>

        <el-form
          ref="pwdFormRef"
          :model="pwdForm"
          :rules="pwdRules"
          label-width="84px"
          class="profile-form"
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
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="pwdForm.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
              autocomplete="new-password"
              @keyup.enter="handleChangePassword"
            />
          </el-form-item>
          <el-form-item class="form-card__actions">
            <el-button type="primary" :loading="pwdSaving" @click="handleChangePassword">
              <el-icon style="margin-right: 4px"><Key /></el-icon>
              修改密码
            </el-button>
            <el-button :disabled="pwdSaving" @click="resetPwdForm">重置</el-button>
          </el-form-item>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { Calendar, Camera, Check, Key, Lock, User, WarningFilled } from "@element-plus/icons-vue";
import { getProfile, updateProfile } from "@/admin/api/admin/profile";
import type { AdminProfileVO } from "@/admin/api/admin/profile";
import { pwdUpdate } from "@/admin/api/admin/account";
import { uploadImage } from "@shared/api/common/upload";
import { resolveResourceUrl } from "@shared/utils/resource";
import dayjs from "@/shared/utils/dayjs";

/* ==================== 状态元数据（0 正常 / 1 异常 / 2 封禁 / 3 注销） ==================== */

const STATUS_META: Record<number, { label: string; cls: string }> = {
  0: { label: "正常", cls: "ok" },
  1: { label: "异常", cls: "warn" },
  2: { label: "封禁", cls: "ban" },
  3: { label: "注销", cls: "off" },
};

function statusLabel(status: number | undefined): string {
  return status === undefined || status === null ? "未知" : (STATUS_META[status]?.label ?? "未知");
}

/* ==================== 资料状态 ==================== */

const loading = ref(false);
const profile = ref<Partial<AdminProfileVO>>({});

const status = computed(() => profile.value.status);
const statusMeta = computed(() => {
  const s = status.value;
  const meta = (s === undefined || s === null) ? STATUS_META[3] : (STATUS_META[s] ?? STATUS_META[3]);
  return { label: statusLabel(s), cls: meta.cls };
});

const avatarText = computed(() => {
  const u = profile.value.username ?? "";
  return (u.trim().charAt(0) || profile.value.loginAccount?.trim().charAt(0) || "A").toUpperCase();
});

/** 头像原始路径（后端返回相对路径，展示时经 resolveResourceUrl 拼接 /api 前缀） */
const avatarPath = ref("");
const avatarUrl = computed(() => resolveResourceUrl(avatarPath.value));

function formatTime(v?: string): string {
  return v ? dayjs(v).format("YYYY-MM-DD HH:mm") : "—";
}

async function loadProfile() {
  loading.value = true;
  try {
    const res = await getProfile();
    const data = res.data?.data;
    if (data) {
      profile.value = data;
      profileForm.username = data.username ?? "";
      profileForm.mobile = data.mobile ?? "";
      profileForm.email = data.email ?? "";
      profileForm.loginAccount = data.loginAccount ?? "";
    }
  } catch {
    ElMessage.error("加载个人资料失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}

/* ==================== 头像上传 ==================== */

const avatarUploading = ref(false);

const AVATAR_MAX_SIZE = 5 * 1024 * 1024;

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
    avatarPath.value = url;
    ElMessage.success("头像上传成功");
  } catch {
    ElMessage.error("头像上传失败，请稍后重试");
  } finally {
    avatarUploading.value = false;
  }
}

onMounted(loadProfile);

/* ==================== 基本信息 ==================== */

const profileFormRef = ref<FormInstance>();
const profileSaving = ref(false);

const profileForm = reactive({
  loginAccount: "",
  username: "",
  mobile: "",
  email: "",
});

const profileRules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 2, max: 12, message: "用户名长度为 2~12 个字符", trigger: "blur" },
  ],
  mobile: [
    // 后端 AdminProfileDTO.mobile 为可选字段：不强制填写，填写时才校验格式
    { pattern: /^1[3-9]\d{9}$/, message: "手机号码格式不正确", trigger: "blur" },
  ],
  email: [
    // 后端 AdminProfileDTO.email 为可选字段：不强制填写，填写时才校验格式
    { type: "email", message: "邮箱格式不正确", trigger: "blur" },
  ],
};

function resetProfileForm() {
  profileForm.username = profile.value.username ?? "";
  profileForm.mobile = profile.value.mobile ?? "";
  profileForm.email = profile.value.email ?? "";
  profileFormRef.value?.clearValidate();
}

async function handleSaveProfile() {
  const valid = profileFormRef.value ? await profileFormRef.value.validate().catch(() => false) : false;
  if (!valid) return;
  profileSaving.value = true;
  try {
    await updateProfile({
      username: profileForm.username,
      // 空值不提交：后端 @Pattern/@Email 不接受空串，字段留空时应保持原值
      mobile: profileForm.mobile || undefined,
      email: profileForm.email || undefined,
    });
    ElMessage.success("个人资料已保存");
    await loadProfile();
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

function resetPwdForm() {
  pwdForm.originalPassword = "";
  pwdForm.password = "";
  pwdForm.confirmPassword = "";
  pwdFormRef.value?.clearValidate();
}

async function handleChangePassword() {
  const valid = pwdFormRef.value ? await pwdFormRef.value.validate().catch(() => false) : false;
  if (!valid) return;
  pwdSaving.value = true;
  try {
    await pwdUpdate({
      originalPassword: pwdForm.originalPassword,
      password: pwdForm.password,
    });
    ElMessage.success("密码修改成功，下次登录请使用新密码");
    resetPwdForm();
  } catch {
    ElMessage.error("修改失败，请检查当前密码是否正确");
  } finally {
    pwdSaving.value = false;
  }
}
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ==================== 顶部用户卡片 ==================== */

.user-card {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 26px 28px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.user-card__glow {
  position: absolute;
  top: -60px;
  right: -40px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.12), transparent 70%);
  pointer-events: none;
}

.user-card__avatar {
  flex-shrink: 0;
  position: relative;
}

.avatar-uploader {
  display: block;
}

.avatar-wrap {
  position: relative;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  cursor: pointer;
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
  background: rgba(15, 23, 42, 0.55);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
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
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.3);
}

.user-card__info {
  flex: 1;
  min-width: 0;
}

.user-card__name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.user-card__name {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}

.status-badge.ok {
  color: #10b981;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

.status-badge.warn {
  color: #f59e0b;
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.status-badge.ban {
  color: #ef4444;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.status-badge.off {
  color: #94a3b8;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.user-card__account,
.user-card__created,
.user-card__ban {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}

.user-card__account .el-icon,
.user-card__created .el-icon,
.user-card__ban .el-icon {
  color: #94a3b8;
}

.user-card__ban {
  margin-top: 10px;
  padding: 6px 10px;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
}

.user-card__ban .el-icon {
  color: #ef4444;
}

.user-card__ban-reason {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card__refresh {
  flex-shrink: 0;
}

/* ==================== 表单卡片 ==================== */

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 18px;
}

.form-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 20px 22px 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.form-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  margin-bottom: 18px;
  border-bottom: 1px solid #f1f5f9;
}

.form-card__icon {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-card__title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.form-card__tip {
  margin-left: auto;
  font-size: 11.5px;
  color: #94a3b8;
}

.profile-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: #475569;
}

.profile-form :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.profile-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #6366f1 inset;
}

.profile-form :deep(.el-input.is-disabled .el-input__wrapper) {
  background: #f8fafc;
}

.form-card__actions {
  margin-top: 4px;
  margin-bottom: 0;
}

.form-card__actions :deep(.el-form-item__content) {
  display: flex;
  gap: 10px;
}

.form-card__actions .el-button {
  border-radius: 8px;
}

.form-card__actions .el-button--primary {
  background: linear-gradient(135deg, #6366f1, #7c3aed);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.form-card__actions .el-button--primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.4);
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .user-card {
    flex-direction: column;
    text-align: center;
  }

  .user-card__name-row {
    justify-content: center;
  }

  .user-card__account,
  .user-card__created,
  .user-card__ban {
    justify-content: center;
  }

  .user-card__refresh {
    display: none;
  }
}
</style>
