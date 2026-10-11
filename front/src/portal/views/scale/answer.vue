<template>
  <div class="scale-answer-page">
    <!-- 加载态 -->
    <div v-if="loading" class="answer-loading">
      <el-icon class="is-loading" :size="40" color="#6c63ff">
        <Loading />
      </el-icon>
      <p class="loading-text">正在准备测评…</p>
    </div>

    <!-- 游客态 -->
    <div v-else-if="!userStore.token" class="answer-error">
      <el-empty description="登录后即可开始测评" />
      <el-button type="primary" @click="userStore.openLoginDialog('login')">去登录</el-button>
    </div>

    <!-- 空态/错误态 -->
    <div v-else-if="error || !startData" class="answer-error">
      <el-empty :description="error || '测评数据加载失败'" />
      <el-button type="primary" plain @click="handleBackToList">返回量表列表</el-button>
    </div>

    <!-- 答题主体 -->
    <template v-else>
      <div class="breadcrumb">
        <router-link to="/scale" class="breadcrumb-link">量表测评</router-link>
        <span class="breadcrumb-sep">›</span>
        <span class="breadcrumb-current">{{ startData.scaleName }}</span>
      </div>

      <AnswerHeader
        :scale-name="startData.scaleName"
        :total-question-count="startData.totalQuestionCount"
        :version-no="startData.versionNo"
        :description="startData.description"
        :time-limit="timeLimit"
        :estimated-minutes="estimatedMinutes"
        :time-warning="timeWarning"
        :formatted-time="formattedTime"
      />

      <div class="progress-row">
        <span class="progress-label">作答进度</span>
        <span class="progress-count">{{ answeredCount }} / {{ startData.totalQuestionCount }}</span>
      </div>
      <el-progress
        class="progress-bar"
        :percentage="progressPercent"
        :stroke-width="8"
        color="#6c63ff"
        :show-text="false"
      />

      <transition name="slide-fade" mode="out-in">
        <QuestionCard
          :key="currentIndex"
          :current-index="currentIndex"
          :total-count="startData.totalQuestionCount"
          :title="question.title"
          :question-type="question.questionType"
          :options="question.options"
          :selected-ids="currentSelectedIds"
          :answer-text="currentAnswerText"
          :is-required="isCurrentRequired"
          :answer-ready="currentAnswerReady"
          :copyright-info="startData.copyrightInfo"
          @select-single="handleSelectSingle"
          @toggle-multi="handleToggleMulti"
          @update:answer-text="currentAnswerText = $event; handleFillInput()"
        />
      </transition>

      <div class="q-actions">
        <el-button class="action-btn" :disabled="currentIndex === 0" @click="goPrev">上一题</el-button>
        <el-button
          v-if="!isLastQuestion"
          type="primary"
          class="action-btn primary"
          :disabled="isCurrentRequired && !currentAnswerReady"
          @click="goNext"
        >下一题</el-button>
        <el-button
          v-else
          type="primary"
          class="action-btn primary"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >提交答卷</el-button>
      </div>

      <AnswerNavGrid
        :answered-count="answeredCount"
        :total-count="startData.totalQuestionCount"
        :cells="navCells"
        @jump="jumpTo"
      />

      <div class="quit-row">
        <el-button text type="danger" :disabled="submitting" @click="handleTerminate">结束测评，不保存本次作答</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { useRoute, useRouter, onBeforeRouteLeave } from "vue-router";
import { Loading } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { startAssessment, resumeAssessment, submitAssessment, terminateAssessment, bindToolRecord } from "@/portal/api/scale/user-scale";
import type { ScaleStartData, ScaleStartQuestion } from "@/portal/api/scale/user-scale";
import { getScalePrecheck } from "@/portal/api/scale/precheck";
import type { ScalePrecheckVO } from "@/portal/api/scale/precheck";
import { useUserStore } from "@/portal/stores/user";
import { useAnswerTimer } from "@/portal/composables/useAnswerTimer";
import { useAnswerDraft } from "@/portal/composables/useAnswerDraft";
import AnswerHeader from "@/portal/components/scale/AnswerHeader.vue";
import QuestionCard from "@/portal/components/scale/QuestionCard.vue";
import AnswerNavGrid from "@/portal/components/scale/AnswerNavGrid.vue";

