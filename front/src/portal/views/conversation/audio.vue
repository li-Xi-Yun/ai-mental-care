<template>
  <div class="audio-page" :style="{ '--portal-header-height': PORTAL_HEADER_HEIGHT + 'px' }">
    <header class="ap-header">
      <button class="ap-back" title="返回文本对话" @click="handleBack">
        <el-icon :size="18"><ArrowLeft /></el-icon>
      </button>
      <div class="ap-header-center">
        <div class="ap-title">{{ conversationName || "语音对话" }}</div>
        <div class="ap-sub">{{ statusText }}</div>
      </div>
      <div class="ap-badge" :class="badgeClass">{{ badgeText }}</div>
    </header>

    <div class="ap-body" ref="bodyRef">
      <!-- 未登录占位 -->
      <div v-if="!userStore.token" class="ap-empty">
        <p>登录后即可开始语音对话</p>
        <el-button type="primary" round @click="userStore.openLoginDialog('login')">去登录</el-button>
      </div>

      <template v-else>
        <!-- 历史消息 -->
        <div v-for="msg in displayMessages" :key="msg.id" class="msg" :class="msg.type === 'user' ? 'user' : 'ai'">
          <template v-if="msg.type === 'assistant'">
            <div class="msg-avatar ai">AI</div>
            <div class="msg-content">
              <div class="msg-bubble ai">{{ msg.content }}</div>
              <div class="msg-time">{{ msgTime(msg.createdTime) }}</div>
            </div>
          </template>
          <template v-else>
            <div class="msg-content right">
              <div class="msg-bubble user">{{ msg.content }}</div>
              <div class="msg-time">{{ msgTime(msg.createdTime) }}</div>
            </div>
          </template>
        </div>

        <!-- 已定型的用户话（ASR 结果，等待 memory 对账替换） -->
        <div v-if="asrFinal" class="msg user">
          <div class="msg-content right">
            <div class="msg-bubble user">{{ asrFinal }}</div>
          </div>
        </div>

        <!-- 当前识别中的用户话（临时气泡） -->
        <div v-if="asrDraft" class="msg user">
          <div class="msg-content right">
            <div class="msg-bubble user pending">{{ asrDraft }}</div>
          </div>
        </div>

        <!-- AI 流式回复 -->
        <div v-if="aiStream" class="msg ai">
          <div class="msg-avatar ai">AI</div>
          <div class="msg-content">
            <div class="msg-bubble ai">{{ aiStream }}</div>
          </div>
        </div>

        <!-- AI 思考动画 -->
        <div v-if="phase === 'replying' && !aiStream" class="msg ai">
          <div class="msg-avatar ai">AI</div>
          <div class="typing-indicator">
            <span class="typing-text">AI 正在回应</span>
            <span class="typing-dots"><i></i><i></i><i></i></span>
          </div>
        </div>

        <!-- 量表推荐卡片（与文本页共用组件） -->
        <PendingActionCard
          v-for="act in displayPendingActions"
          :key="act.id"
          :act="act"
          :busy="pendingActActions[act.id]"
          @start="handleScaleStart"
          @answered="handleScaleAnswered"
        />

        <!-- 错误态（权限/设备/连接失败） -->
        <div v-if="phase === 'error'" class="ap-error">
          <p class="ap-error-text">{{ errorText }}</p>
          <div class="ap-error-actions">
            <el-button type="primary" size="small" round @click="handleRetry">重试</el-button>
            <el-button size="small" round @click="handleBack">返回文本对话</el-button>
          </div>
        </div>
      </template>
    </div>

    <footer class="ap-footer">
      <template v-if="userStore.token && phase !== 'error'">
        <div class="ap-volume">
          <span class="ap-volume-fill" :class="{ active: volume > 4 }" :style="{ width: `${volume}%` }"></span>
        </div>
        <div class="ap-status">
          <span class="ap-dot" :class="badgeClass"></span>
          {{ statusText }}
        </div>
      </template>
    </footer>

    <!-- AudioContext 挂起兜底（浏览器自动播放策略；点击一次后继续，无需重新进入） -->
    <div v-if="needResume" class="ap-mask" @click="handleResume">
      <div class="ap-mask-card">
        <p>浏览器需要一次点击才能启用语音播放</p>
        <el-button type="primary" round>点击继续</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { ArrowLeft } from "@element-plus/icons-vue";
