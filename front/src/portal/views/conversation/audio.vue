<template>
  <div class="audio-page">
    <header class="ap-header">
      <button class="ap-back" title="返回文本对话" @click="handleBack">
        <el-icon :size="18"><ArrowLeft /></el-icon>
      </button>
      <div class="ap-header-center">
        <div class="ap-title">{{ conversationName || "语音对话" }}</div>
        <div class="ap-sub">{{ statusText }}</div>
      </div>
      <button class="ap-mode" title="修改输入/输出方式" @click="modeDialogVisible = true">
        <el-icon :size="14"><Setting /></el-icon>
        <span>模式</span>
      </button>
      <div class="ap-badge" :class="badgeClass">{{ badgeText }}</div>
    </header>

    <!-- 舞台：上方渐进文本 + 中央语音球 -->
    <div class="ap-stage">
      <!-- 未登录占位 -->
      <div v-if="!userStore.token" class="ap-empty">
        <p>登录后即可开始语音对话</p>
        <el-button type="primary" round @click="userStore.openLoginDialog('login')">去登录</el-button>
      </div>

      <template v-else>
        <!-- 渐进文本区（上方） -->
        <div class="ap-text-zone">
          <p v-if="userText" class="ap-user-text" :class="{ 'is-final': userTextFinal }">{{ userText }}</p>
          <p v-if="aiStream" class="ap-ai-text">{{ aiStream }}<span v-if="phase === 'replying'" class="stream-cursor"></span></p>
          <p v-if="!userText && !aiStream" class="ap-idle-hint">{{ idleHint }}</p>

          <!-- 量表推荐卡片（可操作，不属于历史消息） -->
          <div v-if="displayPendingActions.length" class="ap-cards">
            <PendingActionCard
              v-for="act in displayPendingActions"
              :key="act.id"
              :act="act"
              :busy="pendingActActions[act.id]"
              @start="handleScaleStart"
              @answered="handleScaleAnswered"
            />
          </div>
        </div>

        <!-- 中央：虚拟形象 / 语音球（有启用模型且选中 AVATAR_2D 时展示形象，否则保留语音球） -->
        <div class="ap-orb-zone">
          <Live2dAvatar
            v-if="showAvatar"
            ref="avatarRef"
            :model-url="signedModelUrl"
            :expression-list="live2dConfig?.expressionList ?? []"
            :motion-list="live2dConfig?.motionList ?? []"
            :idle-expression-list="live2dConfig?.idleExpressionList ?? []"
            :idle-motion-list="live2dConfig?.idleMotionList ?? []"
            :idle-interval-seconds="live2dConfig?.idleIntervalSeconds ?? 0"
            :idle-auto-play="live2dConfig?.idleAutoPlay !== 0"
            :volume="volume"
            :speaking="isSpeaking"
            @ready="flushAvatarCommands"
            @load-error="handleAvatarLoadError"
          />
          <div v-else class="ap-orb" :class="orbClass" :style="orbStyle">
            <span class="ap-ring"></span>
            <span class="ap-ring ap-ring-2"></span>
            <div class="ap-orb-core">
              <el-icon :size="30">
                <Microphone v-if="isAudioInput" />
                <ChatDotRound v-else />
              </el-icon>
            </div>
          </div>
          <div class="ap-status-text">{{ statusText }}</div>
          <transition name="fade">
            <div v-if="notice" class="ap-notice">{{ notice }}</div>
          </transition>
        </div>

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
        <!-- 文本输入模式：输入框发送 -->
        <div v-if="hasTextInput" class="ap-text-row">
          <el-input
            v-model="textInput"
            placeholder="输入你想说的话…"
            :disabled="phase === 'initializing'"
            @keyup.enter="handleSendText"
          />
          <el-button
            type="primary"
            :disabled="!textInput.trim() || phase === 'initializing'"
            @click="handleSendText"
          >发送</el-button>
        </div>
        <div v-if="isAudioInput" class="ap-volume">
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

    <!-- 页内修改输入/输出方式 -->
    <VoiceModeDialog
      v-model="modeDialogVisible"
      title="对话模式"
      confirm-text="应用"
      :input-types="currentInputTypes"
      :output-types="currentOutputTypes"
      @confirm="handleModeConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { ArrowLeft, ChatDotRound, Microphone, Setting } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import Live2dAvatar from "@/portal/components/conversation/Live2dAvatar.vue";
