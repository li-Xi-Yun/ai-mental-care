<template>
  <main class="chat-panel">
    <header class="ca-header">
      <div class="panel-toggle left" :class="{ hidden: showConversationList }" title="收起/展开会话列表" @click="emit('toggleList')">
        <el-icon :size="16"><Operation /></el-icon>
      </div>
      <div class="ca-header-center">
        <div class="ca-title">{{ currentConversation?.name || "AI 对话" }}</div>
        <div class="ca-disclaimer">AI 生成内容仅供参考，请勿过度依赖</div>
      </div>
      <div class="panel-toggle right" :class="{ hidden: showEmotionPanel }" title="收起/展开情绪分析" @click="emit('toggleEmotion')">
        <el-icon :size="16"><DataAnalysis /></el-icon>
      </div>
    </header>

    <div class="ca-body" ref="chatBodyRef">
      <!-- 空态问候 -->
      <div v-if="!loading && !displayMessages.length" class="chat-greeting">
        <div class="greeting-logo">
          <el-icon :size="26"><ChatDotRound /></el-icon>
        </div>
        <h3 class="greeting-title">{{ currentConversation?.draft ? "开始新的对话" : currentConversation?.name || "AI 心理助手" }}</h3>
        <p class="greeting-text">我是你的 AI 心理健康助手。你可以向我倾诉最近的烦恼、压力或任何心事，我会一直在这里倾听。</p>
        <p class="greeting-tip">AI 生成内容仅供参考，请勿过度依赖。</p>
        <el-button type="primary" round @click="emit('focusInput')">开始倾诉</el-button>
      </div>

      <!-- 加载骨架 -->
      <div v-if="loading" class="chat-skeleton">
        <div class="sk-row ai">
          <div class="sk-avatar"></div>
          <div class="sk-line" style="width: 46%"></div>
        </div>
        <div class="sk-row user">
          <div class="sk-line" style="width: 38%"></div>
        </div>
        <div class="sk-row ai">
          <div class="sk-avatar"></div>
          <div class="sk-line" style="width: 62%"></div>
        </div>
      </div>

      <!-- 消息气泡 -->
      <div
        v-for="msg in displayMessages"
        :key="msg.id"
        class="msg"
        :class="msg.type === 'user' ? 'user' : 'ai'"
      >
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

      <!-- AI 流式回复（WebSocket 增量） -->
      <div v-if="streamText" class="msg ai">
        <div class="msg-avatar ai">AI</div>
        <div class="msg-content">
          <div class="msg-bubble ai">{{ streamText }}<span class="stream-cursor"></span></div>
        </div>
      </div>

      <!-- 思考动画（尚未收到流式首包时展示） -->
      <div v-else-if="thinking" class="msg ai">
        <div class="msg-avatar ai">AI</div>
        <div class="typing-indicator">
          <span class="typing-text">正在思考</span>
          <span class="typing-dots"><i></i><i></i><i></i></span>
        </div>
      </div>

      <!-- 待处理人工交互卡片 -->
      <PendingActionCard
        v-for="act in displayPendingActions"
        :key="act.id"
        :act="act"
        :busy="pendingActActions[act.id]"
        @start="(act) => emit('scaleStart', act)"
        @answered="(act, answered) => emit('scaleAnswered', act, answered)"
      />
    </div>

    <ChatInputArea
      :model-value="inputMessage"
      :disabled="thinking"
      ref="inputAreaRef"
      @update:model-value="emit('update:inputMessage', $event)"
      @send="emit('send')"
      @voice="showVoicePage"
      @video="showComingSoon"
    />

    <!-- 语音对话输入/输出模式选择（可多选） -->
    <VoiceModeDialog
      v-model="voiceDialogVisible"
      :input-types="conversationStore.voiceInputTypes"
      :output-types="conversationStore.voiceOutputTypes"
      @confirm="handleVoiceConfirm"
    />
  </main>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Operation, DataAnalysis, ChatDotRound } from "@element-plus/icons-vue";
import dayjs from "@/shared/utils/dayjs";
import { useConversationStore } from "@/portal/stores/conversation";
import ChatInputArea from "./ChatInputArea.vue";
import PendingActionCard from "./PendingActionCard.vue";
import VoiceModeDialog from "./VoiceModeDialog.vue";
import type { ConversationItem, MemoryMessage, PendingActionItem } from "@/portal/api/conversation/types";

const props = defineProps<{
  currentConversation?: ConversationItem;
  loading: boolean;
  displayMessages: MemoryMessage[];
  thinking: boolean;
  /** AI 流式回复文本（WebSocket 增量） */
  streamText?: string;
  displayPendingActions: PendingActionItem[];
  pendingActActions: Record<string, "start" | "done" | undefined>;
  showConversationList: boolean;
  showEmotionPanel: boolean;
  inputMessage: string;
}>();

