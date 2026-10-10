<template>
  <div class="scale-answer-page">
    <!-- 加载态 -->
    <div v-if="loading" class="answer-loading">
      <el-icon class="is-loading" :size="40" color="#6c63ff">
        <Loading />
      </el-icon>
      <p class="loading-text">正在准备测评…</p>
    </div>

    <!-- 游客态：未登录不可作答（登录后自动加载） -->
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
      <!-- 面包屑 -->
      <div class="breadcrumb">
        <router-link to="/scale" class="breadcrumb-link">量表测评</router-link>
        <span class="breadcrumb-sep">›</span>
        <span class="breadcrumb-current">{{ startData.scaleName }}</span>
      </div>

      <!-- 顶部信息条：量表名 + 题目总数 + 预计用时 + 计时器 -->
      <div class="answer-header">
        <div class="header-info">
          <h2 class="scale-title">{{ startData.scaleName }}</h2>
          <div class="scale-meta">
            <span class="meta-item">共 {{ startData.totalQuestionCount }} 题</span>
            <span class="meta-divider">·</span>
            <span class="meta-item">预计 {{ estimatedMinutes }} 分钟</span>
            <template v-if="startData.versionNo">
              <span class="meta-divider">·</span>
              <span class="meta-item">版本 {{ startData.versionNo }}</span>
            </template>
          </div>
          <p v-if="startData.description" class="scale-desc">{{ startData.description }}</p>
        </div>

        <!-- 计时器 -->
        <div v-if="timeLimit > 0" class="timer-box" :class="{ 'is-warning': timeWarning }">
          <span class="timer-label">剩余时间</span>
          <span class="timer-value">{{ formattedTime }}</span>
        </div>
      </div>

      <!-- 作答进度条 -->
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

      <!-- 题目卡片（单题展示） -->
      <transition name="slide-fade" mode="out-in">
        <div :key="currentIndex" class="question-card">
          <div class="q-header">
            <span class="q-number">
              第 <b>{{ currentIndex + 1 }}</b> / {{ startData.totalQuestionCount }} 题
            </span>
            <span class="q-required" :class="{ unanswered: isCurrentRequired && !currentAnswerReady }">
              {{ isCurrentRequired ? (currentAnswerReady ? "必答题 · 已作答" : "必答题 · 未作答") : "选答题" }}
            </span>
          </div>

          <h3 class="q-text">{{ question.title }}</h3>

          <!-- 单选 -->
          <div v-if="question.questionType === 1" class="option-list">
            <div
              v-for="option in question.options"
              :key="option.optionId"
              class="option-item"
              :class="{ selected: currentSelectedIds.includes(option.optionId) }"
              @click="handleSelectSingle(option.optionId)"
            >
              <span class="option-radio" :class="{ checked: currentSelectedIds.includes(option.optionId) }"></span>
              <span class="option-text">{{ option.optionText }}</span>
            </div>
          </div>

          <!-- 多选 -->
          <div v-else-if="question.questionType === 2" class="option-list">
            <div
              v-for="option in question.options"
              :key="option.optionId"
              class="option-item multi"
              :class="{ selected: currentSelectedIds.includes(option.optionId) }"
              @click="handleToggleMulti(option.optionId)"
            >
              <span class="option-checkbox" :class="{ checked: currentSelectedIds.includes(option.optionId) }">
                <el-icon v-if="currentSelectedIds.includes(option.optionId)" :size="12"><Check /></el-icon>
              </span>
              <span class="option-text">{{ option.optionText }}</span>
            </div>
          </div>

          <!-- 填空 -->
          <div v-else class="fill-input">
            <el-input
              v-model="currentAnswerText"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              resize="none"
              placeholder="请输入您的回答…"
              @input="handleFillInput"
            />
          </div>

          <!-- 版权说明 -->
          <p v-if="startData.copyrightInfo" class="copyright">{{ startData.copyrightInfo }}</p>
        </div>
      </transition>

      <!-- 操作按钮 -->
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

      <!-- 底部答题导航网格 -->
      <div class="nav-grid">
        <div class="nav-grid-title">
          <span>答题导航</span>
          <span class="nav-grid-hint">{{ answeredCount }} / {{ startData.totalQuestionCount }} 已答</span>
        </div>
        <div class="nav-grid-body">
          <button
            v-for="(q, index) in startData.questions"
            :key="q.questionId"
            class="nav-cell"
            :class="navCellClass(q, index)"
            :title="q.title"
            @click="jumpTo(index)"
          >{{ index + 1 }}</button>
        </div>
      </div>

      <!-- 结束测评：中途退出 -->
      <div class="quit-row">
        <el-button text type="danger" :disabled="submitting" @click="handleTerminate">结束测评，不保存本次作答</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { useRoute, useRouter, onBeforeRouteLeave } from "vue-router";
