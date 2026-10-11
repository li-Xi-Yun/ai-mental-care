import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { login } from "@/admin/api/admin/account";
import type { AdminLoginResultVO } from "@/admin/api/admin/account";
import { setTokenInfo } from "@/shared/api/auth";
import { getCode } from "@shared/api/common/verification";
import { useAdminStore } from "@/admin/stores/admin";
import { getStorage, setStorage } from "@/shared/utils/storage";

/**
 * 管理员登录页全部业务逻辑（单一职责）：
 * 表单校验 / 验证码获取与刷新 / 记住账号恢复 / 登录提交与跳转。
 * 仅逻辑拆分，页面视觉样式保持原样。
 */
export function useLogin() {
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

  // 初始化：获取验证码 + 恢复记住的账号（与原页面 setup 同步执行时机保持一致）
  refreshCaptcha();
  restoreRememberedAccount();

  return {
    formRef,
    form,
    rules,
    loading,
    captchaImage,
    captchaLoading,
    currentYear,
    refreshCaptcha,
    handleLogin,
  };
}

export type UseLogin = ReturnType<typeof useLogin>;