const emit = defineEmits<{
  (e: "update:inputMessage", val: string): void;
  (e: "toggleList"): void;
  (e: "toggleEmotion"): void;
  (e: "send"): void;
  (e: "focusInput"): void;
  (e: "scaleStart", act: PendingActionItem): void;
  (e: "scaleAnswered", act: PendingActionItem, answered: number): void;
}>();

const router = useRouter();
const conversationStore = useConversationStore();
const chatBodyRef = ref<HTMLElement | null>(null);
const inputAreaRef = ref<InstanceType<typeof ChatInputArea> | null>(null);

/* ==================== 语音对话模式选择 ==================== */

const voiceDialogVisible = ref(false);

/** 语音对话：先弹输入/输出模式选择弹窗，确认后进入语音页 */
function showVoicePage() {
  voiceDialogVisible.value = true;
}

function handleVoiceConfirm(inputTypes: string[], outputTypes: string[]) {
  conversationStore.setVoiceModes(inputTypes, outputTypes);
  const query: Record<string, string> = {
    inputTypes: inputTypes.join(","),
    outputTypes: outputTypes.join(","),
  };
  const conv = props.currentConversation;
  if (conv && !conv.draft && conv.id && !conv.id.startsWith("local-")) {
    // 从已有会话进入：语音页负责释放当前 TEXT 生命周期后按所选模式重建
    query.conversationId = conv.id;
    query.fromText = "1";
  }
  voiceDialogVisible.value = false;
  void router.push({ path: "/conversation/audio", query });
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

function showComingSoon() {
  ElMessage.info("该功能即将上线，敬请期待");
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatBodyRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

function focusInput() {
  nextTick(() => {
    inputAreaRef.value?.focus();
  });
}

watch(() => props.displayMessages, () => scrollToBottom());
watch(() => props.thinking, () => scrollToBottom());
watch(() => props.streamText, () => scrollToBottom());

defineExpose({ scrollToBottom, focusInput });
</script>

<style scoped>
.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #f8f9fc;
  position: relative;
}

.ca-header {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e5e7f0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  padding: 0 56px;
}

.panel-toggle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 40px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: #9a9ab0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: all 0.2s ease;
  font-size: 13px;
}

.panel-toggle.left {
  left: 8px;
}

.panel-toggle.right {
  right: 8px;
}

.panel-toggle:hover {
  color: #6c63ff;
  background: #f0eeff;
}

.panel-toggle.hidden {
  color: #6c63ff;
}

.ca-header-center {
  text-align: center;
  min-width: 0;
}

.ca-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e1e2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 50vw;
}

.ca-disclaimer {
  font-size: 11px;
  color: #9a9ab0;
  margin-top: 2px;
  letter-spacing: 0.2px;
}

.ca-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  scroll-behavior: smooth;
}

.ca-body::-webkit-scrollbar {
  width: 5px;
}

.ca-body::-webkit-scrollbar-thumb {
  background: #d8dae6;
  border-radius: 5px;
}

.chat-greeting {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #9a9ab0;
}

.greeting-logo {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: linear-gradient(135deg, #6c63ff, #818cf8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  box-shadow: 0 8px 24px rgba(108, 99, 255, 0.3);
}

.greeting-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e1e2e;
  margin: 0 0 8px;
}

.greeting-text {
  font-size: 13px;
  line-height: 1.8;
  max-width: 360px;
  margin: 0 0 6px;
}

.greeting-tip {
  font-size: 12px;
  margin: 0 0 20px;
}

.chat-skeleton {
  padding-top: 8px;
}

.chat-skeleton .sk-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.chat-skeleton .sk-row.user {
  justify-content: flex-end;
}

.chat-skeleton .sk-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #818cf8, #6c63ff);
  flex-shrink: 0;
}

.chat-skeleton .sk-line {
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(90deg, #eef0f6, #fafbfe, #eef0f6);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.msg {
  display: flex;
  margin-bottom: 20px;
  animation: msgIn 0.3s ease;
}

@keyframes msgIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
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
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.msg-bubble.ai {
  background: #fff;
  color: #1e1e2e;
  border: 1px solid #e8eaf0;
}

.msg-bubble.user {
  background: linear-gradient(135deg, #6c63ff, #818cf8);
  color: #fff;
}

/* 流式输出光标 */
.stream-cursor {
  display: inline-block;
  width: 6px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #6c63ff;
  border-radius: 1px;
  animation: cursor-blink 1s steps(2, start) infinite;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.msg-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
}

.msg-content.right .msg-time {
  text-align: right;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e8eaf0;
  border-radius: 16px;
}

.typing-text {
  font-size: 13px;
  color: #6c63ff;
}

.typing-dots i {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6c63ff;
  margin: 0 2px;
  animation: dotBounce 1.2s infinite ease-in-out;
}

.typing-dots i:nth-child(1) { animation-delay: 0s; }
.typing-dots i:nth-child(2) { animation-delay: 0.2s; }
.typing-dots i:nth-child(3) { animation-delay: 0.4s; }

@keyframes dotBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}
</style>