import { Check, Loading } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { startAssessment, resumeAssessment, submitAssessment, terminateAssessment, bindToolRecord } from "@/portal/api/scale/user-scale";
import type { ScaleStartData, ScaleStartQuestion } from "@/portal/api/scale/user-scale";
import { getScalePrecheck } from "@/portal/api/scale/precheck";
import type { ScalePrecheckVO } from "@/portal/api/scale/precheck";
import { useUserStore } from "@/portal/stores/user";
import { getSession, setSession, removeSession } from "@/shared/utils/storage";

/* ==================== 常量 ==================== */
const ANSWER_STORAGE_KEY = "scale_answer_draft_v1";

/** 题目类型 */
const QUESTION_TYPE = {
  SINGLE: 1,
  MULTI: 2,
  FILL: 3,
} as const;

/** 是否必答：0-否 1-是 */
const REQUIRED_YES = 1;

/* ==================== 路由 ==================== */
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const scaleId = Number(route.params.scaleId as string);
/** 路由携带的测评记录ID（续答时存在）。续答场景复用原记录，不新建。 */
const routeRecordId = Number(route.query.recordId as string) || 0;
/** 对话卡片场景：工具ID（conversation_pending_action.id）与会话ID */
const routeToolId = Number(route.query.toolId as string) || 0;
const routeConversationId = Number(route.query.conversationId as string) || 0;

/* ==================== 基础状态 ==================== */
const loading = ref(true);
const error = ref("");
const submitting = ref(false);

/** 开始测评返回的整卷数据 */
const startData = ref<ScaleStartData | null>(null);
/** 当前展示题目下标（0 起） */
const currentIndex = ref(0);

/** 作答保存结构：questionId -> optionIds / answerText */
interface AnswerEntry {
  optionIds: number[];
  answerText?: string;
  /** 本题耗时（秒），用于提交时回传 */
  spendSeconds?: number;
}
const answers = ref<Record<number, AnswerEntry>>({});

/* ==================== 持久化（sessionStorage 防刷新丢失） ==================== */

/** 从 sessionStorage 恢复作答状态 */
function restoreDraft() {
  try {
    const draft = getSession<{
      scaleId: number;
      recordId: number;
      currentIndex: number;
      answers: Record<number, AnswerEntry>;
    }>(ANSWER_STORAGE_KEY);
    if (!draft || draft.scaleId !== scaleId || draft.recordId !== startData.value?.recordId) {
      return;
    }
    currentIndex.value = draft.currentIndex ?? 0;
    answers.value = draft.answers ?? {};
  } catch {
    // 忽略损坏的草稿
  }
}

/** 将当前作答状态写入 sessionStorage */
function persistDraft() {
  if (!startData.value) return;
  setSession(ANSWER_STORAGE_KEY, {
    scaleId,
    recordId: startData.value.recordId,
    currentIndex: currentIndex.value,
    answers: answers.value,
  });
}

/** 作答内容变化时同步草稿 */
watch(answers, persistDraft, { deep: true });
watch(currentIndex, persistDraft);

/* ==================== 当前题目相关 ==================== */

const question = computed<ScaleStartQuestion>(() => {
  const q = startData.value?.questions[currentIndex.value];
  return q ?? (startData.value?.questions[0] as ScaleStartQuestion);
});

const isLastQuestion = computed(() => currentIndex.value >= (startData.value?.questions.length ?? 1) - 1);

const isCurrentRequired = computed(() => question.value?.required === REQUIRED_YES);

/** 当前题已选选项 ID 列表（复用 saveAnswer 前的即时视图） */
const currentSelectedIds = computed<number[]>(() => answers.value[question.value?.questionId]?.optionIds ?? []);

const currentAnswerText = ref("");

/** 当前题是否为有效已作答状态 */
const currentAnswerReady = computed(() => {
  const entry = answers.value[question.value?.questionId];
  if (!entry) return false;
  if (question.value?.questionType === QUESTION_TYPE.MULTI) return entry.optionIds.length > 0;
  if (question.value?.questionType === QUESTION_TYPE.FILL) return Boolean(entry.answerText?.trim());
  return entry.optionIds.length > 0;
});

