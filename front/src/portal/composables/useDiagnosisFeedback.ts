import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import type { AssessmentFeedbackVO } from "@/portal/api/conversation/emotion-diagnosis";
import { getDiagnosisFeedback, submitDiagnosisFeedback } from "@/portal/api/conversation/emotion-diagnosis";

export function useDiagnosisFeedback(diagnosisId: string) {
  const feedback = ref<AssessmentFeedbackVO | null>(null);
  const editing = ref(false);
  const submitting = ref(false);

  const form = reactive({
    diagnosisScore: 0,
    agreeRisk: false,
    agreeSelf: false,
    agreeSocial: false,
    agreePro: false,
    useSuggestion: null as number | null,
    feedbackContent: "" as string | null,
  });

  async function loadFeedback() {
    try {
      const res = await getDiagnosisFeedback(diagnosisId);
      feedback.value = res.data.data ?? null;
    } catch {
      feedback.value = null;
    }
  }

  function startEdit() {
    const f = feedback.value;
    form.diagnosisScore = f?.diagnosisScore ?? 0;
    form.agreeRisk = f?.agreeRiskJudge === 1;
    form.agreeSelf = f?.agreeSuggestionSelf === 1;
    form.agreeSocial = f?.agreeSuggestionSocial === 1;
    form.agreePro = f?.agreeSuggestionProfessional === 1;
    form.useSuggestion = f?.useSuggestion ?? null;
    form.feedbackContent = f?.feedbackContent ?? "";
    editing.value = true;
  }

  function onUseSuggestionChange(val: string | number | boolean | undefined) {
    // 注意：0 表示「未采纳」是合法值，不能用 `Number(val) || null` 吞掉
    form.useSuggestion = val == null || val === "" ? null : Number(val);
  }

  async function submitFeedback() {
    submitting.value = true;
    try {
      await submitDiagnosisFeedback(diagnosisId, {
        diagnosisScore: form.diagnosisScore > 0 ? form.diagnosisScore : null,
        feedbackContent: (form.feedbackContent ?? "").trim() || null,
        agreeRiskJudge: form.agreeRisk ? 1 : 0,
        agreeSuggestionSelf: form.agreeSelf ? 1 : 0,
        agreeSuggestionSocial: form.agreeSocial ? 1 : 0,
        agreeSuggestionProfessional: form.agreePro ? 1 : 0,
        useSuggestion: form.useSuggestion,
      });
      ElMessage.success("反馈提交成功，感谢你的参与");
      editing.value = false;
      await loadFeedback();
    } catch {
      ElMessage.error("反馈提交失败，请稍后重试");
    } finally {
      submitting.value = false;
    }
  }

  function agreeText(v?: number | null): string {
    if (v == null) return "未反馈";
    return v === 1 ? "认同" : "不认同";
  }

  function agreeTagType(v?: number | null): "success" | "warning" | "danger" | "info" {
    if (v == null) return "info";
    return v === 1 ? "success" : "danger";
  }

  function useSuggestionText(v?: number | null): string {
    if (v == null) return "未反馈";
    return ["未采纳", "尝试部分", "全部采纳"][v] ?? "未反馈";
  }

  return {
    feedback,
    editing,
    submitting,
    form,
    loadFeedback,
    startEdit,
    onUseSuggestionChange,
    submitFeedback,
    agreeText,
    agreeTagType,
    useSuggestionText,
  };
}