import PendingActionCard from "@/portal/components/conversation/PendingActionCard.vue";
import VoiceModeDialog from "@/portal/components/conversation/VoiceModeDialog.vue";
import { useAudioConversation } from "@/portal/composables/useAudioConversation";
import { useScaleCardActions } from "@/portal/composables/useScaleCardActions";
import { useUserStore } from "@/portal/stores/user";
import { useConversationStore } from "@/portal/stores/conversation";
import {
  getEnabledLive2dConfig,
  getSignUrl,
  parseAvatarCommand,
  type Live2dConfigVO,
} from "@/portal/api/live2d/config";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const conversationStore = useConversationStore();

/** 输入/输出模式（来自模式选择弹窗；直接访问时缺省为 语音输入 + 语音&文本输出） */
function parseTypes(raw: unknown, fallback: string[]): string[] {
  const list = String(raw ?? "")
    .split(",")
    .map((s) => s.trim().toUpperCase())
    .filter(Boolean);
  return list.length ? list : fallback;
}
const inputTypes = parseTypes(route.query.inputTypes, ["AUDIO"]);
let outputTypes = parseTypes(route.query.outputTypes, ["TEXT", "AUDIO"]);
// 防御性校验（对话框为第一道闸）：AVATAR_2D 只能作为附加输出，必须与 TEXT/AUDIO 至少一项共选；
// 若 URL 中出现「仅 AVATAR_2D」（如非法直链/篡改），回退默认 TEXT+AUDIO，避免后端无文本输出管道
if (outputTypes.includes("AVATAR_2D") && !outputTypes.includes("TEXT") && !outputTypes.includes("AUDIO")) {
  outputTypes = ["TEXT", "AUDIO"];
}
/** 从文本页进入：进入时释放旧 TEXT 生命周期（仅消费一次，消费后从 URL 移除，刷新不再重复释放） */
const fromText = String(route.query.fromText ?? "") === "1";

const {
  phase,
  errorText,
  notice,
  needResume,
  volume,
  pendingActions,
  userText,
  userTextFinal,
  aiStream,
  conversationId,
  conversationName,
  isAudioInput,
  boot,
  retry,
  teardown,
  disposeOnUnload,
  resumeAudio,
  sendText,
  setModes,
  playerPlaying,
} = useAudioConversation({
  initialConversationId: String(route.query.conversationId ?? ""),
  inputTypes,
  outputTypes,
  releaseOnBoot: fromText,
  onConversationIdChange: (id) => {
    // 新会话在 init 时才拿到真实 ID：回写 URL，刷新/返回不丢上下文
    const { fromText: _ft, ...rest } = route.query;
    void router.replace({ query: { ...rest, conversationId: id } });
  },
  onAvatar2dExpression: (raw) => {
    const id = parseAvatarCommand(raw, "expression");
    if (id) {
      if (avatarRef.value) avatarRef.value.applyExpression(id);
      else pendingAvatarExpressions.push(id);
    }
  },
  onAvatar2dMotion: (raw) => {
    const id = parseAvatarCommand(raw, "motion");
    if (id) {
      if (avatarRef.value) avatarRef.value.applyMotion(id);
      else pendingAvatarMotions.push(id);
    }
  },
  onIdleTimeout: () => {
    ElMessage.warning("会话已超时结束，已返回文本对话");
    void router.push({ name: "PortalConversation" });
  },
});

/* ==================== 页内修改对话模式 ==================== */

/** 当前模式（可页内修改；与 composable 运行态保持同步） */
const currentInputTypes = ref<string[]>([...inputTypes]);
const currentOutputTypes = ref<string[]>([...outputTypes]);
const hasTextInput = computed(() => currentInputTypes.value.includes("TEXT"));

/* ==================== Live2D 虚拟形象 ==================== */