/* ==================== 进度 ==================== */

/** 已作答题数（含未答但非必答的题不算） */
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

/** 预计用时：按每题 20 秒估算（可用于顶部 meta 展示） */
const estimatedMinutes = computed(() => {
  const count = startData.value?.totalQuestionCount ?? 0;
  if (count <= 0) return 0;
  return Math.max(1, Math.round((count * 20) / 60));
});

/** 提交按钮可用性：最后一道必答题已答即可提交 */
const canSubmit = computed(() => {
  const q = question.value;
  if (!q) return false;
  if (q.required === REQUIRED_YES) return currentAnswerReady.value;
  return true;
});

/* ==================== 计时器 ==================== */

const timeLimit = computed(() => startData.value?.timeLimit ?? 0);
const remainingSeconds = ref(0);
const timerId = ref<number | null>(null);
const timeWarning = ref(false);

const formattedTime = computed(() => {
  const total = Math.max(0, remainingSeconds.value);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(s).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
});

/** 启动倒计时 */
function startTimer() {
  if (timeLimit.value <= 0) return;
  const limit = timeLimit.value;
  remainingSeconds.value = limit;
  // 每秒递减，最后 60 秒变红提醒
  timerId.value = window.setInterval(() => {
    remainingSeconds.value -= 1;
    timeWarning.value = remainingSeconds.value <= 60;
    if (remainingSeconds.value <= 0) {
      if (timerId.value !== null) window.clearInterval(timerId.value);
      timerId.value = null;
      ElMessage.warning("作答时间已到，正在自动提交…");
      void submitAnswers(true);
    }
  }, 1000);
}

function stopTimer() {
  if (timerId.value !== null) window.clearInterval(timerId.value);
  timerId.value = null;
}

/* ==================== 作答交互 ==================== */

/** 单选：点选即替换 */
function handleSelectSingle(optionId: number) {
  const q = question.value;
  if (!q) return;
  answers.value[q.questionId] = {
    optionIds: [optionId],
    spendSeconds: 0,
  };
  persistDraft();
}

/** 多选：点击切换选中 */
function handleToggleMulti(optionId: number) {
  const q = question.value;
  if (!q) return;
  const current = answers.value[q.questionId]?.optionIds ?? [];
  const next = current.includes(optionId)
    ? current.filter((id) => id !== optionId)
    : [...current, optionId];
  answers.value[q.questionId] = { optionIds: next, spendSeconds: 0 };
  persistDraft();
}

/** 填空：实时同步文本 */
function handleFillInput() {
  const q = question.value;
  if (!q) return;
  answers.value[q.questionId] = {
    optionIds: [],
    answerText: currentAnswerText.value,
    spendSeconds: 0,
  };
}

/* ==================== 导航 ==================== */

/** 切换题目时回填填空文本 */
function syncFillText() {
  const q = question.value;
  if (!q) return;
  currentAnswerText.value = answers.value[q.questionId]?.answerText ?? "";
}

/** 上一题 */
function goPrev() {
  if (currentIndex.value > 0) {
    currentIndex.value -= 1;
    syncFillText();
  }
}

/** 下一题（必答题未答时禁用按钮，由 UI 保证） */
function goNext() {
  if (!isLastQuestion.value) {
    currentIndex.value += 1;
    syncFillText();
  }
}

/** 点击导航网格跳转 */
function jumpTo(index: number) {
  if (index < 0 || index >= (startData.value?.questions.length ?? 0)) return;
  currentIndex.value = index;
  syncFillText();
}

/** 导航单元格样式：未答 / 已答 / 必答未答 / 当前 */
function navCellClass(q: ScaleStartQuestion, index: number): Record<string, boolean> {
  const entry = answers.value[q.questionId];
  const done =
    q.questionType === QUESTION_TYPE.MULTI
      ? Boolean(entry?.optionIds.length)
      : q.questionType === QUESTION_TYPE.FILL
        ? Boolean(entry?.answerText?.trim())
        : Boolean(entry?.optionIds.length);
  return {
    done,
    "required-missing": !done && q.required === REQUIRED_YES,
    active: index === currentIndex.value,
  };
}

/* ==================== 加载 ==================== */

/**
 * 作答前检查（非续答场景）：
 * 冷却 / 重复限次 / 已存在未完成记录 时阻止进入作答。
 * 返回 true 表示放行继续，false 表示已提示并阻止。
 */