import { PORTAL_HEADER_HEIGHT } from "@/shared/api/config";
import PendingActionCard from "@/portal/components/conversation/PendingActionCard.vue";
import { useAudioConversation } from "@/portal/composables/useAudioConversation";
import { useScaleCardActions } from "@/portal/composables/useScaleCardActions";
import { useUserStore } from "@/portal/stores/user";
import dayjs from "@/shared/utils/dayjs";
import type { MemoryMessage } from "@/portal/api/conversation/types";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const {
  phase,
  errorText,
  needResume,
  volume,
  messages,
  pendingActions,
  asrDraft,
  asrFinal,
  aiStream,
  conversationId,
  conversationName,
  boot,
  retry,
  teardown,
  resumeAudio,
} = useAudioConversation({
  initialConversationId: String(route.query.conversationId ?? ""),
  lifecycleBound: String(route.query.lifecycleBound ?? "") === "1",
  onConversationIdChange: (id) => {
    // 新会话在 init 时才拿到真实 ID：回写 URL，刷新/返回不丢上下文（store 未持久化）
    void router.replace({ query: { ...route.query, conversationId: id } });
  },
});

/** 量表卡片动作（与文本对话页共用同一实现） */
const { pendingActActions, handleScaleStart, handleScaleAnswered } = useScaleCardActions(() => conversationId.value);

const bodyRef = ref<HTMLElement | null>(null);

const displayMessages = computed(() =>
  [...messages.value].filter((m) => m.type === "user" || m.type === "assistant").sort(cmpByTime),
);

const displayPendingActions = computed(() => [...pendingActions.value].sort((a, b) => b.roundNum - a.roundNum));

const statusText = computed(() => {
  switch (phase.value) {
    case "connecting":
      return "正在连接…";
    case "initializing":
      return "正在准备会话…";
    case "requesting-mic":
      return "正在开启麦克风…";
    case "listening":
      return "正在聆听，请直接说话";
    case "speech":
      return "识别中…";
    case "replying":
      return "AI 回复中…";
    case "error":
      return errorText.value || "出错了";
    default:
      return "未登录";
  }
});

const badgeText = computed(() => {
  switch (phase.value) {
    case "listening":
      return "聆听中";
    case "speech":
      return "识别中";
    case "replying":
      return "回复中";
    case "error":
      return "异常";
    case "idle":
      return "未开始";
    default:
      return "连接中";
  }
});

/** 徽标/状态点样式类（与 phase 同名） */
const badgeClass = computed(() => phase.value);

function cmpByTime(a: MemoryMessage, b: MemoryMessage): number {
  const ta = dayjs(a.createdTime);
  const tb = dayjs(b.createdTime);
  return (ta.isValid() ? ta.valueOf() : 0) - (tb.isValid() ? tb.valueOf() : 0);
}

function msgTime(v?: string): string {
  if (!v) return "";
  const d = dayjs(v);
  if (!d.isValid()) return String(v);
  const now = dayjs();
  if (d.isSame(now, "day")) return d.format("HH:mm");
  if (d.isSame(now.subtract(1, "day"), "day")) return `昨天 ${d.format("HH:mm")}`;
  return d.format("M月D日 HH:mm");
}

