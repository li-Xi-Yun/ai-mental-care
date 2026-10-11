import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import type { FormRules } from "element-plus";
import { getProfile, updateProfile } from "@/admin/api/admin/profile";
import type { AdminProfileVO } from "@/admin/api/admin/profile";
import { pwdUpdate } from "@/admin/api/admin/account";
import { uploadImage } from "@shared/api/common/upload";
import { resolveResourceUrl } from "@shared/utils/resource";
import { PROFILE_STATUS_META, profileStatusLabel } from "@/admin/constants/profile";
import { useAdminStore } from "@/admin/stores/admin";
import dayjs from "@/shared/utils/dayjs";

/** 基本信息表单字段 */
export interface ProfileFormState {
  loginAccount: string;
  username: string;
  mobile: string;
  email: string;
}

/** 修改密码表单字段 */
export interface PwdFormState {
  originalPassword: string;
  password: string;
  confirmPassword: string;
}

/**
 * 个人资料页全部业务逻辑（单一职责拆分）：
 * - 资料加载 / 状态与头像展示
 * - 头像上传（校验 + 上传）
 * - 基本信息表单（校验规则 + 保存 / 重置）
 * - 修改密码表单（校验规则 + 提交 / 重置）
 *
 * 两个表单卡子组件自持 FormInstance 完成校验，通过事件回传提交/重置动作。
 */
export function useProfile() {
  const adminStore = useAdminStore();

  /* ==================== 资料状态 ==================== */

  const loading = ref(false);
  const profile = ref<Partial<AdminProfileVO>>({});

  const status = computed(() => profile.value.status);
  const statusMeta = computed(() => {
    const s = status.value;
    const meta = s === undefined || s === null ? PROFILE_STATUS_META[3] : (PROFILE_STATUS_META[s] ?? PROFILE_STATUS_META[3]);
    return { label: profileStatusLabel(s), cls: meta.cls };
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

  const profileSaving = ref(false);

  const profileForm = reactive<ProfileFormState>({
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

  /** 重置为最近一次加载的资料值（校验状态由 BasicInfoForm 内部清除） */
  function resetProfileForm() {
    profileForm.username = profile.value.username ?? "";
    profileForm.mobile = profile.value.mobile ?? "";
    profileForm.email = profile.value.email ?? "";
  }

  /** 保存（表单校验由 BasicInfoForm 完成后触发） */
  async function handleSaveProfile() {
    profileSaving.value = true;
    try {
      await updateProfile({
        username: profileForm.username,
        // 空值不提交：后端 @Pattern/@Email 不接受空串，字段留空时应保持原值
        mobile: profileForm.mobile || undefined,
        email: profileForm.email || undefined,
      });
      ElMessage.success("个人资料已保存");
      // 同步 Pinia store：顶栏/侧栏的管理员名称立即更新
      adminStore.setAdminInfo({ ...adminStore.adminInfo, username: profileForm.username });
      await loadProfile();
    } catch {
      ElMessage.error("保存失败，请稍后重试");
    } finally {
      profileSaving.value = false;
    }
  }

  /* ==================== 修改密码 ==================== */

  const pwdSaving = ref(false);

  const pwdForm = reactive<PwdFormState>({
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

  /** 重置密码表单（校验状态由 PasswordForm 内部清除） */
  function resetPwdForm() {
    pwdForm.originalPassword = "";
    pwdForm.password = "";
    pwdForm.confirmPassword = "";
  }

  /** 修改密码（表单校验由 PasswordForm 完成后触发） */
  async function handleChangePassword() {
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

  return {
    // 资料
    loading,
    profile,
    status,
    statusMeta,
    avatarText,
    avatarUrl,
    formatTime,
    loadProfile,
    // 头像上传
    beforeAvatarUpload,
    handleAvatarUpload,
    // 基本信息
    profileForm,
    profileRules,
    profileSaving,
    handleSaveProfile,
    resetProfileForm,
    // 修改密码
    pwdForm,
    pwdRules,
    pwdSaving,
    handleChangePassword,
    resetPwdForm,
  };
}

export type UseProfile = ReturnType<typeof useProfile>;
