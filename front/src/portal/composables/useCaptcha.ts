import { ref } from "vue";
import { ElMessage } from "element-plus";
import { getCode } from "@shared/api/common/verification";

export function useCaptcha() {
  const captchaLoading = ref(false);
  const captchaImage = ref("");
  const captchaKey = ref("");

  function normalizeCaptchaImage(image: string) {
    return image.startsWith("data:") ? image : `data:image/png;base64,${image}`;
  }

  function clearCaptchaFields(...forms: { code: string }[]) {
    for (const form of forms) {
      form.code = "";
    }
  }

  async function refreshCaptcha(...forms: { code: string }[]) {
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
      clearCaptchaFields(...forms);
    } catch (error) {
      ElMessage.error((error as Error)?.message || "验证码加载失败，请检查网络后重试");
    } finally {
      captchaLoading.value = false;
    }
  }

  return {
    captchaLoading,
    captchaImage,
    captchaKey,
    refreshCaptcha,
  };
}