async function checkBeforeStart(scaleId: number): Promise<boolean> {
  let precheck: ScalePrecheckVO | null = null;
  try {
    const precheckRes = await getScalePrecheck(scaleId);
    precheck = precheckRes.data?.data ?? null;
  } catch (e) {
    // precheck 接口失败不阻塞作答，降级放行
    console.warn("[scale-answer] precheck 失败，跳过作答前检查：", e);
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
      await ElMessageBox.confirm(
        "存在未完成的测评记录，请先完成或终止后重测",
        "提示",
        {
          confirmButtonText: "去测评记录",
          cancelButtonText: "取消",
          type: "warning",
        },
      );
    } catch {
      return false; // 用户取消
    }
    router.push("/scale/records");
    return false;
  }
  ElMessage.warning(reason || "当前不允许开始本量表测评");
  return false;
}

// 游客态经弹窗登录成功后自动重新拉取测评，避免停留在游客空态需手动刷新
watch(
  () => userStore.token,
  (t) => {
    if (t && !startData.value && !loading.value) void init();
  },
);

/** 开始测评 / 续答：拉取整卷 */
async function init() {
  // 游客不能作答（题目内容只对登录用户下发）：未登录时在当前页面弹登录窗，不发请求、不跳转
  if (!userStore.token) {
    loading.value = false;
    userStore.openLoginDialog("login");
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    // 续答场景：携带 recordId 时复用原未完成记录，不新建
    if (routeRecordId <= 0) {
      // 非续答场景：先做作答前检查（冷却/未完成记录等），不通过则不进入作答
      const allowedToStart = await checkBeforeStart(scaleId);
      if (!allowedToStart) {
        return;
      }
    }
    const res = routeRecordId > 0
      ? await resumeAssessment(routeRecordId)
      : await startAssessment({ scaleId });
    startData.value = res.data?.data ?? null;
    if (!startData.value) {
      error.value = "未获取到测评数据，请稍后重试";
      return;
    }
    // 对话卡片场景：进入答题页拿到 recordId 后立即回写，保证刷新后可恢复作答上下文
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
    restoreDraft();
    // 填充当前题填空文本（restoreDraft 后 currentIndex 已恢复）
    syncFillText();
    startTimer();
  } catch (e) {
    error.value = (e as Error)?.message || "测评加载失败，请稍后重试";
  } finally {
    loading.value = false;
  }
}

/* ==================== 提交 ==================== */

/** 组装提交载荷：每道必答题都要有 answerStatus=1 的项 */
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

/** 提交校验：必答题未答则高亮首个未答题并提示 */
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

/** 返回量表列表 */
function handleBackToList() {
  router.replace("/scale");
}

/** 提交测评 */
async function handleSubmit() {
  if (!validateBeforeSubmit()) return;
  if (submitting.value) return;

  submitting.value = true;
  try {
    const res = await submitAssessment(buildSubmitPayload());
    if (!res.data?.data) {
      throw new Error("提交响应缺少测评结果");
    }
    stopTimer();
    removeSession(ANSWER_STORAGE_KEY);
    ElMessage.success("测评提交成功");
    // 对话卡片场景：评测已提交，需回到对话页由用户点击"已完成"触发 AI 分析解读
    router.replace(routeConversationId > 0 ? `/conversation` : `/scale/records/${res.data.data.recordId}`);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "提交失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
}

/** 超时自动提交 */
async function submitAnswers(_fromTimeout: boolean) {
  if (submitting.value || !startData.value) return;
  submitting.value = true;
  try {
    const res = await submitAssessment(buildSubmitPayload());
    stopTimer();
    removeSession(ANSWER_STORAGE_KEY);
    router.replace(`/scale/records/${res.data?.data?.recordId ?? startData.value.recordId}`);
  } catch (e) {
    stopTimer();
    // 超时提交失败：保留草稿并提示
    ElMessage.error((e as Error)?.message || "自动提交失败，请手动提交或刷新页面重试");
  } finally {
    submitting.value = false;
  }
}

/** 中途退出：终止测评 */
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
    return; // 用户取消
  }

  try {
    await terminateAssessment(recordId);
    ElMessage.info("测评已结束");
  } catch {
    // 终止失败不阻塞跳转
    ElMessage.info("测评已结束");
  } finally {
    stopTimer();
    removeSession(ANSWER_STORAGE_KEY);
    router.replace("/scale/records");
  }
}

/* ==================== 生命周期 ==================== */