/** 当前输出模式是否包含虚拟形象 */
const hasAvatar = computed(() => currentOutputTypes.value.includes("AVATAR_2D"));
const avatarRef = ref<InstanceType<typeof Live2dAvatar> | null>(null);
/** 启用模型配置（config 为 null 时不展示形象） */
const live2dConfig = ref<Live2dConfigVO | null>(null);
/** 签名下载 URL（300s 过期，过期后重新签发触发组件重载） */
const signedModelUrl = ref("");
/** 形象加载失败/无启用模型：回退语音球（一次性标志，切换模式后重置重试） */
const avatarFail = ref(false);
let avatarLoading = false;
/** 加载失败续签重试次数（签名 300s 过期场景的一次性自愈；超过 1 次仍失败则回落语音球） */
const avatarRetries = ref(0);
const pendingAvatarExpressions: string[] = [];
const pendingAvatarMotions: string[] = [];

/** 拉取启用模型配置并签发签名 URL；失败/无模型统一置 avatarFail 回退语音球 */
async function loadLive2d() {
  if (avatarLoading || avatarFail.value) return;
  avatarLoading = true;
  try {
    const cfg = await getEnabledLive2dConfig();
    live2dConfig.value = cfg;
    if (!cfg) {
      signedModelUrl.value = "";
      avatarFail.value = true;
      if (hasAvatar.value) {
        currentOutputTypes.value = currentOutputTypes.value.filter((type) => type !== "AVATAR_2D");
        await setModes(currentInputTypes.value, currentOutputTypes.value);
      }
      return;
    }
    signedModelUrl.value = await getSignUrl(cfg.modelUrl);
  } catch (e) {
    console.warn("[audio] Live2D 配置加载失败", e);
    avatarFail.value = true;
  } finally {
    avatarLoading = false;
  }
}

watch(
  [hasAvatar, () => userStore.token],
  ([avatar, token]) => {
    if (avatar && token) {
      void loadLive2d();
    } else if (!avatar) {
      // 关闭形象模式：清空签名并重置失败/重试计数，下次开启干净重试
      signedModelUrl.value = "";
      avatarFail.value = false;
      avatarRetries.value = 0;
    }
  },
  { immediate: true },
);

function flushAvatarCommands() {
  const avatar = avatarRef.value;
  if (!avatar) return;
  pendingAvatarExpressions.splice(0).forEach((id) => avatar.applyExpression(id));
  pendingAvatarMotions.splice(0).forEach((id) => avatar.applyMotion(id));
}

/** 是否展示虚拟形象：登录 + 选中 AVATAR_2D + 签名 URL 就绪 + 未失败 */
const showAvatar = computed(
  () => userStore.token && hasAvatar.value && !!signedModelUrl.value && !avatarFail.value,
);
const isSpeaking = computed(() => playerPlaying.value);

/** 模型加载失败：签名 300s 过期场景重签一次自愈，仍失败才回落语音球 */
async function handleAvatarLoadError() {
  if (avatarFail.value) return;
  const cfg = live2dConfig.value;
  if (cfg && avatarRetries.value < 2) {
    avatarRetries.value += 1;
    try {
      const fresh = await getSignUrl(cfg.modelUrl);
      if (fresh) {
        console.warn("[audio] Live2D 模型加载失败，重新签发签名 URL 重试");
        signedModelUrl.value = fresh; // 变化触发组件 reload
        return;
      }
    } catch (e) {
      console.warn("[audio] Live2D 签名续签失败", e);
    }
  }
  console.warn("[audio] Live2D 模型加载失败，回退语音球");
  avatarFail.value = true;
}

/* ==================== 页内修改对话模式 ==================== */

const modeDialogVisible = ref(false);

async function handleModeConfirm(inputs: string[], outputs: string[]) {
  if (outputs.includes("AVATAR_2D") && !live2dConfig.value) {
    outputs = outputs.filter((type) => type !== "AVATAR_2D");
    ElMessage.warning("当前没有启用 Live2D 模型，已切换为普通输出");
  }
  const previousInputs = [...currentInputTypes.value];
  const previousOutputs = [...currentOutputTypes.value];
  try {
    await setModes(inputs, outputs);
    conversationStore.setVoiceModes(inputs, outputs);
    currentInputTypes.value = [...inputs];
    currentOutputTypes.value = [...outputs];
    const { fromText: _ft, ...rest } = route.query;
    await router.replace({
      query: { ...rest, inputTypes: inputs.join(","), outputTypes: outputs.join(",") },
    });
  } catch (e) {
    currentInputTypes.value = previousInputs;
    currentOutputTypes.value = previousOutputs;
    ElMessage.error((e as Error)?.message || "对话模式切换失败");
  }
}