// 消息/识别文本变化后自动滚到底部
watch([displayMessages, asrDraft, asrFinal, aiStream], () => {
  nextTick(() => {
    const el = bodyRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
});

function handleBack() {
  router.push({ name: "PortalConversation" });
}

async function handleRetry() {
  await retry();
}

async function handleResume() {
  await resumeAudio();
}

onMounted(() => {
  // 进入页面即启动（连接 → 初始化 → 持续收音），无"开始语音输入"按钮
  if (userStore.token) void boot();
});

watch(
  () => userStore.token,
  (token, prev) => {
    if (token && !prev) void boot();
    else if (!token && prev) void teardown();
  },
);

// 离开页面：完整释放（补发尾帧停采集 → 停播放 → 释放 lifecycle → 断 WS）
onBeforeRouteLeave(() => teardown());

onBeforeUnmount(() => {
  void teardown();
});
</script>

<style scoped>
.audio-page {
  height: calc(100vh - var(--portal-header-height));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f8f9fc;
  position: relative;
}

/* ==================== 页头 ==================== */

.ap-header {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e5e7f0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  flex-shrink: 0;
  gap: 10px;
}

.ap-back {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #5a5a72;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.ap-back:hover {
  background: #f0eeff;
  color: #6c63ff;
}

.ap-header-center {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.ap-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e1e2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ap-sub {
  font-size: 11px;
  color: #9a9ab0;
  margin-top: 2px;
}

.ap-badge {
  flex-shrink: 0;
  min-width: 52px;
  text-align: center;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 999px;
  color: #6c63ff;
  background: #f0eeff;
}

.ap-badge.listening {
  color: #52c41a;
  background: #f0fdf4;
}

.ap-badge.speech,
.ap-badge.replying {
  color: #fa8c16;
  background: #fff7e6;
}

.ap-badge.error {
  color: #ff4d4f;
  background: #fef2f2;
}

/* ==================== 消息区 ==================== */

.ap-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  scroll-behavior: smooth;
}

.ap-body::-webkit-scrollbar {
  width: 5px;
}

.ap-body::-webkit-scrollbar-thumb {
  background: #d8dae6;
  border-radius: 5px;
}

.ap-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #9a9ab0;
  font-size: 13px;
}

.msg {
  display: flex;
  margin-bottom: 20px;
  animation: msgIn 0.3s ease;
}

@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.msg.user {
  justify-content: flex-end;
}

.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.msg-avatar.ai {
  background: linear-gradient(135deg, #818cf8, #6c63ff);
  color: #fff;
  margin-right: 10px;
  box-shadow: 0 2px 8px rgba(108, 99, 255, 0.25);
}

.msg-content {
  max-width: 68%;
  min-width: 0;
}

.msg-content.right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-bubble {
  display: inline-block;
  padding: 12px 18px;
  font-size: 14px;
  line-height: 1.72;
  word-break: break-word;
  text-align: left;
  white-space: pre-wrap;
}

.msg-bubble.ai {
  background: #fff;
  border: 1px solid #e5e7f0;
  border-radius: 18px 18px 18px 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.06);
  color: #1e1e2e;
}

.msg-bubble.user {
  background: linear-gradient(135deg, #6c63ff, #4338ca);
  border-radius: 18px 18px 6px 18px;
  box-shadow: 0 4px 14px rgba(108, 99, 255, 0.22);
  color: #fff;
}

/* 识别中的临时气泡：半透明表示尚未定型 */
.msg-bubble.user.pending {
  opacity: 0.62;
}

.msg-time {
  font-size: 11px;
  color: #9a9ab0;
  margin-top: 5px;
  padding: 0 6px;
}

.msg-content.right .msg-time {
  text-align: right;
}

/* 思考动画 */

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border: 1px solid #e5e7f0;
  border-radius: 18px 18px 18px 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding: 12px 18px;
  animation: msgIn 0.3s ease;
}

.typing-text {
  font-size: 13px;
  color: #6c63ff;
  font-weight: 500;
}

.typing-dots {
  display: flex;
  gap: 4px;
}

.typing-dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6c63ff;
  animation: dotBounce 1.4s infinite ease-in-out both;
}

.typing-dots i:nth-child(2) {
  animation-delay: 0.16s;
}

.typing-dots i:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes dotBounce {
  0%,
  80%,
  100% {
    transform: scale(0.55);
    opacity: 0.35;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 错误态 */

.ap-error {
  background: #fff;
  border: 1px solid #fecaca;
  border-radius: 14px;
  padding: 18px;
  text-align: center;
  margin: 12px 0;
}

.ap-error-text {
  color: #dc2626;
  font-size: 13px;
  margin: 0 0 12px;
}

.ap-error-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

/* ==================== 底部状态 ==================== */

.ap-footer {
  flex-shrink: 0;
  background: #fff;
  border-top: 1px solid #e5e7f0;
  padding: 14px 28px 16px;
}

.ap-volume {
  height: 6px;
  border-radius: 3px;
  background: #eef0f6;
  overflow: hidden;
}

.ap-volume-fill {
  display: block;
  height: 100%;
  width: 0;
  border-radius: 3px;
  background: #c4c6d4;
  transition: width 0.12s linear, background 0.2s ease;
}

.ap-volume-fill.active {
  background: linear-gradient(90deg, #6c63ff, #818cf8);
}

.ap-status {
  margin-top: 10px;
  font-size: 12px;
  color: #9a9ab0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.ap-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c4c6d4;
}

.ap-dot.listening {
  background: #52c41a;
  animation: dotPulse 1.6s infinite;
}

.ap-dot.speech,
.ap-dot.replying {
  background: #fa8c16;
  animation: dotPulse 1.6s infinite;
}

.ap-dot.error {
  background: #ff4d4f;
}

@keyframes dotPulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

/* AudioContext 挂起兜底遮罩 */

.ap-mask {
  position: absolute;
  inset: 0;
  background: rgba(20, 20, 40, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
  cursor: pointer;
}

.ap-mask-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px 28px;
  text-align: center;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}

.ap-mask-card p {
  font-size: 13px;
  color: #5a5a72;
  margin: 0 0 14px;
}
</style>