/* ==================== 常量 ==================== */

const QUESTION_TYPE = {
  SINGLE: 1,
  MULTI: 2,
  FILL: 3,
} as const;

const REQUIRED_YES = 1;

/* ==================== 路由 ==================== */

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const scaleId = Number(route.params.scaleId as string);
const routeRecordId = Number(route.query.recordId as string) || 0;
const routeToolId = Number(route.query.toolId as string) || 0;
const routeConversationId = Number(route.query.conversationId as string) || 0;

/* ==================== 基础状态 ==================== */

const loading = ref(true);
const error = ref("");
const submitting = ref(false);
const startData = ref<ScaleStartData | null>(null);

/* ==================== composables ==================== */

const { answers, currentIndex, restoreDraft, persistDraft, clearDraft } = useAnswerDraft(scaleId);
const { timeLimit, timeWarning, formattedTime, startTimer, stopTimer } = useAnswerTimer();

/* ==================== 当前题相关 ==================== */

const question = computed<ScaleStartQuestion>(() => {
  const q = startData.value?.questions[currentIndex.value];
  return q ?? (startData.value?.questions[0] as ScaleStartQuestion);
});

const isLastQuestion = computed(() => currentIndex.value >= (startData.value?.questions.length ?? 1) - 1);
const isCurrentRequired = computed(() => question.value?.required === REQUIRED_YES);
const currentSelectedIds = computed<number[]>(() => answers.value[question.value?.questionId]?.optionIds ?? []);
const currentAnswerText = ref("");

const currentAnswerReady = computed(() => {
  const entry = answers.value[question.value?.questionId];
  if (!entry) return false;
  if (question.value?.questionType === QUESTION_TYPE.MULTI) return entry.optionIds.length > 0;
  if (question.value?.questionType === QUESTION_TYPE.FILL) return Boolean(entry.answerText?.trim());
  return entry.optionIds.length > 0;
});

/* ==================== 进度 ==================== */

const answeredCount = computed(() => {
  let count = 0;
  for (const q of startData.value?.questions ?? []) {
    const entry = answers.value[q.questionId];
    if (!entry) continue;
    if (q.questionType === QUESTION_TYPE.MULTI && entry.optionIds.length > 0) count++;
    else if (q.questionType === QUESTION_TYPE.FILL && entry.answerText?.trim()) count++;
    else if (q.questionType === QUESTION_TYPE.SINGLE && entry.optionIds.length > 0) count++;
  }
  return count;
});

const progressPercent = computed(() => {
  const total = startData.value?.totalQuestionCount ?? 1;
  return Math.min(100, Math.round((answeredCount.value / total) * 100));
});

const estimatedMinutes = computed(() => {
  const count = startData.value?.totalQuestionCount ?? 0;
  if (count <= 0) return 0;
  return Math.max(1, Math.round((count * 20) / 60));
});

const canSubmit = computed(() => {
  const q = question.value;
  if (!q) return false;
  if (q.required === REQUIRED_YES) return currentAnswerReady.value;
  return true;
});

/* ==================== 导航单元格 ==================== */

const navCells = computed(() => {
  return (startData.value?.questions ?? []).map((q, index) => {
    const entry = answers.value[q.questionId];
    const done =
      q.questionType === QUESTION_TYPE.MULTI
        ? Boolean(entry?.optionIds.length)
        : q.questionType === QUESTION_TYPE.FILL
          ? Boolean(entry?.answerText?.trim())
          : Boolean(entry?.optionIds.length);
    return {
      questionId: q.questionId,
      title: q.title,
      classes: {
        done,
        "required-missing": !done && q.required === REQUIRED_YES,
        active: index === currentIndex.value,
      },
    };
  });
});

/* ==================== 持久化 ==================== */

watch(answers, () => {
  if (startData.value) persistDraft(startData.value.recordId);
}, { deep: true });
watch(currentIndex, () => {
  if (startData.value) persistDraft(startData.value.recordId);
});