/** 切换题目时同步填空文本 */
watch(currentIndex, syncFillText);

/** 离开页面时清理计时器与草稿（含中途退出、提交、关闭标签） */
onBeforeRouteLeave(() => {
  stopTimer();
  // 保留草稿以便「继续作答」场景；提交/终止路径已主动清理
});

onBeforeUnmount(() => {
  stopTimer();
  removeSession(ANSWER_STORAGE_KEY);
});

onMounted(() => {
  void init();
});
</script>

<style scoped>
.scale-answer-page {
  max-width: 640px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}

/* ==================== 加载 / 错误 ==================== */
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

/* ==================== 面包屑 ==================== */
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

/* ==================== 顶部信息条 ==================== */
.answer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.header-info {
  min-width: 0;
}

.scale-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.3;
  margin-bottom: 6px;
}

.scale-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #888;
}

.meta-item {
  white-space: nowrap;
}

.meta-divider {
  color: #d0d0d0;
}

.scale-desc {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  margin-top: 6px;
}

/* 计时器 */
.timer-box {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  background: #f0eeff;
  border: 1px solid #d9d2ff;
  border-radius: 12px;
  padding: 10px 18px;
  min-width: 110px;
}

.timer-box.is-warning {
  background: #fff1f0;
  border-color: #ffa39e;
}

.timer-label {
  font-size: 11px;
  color: #6c63ff;
}

.timer-box.is-warning .timer-label {
  color: #ff4d4f;
}

.timer-value {
  font-size: 20px;
  font-weight: 700;
  color: #3f3d9e;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5px;
}

.timer-box.is-warning .timer-value {
  color: #ff4d4f;
}

/* ==================== 进度条 ==================== */
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

/* ==================== 题目卡片 ==================== */
.question-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px 24px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.q-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.q-number {
  font-size: 13px;
  color: #999;
}

.q-number b {
  color: #6c63ff;
  font-size: 16px;
}

.q-required {
  font-size: 12px;
  color: #52c41a;
  padding: 2px 10px;
  border-radius: 12px;
  background: #f6ffed;
}

.q-required.unanswered {
  color: #ff4d4f;
  background: #fff1f0;
}

.q-text {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  line-height: 1.7;
  margin: 0 0 20px;
}

/* 选项 */
.option-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  border: 1.5px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, transform 0.15s;
}

.option-item:hover {
  border-color: #6c63ff;
  background: #f8f7ff;
}

.option-item.selected {
  border-color: #6c63ff;
  background: #f0eeff;
}

.option-radio {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid #d0d0d0;
  flex-shrink: 0;
  transition: all 0.2s;
}

.option-radio.checked {
  border-color: #6c63ff;
  background: #6c63ff;
  box-shadow: inset 0 0 0 3px #fff;
}

.option-checkbox {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 2px solid #d0d0d0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: all 0.2s;
}

.option-checkbox.checked {
  background: #6c63ff;
  border-color: #6c63ff;
}

.option-text {
  font-size: 14px;
  color: #333;
  line-height: 1.5;
  flex: 1;
}

/* 填空 */
.fill-input {
  margin-bottom: 4px;
}

/* 版权说明 */
.copyright {
  margin-top: 16px;
  font-size: 11px;
  color: #bbb;
  text-align: center;
}

/* ==================== 操作按钮 ==================== */
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

/* ==================== 底部导航网格 ==================== */
.nav-grid {
  margin-top: 26px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 16px 18px;
}

.nav-grid-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.nav-grid-hint {
  font-size: 12px;
  font-weight: 400;
  color: #999;
}

.nav-grid-body {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(36px, 1fr));
  gap: 8px;
}

.nav-cell {
  height: 36px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  background: #fff;
  font-size: 13px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  transition: all 0.15s;
  font-variant-numeric: tabular-nums;
}

.nav-cell:hover {
  border-color: #6c63ff;
  color: #6c63ff;
}

.nav-cell.done {
  border-color: #6c63ff;
  background: #f0eeff;
  color: #6c63ff;
}

.nav-cell.required-missing {
  border-color: #ff4d4f;
  color: #ff4d4f;
  background: #fff1f0;
}

.nav-cell.active {
  background: #6c63ff;
  border-color: #6c63ff;
  color: #fff;
}

/* ==================== 退出 ==================== */
.quit-row {
  margin-top: 18px;
  text-align: center;
}

/* ==================== 题目切换动画 ==================== */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(16px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-16px);
}
</style>