/* ==================== 文本输入 ==================== */

const textInput = ref("");

async function handleSendText() {
  const value = textInput.value.trim();
  if (!value) return;
  textInput.value = "";
  await sendText(value);
}

/** 量表卡片动作（与文本对话页共用同一实现） */
const { pendingActActions, handleScaleStart, handleScaleAnswered } = useScaleCardActions(() => conversationId.value);

const displayPendingActions = computed(() => [...pendingActions.value].sort((a, b) => b.roundNum - a.roundNum));

/* ==================== 状态文案与语音球 ==================== */

const statusText = computed(() => {
  switch (phase.value) {
    case "connecting":
      return "正在连接…";
    case "initializing":
      return "正在准备会话…";
    case "ready":
      return "等待输入";
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
    case "ready":
      return "待输入";
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

/** 徽标/语音球样式类（与 phase 同名） */
const badgeClass = computed(() => phase.value);
const orbClass = computed(() => `phase-${phase.value}`);
/** 语音球随实时音量律动（--ap-level: 0-100） */
const orbStyle = computed(() => ({ "--ap-level": String(volume.value) }));

const idleHint = computed(() => {
  if (phase.value === "error") return "";
  if (phase.value === "connecting" || phase.value === "initializing" || phase.value === "requesting-mic") return "";
  if (isAudioInput.value) return "直接开口说话，我会一直在这里倾听";
  return "在上方输入框写下你的心事";
});

/* ==================== 生命周期 ==================== */

function handleBack() {
  router.push({ name: "PortalConversation" });
}

async function handleRetry() {
  await retry();
}

async function handleResume() {
  await resumeAudio();
}

/* ==================== 页面卸载 / BFCache 恢复兜底 ==================== */

/**
 * pagehide：关闭标签页 / 刷新 / 跨文档离开不缓存 → 轻量释放（keepalive DELETE）。
 * 进入 BFCache（e.persisted=true，跨文档回退被缓存）→ 不释放，等 pageshow 恢复时统一处理。
 * SPA 内导航离开不触发 pagehide（同 document），仍由 onBeforeRouteLeave/onBeforeUnmount 释放。
 */
const handlePageHide = (e: PageTransitionEvent) => {
  if (e.persisted) return;
  disposeOnUnload();
};

/**
 * pageshow：从 BFCache 恢复（跨文档回退再前进回来）时，
 * 回退操作视为语音对话结束，强制回到文本对话页；
 * 组件卸载走 onBeforeRouteLeave/onBeforeUnmount → teardown 释放。
 */
const handlePageShow = (e: PageTransitionEvent) => {
  if (e.persisted) {
    void router.replace({ name: "PortalConversation" });
  }
};

onMounted(async () => {
  window.addEventListener("pagehide", handlePageHide);
  window.addEventListener("pageshow", handlePageShow);
  // 消费 fromText 后立即从 URL 移除：刷新/前进后退不再重复释放旧生命周期
  if (fromText) {
    const { fromText: _ft, ...rest } = route.query;
    await router.replace({ query: rest });
  }
  // 进入页面即启动（连接 → 释放旧资源 → 准备输入），但不 init；首次输入才初始化
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
  window.removeEventListener("pagehide", handlePageHide);
  window.removeEventListener("pageshow", handlePageShow);
  void teardown();
});
</script>

<style scoped>
/* ==================== 页面与背景（暖橙奶油） ==================== */

.audio-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  background:
    radial-gradient(58% 46% at 18% 8%, rgba(255, 214, 184, 0.55), transparent 72%),
    radial-gradient(52% 44% at 86% 18%, rgba(255, 232, 214, 0.75), transparent 72%),
    radial-gradient(70% 50% at 50% 108%, rgba(255, 196, 158, 0.5), transparent 70%),
    linear-gradient(180deg, #fff9f3 0%, #ffebdc 55%, #ffe0cc 100%);
  color: #5c4a3d;
}

/* ==================== 页头 ==================== */

.ap-header {
  height: 64px;
  background: rgba(255, 252, 248, 0.72);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 180, 140, 0.22);
  display: flex;
  align-items: center;
  padding: 0 16px;
  flex-shrink: 0;
  gap: 10px;
  position: relative;
  z-index: 2;
}

.ap-back {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #8a7566;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.ap-back:hover {
  background: rgba(255, 176, 124, 0.16);
  color: #ff8a5c;
}

.ap-mode {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(255, 176, 124, 0.35);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
  color: #8a7566;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ap-mode:hover {
  border-color: #ffb07c;
  color: #ff8a5c;
  background: #fff4ea;
}

.ap-header-center {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.ap-title {
  font-size: 15px;
  font-weight: 600;
  color: #5c4a3d;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ap-sub {
  font-size: 11px;
  color: #9a8578;
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
  color: #ff8a5c;
  background: rgba(255, 176, 124, 0.18);
}

.ap-badge.listening {
  color: #d97a3a;
  background: rgba(255, 196, 130, 0.28);
}

.ap-badge.speech,
.ap-badge.replying {
  color: #c96a2e;
  background: rgba(255, 168, 110, 0.3);
}

.ap-badge.error {
  color: #d0503f;
  background: rgba(255, 130, 110, 0.2);
}

/* ==================== 舞台 ==================== */

.ap-stage {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 18px 26px 8px;
  position: relative;
  z-index: 1;
}

.ap-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #9a8578;
  font-size: 13px;
}

/* ==================== 渐进文本区（上方） ==================== */

.ap-text-zone {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  text-align: center;
  gap: 12px;
  overflow-y: auto;
  padding: 4px 2px 10px;
}

.ap-text-zone::-webkit-scrollbar {
  width: 4px;
}

.ap-text-zone::-webkit-scrollbar-thumb {
  background: rgba(255, 176, 124, 0.35);
  border-radius: 4px;
}

.ap-user-text {
  margin: 0;
  font-size: 19px;
  line-height: 1.7;
  font-weight: 600;
  color: #5c4a3d;
  animation: text-in 0.28s ease both;
}

/* 识别中的中间结果：略淡 + 呼吸感，表示尚未定型 */
.ap-user-text:not(.is-final) {
  color: #9a8578;
  animation: text-in 0.28s ease both, draft-breathe 1.6s ease-in-out infinite;
}

.ap-ai-text {
  margin: 0;
  font-size: 16px;
  line-height: 1.8;
  color: #7a6558;
  white-space: pre-wrap;
  word-break: break-word;
  animation: text-in 0.28s ease both;
}

.ap-idle-hint {
  margin: 0;
  font-size: 13px;
  color: #b8a598;
}

@keyframes text-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes draft-breathe {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.62;
  }
}

/* 流式输出光标 */
.stream-cursor {
  display: inline-block;
  width: 6px;
  height: 15px;
  margin-left: 3px;
  vertical-align: -2px;
  background: #ff8a5c;
  border-radius: 1px;
  animation: cursor-blink 1s steps(2, start) infinite;
}

@keyframes cursor-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

.ap-cards {
  margin-top: 4px;
}

/* ==================== 语音球 ==================== */

.ap-orb-zone {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 6px 0 20px;
}

.ap-orb {
  position: relative;
  width: 186px;
  height: 186px;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 随实时音量缩放（--ap-level: 0-100） */
  transform: scale(calc(1 + var(--ap-level, 0) * 0.0022));
  transition: transform 0.12s ease-out;
}

.ap-orb-core {
  width: 118px;
  height: 118px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffb07c, #ff8a5c);
  box-shadow:
    0 18px 40px rgba(255, 138, 92, 0.32),
    inset 0 0 0 1px rgba(255, 255, 255, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  position: relative;
  z-index: 2;
}

.ap-ring {
  position: absolute;
  inset: 12px;
  border-radius: 50%;
  border: 2px solid rgba(255, 160, 110, 0.45);
  animation: ring-pulse 2.6s ease-out infinite;
}

.ap-ring-2 {
  inset: 0;
  animation-delay: 1.3s;
}

@keyframes ring-pulse {
  0% {
    transform: scale(0.82);
    opacity: 0;
  }
  35% {
    opacity: 0.75;
  }
  100% {
    transform: scale(1.12);
    opacity: 0;
  }
}

/* 各阶段：聆听缓慢呼吸 / 说话活跃 / 回复流光 / 初始化转圈 / 异常降饱和 */
.phase-listening .ap-orb-core {
  animation: core-breathe 3s ease-in-out infinite;
}

.phase-speech .ap-orb-core {
  animation: core-breathe 1.1s ease-in-out infinite;
  box-shadow:
    0 22px 52px rgba(255, 138, 92, 0.45),
    inset 0 0 0 1px rgba(255, 255, 255, 0.4);
}

.phase-speech .ap-ring {
  animation-duration: 1.2s;
}

.phase-replying .ap-orb-core {
  background: linear-gradient(120deg, #ffb07c, #ff9a62, #ffc79a, #ff8a5c);
  background-size: 220% 220%;
  animation: core-flow 2.6s ease-in-out infinite;
}

.phase-initializing .ap-orb-core {
  filter: saturate(0.85);
}

.phase-initializing .ap-ring {
  border-style: dashed;
  animation-duration: 1s;
}

.phase-error .ap-orb-core {
  background: linear-gradient(135deg, #e08b7a, #d0503f);
  animation: none;
}

.phase-error .ap-ring {
  border-color: rgba(208, 80, 63, 0.35);
  animation: none;
  opacity: 0.4;
}

@keyframes core-breathe {
  0%,
  100% {
    box-shadow: 0 18px 40px rgba(255, 138, 92, 0.28), inset 0 0 0 1px rgba(255, 255, 255, 0.35);
  }
  50% {
    box-shadow: 0 22px 54px rgba(255, 138, 92, 0.5), inset 0 0 0 1px rgba(255, 255, 255, 0.5);
  }
}

@keyframes core-flow {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.ap-status-text {
  font-size: 13px;
  color: #9a8578;
}

/* 轻提示（非阻塞） */
.ap-notice {
  max-width: 80vw;
  text-align: center;
  font-size: 12.5px;
  color: #b25c28;
  background: rgba(255, 236, 214, 0.92);
  border: 1px solid rgba(255, 176, 124, 0.45);
  border-radius: 999px;
  padding: 6px 16px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ==================== 错误态 ==================== */

.ap-error {
  margin: 0 auto 10px;
  max-width: 520px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(208, 80, 63, 0.35);
  border-radius: 14px;
  padding: 16px 18px;
  text-align: center;
}

.ap-error-text {
  color: #b4442f;
  font-size: 13px;
  margin: 0 0 12px;
}

.ap-error-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

/* ==================== 底部 ==================== */

.ap-footer {
  flex-shrink: 0;
  background: rgba(255, 252, 248, 0.78);
  backdrop-filter: blur(10px);
  border-top: 1px solid rgba(255, 180, 140, 0.22);
  padding: 14px 28px 16px;
  position: relative;
  z-index: 2;
}

.ap-text-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.ap-volume {
  height: 6px;
  border-radius: 3px;
  background: rgba(255, 214, 184, 0.55);
  overflow: hidden;
}

.ap-volume-fill {
  display: block;
  height: 100%;
  width: 0;
  border-radius: 3px;
  background: #e6c3ac;
  transition: width 0.12s linear, background 0.2s ease;
}

.ap-volume-fill.active {
  background: linear-gradient(90deg, #ffb07c, #ff8a5c);
}

.ap-status {
  margin-top: 10px;
  font-size: 12px;
  color: #9a8578;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.ap-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e6c3ac;
}

.ap-dot.listening {
  background: #ff9a62;
  animation: dot-pulse 1.6s infinite;
}

.ap-dot.speech,
.ap-dot.replying {
  background: #ff8a5c;
  animation: dot-pulse 1.6s infinite;
}

.ap-dot.error {
  background: #d0503f;
}

@keyframes dot-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

/* ==================== 遮罩 ==================== */

.ap-mask {
  position: absolute;
  inset: 0;
  background: rgba(90, 60, 40, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
  cursor: pointer;
}

.ap-mask-card {
  background: #fffaf5;
  border-radius: 16px;
  padding: 24px 28px;
  text-align: center;
  box-shadow: 0 12px 40px rgba(120, 80, 50, 0.22);
}

.ap-mask-card p {
  font-size: 13px;
  color: #7a6558;
  margin: 0 0 14px;
}
</style>