/* ==================== 作答交互 ==================== */

function handleSelectSingle(optionId: number) {
  const q = question.value;
  if (!q) return;
  answers.value[q.questionId] = { optionIds: [optionId], spendSeconds: 0 };
  if (startData.value) persistDraft(startData.value.recordId);
}

function handleToggleMulti(optionId: number) {
  const q = question.value;
  if (!q) return;
  const current = answers.value[q.questionId]?.optionIds ?? [];
  const next = current.includes(optionId)
    ? current.filter((id) => id !== optionId)
    : [...current, optionId];
  answers.value[q.questionId] = { optionIds: next, spendSeconds: 0 };
  if (startData.value) persistDraft(startData.value.recordId);
}

function handleFillInput() {
  const q = question.value;
  if (!q) return;
  answers.value[q.questionId] = { optionIds: [], answerText: currentAnswerText.value, spendSeconds: 0 };
  if (startData.value) persistDraft(startData.value.recordId);
}

/* ==================== 导航 ==================== */

function syncFillText() {
  const q = question.value;
  if (!q) return;
  currentAnswerText.value = answers.value[q.questionId]?.answerText ?? "";
}

function goPrev() {
  if (currentIndex.value > 0) {
    currentIndex.value -= 1;
    syncFillText();
  }
}

function goNext() {
  if (!isLastQuestion.value) {
    currentIndex.value += 1;
    syncFillText();
  }
}

function jumpTo(index: number) {
  if (index < 0 || index >= (startData.value?.questions.length ?? 0)) return;
  currentIndex.value = index;
  syncFillText();
}

/* ==================== 加载 ==================== */

async function checkBeforeStart(scaleId: number): Promise<boolean> {
  let precheck: ScalePrecheckVO | null = null;
  try {
    const precheckRes = await getScalePrecheck(scaleId);
    precheck = precheckRes.data?.data ?? null;
  } catch {
    return true;
  }
  if (precheck?.allowed) return true;

  const reason = precheck?.reason ?? "";
  if (reason === "COOLING" || reason === "REPEAT_LIMITED") {
    const minutes = precheck?.coolRemainMinutes ?? 0;
    ElMessage.warning(minutes > 0 ? `该量表正在进行冷却中，剩余约 ${minutes} 分钟` : "该量表正在进行冷却中，请稍后重试");
    return false;
  }
  if (reason === "UNFINISHED_EXISTS") {
    try {
      await ElMessageBox.confirm("存在未完成的测评记录，请先完成或终止后重测", "提示", {
        confirmButtonText: "去测评记录",
        cancelButtonText: "取消",
        type: "warning",
      });
    } catch {
      return false;
    }
    router.push("/scale/records");
    return false;
  }
  ElMessage.warning(reason || "当前不允许开始本量表测评");
  return false;
}

watch(
  () => userStore.token,
  (t) => {
    if (t && !startData.value && !loading.value) void init();
  },
);

async function init() {
  if (!userStore.token) {
    loading.value = false;
    userStore.openLoginDialog("login");
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    if (routeRecordId <= 0) {
      const allowedToStart = await checkBeforeStart(scaleId);
      if (!allowedToStart) return;
    }
    const res = routeRecordId > 0
      ? await resumeAssessment(routeRecordId)
      : await startAssessment({ scaleId });
    startData.value = res.data?.data ?? null;
    if (!startData.value) {
      error.value = "未获取到测评数据，请稍后重试";
      return;
    }
    if (routeToolId > 0 && routeConversationId > 0) {
      try {
        await bindToolRecord({
          toolId: routeToolId,
          conversationId: routeConversationId,
          recordId: startData.value.recordId,
        });
      } catch {
        // 回写失败不阻塞作答
      }
    }
    restoreDraft(startData.value.recordId);
    syncFillText();
    startTimer(startData.value.timeLimit ?? 0, () => void submitAnswers(true));
  } catch (e) {
    error.value = (e as Error)?.message || "测评加载失败，请稍后重试";
  } finally {
    loading.value = false;
  }
}

/* ==================== 提交 ==================== */

function buildSubmitPayload() {
  const questions = startData.value?.questions ?? [];
  const items = questions.map((q) => {
    const entry = answers.value[q.questionId];
    return {
      questionId: q.questionId,
      answerStatus: entry ? 1 : 0,
      spendSeconds: entry?.spendSeconds ?? 0,
      optionIds: entry?.optionIds ?? [],
      answerText: entry?.answerText ?? "",
    };
  });
  return { recordId: startData.value?.recordId ?? 0, answers: items };
}

function validateBeforeSubmit(): boolean {
  const questions = startData.value?.questions ?? [];
  const firstMissing = questions.findIndex((q) => {
    const entry = answers.value[q.questionId];
    const done =
      q.questionType === QUESTION_TYPE.MULTI
        ? Boolean(entry?.optionIds.length)
        : q.questionType === QUESTION_TYPE.FILL
          ? Boolean(entry?.answerText?.trim())
          : Boolean(entry?.optionIds.length);
    return q.required === REQUIRED_YES && !done;
  });
  if (firstMissing >= 0) {
    jumpTo(firstMissing);
    ElMessage.warning(`第 ${firstMissing + 1} 题为必答题，请先完成作答`);
    return false;
  }
  return true;
}

function handleBackToList() {
  router.replace("/scale");
}

async function handleSubmit() {
  if (!validateBeforeSubmit()) return;
  if (submitting.value) return;

  submitting.value = true;
  try {
    const res = await submitAssessment(buildSubmitPayload());
    if (!res.data?.data) throw new Error("提交响应缺少测评结果");
    stopTimer();
    clearDraft();
    ElMessage.success("测评提交成功");
    router.replace(routeConversationId > 0 ? `/conversation` : `/scale/records/${res.data.data.recordId}`);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "提交失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
}

async function submitAnswers(_fromTimeout: boolean) {
  if (submitting.value || !startData.value) return;
  submitting.value = true;
  try {
    const res = await submitAssessment(buildSubmitPayload());
    stopTimer();
    clearDraft();
    router.replace(`/scale/records/${res.data?.data?.recordId ?? startData.value.recordId}`);
  } catch (e) {
    stopTimer();
    ElMessage.error((e as Error)?.message || "自动提交失败，请手动提交或刷新页面重试");
  } finally {
    submitting.value = false;
  }
}

async function handleTerminate() {
  if (submitting.value) return;
  const recordId = startData.value?.recordId ?? 0;
  if (!recordId) return;

  try {
    await ElMessageBox.confirm("确定要结束本次测评吗？已作答的内容将不会保存。", "结束测评", {
      confirmButtonText: "结束测评",
      cancelButtonText: "继续作答",
      type: "warning",
    });
  } catch {
    return;
  }

  try {
    await terminateAssessment(recordId);
    ElMessage.info("测评已结束");
  } catch {
    ElMessage.info("测评已结束");
  } finally {
    stopTimer();
    clearDraft();
    router.replace("/scale/records");
  }
}

/* ==================== 生命周期 ==================== */

watch(currentIndex, syncFillText);
onBeforeRouteLeave(() => stopTimer());
onBeforeUnmount(() => {
  stopTimer();
  clearDraft();
});
onMounted(() => void init());
</script>

<style scoped>
.scale-answer-page {
  max-width: 640px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}

.answer-loading,
.answer-error {
  min-height: 50vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.loading-text {
  color: #999;
  font-size: 13px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #999;
  margin-bottom: 16px;
}

.breadcrumb-link {
  color: #6c63ff;
  text-decoration: none;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

.progress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.progress-label {
  font-size: 13px;
  color: #666;
}

.progress-count {
  font-size: 13px;
  color: #6c63ff;
  font-weight: 600;
}

.progress-bar {
  margin-bottom: 20px;
}

.q-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 18px;
}

.action-btn {
  min-width: 120px;
  border-radius: 10px;
  font-weight: 500;
}

.action-btn.primary {
  background: #6c63ff;
  border-color: #6c63ff;
}

.action-btn.primary:hover {
  background: #5549ef;
  border-color: #5549ef;
}

.quit-row {
  margin-top: 24px;
  text-align: center;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>